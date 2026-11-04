package com.dantemorrigan.gauntlet.client.screen;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;

/** Adds an "Infinity Gauntlet" button (bottom left) to the Options and Pause screens. */
public final class SettingsButtons {
	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof OptionsScreen) && !(screen instanceof PauseScreen)) return;
			Button button = Button.builder(Component.translatable("settings.infinity_gauntlet.button"),
					b -> client.setScreen(new ModSettingsScreen(screen))).bounds(8, h - 28, 130, 20).build();
			Screens.getWidgets(screen).add(button);
		});
	}

	private SettingsButtons() {}
}
