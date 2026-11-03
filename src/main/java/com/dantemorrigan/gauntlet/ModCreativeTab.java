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
				output.accept(ModItems.GAUNTLET);
				output.accept(ModItems.POWER_STONE);
				output.accept(ModItems.SPACE_STONE);
				output.accept(ModItems.REALITY_STONE);
				output.accept(ModItems.MIND_STONE);
				output.accept(ModItems.TIME_STONE);
				output.accept(ModItems.SOUL_STONE);
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
