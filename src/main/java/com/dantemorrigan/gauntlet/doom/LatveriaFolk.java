package com.dantemorrigan.gauntlet.doom;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerType;

/**
 * The people of Latveria wear Latveria's dress: a long green coat with a red-and-black embroidered collar and hem, gold
 * buttons, a black belt with a gold buckle, black trousers and tall boots, and a fur kalpak with a green band. It is a
 * villager type of its own (textures/entity/villager/type/latveria.png), so profession clothes still go on top; every
 * villager in Latveria, born there or brought from an older world, is dressed in it.
 */
public final class LatveriaFolk {
	public static final ResourceKey<VillagerType> TYPE = ResourceKey.create(Registries.VILLAGER_TYPE, InfinityGauntlet.id("latveria"));

	private LatveriaFolk() {}

	/** Called while the registries are open. */
	public static void register() {
		Registry.register(BuiltInRegistries.VILLAGER_TYPE, TYPE, new VillagerType());
		// Children born in Latveria take its dress from the biome.
		VillagerType.BY_BIOME.put(ResourceKey.create(Registries.BIOME, InfinityGauntlet.id("latveria")), TYPE);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Villager v && level.dimension() == Latveria.LEVEL) dress(v);
		});
	}

	public static void dress(Villager v) {
		if (v.getVillagerData().type().is(TYPE)) return;
		v.setVillagerData(v.getVillagerData().withType(BuiltInRegistries.VILLAGER_TYPE.getOrThrow(TYPE)));
	}
}
