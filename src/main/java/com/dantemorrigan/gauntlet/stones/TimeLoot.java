package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import java.util.Map;

/**
 * Distributes the Map to the Temple of Time (villages, ruins, temples, dungeons, traders)
 * and the three Ancient Gears (Overworld, Nether, End) across chest loot tables and mob drops.
 */
public final class TimeLoot {
	private static final Map<ResourceKey<LootTable>, Float> MAP_CHANCES = Map.ofEntries(
			Map.entry(BuiltInLootTables.VILLAGE_CARTOGRAPHER, 0.55f),
			Map.entry(BuiltInLootTables.VILLAGE_TEMPLE, 0.45f),
			Map.entry(BuiltInLootTables.VILLAGE_PLAINS_HOUSE, 0.25f),
			Map.entry(BuiltInLootTables.VILLAGE_DESERT_HOUSE, 0.25f),
			Map.entry(BuiltInLootTables.VILLAGE_SAVANNA_HOUSE, 0.25f),
			Map.entry(BuiltInLootTables.VILLAGE_TAIGA_HOUSE, 0.25f),
			Map.entry(BuiltInLootTables.VILLAGE_SNOWY_HOUSE, 0.25f),
			Map.entry(BuiltInLootTables.SHIPWRECK_MAP, 0.4f),
			Map.entry(BuiltInLootTables.UNDERWATER_RUIN_BIG, 0.45f),
			Map.entry(BuiltInLootTables.UNDERWATER_RUIN_SMALL, 0.3f),
			Map.entry(BuiltInLootTables.RUINED_PORTAL, 0.35f),
			Map.entry(BuiltInLootTables.DESERT_PYRAMID, 0.35f),
			Map.entry(BuiltInLootTables.JUNGLE_TEMPLE, 0.35f),
			Map.entry(BuiltInLootTables.STRONGHOLD_LIBRARY, 0.4f),
			Map.entry(BuiltInLootTables.SIMPLE_DUNGEON, 0.25f),
			Map.entry(BuiltInLootTables.ABANDONED_MINESHAFT, 0.2f),
			Map.entry(BuiltInLootTables.ANCIENT_CITY, 0.35f));

	private static final Map<ResourceKey<LootTable>, Float> OVERWORLD_GEAR_CHANCES = Map.ofEntries(
			Map.entry(BuiltInLootTables.STRONGHOLD_CORRIDOR, 0.45f),
			Map.entry(BuiltInLootTables.STRONGHOLD_CROSSING, 0.45f),
			Map.entry(BuiltInLootTables.STRONGHOLD_LIBRARY, 0.4f),
			Map.entry(BuiltInLootTables.SIMPLE_DUNGEON, 0.35f),
			Map.entry(BuiltInLootTables.ABANDONED_MINESHAFT, 0.3f),
			Map.entry(BuiltInLootTables.DESERT_PYRAMID, 0.4f),
			Map.entry(BuiltInLootTables.JUNGLE_TEMPLE, 0.4f),
			Map.entry(BuiltInLootTables.ANCIENT_CITY, 0.5f),
			Map.entry(BuiltInLootTables.PILLAGER_OUTPOST, 0.35f),
			Map.entry(BuiltInLootTables.WOODLAND_MANSION, 0.45f),
			Map.entry(BuiltInLootTables.UNDERWATER_RUIN_BIG, 0.35f),
			Map.entry(BuiltInLootTables.RUINED_PORTAL, 0.3f));

	private static final Map<ResourceKey<LootTable>, Float> NETHER_GEAR_CHANCES = Map.of(
			BuiltInLootTables.NETHER_BRIDGE, 0.5f,
			BuiltInLootTables.BASTION_TREASURE, 0.65f,
			BuiltInLootTables.BASTION_OTHER, 0.4f,
			BuiltInLootTables.BASTION_BRIDGE, 0.45f,
			BuiltInLootTables.BASTION_HOGLIN_STABLE, 0.4f);

	private static final Map<ResourceKey<LootTable>, Float> END_GEAR_CHANCES = Map.of(
			BuiltInLootTables.END_CITY_TREASURE, 0.6f);

	public static void init() {
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (!source.isBuiltin()) return;
			addIfPresent(builder, MAP_CHANCES.get(key), ModItems.QUEST_TIME_TEMPLE_MAP);
			addIfPresent(builder, OVERWORLD_GEAR_CHANCES.get(key), ModItems.QUEST_TIME_GEAR_OVERWORLD);
			addIfPresent(builder, NETHER_GEAR_CHANCES.get(key), ModItems.QUEST_TIME_GEAR_NETHER);
			addIfPresent(builder, END_GEAR_CHANCES.get(key), ModItems.QUEST_TIME_GEAR_END);
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (!(entity instanceof Mob) || !(entity.level() instanceof ServerLevel level)) return;
			boolean playerKill = damageSource.getEntity() instanceof Player || entity.getLastHurtByMob() instanceof Player;
			if (!playerKill && !(entity instanceof Enemy)) return;
			Item gear = gearForDimension(level.dimension());
			if (gear == null) return;
			float chance = playerKill ? (entity instanceof Enemy ? 0.22f : 0.12f) : 0.04f;
			if (level.getRandom().nextFloat() < chance) {
				entity.spawnAtLocation(level, new ItemStack(gear));
			}
		});
	}

	public static Item gearForDimension(ResourceKey<Level> dimension) {
		if (dimension.equals(Level.OVERWORLD)) return ModItems.QUEST_TIME_GEAR_OVERWORLD;
		if (dimension.equals(Level.NETHER)) return ModItems.QUEST_TIME_GEAR_NETHER;
		if (dimension.equals(Level.END)) return ModItems.QUEST_TIME_GEAR_END;
		return null;
	}

	private static void addIfPresent(LootTable.Builder builder, Float chance, Item item) {
		if (chance == null) return;
		builder.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
				.when(LootItemRandomChanceCondition.randomChance(chance))
				.add(LootItem.lootTableItem(item)));
	}

	private TimeLoot() {}
}
