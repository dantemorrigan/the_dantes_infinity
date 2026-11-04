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
		ModSounds.init();
		ModEntities.init();
		ModBlocks.init();
		ModItems.init();
		ModCreativeTab.init();
		ModMenus.init();
		GauntletCommand.init();
		com.dantemorrigan.gauntlet.guide.GuideBook.init();
		com.dantemorrigan.gauntlet.stones.PowerStoneFall.init();
		com.dantemorrigan.gauntlet.stones.PowerStoneCurse.init();
		com.dantemorrigan.gauntlet.stones.StoneKeeper.init();
		com.dantemorrigan.gauntlet.stones.SpaceStoneQuest.init();
		com.dantemorrigan.gauntlet.stones.RealityFog.init();
		com.dantemorrigan.gauntlet.stones.SoulTemple.init();
		com.dantemorrigan.gauntlet.stones.SoulQuest.init();
		com.dantemorrigan.gauntlet.stones.SoulLoot.init();
		GauntletEffects.init();
		ModNetwork.init();
		com.dantemorrigan.gauntlet.power.InfinityBeam.init();
		ServerPlayNetworking.registerGlobalReceiver(ModNetwork.BeamAction.TYPE, (payload, context) -> com.dantemorrigan.gauntlet.power.InfinityBeam.request(context.player(), payload.start()));
		SpaceManager.init();
        com.dantemorrigan.gauntlet.cosmos.CosmosTravel.init();
        com.dantemorrigan.gauntlet.cosmos.SupersonicFlight.init();
		PlanetDestruction.init();
		TimeAbility.init();
		com.dantemorrigan.gauntlet.power.RealityStone.init();
		com.dantemorrigan.gauntlet.power.RealityStrike.init();
		ServerPlayNetworking.registerGlobalReceiver(ModNetwork.RealityHold.TYPE, (payload, context) -> com.dantemorrigan.gauntlet.power.RealityStone.hold(context.player(), payload.closed()));
		ServerPlayNetworking.registerGlobalReceiver(ModNetwork.RealityStrike.TYPE, (payload, context) -> com.dantemorrigan.gauntlet.power.RealityStrike.strike(context.player()));
		com.dantemorrigan.gauntlet.power.SpaceStone.init();
		ServerPlayNetworking.registerGlobalReceiver(ModNetwork.SpaceAction.TYPE, (payload, context) -> com.dantemorrigan.gauntlet.power.SpaceStone.action(context.player(), payload.action()));
		com.dantemorrigan.gauntlet.celestial.CelestialApocalypse.init();
		ServerPlayNetworking.registerGlobalReceiver(ModNetwork.TimeRewindRequest.TYPE, (payload, context) -> TimeAbility.request(context.player()));
		com.dantemorrigan.gauntlet.power.TimeShift.init();
		ServerPlayNetworking.registerGlobalReceiver(ModNetwork.TimeShiftRequest.TYPE, (payload, context) -> com.dantemorrigan.gauntlet.power.TimeShift.request(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(ModNetwork.ToggleMode.TYPE, (payload, context) -> PowerAbility.toggleMode(context.player()));
		com.dantemorrigan.gauntlet.power.MindStone.init();
		ServerPlayNetworking.registerGlobalReceiver(ModNetwork.MindAction.TYPE, (payload, context) -> com.dantemorrigan.gauntlet.power.MindStone.action(context.player(), payload.action()));
		com.dantemorrigan.gauntlet.power.SoulStone.init();
		ServerPlayNetworking.registerGlobalReceiver(ModNetwork.SoulAction.TYPE, (payload, context) -> com.dantemorrigan.gauntlet.power.SoulStone.action(context.player(), payload.action()));
        com.dantemorrigan.gauntlet.ability.AbilityController.init();
		com.dantemorrigan.gauntlet.snap.InfinitySnap.init();
		com.dantemorrigan.gauntlet.extractor.StoneExtraction.init();
		com.dantemorrigan.gauntlet.meteor.MeteorStream.init();
		ServerPlayNetworking.registerGlobalReceiver(ModNetwork.GestureStart.TYPE, (payload, context) -> GauntletGesture.start(context.player()));
		LOGGER.info("Infinity Gauntlet loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}

