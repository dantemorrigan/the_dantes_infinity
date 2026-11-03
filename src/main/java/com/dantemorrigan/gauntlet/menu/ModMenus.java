package com.dantemorrigan.gauntlet.menu;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
	public static final MenuType<GauntletMenu> GAUNTLET = Registry.register(BuiltInRegistries.MENU,
			InfinityGauntlet.id("gauntlet"), new MenuType<>(GauntletMenu::new, FeatureFlags.VANILLA_SET));

	public static void init() {}

	private ModMenus() {}
}
