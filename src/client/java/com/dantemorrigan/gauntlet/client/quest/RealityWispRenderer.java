package com.dantemorrigan.gauntlet.client.quest;

import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.stones.RealityWisp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** The fleeing Reality Stone: the mod's own stone model, spinning, full bright. */
public final class RealityWispRenderer extends EntityRenderer<RealityWisp, RealityWispRenderer.State> {
	public static final class State extends EntityRenderState {
		final ItemStackRenderState item = new ItemStackRenderState();
	}

	private final ItemModelResolver items;

	public RealityWispRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.items = context.getItemModelResolver();
	}

	@Override public State createRenderState() { return new State(); }

	@Override
	public void extractRenderState(RealityWisp wisp, State state, float partial) {
		super.extractRenderState(wisp, state, partial);
		items.updateForNonLiving(state.item, new ItemStack(ModItems.REALITY_STONE), ItemDisplayContext.GROUND, wisp);
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		pose.pushPose();
		pose.translate(0, 0.3 + Math.sin(state.ageInTicks * 0.25) * 0.08, 0);
		pose.mulPose(Axis.YP.rotationDegrees(state.ageInTicks * 14));
		pose.scale(1.6f, 1.6f, 1.6f);
		state.item.submit(pose, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
		pose.popPose();
		super.submit(state, pose, collector, camera);
	}
}
