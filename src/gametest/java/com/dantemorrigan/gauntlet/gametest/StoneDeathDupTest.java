package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.boss.Thanos;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;

/** Regression: stones kept on death come back once, not twice (mob kill, Thanos kill, two deaths in a row). */
public class StoneDeathDupTest implements FabricClientGameTest {
	private static ServerPlayer player(MinecraftServer s) { return s.getPlayerList().getPlayers().getFirst(); }

	private static int count(MinecraftServer s) {
		var inv = player(s).getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			n += com.dantemorrigan.gauntlet.stones.StoneKeeper.stonesIn(st).size() * Math.max(1, st.getCount());
			if (st.is(ModItems.POWER_ORB)) n += st.getCount();
		}
		return n;
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		{
			var sp = ctx.worldBuilder().create();
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamemode survival @p");
			server.runCommand("gauntlet thanos reset");
			server.runOnServer(s -> {
				var p = player(s);
				p.getInventory().add(new ItemStack(ModItems.SPACE_STONE));
				p.getInventory().add(new ItemStack(ModItems.REALITY_STONE));
				ItemStack g = new ItemStack(ModItems.GAUNTLET);
				com.dantemorrigan.gauntlet.GauntletState.putStone(g, new ItemStack(ModItems.MIND_STONE));
				com.dantemorrigan.gauntlet.GauntletState.putStone(g, new ItemStack(ModItems.SOUL_STONE));
				p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, g);
				p.getInventory().add(new ItemStack(ModItems.POWER_ORB));
			});
			for (int round = 0; round < 3; round++) {
				final int r = round;
				server.runOnServer(s -> {
					ServerPlayer p = player(s);
					ServerLevel level = (ServerLevel) p.level();
					if (r == 1) {
						Thanos t = ModEntities.THANOS.create(level, EntitySpawnReason.COMMAND);
						t.snapTo(p.getX() + 3, p.getY(), p.getZ(), 0, 0);
						level.addFreshEntity(t);
						p.hurtServer(level, t.damageSource(), 1000f);
						t.discard();
					} else {
						p.hurtServer(level, level.damageSources().generic(), 1000f);
					}
				});
				ctx.waitTicks(10);
				server.runOnServer(s -> player(s).connection.handleClientCommand(
						new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN)));
				ctx.waitTicks(20);
				int n = server.computeOnServer(StoneDeathDupTest::count);
				int dropped = server.computeOnServer(s -> ((ServerLevel) player(s).level()).getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
						player(s).getBoundingBox().inflate(200), e -> e.getItem().is(ModItems.SPACE_STONE) || e.getItem().is(ModItems.REALITY_STONE)).size());
				System.out.println("STONE-DUP round " + round + ": in inventory " + n + ", dropped " + dropped);
				if (n + dropped != 5) throw new AssertionError("Round " + round + ": expected 5 stones, inventory " + n + " + dropped " + dropped);
			}
			// Die in the Nether in lava, quit on the death screen, reopen the world, respawn.
			server.runCommand("execute in minecraft:the_nether run tp @p 0 100 0");
			ctx.waitTicks(20);
			server.runOnServer(s -> { var p = player(s); p.hurtServer((ServerLevel) p.level(), ((ServerLevel) p.level()).damageSources().lava(), 1000f); });
			ctx.waitTicks(10);
			var save = sp.getWorldSave();
			sp.close();
			try (var again = save.open()) {
				ctx.waitTicks(20);
				var srv = again.getServer();
				srv.runOnServer(s -> { if (player(s).isDeadOrDying()) player(s).connection.handleClientCommand(
						new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN)); });
				ctx.waitTicks(20);
				int n = srv.computeOnServer(StoneDeathDupTest::count);
				System.out.println("STONE-DUP reopen: in inventory " + n);
				if (n != 5) throw new AssertionError("After reopening: expected 5 stones, inventory " + n);
			}
			return;
		}
	}
}
