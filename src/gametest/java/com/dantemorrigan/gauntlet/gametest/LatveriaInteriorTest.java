package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.doom.Latveria;
import com.dantemorrigan.gauntlet.doom.LatveriaPlan;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Screenshots from inside several houses: each storey of the first villagers' homes. */
public class LatveriaInteriorTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamemode spectator @p");
			server.runCommand("execute as @p run gauntlet latveria enter");
			ctx.waitTicks(260);
			int[] picks = {2, 9, 17, 30, 44, 58};
			for (int n : picks) {
				String cmd = server.computeOnServer(s -> {
					var level = s.getLevel(Latveria.LEVEL);
					BlockPos p = LatveriaPlan.VILLAGERS.get(Math.min(n, LatveriaPlan.VILLAGERS.size() - 1));
					level.getChunk(p);
					for (Direction d : Direction.Plane.HORIZONTAL) {
						BlockPos side = p.above().relative(d, 2).relative(d.getClockWise());
						if (!level.getBlockState(side).isAir() && (level.getBlockState(p.above().relative(d, 2)).isAir() || level.getBlockState(p.above().relative(d, 2)).getBlock() instanceof net.minecraft.world.level.block.DoorBlock)) {
							float yaw = d.toYRot();
							return p.getX() + 0.5 + d.getStepX() * 3 + " " + (p.getY() + 3.2) + " " + (p.getZ() + 0.5 + d.getStepZ() * 3) + " " + yaw + " 15";
						}
					}
					return p.getX() + " " + (p.getY() + 3) + " " + p.getZ() + " 0 0";
				});
				server.runCommand("execute in infinity_gauntlet:latveria run tp @p " + cmd);
				ctx.waitTicks(40);
				sp.getClientLevel().waitForChunksRender();
				ctx.takeScreenshot("interior_" + n + "_ground");
				String[] t = cmd.split(" ");
				String up = t[0] + " " + (Double.parseDouble(t[1]) + 4) + " " + t[2] + " " + t[3] + " " + t[4];
				server.runCommand("execute in infinity_gauntlet:latveria run tp @p " + up);
				ctx.waitTicks(20);
				ctx.takeScreenshot("interior_" + n + "_upper");
			}
		}
	}
}
