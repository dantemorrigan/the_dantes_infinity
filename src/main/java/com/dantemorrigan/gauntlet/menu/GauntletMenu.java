package com.dantemorrigan.gauntlet.menu;

import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.Stone;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;


public class GauntletMenu extends AbstractContainerMenu {
	public static final int STONE_SLOTS = Stone.values().length;
	/** Slot positions in the GUI, same order as {@link Stone}: four knuckles, back of hand, thumb. */
	public static final int[][] STONE_POS = {
			// Left hand seen from the back: thumb on the right.
			{110, 18}, {90, 18}, {70, 18}, {50, 18}, // power, space, reality, soul
			{80, 46},                               // mind (center)
			{132, 46},                              // time (thumb)
	};

	private final Container stones;
	private final ItemStack gauntlet;
	private final int lockedSlot;

	/** Client side. */
	public GauntletMenu(int id, Inventory inventory) {
		this(id, inventory, new SimpleContainer(STONE_SLOTS), ItemStack.EMPTY, inventory.player.getMainHandItem().is(com.dantemorrigan.gauntlet.ModItems.GAUNTLET) ? inventory.getSelectedSlot() : 40);
	}

	/** Server side: stones are read from and saved back into the gauntlet stack. */
	public GauntletMenu(int id, Inventory inventory, ItemStack gauntlet, int lockedSlot) {
		this(id, inventory, new GauntletContainer(gauntlet), gauntlet, lockedSlot);
	}

	private GauntletMenu(int id, Inventory inventory, Container stones, ItemStack gauntlet, int lockedSlot) {
		super(ModMenus.GAUNTLET, id);
		this.stones = stones;
		this.gauntlet = gauntlet;
		this.lockedSlot = lockedSlot;

		Stone[] all = Stone.values();
		for (int i = 0; i < all.length; i++) {
			addSlot(new StoneSlot(stones, i, STONE_POS[i][0], STONE_POS[i][1], all[i]));
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(col == lockedSlot
					? new LockedSlot(inventory, col, 8 + col * 18, 142)
					: new Slot(inventory, col, 8 + col * 18, 142));
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return gauntlet.isEmpty() || (lockedSlot==40 ? player.getOffhandItem() : player.getInventory().getItem(lockedSlot)) == gauntlet;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) return ItemStack.EMPTY;
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (index < STONE_SLOTS) {
			if (!moveItemStackTo(stack, STONE_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
		} else if (!moveItemStackTo(stack, 0, STONE_SLOTS, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
		else slot.setChanged();
		return original;
	}

	/** Container backed by the gauntlet's CONTAINER component. */
	private static class GauntletContainer extends SimpleContainer {
		private final ItemStack gauntlet;

		GauntletContainer(ItemStack gauntlet) {
			super(STONE_SLOTS);
			this.gauntlet = gauntlet;
			NonNullList<ItemStack> items = NonNullList.withSize(STONE_SLOTS, ItemStack.EMPTY);
			gauntlet.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
			for (int i = 0; i < STONE_SLOTS; i++) setItem(i, items.get(i));
		}

		@Override
		public void setChanged() {
			super.setChanged();
			gauntlet.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(getItems()));
			GauntletState.refresh(gauntlet);
		}
	}

	/** Accepts only its own stone. */
	private static class StoneSlot extends Slot {
		private final Stone stone;

		StoneSlot(Container container, int index, int x, int y, Stone stone) {
			super(container, index, x, y);
			this.stone = stone;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return stone.matches(stack);
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public Identifier getNoItemIcon() {
			return InfinityGauntlet.id("container/slot/" + stone.id + "_stone");
		}
	}

	/** The slot holding the open gauntlet: can't be moved while the menu is open. */
	private static class LockedSlot extends Slot {
		LockedSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPickup(Player player) {
			return false;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}
	}
}
