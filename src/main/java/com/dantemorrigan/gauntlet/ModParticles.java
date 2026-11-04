package com.dantemorrigan.gauntlet;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Particle types from the VFX packs. Behaviour/animation lives in the client providers.
 *
 * <p>Parameter convention (velocity args of addParticle):
 * <ul>
 *   <li>power_charge: xa = owner entity id (aura follows that entity's fist).</li>
 *   <li>flat effects (power_shockwave, ground_fracture): (xa,ya,za) = surface normal; its length = size multiplier.</li>
 *   <li>others: xa = size multiplier (0 = default), ya/za unused.</li>
 * </ul>
 */
public final class ModParticles {
	public static final SimpleParticleType POWER_CHARGE = register("power_charge");
	public static final SimpleParticleType POWER_IMPACT = register("power_impact");
	public static final SimpleParticleType POWER_SHOCKWAVE = register("power_shockwave");
	public static final SimpleParticleType POWER_SPARKS_SMOKE = register("power_sparks_smoke");
	public static final SimpleParticleType GROUND_FRACTURE = register("ground_fracture");
	public static final SimpleParticleType PLANET_RIFT = register("planet_rift");
	public static final SimpleParticleType DETONATION_FLASH = register("detonation_flash");
	public static final SimpleParticleType VOID_DISSOLVE = register("void_dissolve");
	/** (xa,ya,za) = meteor velocity: the sheet diagonal is turned along it (bright tip forward). */
	public static final SimpleParticleType CELESTIAL_METEOR_TRAIL = register("celestial_meteor_trail");
	/** xa = size multiplier. Upright flash at the ground impact. */
	public static final SimpleParticleType CELESTIAL_METEOR_IMPACT = register("celestial_meteor_impact");
	// Reality stone (pack: Reality_Stone_VFX_Basic). xa = size multiplier unless noted.
	public static final SimpleParticleType REALITY_CHARGE = register("reality_charge"); // xa = owner id (follows the fist)
	public static final SimpleParticleType REALITY_FRACTURE = register("reality_fracture");
	public static final SimpleParticleType REALITY_REWRITE = register("reality_rewrite");
	public static final SimpleParticleType REALITY_RESTORE = register("reality_restore");
	public static final SimpleParticleType REALITY_BUTTERFLY = register("reality_butterfly"); // flutters around its spawn point
	public static final SimpleParticleType REALITY_PETAL = register("reality_petal");         // (xa,ya,za) = initial velocity
	public static final SimpleParticleType REALITY_GLASS = register("reality_glass");         // (xa,ya,za) = initial velocity
	public static final SimpleParticleType REALITY_AETHER = register("reality_aether");       // slow drifting ribbons
	/** Space stone: short blue flash (xa = size). */
	public static final SimpleParticleType SPACE_TELEPORT_FLASH = register("space_teleport_flash");
	/** Space stone: blue energy wisps (xa = size). */
	public static final SimpleParticleType SPACE_ENERGY_WISPS = register("space_energy_wisps");

    public static final SimpleParticleType COSMOS_SPEED_STREAK = register("cosmos_speed_streak");
    public static final SimpleParticleType COSMOS_BOOST_FLASH = register("cosmos_boost_flash");
    public static final SimpleParticleType COSMOS_WARP_RING = register("cosmos_warp_ring");
    public static final SimpleParticleType COSMOS_STAR_DUST = register("cosmos_star_dust");

	// Mind stone (pack: Mind_Stone_Telekinesis_VFX). mind_hold: xa = owner id (follows the held target), ya = size.
	public static final SimpleParticleType MIND_HOLD = register("mind_hold");
	public static final SimpleParticleType MIND_LIFT_FRACTURE = register("mind_lift_fracture"); // (xa,ya,za) = normal, length = size
	public static final SimpleParticleType MIND_THROW_TRAIL = register("mind_throw_trail");     // (xa,ya,za) = velocity
	public static final SimpleParticleType MIND_RUPTURE = register("mind_rupture");             // xa = size
	// Soul stone (pack: Soul_Stone_Extraction_VFX). soul_wisp: xa = client soul id (follows that soul).
	public static final SimpleParticleType SOUL_WISP = register("soul_wisp");
	public static final SimpleParticleType SOUL_SIPHON = register("soul_siphon");         // (xa,ya,za) = flow direction
	public static final SimpleParticleType SOUL_MASS_WAVE = register("soul_mass_wave");   // (xa,ya,za) = normal, length = size
	public static final SimpleParticleType SOUL_ABSORPTION = register("soul_absorption"); // xa = size

	// Infinity Extractor (pack: Infinity_Extractor_Visual_Pack).
	/** (xa,ya,za) = direction the pulse flows along (toward the extractor). A short segment of the thread. */
	public static final SimpleParticleType EXTRACTOR_TETHER = register("extractor_tether");
	public static final SimpleParticleType EXTRACTOR_GATHER = register("extractor_gather");   // xa = size
	public static final SimpleParticleType EXTRACTOR_SUCCESS = register("extractor_success"); // xa = size
	public static final SimpleParticleType EXTRACTOR_SEAL = register("extractor_seal");       // xa = size
	/** xa = owner entity id: flies from its spawn point into the extractor's chamber. */
	public static final SimpleParticleType EXTRACTOR_MOTE = register("extractor_mote");

	/** Meteor Stream: the artillery ring lying on the ground. (xa,ya,za) = surface normal, its length = diameter in blocks. */
	public static final SimpleParticleType METEOR_RETICLE = register("meteor_reticle");

	/** Uru: rune ring opening from the chest when the armor absorbs a full-gauntlet hit. xa = wearer entity id (follows it). */
	public static final SimpleParticleType URU_GUARD = register("uru_guard");

	// Space Stone quest (pack: 01_Space_Quest). xa = size multiplier; obsidian_wave: (xa,ya,za) = normal, length = size.
	public static final SimpleParticleType QUEST_SPACE_PORTAL = register("quest_space_portal");
	public static final SimpleParticleType QUEST_SPACE_FROST_SHARD = register("quest_space_frost_shard");
	public static final SimpleParticleType QUEST_SPACE_RETURN_FOLD = register("quest_space_return_fold");
	public static final SimpleParticleType QUEST_SPACE_LAVA_RELEASE = register("quest_space_lava_release");
	public static final SimpleParticleType QUEST_SPACE_OBSIDIAN_WAVE = register("quest_space_obsidian_wave");

	// Reality Stone quest (pack: 02_Reality_Quest). xa = size multiplier.
	public static final SimpleParticleType QUEST_REALITY_FOG = register("quest_reality_fog");
	public static final SimpleParticleType QUEST_REALITY_STONE_TRAIL = register("quest_reality_stone_trail");
	public static final SimpleParticleType QUEST_REALITY_ARROW_HIT = register("quest_reality_arrow_hit");
	public static final SimpleParticleType QUEST_REALITY_FOG_DISPEL = register("quest_reality_fog_dispel");

	// Soul Stone quest (pack: 05_Soul_Quest). xa = size; water_ripple: (xa,ya,za) = normal, length = size.
	public static final SimpleParticleType QUEST_SOUL_RITUAL_BEAM = register("quest_soul_ritual_beam");
	public static final SimpleParticleType QUEST_SOUL_CLOUD_PULSE = register("quest_soul_cloud_pulse");
	public static final SimpleParticleType QUEST_SOUL_WATER_RIPPLE = register("quest_soul_water_ripple");
	public static final SimpleParticleType QUEST_SOUL_STONE_RISE = register("quest_soul_stone_rise");
	public static final SimpleParticleType QUEST_SOUL_RETURN_FLARE = register("quest_soul_return_flare");

	// Time Stone quest (pack: 03_Time_Quest). xa = size.
	public static final SimpleParticleType QUEST_TIME_GEAR_LOCK = register("quest_time_gear_lock");
	public static final SimpleParticleType QUEST_TIME_CLOCK_START = register("quest_time_clock_start");
	public static final SimpleParticleType QUEST_TIME_REWARD_SPIRAL = register("quest_time_reward_spiral");

	// Mind Stone quest (pack: 04_Mind_Quest).
	public static final SimpleParticleType QUEST_MIND_ZOMBIE_AURA = register("quest_mind_zombie_aura");
	public static final SimpleParticleType QUEST_MIND_ESSENCE = register("quest_mind_essence");
	public static final SimpleParticleType QUEST_MIND_CONVERGENCE = register("quest_mind_convergence");
	public static final SimpleParticleType QUEST_MIND_REWARD_BURST = register("quest_mind_reward_burst");

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, InfinityGauntlet.id(name), FabricParticleTypes.simple(true));
	}

	public static void init() {}

	private ModParticles() {}
}

