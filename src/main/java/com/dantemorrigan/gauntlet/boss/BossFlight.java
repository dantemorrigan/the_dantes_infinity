package com.dantemorrigan.gauntlet.boss;

import com.dantemorrigan.gauntlet.cosmos.CosmosTravel;
import com.dantemorrigan.gauntlet.doom.DoomArmor;
import com.dantemorrigan.gauntlet.stormbreaker.Stormbreaker;
import net.minecraft.server.level.ServerPlayer;

public final class BossFlight {
	public static boolean airborne(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && !player.onGround() && !player.isInWater()
				&& (player.getAbilities().flying || player.isFallFlying() || DoomArmor.jetsActive(player));
	}

	public static void interrupt(ServerPlayer player, double dropSpeed) {
		if (!airborne(player)) return;
		if (CosmosTravel.inCosmos(player)) {
			player.setDeltaMovement(player.getDeltaMovement().scale(.22));
		} else {
			if (Stormbreaker.isFlying(player)) Stormbreaker.releaseFlight(player);
			if (player.hasAttached(CosmosTravel.FLIGHT)) CosmosTravel.stop(player, false);
			DoomArmor.releaseJets(player);
			if (player.isFallFlying()) player.stopFallFlying();
			if (player.getAbilities().flying) {
				player.getAbilities().flying = false;
				player.onUpdateAbilities();
			}
			player.setDeltaMovement(player.getDeltaMovement().multiply(.35, 0, .35).add(0, -dropSpeed, 0));
		}
		player.hurtMarked = true;
	}

	private BossFlight() {}
}
