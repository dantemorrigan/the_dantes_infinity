package com.dantemorrigan.gauntlet.client.fx;

import com.dantemorrigan.gauntlet.ModParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.core.particles.SimpleParticleType;
import org.joml.Vector3f;

/** Client providers: frames come from the particle JSONs; timing/size/orientation from {@link VfxPresets}. */
public final class ModParticleProviders {
	public static void init() {
        billboard(ModParticles.COSMOS_BOOST_FLASH,"cosmos_boost_flash");
        flat(ModParticles.COSMOS_WARP_RING,"cosmos_warp_ring");
        cosmos(ModParticles.COSMOS_SPEED_STREAK,"cosmos_speed_streak");
        cosmos(ModParticles.COSMOS_STAR_DUST,"cosmos_star_dust");
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
		billboard(ModParticles.SPACE_TELEPORT_FLASH, "space_teleport_flash");
		// Reality stone
		ParticleProviderRegistry.getInstance().register(ModParticles.REALITY_CHARGE, sprites -> (t, level, x, y, z, xa, ya, za, random) ->
				new FxParticle(level, x, y, z, sprites, VfxPresets.get("reality_charge"), 1f, null, (int) xa,
						com.dantemorrigan.gauntlet.client.fx.RealityClient::fistWhileCharging, 1f));
		ParticleProviderRegistry.getInstance().register(ModParticles.REALITY_FRACTURE, sprites -> (t, level, x, y, z, xa, ya, za, random) -> {
			// (xa,ya,za): if non-zero, a surface normal (flat on the terrain); its length = size. Otherwise upright.
			Vector3f n = new Vector3f((float) xa, (float) ya, (float) za);
			float len = n.length();
			return new FxParticle(level, x, y, z, sprites, VfxPresets.get("reality_fracture"), len > 1e-4f ? len : 1f, len > 1e-4f ? n.div(len) : null, -1, null, 1f);
		});
		billboard(ModParticles.REALITY_REWRITE, "reality_rewrite");
		flat(ModParticles.REALITY_RESTORE, "reality_restore"); // ring pulse lies on the ground
		motion(ModParticles.REALITY_BUTTERFLY, "reality_butterfly", MotionParticle.Kind.BUTTERFLY);
		motion(ModParticles.REALITY_PETAL, "reality_petal", MotionParticle.Kind.PETAL);
		motion(ModParticles.REALITY_GLASS, "reality_glass", MotionParticle.Kind.GLASS);
		motion(ModParticles.REALITY_AETHER, "reality_aether", MotionParticle.Kind.AETHER);
		billboard(ModParticles.SPACE_ENERGY_WISPS, "space_energy_wisps");
		ParticleProviderRegistry.getInstance().register(ModParticles.CELESTIAL_METEOR_TRAIL, sprites -> (t, level, x, y, z, xa, ya, za, random) -> {
			Vector3f v = new Vector3f((float) xa, (float) ya, (float) za);
			float speed = v.length();
			if (speed < 1.0e-4f) v.set(0, -1, 0);
			return new FxParticle(level, x, y, z, sprites, VfxPresets.get("celestial_meteor_trail"), 1f, null, -1, null, 1f).alignTo(v.normalize());
		});
		// Mind stone
		ParticleProviderRegistry.getInstance().register(ModParticles.MIND_HOLD, sprites -> (t, level, x, y, z, xa, ya, za, random) ->
				new FxParticle(level, x, y, z, sprites, VfxPresets.get("mind_hold"), ya > 0 ? (float) ya : 1f, null, (int) xa, MindClient::holdCenter, 0.9f));
		flat(ModParticles.MIND_LIFT_FRACTURE, "mind_lift_fracture");
		aligned(ModParticles.MIND_THROW_TRAIL, "mind_throw_trail", 10);
		billboard(ModParticles.MIND_RUPTURE, "mind_rupture");
		// Soul stone
		ParticleProviderRegistry.getInstance().register(ModParticles.SOUL_WISP, sprites -> (t, level, x, y, z, xa, ya, za, random) -> {
			int id = (int) xa;
			return new FxParticle(level, x, y, z, sprites, VfxPresets.get("soul_wisp"), ya > 0 ? (float) ya : 1f, null, -1, null, 1f) {
				@Override
				protected void motion() {
					net.minecraft.world.phys.Vec3 p = SoulClient.soulPos(id);
					if (p == null) this.remove();
					else this.setPos(p.x, p.y, p.z);
				}
			}.lifetime(400);
		});
		aligned(ModParticles.SOUL_SIPHON, "soul_siphon", 6);
		// Ground wave: translucent, it must not hide the souls rising behind it.
		ParticleProviderRegistry.getInstance().register(ModParticles.SOUL_MASS_WAVE, sprites -> (t, level, x, y, z, xa, ya, za, random) -> {
			Vector3f n = new Vector3f((float) xa, (float) ya, (float) za);
			float len = n.length();
			if (len < 1.0e-4f) { n.set(0, 1, 0); len = 1; }
			return new FxParticle(level, x, y, z, sprites, VfxPresets.get("soul_mass_wave"), len, n.div(len), -1, null, 0.45f);
		});
		billboard(ModParticles.SOUL_ABSORPTION, "soul_absorption");
	}

	/** (xa,ya,za) = direction of travel: the sprite's +X runs along it. Short-lived segment of a looping strip. */
	private static void aligned(SimpleParticleType type, String name, int life) {
		ParticleProviderRegistry.getInstance().register(type, sprites -> (t, level, x, y, z, xa, ya, za, random) -> {
			Vector3f v = new Vector3f((float) xa, (float) ya, (float) za);
			if (v.lengthSquared() < 1.0e-8f) v.set(1, 0, 0);
			return new FxParticle(level, x, y, z, sprites, VfxPresets.get(name), 1f, null, -1, null, 1f).alignX(v.normalize()).lifetime(life);
		});
	}

    private static void cosmos(SimpleParticleType type,String name) {
        ParticleProviderRegistry.getInstance().register(type,sprites -> (t,level,x,y,z,xa,ya,za,random) ->
            new com.dantemorrigan.gauntlet.client.cosmos.CosmosParticle(level,x,y,z,xa,ya,za,sprites,name));
    }
	private static void motion(SimpleParticleType type, String name, MotionParticle.Kind kind) {
		ParticleProviderRegistry.getInstance().register(type, sprites -> (t, level, x, y, z, xa, ya, za, random) ->
				new MotionParticle(level, x, y, z, xa, ya, za, sprites, VfxPresets.get(name), 1f, kind));
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

