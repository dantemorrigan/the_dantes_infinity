package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.doom.Doombot;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;

/** All six Doombots in a row on open ground beside the player: their size against a person, from the front and the side. */
public class DoombotShowcaseTest implements FabricClientGameTest {
	private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("time set 6000");
			server.runCommand("gamemode creative @p");
			server.runCommand("execute as @p at @s run fill ~-24 ~-1 ~-6 ~24 ~-1 ~12 minecraft:polished_deepslate");
			server.runCommand("execute as @p at @s run fill ~-24 ~ ~-6 ~24 ~12 ~12 minecraft:air");
			String[] roles = {"soldier", "bastion", "interceptor", "arcanist", "engineer", "royal_guard"};
			for (int i = 0; i < 6; i++) {
				server.runCommand("execute as @p at @s run gauntlet doombot spawn " + roles[i] + " true ~" + (-12.5 + i * 5.0) + " ~ ~-1");
			}
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~8 180 0");
			ctx.waitTicks(30);
			var sizes = server.computeOnServer(s -> {
				StringBuilder out = new StringBuilder();
				for (Doombot b : s.overworld().getEntitiesOfClass(Doombot.class, s.getPlayerList().getPlayers().getFirst().getBoundingBox().inflate(40)))
					out.append(Doombot.NAMES[b.variant()]).append(' ').append(String.format("%.2f", b.getBbHeight())).append(" x ").append(String.format("%.2f", b.getBbWidth())).append("; ");
				return out.toString();
			});
			System.out.println("DOOMBOT SIZES: " + sizes);
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			ctx.takeScreenshot("bots_front");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 180 8");
			ctx.waitTicks(10);
			ctx.takeScreenshot("bots_with_player");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~-8 270 0");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			ctx.waitTicks(10);
			ctx.takeScreenshot("bots_side");
			check(sizes.contains("bastion"), "No Doombots were spawned");
		}
	}
}
