package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.client.doom.DoomClient;
import com.dantemorrigan.gauntlet.doom.DoctorDoom;
import com.dantemorrigan.gauntlet.doom.Doombot;
import com.dantemorrigan.gauntlet.doom.Latveria;
import com.dantemorrigan.gauntlet.doom.LatveriaPlan;
import com.dantemorrigan.gauntlet.power.SpaceStone;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * The admin tools of The Fury of Doom, and through {@code prepare} the honest way in: the teaser seen, Loki beaten
 * and the Mind Stone carried make the seal readable, and the portal option opens.
 */
public class LatveriaCommandsTest implements FabricClientGameTest {
	private static ServerPlayer player(MinecraftServer s) { return s.getPlayerList().getPlayers().getFirst(); }

	private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamemode creative @p");
			server.runCommand("execute as @p run gauntlet latveria help");

			// The honest way in.
			server.runCommand("execute as @p run gauntlet latveria prepare");
			ctx.waitTicks(30);
			check(server.computeOnServer(s -> SpaceStone.latveriaSealed(player(s)) && Latveria.hasAccess(player(s))), "prepare did not lead to the seal being read");
			check(server.computeOnServer(s -> player(s).getInventory().countItem(ModItems.MIND_STONE)) > 0, "prepare must hand over a Mind Stone");

			// Travel to named places; skip parts of the assault.
			server.runCommand("execute as @p run gauntlet latveria tp yard");
			ctx.waitTicks(40);
			check(server.computeOnServer(s -> player(s).level().dimension() == Latveria.LEVEL && player(s).position().distanceTo(new net.minecraft.world.phys.Vec3(0.5, LatveriaPlan.FLOOR, -212.5)) < 2),
					"tp yard did not land in the yard");
			server.runCommand("execute as @p run gauntlet latveria squads");
			ctx.waitTicks(10);
			// Squads far from the players are written into their chunks, so count the record, not the loaded entities.
			String state = server.computeOnServer(s -> Latveria.status(s));
			check(state.contains("spawnedMask=15") && state.contains("squadsAlive=[2, 5, 7, 4]"), "squads did not bring up every squad: " + state);
			server.runCommand("execute as @p run gauntlet latveria skip all");
			ctx.waitTicks(60);
			check(server.computeOnServer(s -> Latveria.generatorOff(s) && s.getLevel(Latveria.LEVEL).getBlockState(new BlockPos(0, LatveriaPlan.FLOOR + 2, -253)).isAir()),
					"skip all did not open the castle");
			String left = server.computeOnServer(s -> s.getLevel(Latveria.LEVEL).getEntitiesOfClass(Doombot.class, new AABB(-400, 0, -500, 400, 320, 400), b -> b.isAlive() && b.squad() >= 0)
					.stream().map(b -> Doombot.NAMES[b.variant()] + "/squad" + b.squad() + "@" + b.blockPosition().toShortString()).toList().toString());
			check(left.equals("[]"), "skip all left squad Doombots standing: " + left);
			server.runCommand("execute as @p run gauntlet latveria generator on");
			check(server.computeOnServer(s -> !Latveria.generatorOff(s) && s.getLevel(Latveria.LEVEL).getBlockState(LatveriaPlan.GENERATOR).is(Blocks.SEA_LANTERN)), "generator on failed");

			// The boss on demand.
			server.runCommand("execute as @p run gauntlet doom spawn");
			ctx.waitTicks(110);
			check(server.computeOnServer(s -> Latveria.boss() != null && Latveria.boss().fighting()), "doom spawn did not start a fight");
			server.runCommand("execute as @p run gauntlet doom action seven_suns");
			ctx.waitTicks(2);
			check(server.computeOnServer(s -> Latveria.boss().action()) == DoctorDoom.A_SUNS, "doom action did not start the Seven Suns");
			server.runCommand("execute as @p run gauntlet doom stage 3");
			for (int i = 0; i < 60 && server.computeOnServer(s -> Latveria.boss().phase()) < 3; i++) ctx.waitTicks(5);
			check(server.computeOnServer(s -> Latveria.boss().phase()) == 3, "doom stage 3 did not reach the last stage");
			server.runCommand("execute as @p run gauntlet doom passive");
			server.runCommand("execute as @p run gauntlet doom heal");
			check(server.computeOnServer(s -> Latveria.boss().getHealth() == Latveria.boss().getMaxHealth()), "doom heal failed");
			server.runCommand("execute as @p run gauntlet doom stop");
			ctx.waitTicks(5);
			check(server.computeOnServer(s -> Latveria.boss() == null), "doom stop did not call the fight off");

			// Doombots on demand, music, the title card, conquest, the items.
			server.runCommand("execute as @p run gauntlet doombot spawn bastion true");
			server.runCommand("execute as @p run gauntlet doombot spawn interceptor");
			ctx.waitTicks(10);
			check(server.computeOnServer(s -> player(s).level().getEntitiesOfClass(Doombot.class, player(s).getBoundingBox().inflate(10), Doombot::allied).size()) == 1, "an allied Bastion was not made");
			server.runCommand("execute as @p run gauntlet doombot kill");
			ctx.waitTicks(5);
			server.runCommand("execute as @p run gauntlet latveria music sanctum");
			ctx.waitTicks(5);
			check(ctx.computeOnClient(mc -> DoomClient.lastTheme()).equals("sovereign_sanctum"), "music sanctum did not start the theme");
			server.runCommand("execute as @p run gauntlet latveria conquer");
			ctx.waitTicks(5);
			check(server.computeOnServer(s -> Latveria.conquered(s) && Latveria.isOwner(player(s)) && player(s).getInventory().countItem(ModItems.LATVERIA_KEY) == 1), "conquer did not hand over the castle");
			server.runCommand("execute as @p run gauntlet latveria give");
			server.runCommand("execute as @p run gauntlet latveria tp throne");
			ctx.waitTicks(30);
			ctx.takeScreenshot("latveria_commands_throne");
			server.runCommand("execute as @p run gauntlet latveria forget");
			check(server.computeOnServer(s -> !Latveria.isOwner(player(s)) && !Latveria.hasAccess(player(s))), "forget did not wipe the player");
			server.runCommand("execute as @p run gauntlet latveria reset");
			check(server.computeOnServer(s -> !Latveria.conquered(s)), "reset did not reopen the assault");
		}
	}
}
