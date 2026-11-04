package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.boss.ThanosEncounter;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.item.ItemStack;

/** Thanos does not come the moment the fifth stone is gathered: the player gets a breather first. */
public class ThanosTriggerTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamemode survival @p");
			server.runCommand("gauntlet thanos reset");
			GauntletConfig cfg = GauntletConfig.get();
			int delay = cfg.thanosDelayTicks, warn = cfg.thanosWarningSeconds;
			try {
				cfg.thanosDelayTicks = 400;
				cfg.thanosWarningSeconds = 1;
				// Four stones: nothing happens, however long we wait.
				server.runOnServer(s -> {
					var p = s.getPlayerList().getPlayers().getFirst();
					for (var item : new net.minecraft.world.item.Item[]{ModItems.SPACE_STONE, ModItems.REALITY_STONE, ModItems.MIND_STONE, ModItems.SOUL_STONE})
						p.getInventory().add(new ItemStack(item));
				});
				ctx.waitTicks(500);
				if (server.computeOnServer(s -> ThanosEncounter.running())) throw new AssertionError("Thanos came with only four stones");
				// The fifth stone: a quiet breather, then he comes.
				server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().getInventory().add(new ItemStack(ModItems.TIME_STONE)));
				ctx.waitTicks(300);
				if (server.computeOnServer(s -> ThanosEncounter.running())) throw new AssertionError("Thanos came during the breather");
				boolean came = false;
				for (int i = 0; i < 40 && !came; i++) {
					ctx.waitTicks(10);
					came = server.computeOnServer(s -> ThanosEncounter.running());
				}
				if (!came) throw new AssertionError("Thanos never came after the breather");
				System.out.println("THANOS-TRIGGER ok: breather respected, then he came");
			} finally {
				cfg.thanosDelayTicks = delay;
				cfg.thanosWarningSeconds = warn;
			}
			server.runCommand("gauntlet thanos reset");
		}
	}
}
