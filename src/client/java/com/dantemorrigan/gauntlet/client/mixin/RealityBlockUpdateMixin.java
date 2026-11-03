package com.dantemorrigan.gauntlet.client.mixin;

import com.dantemorrigan.gauntlet.client.fx.RealityClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class RealityBlockUpdateMixin {
    @Inject(method = "handleBlockUpdate", at = @At("RETURN"))
    private void infinityGauntlet$keepBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        RealityClient.invalidateHiddenBlock(packet.getPos());
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("RETURN"))
    private void infinityGauntlet$keepSectionUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        packet.runUpdates((pos, state) -> RealityClient.invalidateHiddenBlock(pos));
    }
}
