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
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void registerUru(net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback.RegistrationHelper helper,
			net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?, ?> renderer) {
		helper.register(new com.dantemorrigan.gauntlet.client.render.UruDetailLayer((net.minecraft.client.renderer.entity.RenderLayerParent) renderer));
	}

	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenus.GAUNTLET, GauntletScreen::new);
        com.dantemorrigan.gauntlet.client.ability.AbilityInput.init();
		ModParticleProviders.init();
		ClientPower.init();
		com.dantemorrigan.gauntlet.client.fx.CosmicClient.init();
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
		QuestCompassHud.init();
		com.dantemorrigan.gauntlet.client.quest.SpaceQuestClient.init();
		com.dantemorrigan.gauntlet.client.quest.RealityFogClient.init();
		com.dantemorrigan.gauntlet.client.quest.SoulQuestClient.init();
		com.dantemorrigan.gauntlet.client.quest.MindZombieRenderer.init();
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.dantemorrigan.gauntlet.ModEntities.REALITY_WISP, com.dantemorrigan.gauntlet.client.quest.RealityWispRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.dantemorrigan.gauntlet.ModEntities.MIND_ZOMBIE, com.dantemorrigan.gauntlet.client.quest.MindZombieRenderer::new);
		com.dantemorrigan.gauntlet.client.boss.LokiClient.init();
		com.dantemorrigan.gauntlet.client.boss.TribunalClient.init();
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
				com.dantemorrigan.gauntlet.ModEntities.TRIBUNAL, com.dantemorrigan.gauntlet.client.boss.TribunalRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.dantemorrigan.gauntlet.ModEntities.LOKI, com.dantemorrigan.gauntlet.client.boss.LokiRenderer::new);
		com.dantemorrigan.gauntlet.client.boss.ThanosClient.init();
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.dantemorrigan.gauntlet.ModEntities.THANOS, com.dantemorrigan.gauntlet.client.boss.ThanosRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.dantemorrigan.gauntlet.ModEntities.THANOS_SWORD, com.dantemorrigan.gauntlet.client.boss.ThanosSwordRenderer::new);
		com.dantemorrigan.gauntlet.client.screen.SettingsButtons.init();
		com.dantemorrigan.gauntlet.client.stormbreaker.StormbreakerClient.init();
		WornGauntlet.init();
		com.dantemorrigan.gauntlet.client.render.GauntletGestureClient.init();
		com.dantemorrigan.gauntlet.client.fx.SnapClient.init();
		com.dantemorrigan.gauntlet.client.fx.ExtractorClient.init();
		com.dantemorrigan.gauntlet.client.fx.MeteorStreamClient.init();
		// Uru 3D details on every humanoid model; rune flash when the armor absorbs a full-gauntlet hit.
		net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer.getModel() instanceof net.minecraft.client.model.HumanoidModel<?>) registerUru(helper, renderer);
		});
		com.dantemorrigan.gauntlet.client.render.UruClient.init();
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.dantemorrigan.gauntlet.ModEntities.GAUNTLET_DOLL, com.dantemorrigan.gauntlet.client.render.GauntletDollRenderer::new);
	}
}

