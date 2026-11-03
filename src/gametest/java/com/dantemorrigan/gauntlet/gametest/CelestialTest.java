package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.client.celestial.CelestialClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;

/** Celestial Apocalypse walkthrough with screenshots (sun by day via real input, moon by night via command). */
public class CelestialTest implements FabricClientGameTest {
	private static TestSingleplayerContext SP;

	private static void cmd(ClientGameTestContext ctx, String command) {
		SP.getServer().runCommand("execute as @p at @p run " + command);
		ctx.waitTicks(2);
	}

	/** Look at the sun/moon using the same transform as the sky renderer. */
	private static void lookAtBody(ClientGameTestContext ctx, boolean sun) {
		float[] yp = ctx.computeOnClient(mc -> {
			float deg = mc.gameRenderer.getMainCamera().attributeProbe().getValue(sun ? EnvironmentAttributes.SUN_ANGLE : EnvironmentAttributes.MOON_ANGLE, 0f);
			float a = deg * Mth.DEG_TO_RAD;
			double dx = -Math.sin(a), dy = Math.cos(a), dz = 0;
			float pitch = (float) -Math.toDegrees(Math.asin(dy));
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			return new float[]{yaw, pitch};
		});
		ctx.getInput().lookAt(yp[0], yp[1]);
	}

	private static void waitEvent(ClientGameTestContext ctx, int t) {
		ctx.waitFor(mc -> CelestialClient.phase() == 0 || CelestialClient.eventTime(0) >= t, 600);
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
        LegacyBindings.enable(ctx);
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			SP = sp;
			sp.getClientLevel().waitForChunksRender();
			// Short rain for the test (real-time deadline).
			sp.getServer().runOnServer(server -> {
				try {
					Files.writeString(Path.of("config/infinity_gauntlet.json"), "{\"meteorRainSeconds\": 25, \"meteorImpactsPerSecond\": 2.0, \"meteorImpactRadius\": 40}");
				} catch (java.io.IOException e) {
					throw new RuntimeException(e);
				}
			});
			cmd(ctx, "gauntlet reload_config");
			cmd(ctx, "gamerule advance_time false");
			cmd(ctx, "weather clear");
			cmd(ctx, "time set 2500");
			cmd(ctx, "gauntlet give_power");
			ctx.waitTicks(5);
			ctx.getInput().pressKey(GLFW.GLFW_KEY_V);
			ctx.waitTicks(3);
			ctx.getInput().pressKey(GLFW.GLFW_KEY_V); // strike -> planet -> celestial
			ctx.waitTicks(5);
			lookAtBody(ctx, true);
			ctx.waitTicks(3);
			ctx.takeScreenshot("c00_sun_before");
			ctx.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			ctx.waitTicks(30);
			ctx.takeScreenshot("c01_aiming_thread");
			ctx.waitFor(mc -> CelestialClient.phase() == 1, 200);
			ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			int[] shots = {64, 94, 124, 154, 184, 195, 210, 225, 238};
			for (int t : shots) {
				waitEvent(ctx, t);
				lookAtBody(ctx, true);
				ctx.waitTicks(1);
				ctx.takeScreenshot("c10_sun_t" + t);
			}
			waitEvent(ctx, 250);
			ctx.getInput().lookAt(0, 20);
			ctx.takeScreenshot("c20_rain_ground");
			ctx.waitTicks(20);
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			ctx.getInput().lookAt(30, -10);
			ctx.takeScreenshot("c21_rain_sky");
			ctx.waitTicks(40);
			ctx.getInput().lookAt(90, 15);
			ctx.takeScreenshot("c22_rain_craters");
			ctx.waitTicks(40);
			ctx.getInput().lookAt(200, 25);
			ctx.takeScreenshot("c23_rain_fires");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			cmd(ctx, "gauntlet state");

			// Night: break the moon (debug start) and look at it.
			cmd(ctx, "gauntlet celestial stop");
			cmd(ctx, "time set 16000");
			ctx.waitTicks(10);
			lookAtBody(ctx, false);
			ctx.takeScreenshot("c30_moon_before");
			cmd(ctx, "gauntlet celestial moon");
			for (int t : new int[]{100, 160, 200, 230}) {
				waitEvent(ctx, t);
				lookAtBody(ctx, false);
				ctx.waitTicks(1);
				ctx.takeScreenshot("c31_moon_t" + t);
			}
			waitEvent(ctx, 300);
			ctx.getInput().lookAt(40, -15);
			ctx.takeScreenshot("c32_night_red_sky");
			// Natural end of the (shortened) rain.
			ctx.waitFor(mc -> CelestialClient.phase() == 0, 1600);
			ctx.waitTicks(120);
			ctx.getInput().lookAt(40, -15);
			ctx.takeScreenshot("c40_after_end_no_moon");
			cmd(ctx, "gauntlet celestial restore");
			ctx.waitTicks(10);
			lookAtBody(ctx, false);
			ctx.takeScreenshot("c41_restored_moon");
		}
	}
}
