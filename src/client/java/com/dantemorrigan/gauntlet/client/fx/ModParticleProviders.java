package com.dantemorrigan.gauntlet.client.fx;

import com.dantemorrigan.gauntlet.ModParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.core.particles.SimpleParticleType;
import org.joml.Vector3f;

/** Client providers: frames come from the particle JSONs; timing/size/orientation from {@link VfxPresets}. */
public final class ModParticleProviders {
	public static void init() {
		// Charge aura: follows the owner's fist while they charge; xa = owner entity id.
		ParticleProviderRegistry.getInstance().register(ModParticles.POWER_CHARGE, sprites -> (type, level, x, y, z, xa, ya, za, random) ->
				new FxParticle(level, x, y, z, sprites, VfxPresets.get("power_charge"), 1f, null, (int) xa,
						FistPosition::ifCharging, 0.95f));
		billboard(ModParticles.POWER_IMPACT, "power_impact");
		flat(ModParticles.POWER_SHOCKWAVE, "power_shockwave");
		billboard(ModParticles.POWER_SPARKS_SMOKE, "power_sparks_smoke");
		flat(ModParticles.GROUND_FRACTURE, "ground_fracture");
		billboard(ModParticles.PLANET_RIFT, "planet_rift");
		billboard(ModParticles.DETONATION_FLASH, "detonation_flash");
		billboard(ModParticles.VOID_DISSOLVE, "void_dissolve");
		billboard(ModParticles.CELESTIAL_METEOR_IMPACT, "celestial_meteor_impact");
		ParticleProviderRegistry.getInstance().register(ModParticles.CELESTIAL_METEOR_TRAIL, sprites -> (t, level, x, y, z, xa, ya, za, random) -> {
			Vector3f v = new Vector3f((float) xa, (float) ya, (float) za);
			float speed = v.length();
			if (speed < 1.0e-4f) v.set(0, -1, 0);
			return new FxParticle(level, x, y, z, sprites, VfxPresets.get("celestial_meteor_trail"), 1f, null, -1, null, 1f).alignTo(v.normalize());
		});
	}

	/** xa = size multiplier (0 → 1). */
	private static void billboard(SimpleParticleType type, String name) {
		ParticleProviderRegistry.getInstance().register(type, sprites -> (t, level, x, y, z, xa, ya, za, random) ->
				new FxParticle(level, x, y, z, sprites, VfxPresets.get(name), xa > 0 ? (float) xa : 1f, null, -1, null, 1f));
	}

	/** (xa,ya,za) = surface normal; its length = size multiplier. */
	private static void flat(SimpleParticleType type, String name) {
		ParticleProviderRegistry.getInstance().register(type, sprites -> (t, level, x, y, z, xa, ya, za, random) -> {
			Vector3f n = new Vector3f((float) xa, (float) ya, (float) za);
			float len = n.length();
			if (len < 1.0e-4f) {
				n.set(0, 1, 0);
				len = 1;
			}
			n.div(len);
			return new FxParticle(level, x, y, z, sprites, VfxPresets.get(name), len, n, -1, null, 1f);
		});
	}

	private ModParticleProviders() {}
}
