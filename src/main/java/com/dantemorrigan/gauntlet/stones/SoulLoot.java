package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.ModItems;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.Map;

/** The map to the Temple of Soul turns up in chests (traders sell it via the villager_trade data). */
public final class SoulLoot {
	private static final Map<ResourceKey<LootTable>, Float> CHANCES = Map.of(
			BuiltInLootTables.IGLOO_CHEST, 0.6f,
			BuiltInLootTables.SHIPWRECK_MAP, 0.3f,
			BuiltInLootTables.STRONGHOLD_LIBRARY, 0.35f,
			BuiltInLootTables.VILLAGE_CARTOGRAPHER, 0.3f,
			BuiltInLootTables.SIMPLE_DUNGEON, 0.12f,
			BuiltInLootTables.ABANDONED_MINESHAFT, 0.08f);

	public static void init() {
		QuestLoot.distribute(CHANCES, ModItems.QUEST_SOUL_TEMPLE_MAP);
	}

	private SoulLoot() {}
}
