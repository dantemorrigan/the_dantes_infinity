package com.dantemorrigan.gauntlet.client.mixin;

import com.dantemorrigan.gauntlet.client.MusicDuck;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
abstract class SoundEngineMixin {
	/** The engine re-evaluates every playing sound each tick, so a changing factor gives a smooth fade of the vanilla soundtrack. */
	@Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
	private void infinityGauntlet$duckMusic(SoundInstance instance, CallbackInfoReturnable<Float> cir) {
		if (instance.getSource() == SoundSource.MUSIC) cir.setReturnValue(cir.getReturnValueF() * MusicDuck.factor());
	}
}
