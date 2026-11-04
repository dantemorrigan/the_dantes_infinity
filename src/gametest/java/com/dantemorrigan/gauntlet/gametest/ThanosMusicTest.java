package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.client.boss.ThanosClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** The battle theme really plays in the sound engine (needs an OpenAL device: ALSOFT_DRIVERS=null works headless). */
public class ThanosMusicTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamemode creative @p");
			server.runCommand("gauntlet thanos reset");
			for (int round = 0; round < 3; round++) {
				server.runCommand("execute as @p at @s run gauntlet thanos spawn");
				ctx.waitTicks(60);
				boolean playing = ctx.computeOnClient(mc -> ThanosClient.musicPlaying());
				float vol = ctx.computeOnClient(mc -> ThanosClient.musicVolume());
				System.out.println("THANOS-MUSIC round " + round + " playing=" + playing + " volume=" + vol);
				if (!playing) throw new AssertionError("Round " + round + ": the battle theme is not playing");
				if (vol < 0.3f || vol > 0.5f) throw new AssertionError("Round " + round + ": odd music volume " + vol);
				// Fight ends: it fades out and stops; the next fight must start it again.
				server.runCommand("gauntlet thanos stop");
				ctx.waitTicks(200);
				if (ctx.computeOnClient(mc -> ThanosClient.musicPlaying())) throw new AssertionError("Round " + round + ": the theme did not stop");
			}
			// Changing dimension mid-fight kills every sound on the client; the next fight must still get its music.
			server.runCommand("execute as @p at @s run gauntlet thanos spawn");
			ctx.waitTicks(40);
			server.runCommand("execute in minecraft:the_nether run tp @p 0 120 0");
			ctx.waitTicks(60);
			server.runCommand("gauntlet thanos stop");
			ctx.waitTicks(10);
			server.runCommand("execute in minecraft:the_nether run fill -8 119 -8 8 119 8 minecraft:netherrack");
			server.runCommand("execute as @p at @s run gauntlet thanos spawn");
			ctx.waitTicks(80);
			boolean again = ctx.computeOnClient(mc -> ThanosClient.musicPlaying());
			System.out.println("THANOS-MUSIC after dimension change playing=" + again);
			if (!again) throw new AssertionError("No battle theme after a dimension change");
		}
	}
}
