package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.ModBlocks;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.stones.StoneRegistry;
import com.dantemorrigan.gauntlet.stones.TimeClockBlock;
import com.dantemorrigan.gauntlet.stones.TimeQuest;
import com.dantemorrigan.gauntlet.stones.TimeTemple;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Smoke: Temple of Time built, map unrolled, three dimensional gears inserted (duplicate rejected), Time Stone granted. */
public class TimeQuestTest implements FabricClientGameTest {
	private static net.minecraft.server.level.ServerPlayer player(net.minecraft.server.MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("time set 6000");
			server.runCommand("gamemode survival @p");
			server.runCommand("gauntlet stones reset");

			// Map unrolls into a filled map in hand.
			server.runOnServer(s -> {
				var p = player(s);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.QUEST_TIME_TEMPLE_MAP));
				var res = p.getMainHandItem().use(p.level(), p, InteractionHand.MAIN_HAND);
				if (res instanceof net.minecraft.world.InteractionResult.Success suc && suc.heldItemTransformedTo() != null) {
					p.setItemInHand(InteractionHand.MAIN_HAND, suc.heldItemTransformedTo());
				}
			});
			if (!server.computeOnServer(s -> player(s).getMainHandItem().is(Items.FILLED_MAP))) {
				throw new AssertionError("Time Temple map did not unroll into a filled map");
			}

			// Teleport to the natural Temple of Time site (without forceBuild) and verify chunks shape and build the temple.
			var site = server.computeOnServer(TimeTemple::site);
			server.runCommand("tp @p " + (site.getX() + 0.5) + " " + (site.getY() + 8) + " " + (site.getZ() + 26.5) + " 180 8");
			ctx.waitTicks(45);
			sp.getClientLevel().waitForChunksRender();
			if (!server.computeOnServer(TimeTemple::built)) {
				throw new AssertionError("Temple of Time was not built naturally on chunk load");
			}
			ctx.takeScreenshot("time_temple");

			Vec3 stand = TimeTemple.sanctumStand(site);
			var clockPos = TimeTemple.clockPos(site);
			server.runCommand("tp @p " + stand.x + " " + stand.y + " " + stand.z + " 180 0");
			ctx.waitTicks(15);
			sp.getClientLevel().waitForChunksRender();
			ctx.takeScreenshot("time_clock_broken");

			// Insert Overworld gear -> gears = 1; duplicate Overworld gear must be rejected.
			server.runOnServer(s -> {
				var p = player(s);
				var level = s.overworld();
				var g1 = new ItemStack(ModItems.QUEST_TIME_GEAR_OVERWORLD, 2);
				if (!TimeQuest.insertGear(p, clockPos, level.getBlockState(clockPos), g1)) {
					throw new AssertionError("First gear should be accepted");
				}
				if (TimeQuest.insertGear(p, clockPos, level.getBlockState(clockPos), g1)) {
					throw new AssertionError("Duplicate Overworld gear must be rejected");
				}
				if (level.getBlockState(clockPos).getValue(TimeClockBlock.GEARS) != 1) {
					throw new AssertionError("Clock state should be 1 gear");
				}
				TimeQuest.insertGear(p, clockPos, level.getBlockState(clockPos), new ItemStack(ModItems.QUEST_TIME_GEAR_NETHER));
				if (level.getBlockState(clockPos).getValue(TimeClockBlock.GEARS) != 2) {
					throw new AssertionError("Clock state should be 2 gears");
				}
				TimeQuest.insertGear(p, clockPos, level.getBlockState(clockPos), new ItemStack(ModItems.QUEST_TIME_GEAR_END));
				if (!level.getBlockState(clockPos).is(ModBlocks.QUEST_TIME_CLOCK)
						|| level.getBlockState(clockPos).getValue(TimeClockBlock.GEARS) != 3) {
					throw new AssertionError("Clock state should be 3 gears");
				}
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("time_clock_started");
			if (!server.computeOnServer(s -> StoneRegistry.released(s, Stone.TIME))) {
				throw new AssertionError("Time Stone was not marked released");
			}
			if (!server.computeOnServer(s -> player(s).getInventory().contains(st -> st.is(ModItems.TIME_STONE)))) {
				throw new AssertionError("Player did not receive the Time Stone");
			}
		}
	}
}
