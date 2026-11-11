package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.ServerRules;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.power.RealityStrike;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Server rules reach the client (join and config reload), and ability cooldowns of one world do not follow the
 * player into the next world opened by the same client.
 */
public class RuntimeStateTest implements FabricClientGameTest {
	private static int strikeCooldown(TestServerContext server) {
		return server.computeOnServer(s -> RealityStrike.cooldown(s.getPlayerList().getPlayers().getFirst()));
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		int radius = GauntletConfig.get().realityRadius;
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			TestServerContext server = sp.getServer();
			sp.getClientLevel().waitForChunksRender();
			server.runCommand("gamemode survival @p");

			// The client follows the server's rules, not its own file.
			ctx.runOnClient(mc -> ServerRules.realityRadius = -1);
			server.runOnServer(s -> {
				GauntletConfig.get().realityRadius = radius + 5;
				GauntletConfig.get().abilityBlockChanges = false;
				ServerRules.broadcast(s);
			});
			ctx.waitTicks(10);
			if (ServerRules.realityRadius != radius + 5 || ServerRules.abilityBlockChanges)
				throw new AssertionError("Server rules did not reach the client: radius " + ServerRules.realityRadius + ", blocks " + ServerRules.abilityBlockChanges);
			server.runOnServer(s -> {
				GauntletConfig.get().realityRadius = radius;
				GauntletConfig.get().abilityBlockChanges = true;
				ServerRules.broadcast(s);
			});
			ctx.waitTicks(10);
			if (ServerRules.realityRadius != radius || !ServerRules.abilityBlockChanges) throw new AssertionError("Server rules were not restored");

			// A cooldown late in this world's time.
			server.runCommand("execute as @p at @p run gauntlet give_power");
			server.runCommand("execute as @p at @p run gauntlet give_stone " + Stone.REALITY.id);
			ctx.waitTicks(200);
			server.runOnServer(s -> RealityStrike.strike(s.getPlayerList().getPlayers().getFirst()));
			ctx.waitTicks(2);
			if (strikeCooldown(server) <= 0) throw new AssertionError("Reality Strike did not start its cooldown");
		}
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			TestServerContext server = sp.getServer();
			server.runCommand("gamemode survival @p");
			ctx.waitTicks(5);
			int left = strikeCooldown(server);
			if (left > 0) throw new AssertionError("Cooldown of the previous world followed the player: " + left + " ticks");
			if (ServerRules.realityRadius != radius) throw new AssertionError("Rules were not sent on join");
		}
		System.out.println("RUNTIME STATE: rules sync and per-world cooldowns passed");
	}
}
