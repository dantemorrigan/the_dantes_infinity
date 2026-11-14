package com.dantemorrigan.gauntlet.client.doom;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/**
 * What each item of The Fury of Doom is for, under its name: up to four short grey lines from
 * {@code tooltip.infinity_gauntlet.<id>.1..4}. The field guide says the same at length; this is the reminder in hand.
 */
public final class DoomTooltips {
	private static final java.util.Set<String> IDS = java.util.Set.of("doom_helmet", "doom_chestplate", "doom_leggings", "doom_boots",
			"doom_gauntlet", "doom_grimoire", "latveria_key", "castle_seal", "doombot_controller", "mechanical_heart", "time_platform",
			"arc_reactor", "doom_circuit", "jade_shard", "repair_tool");

	private DoomTooltips() {}

	public static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
			if (!id.getNamespace().equals("infinity_gauntlet") || !IDS.contains(id.getPath())) return;
			int at = Math.min(1, lines.size());
			for (int i = 1; i <= 4; i++) {
				String key = "tooltip.infinity_gauntlet." + id.getPath() + "." + i;
				if (!Language.getInstance().has(key)) break;
				lines.add(at++, Component.translatable(key).withStyle(ChatFormatting.GRAY));
			}
		});
	}
}
