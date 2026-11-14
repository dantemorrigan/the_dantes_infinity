package com.dantemorrigan.gauntlet.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Screenshots of the city, the square with Doom's statue and the castle from the streets, the roofs and the air. */
public class LatveriaCityTest implements FabricClientGameTest {
	/** name, x, y, z, yaw, pitch (yaw 0 looks south, 90 west, 180 north, 270 east). */
	private static final Object[][] SHOTS = {
			{"statue_far", 0.5, 112.0, 118.5, 180f, -4f},
			{"statue_mid", 0.5, 96.0, 78.5, 180f, 10f},
			{"statue_front_close", 0.5, 82.0, 58.5, 180f, 30f},
			{"statue_side", -46.5, 100.0, 30.5, 270f, 12f},
			{"square_diagonal", 14.5, 72.0, 56.5, 200f, 8f},
			{"clock_tower", -18.5, 78.0, 14.5, 180f, 28f},
			{"inn_front", 8.5, 75.0, 24.5, 270f, 10f},
			{"aerial_city", 0.5, 140.0, 128.5, 180f, 28f},
			{"aerial_city_east", 60.5, 110.0, 100.5, 215f, 25f},
			{"courtyard_above", 20.5, 95.0, -34.5, 180f, 70f},
			{"castle_road", 0.5, 150.0, -120.5, 180f, 12f},
			{"castle_gate", 0.5, 112.0, -185.5, 180f, 12f},
			{"castle_front_up", 0.5, 115.0, -225.5, 180f, -40f},
			{"castle_yard_wide", 0.5, 125.0, -215.5, 180f, -20f},
			{"castle_side", -110.5, 130.0, -250.5, 270f, 5f},
			{"castle_flag", 0.5, 118.0, -205.5, 180f, 14f},
			{"castle_gate_flag", 0.5, 106.0, -236.5, 180f, 4f},
			{"plaza_flag", 0.5, 86.0, 38.5, 90f, 18f},
			{"church", -56.5, 82.0, 88.5, 180f, 22f},
			{"gate_south", 0.5, 76.0, 112.5, 0f, 6f},
			{"wall_east", 60.5, 85.0, 60.5, 270f, 14f},
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
				ctx.waitTicks(50);
				sp.getClientLevel().waitForChunksRender();
				ctx.waitTicks(10);
				ctx.takeScreenshot("city_" + s[0]);
			}
		}
	}
}
