package com.dantemorrigan.gauntlet;

import com.dantemorrigan.gauntlet.menu.ModMenus;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.power.PowerAbility;
import com.dantemorrigan.gauntlet.power.TimeAbility;
import com.dantemorrigan.gauntlet.space.PlanetDestruction;
import com.dantemorrigan.gauntlet.space.SpaceManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfinityGauntlet implements ModInitializer {
	public static final String MOD_ID = "infinity_gauntlet";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		GauntletConfig.get();
		ModComponents.init();
		ModParticles.init();
		ModItems.init();
		ModCreativeTab.init();
		ModMenus.init();
		GauntletCommand.init();
		GauntletEffects.init();
		ModNetwork.init();
		SpaceManager.init();
		PlanetDestruction.init();
		TimeAbility.init();
		com.dantemorrigan.gauntlet.celestial.CelestialApocalypse.init();
		ServerPlayNetworking.registerGlobalReceiver(ModNetwork.TimeRewindRequest.TYPE, (payload, context) -> TimeAbility.request(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(ModNetwork.ToggleMode.TYPE, (payload, context) -> PowerAbility.toggleMode(context.player()));
		LOGGER.info("Infinity Gauntlet loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
