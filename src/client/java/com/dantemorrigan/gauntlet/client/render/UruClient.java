package com.dantemorrigan.gauntlet.client.render;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.uru.UruArmor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the Armor of Uru: the rune flash, faint cyan sparks drifting off the runes of a complete set, and the
 * armored sleeve (plates, pauldron, vambrace) on your own arm in first person.
 */
public final class UruClient {
	private static final Identifier TEXTURE = InfinityGauntlet.id("textures/entity/equipment/humanoid/uru.png");
	private static final DustParticleOptions SPARK = new DustParticleOptions(0x5EF2FF, 0.55f);
	private static ArmorModelSet<PlayerModel> armor;

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.UruGuardFx.TYPE, (fx, ctx) -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null) return;
			var e = mc.level.getEntity(fx.entityId());
			if (e != null) mc.particleEngine.createParticle(ModParticles.URU_GUARD, e.getX(), e.getY() + e.getBbHeight() * 0.62, e.getZ(), fx.entityId(), 0, 0);
		});
		ClientTickEvents.END_CLIENT_TICK.register(UruClient::tick);
	}

	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null || mc.isPaused() || mc.level.getGameTime() % 3 != 0) return;
		RandomSource rnd = mc.level.getRandom();
		Vec3 cam = mc.gameRenderer.getMainCamera().position();
		for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, new AABB(cam, cam).inflate(32), UruArmor::fullSet)) {
			if (e == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
			if (rnd.nextInt(3) != 0) continue;
			// A spark from one of the runes: chest gem, shoulders, knees.
			float yaw = e.yBodyRot * Mth.DEG_TO_RAD;
			Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), side = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
			Vec3 at = switch (rnd.nextInt(4)) {
				case 0 -> e.position().add(fwd.scale(0.22)).add(0, 1.18, 0);
				case 1 -> e.position().add(side.scale(0.42)).add(0, 1.38, 0);
				case 2 -> e.position().add(side.scale(-0.42)).add(0, 1.38, 0);
				default -> e.position().add(fwd.scale(0.2)).add(side.scale(rnd.nextBoolean() ? 0.12 : -0.12)).add(0, 0.45, 0);
			};
			mc.level.addParticle(SPARK, at.x + rnd.nextGaussian() * 0.05, at.y + rnd.nextGaussian() * 0.05, at.z + rnd.nextGaussian() * 0.05,
					0, 0.02, 0);
		}
	}

	/** First person: the chestplate sleeve, pauldron and vambrace on the arm drawn by the hand renderer. */
	public static void firstPersonArm(PlayerModel model, ModelPart arm, PoseStack pose, SubmitNodeCollector collector, int light) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !mc.player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.URU_CHESTPLATE)) return;
		if (armor == null) armor = ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, mc.getEntityModels(), part -> new PlayerModel(part, false));
		boolean right = arm == model.rightArm;
		ModelPart sleeve = right ? armor.chest().rightArm : armor.chest().leftArm;
		sleeve.x = arm.x;
		sleeve.y = arm.y;
		sleeve.z = arm.z;
		sleeve.xRot = arm.xRot;
		sleeve.yRot = arm.yRot;
		sleeve.zRot = arm.zRot;
		sleeve.visible = true;
		collector.submitModelPart(sleeve, pose, RenderTypes.armorCutoutNoCull(TEXTURE), light, OverlayTexture.NO_OVERLAY, null);
		UruDetailLayer.part(pose, collector, light, arm, right ? "pauldron_right" : "pauldron_left");
	}

	private UruClient() {}
}
