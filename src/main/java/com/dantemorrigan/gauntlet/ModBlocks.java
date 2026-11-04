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

	// The purple meteorite the Power Stone falls in (see stones/PowerStoneFall). Tough, glows, resists blasts.
	public static final Block PURPLE_METEORITE = register("purple_meteorite", BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_PURPLE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops()
			.strength(25.0f, 1200.0f).lightLevel(state -> 4).sound(SoundType.ANCIENT_DEBRIS));
	public static final Block PURPLE_METEORITE_CORE = register("purple_meteorite_core", BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_MAGENTA).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops()
			.strength(30.0f, 1200.0f).lightLevel(state -> 9).sound(SoundType.ANCIENT_DEBRIS));
	/** The sealed Orb inside the meteorite: drops the Orb item, can't be blown up or pushed. No block item. */
	public static final Block POWER_ORB = registerNoItem("power_orb", p -> new com.dantemorrigan.gauntlet.stones.PowerOrbBlock(p),
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 3_600_000.0f).lightLevel(state -> 11)
					.sound(SoundType.NETHERITE_BLOCK).noOcclusion()
					.pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK));

	// Space Stone quest: the ancient ice tower, the rift portal, the Tesseract on its pedestal.
	public static final Block QUEST_SPACE_CARVED_ICE = register("quest_space_carved_ice", BlockBehaviour.Properties.of()
			.mapColor(MapColor.ICE).strength(2.8f, 9.0f).sound(SoundType.GLASS).requiresCorrectToolForDrops());
	public static final Block FROST_PORTAL = registerNoItem("frost_portal", p -> new com.dantemorrigan.gauntlet.stones.FrostPortalBlock(p),
			BlockBehaviour.Properties.of().mapColor(MapColor.ICE).noCollision().strength(-1.0f, 3_600_000.0f).lightLevel(state -> 12)
					.noLootTable().sound(SoundType.GLASS).pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK));
	public static final Block TESSERACT = registerNoItem("tesseract", p -> new com.dantemorrigan.gauntlet.stones.TesseractBlock(p),
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(-1.0f, 3_600_000.0f).lightLevel(state -> 15)
					.noLootTable().noOcclusion().sound(SoundType.AMETHYST).pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK));

	public static final ResourceKey<PlacedFeature> ORE_URU = ResourceKey.create(Registries.PLACED_FEATURE, InfinityGauntlet.id("ore_uru"));

	private static Block register(String name, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, InfinityGauntlet.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, new Block(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, InfinityGauntlet.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	private static Block registerNoItem(String name, java.util.function.Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, InfinityGauntlet.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	public static void init() {
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES, ORE_URU);
	}

	private ModBlocks() {}
}
