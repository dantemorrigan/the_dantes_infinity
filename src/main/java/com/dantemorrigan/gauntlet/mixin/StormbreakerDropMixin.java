package com.dantemorrigan.gauntlet.mixin;

import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.stormbreaker.Stormbreaker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
abstract class StormbreakerDropMixin {
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void infinityGauntlet$plantDroppedAxe(ItemStack stack, boolean random, boolean retainOwnership,
            CallbackInfoReturnable<ItemEntity> cir) {
        if (stack.is(ModItems.STORMBREAKER) && Stormbreaker.drop((ServerPlayer) (Object) this, stack)) {
            cir.setReturnValue(null);
        }
    }
}
