package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.stones.MindCity;
import com.dantemorrigan.gauntlet.stones.MindQuest;
import com.dantemorrigan.gauntlet.stones.MindZombie;
import com.dantemorrigan.gauntlet.stones.StoneRegistry;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Smoke: Ruined City built, map unrolled, 10 sun-immune Mind Zombies defeated, essence converges, Mind Stone in player's hand. */
public class MindQuestTest implements FabricClientGameTest {
	private static net.minecraft.server.level.ServerPlayer player(net.minecraft.server.MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("time set 6000");
			server.runCommand("gamemode creative @p");
			server.runCommand("gauntlet stones reset");
			server.runCommand("execute as @p run gauntlet mind_quest city_here");
			if (!server.computeOnServer(MindCity::built)) throw new AssertionError("Ruined City not built");

			// Map unrolls into a filled map in hand.
			server.runOnServer(s -> {
				var p = player(s);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.QUEST_MIND_CITY_MAP));
				var res = p.getMainHandItem().use(p.level(), p, InteractionHand.MAIN_HAND);
				if (res instanceof net.minecraft.world.InteractionResult.Success suc && suc.heldItemTransformedTo() != null) {
					p.setItemInHand(InteractionHand.MAIN_HAND, suc.heldItemTransformedTo());
				}
			});
			if (!server.computeOnServer(s -> player(s).getMainHandItem().is(Items.FILLED_MAP))) {
				throw new AssertionError("Ruined City map did not unroll into a filled map");
			}

			var site = server.computeOnServer(MindCity::site);
			server.runCommand("tp @p " + (site.getX() + 0.5) + " " + (site.getY() + 10) + " " + (site.getZ() + 22.5) + " 180 18");
			ctx.waitTicks(20);
			sp.getClientLevel().waitForChunksRender();
			ctx.takeScreenshot("mind_ruined_city");

			// Verify 10 Mind Zombies are present in the city and do not burn in midday sunlight.
			int zombieCount = server.computeOnServer(s -> {
				var level = s.overworld();
				List<MindZombie> list = level.getEntitiesOfClass(MindZombie.class, new AABB(site).inflate(MindCity.R_BLEND + 24.0), MindZombie::isAlive);
				for (MindZombie z : list) {
					if (z.isOnFire()) throw new AssertionError("Mind Zombie should not burn in sunlight");
				}
				return list.size();
			});
			if (zombieCount != MindQuest.TOTAL_ZOMBIES) {
				throw new AssertionError("Expected 10 Mind Zombies in the Ruined City, found " + zombieCount);
			}

			Vec3 stand = MindCity.plazaStand(site);
			server.runCommand("tp @p " + stand.x + " " + stand.y + " " + stand.z + " 180 4");
			ctx.waitTicks(16);
			sp.getClientLevel().waitForChunksRender();
			ctx.takeScreenshot("mind_zombies");

			// Defeat the first 9 Mind Zombies; verify progress is 9/10 and stone is not yet released.
			server.runOnServer(s -> {
				var p = player(s);
				var level = s.overworld();
				List<MindZombie> list = level.getEntitiesOfClass(MindZombie.class, new AABB(site).inflate(MindCity.R_BLEND + 24.0), MindZombie::isAlive);
				for (int i = 0; i < 9; i++) {
					MindZombie z = list.get(i);
					z.hurtServer(level, level.damageSources().playerAttack(p), 1000.0f);
				}
			});
			if (server.computeOnServer(MindQuest::defeatedCount) != 9) {
				throw new AssertionError("Expected 9 defeated Mind Zombies");
			}
			if (server.computeOnServer(s -> StoneRegistry.released(s, Stone.MIND))) {
				throw new AssertionError("Mind Stone should not be released before the 10th zombie is defeated");
			}

			// Defeat the 10th (last) Mind Zombie: triggers the essence convergence animation in front of the player.
			server.runOnServer(s -> {
				var p = player(s);
				var level = s.overworld();
				List<MindZombie> list = level.getEntitiesOfClass(MindZombie.class, new AABB(site).inflate(MindCity.R_BLEND + 24.0), MindZombie::isAlive);
				if (list.size() != 1) throw new AssertionError("Expected 1 remaining Mind Zombie, found " + list.size());
				list.getFirst().hurtServer(level, level.damageSources().playerAttack(p), 1000.0f);
			});

			ctx.waitTicks(14);
			ctx.takeScreenshot("mind_convergence");

			ctx.waitTicks(MindQuest.CONVERGENCE_TICKS);
			ctx.takeScreenshot("mind_stone_in_hand");

			if (!server.computeOnServer(s -> StoneRegistry.released(s, Stone.MIND))) {
				throw new AssertionError("Mind Stone was not marked released after convergence");
			}
			if (!server.computeOnServer(s -> player(s).getMainHandItem().is(ModItems.MIND_STONE))) {
				throw new AssertionError("Mind Stone did not appear in the player's main hand");
			}
		}
	}
}
