package com.dantemorrigan.gauntlet.client.cutscene;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.client.fx.ClientPower;
import com.dantemorrigan.gauntlet.client.render.BlockMeshes;
import com.dantemorrigan.gauntlet.client.space.PackData;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client playback of the Planet Destruction cutscene, driven by cinematic_timeline.json and the server's start
 * packet. Everything here is visual: camera shake, white flash, input lock, cracks, lifted debris, rift.
 * {@link #stop()} resets all of it and is called on every way the scene can end.
 */
public final class Cutscene {
	// ---- Active scene ----
	private static boolean active;
	private static int eventId;
	private static Vec3 origin = Vec3.ZERO;
	private static int age;
	private static int transferTick = 68;
	private static ClientLevel sceneLevel;
	private static boolean transferred;

	// ---- Effects, also usable standalone (debug / strike) ----
	private static float shakeAmp;
	private static int shakeTicks;
	private static int shakeDuration;
	private static float flash;
	private static float flashPrev;
	private static int flashDemoTicks = -1;

	/** Lifted pieces of the environment (client meshes, not entities). */
	private static final List<Lifted> LIFTED = new ArrayList<>();
	/** Blocks hidden client-side while their copies fly away; restored if the scene is aborted. */
	private static final Map<BlockPos, BlockState> HIDDEN = new HashMap<>();
	private static ClientLevel hiddenLevel;

	private static final class Lifted {
		final BlockMeshes.Mesh mesh;
		Vec3 pos, prevPos, vel;
		final Vector3f axis;
		float angle, prevAngle;
		final float spin, scale;

		Lifted(BlockMeshes.Mesh mesh, Vec3 pos, Vec3 vel, Vector3f axis, float spin, float scale) {
			this.mesh = mesh;
			this.pos = this.prevPos = pos;
			this.vel = vel;
			this.axis = axis;
			this.spin = spin;
			this.scale = scale;
		}
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(Cutscene::tick);
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.CutsceneStart.TYPE, (p, ctx) -> start(p.eventId(), p.origin(), p.elapsed(), p.transferTick()));
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.CutsceneStop.TYPE, (p, ctx) -> {
			if (active && p.eventId() == eventId) stop();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(Cutscene::stop));
		HudElementRegistry.addLast(InfinityGauntlet.id("white_flash"), (graphics, delta) -> {
			float a = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), flashPrev, flash) * GauntletConfig.get().flashIntensity;
			if (a > 0.003f) graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), ARGB.colorFromFloat(Math.min(1f, a), 1f, 0.97f, 1f));
		});
		// Keep the white flash over any screen too (the dimension loading screen appears under the flash).
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> ScreenEvents.afterExtract(screen).register((s, graphics, mx, my, delta) -> {
			float a = flash * GauntletConfig.get().flashIntensity;
			if (a > 0.003f) graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), ARGB.colorFromFloat(Math.min(1f, a), 1f, 0.97f, 1f));
		}));
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			if (LIFTED.isEmpty()) return;
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null || mc.level != sceneLevel && mc.level != hiddenLevel) return;
			float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
			Vec3 cam = mc.gameRenderer.getMainCamera().position();
			PoseStack pose = context.poseStack();
			for (Lifted l : LIFTED) {
				Vec3 p = l.prevPos.lerp(l.pos, partial);
				pose.pushPose();
				pose.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
				pose.mulPose(Axis.of(l.axis).rotationDegrees(Mth.lerp(partial, l.prevAngle, l.angle)));
				pose.scale(l.scale, l.scale, l.scale);
				l.mesh.submit(pose, context.submitNodeCollector(), LightCoordsUtil.FULL_BRIGHT);
				pose.popPose();
			}
		});
	}

	// ---- Public queries for mixins / other systems ----

	public static boolean inputLocked() {
		return active && age < PackData.timelineTick("fade_from_white", 75);
	}

	public static boolean isActive() {
		return active;
	}

	public static int age() {
		return age;
	}

	/** Camera shake offsets in degrees (yaw, pitch) for this frame. */
	public static float shakeYaw(float partial) {
		return shakeValue(partial, 1.0f, 0.37f);
	}

	public static float shakePitch(float partial) {
		return shakeValue(partial, 0.8f, 1.91f);
	}

	private static float shakeValue(float partial, float scale, float phase) {
		float amp = currentShake();
		if (amp <= 0.001f) return 0;
		GauntletConfig cfg = GauntletConfig.get();
		float t = (Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0) + partial;
		float noise = Mth.sin(t * 2.9f + phase) * 0.6f + Mth.sin(t * 7.3f + phase * 2f) * 0.4f;
		return noise * amp * cfg.cameraShakeMaxDegrees * cfg.cameraShakeScale * scale;
	}

	private static float currentShake() {
		float impulse = shakeTicks > 0 ? shakeAmp * shakeTicks / (float) Math.max(1, shakeDuration) : 0;
		float rumble = 0;
		if (active && !transferred) {
			int rift = PackData.timelineTick("planet_rift", 55);
			int flashTick = PackData.timelineTick("flash_to_white", 65);
			if (age >= 4 && age < rift) rumble = 0.08f + 0.25f * (age - 4) / (float) Math.max(1, rift - 4);
			else if (age >= rift && age < transferTick) rumble = 0.35f + 0.5f * Math.min(1f, (age - rift) / (float) Math.max(1, flashTick - rift));
		}
		return Math.max(impulse, rumble);
	}

	/** Short camera shake impulse (0..1 strength). */
	public static void shake(float strength, int ticks) {
		if (strength >= currentShake()) {
			shakeAmp = Math.min(1f, strength);
			shakeTicks = shakeDuration = Math.max(1, ticks);
		}
	}

	public static void flashDemo() {
		flashDemoTicks = 0;
	}

	public static void liftDemo(Vec3 at) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) liftDebris(mc.level, at, 8, false);
	}

	// ---- Lifecycle ----

	public static void start(int id, Vec3 at, int elapsed, int transfer) {
		com.dantemorrigan.gauntlet.client.celestial.CelestialClient.planetDestroyed();
		stop();
		Minecraft mc = Minecraft.getInstance();
		active = true;
		eventId = id;
		origin = at;
		age = elapsed;
		transferTick = transfer;
		sceneLevel = mc.level;
		transferred = false;
		InfinityGauntlet.LOGGER.info("Cutscene #{} start at {}", id, at);
		if (elapsed == 0) runEvents(0);
	}

	/** Ends the scene and clears every effect it may have started. Safe to call any time. */
	public static void stop() {
		active = false;
		shakeTicks = 0;
		shakeAmp = 0;
		flash = flashPrev = 0;
		flashDemoTicks = -1;
		LIFTED.clear();
		restoreHidden();
		sceneLevel = null;
	}

	private static void restoreHidden() {
		Minecraft mc = Minecraft.getInstance();
		if (!HIDDEN.isEmpty() && hiddenLevel != null && mc.level == hiddenLevel) {
			HIDDEN.forEach((pos, state) -> hiddenLevel.setBlock(pos, state, 18));
		}
		HIDDEN.clear();
		hiddenLevel = null;
	}

	private static void tick(Minecraft mc) {
		flashPrev = flash;
		if (shakeTicks > 0) shakeTicks--;
		for (Lifted l : LIFTED) {
			l.prevPos = l.pos;
			l.prevAngle = l.angle;
			l.pos = l.pos.add(l.vel);
			l.vel = l.vel.scale(1.02);
			l.angle += l.spin;
		}
		if (mc.player != null && !LIFTED.isEmpty() && mc.level != null && (mc.level == sceneLevel || mc.level == hiddenLevel)) {
			// Rising pieces are solid: they shove the player (and are slowed a little by the contact).
			for (Lifted l : LIFTED) {
				var hit = com.dantemorrigan.gauntlet.client.render.DebrisCollision.test(l.mesh, l.pos,
						new org.joml.Quaternionf().rotationAxis(l.angle * Mth.DEG_TO_RAD, l.axis), l.scale, mc.player.position());
				if (hit == null) continue;
				Vec3 n = hit.normal();
				double depth = Math.min(hit.depth(), 1.5);
				mc.player.setPos(mc.player.getX() + n.x * depth, mc.player.getY() + n.y * depth, mc.player.getZ() + n.z * depth);
				Vec3 push = n.scale(0.35).add(l.vel.scale(0.8)).add(0, 0.15, 0);
				mc.player.setDeltaMovement(mc.player.getDeltaMovement().add(push));
				l.vel = l.vel.scale(0.85);
				shake(0.3f, 6);
			}
		}
		if (flashDemoTicks >= 0) {
			flashDemoTicks++;
			flash = flashDemoTicks < 4 ? flashDemoTicks / 4f : Math.max(0, 1f - (flashDemoTicks - 8) / 12f);
			if (flashDemoTicks > 22) {
				flashDemoTicks = -1;
				flash = 0;
			}
		}
		if (!active) {
			if (!LIFTED.isEmpty() && flashDemoTicks < 0 && LIFTED.get(0).pos.y > (mc.player != null ? mc.player.getY() + 60 : 400)) LIFTED.clear();
			return;
		}
		if (mc.player == null || mc.level == null) {
			stop();
			return;
		}
		// Death or an unexpected dimension change before the transfer aborts the scene locally too.
		if (!mc.player.isAlive() || mc.level != sceneLevel && !transferred) {
			if (age >= transferTick - 2 && mc.level != sceneLevel) {
				transferred = true; // this is the server transfer
				LIFTED.clear();
				HIDDEN.clear();
				hiddenLevel = null;
			} else {
				stop();
				return;
			}
		}
		age++;
		runEvents(age);
		updateFlash();
		int end = PackData.timelineTick("idle_space", 180);
		if (age >= end) stop();
	}

	private static void updateFlash() {
		int in = PackData.timelineTick("flash_to_white", 65);
		int out = PackData.timelineTick("fade_from_white", 75);
		int reveal = PackData.timelineTick("reveal_planet", 80);
		if (age < in) flash = 0;
		else if (age < in + 2) flash = (age - in + 1) / 2f;
		else if (age < out) flash = 1;
		else if (age < reveal + 6) flash = Math.max(0, 1f - (age - out) / (float) (reveal + 6 - out));
		else flash = 0;
	}

	private static void runEvents(int tick) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) return;
		for (PackData.TimelineEvent e : PackData.timeline()) {
			if (e.tick() != tick) continue;
			switch (e.event()) {
				case "impact" -> {
					ClientPower.strikeFx(origin, new Vec3(0, 1, 0), 2.5f);
					float s = e.data().has("camera_shake") ? e.data().get("camera_shake").getAsFloat() : 0.25f;
					shake(Math.min(1f, s * 3f), 12);
				}
				case "ground_cracks" -> {
					float radius = e.data().has("radius_blocks") ? e.data().get("radius_blocks").getAsFloat() : 3f;
					cracks(level, radius);
				}
				case "lift_debris" -> liftDebris(level, origin, e.data().has("count") ? e.data().get("count").getAsInt() : 24, true);
				case "planet_rift" -> {
					for (int i = 0; i < 3; i++) ClientPower.spawn(ModParticles.PLANET_RIFT, origin.add(0, 1.5 + i * 1.6, 0), 1.0 + i * 0.6, 0, 0);
					shake(0.7f, 14);
				}
				case "flash_to_white" -> ClientPower.spawn(ModParticles.DETONATION_FLASH, origin.add(0, 3, 0), 1.5, 0, 0);
				default -> {}
			}
		}
	}

	/** Purple fractures spreading over the terrain: flat quads slightly above the surface around the origin. */
	private static void cracks(ClientLevel level, float radius) {
		RandomSource rnd = RandomSource.create(eventId * 31L + (long) radius);
		float fullWidth = 12f; // ground_fracture preset width
		ClientPower.spawn(ModParticles.GROUND_FRACTURE, surface(level, origin.x, origin.z), 0, radius * 2f / fullWidth, 0);
		int ring = radius > 6 ? 8 : 0;
		for (int i = 0; i < ring; i++) {
			double a = i * Math.PI * 2 / ring + rnd.nextDouble() * 0.4;
			double d = radius * (0.55 + rnd.nextDouble() * 0.3);
			Vec3 p = surface(level, origin.x + Math.cos(a) * d, origin.z + Math.sin(a) * d);
			ClientPower.spawn(ModParticles.GROUND_FRACTURE, p, 0, 0.45 + rnd.nextDouble() * 0.3, 0);
		}
	}

	private static Vec3 surface(ClientLevel level, double x, double z) {
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, origin.y, z));
		double y = Math.abs(top.getY() - origin.y) < 6 ? top.getY() : origin.y;
		return new Vec3(x, y + 0.04, z);
	}

	/**
	 * Tear small clusters of real surface blocks off the ground (client copies; originals hidden client-side
	 * and restored on abort) plus a few prepared blueprint pieces further out.
	 */
	private static void liftDebris(ClientLevel level, Vec3 center, int count, boolean hideOriginals) {
		RandomSource rnd = RandomSource.create(eventId * 977L + 13);
		count = Math.min(count, 32);
		if (hideOriginals) hiddenLevel = level;
		BlockPos tintPos = BlockPos.containing(center);
		for (int i = 0; i < count; i++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			double d = 2.5 + rnd.nextDouble() * 9.5;
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					BlockPos.containing(center.x + Math.cos(a) * d, center.y, center.z + Math.sin(a) * d)).below();
			if (Math.abs(top.getY() - center.y) > 8) continue;
			List<BlockState> states = new ArrayList<>();
			List<BlockPos> rel = new ArrayList<>();
			int depth = 1 + rnd.nextInt(2);
			for (int dy = 0; dy < depth; dy++) {
				BlockPos p = top.below(dy);
				BlockState s = level.getBlockState(p);
				if (s.isAir() || s.hasBlockEntity() || !s.getFluidState().isEmpty()) continue;
				states.add(s);
				rel.add(new BlockPos(0, -dy, 0));
				if (hideOriginals && !HIDDEN.containsKey(p)) {
					HIDDEN.put(p.immutable(), s);
					level.setBlock(p, Blocks.AIR.defaultBlockState(), 18);
				}
			}
			if (states.isEmpty()) continue;
			BlockMeshes.Mesh mesh = BlockMeshes.mesh(states, rel, level, top, 0.5f, 0.5f - (depth - 1) * 0.5f, 0.5f);
			Vec3 start = Vec3.atCenterOf(top);
			Vec3 vel = new Vec3(Math.cos(a) * 0.02, 0.06 + rnd.nextDouble() * 0.12, Math.sin(a) * 0.02);
			LIFTED.add(new Lifted(mesh, start, vel, randomAxis(rnd), 1.5f + rnd.nextFloat() * 5f, 1f));
		}
		// Prepared pieces (blueprints from the pack) rising further away.
		String[] prepared = {"turf_fragment_00", "rock_fragment_00", "uprooted_trunk", "turf_fragment_02"};
		for (int i = 0; i < prepared.length; i++) {
			PackData.Blueprint bp = PackData.blueprint(prepared[i]);
			if (bp.states().isEmpty()) continue;
			BlockMeshes.Mesh mesh = BlockMeshes.mesh(bp.states(), bp.positions(), level, tintPos, bp.cx(), bp.cy(), bp.cz());
			double a = i * Math.PI / 2 + 0.6;
			Vec3 start = center.add(Math.cos(a) * 16, -2, Math.sin(a) * 16);
			LIFTED.add(new Lifted(mesh, start, new Vec3(0, 0.05 + 0.02 * i, 0), randomAxis(rnd), 0.6f + rnd.nextFloat(), 1f));
		}
	}

	private static Vector3f randomAxis(RandomSource rnd) {
		Vector3f v = new Vector3f(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f);
		return v.lengthSquared() < 1e-4f ? new Vector3f(0, 1, 0) : v.normalize();
	}

	private Cutscene() {}
}
