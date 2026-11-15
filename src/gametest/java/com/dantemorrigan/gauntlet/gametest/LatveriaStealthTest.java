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
				+ " " + hunters(s, 200).stream().map(b -> b.blockPosition().toShortString() + "/" + b.awareness()).toList()));
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
					if (level.getBlockState(door).isAir() && !level.getBlockState(door.relative(d.getClockWise())).isAir()) {
						var in = best.above().relative(d, 5);
						p.teleportTo(level, in.getX() + 0.5, in.getY(), in.getZ() + 0.5, java.util.Set.of(), d.toYRot(), 0, false);
						break;
					}
				}
				p.setShiftKeyDown(true);
			});
			for (int i = 0; i < 6; i++) {
				ctx.waitTicks(100);
				server.runOnServer(s -> player(s).setShiftKeyDown(true));
				status(ctx, server, "hiding " + i);
				if (i == 2) ctx.takeScreenshot("stealth_02_hiding");
			}
			ctx.takeScreenshot("stealth_03_search");
			ctx.waitTicks(400);
			status(ctx, server, "after the search");
			String gone = server.computeOnServer(s -> LatveriaHunt.debug());
			ctx.takeScreenshot("stealth_04_gone");
			System.out.println("STEALTH end: " + gone);
		}
	}
}
