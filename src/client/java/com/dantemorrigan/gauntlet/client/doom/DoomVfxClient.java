package com.dantemorrigan.gauntlet.client.doom;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.doom.DoomVfx;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Doom's world effects as real geometry drawn every frame: bolts fly and steer on the client (interpolated, with a
 * fading ribbon), bursts expand into rings, rift tears open and close, the Iron Bastion is a hex shell that follows
 * its bearer, jets burn under the feet they belong to, suns orbit, the Edict is a turning seal on the ground and a
 * column of light. Everything is soft additive-looking light from a handful of white textures tinted per style.
 */
public final class DoomVfxClient {
	private static final Identifier GLOW = tex("glow"), CORE = tex("core"), RING = tex("ring"), STREAK = tex("streak"),
			FLAME = tex("flame"), COLUMN = tex("column"), HEX = tex("hex"), SEARCH = tex("searchlight");
			/** The castle searchlights: burning or out, and the alarm (red beams held on one entity). */
			private static boolean lightsOn;
			private static int alarmTicks, alarmEntity = -1;

			public static void lights(boolean on) { lightsOn = on; if (!on) alarmTicks = 0; }

			public static void alarm(int entity, int ticks) { alarmEntity = entity; alarmTicks = ticks; }

			public static boolean lightsOn() { return lightsOn; }
	/** Inner and outer colour of each style (RGB). */
	private static final int[][] STYLE = {
			{0xFFF4C8, 0xFFA437}, {0xD6FFE0, 0x37E87A}, {0xFFFBE0, 0xFFB436}, {0xC8FFD8, 0x1FD67A}, {0xC8FFF6, 0x2DD6C8}};

	private static final class Fx {
		int kind, id, entity, target, style, age, life;
		Vec3 a = Vec3.ZERO, b = Vec3.ZERO, pos = Vec3.ZERO, prev = Vec3.ZERO, vel = Vec3.ZERO;
		float size, turn;
		int hitAge = 99;
		final ArrayDeque<Vec3> trail = new ArrayDeque<>();
	}

	private static final List<Fx> FX = new ArrayList<>();
	private static final Map<Integer, Fx> BOLTS = new HashMap<>(), SHIELDS = new HashMap<>(), JETS = new HashMap<>(), ORBITS = new HashMap<>();
	private static final RandomSource RANDOM = RandomSource.create();
	private static long clock;
	/** The Edict's aiming circle for the local wearer: centre and radius, or null. */
	private static Vec3 preview;
	private static float previewRadius;
	private static int previewMode;

