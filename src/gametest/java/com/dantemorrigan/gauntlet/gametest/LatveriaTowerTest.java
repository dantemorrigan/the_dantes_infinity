package com.dantemorrigan.gauntlet.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Screenshots inside the towers: the castle's round and square towers, the central tower's floors, a city wall tower. */
public class LatveriaTowerTest implements FabricClientGameTest {
	private static final int F = com.dantemorrigan.gauntlet.doom.LatveriaPlan.FLOOR;
	private static final Object[][] SHOTS = {
			{"corner_round", -46.5, F + 2.6, -298.5, 315f, 30f},
			{"keep_tower", -36.5, F + 21.6, -294.5, 315f, 30f},
			{"central_a1", -10.5, F + 21.6, -283.5, 315f, 25f},
			{"central_a2", -10.5, F + 33.6, -283.5, 315f, 25f},
			{"central_b", -6.5, F + 73.6, -279.5, 315f, 25f},
			{"gate_tower", -7.5, F + 19.6, -206.5, 315f, 30f},
			{"city_wall_tower", 76.5, 70.6, 36.5, 315f, 30f},
			{"clock_tower", -19.5, 71.6, -8.5, 315f, 30f},
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
				ctx.waitTicks(10);
				ctx.takeScreenshot("tower_" + s[0]);
			}
		}
	}
}
