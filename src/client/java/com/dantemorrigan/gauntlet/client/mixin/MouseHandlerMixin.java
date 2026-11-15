package com.dantemorrigan.gauntlet.client.mixin;

import com.dantemorrigan.gauntlet.client.ability.AbilityScroll;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets the mouse wheel switch gauntlet / Doom armor abilities before the hotbar would take the scroll. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow @org.spongepowered.asm.mixin.Final private Minecraft minecraft;

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void infinityGauntlet$scroll(long window, double x, double y, CallbackInfo ci) {
		if (window == minecraft.getWindow().handle() && AbilityScroll.onMouseScroll(x, y)) ci.cancel();
	}
}
