package com.dantemorrigan.gauntlet.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import org.lwjgl.glfw.GLFW;

/** Space stone: portals Overworld -> Nether -> End -> Overworld by walking through, and teleport where you look. */
public class SpaceStoneTest implements FabricClientGameTest {
	private static TestSingleplayerContext SP;

	private static void cmd(ClientGameTestContext ctx, String command) {
		SP.getServer().runCommand("execute as @p at @p run " + command);
		ctx.waitTicks(2);
	}

	private static String dim(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.level.dimension().identifier().getPath());
	}

	private static void walkThrough(ClientGameTestContext ctx, String expected) {
		ctx.getInput().holdKey(o -> o.keyUp);
		ctx.waitFor(mc -> mc.level != null && mc.level.dimension().identifier().getPath().equals(expected), 200);
		ctx.getInput().releaseKey(o -> o.keyUp);
		ctx.waitFor(mc -> mc.player != null && mc.screen == null, 400);
		ctx.waitTicks(30);
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			SP = sp;
			sp.getClientLevel().waitForChunksRender();
			cmd(ctx, "time set noon");
			cmd(ctx, "gamerule advance_time false");
			cmd(ctx, "gauntlet give_power");
			cmd(ctx, "gauntlet give_stone space");
			ctx.waitTicks(5);
			ctx.getInput().lookAt(0, 0);

			// Teleport where you look (far along the flat ground).
			ctx.getInput().lookAt(0, 8);
			ctx.takeScreenshot("s00_before_blink");
			ctx.getInput().pressKey(GLFW.GLFW_KEY_N);
			ctx.waitTicks(3);
			ctx.takeScreenshot("s01_after_blink");
			ctx.getInput().lookAt(180, 8);
			ctx.waitTicks(2);
			ctx.takeScreenshot("s02_blink_trail_behind");
			ctx.getInput().lookAt(0, 0);
			ctx.waitTicks(25);

			// Open the portal (default destination: Nether).
			ctx.getInput().pressKey(GLFW.GLFW_KEY_B);
			ctx.waitTicks(3);
			ctx.takeScreenshot("s10_portal_opening");
			ctx.waitTicks(20);
			ctx.takeScreenshot("s11_portal_nether_idle");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			ctx.getInput().lookAt(50, 5);
			ctx.waitTicks(2);
			ctx.takeScreenshot("s12_portal_side");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			ctx.getInput().lookAt(0, 0);
			walkThrough(ctx, "the_nether");
			ctx.getInput().lookAt(180, 0);
			ctx.takeScreenshot("s13_arrived_nether_exit_tear");

			// In the Nether: destination auto-skips the Nether -> End.
			ctx.getInput().lookAt(0, 0);
			ctx.waitTicks(40);
			ctx.getInput().pressKey(GLFW.GLFW_KEY_B);
			ctx.waitTicks(20);
			ctx.takeScreenshot("s20_portal_end_idle");
			walkThrough(ctx, "the_end");
			ctx.takeScreenshot("s21_arrived_end");

			// In the End: cycle to Overworld (Shift+B), open, walk through.
			ctx.waitTicks(40);
			ctx.getInput().holdShift();
			ctx.getInput().pressKey(GLFW.GLFW_KEY_B);
			ctx.getInput().releaseShift();
			ctx.waitTicks(5);
			ctx.getInput().pressKey(GLFW.GLFW_KEY_B);
			ctx.waitTicks(20);
			ctx.takeScreenshot("s30_portal_overworld_idle");
			walkThrough(ctx, "overworld");
			ctx.takeScreenshot("s31_back_home");
		}
	}
}
