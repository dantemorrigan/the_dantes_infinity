package com.dantemorrigan.gauntlet.mixin;

import com.dantemorrigan.gauntlet.stones.StoneKeeper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pulls the stones out of a dead player's inventory before it is scattered (unless a player made the kill). */
@Mixin(Player.class)
public abstract class PlayerMixin {
	@Inject(method = "dropEquipment", at = @At("HEAD"))
	private void infinityGauntlet$keepStones(ServerLevel level, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) StoneKeeper.beforeDrop(player);
	}
}
