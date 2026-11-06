package com.dantemorrigan.gauntlet.mixin;

import com.dantemorrigan.gauntlet.stones.MindArena;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
abstract class MindArenaTeleportMixin {
	@Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
			at = @At("HEAD"), cancellable = true)
	private void infinityGauntlet$keepPlayersInMindArena(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> cir) {
		if (MindArena.blocksTeleport((ServerPlayer) (Object) this, transition)) cir.setReturnValue(null);
	}
}
