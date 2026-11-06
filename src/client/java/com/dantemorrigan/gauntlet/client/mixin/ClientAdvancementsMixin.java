package com.dantemorrigan.gauntlet.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.HashSet;
import java.util.Set;

/** Incremental tree visibility updates can contain already-completed progress again after login. */
@Mixin(ClientAdvancements.class)
abstract class ClientAdvancementsMixin {
    @Unique private final Set<Identifier> infinityGauntlet$completed=new HashSet<>();
    @Inject(method="update",at=@At("HEAD"))
    private void infinityGauntlet$revoked(ClientboundUpdateAdvancementsPacket packet,CallbackInfo ci) {
        packet.getProgress().forEach((id,progress)->{if(!progress.isDone())infinityGauntlet$completed.remove(id);});
    }
    @ModifyExpressionValue(method="update",at=@At(value="INVOKE",target="Lnet/minecraft/advancements/DisplayInfo;shouldShowToast()Z"))
    private boolean infinityGauntlet$newCompletion(boolean show,@Local AdvancementNode node) {
        Identifier id=node.holder().id();
        return show && (!id.getNamespace().equals("infinity_gauntlet") || !infinityGauntlet$completed.contains(id));
    }
    @Inject(method="update",at=@At("TAIL"))
    private void infinityGauntlet$remember(ClientboundUpdateAdvancementsPacket packet,CallbackInfo ci) {
        packet.getProgress().forEach((id,progress)->{if(progress.isDone())infinityGauntlet$completed.add(id);});
    }
}
