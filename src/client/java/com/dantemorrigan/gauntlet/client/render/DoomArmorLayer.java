package com.dantemorrigan.gauntlet.client.render;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.client.boss.GeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * Doom's armor on a player (or any humanoid) is Doom himself: the very model, texture and cloak of the boss, scaled
 * to a player's height and cut along the body parts, each piece riding its model part. The vanilla armor layer draws
 * nothing for these items, and the wearer's own skin under a piece is hidden (see HumanoidModelMixin), so there is
 * not a pixel of difference from the Doctor Doom of the throne room.
 */
public class DoomArmorLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
	/** Doom is 39 units tall, a player 32. */
	public static final float SCALE = 32f / 39f;
	private static final Identifier TEXTURE = InfinityGauntlet.id("textures/entity/doctor_doom/doctor_doom.png");
	/** Worn with the whole Infinity Gauntlet, the armor is God Emperor Doom's: ivory, silver and gold. */
	private static final Identifier GOD_TEXTURE = InfinityGauntlet.id("textures/entity/doctor_doom/god_emperor_doom.png");

	/** A working Infinity Gauntlet holding all six stones. */
	public static boolean wholeGauntlet(net.minecraft.world.item.ItemStack stack) {
		return stack.is(ModItems.GAUNTLET) && !Boolean.TRUE.equals(stack.get(com.dantemorrigan.gauntlet.ModComponents.SNAPPED))
				&& com.dantemorrigan.gauntlet.GauntletState.inserted(stack).size() >= 6;
	}
	/** Doom's points that sit on each part's pivot: the neck, the shoulders and the hips (Doom units, Y up). */
	private static final float[] NECK = {0, 29.1f, 0}, SHOULDER_GEO_NEG = {-6.28f, 26.65f, 0}, SHOULDER_GEO_POS = {6.28f, 26.65f, 0},
			HIP_GEO_NEG = {-2.39f, 15.25f, 0}, HIP_GEO_POS = {2.39f, 15.25f, 0};
	/** Below this height (Doom units) a leg cube belongs to the boots. */
	private static final float BOOT_TOP = 2.8f;
	/**
	 * Below this height (Doom units) an arm cube is the forearm and the hand: under the Infinity Gauntlet they are left
	 * out, so Doom's fingers never poke through it. The gauntlet wraps the player's arm from about 5 px under the shoulder.
	 */
	private static final float WRIST_TOP = 20.6f;

	public enum Group { HEAD, BODY, ARM_GEO_NEG, ARM_GEO_POS, LEG_GEO_NEG, LEG_GEO_POS }

	private static final class Model {
		static final GeoModel GEO = GeoModel.load(InfinityGauntlet.id("doctor_doom/doctor_doom.geo.json"));
		static final Map<GeoModel.Bone, Group> GROUPS = new HashMap<>();
		static {
			for (GeoModel.Bone bone : GEO.bones()) {
				Group g = Group.BODY;
				for (GeoModel.Bone b = bone; b != null; b = b.parent) {
					Group found = switch (b.name) {
						case "head" -> Group.HEAD;
						case "arm_r" -> Group.ARM_GEO_NEG;
						case "arm_l" -> Group.ARM_GEO_POS;
						case "leg_r" -> Group.LEG_GEO_NEG;
						case "leg_l" -> Group.LEG_GEO_POS;
						default -> null;
					};
					if (found != null) { g = found; break; }
				}
				GROUPS.put(bone, g);
			}
		}
	}

	public DoomArmorLayer(RenderLayerParent<S, M> parent) {
		super(parent);
	}

	public static boolean any(HumanoidRenderState s) {
		return s.headEquipment.is(ModItems.DOOM_HELMET) || s.chestEquipment.is(ModItems.DOOM_CHESTPLATE)
				|| s.legsEquipment.is(ModItems.DOOM_LEGGINGS) || s.feetEquipment.is(ModItems.DOOM_BOOTS);
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
		if (!any(state)) return;
		M model = this.getParentModel();
		boolean head = state.headEquipment.is(ModItems.DOOM_HELMET), chest = state.chestEquipment.is(ModItems.DOOM_CHESTPLATE);
		boolean legs = state.legsEquipment.is(ModItems.DOOM_LEGGINGS), boots = state.feetEquipment.is(ModItems.DOOM_BOOTS);
		int overlay = OverlayTexture.pack(OverlayTexture.u(0), OverlayTexture.v(state.hasRedOverlay));
		Map<String, GeoModel.BonePose> bones = cloak(state);
		Identifier tex = head && chest && legs && boots && (wholeGauntlet(state.leftHandItemStack) || wholeGauntlet(state.rightHandItemStack)) ? GOD_TEXTURE : TEXTURE;
		if (head) piece(pose, collector, light, overlay, model.head, NECK, Group.HEAD, bones, true, true, false, tex);
		if (chest) {
			piece(pose, collector, light, overlay, model.body, NECK, Group.BODY, bones, true, true, false, tex);
			// Doom's arm on the -X side of his model is the one on the player's left (+X in model-part space).
			piece(pose, collector, light, overlay, model.leftArm, SHOULDER_GEO_NEG, Group.ARM_GEO_NEG, bones, true, true,
					state.leftHandItemStack.is(ModItems.GAUNTLET), tex);
			piece(pose, collector, light, overlay, model.rightArm, SHOULDER_GEO_POS, Group.ARM_GEO_POS, bones, true, true,
					state.rightHandItemStack.is(ModItems.GAUNTLET), tex);
		}
		if (legs || boots) {
			piece(pose, collector, light, overlay, model.leftLeg, HIP_GEO_NEG, Group.LEG_GEO_NEG, bones, legs, boots, false, tex);
			piece(pose, collector, light, overlay, model.rightLeg, HIP_GEO_POS, Group.LEG_GEO_POS, bones, legs, boots, false, tex);
		}
	}

	/** The cloak's motion: it trails behind with speed, ripples, and lifts when crouching. */
	private static Map<String, GeoModel.BonePose> cloak(HumanoidRenderState s) {
		Map<String, GeoModel.BonePose> bones = new HashMap<>();
		float speed = Math.min(1, s.walkAnimationSpeed), t = s.ageInTicks;
		float back = Math.min(26, 4 + speed * 20 + (s.isCrouching ? 10 : 0) + (s.isFallFlying ? 18 : 0));
		pose(bones, "cape", back + Mth.sin(t * 0.09f) * 1.5f, 0, Mth.sin(t * 0.05f) * 1.0f);
		pose(bones, "cape_mid", speed * 5 + Mth.sin(t * 0.13f - 0.8f) * (1.5f + speed * 2.5f), 0, Mth.sin(t * 0.07f) * 1.2f);
		pose(bones, "cape_low", speed * 4 + Mth.sin(t * 0.16f - 1.7f) * (2 + speed * 3.5f), 0, Mth.sin(t * 0.09f + 1) * 1.5f);
		pose(bones, "cape_wing_r", back * 0.12f, 0, 1.5f + speed * 4 + Mth.sin(t * 0.12f) * 1.5f);
		pose(bones, "cape_wing_l", back * 0.12f, 0, -(1.5f + speed * 4 + Mth.sin(t * 0.12f + 2) * 1.5f));
		return bones;
	}

	private static void pose(Map<String, GeoModel.BonePose> bones, String name, float rx, float ry, float rz) {
		GeoModel.BonePose p = new GeoModel.BonePose();
		p.rx = rx; p.ry = ry; p.rz = rz;
		bones.put(name, p);
	}

	/**
	 * Draws Doom's bones of one group in the frame of a model part. In that frame Y points down and X is mirrored
	 * against the Bedrock model, which is a half turn around Z; then Doom's {@code anchor} is moved onto the pivot.
	 */
	public static void piece(PoseStack pose, SubmitNodeCollector collector, int light, int overlay, ModelPart part, float[] anchor, Group group,
			Map<String, GeoModel.BonePose> bones, boolean upper, boolean boots) {
				piece(pose, collector, light, overlay, part, anchor, group, bones, upper, boots, false, TEXTURE);
			}

			public static void piece(PoseStack pose, SubmitNodeCollector collector, int light, int overlay, ModelPart part, float[] anchor, Group group,
					Map<String, GeoModel.BonePose> bones, boolean upper, boolean boots, boolean underGauntlet, Identifier texture) {
				if (!part.visible) return;
		pose.pushPose();
		part.translateAndRotate(pose);
		pose.mulPose(Axis.ZP.rotationDegrees(180));
		pose.scale(SCALE, SCALE, SCALE);
		pose.translate(-anchor[0] / 16f, -anchor[1] / 16f, -anchor[2] / 16f);
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(texture), (p, out) -> {
			PoseStack s = new PoseStack();
			s.last().pose().set(p.pose());
			s.last().normal().set(p.normal());
			Model.GEO.render(s, out, bones, light, overlay, (bone, cube) -> {
				if (Model.GROUPS.get(bone) != group) return 0;
				boolean leg = group == Group.LEG_GEO_NEG || group == Group.LEG_GEO_POS;
				if (leg && !(cube.centerY < BOOT_TOP ? boots : upper)) return 0;
				if (underGauntlet && cube.centerY < WRIST_TOP) return 0;
				return -1;
			});
		});
		pose.popPose();
	}

	/** First person: Doom's arm on the arm the hand renderer draws. */
	public static void firstPersonArm(PoseStack pose, SubmitNodeCollector collector, int light, ModelPart arm, boolean rightArm) {
		piece(pose, collector, light, OverlayTexture.NO_OVERLAY, arm, rightArm ? SHOULDER_GEO_POS : SHOULDER_GEO_NEG,
				rightArm ? Group.ARM_GEO_POS : Group.ARM_GEO_NEG, cloakless(), true, true, WornGauntlet.firstPersonStack != null,
						net.minecraft.client.Minecraft.getInstance().player != null && wholeGauntlet(net.minecraft.client.Minecraft.getInstance().player.getOffhandItem())
				&& com.dantemorrigan.gauntlet.client.doom.DoomClient.fullSet() ? GOD_TEXTURE : TEXTURE);
	}

	private static Map<String, GeoModel.BonePose> cloakless() { return new HashMap<>(); }
}
