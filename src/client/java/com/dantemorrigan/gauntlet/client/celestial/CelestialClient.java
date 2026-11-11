package com.dantemorrigan.gauntlet.client.celestial;

import com.dantemorrigan.gauntlet.GauntletItem;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.celestial.CelestialApocalypse;
import com.dantemorrigan.gauntlet.client.cutscene.Cutscene;
import com.dantemorrigan.gauntlet.client.fx.ClientPower;
import com.dantemorrigan.gauntlet.client.fx.FistPosition;
import com.dantemorrigan.gauntlet.client.fx.VfxPresets;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.power.PowerAbility;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Client side of the Celestial Apocalypse: event clock, red-sky blend factor, meteors (physical ones announced
 * by the server + cosmetic distant ones), their trails and impacts, the violet targeting thread and the HUD timer.
 * Sky drawing itself happens in {@link CelestialSky} (inside the vanilla sky pass).
 */
public final class CelestialClient {
	// ---- synced state ----
	static int phase;
	static String body = "";
	static Vec3 center = Vec3.ZERO;
	static int eventTicks;
	static int localTicks;
	static int rainRemainingMs;
	static long rainSyncNanos;
	static boolean sunDestroyed, moonDestroyed;
	static int endingTicks = -1;
	/** Fade-out after the event is gone (ticks left of red sky). */
	static int afterglow;

	private record ClientMeteor(int id, Vec3 from, Vec3 to, int start, int flight, int size, boolean solar, boolean cosmetic, float spin) {
		Vec3 pos(float age) {
			return from.lerp(to, Mth.clamp(age / flight, 0, 1));
		}
	}

