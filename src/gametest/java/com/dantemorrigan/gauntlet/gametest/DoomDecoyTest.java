package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.doom.Doombot;
import com.dantemorrigan.gauntlet.doom.Latveria;
import com.dantemorrigan.gauntlet.doom.LatveriaPlan;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** The decoy gambit: Doom vanishes and three of him step out; the two fakes burst when struck and go when he falls. */
public class DoomDecoyTest implements FabricClientGameTest {
	private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

	private static List<Doombot> decoys(MinecraftServer s) {
		ServerLevel l = s.getLevel(Latveria.LEVEL);
		return l.getEntitiesOfClass(Doombot.class, LatveriaPlan.ARENA_BOX.inflate(4), b -> b.isAlive() && b.decoy());
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		LegacyBindings.disable(ctx);
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamemode creative @p");
			server.runCommand("execute as @p run gauntlet latveria enter");
			ctx.waitTicks(40);
			server.runCommand("execute as @p run gauntlet latveria unlock throne");
			server.runCommand("execute as @p run gauntlet latveria tp hall");
			ctx.waitTicks(20);
			server.runCommand("execute as @p run gauntlet doom spawn");
			ctx.waitTicks(140);
			server.runCommand("execute as @p run gauntlet doom passive");
			server.runCommand("execute as @p run gauntlet doom action decoy_gambit");
			ctx.waitTicks(30);
			check(server.computeOnServer(s -> decoys(s).size()) == 2, "The gambit did not leave two decoys");
			check(server.computeOnServer(s -> Latveria.boss() != null && Latveria.boss().isAlive()), "Doom himself is gone");
			server.runCommand("execute in infinity_gauntlet:latveria run tp @p 0.5 " + (LatveriaPlan.FLOOR + 3) + " -258.5 180 12");
			ctx.waitTicks(20);
			ctx.runOnClient(mc -> mc.gui.getChat().clearMessages(false));
			ctx.takeScreenshot("doom_decoys_three_dooms");
			check(server.computeOnServer(s -> decoys(s).getFirst().getName().getString()).equals(
					server.computeOnServer(s -> Latveria.boss().getName().getString())), "A decoy does not carry Doom's name");

			// Struck down, a decoy bursts; Doom stays.
			server.runOnServer(s -> {
				Doombot d = decoys(s).getFirst();
				d.hurtServer(s.getLevel(Latveria.LEVEL), s.getLevel(Latveria.LEVEL).damageSources().generic(), 100);
			});
			ctx.waitTicks(5);
			ctx.takeScreenshot("doom_decoys_revealed");
			check(server.computeOnServer(s -> decoys(s).size()) == 1, "A struck decoy did not fall");
			// When Doom falls, the last decoy shuts down.
			server.runCommand("execute as @p run gauntlet doom kill");
			ctx.waitTicks(20);
			check(server.computeOnServer(s -> decoys(s).size()) == 0, "A decoy outlived Doom");
		}
	}
}
