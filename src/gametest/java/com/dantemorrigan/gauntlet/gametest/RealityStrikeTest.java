package com.dantemorrigan.gauntlet.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.lwjgl.glfw.GLFW;

/** Reality strike: a short tap of G — mobs around dissolve into butterflies, the ground turns to redstone + fire. */
public class RealityStrikeTest implements FabricClientGameTest {
	private static TestSingleplayerContext SP;

	private static void cmd(ClientGameTestContext ctx, String command) {
		SP.getServer().runCommand("execute as @p at @p run " + command);
		ctx.waitTicks(2);
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			SP = sp;
			sp.getClientLevel().waitForChunksRender();
			cmd(ctx, "time set noon");
			cmd(ctx, "gamerule advance_time false");
			cmd(ctx, "gauntlet give_power");
			cmd(ctx, "gauntlet give_stone reality");
			cmd(ctx, "summon minecraft:cow ~-3 ~ ~5");
			cmd(ctx, "summon minecraft:sheep ~3 ~ ~6");
			cmd(ctx, "summon minecraft:pig ~0 ~ ~8");
			cmd(ctx, "summon minecraft:zombie ~-5 ~ ~-4 {NoAI:1b}");
			cmd(ctx, "summon minecraft:cow ~ ~ ~30");
			ctx.waitTicks(30);
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			ctx.getInput().lookAt(0, 35);
			ctx.waitTicks(3);
			ctx.takeScreenshot("s00_before");
			int before = sp.getServer().computeOnServer(s -> mobs(s, 12));

			ctx.getInput().holdKey(GLFW.GLFW_KEY_G);
			ctx.waitTicks(2);
			ctx.getInput().releaseKey(GLFW.GLFW_KEY_G);
			ctx.waitTicks(3);
			ctx.takeScreenshot("s01_impact");
			ctx.waitTicks(4);
			ctx.takeScreenshot("s02_butterflies");
			ctx.waitTicks(10);
			ctx.takeScreenshot("s03_wave");
			ctx.waitTicks(30);
			ctx.takeScreenshot("s04_after");
			int after = sp.getServer().computeOnServer(s -> mobs(s, 12));
			int far = sp.getServer().computeOnServer(s -> mobs(s, 40));
			int red = sp.getServer().computeOnServer(s -> {
				var level = s.overworld();
				BlockPos p = level.players().get(0).blockPosition();
				int n = 0;
				for (BlockPos q : BlockPos.betweenClosed(p.offset(-12, -4, -12), p.offset(12, 2, 12))) if (level.getBlockState(q).is(Blocks.REDSTONE_BLOCK)) n++;
				return n;
			});
			boolean inversionStillWorks = ctx.computeOnClient(mc -> mc.player != null);
			System.out.println("DEBUG strike mobs near before/after: " + before + "/" + after + " far=" + far + " redstone=" + red);
			if (after != 0) throw new AssertionError("mobs not dissolved");
			if (far < 1) throw new AssertionError("far mob should survive");
			if (red < 100) throw new AssertionError("ground not turned to redstone: " + red);

			// Dead Space sky now lives in the sky pass: debris must stay visible in front of it.
			cmd(ctx, "gauntlet space enter");
			ctx.waitTicks(80);
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			for (int i = 0; i < 4; i++) {
				ctx.getInput().lookAt(i * 90, 10);
				ctx.waitTicks(3);
				ctx.takeScreenshot("s1" + i + "_space");
			}
		}
	}

	private static int mobs(net.minecraft.server.MinecraftServer s, int r) {
		var level = s.overworld();
		BlockPos p = level.players().get(0).blockPosition();
		return level.getEntitiesOfClass(Mob.class, new AABB(p).inflate(r), m -> m.isAlive()).size();
	}
}
