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

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, InfinityGauntlet.id(name), FabricParticleTypes.simple(true));
	}

	public static void init() {}

	private ModParticles() {}
}
