package com.dantemorrigan.gauntlet.client.stormbreaker;

import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.stormbreaker.Stormbreaker;
import com.dantemorrigan.gauntlet.stormbreaker.StormbreakerEntity;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

final class StormbreakerRenderer extends EntityRenderer<StormbreakerEntity, StormbreakerRenderer.State> {
    static final class State extends EntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        int phase;
        float phaseAge, heading, pitch;
    }

    private final ItemModelResolver models;
    private ItemStack stack;

    StormbreakerRenderer(EntityRendererProvider.Context context) {
        super(context);
        models = context.getItemModelResolver();
    }

    @Override public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(StormbreakerEntity entity, State state, float partial) {
        super.extractRenderState(entity, state, partial);
        state.phase = entity.phase();
        state.phaseAge = entity.phaseAge(partial);
        state.heading = entity.getYRot();
        state.pitch = entity.getXRot();
        if (stack == null) {
            // Renderer providers run during the initial reload, before item components are bound.
            stack = new ItemStack(ModItems.STORMBREAKER);
            stack.set(DataComponents.ITEM_MODEL, Stormbreaker.CHARGED);
        }
        models.updateForNonLiving(state.item, stack, ItemDisplayContext.NONE, entity);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.scale(.84f, .84f, .84f);
        if (state.phase == StormbreakerEntity.EMBEDDED_BLADE || state.phase == StormbreakerEntity.PULLING_BLADE) {
            float pull = state.phase == StormbreakerEntity.PULLING_BLADE ? Math.min(1, state.phaseAge / 12) : 0;
            pose.mulPose(Axis.YP.rotationDegrees(-state.heading));
            pose.mulPose(Axis.XP.rotationDegrees(state.pitch));
            pose.translate(0, 0, .08 - .85 * pull * pull);
            pose.mulPose(Axis.YP.rotationDegrees(90));
            // The thin cutting edge is at model x=-11, y=23, z=8, relative to the item origin (8,8,8).
            pose.translate(19f / 16, -15f / 16, 0);
        } else if (state.phase == StormbreakerEntity.EMBEDDED || state.phase == StormbreakerEntity.PLANTING
                || state.phase == StormbreakerEntity.PULLING) {
            float settling = state.phase == StormbreakerEntity.PLANTING ? Math.max(0, 1 - state.phaseAge / 10) : 0;
            float pull = state.phase == StormbreakerEntity.PULLING ? Math.min(1, state.phaseAge / 12) : 0;
            pose.translate(0, .7 * settling * settling + .85 * pull * pull, 0);
            pose.mulPose(Axis.YP.rotationDegrees(25));
            pose.mulPose(Axis.ZP.rotationDegrees(176 - 37 * settling - 80 * pull * pull));
            // Model origin is at (8, 8, 8); the upper cutting edge touches the terrain.
            pose.translate(15f / 16, -22.8f / 16, 0);
        } else if (state.phase == StormbreakerEntity.SKYFALL) {
            pose.mulPose(Axis.YP.rotationDegrees(28));
            pose.mulPose(Axis.ZP.rotationDegrees(state.ageInTicks * -30));
        } else {
            pose.mulPose(Axis.YP.rotationDegrees(-state.heading));
            pose.mulPose(Axis.XP.rotationDegrees(84 + state.pitch));
            pose.mulPose(Axis.ZP.rotationDegrees(state.ageInTicks * -33));
        }
        state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
        super.submit(state, pose, collector, camera);
    }
}
