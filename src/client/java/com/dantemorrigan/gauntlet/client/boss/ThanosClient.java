package com.dantemorrigan.gauntlet.client.boss;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.ModSounds;
import com.dantemorrigan.gauntlet.boss.Thanos;
import com.dantemorrigan.gauntlet.boss.ThanosEncounter;
import com.dantemorrigan.gauntlet.client.celestial.CelestialSky;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Thanos on the client: Sanctuary II in the sky, the blue landing beam, the rift in reality, the shot at the dragon,
 * the red sky and red sun, the End's cold glow, the boss bar with his lines, camera shake and the battle theme.
 * The server only sends cues ({@link ModNetwork.ThanosFx}); timing and motion are local.
 */
public final class ThanosClient {
	private static final Identifier BAR_FRAME = InfinityGauntlet.id("textures/gui/thanos/boss_frame.png");
	private static final Identifier BAR_FILL = InfinityGauntlet.id("textures/gui/thanos/boss_fill.png");
	private static final Identifier RED_SUN = InfinityGauntlet.id("textures/environment/thanos/red_sun.png");
	private static final Identifier[] BEAM = frames("arrival_beam"), RIFT = frames("nether_rift"), RIFT_CLOSE = frames("rift_close"), RAY = frames("dragon_ray");
	/** Ship model units -> blocks. */
	private static final float SHIP_SCALE = 13f;

	private static Identifier[] frames(String name) {
		Identifier[] out = new Identifier[8];
		for (int i = 0; i < 8; i++) out[i] = InfinityGauntlet.id("textures/particle/thanos/" + name + "_0" + i + ".png");
		return out;
	}

	private static final class Ship {
		Vec3 hover, dir;
		float yaw;
		ResourceKey<Level> dim;
		boolean end;
		long arrive, leave = -1;
		int approach, leaveTicks;
	}

	private record Beam(Vec3 ground, long start, int ticks, boolean up) {}

	private static final class Rift {
		Vec3 pos;
		float yaw;
		long open;
		int openTicks;
		long close = -1;
	}

	private record Ray(Vec3 to, long start, int ticks) {}

