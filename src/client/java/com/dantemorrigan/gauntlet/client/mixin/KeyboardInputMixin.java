package com.dantemorrigan.gauntlet.client.mixin;

import com.dantemorrigan.gauntlet.client.cutscene.Cutscene;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Temporary movement lock while the destruction plays (released automatically when the scene ends). */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void infinityGauntlet$lock(CallbackInfo ci) {
		if (Cutscene.inputLocked() || com.dantemorrigan.gauntlet.client.fx.TimeRewindClient.inputLocked()) {
			((ClientInput) (Object) this).keyPresses = Input.EMPTY;
			((ClientInputAccessor) this).infinityGauntlet$setMoveVector(Vec2.ZERO);
		}
	}
}
