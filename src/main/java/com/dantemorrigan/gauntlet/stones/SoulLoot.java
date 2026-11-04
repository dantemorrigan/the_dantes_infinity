package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.ModItems;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

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
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			Float chance = CHANCES.get(key);
			if (chance == null || !source.isBuiltin()) return;
			builder.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
					.when(LootItemRandomChanceCondition.randomChance(chance))
					.add(LootItem.lootTableItem(ModItems.QUEST_SOUL_TEMPLE_MAP)));
		});
	}

	private SoulLoot() {}
}
