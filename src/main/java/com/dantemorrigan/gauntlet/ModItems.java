package com.dantemorrigan.gauntlet;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.function.Function;

public final class ModItems {
	public static final Item GAUNTLET = register("gauntlet", GauntletItem::new, epic().component(DataComponents.CONTAINER, ItemContainerContents.EMPTY));

	public static final Item POWER_STONE = register("power_stone", stone("power"));
	public static final Item SPACE_STONE = register("space_stone", p -> new LooseStoneItem(p, Stone.SPACE), stone("space"));
	public static final Item REALITY_STONE = register("reality_stone", p -> new LooseStoneItem(p, Stone.REALITY), stone("reality"));
	public static final Item MIND_STONE = register("mind_stone", p -> new LooseStoneItem(p, Stone.MIND), stone("mind"));
	public static final Item TIME_STONE = register("time_stone", TimeStoneItem::new, stone("time"));
	public static final Item SOUL_STONE = register("soul_stone", p -> new LooseStoneItem(p, Stone.SOUL), stone("soul"));

	public static final Item INFINITY_EXTRACTOR = register("infinity_extractor", com.dantemorrigan.gauntlet.extractor.InfinityExtractorItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
	/** Test dummy: a Steve doll wearing a full gauntlet. */
	public static final Item GAUNTLET_DOLL_SPAWN_EGG = register("gauntlet_doll_spawn_egg", net.minecraft.world.item.SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.GAUNTLET_DOLL));

	private static Item.Properties stone(String name) {
		return epic().component(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(
				java.util.List.of(net.minecraft.network.chat.Component.translatable("tooltip.infinity_gauntlet.loose_" + name))));
	}

	private static Item.Properties epic() {
		return new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant();
	}

	private static Item register(String name, Item.Properties properties) {
		return register(name, Item::new, properties);
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, InfinityGauntlet.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	public static void init() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (var player : server.getPlayerList().getPlayers()) {
				if (player.isAlive() && !player.isSpectator()
						&& (player.getMainHandItem().is(POWER_STONE) || player.getOffhandItem().is(POWER_STONE))) {
					player.kill(player.level());
				}
			}
		});
	}

	private ModItems() {}
}
