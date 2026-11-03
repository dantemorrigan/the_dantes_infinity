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
		com.dantemorrigan.gauntlet.client.render.GauntletGestureClient.init();
		com.dantemorrigan.gauntlet.client.fx.SnapClient.init();
		com.dantemorrigan.gauntlet.client.fx.ExtractorClient.init();
		com.dantemorrigan.gauntlet.client.fx.MeteorStreamClient.init();
		// Uru 3D details on every humanoid model; rune flash when the armor absorbs a full-gauntlet hit.
		net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer.getModel() instanceof net.minecraft.client.model.HumanoidModel<?>) registerUru(helper, renderer);
		});
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(com.dantemorrigan.gauntlet.net.ModNetwork.UruGuardFx.TYPE, (fx, ctx) -> {
			var mc = net.minecraft.client.Minecraft.getInstance();
			if (mc.level == null) return;
			var e = mc.level.getEntity(fx.entityId());
			if (e != null) mc.particleEngine.createParticle(com.dantemorrigan.gauntlet.ModParticles.URU_GUARD, e.getX(), e.getY() + e.getBbHeight() * 0.62, e.getZ(), fx.entityId(), 0, 0);
		});
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.dantemorrigan.gauntlet.ModEntities.GAUNTLET_DOLL, com.dantemorrigan.gauntlet.client.render.GauntletDollRenderer::new);
	}
}

