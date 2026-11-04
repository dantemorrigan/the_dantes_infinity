package com.dantemorrigan.gauntlet;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.function.Function;

public final class ModItems {
	public static final Item GAUNTLET = register("gauntlet", GauntletItem::new, epic().component(DataComponents.CONTAINER, ItemContainerContents.EMPTY));

	public static final Item POWER_STONE = register("power_stone", stone("power"));
	public static final Item SPACE_STONE = register("space_stone", p -> new LooseStoneItem(p, Stone.SPACE), stone("space"));
	public static final Item REALITY_STONE = register("reality_stone", p -> new LooseStoneItem(p, Stone.REALITY), stone("reality"));
	public static final Item MIND_STONE = register("mind_stone", p -> new LooseStoneItem(p, Stone.MIND), stone("mind"));
	public static final Item TIME_STONE = register("time_stone", TimeStoneItem::new, stone("time"));
	public static final Item SOUL_STONE = register("soul_stone", p -> new LooseStoneItem(p, Stone.SOUL), stone("soul"));

	public static final Item INFINITY_EXTRACTOR = register("infinity_extractor", com.dantemorrigan.gauntlet.extractor.InfinityExtractorItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
	/** Test dummy: a Steve doll wearing a full gauntlet. */
	public static final Item GAUNTLET_DOLL_SPAWN_EGG = register("gauntlet_doll_spawn_egg", net.minecraft.world.item.SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.GAUNTLET_DOLL));

	/** A loose stone: name and description in the stone's own colour (not the epic purple). */
	private static Item.Properties stone(String name) {
		int color = Stone.valueOf(name.toUpperCase(java.util.Locale.ROOT)).color;
		return epic()
				.component(DataComponents.ITEM_NAME, net.minecraft.network.chat.Component.translatable("item.infinity_gauntlet." + name + "_stone").withStyle(style -> style.withColor(color).withBold(true)))
				.component(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(
						net.minecraft.network.chat.Component.translatable("tooltip.infinity_gauntlet.loose_" + name).withStyle(s -> s.withColor(color).withBold(false).withItalic(false)))));
	}

	/** The sealed Orb from the purple meteorite (holds the Power Stone) and the opened, empty one. */
	public static final Item POWER_ORB = register("power_orb", com.dantemorrigan.gauntlet.stones.OrbItem::new, epic());
	public static final Item POWER_ORB_OPEN = register("power_orb_open", new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant());

	/** The Tesseract with the Space Stone inside: right-click goes home, lava breaks it open. */
	public static final Item QUEST_SPACE_TESSERACT = register("quest_space_tesseract", com.dantemorrigan.gauntlet.stones.TesseractItem::new,
			epic().component(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(
					net.minecraft.network.chat.Component.translatable("tooltip.infinity_gauntlet.tesseract")))));

	/** Parchment that unrolls into a map to the Temple of Soul (chests, traders). */
	public static final Item QUEST_SOUL_TEMPLE_MAP = register("quest_soul_temple_map", com.dantemorrigan.gauntlet.stones.SoulTempleMapItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

	/** Raw uru from the ore, smelted into uru ingots like iron. */
	public static final Item RAW_URU = register("raw_uru", new Item.Properties().rarity(Rarity.UNCOMMON).fireResistant());
	public static final Item URU_INGOT = register("uru_ingot", new Item.Properties().rarity(Rarity.RARE).fireResistant());

	// Armor of Uru: the only protection that holds against a full Infinity Gauntlet (see uru/UruArmor).
	public static final Item URU_HELMET = register("uru_helmet", uru(net.minecraft.world.item.equipment.ArmorType.HELMET));
	public static final Item URU_CHESTPLATE = register("uru_chestplate", uru(net.minecraft.world.item.equipment.ArmorType.CHESTPLATE));
	public static final Item URU_LEGGINGS = register("uru_leggings", uru(net.minecraft.world.item.equipment.ArmorType.LEGGINGS));
	public static final Item URU_BOOTS = register("uru_boots", uru(net.minecraft.world.item.equipment.ArmorType.BOOTS));

	private static Item.Properties uru(net.minecraft.world.item.equipment.ArmorType type) {
		return new Item.Properties().humanoidArmor(com.dantemorrigan.gauntlet.uru.UruArmor.MATERIAL, type).rarity(Rarity.EPIC).fireResistant();
	}

	private static Item.Properties epic() {
		return new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant();
	}

	private static Item register(String name, Item.Properties properties) {
		return register(name, Item::new, properties);
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, InfinityGauntlet.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	public static void init() {}

	private ModItems() {}
}
