package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.stones.OrbItem;
import com.dantemorrigan.gauntlet.stones.PowerStoneFall;
import com.dantemorrigan.gauntlet.stones.StoneRegistry;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** The Power Stone's way into the world: shower → purple meteorite → Orb → stone; the curse; keeping stones on death. */
public class PowerStoneTest implements FabricClientGameTest {
	private static ServerPlayer player(net.minecraft.server.MinecraftServer s) { return s.getPlayerList().getPlayers().getFirst(); }

	private static int find(Inventory inv, net.minecraft.world.item.Item item) {
		for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) return i;
		return -1;
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule fire_spread_radius_around_player 0");
			server.runCommand("time set 6000");
			server.runCommand("gamemode survival @p");
			server.runCommand("effect give @p resistance 99999 4 true");
			server.runCommand("gauntlet stones reset");
			server.runCommand("execute as @p run gauntlet power_shower");
			ctx.waitTicks(5);
			BlockPos center = server.computeOnServer(s -> PowerStoneFall.showerCenter());
			if (center == null) throw new AssertionError("Shower did not start");
			// Watch it from a distance.
			ctx.waitTicks(200);
			server.runCommand("tp @p " + center.getX() + " " + (center.getY() + 40) + " " + (center.getZ() - 70) + " 0 25");
			server.runCommand("effect give @p slow_falling 99999 1 true");
			ctx.waitTicks(40);
			ctx.takeScreenshot("power_shower");
			BlockPos site = null;
			for (int i = 0; i < 60 && site == null; i++) {
				ctx.waitTicks(20);
				site = server.computeOnServer(PowerStoneFall::site);
			}
			if (site == null) throw new AssertionError("No purple meteorite landed");
			BlockPos s0 = site;
			server.runCommand("effect clear @p slow_falling");
			server.runCommand("fill " + (s0.getX() - 8) + " " + (s0.getY() - 4) + " " + (s0.getZ() - 8) + " " + (s0.getX() + 8) + " " + (s0.getY() + 6) + " " + (s0.getZ() + 8) + " air replace fire");
			server.runCommand("tp @p " + (s0.getX() + 0.5) + " " + (s0.getY() + 6) + " " + (s0.getZ() - 6.5) + " 0 40");
			ctx.waitTicks(60);
			ctx.takeScreenshot("power_meteorite");
			if (server.computeOnServer(s -> PowerStoneFall.start(s.overworld(), player(s))))
				throw new AssertionError("A second shower started while the Orb lies in the meteorite");

			// Dig the Orb out.
			server.runOnServer(s -> player(s).gameMode.destroyBlock(s0));
			server.runCommand("tp @p " + (s0.getX() + 0.5) + " " + s0.getY() + " " + (s0.getZ() + 0.5));
			ctx.waitTicks(40);
			if (!server.computeOnServer(s -> StoneRegistry.found(s, Stone.POWER))) throw new AssertionError("Orb not registered as found");
			if (server.computeOnServer(s -> find(player(s).getInventory(), ModItems.POWER_ORB)) < 0) throw new AssertionError("Orb not picked up");

			// Open it: the stone goes to the backpack, not the hand.
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				int slot = find(p.getInventory(), ModItems.POWER_ORB);
				p.getInventory().setItem(slot, ItemStack.EMPTY);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.POWER_ORB));
				OrbItem.open(p, InteractionHand.MAIN_HAND);
			});
			ctx.waitTicks(5);
			int stoneSlot = server.computeOnServer(s -> find(player(s).getInventory(), ModItems.POWER_STONE));
			if (stoneSlot < Inventory.SELECTION_SIZE) throw new AssertionError("Stone not in backpack: slot " + stoneSlot);
			if (!server.computeOnServer(s -> StoneRegistry.released(s, Stone.POWER))) throw new AssertionError("Stone not registered");
			if (server.computeOnServer(s -> PowerStoneFall.start(s.overworld(), player(s)))) throw new AssertionError("Showers go on after the find");

			// Take it in hand: death. The stone must survive the death.
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.getInventory().setItem(stoneSlot, ItemStack.EMPTY);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.POWER_STONE));
			});
			ctx.waitTicks(15);
			ctx.takeScreenshot("power_curse");
			ctx.waitTicks(40);
			if (server.computeOnServer(s -> player(s).isAlive())) throw new AssertionError("Holding the Power Stone did not kill");
			ctx.runOnClient(mc -> mc.player.respawn());
			ctx.waitFor(mc -> mc.player != null && mc.player.isAlive(), 200);
			ctx.waitTicks(20);
			int after = server.computeOnServer(s -> find(player(s).getInventory(), ModItems.POWER_STONE));
			if (after < 0) throw new AssertionError("Stone lost on a non-PvP death; inv=" + server.computeOnServer(s -> player(s).getInventory().getNonEquipmentItems().toString()) + " alive=" + server.computeOnServer(s -> player(s).isAlive()));
			if (after == server.computeOnServer(s -> player(s).getInventory().getSelectedSlot())) throw new AssertionError("Stone restored into the hand");
		}
	}
}