	private static Identifier tex(String name) {
		return InfinityGauntlet.id("textures/fx/doom/" + name + ".png");
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.DoomVfx.TYPE, (p, ctx) -> receive(p));
		ClientTickEvents.END_CLIENT_TICK.register(DoomVfxClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(DoomVfxClient::clear));
		LevelRenderEvents.COLLECT_SUBMITS.register(DoomVfxClient::render);
	}

	private static void clear() {
		lightsOn = false;
		alarmTicks = 0;
		FX.clear();
		BOLTS.clear();
		SHIELDS.clear();
		JETS.clear();
		ORBITS.clear();
		preview = null;
	}

	/** The aiming mark of the selected power: mode 1 the Edict's circle, mode 2 the Rift Step's landing. */
	public static void preview(Vec3 at, float radius, int mode) {
		preview = at;
		previewRadius = radius;
		previewMode = mode;
	}

	/** Test hook: how many effects of a kind are alive. */
	public static int count(int kind) {
		int n = 0;
		for (Fx f : FX) if (f.kind == kind) n++;
		for (Map<Integer, Fx> m : List.of(BOLTS, SHIELDS, JETS, ORBITS)) for (Fx f : m.values()) if (f.kind == kind) n++;
		return n;
	}

	public static boolean jetting(int entityId) {
		return JETS.containsKey(entityId);
	}

	private static void receive(ModNetwork.DoomVfx p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		Fx f = new Fx();
		f.kind = p.kind(); f.id = p.id(); f.entity = p.entity(); f.target = p.target(); f.style = Mth.clamp(p.style(), 0, STYLE.length - 1);
		f.a = p.a(); f.b = p.b(); f.size = p.size(); f.turn = p.turn(); f.life = Math.max(1, p.ticks());
		switch (f.kind) {
			case DoomVfx.BOLT -> { f.pos = f.prev = f.a; f.vel = f.b; f.trail.add(f.a); BOLTS.put(f.id, f); }
			case DoomVfx.BOLT_END -> {
				Fx bolt = BOLTS.remove(f.id);
				if (f.size > 0) {
					Fx burst = new Fx();
					burst.kind = DoomVfx.IMPACT; burst.a = f.a; burst.size = f.size; burst.style = bolt != null ? bolt.style : f.style; burst.life = 10;
					FX.add(burst);
					sparks(mc.level, f.a, burst.style, 14, 0.35 * f.size);
				}
			}
			case DoomVfx.SHIELD -> { if (f.life > 1) SHIELDS.put(f.entity, f); else SHIELDS.remove(f.entity); }
			case DoomVfx.SHIELD_HIT -> { Fx s = SHIELDS.get(f.entity); if (s != null) s.hitAge = 0; }
			case DoomVfx.JET -> { if (f.size > 0 && f.life > 1) JETS.put(f.entity, f); else JETS.remove(f.entity); }
			case DoomVfx.ORBIT -> {
				Fx old = ORBITS.get(f.entity);
				if (f.size <= 0) { ORBITS.remove(f.entity); return; }
				if (old != null) { old.size = f.size; old.life = old.age + f.life; return; }
				ORBITS.put(f.entity, f);
			}
			case DoomVfx.IMPACT -> { FX.add(f); sparks(mc.level, f.a, f.style, (int) (8 + f.size * 6), 0.3 * f.size); }
			case DoomVfx.SLAM -> {
				FX.add(f);
				for (int i = 0; i < 24; i++) {
					double ang = i * Math.TAU / 24;
					mc.level.addParticle(ParticleTypes.CLOUD, f.a.x + Math.cos(ang) * 0.6, f.a.y + 0.1, f.a.z + Math.sin(ang) * 0.6,
							Math.cos(ang) * 0.06 * f.size, 0.01, Math.sin(ang) * 0.06 * f.size);
				}
			}
			case DoomVfx.RIFT -> {
				FX.add(f);
				for (int i = 0; i < 18; i++) mc.level.addParticle(ParticleTypes.PORTAL, f.a.x, f.a.y, f.a.z,
						RANDOM.nextGaussian() * 0.8, RANDOM.nextGaussian() * 0.8, RANDOM.nextGaussian() * 0.8);
			}
			case DoomVfx.TRAIL -> {
				FX.add(f);
				Vec3 d = f.b.subtract(f.a);
				for (int i = 0; i < 16; i++) {
					Vec3 at = f.a.add(d.scale(i / 16.0));
					mc.level.addParticle(dust(f.style, 1f), at.x, at.y, at.z, 0, 0.02, 0);
				}
			}
			default -> FX.add(f);
		}
	}

	private static DustParticleOptions dust(int style, float scale) {
		return new DustParticleOptions(STYLE[style][1], scale);
	}

	private static void sparks(ClientLevel level, Vec3 at, int style, int n, double speed) {
		for (int i = 0; i < n; i++) {
			Vec3 v = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian() + 0.3, RANDOM.nextGaussian()).normalize().scale(speed * (0.5 + RANDOM.nextDouble()));
			level.addParticle(i % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : dust(style, 0.9f), at.x, at.y, at.z, v.x, v.y, v.z);
		}
	}

	// ------------------------------------------------------------------ simulation

	private static void tick(Minecraft mc) {
		if (mc.level == null) { if (!FX.isEmpty() || !BOLTS.isEmpty()) clear(); return; }
		if (mc.isPaused()) return;
		clock++;
		if (alarmTicks > 0) alarmTicks--;
		ClientLevel level = mc.level;
		for (Iterator<Fx> it = BOLTS.values().iterator(); it.hasNext();) {
			Fx b = it.next();
			if (++b.age > b.life + 2) { it.remove(); continue; }
			if (b.target >= 0 && b.turn > 0 && level.getEntity(b.target) instanceof LivingEntity t && t.isAlive()) {
				Vec3 want = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(b.pos).normalize().scale(b.vel.length());
				b.vel = b.vel.lerp(want, b.turn);
			}
			b.prev = b.pos;
			b.pos = b.pos.add(b.vel);
			b.trail.addFirst(b.pos);
			while (b.trail.size() > 7) b.trail.removeLast();
			if (b.age % 2 == 0) level.addParticle(dust(b.style, 0.7f), b.pos.x, b.pos.y, b.pos.z, 0, 0, 0);
		}
		FX.removeIf(f -> ++f.age > f.life);
		for (Map<Integer, Fx> m : List.of(SHIELDS, JETS, ORBITS)) {
			m.values().removeIf(f -> ++f.age > f.life || level.getEntity(f.entity) == null);
		}
		for (Fx s : SHIELDS.values()) s.hitAge++;
		for (Fx j : JETS.values()) {
			if (!(level.getEntity(j.entity) instanceof LivingEntity e)) continue;
			for (Vec3 n : nozzles(e, 1)) {
				level.addParticle(ParticleTypes.FLAME, n.x, n.y - 0.1, n.z, RANDOM.nextGaussian() * 0.02, -0.12, RANDOM.nextGaussian() * 0.02);
				if (j.age % 2 == 0) level.addParticle(ParticleTypes.SMOKE, n.x, n.y - 0.3, n.z, 0, -0.05, 0);
			}
		}
		for (Fx f : FX) {
			if (f.kind == DoomVfx.EDICT && f.age % 3 == 0) {
				double ang = RANDOM.nextDouble() * Math.TAU, r = f.size * Math.sqrt(RANDOM.nextDouble());
				level.addParticle(dust(f.style, 1.1f), f.a.x + Math.cos(ang) * r, f.a.y + 0.1, f.a.z + Math.sin(ang) * r, 0, 0.08, 0);
			}
			if (f.kind == DoomVfx.COLUMN && f.age < 4) sparks(level, f.a.add(0, 1, 0), f.style, 10, 0.5);
		}
	}

	/** Where the jets of an entity sit: under each boot. */
	private static Vec3[] nozzles(LivingEntity e, float partial) {
		Vec3 base = e.getPosition(partial);
		float yaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 side = new Vec3(Math.cos(yaw), 0, Math.sin(yaw)).scale(0.14);
		return new Vec3[]{base.add(side).add(0, 0.08, 0), base.subtract(side).add(0, 0.08, 0)};
	}

	// ------------------------------------------------------------------ drawing

	private static void render(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		boolean gate = LatveriaSky.active() && cam().distanceToSqr(GATE) < 200 * 200;
		boolean lamps = LatveriaSky.active() && lightsOn;
		Entity god = GodDoomScene.radiantDoom();
		boolean anything = gate || lamps || god != null || !FX.isEmpty() || !BOLTS.isEmpty() || !SHIELDS.isEmpty() || !JETS.isEmpty() || !ORBITS.isEmpty() || preview != null;
		if (!anything) return;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		var camera = mc.gameRenderer.getMainCamera();
		Vec3 cam = camera.position();
		Vector3f left = new Vector3f(camera.leftVector()), up = new Vector3f(camera.upVector());
		Vec3 right = new Vec3(-left.x, -left.y, -left.z), upV = new Vec3(up.x, up.y, up.z);
		PoseStack pose = ctx.poseStack();
		Draw d = new Draw(ctx, pose, cam, right, upV);

		if (gate) {
			// The gate home stands in its arch and never turns: a fixed upright plane (north-south), seen from both sides.
			float time = clock + partial;
			Identifier frame = InfinityGauntlet.id(String.format("textures/particle/doctor_doom/latveria_gate_%02d.png", (int) (time / 2.2f) % 16));
			float pulse = 0.85f + 0.15f * Mth.sin(time * 0.09f);
			d.plane(CORE, GATE, 2.5f, 3.0f, 0x0A3A24, 0.62f);
			d.plane(GLOW, GATE, 4.6f, 5.0f, STYLE[DoomVfx.GREEN][1], 0.32f * pulse);
			d.plane(frame, GATE, 3.6f, 3.6f, 0xFFFFFF, 0.97f);
		}
		if (lamps) searchlights(d, mc, partial);
		if (god != null) {
			// The radiance of God Emperor Doom: a glow, a halo behind his head, a pillar of pale light.
			float rad = GodDoomScene.radiance(partial);
			Vec3 body = god.getPosition(partial).add(0, 1.4, 0);
			if (rad > 0) {
				d.billboard(GLOW, body, 8f * rad, 0xFFE6B0, 0.5f * rad);
				d.billboard(CORE, body.add(0, 0.9, 0), 2.6f * rad, 0xFFFFFF, 0.55f * rad);
				d.vertical(RING, body.add(0, 1.2, 0), 1.7f, 0xFFD27A, 0.95f * rad);
				d.vertical(RING, body.add(0, 1.2, 0), 2.3f, 0xFFF2D0, 0.4f * rad);
				d.cylinder(COLUMN, god.getPosition(partial).add(0, -3, 0), 1.6f * rad, 40, 0xFFF4D6, 0.32f * rad);
			}
		}
		for (Fx b : BOLTS.values()) {
			Vec3 head = b.prev.lerp(b.pos, partial);
			float s = b.size;
			int[] c = STYLE[b.style];
			d.billboard(GLOW, head, s * 3.2f, c[1], 0.75f);
			d.billboard(CORE, head, s * 1.3f, c[0], 1f);
			List<Vec3> points = new ArrayList<>();
			points.add(head);
			boolean first = true;
			for (Vec3 p : b.trail) { if (first) { first = false; continue; } points.add(p); }
			d.ribbon(STREAK, points, s * 1.4f, c[1], 0.8f);
			d.ribbon(STREAK, points, s * 0.5f, c[0], 0.9f);
		}
		for (Fx f : FX) {
			float t = Mth.clamp((f.age + partial) / f.life, 0, 1);
			int[] c = STYLE[f.style];
			switch (f.kind) {
				case DoomVfx.IMPACT -> {
					float k = 1 - t;
					d.billboard(GLOW, f.a, f.size * (1.2f + 2.4f * t), c[1], 0.9f * k * k);
					d.billboard(CORE, f.a, f.size * (0.9f + 0.6f * t), c[0], k * k * k);
					d.flat(RING, f.a, f.size * (0.6f + 2.6f * t), 0, c[1], 0.8f * k);
					d.vertical(RING, f.a, f.size * (0.5f + 2.2f * t), c[0], 0.5f * k);
				}
				case DoomVfx.SLAM -> {
					float k = 1 - t;
					d.flat(RING, f.a, f.size * (0.25f + 0.85f * (float) Math.sqrt(t)), 0, c[1], k);
					d.flat(GLOW, f.a, f.size * 0.6f * (1 - t * 0.5f), 0, c[0], 0.5f * k * k);
				}
				case DoomVfx.RIFT -> {
					float open = Mth.sin(Mth.PI * Math.min(1, t * 1.15f));
					d.tear(f.a, f.size * 0.55f * open, f.size * 2.1f * (0.4f + 0.6f * open), c, open);
				}
				case DoomVfx.TRAIL -> d.ribbon(STREAK, List.of(f.b, f.a), f.size * (1 - t), c[1], 0.8f * (1 - t));
				case DoomVfx.EDICT -> {
					float in = Mth.clamp((f.age + partial) / 5f, 0, 1), pulse = 0.75f + 0.25f * Mth.sin((f.age + partial) * 0.6f);
					int frame = (int) ((f.age + partial) / 2.5f) % 12;
					Identifier seal = InfinityGauntlet.id(String.format("textures/particle/doctor_doom/edict_warning_%02d.png", frame));
					float spin = (f.age + partial) * 3f;
					d.flat(seal, f.a, f.size * in, spin, 0xFFFFFF, 0.95f * pulse);
					d.flat(RING, f.a, f.size * 1.05f * in, 0, c[1], 0.8f * pulse);
					d.flat(GLOW, f.a, f.size * 0.9f * in, 0, c[1], 0.35f * pulse * (0.6f + 0.4f * t));
				}
				case DoomVfx.COLUMN -> {
					float k = 1 - t, grow = Math.min(1, t * 6);
					d.cylinder(COLUMN, f.a, f.size * (0.8f + 0.25f * t), 22 * grow, c[1], 0.9f * k);
					d.cylinder(COLUMN, f.a, f.size * 0.3f, 26 * grow, c[0], k);
					d.flat(GLOW, f.a, f.size * 1.6f, 0, c[0], 0.7f * k);
				}
				case DoomVfx.CHARGE -> {
					if (!(mc.level.getEntity(f.entity) instanceof LivingEntity e)) break;
					float pulse = Mth.sin(Mth.PI * t);
					d.billboard(GLOW, hand(e, partial), f.size * (1.2f + pulse), c[1], 0.9f * pulse);
					d.billboard(CORE, hand(e, partial), f.size * 0.6f, c[0], pulse);
				}
				default -> {}
			}
		}
		for (Fx s : SHIELDS.values()) {
			Entity e = mc.level.getEntity(s.entity);
			if (e == null) continue;
			float age = s.age + partial;
			float alpha = Mth.clamp(Math.min(age / 5f, (s.life - age) / 6f), 0, 1);
			float hit = s.hitAge < 8 ? 1 - s.hitAge / 8f : 0;
			Vec3 center = e.getPosition(partial).add(0, e.getBbHeight() * 0.5, 0);
			// From inside the shell (first person) it is a faint veil, not a wall.
			if (center.distanceTo(cam) < s.size) alpha *= 0.3f;
			d.sphere(HEX, center, s.size * (1 + 0.04f * Mth.sin(age * 0.3f) + 0.08f * hit), STYLE[s.style][1], alpha * (0.32f + 0.5f * hit), age * 0.01f);
			d.flat(RING, e.getPosition(partial).add(0, 0.05, 0), s.size * 1.1f, age * 2, STYLE[s.style][1], alpha * 0.7f);
		}
		for (Fx j : JETS.values()) {
			if (!(mc.level.getEntity(j.entity) instanceof LivingEntity e)) continue;
			Vec3 motion = e.getDeltaMovement();
			Vec3 dir = new Vec3(-motion.x * 1.5, -1, -motion.z * 1.5).normalize();
			float flicker = 0.85f + 0.15f * Mth.sin((j.age + partial) * 2.7f);
			for (Vec3 n : nozzles(e, partial)) {
				d.beam(FLAME, n, n.add(dir.scale(0.95 * flicker)), 0.32f, STYLE[DoomVfx.GOLD][1], 0.95f);
				d.beam(FLAME, n, n.add(dir.scale(0.5 * flicker)), 0.16f, STYLE[DoomVfx.GOLD][0], 1f);
				d.billboard(GLOW, n, 0.45f, STYLE[DoomVfx.GOLD][1], 0.7f);
			}
		}
		for (Fx o : ORBITS.values()) {
			Entity e = mc.level.getEntity(o.entity);
			if (e == null) continue;
			Vec3 base = e.getPosition(partial);
			float time = clock + partial;
			int count = (int) o.size;
			float in = Mth.clamp((o.age + partial) / 8f, 0, 1);
			float lift = e instanceof com.dantemorrigan.gauntlet.doom.DoctorDoom ? 3.2f : 1.9f;
			for (int i = 0; i < count; i++) {
				double ang = time * 0.12 + i * Math.TAU / 7;
				Vec3 at = base.add(Math.cos(ang) * 1.5 * in, lift + Math.sin(ang * 2) * 0.2, Math.sin(ang) * 1.5 * in);
				d.billboard(GLOW, at, 1.1f, STYLE[DoomVfx.SUN][1], 0.85f);
				d.billboard(CORE, at, 0.42f, STYLE[DoomVfx.SUN][0], 1f);
			}
		}
		if (preview != null) {
			float time = clock + partial;
			float pulse = 0.75f + 0.25f * Mth.sin(time * 0.35f);
			if (previewMode == 1) {
				d.flat(RING, preview.add(0, 0.06, 0), previewRadius, time * 1.5f, STYLE[DoomVfx.EDICT_GREEN][1], 0.55f * pulse);
				d.flat(RING, preview.add(0, 0.07, 0), previewRadius * 0.62f, -time * 2.2f, STYLE[DoomVfx.EDICT_GREEN][0], 0.4f * pulse);
				d.flat(GLOW, preview.add(0, 0.06, 0), previewRadius * 0.6f, 0, STYLE[DoomVfx.EDICT_GREEN][0], 0.16f);
			} else {
				d.flat(RING, preview.add(0, 0.06, 0), previewRadius, time * 3f, STYLE[DoomVfx.GREEN][1], 0.8f * pulse);
				d.flat(GLOW, preview.add(0, 0.06, 0), previewRadius * 1.3f, 0, STYLE[DoomVfx.GREEN][0], 0.3f);
				d.tear(preview.add(0, 1.0, 0), 0.22f, 1.8f, STYLE[DoomVfx.GREEN], 0.5f * pulse);
			}
		}
	}

	/** Three cones from the castle towers, sweeping the slope; red and fixed on the intruder while the alarm sounds. */
	private static void searchlights(Draw d, Minecraft mc, float partial) {
		double t = mc.level.getGameTime() + partial;
		Entity caught = alarmTicks > 0 ? mc.level.getEntity(alarmEntity) : null;
		boolean red = alarmTicks > 0;
		int color = red ? 0xFF3A28 : 0xFFF0C8;
		float flicker = red ? 0.8f + 0.2f * Mth.sin((float) t * 1.7f) : 1;
		for (int i = 0; i < com.dantemorrigan.gauntlet.doom.Searchlights.LAMPS.length; i++) {
			Vec3 lamp = com.dantemorrigan.gauntlet.doom.Searchlights.LAMPS[i];
			Vec3 dir = caught != null ? caught.getPosition(partial).add(0, caught.getBbHeight() * 0.5, 0).subtract(lamp).normalize()
					: com.dantemorrigan.gauntlet.doom.Searchlights.direction(i, t);
			Vec3 spot = com.dantemorrigan.gauntlet.doom.Searchlights.spot(mc.level, i, dir);
			float spotR = com.dantemorrigan.gauntlet.doom.Searchlights.SPOT;
			// A beam held on the viewer would fill the screen: it thins out toward them instead.
			float end = caught == mc.getCameraEntity() ? 0.25f : 1;
			d.cone(SEARCH, lamp, spot, 0.7f, spotR * 2.4f, color, 0.34f * flicker, 0.12f * flicker * end);
			d.cone(SEARCH, lamp, spot, 0.3f, spotR * 1.1f, color, 0.3f * flicker, 0.1f * flicker * end);
			d.billboard(GLOW, lamp, 3.2f, color, 0.8f * flicker);
			d.billboard(CORE, lamp, 1.1f, 0xFFFFFF, 0.95f);
			if (spot.distanceTo(lamp) > com.dantemorrigan.gauntlet.doom.Searchlights.RANGE - 1) continue;
		d.flat(GLOW, spot.add(0, 0.08, 0), spotR * 1.25f, 0, color, 0.42f * flicker);
			d.flat(GLOW, spot.add(0, 0.09, 0), spotR * 0.55f, 0, 0xFFFFFF, 0.25f * flicker);
		}
	}

	/** The gate back to the wider world: centre of its arch in the city wall. */
	private static final Vec3 GATE = new Vec3(0.5, com.dantemorrigan.gauntlet.doom.LatveriaPlan.VALLEY_Y + 4.0, 128.5);

	private static Vec3 cam() {
		return Minecraft.getInstance().gameRenderer.getMainCamera().position();
	}

	private static Vec3 hand(LivingEntity e, float partial) {
		Vec3 look = e.getViewVector(partial);
		Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
		return e.getEyePosition(partial).add(look.scale(0.6)).add(right.scale(0.35)).add(0, -0.35, 0);
	}

	/** Builds quads in camera-relative world space; every type is the emissive "eyes" type with a soft texture. */
	private record Draw(LevelRenderContext ctx, PoseStack pose, Vec3 cam, Vec3 right, Vec3 up) {
		private void quads(Identifier texture, java.util.function.Consumer<Emit> body) {
			RenderType type = RenderTypes.eyes(texture);
			ctx.submitNodeCollector().submitCustomGeometry(pose, type, (p, out) -> body.accept(new Emit(p, out, cam)));
		}

		void billboard(Identifier texture, Vec3 c, float size, int rgb, float alpha) {
			if (alpha <= 0.01f || size <= 0) return;
			int color = argb(rgb, alpha);
			Vec3 r = right.scale(size * 0.5), u = up.scale(size * 0.5);
			quads(texture, e -> e.quad(c.subtract(r).subtract(u), c.add(r).subtract(u), c.add(r).add(u), c.subtract(r).add(u), color, false));
		}

		/** A horizontal disc of the texture, turned by {@code spin} degrees; seen from above and below. */
		void flat(Identifier texture, Vec3 c, float radius, float spin, int rgb, float alpha) {
			if (alpha <= 0.01f || radius <= 0) return;
			int color = argb(rgb, alpha);
			double a = Math.toRadians(spin);
			Vec3 x = new Vec3(Math.cos(a), 0, Math.sin(a)).scale(radius), z = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(radius);
			quads(texture, e -> e.quad(c.subtract(x).subtract(z), c.add(x).subtract(z), c.add(x).add(z), c.subtract(x).add(z), color, true));
		}

		/** An upright rectangle in the plane of the X axis (facing north and south), both sides, fixed in the world. */
		void plane(Identifier texture, Vec3 c, float halfW, float halfH, int rgb, float alpha) {
			if (alpha <= 0.01f) return;
			int color = argb(rgb, alpha);
			Vec3 x = new Vec3(halfW, 0, 0), y = new Vec3(0, halfH, 0);
			quads(texture, e -> e.quad(c.subtract(x).subtract(y), c.add(x).subtract(y), c.add(x).add(y), c.subtract(x).add(y), color, true));
		}

		/** An upright disc turned toward the camera around the vertical axis. */
		void vertical(Identifier texture, Vec3 c, float radius, int rgb, float alpha) {
			if (alpha <= 0.01f) return;
			Vec3 to = cam.subtract(c);
			Vec3 x = new Vec3(-to.z, 0, to.x).normalize().scale(radius), y = new Vec3(0, radius, 0);
			int color = argb(rgb, alpha);
			quads(texture, e -> e.quad(c.subtract(x).subtract(y), c.add(x).subtract(y), c.add(x).add(y), c.subtract(x).add(y), color, true));
		}

		/** The rift: a dark lens with a glowing rim, upright, facing the camera around the vertical axis. */
		void tear(Vec3 c, float halfWidth, float height, int[] style, float open) {
			if (open <= 0.02f) return;
			Vec3 to = cam.subtract(c);
			Vec3 x = new Vec3(-to.z, 0, to.x).normalize();
			Vec3 y = new Vec3(0, height * 0.5, 0);
			Vec3 wide = x.scale(halfWidth * 2.2), thin = x.scale(halfWidth * 1.1);
			int rim = argb(style[1], 0.9f * open), glow = argb(style[1], 0.45f * open), dark = ARGB.color((int) (230 * open), 4, 14, 8);
			quads(GLOW, e -> e.quad(c.subtract(wide).subtract(y.scale(1.25)), c.add(wide).subtract(y.scale(1.25)), c.add(wide).add(y.scale(1.25)), c.subtract(wide).add(y.scale(1.25)), glow, true));
			quads(CORE, e -> e.quad(c.subtract(thin).subtract(y), c.add(thin).subtract(y), c.add(thin).add(y), c.subtract(thin).add(y), rim, true));
			Vec3 core = x.scale(halfWidth * 0.6);
			quads(CORE, e -> e.quad(c.subtract(core).subtract(y.scale(0.85)), c.add(core).subtract(y.scale(0.85)), c.add(core).add(y.scale(0.85)), c.subtract(core).add(y.scale(0.85)), dark, true));
		}

		/** A camera-facing band along a line of points (first point = head), fading toward the tail. */
		void ribbon(Identifier texture, List<Vec3> points, float width, int rgb, float alpha) {
			if (points.size() < 2 || alpha <= 0.01f) return;
			quads(texture, e -> {
				int n = points.size();
				for (int i = 0; i + 1 < n; i++) {
					Vec3 p0 = points.get(i), p1 = points.get(i + 1);
					Vec3 dir = p0.subtract(p1);
					if (dir.lengthSqr() < 1e-6) continue;
					Vec3 side0 = dir.cross(p0.subtract(cam)).normalize().scale(width * 0.5 * (1 - i / (float) n));
					Vec3 side1 = dir.cross(p1.subtract(cam)).normalize().scale(width * 0.5 * (1 - (i + 1) / (float) n));
					float u0 = 1 - i / (float) (n - 1), u1 = 1 - (i + 1) / (float) (n - 1);
					int c0 = argb(rgb, alpha * u0), c1 = argb(rgb, alpha * u1);
					e.vertex(p1.subtract(side1), u1, 0, c1);
					e.vertex(p0.subtract(side0), u0, 0, c0);
					e.vertex(p0.add(side0), u0, 1, c0);
					e.vertex(p1.add(side1), u1, 1, c1);
					e.vertex(p1.add(side1), u1, 1, c1);
					e.vertex(p0.add(side0), u0, 1, c0);
					e.vertex(p0.subtract(side0), u0, 0, c0);
					e.vertex(p1.subtract(side1), u1, 0, c1);
				}
			});
		}

		/** A camera-facing cone from {@code a} (width {@code w0}) to {@code b} (width {@code w1}), its alpha fading from a to b. */
		void cone(Identifier texture, Vec3 a, Vec3 b, float w0, float w1, int rgb, float alphaA, float alphaB) {
			Vec3 dir = b.subtract(a);
			if (dir.lengthSqr() < 1e-4) return;
			int segs = 6;
			quads(texture, e -> {
				for (int i = 0; i < segs; i++) {
					float k0 = i / (float) segs, k1 = (i + 1) / (float) segs;
					Vec3 p0 = a.add(dir.scale(k0)), p1 = a.add(dir.scale(k1));
					Vec3 s0 = dir.cross(p0.subtract(cam)).normalize().scale((w0 + (w1 - w0) * k0) * 0.5);
					Vec3 s1 = dir.cross(p1.subtract(cam)).normalize().scale((w0 + (w1 - w0) * k1) * 0.5);
					int c0 = argb(rgb, alphaA + (alphaB - alphaA) * k0), c1 = argb(rgb, alphaA + (alphaB - alphaA) * k1);
					e.vertex(p0.subtract(s0), 0, k0, c0); e.vertex(p0.add(s0), 1, k0, c0); e.vertex(p1.add(s1), 1, k1, c1); e.vertex(p1.subtract(s1), 0, k1, c1);
					e.vertex(p1.subtract(s1), 0, k1, c1); e.vertex(p1.add(s1), 1, k1, c1); e.vertex(p0.add(s0), 1, k0, c0); e.vertex(p0.subtract(s0), 0, k0, c0);
				}
			});
		}

		/** A flame or beam quad from {@code a} to {@code b}, turned around its own axis toward the camera. */
		void beam(Identifier texture, Vec3 a, Vec3 b, float width, int rgb, float alpha) {
			Vec3 dir = b.subtract(a);
			Vec3 side = dir.cross(a.subtract(cam)).normalize().scale(width * 0.5);
			int color = argb(rgb, alpha);
			quads(texture, e -> {
				e.vertex(a.subtract(side), 0, 0, color);
				e.vertex(a.add(side), 1, 0, color);
				e.vertex(b.add(side), 1, 1, color);
				e.vertex(b.subtract(side), 0, 1, color);
				e.vertex(b.subtract(side), 0, 1, color);
				e.vertex(b.add(side), 1, 1, color);
				e.vertex(a.add(side), 1, 0, color);
				e.vertex(a.subtract(side), 0, 0, color);
			});
		}

		/** An open cylinder rising from {@code c}; the texture runs around (u) and up (v). */
		void cylinder(Identifier texture, Vec3 c, float radius, float height, int rgb, float alpha) {
			if (alpha <= 0.01f || height <= 0) return;
			int color = argb(rgb, alpha);
			quads(texture, e -> {
				int seg = 20;
				for (int i = 0; i < seg; i++) {
					double a0 = i * Math.TAU / seg, a1 = (i + 1) * Math.TAU / seg;
					Vec3 b0 = c.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius), b1 = c.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius);
					Vec3 t0 = b0.add(0, height, 0), t1 = b1.add(0, height, 0);
					float u0 = i / (float) seg, u1 = (i + 1) / (float) seg;
					e.vertex(b0, u0, 1, color); e.vertex(b1, u1, 1, color); e.vertex(t1, u1, 0, color); e.vertex(t0, u0, 0, color);
					e.vertex(t0, u0, 0, color); e.vertex(t1, u1, 0, color); e.vertex(b1, u1, 1, color); e.vertex(b0, u0, 1, color);
				}
			});
		}

		/** A shell around {@code c}, both faces, the texture scrolling by {@code scroll}. */
		void sphere(Identifier texture, Vec3 c, float radius, int rgb, float alpha, float scroll) {
			if (alpha <= 0.01f) return;
			int color = argb(rgb, alpha);
			quads(texture, e -> {
				int lat = 10, lon = 18;
				for (int i = 0; i < lat; i++) {
					double t0 = Math.PI * i / lat - Math.PI / 2, t1 = Math.PI * (i + 1) / lat - Math.PI / 2;
					for (int j = 0; j < lon; j++) {
						double p0 = Math.TAU * j / lon, p1 = Math.TAU * (j + 1) / lon;
						Vec3 a = point(c, radius, t0, p0), b = point(c, radius, t0, p1), cc = point(c, radius, t1, p1), dd = point(c, radius, t1, p0);
						float u0 = j / (float) lon * 3 + scroll, u1 = (j + 1) / (float) lon * 3 + scroll, v0 = i / (float) lat * 2, v1 = (i + 1) / (float) lat * 2;
						e.vertex(a, u0, v0, color); e.vertex(b, u1, v0, color); e.vertex(cc, u1, v1, color); e.vertex(dd, u0, v1, color);
						e.vertex(dd, u0, v1, color); e.vertex(cc, u1, v1, color); e.vertex(b, u1, v0, color); e.vertex(a, u0, v0, color);
					}
				}
			});
		}

		private static Vec3 point(Vec3 c, float r, double lat, double lon) {
			return c.add(Math.cos(lat) * Math.cos(lon) * r, Math.sin(lat) * r, Math.cos(lat) * Math.sin(lon) * r);
		}
	}

	private record Emit(PoseStack.Pose pose, VertexConsumer out, Vec3 cam) {
		void vertex(Vec3 p, float u, float v, int color) {
			out.addVertex(pose, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z)).setColor(color).setUv(u, v)
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
		}

		void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, boolean bothSides) {
			vertex(a, 0, 1, color); vertex(b, 1, 1, color); vertex(c, 1, 0, color); vertex(d, 0, 0, color);
			if (bothSides) { vertex(d, 0, 0, color); vertex(c, 1, 0, color); vertex(b, 1, 1, color); vertex(a, 0, 1, color); }
		}
	}

	private static int argb(int rgb, float alpha) {
		return ARGB.color((int) (Mth.clamp(alpha, 0, 1) * 255), (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255);
	}

	private DoomVfxClient() {}
}
