package com.dantemorrigan.gauntlet;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModCreativeTab {
	public static final CreativeModeTab TAB = FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.infinity_gauntlet"))
			.icon(() -> new ItemStack(ModItems.GAUNTLET))
			.displayItems((params, output) -> {
				output.accept(ModItems.STORMBREAKER);
				output.accept(ModItems.GAUNTLET);
				output.accept(ModItems.POWER_STONE);
				output.accept(ModItems.SPACE_STONE);
				output.accept(ModItems.REALITY_STONE);
				output.accept(ModItems.MIND_STONE);
				output.accept(ModItems.TIME_STONE);
				output.accept(ModItems.SOUL_STONE);
				output.accept(ModItems.POWER_ORB);
				output.accept(ModItems.POWER_ORB_OPEN);
				output.accept(ModItems.QUEST_SPACE_TESSERACT);
				output.accept(ModItems.QUEST_SOUL_TEMPLE_MAP);
				output.accept(ModBlocks.QUEST_SOUL_RITUAL_BASALT);
				output.accept(ModItems.QUEST_TIME_TEMPLE_MAP);
				output.accept(ModItems.QUEST_TIME_GEAR_OVERWORLD);
				output.accept(ModItems.QUEST_TIME_GEAR_NETHER);
				output.accept(ModItems.QUEST_TIME_GEAR_END);
				output.accept(ModBlocks.QUEST_TIME_CARVED_STONE);
				output.accept(ModItems.QUEST_MIND_CITY_MAP);
				output.accept(ModItems.QUEST_GAUNTLET_BLUEPRINT);
				output.accept(ModBlocks.QUEST_MIND_MOSSY_RELIEF);
				output.accept(ModBlocks.QUEST_SPACE_CARVED_ICE);
				output.accept(ModBlocks.PURPLE_METEORITE);
				output.accept(ModBlocks.PURPLE_METEORITE_CORE);
				output.accept(ModBlocks.URU_ORE);
				output.accept(ModBlocks.DEEPSLATE_URU_ORE);
				output.accept(ModItems.RAW_URU);
				output.accept(ModItems.URU_INGOT);
				output.accept(ModItems.URU_HELMET);
				output.accept(ModItems.URU_CHESTPLATE);
				output.accept(ModItems.URU_LEGGINGS);
				output.accept(ModItems.URU_BOOTS);
				output.accept(ModItems.INFINITY_EXTRACTOR);
				output.accept(ModItems.GAUNTLET_DOLL_SPAWN_EGG);
			})
			.build();

	public static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, InfinityGauntlet.id("main"), TAB);
	}

	private ModCreativeTab() {}
}
