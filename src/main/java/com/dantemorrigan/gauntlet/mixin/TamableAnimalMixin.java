package com.dantemorrigan.gauntlet.mixin;

import com.dantemorrigan.gauntlet.stones.SoulTemple;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.TamableAnimal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TamableAnimal.class)
public abstract class TamableAnimalMixin {
	@Inject(method = "tryToTeleportToOwner", at = @At("HEAD"), cancellable = true)
	private void infinityGauntlet$keepPetInSoulChasm(CallbackInfo ci) {
		TamableAnimal animal = (TamableAnimal) (Object) this;
		if (!(animal.level() instanceof ServerLevel level) || level != level.getServer().overworld()
				|| animal.onGround() || !SoulTemple.built(level.getServer())) return;

		var site = SoulTemple.site(level.getServer());
		// Include the first few blocks below the lip, before the regular ritual detection volume.
		if (animal.getY() < SoulTemple.summitY(site)
				&& SoulTemple.chasm(site).inflate(0, 3, 0).contains(animal.position())) {
			ci.cancel();
		}
	}
}
