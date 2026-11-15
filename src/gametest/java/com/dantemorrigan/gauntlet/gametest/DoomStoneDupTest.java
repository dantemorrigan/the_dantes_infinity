package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.doom.DoctorDoom;
import com.dantemorrigan.gauntlet.doom.DoomFinalEncounter;
import com.dantemorrigan.gauntlet.doom.Latveria;
import com.dantemorrigan.gauntlet.doom.LatveriaPlan;
import com.dantemorrigan.gauntlet.stones.StoneKeeper;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Smoke test: the Doom addon must never make a stone exist twice in an ordinary playthrough. Doom's theft moves the
 * gauntlet (it is not copied), an empty gauntlet stays empty through the whole storyline, and a Doom who dies holding
 * the stolen gauntlet never drops it. Run: {@code xvfb-run -a ./gradlew runClientGameTest -PonlyTest=DoomStoneDupTest}.
 */
public class DoomStoneDupTest implements FabricClientGameTest {
	private static ServerPlayer player(MinecraftServer s) { return s.getPlayerList().getPlayers().getFirst(); }

	private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

	/** Stones in a stack, counting the ones inside a gauntlet. */
	private static int stones(ItemStack st) { return st.isEmpty() ? 0 : StoneKeeper.stonesIn(st).size() * Math.max(1, st.getCount()); }

	@Override
	public void runTest(ClientGameTestContext ctx) {
		var sp = ctx.worldBuilder().create();
		sp.getClientLevel().waitForChunksRender();
		var server = sp.getServer();
		server.runCommand("gamemode survival @p");

		// 1. The theft moves the gauntlet: 3 stones in it + 2 loose = 5 in the world, before and after.
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel latveria = s.getLevel(Latveria.LEVEL);
			ItemStack g = new ItemStack(ModItems.GAUNTLET);
			GauntletState.putStone(g, new ItemStack(ModItems.MIND_STONE));
			GauntletState.putStone(g, new ItemStack(ModItems.SOUL_STONE));
			GauntletState.putStone(g, new ItemStack(ModItems.TIME_STONE));
			p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, g);
			p.getInventory().add(new ItemStack(ModItems.SPACE_STONE));
			p.getInventory().add(new ItemStack(ModItems.REALITY_STONE));
			DoctorDoom doom = Latveria.summon(latveria);
			check(doom != null, "Doom could not be summoned");
			int before = 5;
			check(doom.stealGauntletAndFlee(latveria, p), "the theft did not start");
			int atDoom = stones(doom.getItemBySlot(EquipmentSlot.OFFHAND));
			int inInventory = 0;
			var inv = p.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) inInventory += stones(inv.getItem(i));
			inInventory += stones(p.getOffhandItem());
			check(atDoom == 3, "Doom should hold the 3 stones of the stolen gauntlet, holds " + atDoom);
			check(inInventory == 2, "the player should keep only the 2 loose stones, has " + inInventory);
			check(atDoom + inInventory == before, "stones were made or lost by the theft");
			check(p.getOffhandItem().isEmpty(), "the gauntlet was copied, not moved: the player still wears one");
			check(Integer.bitCount(DoomFinalEncounter.packStoneMask(doom.getItemBySlot(EquipmentSlot.OFFHAND))) == 3, "the stone record does not match the stolen gauntlet");
		});

		// 2. An empty gauntlet is stolen empty and comes back empty (no five stones out of nothing).
		server.runOnServer(s -> {
			ItemStack empty = new ItemStack(ModItems.GAUNTLET);
			check(DoomFinalEncounter.packStoneMask(empty) == 0, "an empty gauntlet must be recorded empty");
			check(GauntletState.inserted(DoomFinalEncounter.buildGauntletFromMask(0)).isEmpty(), "an empty record must rebuild an empty gauntlet");
		});

		// 3. Many Dooms die holding a gauntlet (the default 8.5% hand drop would show up): none may drop it.
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel latveria = s.getLevel(Latveria.LEVEL);
			p.teleportTo(latveria, 0.5, LatveriaPlan.FLOOR, -258.5, java.util.Set.of(), 0, 0, true);
			int dropped = 0;
			for (int i = 0; i < 60; i++) {
				DoctorDoom d = ModEntities.DOCTOR_DOOM.create(latveria, EntitySpawnReason.COMMAND);
				d.snapTo(p.getX() + 2, p.getY(), p.getZ(), 0, 0);
				d.setItemSlot(EquipmentSlot.OFFHAND, DoomFinalEncounter.buildGauntletFromMask(0x1F));
				latveria.addFreshEntity(d);
				d.die(latveria.damageSources().playerAttack(p));
			}
			for (ItemEntity e : latveria.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(16))) {
				if (e.getItem().is(ModItems.GAUNTLET)) dropped++;
			}
			check(dropped == 0, dropped + " Infinity Gauntlets (with stones) fell from dying Dooms");
		});
	}
}
