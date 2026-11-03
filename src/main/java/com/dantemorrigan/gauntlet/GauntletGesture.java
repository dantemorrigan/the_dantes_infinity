package com.dantemorrigan.gauntlet;

import com.dantemorrigan.gauntlet.net.ModNetwork;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

/** Server relay for the "admire the gauntlet" gesture: purely visual, so it only validates and forwards to viewers. */
public final class GauntletGesture {
	/** Gesture length in ticks; the client animation uses the same value. */
	public static final int DURATION = 90;

	private static final Map<ServerPlayer, Long> LAST = new WeakHashMap<>();

	public static void start(ServerPlayer player) {
		if (GauntletState.activeGauntlet(player).isEmpty() || player.isSpectator()) return;
		long now = player.level().getGameTime();
		Long last = LAST.get(player);
		if (last != null && now - last < DURATION - 10) return;
		LAST.put(player, now);
		ModNetwork.GestureFx fx = new ModNetwork.GestureFx(player.getId());
		for (ServerPlayer viewer : PlayerLookup.tracking(player)) {
			if (viewer != player) ServerPlayNetworking.send(viewer, fx);
		}
	}

	private GauntletGesture() {}
}
