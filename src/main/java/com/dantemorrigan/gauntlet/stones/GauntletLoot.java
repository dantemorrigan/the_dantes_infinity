package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.ModItems;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.Map;

/**
 * Distributes the Infinity Gauntlet Forging Schematic across village smiths, temples, houses,
 * dungeons, mineshafts, strongholds, ruins, mansions, ancient cities, bastions, and end cities.
 */
public final class GauntletLoot {
	private static final Map<ResourceKey<LootTable>, Float> CHANCES = Map.ofEntries(
			Map.entry(BuiltInLootTables.VILLAGE_WEAPONSMITH, 0.65f),
			Map.entry(BuiltInLootTables.VILLAGE_ARMORER, 0.65f),
			Map.entry(BuiltInLootTables.VILLAGE_TOOLSMITH, 0.6f),
			Map.entry(BuiltInLootTables.VILLAGE_CARTOGRAPHER, 0.5f),
			Map.entry(BuiltInLootTables.VILLAGE_TEMPLE, 0.45f),
			Map.entry(BuiltInLootTables.VILLAGE_PLAINS_HOUSE, 0.28f),
			Map.entry(BuiltInLootTables.VILLAGE_DESERT_HOUSE, 0.28f),
			Map.entry(BuiltInLootTables.VILLAGE_SAVANNA_HOUSE, 0.28f),
			Map.entry(BuiltInLootTables.VILLAGE_TAIGA_HOUSE, 0.28f),
			Map.entry(BuiltInLootTables.VILLAGE_SNOWY_HOUSE, 0.28f),
			Map.entry(BuiltInLootTables.STRONGHOLD_LIBRARY, 0.6f),
			Map.entry(BuiltInLootTables.STRONGHOLD_CORRIDOR, 0.45f),
			Map.entry(BuiltInLootTables.STRONGHOLD_CROSSING, 0.45f),
			Map.entry(BuiltInLootTables.SIMPLE_DUNGEON, 0.4f),
			Map.entry(BuiltInLootTables.ABANDONED_MINESHAFT, 0.35f),
			Map.entry(BuiltInLootTables.DESERT_PYRAMID, 0.45f),
			Map.entry(BuiltInLootTables.JUNGLE_TEMPLE, 0.45f),
			Map.entry(BuiltInLootTables.IGLOO_CHEST, 0.45f),
			Map.entry(BuiltInLootTables.RUINED_PORTAL, 0.4f),
			Map.entry(BuiltInLootTables.PILLAGER_OUTPOST, 0.4f),
			Map.entry(BuiltInLootTables.WOODLAND_MANSION, 0.55f),
			Map.entry(BuiltInLootTables.ANCIENT_CITY, 0.55f),
			Map.entry(BuiltInLootTables.SHIPWRECK_MAP, 0.45f),
			Map.entry(BuiltInLootTables.SHIPWRECK_TREASURE, 0.4f),
			Map.entry(BuiltInLootTables.BURIED_TREASURE, 0.5f),
			Map.entry(BuiltInLootTables.UNDERWATER_RUIN_BIG, 0.4f),
			Map.entry(BuiltInLootTables.UNDERWATER_RUIN_SMALL, 0.3f),
			Map.entry(BuiltInLootTables.NETHER_BRIDGE, 0.4f),
			Map.entry(BuiltInLootTables.BASTION_TREASURE, 0.6f),
			Map.entry(BuiltInLootTables.BASTION_OTHER, 0.35f),
			Map.entry(BuiltInLootTables.END_CITY_TREASURE, 0.5f));

	public static void init() {
		QuestLoot.distribute(CHANCES, ModItems.QUEST_GAUNTLET_BLUEPRINT);
	}

	private GauntletLoot() {}
}
