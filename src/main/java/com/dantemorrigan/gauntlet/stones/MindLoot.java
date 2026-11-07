package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.ModItems;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.Map;

/** Distributes the Map to the Ruined City across village, ruin, temple, dungeon, and stronghold chests. */
public final class MindLoot {
	private static final Map<ResourceKey<LootTable>, Float> MAP_CHANCES = Map.ofEntries(
			Map.entry(BuiltInLootTables.VILLAGE_CARTOGRAPHER, 0.5f),
			Map.entry(BuiltInLootTables.VILLAGE_TEMPLE, 0.4f),
			Map.entry(BuiltInLootTables.VILLAGE_PLAINS_HOUSE, 0.22f),
			Map.entry(BuiltInLootTables.VILLAGE_DESERT_HOUSE, 0.22f),
			Map.entry(BuiltInLootTables.VILLAGE_SAVANNA_HOUSE, 0.22f),
			Map.entry(BuiltInLootTables.VILLAGE_TAIGA_HOUSE, 0.22f),
			Map.entry(BuiltInLootTables.VILLAGE_SNOWY_HOUSE, 0.22f),
			Map.entry(BuiltInLootTables.SHIPWRECK_MAP, 0.4f),
			Map.entry(BuiltInLootTables.UNDERWATER_RUIN_BIG, 0.4f),
			Map.entry(BuiltInLootTables.UNDERWATER_RUIN_SMALL, 0.28f),
			Map.entry(BuiltInLootTables.RUINED_PORTAL, 0.3f),
			Map.entry(BuiltInLootTables.DESERT_PYRAMID, 0.35f),
			Map.entry(BuiltInLootTables.JUNGLE_TEMPLE, 0.4f),
			Map.entry(BuiltInLootTables.STRONGHOLD_LIBRARY, 0.45f),
			Map.entry(BuiltInLootTables.SIMPLE_DUNGEON, 0.25f),
			Map.entry(BuiltInLootTables.ABANDONED_MINESHAFT, 0.22f),
			Map.entry(BuiltInLootTables.ANCIENT_CITY, 0.4f),
			Map.entry(BuiltInLootTables.WOODLAND_MANSION, 0.4f),
			Map.entry(BuiltInLootTables.PILLAGER_OUTPOST, 0.3f));

	public static void init() {
		QuestLoot.distribute(MAP_CHANCES, ModItems.QUEST_MIND_CITY_MAP);
	}

	private MindLoot() {}
}
