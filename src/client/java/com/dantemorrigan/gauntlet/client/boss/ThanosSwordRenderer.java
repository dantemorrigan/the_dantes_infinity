package com.dantemorrigan.gauntlet.client.boss;

import com.dantemorrigan.gauntlet.boss.ThanosSword;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** The thrown double sword: spinning flat around the center of its grip, tilted a little like a thrown disc. */
public final class ThanosSwordRenderer extends EntityRenderer<ThanosSword, EntityRenderState> {
	public ThanosSwordRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public EntityRenderState createRenderState() {
		return new EntityRenderState();
	}

	@Override
	public void submit(EntityRenderState s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		pose.pushPose();
		pose.translate(0, 0.25, 0);
		pose.mulPose(Axis.ZP.rotationDegrees(12));
		pose.mulPose(Axis.YP.rotationDegrees(s.ageInTicks * 50f));
		GeoModel sword = ThanosModels.sword();
		int light = s.lightCoords;
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(ThanosRenderer.ATLAS), (p, out) ->
				sword.render(ThanosRenderer.stack(p), out, null, light, OverlayTexture.NO_OVERLAY, null));
		pose.popPose();
		super.submit(s, pose, collector, camera);
	}
}
