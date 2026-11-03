package com.dantemorrigan.gauntlet;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The six stones, in gauntlet slot order. */
public enum Stone {
	POWER("power", 0x9628DC),
	SPACE("space", 0x286EF0),
	REALITY("reality", 0xDC1E28),
	SOUL("soul", 0xF08214),
	MIND("mind", 0xF0C81E),
	TIME("time", 0x28C850);

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
