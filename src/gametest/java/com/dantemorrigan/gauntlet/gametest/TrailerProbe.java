package com.dantemorrigan.gauntlet.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Probe: how fast does the client render here (for recording the trailer)? */
public class TrailerProbe implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamemode spectator @p");
			server.runCommand("time set 6000");
			ctx.runOnClient(mc -> { mc.options.hideGui = true; mc.options.renderDistance().set(12); });
			for (int[] size : new int[][]{{1280, 720}, {1920, 1080}}) {
				ctx.runOnClient(mc -> mc.getWindow().setWindowed(size[0], size[1]));
				ctx.waitTicks(20);
				System.out.println("PROBE window " + ctx.computeOnClient(mc -> mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight() + " gui " + mc.getWindow().getGuiScale()));
				for (int i = 0; i < 4; i++) {
					ctx.waitTicks(20);
					System.out.println("PROBE " + size[0] + " fps=" + ctx.computeOnClient(mc -> mc.getFps()));
				}
			}
			System.out.println("PROBE start " + System.currentTimeMillis());
			for (int i = 0; i < 2; i++) {
				ctx.waitTicks(20);
				System.out.println("PROBE t=" + i + " fps=" + ctx.computeOnClient(mc -> mc.getFps()) + " " + System.currentTimeMillis());
			}
		}
	}
}
