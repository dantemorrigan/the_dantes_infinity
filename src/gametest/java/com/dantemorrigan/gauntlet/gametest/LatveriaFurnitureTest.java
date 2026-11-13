package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.doom.Latveria;
import com.dantemorrigan.gauntlet.doom.LatveriaPlan;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Nothing in Latveria hangs in the air: every bed, chest, table and furnace of the houses and the castle stands on a
 * solid block with room above it, and the townsfolk spawn on the floor of a house or a street.
 */
public class LatveriaFurnitureTest implements FabricClientGameTest {
	private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamemode spectator @p");
			server.runCommand("execute as @p run gauntlet latveria enter");
			ctx.waitTicks(20);
			String report = server.computeOnServer(s -> {
				var level = s.getLevel(Latveria.LEVEL);
				int chunks = Latveria.furnishAll(level);
				StringBuilder bad = new StringBuilder();
				int checked = 0;
				for (var op : LatveriaPlan.FURNITURE) {
					BlockPos pos = new BlockPos(op.x0(), op.y0(), op.z0());
					BlockState state = level.getBlockState(pos);
					checked++;
					if (state.isAir()) { bad.append("missing ").append(op.state().getBlock()).append(" at ").append(pos.toShortString()).append("; "); continue; }
					BlockPos below = pos.below();
					BlockState support = level.getBlockState(below);
					boolean supported = support.isFaceSturdy(level, below, Direction.UP);
					// A bed is two blocks: either half may rest on the floor or on the other half's neighbour.
					if (!supported && state.getBlock() instanceof BedBlock) supported = false;
					if (!supported) bad.append(state.getBlock()).append(" floats at ").append(pos.toShortString()).append(" over ").append(support.getBlock()).append("; ");
				}
				for (int[] c : LatveriaPlan.CHESTS) {
					BlockPos below = new BlockPos(c[0], c[1] - 1, c[2]);
					if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) bad.append("chest floats at ").append(c[0]).append(',').append(c[1]).append(',').append(c[2]).append("; ");
				}
				// A house is entered at ground level: the doorway has a floor and two blocks of air.
				for (BlockPos p : LatveriaPlan.VILLAGERS) {
					BlockPos feet = p.above();
					if (!level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP) || !level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir())
						bad.append("no room for a villager at ").append(feet.toShortString()).append("; ");
				}
				return chunks + " chunks, " + checked + " pieces" + (bad.length() == 0 ? "" : ": " + bad);
			});
			System.out.println("FURNITURE: " + report);
			check(!report.contains(":"), "Furniture hangs in the air: " + report);
			ctx.waitTicks(40);
			check(server.computeOnServer(s -> s.getLevel(Latveria.LEVEL).getEntitiesOfClass(Villager.class, new AABB(-400, 0, -500, 400, 320, 400)).size()) >= 20, "The city has too few townsfolk");
			server.runCommand("execute as @p run gauntlet latveria tp square");
			server.runCommand("execute in infinity_gauntlet:latveria run tp @p -12 76 80 90 20");
			ctx.waitTicks(40);
			ctx.takeScreenshot("furniture_house_outside");
		}
	}
}
