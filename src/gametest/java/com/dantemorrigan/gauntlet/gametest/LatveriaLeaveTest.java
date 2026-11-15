package com.dantemorrigan.gauntlet.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Leaving Latveria through the gate while the hunt is on (and while the welcome is still open) must not crash. */
public class LatveriaLeaveTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		System.setProperty("infinity_gauntlet.noWelcome", "false");
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamemode survival @p");
			server.runCommand("effect give @p minecraft:resistance 900 4 true");
			server.runCommand("execute as @p run gauntlet latveria enter");
			ctx.waitTicks(40);
			server.runCommand("execute as @p run gauntlet latveria alarm");
			ctx.waitTicks(200);
			ctx.runOnClient(mc -> mc.setScreen(null));
			for (int round = 0; round < 2; round++) {
				server.runCommand("execute in infinity_gauntlet:latveria run tp @p 0.5 69 128.5");
				ctx.waitTicks(100);
				ctx.runOnClient(mc -> mc.setScreen(null));
				ctx.waitTicks(60);
				String dim = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().level().dimension().identifier().toString());
				System.out.println("LEAVE round " + round + " dim " + dim);
				ctx.takeScreenshot("leave_" + round);
				if (round == 0) {
					server.runCommand("execute as @p run gauntlet latveria enter");
					ctx.waitTicks(200);
				}
			}
		}
	}
}
