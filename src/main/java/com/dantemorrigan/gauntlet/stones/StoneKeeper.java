package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.Stone;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.ArrayList;
import java.util.List;

/**
 * Stones stay with their owner: a player who dies to mobs, falls, lava or their own Power Stone keeps every
 * stone-bearing item (loose stones, a gauntlet or extractor holding stones, the sealed Orb) and gets it back on
 * respawn. Only a kill by another player drops them. A dropped stone never despawns and can't be destroyed.
 */
public final class StoneKeeper {
	private static final AttachmentType<List<ItemStack>> KEPT = AttachmentRegistry.<List<ItemStack>>builder()
			.persistent(ItemStack.CODEC.listOf())
			.copyOnDeath()
			.buildAndRegister(InfinityGauntlet.id("kept_stones"));

	/** Same stash in memory: the attachment covers restarts, the map covers the respawn copy. */
	private static final java.util.Map<java.util.UUID, List<ItemStack>> PENDING = new java.util.HashMap<>();

	public static void init() {
		ServerPlayerEvents.AFTER_RESPAWN.register((old, player, alive) -> restore(player));
		// Died, then the server stopped before the respawn: the attachment brings the stones back on the next respawn.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s -> PENDING.clear());
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item && precious(item.getItem())) {
				item.setUnlimitedLifetime();
				item.setInvulnerable(true);
			}
		});
	}

	public static boolean precious(ItemStack stack) {
		if (stack.isEmpty()) return false;
		if (stack.is(ModItems.POWER_ORB)) return true;
		for (Stone s : Stone.values()) if (s.matches(stack)) return true;
		if (stack.is(ModItems.GAUNTLET) || stack.is(ModItems.INFINITY_EXTRACTOR)) {
			net.minecraft.core.NonNullList<ItemStack> items = net.minecraft.core.NonNullList.withSize(9, ItemStack.EMPTY);
			stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
			for (ItemStack inside : items) {
				for (Stone s : Stone.values()) if (s.matches(inside)) return true;
			}
		}
		return false;
	}

	/** Called right before a dead player's inventory is dropped (see PlayerMixin). */
	public static void beforeDrop(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		if (level.getGameRules().get(GameRules.KEEP_INVENTORY) || killedByPlayer(player)) return;
		Inventory inv = player.getInventory();
		List<ItemStack> kept = new ArrayList<>();
		List<ItemStack> previous = player.getAttached(KEPT);
		if (previous != null) kept.addAll(previous);
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (precious(stack)) {
				kept.add(stack.copy());
				inv.setItem(i, ItemStack.EMPTY);
			}
		}
		if (!kept.isEmpty()) {
			player.setAttached(KEPT, kept);
			PENDING.put(player.getUUID(), kept);
		}
	}

	private static boolean killedByPlayer(ServerPlayer player) {
		DamageSource source = player.getLastDamageSource();
		Entity attacker = source == null ? null : source.getEntity();
		LivingEntity credit = player.getKillCredit();
		return (attacker instanceof Player && attacker != player) || (credit instanceof Player && credit != player);
	}

	private static void restore(ServerPlayer player) {
		List<ItemStack> kept = PENDING.remove(player.getUUID());
		if (kept == null) kept = player.getAttached(KEPT);
		player.removeAttached(KEPT);
		if (kept == null) return;
		for (ItemStack stack : kept) OrbItem.giveIntoBackpack(player, stack.copy());
	}

	private StoneKeeper() {}
}
