package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.doom.Doombot;
import com.dantemorrigan.gauntlet.doom.Latveria;
import com.dantemorrigan.gauntlet.doom.LatveriaHunt;
import com.dantemorrigan.gauntlet.doom.LatveriaPlan;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

/**
 * The hunt in the city: the alarm launches at least three Interceptors, they reach the intruder's last known place and
 * search (asking townsfolk, looking into houses) while the intruder hides; give them nothing and they fly home; step out
 * in front of one and they find you and fight.
 */
public class LatveriaStealthTest implements FabricClientGameTest {
	private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

	private static ServerPlayer player(MinecraftServer s) { return s.getPlayerList().getPlayers().getFirst(); }

	private static java.util.List<Doombot> hunters(MinecraftServer s, double range) {
		var p = player(s);
		return s.getLevel(Latveria.LEVEL).getEntitiesOfClass(Doombot.class, new AABB(p.blockPosition()).inflate(range), LatveriaHunt::hunter);
	}

	private static void status(ClientGameTestContext ctx, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, String when) {
		System.out.println("STEALTH " + when + ": " + server.computeOnServer(s -> LatveriaHunt.debug() + " meter " + LatveriaHunt.meter(player(s))
				+ " " + hunters(s, 200).stream().map(b -> b.blockPosition().toShortString() + "/" + b.awareness() + "/t" + b.tickCount + "/" + s.getLevel(Latveria.LEVEL).isPositionEntityTicking(b.blockPosition())).toList()));
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("execute as @p run gauntlet latveria enter");
			ctx.waitTicks(260);
			// The great lamps over the city, seen from above the square.
			server.runCommand("gamemode spectator @p");
			server.runCommand("execute in infinity_gauntlet:latveria run tp @p 0.5 96 70.5 180 8");
			ctx.waitTicks(60);
			ctx.takeScreenshot("stealth_00_city_lamps");
			server.runCommand("gamemode survival @p");
			server.runCommand("effect give @p minecraft:resistance 900 4 true");
			server.runCommand("execute in infinity_gauntlet:latveria run tp @p 0.5 69 64.5 180 -10");
			ctx.waitTicks(20);
			server.runOnServer(s -> LatveriaHunt.grace(player(s), 0));
			server.runCommand("execute as @p run gauntlet latveria alarm");
			ctx.waitTicks(30);
			ctx.takeScreenshot("stealth_01_launch");
			int launched = server.computeOnServer(s -> hunters(s, 200).size());
			check(launched >= 3, "A squad of at least three Interceptors must launch, got " + launched);
			// Hide inside a house nearby before they arrive, crouched.
			server.runOnServer(s -> {
				LatveriaHunt.giveUp = 700;
				var p = player(s);
				net.minecraft.core.BlockPos best = null;
				for (var v : LatveriaPlan.VILLAGERS) if (best == null || v.distSqr(p.blockPosition()) < best.distSqr(p.blockPosition())) best = v;
				var level = s.getLevel(Latveria.LEVEL);
				for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
					var door = best.above().relative(d, 2);
					if ((level.getBlockState(door).isAir() || level.getBlockState(door).getBlock() instanceof net.minecraft.world.level.block.DoorBlock) && !level.getBlockState(door.relative(d.getClockWise())).isAir()) {
						var in = best.above().relative(d, 5);
						p.teleportTo(level, in.getX() + 0.5, in.getY(), in.getZ() + 0.5, java.util.Set.of(), d.toYRot(), 0, false);
						break;
					}
				}
				p.setShiftKeyDown(true);
			});
			// They search the houses round the place: knock, scan through the walls, find the one hiding inside, and surround it.
			server.runOnServer(s -> { LatveriaHunt.siegeTime = 240; LatveriaHunt.hotTime = 500; });
			boolean besieged = false;
			for (int i = 0; i < (Boolean.getBoolean("quick") ? 3 : 24) && !besieged; i++) {
				ctx.waitTicks(50);
				server.runOnServer(s -> player(s).setShiftKeyDown(true));
				status(ctx, server, "hiding " + i);
				if (i == 3) ctx.takeScreenshot("stealth_02_hiding");
				besieged = server.computeOnServer(s -> LatveriaHunt.phase().equals("SIEGE"));
			}
			check(besieged, "The robots must find the intruder hiding in a house and surround it");
			ctx.waitTicks(120);
			status(ctx, server, "siege");
			ctx.takeScreenshot("stealth_03_siege");
			// Stay inside: they break the door in and fight.
			boolean stormed = false;
			for (int i = 0; i < 12 && !stormed; i++) {
				ctx.waitTicks(20);
				stormed = server.computeOnServer(s -> LatveriaHunt.phase().equals("CHASE"));
			}
			status(ctx, server, "stormed");
			check(stormed, "When the siege runs out with the intruder inside, they storm the house");
			ctx.takeScreenshot("stealth_04_storm");
			// Slip far away into a dark corner: they lose the trail, search, and the district becomes a hot zone.
			server.runOnServer(s -> LatveriaHunt.giveUp = 300);
			server.runCommand("execute in infinity_gauntlet:latveria run tp @p 88.5 69 140.5");
			boolean hot = false;
			for (int i = 0; i < 40 && !hot; i++) {
				ctx.waitTicks(20);
				server.runOnServer(s -> player(s).setShiftKeyDown(true));
				hot = server.computeOnServer(s -> LatveriaHunt.phase().equals("HOTZONE"));
				if (i % 5 == 0) status(ctx, server, "escaping " + i);
			}
			check(hot, "Losing the intruder must leave a hot zone, not an empty sky");
			ctx.waitTicks(100);
			status(ctx, server, "hot zone");
			int patrol = server.computeOnServer(s -> hunters(s, 300).size());
			check(patrol > LatveriaHunt.SQUAD - 1, "Reinforcements must patrol the hot zone, got " + patrol);
			check(server.computeOnServer(s -> com.dantemorrigan.gauntlet.doom.Searchlights.focused()), "The city lamps must turn on the hot zone");
			ctx.takeScreenshot("stealth_05_hotzone");
			ctx.waitTicks(560);
			status(ctx, server, "after the hot zone");
			String end = server.computeOnServer(s -> LatveriaHunt.phase());
			check(end.equals("OUTBOUND") || end.equals("NONE"), "After the hot zone they go home, got " + end);
			ctx.takeScreenshot("stealth_06_gone");
			System.out.println("STEALTH end: " + end);
		}
	}
}
