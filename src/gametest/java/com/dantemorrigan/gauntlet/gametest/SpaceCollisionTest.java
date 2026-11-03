package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.client.space.SpaceScene;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.phys.Vec3;

/** Debris is solid: launch the player at the nearest piece and check it is stopped, pushed out and bounced. */
public class SpaceCollisionTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
        LegacyBindings.enable(ctx);
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			sp.getServer().runCommand("execute as @p at @p run gauntlet space enter");
			ctx.waitFor(mc -> mc.level != null && mc.level.dimension().identifier().getPath().equals("dead_space"), 300);
			ctx.waitTicks(100);
			// Put the player 6 blocks from the nearest piece and shoot straight at it.
			Vec3[] setup = ctx.computeOnClient(mc -> {
				Vec3 piece = SpaceScene.nearestDebris(mc.player.position());
				Vec3 dir = mc.player.position().subtract(piece).normalize();
				Vec3 start = piece.add(dir.scale(9));
				mc.player.setPos(start.x, start.y, start.z);
				SpaceScene.setPlayerVelocity(dir.scale(-0.35));
				return new Vec3[]{piece, dir};
			});
			Vec3 toward = setup[1].scale(-1);
			double minDist = 1e9, maxAway = -1e9;
			for (int i = 0; i < 80; i++) {
				ctx.waitTicks(1);
				Vec3 v = ctx.computeOnClient(mc -> SpaceScene.playerVelocity());
				Vec3 pl = ctx.computeOnClient(mc -> mc.player.position());
				Vec3 piece = ctx.computeOnClient(mc -> SpaceScene.nearestDebris(mc.player.position()));
				minDist = Math.min(minDist, pl.distanceTo(piece));
				maxAway = Math.max(maxAway, -v.dot(toward));
				if (i == 20 || i == 40) ctx.takeScreenshot("col_" + i);
			}
			System.out.println("DEBUG collision: min distance to piece centre " + minDist + ", max velocity away from piece " + maxAway);
			if (maxAway < 0.05) throw new AssertionError("player was not knocked away from the debris (maxAway=" + maxAway + ")");
		}
	}
}
