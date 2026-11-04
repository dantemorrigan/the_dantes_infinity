package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.ability.Ability;
import com.dantemorrigan.gauntlet.ability.AbilityController;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Planet Destruction: allowed in the Overworld and the Nether, refused in the End and during Thanos's visit. */
public class PlanetRestrictionTest implements FabricClientGameTest {
	private static String reason(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
		return server.computeOnServer(s -> AbilityController.reason(s.getPlayerList().getPlayers().getFirst(), Ability.PLANET));
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamemode survival @p");
			server.runCommand("gauntlet thanos reset");
			server.runOnServer(s -> {
				ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
				ItemStack g = new ItemStack(ModItems.GAUNTLET);
				GauntletState.putStone(g, new ItemStack(ModItems.POWER_STONE));
				p.setItemInHand(InteractionHand.OFF_HAND, g);
			});
			ctx.waitTicks(5);
			String overworld = reason(server);
			server.runCommand("execute in minecraft:the_nether run tp @p 0 120 0");
			ctx.waitTicks(20);
			String nether = reason(server);
			server.runCommand("execute in minecraft:the_end run tp @p 100 70 0");
			ctx.waitTicks(20);
			String end = reason(server);
			server.runCommand("execute in minecraft:the_nether run tp @p 0 120 0");
			ctx.waitTicks(20);
			server.runCommand("execute as @p at @s run gauntlet thanos spawn");
			ctx.waitTicks(10);
			String thanos = reason(server);
			System.out.println("PLANET overworld='" + overworld + "' nether='" + nether + "' end='" + end + "' withThanos='" + thanos + "'");
			if (!overworld.isEmpty()) throw new AssertionError("Planet Destruction should work in the Overworld: " + overworld);
			if (!nether.isEmpty()) throw new AssertionError("Planet Destruction should work in the Nether: " + nether);
			if (!end.equals("planet_end")) throw new AssertionError("Planet Destruction should be refused in the End: '" + end + "'");
			if (!thanos.equals("thanos")) throw new AssertionError("Planet Destruction should be refused while Thanos is here: '" + thanos + "'");
			server.runCommand("gauntlet thanos reset");
		}
	}
}
