package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.ability.Ability;
import com.dantemorrigan.gauntlet.client.ability.AbilityInput;
import com.dantemorrigan.gauntlet.meteor.MeteorStream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.lwjgl.glfw.GLFW;

/** Meteor Stream with real input: lock on the sun, aim the ring at the ground, release, watch it rain. */
public class MeteorStreamTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		LegacyBindings.disable(ctx);
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			TestServerContext server = sp.getServer();
			sp.getClientLevel().waitForChunksRender();
			server.runCommand("time set noon");
			server.runCommand("gamerule advance_time false");
			server.runCommand("execute as @p at @p run gauntlet give_power");
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> AbilityInput.choose(Ability.METEOR_STREAM, -1));
			ctx.waitTicks(8);
			ctx.getInput().lookAt(0, -85);
			ctx.getInput().holdKey(GLFW.GLFW_KEY_R);
			ctx.waitTicks(45);
			ctx.takeScreenshot("ms_01_locked");
			ctx.getInput().lookAt(0, 6);
			ctx.waitTicks(20);
			ctx.takeScreenshot("ms_02_ring");
			ctx.getInput().releaseKey(GLFW.GLFW_KEY_R);
			ctx.waitTicks(10);
			if (server.computeOnServer(s -> MeteorStream.cooldown(s.getPlayerList().getPlayers().getFirst())) <= 0) throw new AssertionError("The stream did not start");
			ctx.waitTicks(60);
			ctx.takeScreenshot("ms_03_falling");
			ctx.getInput().lookAt(0, -50);
			ctx.waitTicks(30);
			ctx.takeScreenshot("ms_04_sky");
			ctx.getInput().lookAt(0, 4);
			ctx.waitTicks(60);
			ctx.takeScreenshot("ms_05_impacts");
		}
	}
}
