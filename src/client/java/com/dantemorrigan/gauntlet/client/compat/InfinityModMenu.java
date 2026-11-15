package com.dantemorrigan.gauntlet.client.compat;

import com.dantemorrigan.gauntlet.client.screen.ModSettingsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class InfinityModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<ModSettingsScreen> getModConfigScreenFactory() {
		return ModSettingsScreen::new;
	}
}
