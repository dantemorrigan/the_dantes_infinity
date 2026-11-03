package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.power.MindStone;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.phys.AABB;
import org.lwjgl.glfw.GLFW;

/** Mind stone telekinesis (blocks, mob, tear apart) and Soul stone extraction (single, mass). */
public class MindSoulTest implements FabricClientGameTest {
	private static TestSingleplayerContext SP;

	private static void cmd(ClientGameTestContext ctx, String command) {
		SP.getServer().runCommand("execute as @p at @p run " + command);
		ctx.waitTicks(2);
	}

	private static void shiftPress(ClientGameTestContext ctx, int key) {
		ctx.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT);
		ctx.waitTicks(2);
		ctx.getInput().pressKey(key);
		ctx.waitTicks(2);
		ctx.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT);
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			SP = sp;
			sp.getClientLevel().waitForChunksRender();
			cmd(ctx, "time set noon");
			cmd(ctx, "gamerule advance_time false");
			cmd(ctx, "gamemode creative @p");
			cmd(ctx, "gauntlet give_power");
			cmd(ctx, "gauntlet give_stone mind");
			cmd(ctx, "gauntlet give_stone soul");
			ctx.waitTicks(20);
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

			// ---- Mind: tear out a chunk of ground, hold, throw
			ctx.getInput().lookAt(0, 25);
			ctx.waitTicks(3);
			ctx.takeScreenshot("m00_before");
			ctx.getInput().pressKey(GLFW.GLFW_KEY_Z);
			ctx.waitTicks(4);
			ctx.takeScreenshot("m01_lift");
			ctx.waitTicks(16);
			ctx.getInput().lookAt(0, -5);
			ctx.waitTicks(6);
			ctx.takeScreenshot("m02_hold");
			boolean holding = sp.getServer().computeOnServer(s -> MindStone.holding(s.getPlayerList().getPlayers().getFirst()));
			if (!holding) throw new AssertionError("blocks not held");
			ctx.getInput().pressKey(GLFW.GLFW_KEY_Z);
			ctx.waitTicks(5);
			ctx.takeScreenshot("m03_flight");
			ctx.waitTicks(25);
			ctx.takeScreenshot("m04_impact");
			int falling = sp.getServer().computeOnServer(s -> s.overworld().getEntitiesOfClass(FallingBlockEntity.class, new AABB(s.overworld().players().getFirst().blockPosition()).inflate(80)).size());
			System.out.println("DEBUG mind falling blocks after impact: " + falling);
			ctx.waitTicks(60);
			ctx.takeScreenshot("m05_debris");

			// ---- Mind: lift a cow, tear it apart
			cmd(ctx, "summon minecraft:cow ~ ~ ~6 {NoAI:1b}");
			ctx.getInput().lookAt(0, 12);
			ctx.waitTicks(5);
			ctx.getInput().pressKey(GLFW.GLFW_KEY_Z);
			ctx.waitTicks(20);
			ctx.takeScreenshot("m10_mob_hold");
			boolean mobHeld = sp.getServer().computeOnServer(s -> MindStone.holding(s.getPlayerList().getPlayers().getFirst()));
			if (!mobHeld) throw new AssertionError("mob not held");
			shiftPress(ctx, GLFW.GLFW_KEY_Z);
			ctx.waitTicks(12);
			ctx.takeScreenshot("m11_tearing");
			ctx.waitTicks(14);
			ctx.takeScreenshot("m12_rupture");
			ctx.waitTicks(8);
			ctx.takeScreenshot("m13_pieces");
			int cows = sp.getServer().computeOnServer(s -> mobs(s, 20));
			if (cows != 0) throw new AssertionError("cow not torn apart: " + cows);

			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			// ---- Soul: single extraction
			cmd(ctx, "summon minecraft:zombie ~ ~ ~7 {NoAI:1b}");
			ctx.getInput().lookAt(0, 8);
			ctx.waitTicks(5);
			ctx.getInput().pressKey(GLFW.GLFW_KEY_O);
			ctx.waitTicks(14);
			ctx.takeScreenshot("s00_extract");
			ctx.waitTicks(22);
			ctx.takeScreenshot("s01_flight");
			ctx.waitTicks(24);
			ctx.takeScreenshot("s02_absorbed");
			double maxHp = sp.getServer().computeOnServer(s -> (double) s.getPlayerList().getPlayers().getFirst().getMaxHealth());
			int zombies = sp.getServer().computeOnServer(s -> mobs(s, 20));
			System.out.println("DEBUG soul single: maxHp=" + maxHp + " zombies=" + zombies);
			if (zombies != 0 || maxHp <= 20) throw new AssertionError("single extraction failed: hp=" + maxHp + " zombies=" + zombies);

			// ---- Soul: mass extraction
			for (int i = 0; i < 6; i++) {
				double a = i * Math.PI / 3;
				cmd(ctx, String.format(java.util.Locale.ROOT, "summon minecraft:zombie ~%.1f ~ ~%.1f {NoAI:1b}", Math.cos(a) * 9, Math.sin(a) * 9));
			}
			ctx.getInput().lookAt(0, 30);
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> mc.player.setXRot(40));
			shiftPress(ctx, GLFW.GLFW_KEY_O);
			ctx.waitTicks(8);
			ctx.takeScreenshot("s10_wave");
			ctx.waitTicks(16);
			ctx.takeScreenshot("s11_souls");
			ctx.waitTicks(20);
			ctx.takeScreenshot("s12_flight");
			ctx.waitTicks(40);
			ctx.takeScreenshot("s13_after");
			int left = sp.getServer().computeOnServer(s -> mobs(s, 20));
			double hp2 = sp.getServer().computeOnServer(s -> (double) s.getPlayerList().getPlayers().getFirst().getMaxHealth());
			System.out.println("DEBUG soul mass: left=" + left + " maxHp=" + hp2);
			if (left != 0) throw new AssertionError("mass extraction left " + left);
		}
	}

	private static int mobs(net.minecraft.server.MinecraftServer s, int r) {
		var level = s.overworld();
		BlockPos p = level.players().getFirst().blockPosition();
		return level.getEntitiesOfClass(Mob.class, new AABB(p).inflate(r), m -> m.isAlive()).size();
	}
}
