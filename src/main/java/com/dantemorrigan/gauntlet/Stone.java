package com.dantemorrigan.gauntlet;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The six stones, in gauntlet slot order. */
public enum Stone {
	POWER("power", 0x9B0FD0),
	SPACE("space", 0x0042EF),
	REALITY("reality", 0xC80A14),
	SOUL("soul", 0xEE6302),
	MIND("mind", 0xF3B902),
	TIME("time", 0x04A834);

	public final String id;
	public final int color;

	Stone(String id, int color) {
		this.id = id;
		this.color = color;
	}

	public Item item() {
		return switch (this) {
			case POWER -> ModItems.POWER_STONE;
			case SPACE -> ModItems.SPACE_STONE;
			case REALITY -> ModItems.REALITY_STONE;
			case SOUL -> ModItems.SOUL_STONE;
			case MIND -> ModItems.MIND_STONE;
			case TIME -> ModItems.TIME_STONE;
		};
	}

	public boolean matches(ItemStack stack) {
		return stack.is(item());
	}
}
