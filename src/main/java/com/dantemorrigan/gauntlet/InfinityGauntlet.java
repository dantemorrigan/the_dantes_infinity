package com.dantemorrigan.gauntlet;

import com.dantemorrigan.gauntlet.menu.ModMenus;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfinityGauntlet implements ModInitializer {
	public static final String MOD_ID = "infinity_gauntlet";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModItems.init();
		ModCreativeTab.init();
		ModMenus.init();
		GauntletCommand.init();
		LOGGER.info("Infinity Gauntlet loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
