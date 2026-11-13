package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.client.doom.GodDoomScene;
import com.dantemorrigan.gauntlet.doom.DoctorDoom;
import com.dantemorrigan.gauntlet.doom.GauntletSilence;
import com.dantemorrigan.gauntlet.doom.Latveria;
import com.dantemorrigan.gauntlet.doom.LatveriaPlan;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * God Emperor Doom: the ascension scene plays through (camera, turn to ivory, the Incursion, the white, Battleworld),
 * everyone is pulled back whole, the duel resets; and a full-gauntlet bearer struck down by Doom is spared for it.
 */
public class DoomAscensionTest implements FabricClientGameTest {
	private static ServerPlayer player(MinecraftServer s) { return s.getPlayerList().getPlayers().getFirst(); }

	private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

	@Override
	public void runTest(ClientGameTestContext ctx) {
		LegacyBindings.disable(ctx);
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamemode creative @p");
			server.runCommand("execute as @p run gauntlet latveria enter");
			ctx.waitTicks(270);
			server.runCommand("execute as @p run gauntlet latveria unlock throne");
			server.runCommand("execute as @p run gauntlet latveria tp hall");
			ctx.waitTicks(20);
			server.runCommand("execute as @p run gauntlet doom spawn");
			ctx.waitTicks(140);
			server.runCommand("execute as @p run gauntlet doom passive");
			server.runCommand("execute as @p run gauntlet full");
			server.runCommand("gamemode survival @p");
			server.runCommand("effect give @p minecraft:resistance 200 4 true");
			ctx.runOnClient(mc -> mc.gui.getChat().clearMessages(false));
			sp.getClientLevel().waitForChunksRender();

			server.runCommand("execute as @p run gauntlet doom ascend");
			ctx.waitTicks(3);
			check(ctx.computeOnClient(mc -> GodDoomScene.active()), "The ascension scene did not start");
			int[] shots = {40, 75, 130, 230, 330, 430};
			String[] names = {"01_rise", "02_turned", "03_radiance", "04_incursion", "05_cracks", "06_battleworld"};
			int at = 3;
			for (int i = 0; i < shots.length; i++) {
				ctx.waitTicks(shots[i] - at);
				at = shots[i];
				ctx.takeScreenshot("god_doom_" + names[i]);
				if (i == 1) check(server.computeOnServer(s -> Latveria.boss() != null && Latveria.boss().god()), "Doom did not turn into God Emperor Doom");
			}
			ctx.waitTicks(80);
			check(!ctx.computeOnClient(mc -> GodDoomScene.active()), "The scene did not end");
			check(server.computeOnServer(s -> Latveria.boss() == null), "The duel was not reset after the ascension");
			check(server.computeOnServer(s -> player(s).getZ() > -253 && player(s).getHealth() >= player(s).getMaxHealth() - 0.01f),
					"The player was not pulled back to the yard, whole");
			check(server.computeOnServer(s -> GauntletSilence.silenced(player(s))), "The beaten gauntlet is not silent");
			ctx.takeScreenshot("god_doom_07_after");

			// A full-gauntlet bearer struck down by Doom is spared: the ascension starts instead of death.
			server.runOnServer(s -> GauntletSilence.lift(player(s)));
			server.runCommand("execute as @p run gauntlet latveria tp hall");
			ctx.waitTicks(20);
			server.runCommand("execute as @p run gauntlet doom spawn");
			ctx.waitTicks(140);
			server.runCommand("effect clear @p");
			System.out.println("ASC-DEBUG " + server.computeOnServer(s -> {
				DoctorDoom d = Latveria.boss();
				ServerPlayer p = player(s);
				var g = com.dantemorrigan.gauntlet.GauntletState.activeGauntlet(p);
				String before = "boss=" + (d != null) + " fight=" + (d != null && d.fighting()) + " off=" + p.getOffhandItem() + " stones=" + com.dantemorrigan.gauntlet.GauntletState.inserted(g).size()
						+ " mode=" + p.gameMode.getGameModeForPlayer() + " hp=" + p.getHealth();
				boolean hurt = p.hurtServer(p.level(), p.level().damageSources().mobAttack(d), 1000);
				return before + " hurt=" + hurt + " hpAfter=" + p.getHealth() + " alive=" + p.isAlive() + " asc=" + (d != null && d.ascending());
			}));
			ctx.waitTicks(4);
			check(server.computeOnServer(s -> player(s).isAlive()), "The gauntlet's bearer died instead of seeing the ascension");
			check(server.computeOnServer(s -> Latveria.boss() != null && Latveria.boss().ascending()), "A fatal blow on the full gauntlet did not start the ascension");
			server.runCommand("execute as @p run gauntlet doom stop");
			server.runCommand("gamemode creative @p");
			ctx.waitTicks(10);
		}
	}
}
