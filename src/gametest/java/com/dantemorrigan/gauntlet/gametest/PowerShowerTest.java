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
	/** Every meteorite site the world announced, in order (a server-side listener, so no tick is missed). */
	private static final java.util.List<BlockPos> SITES = new java.util.concurrent.CopyOnWriteArrayList<>();
	/** Whether the Orb block stood there the moment the meteorite landed. */
	private static final java.util.List<Boolean> ORB_AT_LANDING = new java.util.concurrent.CopyOnWriteArrayList<>();
	private static boolean listening;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			if (!listening) {
				listening = true;
				net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(srv -> {
					BlockPos site = PowerStoneFall.site(srv);
					if (site != null && (SITES.isEmpty() || !SITES.getLast().equals(site))) {
						ORB_AT_LANDING.add(srv.overworld().getBlockState(site).is(ModBlocks.POWER_ORB));
						SITES.add(site);
					}
				});
			}
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

				SITES.clear();
				ORB_AT_LANDING.clear();
				boolean started = false;
				for (int i = 0; i < 40 && !started; i++) {
					ctx.waitTicks(10);
					started = server.computeOnServer(s -> PowerStoneFall.active());
				}
				if (!started) throw new AssertionError("The first shower did not start by itself");
				ctx.waitTicks(100);
				ctx.takeScreenshot("shower_red_sky");

				// Nobody touches anything: showers keep coming by themselves. Wait for three meteorites.
				for (int i = 0; i < 400 && SITES.size() < 3; i++) ctx.waitTicks(10);
				if (SITES.size() < 2) throw new AssertionError("Only " + SITES.size() + " meteorite(s) came by themselves");
				System.out.println("SHOWER meteorites seen: " + SITES.size());
				BlockPos current = server.computeOnServer(PowerStoneFall::site);
				for (int i = 0; i < SITES.size(); i++) {
					BlockPos site = SITES.get(i);
					boolean orbNow = server.computeOnServer(s -> s.overworld().getBlockState(site).is(ModBlocks.POWER_ORB));
					System.out.println("SHOWER meteorite " + i + " " + site.toShortString() + " orbAtLanding=" + ORB_AT_LANDING.get(i) + " orbNow=" + orbNow);
					if (!ORB_AT_LANDING.get(i)) throw new AssertionError("Meteorite " + i + " landed without an Orb");
					// Only the meteorite the world still points at may keep its Orb; every missed one must be empty.
					boolean live = site.equals(current);
					if (!live && orbNow) throw new AssertionError("Meteorite " + i + " was missed but still has its Orb: it can be duplicated");
					if (live && !orbNow) throw new AssertionError("The current meteorite has lost its Orb");
				}
			} finally {
				cfg.powerShowerFirstMinutes = first;
				cfg.powerShowerMinMinutes = min;
				cfg.powerShowerMaxMinutes = max;
				cfg.powerShowerSeconds = secs;
			}
		}
	}
}
