package com.dantemorrigan.gauntlet.mixin;

import com.dantemorrigan.gauntlet.GauntletEffects;
import com.dantemorrigan.gauntlet.Stone;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobMixin {
	/** Mind stone: mobs don't pick the player as a target unless the player attacked them. */
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void infinityGauntlet$mindStone(@Nullable LivingEntity target, CallbackInfo ci) {
		if (target instanceof ServerPlayer player
				&& GauntletEffects.hasPassive(player, Stone.MIND)
				&& ((Mob) (Object) this).getLastHurtByMob() != player) {
			ci.cancel();
		}
	}
}
