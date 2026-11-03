package com.dantemorrigan.gauntlet;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Gauntlet state helpers.
 *
 * <p>Visual state lives in CUSTOM_MODEL_DATA flags read by the item model:
 * flags[0..5] = stone inserted, flags[6..11] = stone in use (strong glow). Index = {@link Stone} ordinal.
 */
public final class GauntletState {
	private static final int N = Stone.values().length;

	/** The gauntlet only works in the OFF hand. Returns it, or EMPTY if the player has no working gauntlet there
	 *  (a gauntlet burnt out by the Infinity Snap never works again). */
	public static ItemStack activeGauntlet(LivingEntity player) {
		ItemStack off = wornGauntlet(player);
		return Boolean.TRUE.equals(off.get(ModComponents.SNAPPED)) ? ItemStack.EMPTY : off;
	}

	/** The gauntlet in the off hand, working or burnt out, or EMPTY. */
	public static ItemStack wornGauntlet(LivingEntity player) {
		ItemStack off = player.getOffhandItem();
		return off.is(ModItems.GAUNTLET) ? off : ItemStack.EMPTY;
	}

	public static Set<Stone> inserted(ItemStack gauntlet) {
		NonNullList<ItemStack> items = NonNullList.withSize(N, ItemStack.EMPTY);
		gauntlet.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
		Set<Stone> set = EnumSet.noneOf(Stone.class);
		for (Stone s : Stone.values()) {
			if (s.matches(items.get(s.ordinal()))) set.add(s);
		}
		return set;
	}

	/** Takes the stone out of its slot (returns EMPTY if it is not there). */
	public static ItemStack takeStone(ItemStack gauntlet, Stone stone) {
		NonNullList<ItemStack> items = NonNullList.withSize(N, ItemStack.EMPTY);
		gauntlet.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
		ItemStack taken = items.get(stone.ordinal());
		if (!stone.matches(taken)) return ItemStack.EMPTY;
		items.set(stone.ordinal(), ItemStack.EMPTY);
		gauntlet.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
		refresh(gauntlet);
		return taken;
	}

	/** Puts the stone into its own free slot. Returns false if the slot is taken or the item is not that stone. */
	public static boolean putStone(ItemStack gauntlet, ItemStack stack) {
		for (Stone stone : Stone.values()) {
			if (!stone.matches(stack)) continue;
			NonNullList<ItemStack> items = NonNullList.withSize(N, ItemStack.EMPTY);
			gauntlet.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
			if (!items.get(stone.ordinal()).isEmpty()) return false;
			items.set(stone.ordinal(), stack.copyWithCount(1));
			gauntlet.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
			refresh(gauntlet);
			return true;
		}
		return false;
	}

	public static Set<Stone> inUse(ItemStack gauntlet) {
		Set<Stone> set = EnumSet.noneOf(Stone.class);
		CustomModelData data = gauntlet.get(DataComponents.CUSTOM_MODEL_DATA);
		if (data == null) return set;
		for (Stone s : Stone.values()) {
			Boolean flag = data.getBoolean(N + s.ordinal());
			if (flag != null && flag) set.add(s);
		}
		return set;
	}

	/** Mark stones as being used right now (strong glow). Stones that aren't inserted are ignored. */
	public static void setInUse(ItemStack gauntlet, Set<Stone> stones) {
		write(gauntlet, inserted(gauntlet), stones);
	}

	/** Re-sync visual flags after the stones inside changed. */
	public static void refresh(ItemStack gauntlet) {
		write(gauntlet, inserted(gauntlet), inUse(gauntlet));
	}

	private static void write(ItemStack gauntlet, Set<Stone> inserted, Set<Stone> inUse) {
		List<Boolean> flags = new ArrayList<>(N * 2);
		for (Stone s : Stone.values()) flags.add(inserted.contains(s));
		for (Stone s : Stone.values()) flags.add(inserted.contains(s) && inUse.contains(s));
		gauntlet.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), flags, List.of(), List.of()));
	}

	private GauntletState() {}
}
