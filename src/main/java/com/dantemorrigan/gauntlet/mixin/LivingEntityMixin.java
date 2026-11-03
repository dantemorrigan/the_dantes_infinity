package com.dantemorrigan.gauntlet.mixin;

import com.dantemorrigan.gauntlet.GauntletEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	/** Gauntlet immunities: poison (inventory), harmful effects (awakened), blindness etc. (Mind stone). */
	@Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
	private void infinityGauntlet$blockEffects(MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof ServerPlayer player && GauntletEffects.blocksEffect(player, effect)) {
			cir.setReturnValue(false);
		}
	}
}
