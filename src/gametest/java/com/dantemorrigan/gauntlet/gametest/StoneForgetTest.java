package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.stones.StoneKeeper;
import com.dantemorrigan.gauntlet.stones.StoneRegistry;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.item.ItemStack;

/** Smoke: a stone deleted in creative is forgotten by the world. */
public class StoneForgetTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			var server = sp.getServer();
			server.runCommand("gauntlet stones reset");
			server.runOnServer(s -> {
				StoneRegistry.markReleased(s, Stone.REALITY);
				StoneKeeper.creativeReplaced(s.getPlayerList().getPlayers().getFirst(), new ItemStack(ModItems.REALITY_STONE), ItemStack.EMPTY);
			});
			if (server.computeOnServer(s -> StoneRegistry.found(s, Stone.REALITY) || StoneRegistry.released(s, Stone.REALITY))) throw new AssertionError("Stone not forgotten");
			server.runOnServer(s -> {
				StoneRegistry.markReleased(s, Stone.SOUL);
				StoneKeeper.creativeReplaced(s.getPlayerList().getPlayers().getFirst(), new ItemStack(ModItems.SOUL_STONE), new ItemStack(ModItems.SOUL_STONE));
			});
			if (!server.computeOnServer(s -> StoneRegistry.released(s, Stone.SOUL))) throw new AssertionError("Moving a stone must not forget it");
		}
	}
}
