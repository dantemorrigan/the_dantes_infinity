package com.dantemorrigan.gauntlet.doom;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * An Infinity Gauntlet Doom has drained: its powers answer nothing until the time runs out. Kept in memory only, as a
 * fight-length effect; a server restart lifts it.
 */
public final class GauntletSilence {
	private static final Map<UUID, Long> UNTIL = new HashMap<>();

	private GauntletSilence() {}

	public static void silence(ServerPlayer player, int ticks) {
		UNTIL.put(player.getUUID(), player.level().getServer().overworld().getGameTime() + ticks);
	}

	public static boolean silenced(ServerPlayer player) {
		Long until = UNTIL.get(player.getUUID());
		if (until == null) return false;
		if (player.level().getServer().overworld().getGameTime() < until) return true;
		UNTIL.remove(player.getUUID());
		return false;
	}

	/** Seconds left, rounded up; 0 when the gauntlet answers again. */
	public static int secondsLeft(ServerPlayer player) {
		Long until = UNTIL.get(player.getUUID());
		if (until == null) return 0;
		long left = until - player.level().getServer().overworld().getGameTime();
		return left <= 0 ? 0 : (int) ((left + 19) / 20);
	}

	public static void lift(ServerPlayer player) { UNTIL.remove(player.getUUID()); }

	public static void clear() { UNTIL.clear(); }
}
