package com.dantemorrigan.gauntlet.client.fx;

import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.client.StatusHud;
import com.dantemorrigan.gauntlet.client.cutscene.Cutscene;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Soul stone, client side: souls leave their bodies (soul_wisp), are pulled along a siphon stream and fly in an arc
 * into the gauntlet owner (soul_absorption); the mass extraction adds an expanding ground wave. HUD shows the bonus.
 */
public final class SoulClient {
	public static final KeyMapping EXTRACT = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.infinity_gauntlet.soul", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, ClientPower.CATEGORY));

	private static final class Soul {
		int ownerId, targetId, delay, extract, flight, age;
		Vec3 body, lastOut, pos;
		boolean spawned;
	}

	private record Ring(Vec3 at, int tick, float size) {}

	private static final Map<Integer, Soul> SOULS = new HashMap<>();
	private static final List<Ring> RINGS = new ArrayList<>();
	private static int nextId = 1, ringClock;
	private static int buffSouls, buffTicks;

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(SoulClient::tick);
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.SoulFx.TYPE, (fx, ctx) -> handle(fx));
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.SoulBuff.TYPE, (fx, ctx) -> {
			buffSouls = fx.souls();
			buffTicks = fx.ticks();
			if (fx.souls() <= 0) StatusHud.persistent("soul", null);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(() -> {
			SOULS.clear();
			RINGS.clear();
			buffSouls = buffTicks = 0;
			StatusHud.persistent("soul", null);
		}));
	}

	/** Current position of a flying soul for its wisp particle; null when it is absorbed or gone. */
	public static Vec3 soulPos(int id) {
		Soul s = SOULS.get(id);
		return s == null || !s.spawned ? null : s.pos;
	}

	private static Vec3 chest(Entity e) {
		return e.position().add(0, e.getBbHeight() * 0.6, 0);
	}

	private static void handle(ModNetwork.SoulFx fx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		if (fx.kind() == 1) {
			// A ring every 3 ticks at the wave front (soul_mass_wave frames expand on their own).
			ringClock = 0;
			RINGS.clear();
			float r = fx.delay();
			for (int t = 0; t * 0.8f <= r; t += 5) RINGS.add(new Ring(fx.pos().add(0, 0.05, 0), t, Math.max(0.5f, t * 0.8f * 2f / 8f)));
			Cutscene.shake(0.3f, 20);
			RandomSource rnd = mc.level.getRandom();
			for (int i = 0; i < 60; i++) {
				double a = rnd.nextDouble() * Math.PI * 2;
				mc.level.addParticle(ParticleTypes.SOUL, fx.pos().x, fx.pos().y + 0.2, fx.pos().z, Math.cos(a) * 0.5, 0.05, Math.sin(a) * 0.5);
			}
			return;
		}
		Soul s = new Soul();
		s.ownerId = fx.ownerId();
		s.targetId = fx.targetId();
		s.delay = fx.delay();
		s.extract = fx.extract();
		s.flight = fx.flight();
		Entity t = mc.level.getEntity(fx.targetId());
		s.body = t != null ? chest(t) : fx.pos().add(0, 1, 0);
		s.pos = s.lastOut = s.body;
		SOULS.put(nextId++, s);
	}

	private static void tick(Minecraft mc) {
		while (EXTRACT.consumeClick()) {
			if (mc.player != null && mc.screen == null) ClientPlayNetworking.send(new ModNetwork.SoulAction(mc.player.isShiftKeyDown() ? 1 : 0));
		}
		ClientLevel level = mc.level;
		if (level == null || mc.isPaused()) return;
		RandomSource rnd = level.getRandom();
		// Mass wave rings
		if (!RINGS.isEmpty()) {
			int now = ringClock++;
			RINGS.removeIf(r -> {
				if (r.tick() > now) return false;
				ClientPower.spawn(ModParticles.SOUL_MASS_WAVE, r.at(), 0, r.size(), 0);
				return true;
			});
		}
		for (Iterator<Map.Entry<Integer, Soul>> it = SOULS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, Soul> e = it.next();
			Soul s = e.getValue();
			s.age++;
			Entity owner = level.getEntity(s.ownerId);
			if (owner == null || s.age > s.delay + s.extract + s.flight + 40) {
				it.remove();
				continue;
			}
			if (s.age < s.delay) continue;
			Entity target = level.getEntity(s.targetId);
			Vec3 dest = chest(owner);
			int t = s.age - s.delay;
			if (t < s.extract) {
				if (target != null && target.isAlive()) s.body = chest(target);
				float k = t / (float) s.extract;
				Vec3 toOwner = dest.subtract(s.body).normalize();
				s.pos = s.body.add(0, 0.2 + 0.7 * k, 0).add(toOwner.scale(0.6 * k));
				s.lastOut = s.pos;
				if (!s.spawned) {
					s.spawned = true;
					ClientPower.spawn(ModParticles.SOUL_WISP, s.pos, e.getKey(), 1.0, 0);
				}
				// Siphon stream from the soul to the owner, short segments along the line.
				if (t % 2 == 0) {
					Vec3 d = dest.subtract(s.pos);
					double len = d.length();
					Vec3 dir = d.scale(1 / Math.max(1e-4, len));
					int segs = Math.min(10, Math.max(2, (int) (len / 1.2)));
					for (int i = 0; i < segs; i++) {
						Vec3 at = s.pos.add(d.scale((i + rnd.nextFloat()) / segs));
						ClientPower.spawn(ModParticles.SOUL_SIPHON, at, dir.x, dir.y, dir.z);
					}
				}
				if (target != null && rnd.nextInt(2) == 0) {
					level.addParticle(ParticleTypes.SOUL, s.body.x + rnd.nextGaussian() * 0.25, s.body.y + rnd.nextGaussian() * 0.3, s.body.z + rnd.nextGaussian() * 0.25, 0, 0.04, 0);
				}
			} else if (t < s.extract + s.flight) {
				float k = (t - s.extract) / (float) s.flight;
				k = k * k * (3 - 2 * k);
				Vec3 mid = s.lastOut.lerp(dest, 0.5).add(0, 1.5 + s.lastOut.distanceTo(dest) * 0.15, 0);
				// Quadratic Bezier: out of the body, up in an arc, into the owner's chest.
				Vec3 a = s.lastOut.lerp(mid, k), b = mid.lerp(dest, k);
				s.pos = a.lerp(b, k);
				if (rnd.nextInt(3) == 0) level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, s.pos.x, s.pos.y, s.pos.z, 0, 0, 0);
			} else {
				ClientPower.spawn(ModParticles.SOUL_ABSORPTION, dest, 1.3, 0, 0);
				for (int i = 0; i < 12; i++) level.addParticle(ParticleTypes.SOUL, dest.x + rnd.nextGaussian() * 0.3, dest.y + rnd.nextGaussian() * 0.4, dest.z + rnd.nextGaussian() * 0.3, 0, 0.02, 0);
				it.remove();
			}
		}
		// Empowered: rare absorption pulses + HUD.
		var p = mc.player;
		if (p != null && buffSouls > 0) {
			if (buffTicks > 0) buffTicks--;
			if (p.tickCount % 30 == 0) ClientPower.spawn(ModParticles.SOUL_ABSORPTION, chest(p), 0.7, 0, 0);
			StatusHud.persistent("soul", Component.translatable("msg.infinity_gauntlet.soul.hud", String.valueOf(buffSouls),
					String.format(java.util.Locale.ROOT, "%.0f", buffSouls * com.dantemorrigan.gauntlet.ServerRules.soulHealthPerSoul / 2),
					String.format(java.util.Locale.ROOT, "%.1f", buffSouls * com.dantemorrigan.gauntlet.ServerRules.soulDamagePerSoul), String.valueOf(Mth.ceil(buffTicks / 20f))));
		}
	}

	private SoulClient() {}
}
