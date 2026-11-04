package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.stones.MindCity;
import com.dantemorrigan.gauntlet.stones.MindQuest;
import com.dantemorrigan.gauntlet.stones.MindZombie;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Mind Zombies that were defeated (killed, or made to vanish) never come back, and stragglers do not trigger extra ones. */
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
			server.runCommand("gamemode creative @p");
			server.runCommand("gauntlet stones reset");
			var site = server.computeOnServer(MindCity::site);
			server.runCommand("tp @p " + (site.getX() + 0.5) + " " + (site.getY() + 10) + " " + (site.getZ() + 22.5) + " 180 18");
			ctx.waitTicks(80);
			sp.getClientLevel().waitForChunksRender();
			int start = server.computeOnServer(s -> alive(s, site));
			System.out.println("MIND-ZOMBIES start=" + start);
			if (start != MindQuest.TOTAL_ZOMBIES) throw new AssertionError("Expected 10 zombies, found " + start);

			// Three die, two vanish (like the Reality Stone's wave): five are defeated.
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().getFirst();
				var level = s.overworld();
				List<MindZombie> list = level.getEntitiesOfClass(MindZombie.class, new AABB(site).inflate(MindCity.R_BLEND + 80.0), MindZombie::isAlive);
				for (int i = 0; i < 3; i++) list.get(i).hurtServer(level, level.damageSources().playerAttack(p), 1000f);
				for (int i = 3; i < 5; i++) list.get(i).discard();
			});
			// Plenty of time for the city's upkeep (it runs every two seconds while someone is near).
			ctx.waitTicks(400);
			int defeated = server.computeOnServer(s -> MindQuest.defeatedCount(s));
			int left = server.computeOnServer(s -> alive(s, site));
			System.out.println("MIND-ZOMBIES defeated=" + defeated + " alive=" + left);
			if (defeated != 5) throw new AssertionError("Five were defeated, the quest counted " + defeated);
			if (left != 5) throw new AssertionError("Defeated zombies came back: " + left + " alive, expected 5");

			// Chasing zombies that wander off must not be 'replaced' either.
			server.runOnServer(s -> {
				var level = s.overworld();
				List<MindZombie> list = level.getEntitiesOfClass(MindZombie.class, new AABB(site).inflate(MindCity.R_BLEND + 80.0), MindZombie::isAlive);
				for (int i = 0; i < 2; i++) list.get(i).teleportTo(site.getX() + 300.5, list.get(i).getY(), site.getZ() + 0.5);
			});
			ctx.waitTicks(300);
			int total = server.computeOnServer(s -> alive(s, site));
			System.out.println("MIND-ZOMBIES after two wandered off, alive=" + total);
			if (total > 5) throw new AssertionError("Extra zombies appeared while some wandered off: " + total);
		}
	}
}
