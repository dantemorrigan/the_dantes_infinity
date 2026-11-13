package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.client.doom.LatveriaSky;
import com.dantemorrigan.gauntlet.doom.Latveria;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Latveria's sky, fog and screen filter from the valley, the city and the castle road, in every direction. */
public class LatveriaLookTest implements FabricClientGameTest {
	private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamemode spectator @p");
			server.runCommand("execute as @p run gauntlet latveria enter");
			ctx.waitTicks(260); // the title card of the first arrival
			check(ctx.computeOnClient(mc -> LatveriaSky.active()), "The client does not know it is in Latveria");
			String[] views = {"north_castle", "south_gate", "east", "west"};
			float[] yaws = {180, 0, 270, 90};
			for (int i = 0; i < 4; i++) {
				server.runCommand("execute in infinity_gauntlet:latveria run tp @p 0.5 78 60.5 " + yaws[i] + " -4");
				ctx.waitTicks(30);
				sp.getClientLevel().waitForChunksRender();
				ctx.takeScreenshot("look_" + views[i]);
			}
			server.runCommand("execute in infinity_gauntlet:latveria run tp @p 0.5 78 60.5 150 -38");
			ctx.waitTicks(20);
			ctx.takeScreenshot("look_up_moon");
			server.runCommand("execute in infinity_gauntlet:latveria run tp @p 0.5 78 60.5 180 -62");
			ctx.waitTicks(20);
			ctx.takeScreenshot("look_zenith");
			// Wait for a strike: find the next flash in the world time and look at it.
			for (int i = 0; i < 900; i += 5) {
				ctx.waitTicks(5);
				if (ctx.computeOnClient(mc -> LatveriaSky.flash(0)) > 0.8f) { ctx.takeScreenshot("look_lightning"); break; }
			}
			server.runCommand("gamemode survival @p");
			server.runCommand("execute in infinity_gauntlet:latveria run tp @p 0.5 69 60.5 180 0");
			ctx.waitTicks(40);
			ctx.takeScreenshot("look_street");

			// The gate home: a fixed portal in its arch, seen from the city and from outside, with the player moving about.
			server.runCommand("gamemode creative @p");
			server.runCommand("execute in infinity_gauntlet:latveria run tp @p 0.5 70 112.5 0 -2");
			ctx.waitTicks(30);
			ctx.takeScreenshot("look_gate_from_city");
			server.runCommand("execute in infinity_gauntlet:latveria run tp @p 14.5 70 120.5 40 -2");
			ctx.waitTicks(10);
			ctx.takeScreenshot("look_gate_from_side_a");
			server.runCommand("execute in infinity_gauntlet:latveria run tp @p -14.5 70 120.5 -40 -2");
			ctx.waitTicks(10);
			ctx.takeScreenshot("look_gate_from_side_b");
			server.runCommand("execute in infinity_gauntlet:latveria run tp @p 0.5 70 140.5 180 -2");
			ctx.waitTicks(10);
			ctx.takeScreenshot("look_gate_from_outside");

			// The Doombots beside a player-sized villager, all six, each at its own size.
			server.runCommand("execute in infinity_gauntlet:latveria run tp @p 0.5 69 70.5 180 0");
			String[] roles = {"soldier", "bastion", "interceptor", "arcanist", "engineer", "royal_guard"};
			for (int i = 0; i < 6; i++) {
				server.runCommand("execute as @p run gauntlet doombot spawn " + roles[i] + " true " + (-9.5 + i * 4.0) + " 69 60.5");
			}
			server.runCommand("execute in infinity_gauntlet:latveria run summon minecraft:villager 13 69 62 {NoAI:1b}");
			ctx.waitTicks(40);
			ctx.takeScreenshot("look_doombots_group");
		}
	}
}
