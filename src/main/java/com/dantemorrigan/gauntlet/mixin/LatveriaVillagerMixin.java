package com.dantemorrigan.gauntlet.mixin;

import com.dantemorrigan.gauntlet.doom.Latveria;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Latveria is full of townsfolk. Those nobody is near think a quarter as often (their brain, pathfinding and job search
 * are the dearest part of a villager), so the city stays alive without eating the server tick.
 */
@Mixin(Villager.class)
public abstract class LatveriaVillagerMixin {
	@Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
	private void infinityGauntlet$thinkLess(ServerLevel level, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		if (level.dimension() != Latveria.LEVEL || self.tickCount % 4 == 0) return;
		if (level.getNearestPlayer(self, 24) == null) ci.cancel();
	}
}
