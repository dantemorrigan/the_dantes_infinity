package com.dantemorrigan.gauntlet.client.mixin;

import com.dantemorrigan.gauntlet.client.lang.ModLang;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets the mod's language switch override translations of the mod's own keys. */
@Mixin(ClientLanguage.class)
public abstract class ClientLanguageMixin {
	@Inject(method = "getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
	private void infinityGauntlet$override(String key, String fallback, CallbackInfoReturnable<String> cir) {
		String value = ModLang.override(key);
		if (value != null) cir.setReturnValue(value);
	}
}
