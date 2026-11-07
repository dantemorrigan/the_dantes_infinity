package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.boss.ThanosEncounter;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** Thanos also comes to a survival player carrying five stones in the End (where he shoots the dragon). */
public class ThanosEndTriggerTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamemode survival @p");
			server.runCommand("difficulty normal");
			server.runCommand("gauntlet thanos reset");
			GauntletConfig cfg = GauntletConfig.get();
			int delay = cfg.thanosDelayTicks, warn = cfg.thanosWarningSeconds;
			try {
				cfg.thanosDelayTicks = 100;
				cfg.thanosWarningSeconds = 1;
				server.runOnServer(s -> {
					var p = s.getPlayerList().getPlayers().getFirst();
					for (var item : new net.minecraft.world.item.Item[]{ModItems.SPACE_STONE, ModItems.REALITY_STONE, ModItems.MIND_STONE, ModItems.SOUL_STONE, ModItems.TIME_STONE})
						p.getInventory().add(new ItemStack(item));
					p.teleport(new TeleportTransition(s.getLevel(Level.END), new Vec3(0.5, 75, 0.5), Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
				});
				boolean came = false;
				for (int i = 0; i < 80 && !came; i++) {
					ctx.waitTicks(10);
					came = server.computeOnServer(s -> ThanosEncounter.running());
				}
				String where = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().level().dimension().identifier().toString()
						+ " armed-stones=" + ThanosEncounter.carried(s.getPlayerList().getPlayers().getFirst()).size());
				System.out.println("THANOS-END " + (came ? "came" : "DID NOT COME") + " in " + where);
				if (!came) throw new AssertionError("Thanos never came to a survival player with five stones in the End (" + where + ")");
			} finally {
				cfg.thanosDelayTicks = delay;
				cfg.thanosWarningSeconds = warn;
			}
			server.runCommand("gauntlet thanos reset");
		}
	}
}
