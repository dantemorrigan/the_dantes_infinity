package com.dantemorrigan.gauntlet;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.MapColor;

/**
 * Uru ore: deep in the overworld (rarer than diamonds), mined only with a diamond or netherite pickaxe
 * (tags mineable/pickaxe + needs_diamond_tool). Drops raw uru (fortune works), smelted into uru ingots like iron.
 * The metal glints faintly in the dark.
 */
public final class ModBlocks {
	public static final Block URU_ORE = register("uru_ore", BlockBehaviour.Properties.of()
			.mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops()
			.strength(5.0f, 6.0f).lightLevel(state -> 3).sound(SoundType.STONE));
	public static final Block DEEPSLATE_URU_ORE = register("deepslate_uru_ore", BlockBehaviour.Properties.of()
			.mapColor(MapColor.DEEPSLATE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops()
			.strength(6.5f, 6.0f).lightLevel(state -> 3).sound(SoundType.DEEPSLATE));

	public static final ResourceKey<PlacedFeature> ORE_URU = ResourceKey.create(Registries.PLACED_FEATURE, InfinityGauntlet.id("ore_uru"));

	private static Block register(String name, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, InfinityGauntlet.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, new Block(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, InfinityGauntlet.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	public static void init() {
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES, ORE_URU);
	}

	private ModBlocks() {}
}
