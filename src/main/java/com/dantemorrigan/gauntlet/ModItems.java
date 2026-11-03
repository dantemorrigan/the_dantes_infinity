package com.dantemorrigan.gauntlet;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class ModItems {
	public static final Item GAUNTLET = register("gauntlet", epic());

	public static final Item POWER_STONE = register("power_stone", epic());
	public static final Item SPACE_STONE = register("space_stone", epic());
	public static final Item REALITY_STONE = register("reality_stone", epic());
	public static final Item MIND_STONE = register("mind_stone", epic());
	public static final Item TIME_STONE = register("time_stone", epic());
	public static final Item SOUL_STONE = register("soul_stone", epic());

	private static Item.Properties epic() {
		return new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant();
	}

	private static Item register(String name, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, InfinityGauntlet.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.setId(key)));
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.accept(GAUNTLET);
			output.accept(POWER_STONE);
			output.accept(SPACE_STONE);
			output.accept(REALITY_STONE);
			output.accept(MIND_STONE);
			output.accept(TIME_STONE);
			output.accept(SOUL_STONE);
		});
	}

	private ModItems() {}
}
