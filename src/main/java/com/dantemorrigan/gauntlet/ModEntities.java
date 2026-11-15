package com.dantemorrigan.gauntlet;

import com.dantemorrigan.gauntlet.entity.GauntletDoll;
import com.dantemorrigan.gauntlet.boss.Loki;
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
					.sized(1.4f, 1.4f).clientTrackingRange(8).updateInterval(1).noLootTable().build(WISP_KEY));

	private static final ResourceKey<EntityType<?>> MIND_ZOMBIE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, InfinityGauntlet.id("mind_zombie"));
	/** Smart, sun-immune zombie infused with Mind Stone essence roaming the Ruined City. */
	public static final EntityType<com.dantemorrigan.gauntlet.stones.MindZombie> MIND_ZOMBIE = Registry.register(BuiltInRegistries.ENTITY_TYPE, MIND_ZOMBIE_KEY,
			EntityType.Builder.<com.dantemorrigan.gauntlet.stones.MindZombie>of(com.dantemorrigan.gauntlet.stones.MindZombie::new, MobCategory.MONSTER)
					.sized(0.6f, 1.95f).eyeHeight(1.74f).passengerAttachments(2.0125f).ridingOffset(-0.7f).clientTrackingRange(10).build(MIND_ZOMBIE_KEY));

	private static final ResourceKey<EntityType<?>> LOKI_KEY = ResourceKey.create(Registries.ENTITY_TYPE, InfinityGauntlet.id("loki"));
	public static final EntityType<Loki> LOKI = Registry.register(BuiltInRegistries.ENTITY_TYPE, LOKI_KEY,
			EntityType.Builder.of(Loki::new, MobCategory.MONSTER).sized(0.72f, 2.25f).eyeHeight(1.88f)
					.clientTrackingRange(10).updateInterval(2).noLootTable().build(LOKI_KEY));

	private static final ResourceKey<EntityType<?>> TRIBUNAL_KEY = ResourceKey.create(Registries.ENTITY_TYPE, InfinityGauntlet.id("living_tribunal"));
	public static final EntityType<com.dantemorrigan.gauntlet.boss.LivingTribunal> TRIBUNAL = Registry.register(
			BuiltInRegistries.ENTITY_TYPE, TRIBUNAL_KEY,
			EntityType.Builder.of(com.dantemorrigan.gauntlet.boss.LivingTribunal::new, MobCategory.MONSTER)
					.sized(50, 106).eyeHeight(95).fireImmune().clientTrackingRange(32).updateInterval(1)
					.noSave().noSummon().noLootTable().build(TRIBUNAL_KEY));

	private static final ResourceKey<EntityType<?>> THANOS_KEY = ResourceKey.create(Registries.ENTITY_TYPE, InfinityGauntlet.id("thanos"));
	/** The final boss. Never saved: the encounter lives in memory and simply starts again after a restart. */
	public static final EntityType<com.dantemorrigan.gauntlet.boss.Thanos> THANOS = Registry.register(BuiltInRegistries.ENTITY_TYPE, THANOS_KEY,
			EntityType.Builder.<com.dantemorrigan.gauntlet.boss.Thanos>of(com.dantemorrigan.gauntlet.boss.Thanos::new, MobCategory.MONSTER)
					.sized(1.3f, 2.75f).eyeHeight(2.45f).fireImmune().clientTrackingRange(16).updateInterval(1).noSave().noLootTable().build(THANOS_KEY));

	private static final ResourceKey<EntityType<?>> THANOS_SWORD_KEY = ResourceKey.create(Registries.ENTITY_TYPE, InfinityGauntlet.id("thanos_sword"));
	public static final EntityType<com.dantemorrigan.gauntlet.boss.ThanosSword> THANOS_SWORD = Registry.register(BuiltInRegistries.ENTITY_TYPE, THANOS_SWORD_KEY,
			EntityType.Builder.<com.dantemorrigan.gauntlet.boss.ThanosSword>of(com.dantemorrigan.gauntlet.boss.ThanosSword::new, MobCategory.MISC)
					.sized(1.4f, 0.5f).clientTrackingRange(10).updateInterval(1).noSave().noSummon().noLootTable().build(THANOS_SWORD_KEY));

	private static final ResourceKey<EntityType<?>> STORMBREAKER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, InfinityGauntlet.id("stormbreaker"));
	public static final EntityType<com.dantemorrigan.gauntlet.stormbreaker.StormbreakerEntity> STORMBREAKER =
			Registry.register(BuiltInRegistries.ENTITY_TYPE, STORMBREAKER_KEY,
					EntityType.Builder.<com.dantemorrigan.gauntlet.stormbreaker.StormbreakerEntity>of(
							com.dantemorrigan.gauntlet.stormbreaker.StormbreakerEntity::new, MobCategory.MISC)
							.sized(.95f, 1.8f).clientTrackingRange(12).updateInterval(1).noSave().noSummon().noLootTable().build(STORMBREAKER_KEY));

	private static final ResourceKey<EntityType<?>> DOCTOR_DOOM_KEY = ResourceKey.create(Registries.ENTITY_TYPE, InfinityGauntlet.id("doctor_doom"));
	/** The lord of Latveria. Never saved: an abandoned fight starts over. */
	public static final EntityType<com.dantemorrigan.gauntlet.doom.DoctorDoom> DOCTOR_DOOM = Registry.register(BuiltInRegistries.ENTITY_TYPE, DOCTOR_DOOM_KEY,
			EntityType.Builder.<com.dantemorrigan.gauntlet.doom.DoctorDoom>of(com.dantemorrigan.gauntlet.doom.DoctorDoom::new, MobCategory.MONSTER)
					.sized(0.9f, 2.4f).eyeHeight(2.15f).fireImmune().clientTrackingRange(16).updateInterval(1).noSave().noLootTable().build(DOCTOR_DOOM_KEY));

	private static final ResourceKey<EntityType<?>> DOOMBOT_KEY = ResourceKey.create(Registries.ENTITY_TYPE, InfinityGauntlet.id("doombot"));
	/** A Doombot in one of six roles; saved with the world, hostile until Castle Doom is taken. */
	public static final EntityType<com.dantemorrigan.gauntlet.doom.Doombot> DOOMBOT = Registry.register(BuiltInRegistries.ENTITY_TYPE, DOOMBOT_KEY,
			EntityType.Builder.<com.dantemorrigan.gauntlet.doom.Doombot>of(com.dantemorrigan.gauntlet.doom.Doombot::new, MobCategory.MISC)
					.sized(0.75f, 1.95f).eyeHeight(1.7f).fireImmune().clientTrackingRange(12).updateInterval(1).noLootTable().build(DOOMBOT_KEY));

	private static final ResourceKey<EntityType<?>> LATVERIA_NPC_KEY = ResourceKey.create(Registries.ENTITY_TYPE, InfinityGauntlet.id("latveria_npc"));
	/** Player-skinned Latverian resident or quest giver (Old Mechanic Radovan, Tavern Keeper Mirko, and townsfolk). */
	public static final EntityType<com.dantemorrigan.gauntlet.doom.LatveriaNpc> LATVERIA_NPC = Registry.register(BuiltInRegistries.ENTITY_TYPE, LATVERIA_NPC_KEY,
			EntityType.Builder.<com.dantemorrigan.gauntlet.doom.LatveriaNpc>of(com.dantemorrigan.gauntlet.doom.LatveriaNpc::new, MobCategory.MISC)
					.sized(0.6f, 1.8f).eyeHeight(1.62f).fireImmune().clientTrackingRange(10).updateInterval(2).noLootTable().build(LATVERIA_NPC_KEY));

	public static void init() {
		FabricDefaultAttributeRegistry.register(TRIBUNAL, com.dantemorrigan.gauntlet.boss.LivingTribunal.attributes());
		FabricDefaultAttributeRegistry.register(LOKI, Loki.createAttributes());
		FabricDefaultAttributeRegistry.register(DOCTOR_DOOM, com.dantemorrigan.gauntlet.doom.DoctorDoom.createAttributes());
		FabricDefaultAttributeRegistry.register(DOOMBOT, com.dantemorrigan.gauntlet.doom.Doombot.createAttributes());
		FabricDefaultAttributeRegistry.register(LATVERIA_NPC, com.dantemorrigan.gauntlet.doom.LatveriaNpc.createAttributes());
		FabricDefaultAttributeRegistry.register(THANOS, com.dantemorrigan.gauntlet.boss.Thanos.createAttributes());
		FabricDefaultAttributeRegistry.register(GAUNTLET_DOLL, GauntletDoll.createAttributes());
		FabricDefaultAttributeRegistry.register(MIND_ZOMBIE, com.dantemorrigan.gauntlet.stones.MindZombie.createAttributes());
	}

	private ModEntities() {}
}
