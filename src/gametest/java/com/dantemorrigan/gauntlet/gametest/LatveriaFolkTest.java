package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.doom.Latveria;
import com.dantemorrigan.gauntlet.doom.LatveriaFolk;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;

/** Latveria's people in Latveria's dress: an adult, a librarian over it, and a child, up close; and the townsfolk spawned by the city. */
public class LatveriaFolkTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamemode spectator @p");
			server.runCommand("execute as @p run gauntlet latveria enter");
			ctx.waitTicks(260);
			server.runCommand("execute in infinity_gauntlet:latveria run summon minecraft:villager -1.5 69 40.5 {NoAI:1b,Rotation:[0f,0f]}");
			server.runCommand("execute in infinity_gauntlet:latveria run summon minecraft:villager 0.5 69 40.5 {NoAI:1b,Rotation:[0f,0f],VillagerData:{profession:\"minecraft:librarian\",level:2}}");
			server.runCommand("execute in infinity_gauntlet:latveria run summon minecraft:villager 2.5 69 40.5 {NoAI:1b,Age:-24000,Rotation:[0f,0f]}");
			server.runCommand("execute in infinity_gauntlet:latveria run tp @p 0.5 70.4 44.5 180 8");
			ctx.waitTicks(40);
			ctx.takeScreenshot("folk_front");
			server.runCommand("execute in infinity_gauntlet:latveria run tp @p -2.5 70.4 36.5 330 8");
			ctx.waitTicks(20);
			ctx.takeScreenshot("folk_back");
			long dressed = server.computeOnServer(s -> s.getLevel(Latveria.LEVEL).getEntitiesOfClass(Villager.class, new AABB(-200, 0, -200, 200, 200, 200))
					.stream().filter(v -> v.getVillagerData().type().is(LatveriaFolk.TYPE)).count());
			long all = server.computeOnServer(s -> (long) s.getLevel(Latveria.LEVEL).getEntitiesOfClass(Villager.class, new AABB(-200, 0, -200, 200, 200, 200)).size());
			System.out.println("FOLK dressed " + dressed + " of " + all);
			if (dressed != all || all < 3) throw new AssertionError("Every villager in Latveria wears its dress: " + dressed + " of " + all);
		}
	}
}
