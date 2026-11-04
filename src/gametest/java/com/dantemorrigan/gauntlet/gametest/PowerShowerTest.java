package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.ModBlocks;
import com.dantemorrigan.gauntlet.stones.PowerStoneFall;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;

/**
 * Smoke: Power meteor showers come by themselves on the schedule, the purple meteorite appears, the sky turns red;
 * a meteorite the player ignored loses its Orb when the next shower starts (no second Orb), until the Orb is taken.
 */
public class PowerShowerTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("time set 3000");
			server.runCommand("gamemode creative @p");
			server.runCommand("gauntlet stones reset");
			// A fast schedule (the real one is 20 + 20..50 minutes); nobody runs a command from here on.
			GauntletConfig cfg = GauntletConfig.get();
			int first = cfg.powerShowerFirstMinutes, min = cfg.powerShowerMinMinutes, max = cfg.powerShowerMaxMinutes, secs = cfg.powerShowerSeconds;
			try {
				cfg.powerShowerFirstMinutes = 0;
				cfg.powerShowerMinMinutes = 0;
				cfg.powerShowerMaxMinutes = 0;
				cfg.powerShowerSeconds = 5;

				boolean started = false;
				for (int i = 0; i < 40 && !started; i++) {
					ctx.waitTicks(10);
					started = server.computeOnServer(s -> PowerStoneFall.active());
				}
				if (!started) throw new AssertionError("The first shower did not start by itself");
				ctx.waitTicks(100);
				ctx.takeScreenshot("shower_red_sky");

				BlockPos first1 = waitForSite(ctx, server, null);
				if (first1 == null) throw new AssertionError("The first shower left no meteorite with the Orb");
				boolean orb1 = server.computeOnServer(s -> s.overworld().getBlockState(first1).is(ModBlocks.POWER_ORB));
				if (!orb1) throw new AssertionError("The first meteorite has no Orb");
				System.out.println("SHOWER first meteorite " + first1.toShortString() + " orb=" + orb1);

				// The player ignores it. The next shower comes by itself; the old Orb must be gone by then.
				BlockPos second = waitForSite(ctx, server, first1);
				if (second == null) throw new AssertionError("A second shower did not come by itself");
				boolean oldOrb = server.computeOnServer(s -> s.overworld().getBlockState(first1).is(ModBlocks.POWER_ORB));
				boolean newOrb = server.computeOnServer(s -> s.overworld().getBlockState(second).is(ModBlocks.POWER_ORB));
				System.out.println("SHOWER second meteorite " + second.toShortString() + " newOrb=" + newOrb + " oldOrb=" + oldOrb);
				if (oldOrb) throw new AssertionError("The missed meteorite still has its Orb: it can be duplicated");
				if (!newOrb) throw new AssertionError("The new meteorite has no Orb");
			} finally {
				cfg.powerShowerFirstMinutes = first;
				cfg.powerShowerMinMinutes = min;
				cfg.powerShowerMaxMinutes = max;
				cfg.powerShowerSeconds = secs;
			}
		}
	}

	/** Waits (up to ~40 s) for a meteorite site different from {@code not}. */
	private static BlockPos waitForSite(ClientGameTestContext ctx, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, BlockPos not) {
		for (int i = 0; i < 80; i++) {
			ctx.waitTicks(10);
			BlockPos site = server.computeOnServer(PowerStoneFall::site);
			if (site != null && !site.equals(not)) return site;
		}
		return null;
	}
}
