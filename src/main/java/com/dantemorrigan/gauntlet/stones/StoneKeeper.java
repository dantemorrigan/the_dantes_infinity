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
		ServerPlayerEvents.AFTER_RESPAWN.register((old, player, alive) -> restore(old, player));
		// Died, then the server stopped before the respawn: the attachment brings the stones back on the next respawn.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s -> PENDING.clear());
		// A stone that falls out of the world is gone for good: the registry must forget it so its quest can run again.
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (!(entity instanceof ItemEntity item) || !precious(item.getItem()) || item.getY() >= level.getMinY()) return;
			if (item.getRemovalReason() != Entity.RemovalReason.DISCARDED) return;
			java.util.Set<Stone> lost = java.util.EnumSet.noneOf(Stone.class);
			lost.addAll(stonesIn(item.getItem()));
			if (item.getItem().is(ModItems.POWER_ORB)) lost.add(Stone.POWER);
			if (item.getItem().is(ModItems.QUEST_SPACE_TESSERACT)) lost.add(Stone.SPACE);
			for (Stone stone : lost) StoneRegistry.forget(level.getServer(), stone);
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item && precious(item.getItem())) {
				item.setUnlimitedLifetime();
				item.setInvulnerable(true);
			}
		});
	}

	public static boolean precious(ItemStack stack) {
		if (stack.isEmpty()) return false;
		if (stack.is(ModItems.POWER_ORB) || stack.is(ModItems.QUEST_SPACE_TESSERACT)) return true;
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

	/** Stones in {@code old} that are not in {@code now} were deleted in the creative inventory. */
	public static void creativeReplaced(ServerPlayer player, ItemStack old, ItemStack now) {
		var server = player.level().getServer();
		java.util.Set<Stone> after = stonesIn(now);
		if (now.is(ModItems.POWER_ORB)) after.add(Stone.POWER);
		if (now.is(ModItems.QUEST_SPACE_TESSERACT)) after.add(Stone.SPACE);
		for (Stone s : after) {
			if (s == Stone.POWER && now.is(ModItems.POWER_ORB)) StoneRegistry.markFound(server, Stone.POWER);
			else if (s == Stone.SPACE && now.is(ModItems.QUEST_SPACE_TESSERACT)) StoneRegistry.markFound(server, Stone.SPACE);
			else StoneRegistry.markReleased(server, s);
			if (s == Stone.TIME) TimeQuest.markClockCompleted(server);
		}
		if (!precious(old)) return;
		java.util.Set<Stone> before = stonesIn(old);
		boolean orb = old.is(ModItems.POWER_ORB) && !now.is(ModItems.POWER_ORB);
		boolean cube = old.is(ModItems.QUEST_SPACE_TESSERACT) && !now.is(ModItems.QUEST_SPACE_TESSERACT);
		if (orb) before.add(Stone.POWER);
		if (cube) before.add(Stone.SPACE);
		before.removeAll(after);
		for (Stone s : before) {
			if (!anyPlayerHasExcluding(server, old, s)) {
				StoneRegistry.forget(server, s);
			}
		}
	}

	/** Returns true if any online player currently carries the given stone (in inventory, cursor, gauntlet, or extractor). */
	public static boolean anyPlayerHas(net.minecraft.server.MinecraftServer server, Stone stone) {
		return anyPlayerHasExcluding(server, ItemStack.EMPTY, stone);
	}

	private static boolean anyPlayerHasExcluding(net.minecraft.server.MinecraftServer server, ItemStack excluded, Stone stone) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Inventory inv = p.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack stack = inv.getItem(i);
				if (stack != excluded && stonesIn(stack).contains(stone)) return true;
			}
			ItemStack carried = p.containerMenu.getCarried();
			if (carried != excluded && stonesIn(carried).contains(stone)) return true;
		}
		return false;
	}

	public static java.util.Set<Stone> stonesIn(ItemStack stack) {
		java.util.Set<Stone> out = new java.util.HashSet<>();
		if (stack.isEmpty()) return out;
		for (Stone s : Stone.values()) if (s.matches(stack)) out.add(s);
		net.minecraft.core.NonNullList<ItemStack> items = net.minecraft.core.NonNullList.withSize(9, ItemStack.EMPTY);
		stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
		for (ItemStack inside : items) for (Stone s : Stone.values()) if (s.matches(inside)) out.add(s);
		return out;
	}

	/** Called right before a dead player's inventory is dropped (see PlayerMixin). */
	public static void beforeDrop(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		if (level.getGameRules().get(GameRules.KEEP_INVENTORY) || killedByPlayer(player)) return;
		Inventory inv = player.getInventory();
		List<ItemStack> kept = new ArrayList<>();
		// Never merge an older stash: a player can't die twice without respawning, so anything still attached here is a
		// stale copy of stones already given back (merging it doubled them).
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

	private static void restore(ServerPlayer old, ServerPlayer player) {
		List<ItemStack> kept = PENDING.remove(player.getUUID());
		if (kept == null) kept = old.getAttached(KEPT);
		if (kept == null) kept = player.getAttached(KEPT);
		// Clear the stash everywhere: the death copy of attachments may reach the new player after this event, and a
		// stale stash would be added again on the next death (stones doubled).
		old.removeAttached(KEPT);
		player.removeAttached(KEPT);
		var server = player.level().getServer();
		java.util.UUID id = player.getUUID();
		server.execute(() -> {
			ServerPlayer now = server.getPlayerList().getPlayer(id);
			if (now != null) now.removeAttached(KEPT);
		});
		if (kept == null) return;
		for (ItemStack stack : kept) OrbItem.giveIntoBackpack(player, stack.copy());
	}

	private StoneKeeper() {}
}
