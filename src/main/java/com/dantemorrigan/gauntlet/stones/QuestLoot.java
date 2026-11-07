package com.dantemorrigan.gauntlet.stones;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import java.util.Map;

/** Shared chest-loot injection for quest items: one extra pool with a single roll and a per-table chance. */
final class QuestLoot {
	/** Adds {@code item} to each built-in loot table listed in {@code chances}. */
	static void distribute(Map<ResourceKey<LootTable>, Float> chances, ItemLike item) {
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (source.isBuiltin()) add(builder, chances.get(key), item);
		});
	}

	static void add(LootTable.Builder builder, Float chance, ItemLike item) {
		if (chance == null) return;
		builder.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
				.when(LootItemRandomChanceCondition.randomChance(chance))
				.add(LootItem.lootTableItem(item)));
	}

	private QuestLoot() {}
}
