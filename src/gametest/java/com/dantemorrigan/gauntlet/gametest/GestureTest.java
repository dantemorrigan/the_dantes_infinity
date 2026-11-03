package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.GauntletGesture;
import com.dantemorrigan.gauntlet.client.render.GauntletGestureClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;

/** The "admire the gauntlet" gesture: screenshots of each phase in first and third person. */
public class GestureTest implements FabricClientGameTest {
	private static final float[] PHASES = {0.15f, 0.27f, 0.42f, 0.56f, 0.66f, 0.75f, 0.85f};

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("gamerule advance_time false");
			sp.getServer().runCommand("execute as @p at @p run gauntlet give_power");
			ctx.waitTicks(10);
			ctx.getInput().lookAt(0, 0);
			ctx.takeScreenshot("gesture_00_idle");
			for (CameraType camera : new CameraType[]{CameraType.FIRST_PERSON, CameraType.THIRD_PERSON_FRONT, CameraType.THIRD_PERSON_BACK}) {
				ctx.runOnClient(mc -> mc.options.setCameraType(camera));
				ctx.waitTicks(3);
				for (float phase : PHASES) {
					ctx.runOnClient(mc -> GauntletGestureClient.startAt(mc.player.getId(), phase));
					ctx.takeScreenshot("gesture_" + (camera == CameraType.FIRST_PERSON ? "fp" : camera == CameraType.THIRD_PERSON_FRONT ? "front" : "back") + "_" + Math.round(phase * 100));
				}
			}
			// The key starts it for real.
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			ctx.waitTicks(GauntletGesture.DURATION); // let the last frozen phase finish
			ctx.getInput().pressKey(GauntletGestureClient.KEY);
			ctx.waitTicks(30);
			ctx.runOnClient(mc -> {
				if (GauntletGestureClient.pose(mc.player.getId()) == null) throw new AssertionError("gesture key did not start the gesture");
			});
			ctx.takeScreenshot("gesture_key_30t");
		}
	}
}
