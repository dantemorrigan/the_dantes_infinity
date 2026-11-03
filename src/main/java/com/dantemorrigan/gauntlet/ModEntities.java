package com.dantemorrigan.gauntlet;

import com.dantemorrigan.gauntlet.entity.GauntletDoll;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	private static final ResourceKey<EntityType<?>> DOLL_KEY = ResourceKey.create(Registries.ENTITY_TYPE, InfinityGauntlet.id("gauntlet_doll"));

	/** A Steve doll wearing a full gauntlet: a moving, lightly attacking target for testing the Infinity Extractor. */
	public static final EntityType<GauntletDoll> GAUNTLET_DOLL = Registry.register(BuiltInRegistries.ENTITY_TYPE, DOLL_KEY,
			EntityType.Builder.of(GauntletDoll::new, MobCategory.MISC).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).noLootTable().build(DOLL_KEY));

	public static void init() {
		FabricDefaultAttributeRegistry.register(GAUNTLET_DOLL, GauntletDoll.createAttributes());
	}

	private ModEntities() {}
}
