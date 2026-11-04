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

	private static final ResourceKey<EntityType<?>> WISP_KEY = ResourceKey.create(Registries.ENTITY_TYPE, InfinityGauntlet.id("reality_wisp"));
	/** The Reality Stone fleeing through the red mist in the Nether. */
	public static final EntityType<com.dantemorrigan.gauntlet.stones.RealityWisp> REALITY_WISP = Registry.register(BuiltInRegistries.ENTITY_TYPE, WISP_KEY,
			EntityType.Builder.<com.dantemorrigan.gauntlet.stones.RealityWisp>of(com.dantemorrigan.gauntlet.stones.RealityWisp::new, MobCategory.MISC)
					.sized(0.6f, 0.6f).clientTrackingRange(8).updateInterval(1).noLootTable().build(WISP_KEY));

	private static final ResourceKey<EntityType<?>> MIND_ZOMBIE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, InfinityGauntlet.id("mind_zombie"));
	/** Smart, sun-immune zombie infused with Mind Stone essence roaming the Ruined City. */
	public static final EntityType<com.dantemorrigan.gauntlet.stones.MindZombie> MIND_ZOMBIE = Registry.register(BuiltInRegistries.ENTITY_TYPE, MIND_ZOMBIE_KEY,
			EntityType.Builder.<com.dantemorrigan.gauntlet.stones.MindZombie>of(com.dantemorrigan.gauntlet.stones.MindZombie::new, MobCategory.MONSTER)
					.sized(0.6f, 1.95f).eyeHeight(1.74f).passengerAttachments(2.0125f).ridingOffset(-0.7f).clientTrackingRange(10).build(MIND_ZOMBIE_KEY));

	public static void init() {
		FabricDefaultAttributeRegistry.register(GAUNTLET_DOLL, GauntletDoll.createAttributes());
		FabricDefaultAttributeRegistry.register(MIND_ZOMBIE, com.dantemorrigan.gauntlet.stones.MindZombie.createAttributes());
	}

	private ModEntities() {}
}
