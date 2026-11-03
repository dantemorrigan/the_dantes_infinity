package com.dantemorrigan.gauntlet.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import org.lwjgl.glfw.GLFW;

/** Reality Inversion: hold G, the world folds/twists, mobs -> butterflies, arrows -> petals; release restores all. */
public class RealityStoneTest implements FabricClientGameTest {
	private static TestSingleplayerContext SP;

	private static void cmd(ClientGameTestContext ctx, String command) {
		SP.getServer().runCommand("execute as @p at @p run " + command);
		ctx.waitTicks(2);
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
        LegacyBindings.enable(ctx);
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			SP = sp;
			sp.getClientLevel().waitForChunksRender();
			cmd(ctx, "time set noon");
			cmd(ctx, "gamerule advance_time false");
			cmd(ctx, "gauntlet give_power");
			cmd(ctx, "gauntlet give_stone reality");
			// A little house, a pond and some mobs.
			cmd(ctx, "fill ~4 ~ ~6 ~9 ~4 ~11 minecraft:oak_planks hollow");
			cmd(ctx, "fill ~5 ~1 ~6 ~8 ~2 ~6 minecraft:glass");
			cmd(ctx, "fill ~-9 ~-1 ~4 ~-4 ~-1 ~9 minecraft:water");
			cmd(ctx, "summon minecraft:cow ~-3 ~ ~10");
			cmd(ctx, "summon minecraft:sheep ~2 ~ ~12");
			cmd(ctx, "summon minecraft:zombie ~-6 ~ ~-6 {NoAI:1b}");
			ctx.waitTicks(30);
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			ctx.getInput().lookAt(10, 30);
			ctx.waitTicks(3);
			ctx.takeScreenshot("r00_before");
			int visibleBefore = sp.getServer().computeOnServer(s -> visibleMobs(s));

			ctx.getInput().holdKey(GLFW.GLFW_KEY_G);
			ctx.waitTicks(8);
			ctx.takeScreenshot("r01_charge");
			ctx.waitTicks(6);
			ctx.takeScreenshot("r02_cracks");
			ctx.waitTicks(10);
			ctx.takeScreenshot("r03_rewrite");
			// Arrows flying into the field.
			for (int i = 0; i < 4; i++) cmd(ctx, "summon minecraft:arrow ~" + (i * 2 - 3) + " ~6 ~-8 {Motion:[0.0,0.1,1.2]}");
			ctx.waitTicks(4);
			ctx.takeScreenshot("r04_petals");
			ctx.waitTicks(20);
			ctx.takeScreenshot("r05_folded");
			int visibleDuring = sp.getServer().computeOnServer(s -> visibleMobs(s));
			// Move the hand (gaze): the parting turns with it.
			ctx.getInput().lookAt(80, 30);
			ctx.waitTicks(25);
			ctx.takeScreenshot("r06_hand_moved");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			ctx.getInput().lookAt(80, 5);
			ctx.waitTicks(3);
			ctx.takeScreenshot("r07_first_person_walls");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			ctx.getInput().lookAt(10, 30);
			ctx.waitTicks(15);
			ctx.takeScreenshot("r08_back_view");

			ctx.getInput().releaseKey(GLFW.GLFW_KEY_G);
			ctx.waitTicks(6);
			ctx.takeScreenshot("r09_release");
			ctx.waitTicks(25);
			ctx.takeScreenshot("r10_restored");
			int visibleAfter = sp.getServer().computeOnServer(s -> visibleMobs(s));
			boolean houseOk = sp.getServer().computeOnServer(s -> {
				BlockPos p = s.overworld().players().get(0).blockPosition();
				return s.overworld().getBlockState(p.offset(4, 0, 6)).isSolidRender();
			});
			boolean clientOk = ctx.computeOnClient(mc -> {
				BlockPos p = mc.player.blockPosition();
				return !mc.level.getBlockState(p.offset(4, 0, 6)).isAir() && !mc.level.getBlockState(p.offset(6, -1, 0)).isAir();
			});
			System.out.println("DEBUG visible mobs before/during/after: " + visibleBefore + "/" + visibleDuring + "/" + visibleAfter
					+ " serverHouse=" + houseOk + " clientBlocksBack=" + clientOk);
			if (visibleAfter != visibleBefore) throw new AssertionError("mobs not restored");
			if (!clientOk) throw new AssertionError("client blocks not restored");
		}
	}

	private static int visibleMobs(net.minecraft.server.MinecraftServer s) {
		var level = s.overworld();
		BlockPos p = level.players().get(0).blockPosition();
		return level.getEntitiesOfClass(Mob.class, new AABB(p).inflate(30), m -> !m.isInvisible()).size();
	}
}