	private static Ship ship;
	private static final List<Beam> BEAMS = new ArrayList<>();
	private static final List<Rift> RIFTS = new ArrayList<>();
	private static Ray ray;
	private static long charge = -1;
	private static int chargeTicks;
	private static long ticks;
	private static float tint, tintO;
	private static float shake;
	private static int shakeTicks;
	private static int captionLine = -1;
	private static long captionUntil;
	private static float barShown, barLag = 1, barHealth = 1;
	private static Music music;

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.ThanosFx.TYPE, (fx, ctx) -> onFx(fx));
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> clear(true));
		ClientTickEvents.END_CLIENT_TICK.register(ThanosClient::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(ThanosClient::render);
		HudElementRegistry.addLast(InfinityGauntlet.id("thanos_boss_bar"), (g, d) -> hud(g, d.getGameTimeDeltaPartialTick(false)));
	}

	private static void clear(boolean hard) {
		ship = null;
		BEAMS.clear();
		RIFTS.clear();
		ray = null;
		charge = -1;
		captionLine = -1;
		if (hard) {
			tint = tintO = 0;
			if (music != null) { music.halt(); music = null; }
		}
	}

	// ------------------------------------------------------------------ cues

	private static void onFx(ModNetwork.ThanosFx fx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		switch (fx.kind()) {
			case ThanosEncounter.FX_SHIP_ARRIVE -> {
				ship = new Ship();
				ship.hover = fx.a();
				ship.dir = fx.b();
				ship.yaw = fx.f();
				ship.dim = mc.level.dimension();
				ship.end = fx.id() == 2;
				ship.arrive = ticks;
				ship.approach = Math.max(1, fx.ticks());
			}
			case ThanosEncounter.FX_SHIP_LEAVE -> {
				if (ship != null && ship.leave < 0) {
					ship.leave = ticks;
					ship.leaveTicks = Math.max(1, fx.ticks());
				}
			}
			case ThanosEncounter.FX_BEAM -> {
				BEAMS.add(new Beam(fx.a(), ticks, fx.ticks(), false));
				shake(0.35f, 20);
			}
			case ThanosEncounter.FX_BEAM_UP -> BEAMS.add(new Beam(fx.a(), ticks, fx.ticks(), true));
			case ThanosEncounter.FX_DRAGON_CHARGE -> { charge = ticks; chargeTicks = fx.ticks(); }
			case ThanosEncounter.FX_DRAGON_RAY -> {
				charge = -1;
				ray = new Ray(fx.b(), ticks, Math.max(4, fx.ticks()));
				Vec3 at = fx.b();
				mc.level.addParticle(ModParticles.THANOS_DRAGON_IMPACT, true, true, at.x, at.y, at.z, 9, 0, 0);
				mc.level.addParticle(ModParticles.THANOS_DRAGON_SHOCKWAVE, true, true, at.x, at.y, at.z, 14, 0, 0);
				for (int i = 0; i < 6; i++) {
					mc.level.addParticle(ParticleTypes.EXPLOSION_EMITTER, true, true, at.x + (i - 3) * 2.5, at.y + (i % 3) * 1.5, at.z + (i % 2) * 3, 0, 0, 0);
				}
				shake(1.0f, 30);
			}
			case ThanosEncounter.FX_RIFT_OPEN -> {
				Rift r = new Rift();
				r.pos = fx.a();
				r.yaw = fx.f();
				r.open = ticks;
				r.openTicks = Math.max(4, fx.ticks());
				RIFTS.add(r);
				shake(0.3f, 30);
			}
			case ThanosEncounter.FX_RIFT_CLOSE -> {
				Rift nearest = null;
				for (Rift r : RIFTS) if (r.close < 0 && (nearest == null || r.pos.distanceToSqr(fx.a()) < nearest.pos.distanceToSqr(fx.a()))) nearest = r;
				if (nearest != null) nearest.close = ticks;
				mc.level.addParticle(ModParticles.THANOS_RIFT_CLOSE, true, true, fx.a().x, fx.a().y + 3.5, fx.a().z, 6, 0, 0);
			}
			case ThanosEncounter.FX_LINE -> {
				captionLine = fx.id();
				captionUntil = ticks + fx.ticks() + 30;
			}
			case ThanosEncounter.FX_MUSIC_START -> {
				// Sent at the start and again every few seconds of the fight: (re)start unless it is really playing.
				if (mc.player == null || !mc.player.isAlive()) break;
				boolean playing = music != null && !music.isStopped() && !music.fading() && mc.getSoundManager().isActive(music);
				if (!playing) {
					if (music != null && !music.isStopped() && !music.fading() && music.age < 40) break; // just queued, give it a moment
					if (music != null) music.halt();
					music = new Music();
					mc.getSoundManager().play(music);
				}
				mc.getMusicManager().stopPlaying();
			}
			case ThanosEncounter.FX_MUSIC_STOP -> { if (music != null) music.fadeOut(Math.max(1, fx.ticks())); }
			case ThanosEncounter.FX_IMPACT -> {
				Vec3 c = fx.a();
				float s = fx.f() > 0 ? fx.f() : 1;
				mc.level.addParticle(ModParticles.THANOS_ARRIVAL_RING, true, true, c.x, c.y + 0.15, c.z, 0, 1.6 * s, 0);
				mc.level.addParticle(ModParticles.THANOS_ARRIVAL_FLASH, true, true, c.x, c.y + 1.4, c.z, 1.4 * s, 0, 0);
				mc.level.addParticle(ModParticles.THANOS_SLAM_DUST, true, true, c.x, c.y + 0.4, c.z, 1.8 * s, 0, 0);
				shake(0.9f * s, 24);
			}
			case ThanosEncounter.FX_SHAKE -> shake(fx.f(), Math.max(4, fx.ticks()));
			case ThanosEncounter.FX_END -> clear(false);
			default -> {}
		}
	}

	private static void shake(float amount, int t) {
		if (amount >= shake * shakeTicks / 20f) {
			shake = amount;
			shakeTicks = t;
		}
	}

	// ------------------------------------------------------------------ queries for mixins

	/** 0..1: how much the Overworld sky (and sun) turn red under the ship. */
	public static float skyTint(float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || !mc.level.dimension().equals(Level.OVERWORLD)) return 0;
		return Mth.lerp(partial, tintO, tint);
	}

	/** 0..1: the soft cold light of the ship in the End. */
	public static float endLight(float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || !mc.level.dimension().equals(Level.END)) return 0;
		return Mth.lerp(partial, tintO, tint);
	}

	public static boolean hideSun() {
		return skyTint(0) > 0.45f;
	}

	public static float shakeYaw(float partial) {
		if (shakeTicks <= 0) return 0;
		float t = ticks + partial;
		return shake * Math.min(1, shakeTicks / 10f) * Mth.sin(t * 2.9f) * 1.2f;
	}

	public static float shakePitch(float partial) {
		if (shakeTicks <= 0) return 0;
		float t = ticks + partial;
		return shake * Math.min(1, shakeTicks / 10f) * Mth.cos(t * 3.7f) * 0.9f;
	}

	/** Red sun, drawn at the end of the vanilla sun/moon pass (same transform as vanilla). */
	public static void drawRedSun(PoseStack pose, float sunAngle, float rainBrightness, float partial) {
		float f = skyTint(partial);
		if (f <= 0.01f) return;
		Quaternionf q = new Quaternionf().rotateY(-Mth.HALF_PI).rotateX(sunAngle);
		float size = 38;
		Vector3f c = new Vector3f(0, 100, 0).rotate(q), x = new Vector3f(size, 0, 0).rotate(q), z = new Vector3f(0, 0, size).rotate(q);
		int color = ARGB.colorFromFloat(Math.min(1f, f * 1.6f) * rainBrightness, 1f, 1f, 1f);
		CelestialSky.draw(pose, RED_SUN, buf -> {
			Vector3f[] p = {new Vector3f(c).sub(x).sub(z), new Vector3f(c).add(x).sub(z), new Vector3f(c).add(x).add(z), new Vector3f(c).sub(x).add(z)};
			float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
			for (int i = 0; i < 4; i++) buf.addVertex(pose.last().pose(), p[i].x, p[i].y, p[i].z).setUv(uv[i][0], uv[i][1]).setColor(color);
			for (int i = 3; i >= 0; i--) buf.addVertex(pose.last().pose(), p[i].x, p[i].y, p[i].z).setUv(uv[i][0], uv[i][1]).setColor(color);
		});
	}

	// ------------------------------------------------------------------ ticking

	private static void tick(Minecraft mc) {
		ticks++;
		tintO = tint;
		if (shakeTicks > 0) shakeTicks--;
		if (mc.level == null || mc.player == null) return;
		if (ship != null && !mc.level.dimension().equals(ship.dim)) clear(false);

		// Sky / light follow the ship: rise while it arrives, fall while it leaves.
		float target = 0;
		if (ship != null) {
			float in = Mth.clamp((ticks - ship.arrive) / (float) ship.approach, 0, 1);
			float out = ship.leave < 0 ? 1 : 1 - Mth.clamp((ticks - ship.leave) / (float) ship.leaveTicks, 0, 1);
			target = Math.min(in, out);
			if (ship.leave >= 0 && ticks - ship.leave > ship.leaveTicks + 10) ship = null;
		}
		tint += Mth.clamp(target - tint, -0.02f, 0.02f);

		RandomSource rnd = mc.level.getRandom();
		if (ship != null) {
			Vec3 emitter = emitter(0);
			if (ticks % 6 == 0 && ship.leave < 0) {
				mc.level.addParticle(ModParticles.THANOS_SHIP_MIST, true, true, emitter.x + rnd.nextGaussian() * 6, emitter.y - 2, emitter.z + rnd.nextGaussian() * 6, 3.5, 0, 0);
			}
			if (ship.end && ticks % 10 == 0) {
				Vec3 p = shipPos(0);
				mc.level.addParticle(ModParticles.THANOS_END_HULL_HALO, true, true, p.x, p.y, p.z, 9, 0, 0);
			}
		}
		for (Iterator<Beam> it = BEAMS.iterator(); it.hasNext(); ) {
			Beam b = it.next();
			long age = ticks - b.start;
			if (age > b.ticks) { it.remove(); continue; }
			Vec3 top = ship != null ? emitter(0) : b.ground.add(0, 60, 0);
			for (int i = 0; i < 3; i++) {
				double k = rnd.nextDouble();
				Vec3 p = top.lerp(b.ground, k);
				double a = rnd.nextDouble() * Mth.TWO_PI, r = 0.6 + rnd.nextDouble() * 1.4;
				mc.level.addParticle(ModParticles.THANOS_ARRIVAL_STREAK, true, true, p.x + Math.cos(a) * r, p.y, p.z + Math.sin(a) * r, 0.8, 0, 0);
			}
			if (age % 7 == 0) mc.level.addParticle(ModParticles.THANOS_ARRIVAL_RING, true, true, b.ground.x, b.ground.y + 0.1, b.ground.z, 0, 1.1, 0);
		}
		for (Iterator<Rift> it = RIFTS.iterator(); it.hasNext(); ) {
			Rift r = it.next();
			if (r.close >= 0 && ticks - r.close > 22) { it.remove(); continue; }
			if (ticks % 3 == 0) {
				Vec3 side = new Vec3(Mth.cos(r.yaw * Mth.DEG_TO_RAD), 0, -Mth.sin(r.yaw * Mth.DEG_TO_RAD));
				double h = rnd.nextDouble() * 7;
				Vec3 p = r.pos.add(side.scale(rnd.nextGaussian() * 0.6)).add(0, h, 0);
				mc.level.addParticle(ModParticles.THANOS_RIFT_SPARKS, true, true, p.x, p.y, p.z, 0.9, 0, 0);
				mc.level.addParticle(ParticleTypes.REVERSE_PORTAL, true, true, p.x, p.y, p.z, 0, 0.05, 0);
			}
		}
		if (charge >= 0 && ship != null) {
			Vec3 c = cannon(0);
			float k = Mth.clamp((ticks - charge) / (float) chargeTicks, 0, 1);
			if (ticks % 3 == 0) mc.level.addParticle(ModParticles.THANOS_DRAGON_CHARGE, true, true, c.x, c.y, c.z, 3 + k * 6, 0, 0);
		}
		if (ray != null && ticks - ray.start > ray.ticks + 6) ray = null;

		// The dying Titan: ash drifting off on the wind, more as he crumbles.
		AABB around = mc.player.getBoundingBox().inflate(64);
		for (Thanos t : mc.level.getEntitiesOfClass(Thanos.class, around, e -> e.isDeadOrDying())) {
			if (t.deathTime < Thanos.DUST_START) continue;
			float k = (t.deathTime - Thanos.DUST_START) / (float) (Thanos.DEATH_TICKS - Thanos.DUST_START);
			int n = (int) (6 + 14 * Math.sin(Math.min(1, k) * Math.PI));
			for (int i = 0; i < n; i++) {
				double px = t.getX() + (rnd.nextDouble() - 0.5) * 1.6, py = t.getY() + rnd.nextDouble() * 2.4 * (1 - k * 0.5), pz = t.getZ() + (rnd.nextDouble() - 0.5) * 1.6;
				float g = 0.35f + rnd.nextFloat() * 0.25f;
				mc.level.addParticle(new DustParticleOptions(ARGB.colorFromFloat(1, g + 0.1f, g, g - 0.08f), 0.9f + rnd.nextFloat() * 0.6f),
						px, py, pz, 0.08 + rnd.nextDouble() * 0.05, 0.02, 0.03);
				if (i % 3 == 0) mc.level.addParticle(ParticleTypes.ASH, px, py, pz, 0.1, 0, 0);
			}
		}

		// Battle theme: keep vanilla music out while it plays; it fades when the player dies.
		if (music != null) {
			if (music.isStopped()) music = null;
			else {
				mc.getMusicManager().stopPlaying();
				if (!mc.player.isAlive()) music.fadeOut(60);
			}
		}
	}

	// ------------------------------------------------------------------ ship geometry

	private static Vec3 shipPos(float partial) {
		if (ship == null) return Vec3.ZERO;
		float t = ticks + partial;
		float in = Mth.clamp((t - ship.arrive) / ship.approach, 0, 1);
		float e = 1 - (1 - in) * (1 - in) * (1 - in);
		Vec3 back = ship.dir.scale(-360 * (1 - e)).add(0, 110 * (1 - e), 0);
		Vec3 p = ship.hover.add(back).add(0, Mth.sin(t * 0.045f) * 0.7f, 0);
		if (ship.leave >= 0) {
			float k = Mth.clamp((t - ship.leave) / ship.leaveTicks, 0, 1);
			float l = k * k;
			p = p.add(ship.dir.scale(460 * l)).add(0, 170 * l, 0);
		}
		return p;
	}

	/** Pitch (degrees): nose down a little while braking on arrival, nose up while leaving. */
	private static float shipPitch(float partial) {
		if (ship == null) return 0;
		float t = ticks + partial;
		float in = Mth.clamp((t - ship.arrive) / ship.approach, 0, 1);
		float p = -8 * (1 - in) * (1 - in);
		if (ship.leave >= 0) p += 10 * Mth.clamp((t - ship.leave) / 40f, 0, 1);
		return p;
	}

	private static Vec3 local(float partial, float x, float y, float z) {
		Vector3f v = new Vector3f(x, y, z).mul(SHIP_SCALE / 16f);
		v.rotateX(shipPitch(partial) * Mth.DEG_TO_RAD);
		v.rotateY(ship.yaw * Mth.DEG_TO_RAD);
		return shipPos(partial).add(v.x, v.y, v.z);
	}

	static Vec3 emitter(float partial) {
		return local(partial, 0, -19, 0);
	}

	static Vec3 cannon(float partial) {
		return local(partial, 0, -19, -24);
	}

	// ------------------------------------------------------------------ world rendering

	private static void render(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 cam = mc.gameRenderer.getMainCamera().position();
		PoseStack pose = ctx.poseStack();
		if (ship != null) renderShip(ctx, pose, cam, partial);
		for (Beam b : BEAMS) renderBeam(ctx, pose, cam, b, partial);
		for (Rift r : RIFTS) renderRift(ctx, pose, cam, r, partial);
		if (ray != null && ship != null) {
			float age = ticks - ray.start + partial;
			float a = age < ray.ticks ? 1 : Mth.clamp(1 - (age - ray.ticks) / 6f, 0, 1);
			Identifier tex = RAY[(int) (age / 1.5f) % 8];
			strip(ctx, pose, cam, cannon(partial), ray.to, 3.6f * (0.6f + 0.4f * a), tex, ARGB.color((int) (255 * a), 255, 255, 255), age * 0.3f);
			strip(ctx, pose, cam, cannon(partial), ray.to, 8f, tex, ARGB.color((int) (90 * a), 255, 180, 140), age * 0.3f);
		}
	}

	private static void renderShip(LevelRenderContext ctx, PoseStack pose, Vec3 cam, float partial) {
		GeoModel model = ThanosModels.ship();
		Vec3 p = shipPos(partial);
		pose.pushPose();
		pose.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
		pose.mulPose(Axis.YP.rotationDegrees(ship.yaw));
		pose.mulPose(Axis.XP.rotationDegrees(shipPitch(partial)));
		pose.scale(SHIP_SCALE, SHIP_SCALE, SHIP_SCALE);
		int light = LightCoordsUtil.pack(4, 15);
		var poses = new java.util.HashMap<String, GeoModel.BonePose>();
		// Recoil after the shot at the dragon.
		if (ray != null) {
			GeoAnimation recoil = ThanosModels.shipAnim("recoil");
			if (recoil != null) recoil.apply(poses, (ticks - ray.start + partial) / 20f, 1);
		}
		ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityCutout(ThanosRenderer.ATLAS), (pp, out) ->
				model.render(ThanosRenderer.stack(pp), out, poses, light, OverlayTexture.NO_OVERLAY, null));
		ctx.submitNodeCollector().order(1).submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(ThanosRenderer.EMISSIVE), (pp, out) ->
				model.render(ThanosRenderer.stack(pp), out, poses, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null));
		pose.popPose();
	}

	private static void renderBeam(LevelRenderContext ctx, PoseStack pose, Vec3 cam, Beam b, float partial) {
		float age = ticks - b.start + partial;
		Vec3 top = ship != null ? emitter(partial) : b.ground.add(0, 60, 0);
		Vec3 bottom = b.ground;
		// It reaches down from the ship, holds, and thins out at the end.
		float reach = b.up ? 1 : Mth.clamp(age / 10f, 0, 1);
		float fade = Mth.clamp((b.ticks - age) / 14f, 0, 1) * Mth.clamp(age / 4f, 0, 1);
		Vec3 end = top.lerp(bottom, reach);
		Identifier tex = BEAM[(int) (age / 2) % 8];
		float pulse = 1 + 0.08f * Mth.sin(age * 0.8f);
		strip(ctx, pose, cam, top, end, 2.6f * pulse * (0.4f + 0.6f * fade), tex, ARGB.color((int) (255 * fade), 255, 255, 255), age * 0.12f);
		strip(ctx, pose, cam, top, end, 7.5f * pulse, tex, ARGB.color((int) (95 * fade), 150, 200, 255), age * 0.07f);
	}

	private static void renderRift(LevelRenderContext ctx, PoseStack pose, Vec3 cam, Rift r, float partial) {
		float age = ticks - r.open + partial;
		boolean closing = r.close >= 0;
		float ca = closing ? ticks - r.close + partial : 0;
		float open = Mth.clamp(age / r.openTicks, 0, 1);
		open = open * open * (3 - 2 * open);
		float width = 4.2f * (closing ? Math.max(0, 1 - ca / 18f) : open);
		if (width <= 0.02f) return;
		float height = 7.5f * Math.min(1, 0.35f + open);
		Identifier tex = closing && ca < 16 ? RIFT_CLOSE[Math.min(7, (int) (ca / 2))] : RIFT[(int) (age / 2) % 8];
		pose.pushPose();
		pose.translate(r.pos.x - cam.x, r.pos.y + 0.2 - cam.y, r.pos.z - cam.z);
		pose.mulPose(Axis.YP.rotationDegrees(r.yaw));
		float w = width / 2, h = height;
		int c = ARGB.color(255, 255, 255, 255);
		for (float scale : new float[]{1f, 1.7f}) {
			int col = scale > 1 ? ARGB.color(110, 255, 160, 255) : c;
			float ww = w * scale, hh = h * (scale > 1 ? 1.12f : 1f), y0 = scale > 1 ? -0.4f : 0;
			ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(tex), (p, b) -> {
				vertex(b, p, -ww, y0, 0, 0, 1, col);
				vertex(b, p, ww, y0, 0, 1, 1, col);
				vertex(b, p, ww, y0 + hh, 0, 1, 0, col);
				vertex(b, p, -ww, y0 + hh, 0, 0, 0, col);
			});
		}
		pose.popPose();
	}

	/** A camera-facing strip from a to b, the texture repeating along it (one square tile per width). */
	private static void strip(LevelRenderContext ctx, PoseStack pose, Vec3 cam, Vec3 a, Vec3 b, float width, Identifier tex, int color, float scroll) {
		Vec3 axis = b.subtract(a);
		double len = axis.length();
		if (len < 0.01) return;
		Vec3 dir = axis.scale(1 / len);
		Vec3 mid = a.add(b).scale(0.5);
		Vec3 toCam = cam.subtract(mid);
		Vec3 side = dir.cross(toCam);
		if (side.lengthSqr() < 1e-6) side = dir.cross(new Vec3(1, 0, 0));
		side = side.normalize().scale(width / 2);
		Vec3 sideF = side;
		float tiles = (float) (len / width);
		pose.pushPose();
		pose.translate(a.x - cam.x, a.y - cam.y, a.z - cam.z);
		ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(tex), (p, buf) -> {
			Vec3 n = axis.normalize();
			int segs = Math.max(1, (int) Math.ceil(tiles));
			for (int i = 0; i < segs; i++) {
				float t0 = i / (float) segs, t1 = (i + 1) / (float) segs;
				Vec3 p0 = axis.scale(t0), p1 = axis.scale(t1);
				float v0 = t0 * tiles - scroll, v1 = t1 * tiles - scroll;
				vertexW(buf, p, p0.subtract(sideF), 0, v0, color, n);
				vertexW(buf, p, p0.add(sideF), 1, v0, color, n);
				vertexW(buf, p, p1.add(sideF), 1, v1, color, n);
				vertexW(buf, p, p1.subtract(sideF), 0, v1, color, n);
			}
		});
		pose.popPose();
	}

	private static void vertexW(VertexConsumer b, PoseStack.Pose p, Vec3 v, float u, float vv, int c, Vec3 n) {
		b.addVertex(p, (float) v.x, (float) v.y, (float) v.z).setColor(c).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p, 0, 1, 0);
	}

	private static void vertex(VertexConsumer b, PoseStack.Pose p, float x, float y, float z, float u, float v, int c) {
		b.addVertex(p, x, y, z).setColor(c).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p, 0, 0, 1);
	}

	// ------------------------------------------------------------------ HUD: boss bar and lines

	private static Thanos boss() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return null;
		Thanos best = null;
		double bestD = 128 * 128;
		for (Thanos t : mc.level.getEntitiesOfClass(Thanos.class, mc.player.getBoundingBox().inflate(128), e -> true)) {
			if (!t.fighting() && !t.isDeadOrDying()) continue;
			double d = t.distanceToSqr(mc.player);
			if (d < bestD) { bestD = d; best = t; }
		}
		return best;
	}

	private static void hud(GuiGraphicsExtractor g, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui) return;
		Thanos t = boss();
		float want = t != null && !(t.isDeadOrDying() && t.deathTime > Thanos.DUST_START) ? 1 : 0;
		barShown += Mth.clamp(want - barShown, -0.05f, 0.08f);
		int w = g.guiWidth();
		int x = (w - 182) / 2, y = 16;
		if (barShown > 0.01f) {
			float hp = t == null ? 0 : Mth.clamp(t.getHealth() / t.getMaxHealth(), 0, 1);
			barHealth = hp;
			barLag = barLag > hp ? Math.max(hp, barLag - 0.004f) : hp;
			int a = (int) (255 * barShown);
			Component name = Component.translatable("entity.infinity_gauntlet.thanos.title").withStyle(ChatFormatting.BOLD);
			int nw = mc.font.width(name);
			int gold = ARGB.color(a, 0xF2, 0xC2, 0x5A);
			g.text(mc.font, name, (w - nw) / 2, y - 11, gold, true);
			g.blit(RenderPipelines.GUI_TEXTURED, BAR_FRAME, x, y, 0, 0, 182, 9, 182, 9, 182, 9, ARGB.color(a, 255, 255, 255));
			int lag = (int) (182 * barLag), fill = (int) (182 * barHealth);
			if (lag > fill) g.blit(RenderPipelines.GUI_TEXTURED, BAR_FILL, x + fill, y, fill, 0, lag - fill, 9, lag - fill, 9, 182, 9, ARGB.color(a, 255, 240, 220));
			boolean enraged = t != null && t.flag(Thanos.FLAG_ENRAGED);
			float pulse = enraged ? 0.75f + 0.25f * Mth.sin((ticks + partial) * 0.3f) : 1f;
			int fc = enraged ? ARGB.color(a, 255, (int) (150 * pulse), (int) (150 * pulse)) : ARGB.color(a, 255, 255, 255);
			if (fill > 0) g.blit(RenderPipelines.GUI_TEXTURED, BAR_FILL, x, y, 0, 0, fill, 9, fill, 9, 182, 9, fc);
		}
		if (captionLine >= 0 && ticks < captionUntil) {
			float fade = Mth.clamp((captionUntil - ticks - partial) / 20f, 0, 1);
			Component line = Component.translatable("entity.infinity_gauntlet.thanos").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)
					.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
					.append(Component.translatable("thanos.infinity_gauntlet.line." + captionLine).withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC));
			int lw = mc.font.width(line);
			int cy = y + (barShown > 0.01f ? 14 : 0);
			int ba = (int) (150 * fade);
			g.fill((w - lw) / 2 - 6, cy - 3, (w + lw) / 2 + 6, cy + 11, ARGB.color(ba, 12, 6, 20));
			g.text(mc.font, line, (w - lw) / 2, cy, ARGB.color((int) (255 * fade), 255, 255, 255), true);
		}
	}

	// ------------------------------------------------------------------ music

	/** The battle theme: not loud, steady, looping; fades in, and out when asked. */
	private static final class Music extends AbstractTickableSoundInstance {
		private static final float LEVEL = 0.42f;
		private float step = LEVEL / 40f;
		private boolean out;
		int age;

		Music() {
			super(ModSounds.THANOS_MUSIC, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
			this.looping = true;
			this.delay = 0;
			this.volume = 0.001f;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
		}

		void fadeOut(int t) {
			if (out) return;
			out = true;
			step = Math.max(0.001f, this.volume / t);
		}

		boolean fading() { return out; }

		void halt() { this.stop(); }

		@Override
		public void tick() {
			age++;
			if (out) {
				this.volume -= step;
				if (this.volume <= 0.001f) this.stop();
			} else if (this.volume < LEVEL) {
				this.volume = Math.min(LEVEL, this.volume + step);
			}
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}
	}

	private ThanosClient() {}
}
