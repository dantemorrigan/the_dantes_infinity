package com.dantemorrigan.gauntlet.client.fx;

import com.dantemorrigan.gauntlet.GauntletItem;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.client.cutscene.Cutscene;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Client side of the Power stone: mode key, charge aura, strike visuals. */
public final class ClientPower {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(InfinityGauntlet.id("gauntlet"));
	public static final KeyMapping TOGGLE_MODE = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.infinity_gauntlet.power_mode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));

	/** One looping aura per charging entity. */
	private static final Map<Integer, Particle> AURAS = new HashMap<>();

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(ClientPower::tick);
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.PowerFx.TYPE, (fx, ctx) -> strikeFx(fx.pos(), fx.normal(), fx.strength()));
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.EffectDemo.TYPE, (demo, ctx) -> demo(demo.effect(), demo.pos()));
	}

	private static void tick(Minecraft mc) {
		while (TOGGLE_MODE.consumeClick()) {
			if (mc.player != null) ClientPlayNetworking.send(new ModNetwork.ToggleMode());
		}
		ClientLevel level = mc.level;
		if (level == null) {
			AURAS.clear();
			return;
		}
		for (Iterator<Map.Entry<Integer, Particle>> it = AURAS.entrySet().iterator(); it.hasNext(); ) {
			if (!it.next().getValue().isAlive()) it.remove();
		}
		for (Player p : level.players()) {
			if ((GauntletItem.chargeTicks(p) > 0 || p==mc.player && com.dantemorrigan.gauntlet.client.ability.AbilityInput.powerCharge()>0) && !AURAS.containsKey(p.getId())) {
				Vec3 fist = FistPosition.of(p, 1f);
				Particle aura = spawn(ModParticles.POWER_CHARGE, fist, p.getId(), 0, 0);
				if (aura != null) AURAS.put(p.getId(), aura);
			}
		}
	}

	public static Particle spawn(ParticleOptions type, Vec3 pos, double xa, double ya, double za) {
		return Minecraft.getInstance().particleEngine.createParticle(type, pos.x, pos.y, pos.z, xa, ya, za);
	}

	/** Impact flash + ground-oriented shockwave + sparks/smoke (one shot each). */
	public static void strikeFx(Vec3 pos, Vec3 normal, float strength) {
		Vec3 n = normal.lengthSqr() < 1.0e-4 ? new Vec3(0, 1, 0) : normal.normalize();
		Vec3 lifted = pos.add(n.scale(0.06)); // above the surface, avoids z-fighting
		float s = Math.max(0.5f, strength);
		spawn(ModParticles.POWER_IMPACT, lifted.add(n.scale(0.3)), s, 0, 0);
		Vec3 ring = n.scale(s);
		spawn(ModParticles.POWER_SHOCKWAVE, lifted, ring.x, ring.y, ring.z);
		spawn(ModParticles.POWER_SPARKS_SMOKE, lifted.add(n.scale(0.4)), s, 0, 0);
		Cutscene.shake(0.12f * s, 8);
	}

	/** Debug: one effect at a time. */
	public static void demo(String effect, Vec3 pos) {
		Minecraft mc = Minecraft.getInstance();
		switch (effect) {
			case "power_charge" -> {
				if (mc.player != null) com.dantemorrigan.gauntlet.client.StatusHud.show(net.minecraft.network.chat.Component.translatable("msg.infinity_gauntlet.power.aura_hint"));
			}
			case "power_impact" -> spawn(ModParticles.POWER_IMPACT, pos.add(0, 0.5, 0), 1, 0, 0);
			case "power_shockwave" -> spawn(ModParticles.POWER_SHOCKWAVE, pos.add(0, 0.06, 0), 0, 1, 0);
			case "power_sparks_smoke" -> spawn(ModParticles.POWER_SPARKS_SMOKE, pos.add(0, 0.5, 0), 1, 0, 0);
			case "power_strike" -> strikeFx(pos, new Vec3(0, 1, 0), 1.2f);
			case "ground_fracture" -> spawn(ModParticles.GROUND_FRACTURE, pos.add(0, 0.05, 0), 0, 0.5, 0);
			case "planet_rift" -> spawn(ModParticles.PLANET_RIFT, pos.add(0, 2, 0), 1, 0, 0);
			case "detonation_flash" -> spawn(ModParticles.DETONATION_FLASH, pos.add(0, 2, 0), 1, 0, 0);
			case "void_dissolve" -> spawn(ModParticles.VOID_DISSOLVE, pos.add(0, 1, 0), 1, 0, 0);
			case "camera_shake" -> Cutscene.shake(0.6f, 40);
			case "white_flash" -> Cutscene.flashDemo();
			case "lift_debris" -> Cutscene.liftDemo(pos);
			default -> {}
		}
	}

	private ClientPower() {}
}
