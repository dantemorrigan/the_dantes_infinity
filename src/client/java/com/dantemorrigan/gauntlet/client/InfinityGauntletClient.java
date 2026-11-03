package com.dantemorrigan.gauntlet.client;

import com.dantemorrigan.gauntlet.client.cutscene.Cutscene;
import com.dantemorrigan.gauntlet.client.fx.ClientPower;
import com.dantemorrigan.gauntlet.client.fx.ModParticleProviders;
import com.dantemorrigan.gauntlet.client.render.WornGauntlet;
import com.dantemorrigan.gauntlet.client.screen.GauntletScreen;
import com.dantemorrigan.gauntlet.client.space.SpaceScene;
import com.dantemorrigan.gauntlet.menu.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public class InfinityGauntletClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenus.GAUNTLET, GauntletScreen::new);
        com.dantemorrigan.gauntlet.client.ability.AbilityInput.init();
		ModParticleProviders.init();
		ClientPower.init();
		com.dantemorrigan.gauntlet.client.fx.RealityClient.init();
		com.dantemorrigan.gauntlet.client.fx.SpaceStoneClient.init();
		com.dantemorrigan.gauntlet.client.fx.TimeRewindClient.init();
		com.dantemorrigan.gauntlet.client.fx.TimeShiftClient.init();
		com.dantemorrigan.gauntlet.client.fx.MindClient.init();
		com.dantemorrigan.gauntlet.client.fx.SoulClient.init();
		Cutscene.init();
		SpaceScene.init();
        com.dantemorrigan.gauntlet.client.cosmos.CosmosClient.init();
		com.dantemorrigan.gauntlet.client.celestial.CelestialClient.init();
		com.dantemorrigan.gauntlet.client.fx.InfinityBeamClient.init();
		StatusHud.init();
		WornGauntlet.init();
	}
}

