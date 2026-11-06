package com.dantemorrigan.gauntlet.mixin;

import com.dantemorrigan.gauntlet.stones.MindArena;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class MindArenaMovementMixin {
	@Shadow public ServerPlayer player;

	@Inject(method = "handleMovePlayer", at = @At("RETURN"))
	private void infinityGauntlet$stopBarrierCrossing(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
		MindArena.enforce(player);
		com.dantemorrigan.gauntlet.boss.TribunalEncounter.enforce(player);
	}

	@Inject(method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V", at = @At("HEAD"), cancellable = true)
	private void infinityGauntlet$stopLocalTeleport(PositionMoveRotation move, Set<Relative> relatives, CallbackInfo ci) {
		if (MindArena.blocksTeleport(player, player.level(), move.position(), relatives)) ci.cancel();
	}
}
