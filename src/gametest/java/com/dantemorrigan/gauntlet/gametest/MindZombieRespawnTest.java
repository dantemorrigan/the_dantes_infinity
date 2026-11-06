package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.stones.MindCity;
import com.dantemorrigan.gauntlet.stones.MindQuest;
import com.dantemorrigan.gauntlet.stones.MindZombie;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Reality Strike kills count toward the city's quota and do not get replaced by new zombies. */
public class MindZombieRespawnTest implements FabricClientGameTest {
	private static int alive(net.minecraft.server.MinecraftServer s, net.minecraft.core.BlockPos site) {
		return s.overworld().getEntitiesOfClass(MindZombie.class, new AABB(site).inflate(MindCity.R_BLEND + 80.0), MindZombie::isAlive).size();
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("difficulty normal");
			server.runCommand("gamemode survival @p");
			server.runCommand("gauntlet stones reset");
			var site = server.computeOnServer(MindCity::site);
			server.runCommand("tp @p " + (site.getX() + 0.5) + " " + (site.getY() + 10) + " " + (site.getZ() + 22.5) + " 180 18");
			ctx.waitTicks(80);
			server.runCommand("execute as @p run gauntlet give_power");
			server.runCommand("execute as @p run gauntlet give_stone reality");
			sp.getClientLevel().waitForChunksRender();
			int start = server.computeOnServer(s -> alive(s, site));
			System.out.println("MIND-ZOMBIES start=" + start);
			if (start != MindQuest.TOTAL_ZOMBIES) throw new AssertionError("Expected 10 zombies, found " + start);

			// Compare regular deaths with kills from the actual Reality Strike wave.
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().getFirst();
				var level = s.overworld();
				List<MindZombie> list = level.getEntitiesOfClass(MindZombie.class, new AABB(site).inflate(MindCity.R_BLEND + 80.0), MindZombie::isAlive);
				for (MindZombie zombie : list) zombie.setNoAi(true);
				for (int i = 0; i < 3; i++) list.get(i).hurtServer(level, level.damageSources().playerAttack(p), 1000f);
				MindZombie target = list.get(3);
				target.teleportTo(p.getX() + 2, p.getY(), p.getZ());
				com.dantemorrigan.gauntlet.power.RealityStrike.strike(p);
			});
			// Plenty of time for the city's upkeep (it runs every two seconds while someone is near).
			ctx.waitTicks(400);
			int defeated = server.computeOnServer(s -> MindQuest.defeatedCount(s));
			int left = server.computeOnServer(s -> alive(s, site));
			System.out.println("MIND-ZOMBIES defeated=" + defeated + " alive=" + left);
			if (defeated <= 3) throw new AssertionError("Reality Strike did not award quest progress");
			if (left != start - defeated) throw new AssertionError("Defeated zombies came back: " + left + " alive");
		}
	}
}
