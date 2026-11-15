package com.dantemorrigan.gauntlet.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** The statue of Doom: his face close up, and the whole figure from the square. */
public class LatveriaStatueTest implements FabricClientGameTest {
	private static final Object[][] SHOTS = {
			{"face", 0.5, 121.0, 47.5, 180f, 2f},
			{"face_left", -9.5, 121.0, 44.5, 215f, 2f},
			{"figure", 0.5, 95.0, 88.5, 180f, -12f},
			{"ground", 0.5, 70.0, 52.5, 180f, -40f},
	};

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamemode spectator @p");
			server.runCommand("execute as @p run gauntlet latveria enter");
			ctx.waitTicks(260);
			for (Object[] s : SHOTS) {
				server.runCommand("execute in infinity_gauntlet:latveria run tp @p " + s[1] + " " + s[2] + " " + s[3] + " " + s[4] + " " + s[5]);
				ctx.waitTicks(40);
				sp.getClientLevel().waitForChunksRender();
				ctx.takeScreenshot("statue_" + s[0]);
			}
		}
	}
}
