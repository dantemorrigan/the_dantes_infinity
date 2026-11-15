package com.dantemorrigan.gauntlet.client.doom;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.doom.LatveriaNpc;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Renders Latverian Quest NPCs (Old Mechanic Radovan and Tavern Keeper Mirko)
 * using the standard Minecraft player model and custom 64x64 Latverian player skins.
 */
public class LatveriaNpcRenderer extends LivingEntityRenderer<LatveriaNpc, AvatarRenderState, PlayerModel> {
	public static final Identifier MECHANIC_SKIN = InfinityGauntlet.id("textures/entity/latveria/mechanic.png");
	public static final Identifier TAVERN_SKIN = InfinityGauntlet.id("textures/entity/latveria/tavern_keeper.png");
	public static final Identifier[] CITIZEN_SKINS = {
			InfinityGauntlet.id("textures/entity/latveria/citizen_1.png"),
			InfinityGauntlet.id("textures/entity/latveria/citizen_2.png"),
			InfinityGauntlet.id("textures/entity/latveria/citizen_3.png"),
			InfinityGauntlet.id("textures/entity/latveria/citizen_4.png")
	};

	private static final PlayerSkin MECHANIC_PLAYER_SKIN = new PlayerSkin(
			new ClientAsset.ResourceTexture(MECHANIC_SKIN, MECHANIC_SKIN), null, null, PlayerModelType.WIDE, true);
	private static final PlayerSkin TAVERN_PLAYER_SKIN = new PlayerSkin(
			new ClientAsset.ResourceTexture(TAVERN_SKIN, TAVERN_SKIN), null, null, PlayerModelType.WIDE, true);

	public static class State extends AvatarRenderState {
		public int role;
		public boolean bowing;
		public int talkTicks;
	}

	public LatveriaNpcRenderer(EntityRendererProvider.Context context) {
		super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false) {
			@Override
			public void setupAnim(AvatarRenderState s) {
				super.setupAnim(s);
				if (s instanceof State st) {
					if (st.bowing) {
						this.head.xRot += 0.45F;
						this.rightArm.xRot = -0.45F;
						this.rightArm.yRot = -0.25F;
					} else if (st.talkTicks > 0) {
						float wave = Mth.sin(s.ageInTicks * 0.45F);
						this.head.xRot += wave * 0.08F;
						this.rightArm.xRot += wave * 0.18F - 0.2F;
						this.leftArm.xRot -= wave * 0.12F + 0.1F;
					}
				}
			}
		}, 0.5F);
		this.addLayer(new HumanoidArmorLayer<>(this,
				ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), part -> new PlayerModel(part, false)),
				context.getEquipmentRenderer()));
		this.addLayer(new PlayerItemInHandLayer<>(this));
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new State();
	}

	public static Identifier skinForRole(int role) {
		return switch (role) {
			case LatveriaNpc.ROLE_MECHANIC -> MECHANIC_SKIN;
			case LatveriaNpc.ROLE_TAVERN_KEEPER -> TAVERN_SKIN;
			default -> CITIZEN_SKINS[Math.floorMod(role - LatveriaNpc.ROLE_CITIZEN_1, CITIZEN_SKINS.length)];
		};
	}

	@Override
	public Identifier getTextureLocation(AvatarRenderState state) {
		return state instanceof State st ? skinForRole(st.role) : state.skin.body().texturePath();
	}

	@Override
	protected RenderType getRenderType(AvatarRenderState state, boolean bodyVisible, boolean translucent, boolean glowing) {
		if (bodyVisible) {
			return RenderTypes.entityTranslucent(getTextureLocation(state));
		}
		return super.getRenderType(state, bodyVisible, translucent, glowing);
	}

	@Override
	protected void scale(AvatarRenderState state, PoseStack poseStack) {
		poseStack.scale(0.9375F, 0.9375F, 0.9375F);
	}

	@Override
	public void extractRenderState(LatveriaNpc npc, AvatarRenderState s, float partialTicks) {
		super.extractRenderState(npc, s, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(npc, s, partialTicks, this.itemModelResolver);
		int role = npc.role();
		if (s instanceof State state) {
			state.role = role;
			state.bowing = npc.bowing();
			state.talkTicks = npc.talkTicks();
		}
		s.skin = role == LatveriaNpc.ROLE_TAVERN_KEEPER ? TAVERN_PLAYER_SKIN : MECHANIC_PLAYER_SKIN;
		s.rightArmPose = npc.getMainHandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		s.leftArmPose = npc.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		s.id = npc.getId();
		s.isSpectator = false;
		s.showHat = true;
		s.showJacket = true;
		s.showLeftPants = true;
		s.showRightPants = true;
		s.showLeftSleeve = true;
		s.showRightSleeve = true;
	}
}
