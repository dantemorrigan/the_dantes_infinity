package com.dantemorrigan.gauntlet.client.quest;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.stones.MindZombie;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.zombie.BabyZombieModel;
import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Renders the smart Mind Zombies of the Ruined City: vanilla 64x64 zombie texture overlaid with the full-bright
 * yellow Mind Stone emissive mask ({@code quest_mind_zombie_emissive.png}) and a low-alpha yellow body aura.
 */
public final class MindZombieRenderer extends AbstractZombieRenderer<MindZombie, ZombieRenderState, ZombieModel<ZombieRenderState>> {
	private static final Identifier EMISSIVE_TEXTURE = InfinityGauntlet.id("textures/entity/quest_mind_zombie_emissive.png");

	public MindZombieRenderer(EntityRendererProvider.Context context) {
		super(context,
				new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE)),
				new BabyZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_BABY)),
				ArmorModelSet.bake(ModelLayers.ZOMBIE_ARMOR, context.getModelSet(), ZombieModel::new),
				ArmorModelSet.bake(ModelLayers.ZOMBIE_BABY_ARMOR, context.getModelSet(), BabyZombieModel::new));
		this.addLayer(new RenderLayer<>(this) {
			@Override
			public void submit(PoseStack pose, SubmitNodeCollector collector, int light, ZombieRenderState state, float yRot, float xRot) {
				collector.order(1).submitModel(
						this.getParentModel(),
						state,
						pose,
						RenderTypes.entityTranslucentEmissive(EMISSIVE_TEXTURE),
						LightCoordsUtil.FULL_BRIGHT,
						OverlayTexture.NO_OVERLAY,
						-1,
						null,
						state.outlineColor,
						null);
			}
		});
	}

	@Override
	public ZombieRenderState createRenderState() {
		return new ZombieRenderState();
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level == null || mc.isPaused() || mc.level.getGameTime() % 12 != 0) return;
			Vec3 cam = mc.gameRenderer.getMainCamera().position();
			for (MindZombie z : mc.level.getEntitiesOfClass(MindZombie.class, new AABB(cam, cam).inflate(48.0), MindZombie::isAlive)) {
				mc.particleEngine.createParticle(ModParticles.QUEST_MIND_ZOMBIE_AURA,
						z.getX(), z.getY() + z.getBbHeight() * 0.55, z.getZ(), z.getId(), 0, 0);
			}
		});
	}
}
