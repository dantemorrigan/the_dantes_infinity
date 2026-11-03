package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.client.celestial.CelestialClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Sun destroyed -> eternal night with the moon; moon destroyed -> normal days. */
public class EternalNightTest implements FabricClientGameTest {
	private static long tod(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> Math.floorMod(mc.level.getOverworldClockTime(), 24000L));
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			sp.getServer().runCommand("weather clear");
			sp.getServer().runCommand("time set 3000");
			ctx.waitTicks(5);
			ctx.getInput().lookAt(-90, -40);
			ctx.takeScreenshot("n0_day");
			sp.getServer().runCommand("execute as @p at @p run gauntlet celestial sun");
			ctx.waitFor(mc -> CelestialClient.eventTime(0) >= 260, 600);
			System.out.println("DEBUG tod after sun broke: " + tod(ctx));
			ctx.takeScreenshot("n1_dusk_red");
			sp.getServer().runCommand("gauntlet celestial stop");
			ctx.waitTicks(140);
			System.out.println("DEBUG tod after event: " + tod(ctx));
			ctx.getInput().lookAt(90, -40);
			ctx.takeScreenshot("n2_eternal_night_moon");
			sp.getServer().runCommand("time set 1000");
			ctx.waitTicks(10);
			System.out.println("DEBUG tod after /time set 1000: " + tod(ctx));
			ctx.takeScreenshot("n3_still_night");
			sp.getServer().runCommand("gauntlet celestial restore");
			sp.getServer().runCommand("time set 3000");
			ctx.waitTicks(10);
			System.out.println("DEBUG tod after restore + day: " + tod(ctx));
			sp.getServer().runCommand("execute as @p at @p run gauntlet celestial moon");
			ctx.waitFor(mc -> CelestialClient.eventTime(0) >= 200, 600);
			sp.getServer().runCommand("gauntlet celestial stop");
			ctx.waitTicks(140);
			System.out.println("DEBUG tod after moon broke: " + tod(ctx));
			ctx.getInput().lookAt(-90, -40);
			ctx.takeScreenshot("n4_moon_gone_day");
		}
	}
}
