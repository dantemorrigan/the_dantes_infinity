package com.dantemorrigan.gauntlet.client;

import com.dantemorrigan.gauntlet.client.screen.GauntletScreen;
import com.dantemorrigan.gauntlet.menu.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public class InfinityGauntletClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenus.GAUNTLET, GauntletScreen::new);
	}
}
