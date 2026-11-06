package com.dantemorrigan.gauntlet;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;

/** Grants custom mod advancements whose criteria use {@code minecraft:impossible}. */
public final class ModAdvancements {
	public static boolean completed(ServerPlayer player,String id) {
        var holder=player.level().getServer().getAdvancements().get(InfinityGauntlet.id(id));
        return holder!=null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }
    public static void award(ServerPlayer player, String id) {
		if (player == null || player.level().getServer() == null) return;
		AdvancementHolder holder = player.level().getServer().getAdvancements().get(InfinityGauntlet.id(id));
		if (holder == null || player.getAdvancements().getOrStartProgress(holder).isDone()) return;
		for (String criterion : holder.value().criteria().keySet()) {
			player.getAdvancements().award(holder, criterion);
		}
	}

	private ModAdvancements() {}
}
