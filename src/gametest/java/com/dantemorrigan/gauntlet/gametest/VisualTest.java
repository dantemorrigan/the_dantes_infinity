package com.dantemorrigan.gauntlet.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Scripted walkthrough that takes screenshots of every stage (run with ./gradlew runClientGameTest). */
public class VisualTest implements FabricClientGameTest {
	private static TestSingleplayerContext SP;

	/** Run as the player but with server permissions (the test player is not an operator). */
	private static void cmd(ClientGameTestContext ctx, String command) {
		SP.getServer().runCommand("execute as @p at @p run " + command);
		ctx.waitTicks(2);
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
        LegacyBindings.enable(ctx);
		net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave save;
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			SP = sp;
			sp.getClientLevel().waitForChunksRender();
			cmd(ctx, "time set noon");
			cmd(ctx, "weather clear");
			cmd(ctx, "gamerule advance_time false");
			// A small "house" next to the player so the building fragment is real.
			cmd(ctx, "fill ~3 ~ ~-2 ~5 ~2 ~-4 minecraft:oak_planks hollow");
			cmd(ctx, "gauntlet give_power");
			ctx.waitTicks(10);
			ctx.getInput().lookAt(0, 10);
			ctx.takeScreenshot("01_worn_first_person");

			ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
			ctx.waitTicks(5);
			ctx.takeScreenshot("02_worn_third_person_front");
			ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
			ctx.waitTicks(5);
			ctx.takeScreenshot("03_worn_third_person_back");
			ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));

			// Time stone: walk away, then rewind (R).
			cmd(ctx, "gauntlet give_time");
			ctx.getInput().lookAt(0, 10);
			ctx.getInput().holdKeyFor(opts -> opts.keyUp, 40);
			ctx.getInput().pressKey(GLFW.GLFW_KEY_R);
			ctx.waitTicks(10);
			ctx.takeScreenshot("03b_time_rewind_personal");
			ctx.waitTicks(40);

			// Charge (hold right mouse) and look at the ground.
			ctx.getInput().lookAt(0, 70);
			ctx.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			ctx.waitTicks(10);
			ctx.takeScreenshot("04_charging_aura");
			ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			ctx.waitTicks(1);
			ctx.takeScreenshot("05_strike_impact");
			ctx.waitTicks(3);
			ctx.takeScreenshot("06_strike_shockwave");
			ctx.waitTicks(6);
			ctx.takeScreenshot("07_strike_smoke");

			// Individual effects.
			for (String fx : new String[]{"ground_fracture", "planet_rift", "detonation_flash", "void_dissolve"}) {
				ctx.getInput().lookAt(0, 45);
				cmd(ctx, "gauntlet fx " + fx);
				ctx.waitTicks(fx.equals("detonation_flash") ? 1 : 5);
				ctx.takeScreenshot("08_fx_" + fx);
				ctx.waitTicks(20);
			}

			// Ultimate via real input: switch mode (V), charge 45 ticks, strike the ground.
			ctx.runOnClient(mc -> mc.options.renderDistance().set(6));
			ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
			ctx.getInput().pressKey(GLFW.GLFW_KEY_V);
			ctx.waitTicks(5);
			ctx.getInput().lookAt(0, 80);
			ctx.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			ctx.waitTicks(45);
			ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			ctx.waitTicks(2);
			ctx.getInput().lookAt(0, 30);
			int[] shots = {3, 12, 25, 40, 50, 58, 64, 67};
			int t = 2;
			for (int s : shots) {
				ctx.waitTicks(s - t);
				t = s;
				ctx.takeScreenshot("10_cutscene_t" + s);
			}
			ctx.waitFor(mc -> mc.level != null && mc.level.dimension().identifier().getPath().equals("dead_space"), 200);
			ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
			int[] spaceShots = {76, 82, 95, 120, 160, 200};
			for (int s : spaceShots) {
				int now = ctx.computeOnClient(mc -> com.dantemorrigan.gauntlet.client.cutscene.Cutscene.isActive()
						? com.dantemorrigan.gauntlet.client.cutscene.Cutscene.age() : 999);
				if (now < s) ctx.waitTicks(s - now);
				ctx.getInput().lookAt(180, -6);
				ctx.takeScreenshot("11_space_t" + s);
			}
			// Look around: debris, sky faces.
			float[][] views = {{0, 0}, {90, 0}, {-90, 0}, {180, -60}, {180, 60}};
			for (float[] v : views) {
				ctx.getInput().lookAt(v[0], v[1]);
				ctx.waitTicks(2);
				ctx.takeScreenshot("12_space_view_" + (int) v[0] + "_" + (int) v[1]);
			}
			// Fly forward a bit (inertia).
			ctx.getInput().holdKeyFor(opts -> opts.keyUp, 30);
			ctx.waitTicks(10);
			ctx.takeScreenshot("13_space_after_flight");
			save = sp.getWorldSave();
		}
		// Reconnect: the player must still be in Dead Space with the scene rebuilt.
		try (TestSingleplayerContext sp = save.open()) {
			SP = sp;
			ctx.waitFor(mc -> mc.level != null && mc.player != null, 600);
			ctx.waitFor(mc -> mc.level.dimension().identifier().getPath().equals("dead_space"), 400);
			ctx.waitTicks(40);
			ctx.getInput().lookAt(180, -6);
			ctx.takeScreenshot("14_space_after_relog");
			// Time stone undoes the destruction: planet reassembles, green flash, back home.
			ctx.getInput().pressKey(GLFW.GLFW_KEY_R);
			ctx.waitTicks(20);
			ctx.takeScreenshot("15_world_rewind_t20");
			ctx.waitTicks(25);
			ctx.takeScreenshot("16_world_rewind_t45");
			ctx.waitFor(mc -> mc.level != null && !mc.level.dimension().identifier().getPath().equals("dead_space"), 400);
			ctx.waitTicks(10);
			ctx.takeScreenshot("17_world_restored");
			ctx.waitTicks(40);
			ctx.takeScreenshot("18_world_restored_later");
		}
	}
}
