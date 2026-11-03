package com.dantemorrigan.gauntlet.client.render;

import com.dantemorrigan.gauntlet.entity.GauntletDoll;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

/** Draws the Steve doll with the player model and the default Steve skin; the gauntlet is shown in its off hand. */
public class GauntletDollRenderer extends LivingEntityRenderer<GauntletDoll, AvatarRenderState, PlayerModel> {
	public GauntletDollRenderer(EntityRendererProvider.Context context) {
		super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
		this.addLayer(new PlayerItemInHandLayer<>(this));
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new AvatarRenderState();
	}

	@Override
	public Identifier getTextureLocation(AvatarRenderState state) {
		return state.skin.body().texturePath();
	}

	@Override
	protected void scale(AvatarRenderState state, PoseStack poseStack) {
		poseStack.scale(0.9375F, 0.9375F, 0.9375F);
	}

	@Override
	public void extractRenderState(GauntletDoll doll, AvatarRenderState state, float partialTicks) {
		super.extractRenderState(doll, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(doll, state, partialTicks, this.itemModelResolver);
		state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
		state.leftArmPose = doll.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		state.id = doll.getId();
		state.isSpectator = false;
	}
}
