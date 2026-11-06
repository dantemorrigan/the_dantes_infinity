package com.dantemorrigan.gauntlet.client.mixin;

import com.dantemorrigan.gauntlet.client.stormbreaker.StormbreakerClient;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(CloudRenderer.class)
abstract class StormCloudRendererMixin {
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int infinityGauntlet$darkenClouds(int color) {
        float storm = StormbreakerClient.activeSky(0);
        return storm <= 0 ? color : ARGB.srgbLerp(storm * .92f, color,
                ARGB.color(ARGB.alpha(color), 28, 36, 56));
    }
}