	private static final List<ClientMeteor> METEORS = new ArrayList<>();
	private static final Map<String, ItemStackRenderState> MODELS = new HashMap<>();
	private static int clientTicks;
	private static float distantAccumulator;
	private static final Identifier THREAD = InfinityGauntlet.id("textures/environment/dead_space/energy_plane.png");

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.CelestialSync.TYPE, (s, ctx) -> sync(s));
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.MeteorSpawn.TYPE, (m, ctx) ->
				METEORS.add(new ClientMeteor(m.id(), m.from(), m.to(), clientTicks, m.flightTicks(), m.size(), m.solar(), false, (m.id() * 37) % 360)));
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.MeteorImpact.TYPE, (m, ctx) -> impact(m.id(), m.pos(), m.size(), Block.stateById(m.groundState())));
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(CelestialClient::reset));
		ClientTickEvents.END_CLIENT_TICK.register(CelestialClient::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(CelestialClient::render);
		HudElementRegistry.addLast(InfinityGauntlet.id("meteor_timer"), (g, d) -> {
			Minecraft mc = Minecraft.getInstance();
			int ms = remainingMs();
			com.dantemorrigan.gauntlet.client.StatusHud.persistent("meteor", phase == 2 && mc.level != null && mc.level.dimension().equals(Level.OVERWORLD)
					? Component.translatable("msg.infinity_gauntlet.celestial.meteor_rain", ms / 60000 + ":" + String.format("%02d", (ms / 1000) % 60)) : null);
		});
	}

	private static void reset() {
		phase = 0;
		METEORS.clear();
		MODELS.clear();
		afterglow = 0;
		sunDestroyed = moonDestroyed = false;
		CelestialSky.reset();
	}

	/** Destruction ends weather instantly, without the normal red-sky afterglow. Keep body destruction flags. */
	public static void planetDestroyed() {
		phase = 0;
		afterglow = 0;
		METEORS.clear();
		distantAccumulator = 0;
		rainRemainingMs = 0;
		endingTicks = -1;
		com.dantemorrigan.gauntlet.client.StatusHud.persistent("meteor", null);
	}

	private static void sync(ModNetwork.CelestialSync s) {
		if (s.phase() == 0) METEORS.clear();
		if (phase != 0 && s.phase() == 0) afterglow = 100; // fade the red sky out over 5 s
		if (s.phase() == 3 && phase != 3) endingTicks = 0;
		if (s.phase() != 0 && (phase == 0 || !s.body().equals(body))) CelestialSky.reset();
		phase = s.phase();
		body = s.body();
		center = s.center();
		eventTicks = s.eventTicks();
		localTicks = 0;
		rainRemainingMs = s.rainRemainingMs();
		rainSyncNanos = System.nanoTime();
		sunDestroyed = s.sunDestroyed();
		moonDestroyed = s.moonDestroyed();
	}

	/** Event clock in ticks (from aim start), smooth between syncs. */
	public static float eventTime(float partial) {
		return eventTicks + localTicks + partial;
	}

	public static int phase() {
		return phase;
	}

	public static String body() {
		return body;
	}

	public static boolean destroyed(String b) {
		return b.equals("sun") ? sunDestroyed : moonDestroyed;
	}

	static int remainingMs() {
		return (int) Math.max(0, rainRemainingMs - (System.nanoTime() - rainSyncNanos) / 1_000_000L);
	}

	/** 0..1 strength of the red sky for a viewer at {@code pos}: the Celestial Apocalypse or a Meteor Stream, whichever is stronger. */
	public static float redFactor(Vec3 pos, float partial) {
		float stream = com.dantemorrigan.gauntlet.client.fx.MeteorStreamClient.redFactor(pos);
		Minecraft mc = Minecraft.getInstance();
		if (stream > 0 && (mc.level == null || !mc.level.dimension().equals(Level.OVERWORLD))) stream = 0;
		return Math.max(eventRed(pos, partial), stream);
	}

	private static float eventRed(Vec3 pos, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || !mc.level.dimension().equals(Level.OVERWORLD)) return 0;
		float time;
		if (phase == 0) time = afterglow > 0 ? afterglow / 100f : 0;
		else if (phase == 1) time = Mth.clamp((eventTime(partial) - CelestialApocalypse.T_PULL) / 40f, 0, 1);
		else if (phase == 2) time = 1;
		else time = Math.max(0, 1 - Math.max(0, endingTicks) / 100f);
		if (time <= 0) return 0;
		double d = Math.sqrt(Math.pow(pos.x - center.x, 2) + Math.pow(pos.z - center.z, 2));
		float radial = (float) Mth.clamp(1 - (d - com.dantemorrigan.gauntlet.ServerRules.redSkyRadius) / Math.max(1, com.dantemorrigan.gauntlet.ServerRules.redSkyFalloff), 0, 1);
		return time * radial;
	}

	// ------------------------------------------------------------------ tick

	private static void tick(Minecraft mc) {
		clientTicks++;
		if (mc.level == null || mc.player == null) return;
		if (!mc.level.dimension().equals(Level.OVERWORLD)) {
			METEORS.clear();
			afterglow = 0;
			return;
		}
		localTicks++;
		if (endingTicks >= 0) endingTicks++;
		if (afterglow > 0) afterglow--;
		RandomSource rnd = mc.level.getRandom();

		// Fragment/strike shakes at the timeline beats.
		if (phase == 1) {
			int t = eventTicks + localTicks;
			if (t == CelestialApocalypse.T_FRACTURE || t == CelestialApocalypse.T_FRACTURE + 30 || t == CelestialApocalypse.T_FRACTURE + 60
					|| t == CelestialApocalypse.T_FRACTURE + 90) Cutscene.shake(0.35f + (t - 60) / 300f, 14);
			if (t == CelestialApocalypse.T_DETACH) Cutscene.shake(0.9f, 30);
		}

		// Cosmetic distant meteors while it rains (no impact, sky decoration only).
		if (phase == 2 && redFactor(mc.player.position(), 0) > 0.3f) {
			distantAccumulator += 8f / 20f;
			while (distantAccumulator >= 1 && METEORS.size() < 60) {
				distantAccumulator--;
				double a = rnd.nextDouble() * Math.PI * 2;
				double r = 140 + rnd.nextDouble() * 160;
				Vec3 end = mc.player.position().add(Math.cos(a) * r, 20 + rnd.nextDouble() * 40, Math.sin(a) * r);
				Vec3 from = end.add(rnd.nextGaussian() * 40, 120 + rnd.nextDouble() * 60, rnd.nextGaussian() * 40);
				METEORS.add(new ClientMeteor(-1, from, end, clientTicks, 30 + rnd.nextInt(30), 0, body.equals("sun"), true, rnd.nextFloat() * 360));
			}
		}

		for (Iterator<ClientMeteor> it = METEORS.iterator(); it.hasNext(); ) {
			ClientMeteor m = it.next();
			float age = clientTicks - m.start();
			if (age > m.flight() + (m.cosmetic() ? 0 : 10)) {
				it.remove();
				continue;
			}
			Vec3 p = m.pos(age), prev = m.pos(age - 1);
			Vec3 v = p.subtract(prev);
			int trails = m.cosmetic() ? 1 : 2 + m.size();
			for (int i = 0; i < trails; i++) {
				Vec3 tp = prev.lerp(p, i / (float) trails);
				ClientPower.spawn(ModParticles.CELESTIAL_METEOR_TRAIL, tp, v.x, v.y, v.z);
			}
			if (!m.cosmetic()) {
				mc.level.addParticle(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 0, 0.02, 0);
				mc.level.addParticle(ParticleTypes.FLAME, p.x + rnd.nextGaussian() * 0.4, p.y, p.z + rnd.nextGaussian() * 0.4, 0, 0, 0);
				if (m.solar()) mc.level.addParticle(ParticleTypes.LAVA, p.x, p.y, p.z, 0, 0, 0);
				if (m.size() >= 3) { // the Power Stone's meteor burns violet
					for (int i = 0; i < 6; i++) mc.level.addParticle(new net.minecraft.core.particles.DustParticleOptions(0x9B0FD0, 3.0f),
							p.x + rnd.nextGaussian() * 1.2, p.y + rnd.nextGaussian() * 1.2, p.z + rnd.nextGaussian() * 1.2, 0, 0, 0);
					mc.level.addParticle(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 0, 0, 0);
				}
			}
		}
	}

	private static void impact(int id, Vec3 pos, int size, BlockState ground) {
		Minecraft mc = Minecraft.getInstance();
		METEORS.removeIf(m -> m.id() == id);
		if (mc.level == null || mc.player == null) return;
		float s = 1f + size * 0.5f;
		ClientPower.spawn(ModParticles.CELESTIAL_METEOR_IMPACT, pos.add(0, 1.6 * s, 0), s, 0, 0);
		ClientPower.spawn(ModParticles.POWER_SHOCKWAVE, pos.add(0, 0.08, 0), 0, 2.0 + size, 0);
		ClientPower.spawn(ModParticles.POWER_SPARKS_SMOKE, pos.add(0, 1, 0), 1.5 * s, 0, 0);
		RandomSource rnd = mc.level.getRandom();
		if (!ground.isAir()) {
			BlockParticleOption chips = new BlockParticleOption(ParticleTypes.BLOCK, ground);
			for (int i = 0; i < 24; i++) {
				mc.level.addParticle(chips, pos.x + rnd.nextGaussian(), pos.y + 0.5, pos.z + rnd.nextGaussian(),
						rnd.nextGaussian() * 0.4, 0.4 + rnd.nextDouble() * 0.6, rnd.nextGaussian() * 0.4);
			}
		}
		double dist = mc.player.position().distanceTo(pos);
		float shake = (float) Mth.clamp(1 - dist / 90.0, 0, 1) * (0.5f + size * 0.2f);
		if (shake > 0.02f) Cutscene.shake(shake, 12);
	}

	// ------------------------------------------------------------------ world render: meteors + targeting thread

	private static void render(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || !mc.level.dimension().equals(Level.OVERWORLD)) return;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 cam = mc.gameRenderer.getMainCamera().position();
		PoseStack pose = ctx.poseStack();
		for (ClientMeteor m : METEORS) {
			if (m.cosmetic()) continue;
			float age = clientTicks - m.start() + partial;
			Vec3 p = m.pos(age);
			ItemStackRenderState rs = model(m.solar(), m.size());
			float scale = (new float[]{0.6f, 1.2f, 2.4f, 3.2f})[Mth.clamp(m.size(), 0, 3)] * 2.5f;
			pose.pushPose();
			pose.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
			pose.mulPose(Axis.YP.rotationDegrees(m.spin() + age * 9));
			pose.mulPose(Axis.XP.rotationDegrees(age * 13));
			pose.scale(scale, scale, scale);
			rs.submit(pose, ctx.submitNodeCollector(), LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
			pose.popPose();
		}
		renderThread(ctx, mc, cam, partial);
	}

	private static ItemStackRenderState model(boolean solar, int size) {
		String id = size >= 3 ? "ca_power_meteor" : (solar ? "ca_solar_meteor_" : "ca_lunar_meteor_") + Mth.clamp(size, 0, 2);
		return MODELS.computeIfAbsent(id, key -> {
			Minecraft mc = Minecraft.getInstance();
			ItemStack stack = new ItemStack(Items.PAPER);
			stack.set(DataComponents.ITEM_MODEL, InfinityGauntlet.id(key));
			ItemStackRenderState rs = new ItemStackRenderState();
			mc.getItemModelResolver().updateForTopItem(rs, stack, ItemDisplayContext.NONE, mc.level, null, 0);
			return rs;
		});
	}

	/** Violet thread from the fist into the sky while aiming the Celestial mode. */
	private static void renderThread(LevelRenderContext ctx, Minecraft mc, Vec3 cam, float partial) {
		LocalPlayer player = mc.player;
		if (GauntletItem.chargeTicks(player) <= 0 || PowerAbility.mode(player.getUseItem()) != PowerAbility.MODE_CELESTIAL) return;
		Vec3 from = FistPosition.of(player, partial);
		Vec3 dir = player.getViewVector(partial);
		Vec3 to = from.add(dir.scale(Math.min(180, mc.options.getEffectiveRenderDistance() * 16)));
		Vec3 side = dir.cross(cam.subtract(from)).normalize().scale(0.12 + 0.05 * Mth.sin((GauntletItem.chargeTicks(player) + partial) * 0.8f));
		float charge = Mth.clamp(GauntletItem.chargeTicks(player) / (com.dantemorrigan.gauntlet.ServerRules.celestialAimSeconds * 20f), 0, 1);
		int color = ARGB.colorFromFloat(0.5f + 0.5f * charge, 0.85f, 0.5f, 1f);
		PoseStack pose = ctx.poseStack();
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.eyes(THREAD), (p, buffer) -> {
			Vec3[] q = {from.subtract(side), from.add(side), to.add(side.scale(6)), to.subtract(side.scale(6))};
			float[][] uv = {{0.45f, 0}, {0.55f, 0}, {0.55f, 1}, {0.45f, 1}};
			for (int pass = 0; pass < 2; pass++) {
				for (int i = 0; i < 4; i++) {
					int k = pass == 0 ? i : 3 - i;
					vertex(buffer, p, q[k], uv[k], color);
				}
			}
		});
		pose.popPose();
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose p, Vec3 v, float[] uv, int color) {
		buffer.addVertex(p, (float) v.x, (float) v.y, (float) v.z).setColor(color).setUv(uv[0], uv[1]).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p, 0, 1, 0);
	}

	/** Preset colors for the red sky (red_sky_presets.json). */
	private static JsonObject presets;

	static float[] preset(String time, String key, float[] def) {
		if (presets == null) {
			presets = VfxPresets.readJson(InfinityGauntlet.id("celestial_apocalypse/red_sky_presets.json"));
			if (presets == null) presets = new JsonObject();
		}
		JsonObject root = presets;
		if (root == null || !root.has(time) || !root.getAsJsonObject(time).has(key)) return def;
		var e = root.getAsJsonObject(time).get(key);
		if (e.isJsonArray()) {
			JsonArray a = e.getAsJsonArray();
			return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
		}
		return new float[]{e.getAsFloat()};
	}

	/** Daylight 0 (night) .. 1 (day) from the real sun angle at the camera. */
	static float daylight(float partial) {
		Minecraft mc = Minecraft.getInstance();
		float deg = mc.gameRenderer.getMainCamera().attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, partial);
		float y = Mth.cos(deg * Mth.DEG_TO_RAD);
		return Mth.clamp((y + 0.15f) / 0.45f, 0, 1);
	}

	public static float daylightPublic(float partial) {
		return daylight(partial);
	}

	public static float[] presetPublic(String time, String key, float[] def) {
		return preset(time, key, def);
	}

	private CelestialClient() {}
}
