package com.dantemorrigan.gauntlet.client.space;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.client.fx.ClientPower;
import com.dantemorrigan.gauntlet.client.render.BlockMeshes;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.space.SpaceManager;
import com.dantemorrigan.gauntlet.space.SpaceRecord;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import com.dantemorrigan.gauntlet.client.render.DebrisCollision;
import com.dantemorrigan.gauntlet.client.cutscene.Cutscene;

import java.util.ArrayList;
import java.util.List;

/**
 * Dead Space scene on the client: cube-map sky, a large irregularly fractured planet with secondary explosions, slowly tumbling debris (real captured blocks + prepared blueprints) and inertial
 * zero-g movement. Geometry is cached per scene; nothing here is an entity.
 */
public final class SpaceScene {
	/** Planet placement relative to the scene center, and world blocks per logical blueprint block. */
	private static final Vec3 PLANET_OFFSET = new Vec3(0, 35, -420);
	private static final float BLOCKS_PER_LOGICAL = 9.5f;
	private static final String[] FACES = {"positive_x", "negative_x", "positive_y", "negative_y", "positive_z", "negative_z"};
	private static final String[] FACE_KEYS = {"+X", "-X", "+Y", "-Y", "+Z", "-Z"};

	private static SpaceRecord record;
	private static int ageAtReceive;
	private static int ticksSince;
	private static Vec3 velocity = Vec3.ZERO;

	private static List<Debris> debris;
	private static ShatteredPlanet planet;
	private static int particleBudget;

	/** Debris piece. kickOffset/kickVel: what collisions (player, other pieces) added on top of the scripted drift. */
	private static final class Debris {
		final BlockMeshes.Mesh mesh;
		final Vec3 base, drift;
		final Vector3f axis;
		final float spin, phase, scale;
		Vec3 kickOffset = Vec3.ZERO, kickVel = Vec3.ZERO;

		Debris(BlockMeshes.Mesh mesh, Vec3 base, Vec3 drift, Vector3f axis, float spin, float phase, float scale) {
			this.mesh = mesh;
			this.base = base;
			this.drift = drift;
			this.axis = axis;
			this.spin = spin;
			this.phase = phase;
			this.scale = scale;
		}

		/** Mass grows with volume: big rocks barely move, small ones fly. */
		float mass() {
			return Math.max(1f, scale * scale * scale * Math.min(6f, 0.15f * mesh.blocks().size()));
		}
	}

	private static int bumpCooldown;

	/** Test hook: position of the debris piece nearest to {@code from} (null if none). */
	public static Vec3 nearestDebris(Vec3 from) {
		if (debris == null) return null;
		Vec3 best = null;
		for (Debris d : debris) {
			Vec3 p = debrisPos(d, age(0));
			if (best == null || p.distanceToSqr(from) < best.distanceToSqr(from)) best = p;
		}
		return best;
	}

	/** Test hook: current scripted velocity of the player. */
	public static Vec3 playerVelocity() {
		return velocity;
	}

	/** Test hook: launch the player with a velocity. */
	public static void setPlayerVelocity(Vec3 v) {
		velocity = v;
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.SpaceState.TYPE, (p, ctx) -> enter(p.record(), p.eventAge()));
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.SpaceLeave.TYPE, (p, ctx) -> leave());
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(SpaceScene::leave));
		ClientTickEvents.END_CLIENT_TICK.register(SpaceScene::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(SpaceScene::render);
	}

	public static boolean active() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null && mc.level.dimension().equals(SpaceManager.DEAD_SPACE);
	}

	public static void enter(SpaceRecord r, int eventAge) {
		com.dantemorrigan.gauntlet.client.celestial.CelestialClient.planetDestroyed();
		record = r;
		ageAtReceive = eventAge;
		ticksSince = 0;
		velocity = Vec3.ZERO;
		debris = null; // rebuilt lazily (needs the new level's registries)
		planet = null;
		PackData.invalidate();
	}

	public static void leave() {
		record = null;
		debris = null;
		planet = null;
		velocity = Vec3.ZERO;
	}

	private static int rewindTick = -1, rewindDuration;
	private static float rewindFrom;

	/** Time stone: play the destruction backwards — the planet reassembles. */
	public static void startRewind(int duration) {
		rewindFrom = ageAtReceive + ticksSince;
		rewindTick = 0;
		rewindDuration = Math.max(1, duration);
	}

	public static void stopRewind() {
		rewindTick = -1;
	}

	private static float age(float partial) {
		if (rewindTick >= 0) {
			float t = Mth.clamp((rewindTick + partial) / rewindDuration, 0, 1);
			float eased = t * t * (3 - 2 * t);
			return Mth.lerp(eased, rewindFrom, 70f);
		}
		return ageAtReceive + ticksSince + partial;
	}

	// ---------------------------------------------------------------- tick / movement

	private static void tick(Minecraft mc) {
		if (!active() || mc.player == null) return;
		if (rewindTick >= 0) rewindTick++;
		else ticksSince++;
		move(mc.player);
		collide(mc.player);
		// A few dissolving motes near the debris, with a hard budget.
		particleBudget = Math.max(0, particleBudget - 1);
		if (debris != null && !debris.isEmpty() && mc.level.getGameTime() % 6 == 0 && particleBudget < GauntletConfig.get().maxSpaceParticles) {
			RandomSource rnd = mc.level.getRandom();
			Debris d = debris.get(rnd.nextInt(debris.size()));
			Vec3 p = debrisPos(d, age(0)).add(rnd.nextGaussian() * 1.5, rnd.nextGaussian() * 1.5, rnd.nextGaussian() * 1.5);
			ClientPower.spawn(ModParticles.VOID_DISSOLVE, p, 0.6 + rnd.nextDouble() * 0.6, 0, 0);
			particleBudget += 12;
		}
	}

	/**
	 * Pieces are solid: the player bounces off them, is knocked back by moving ones, and shoves light pieces away.
	 * Runs after the movement step so the correction is never fought by input.
	 */
	private static void collide(LocalPlayer player) {
		if (debris == null || rewindTick >= 0) return;
		if (bumpCooldown > 0) bumpCooldown--;
		float age = age(0);
		for (Debris d : debris) {
			d.kickOffset = d.kickOffset.add(d.kickVel);
			d.kickVel = d.kickVel.scale(0.985);
			if (d.kickVel.lengthSqr() < 1e-7) d.kickVel = Vec3.ZERO;
		}
		for (Debris d : debris) {
			Vec3 p = debrisPos(d, age);
			Quaternionf rot = new Quaternionf().rotationAxis((d.phase + age * d.spin) * Mth.DEG_TO_RAD, d.axis);
			var hit = DebrisCollision.test(d.mesh, p, rot, d.scale, player.position());
			if (hit == null) continue;
			Vec3 n = hit.normal();
			double depth = Math.min(hit.depth(), 1.5);
			// Un-penetrate, then exchange momentum along the contact normal (player mass 1).
			player.setPos(player.getX() + n.x * depth, player.getY() + n.y * depth, player.getZ() + n.z * depth);
			Vec3 pieceVel = d.drift.add(d.kickVel);
			double closing = velocity.subtract(pieceVel).dot(n); // < 0: moving into the piece
			float m = d.mass();
			double impulse = closing < 0 ? -(1 + 0.55) * closing : 0;
			double playerShare = m / (m + 1.0);
			velocity = velocity.add(n.scale(impulse * playerShare + 0.12));      // knocked away
			d.kickVel = d.kickVel.subtract(n.scale(impulse * (1 - playerShare) + 0.01)); // light pieces get pushed
			double speed = Math.max(impulse, 0.15);
			if (bumpCooldown == 0) {
				bumpCooldown = 6;
				Cutscene.shake((float) Math.min(0.5, 0.12 + speed * 0.8), 6);
				player.playSound(net.minecraft.sounds.SoundEvents.STONE_HIT, (float) Math.min(1.0, 0.4 + speed), 0.6f);
			}
		}
		GauntletConfig cfg = GauntletConfig.get();
		if (velocity.length() > cfg.spaceMaxSpeed * 1.8) velocity = velocity.normalize().scale(cfg.spaceMaxSpeed * 1.8);
		player.setDeltaMovement(velocity);
	}

	/** Zero-g: thrust along the view with WASD, Space/Shift for up/down, slow damping (inertia). */
	private static void move(LocalPlayer player) {
		GauntletConfig cfg = GauntletConfig.get();
		var input = player.input;
		Vec3 look = player.getViewVector(1f);
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
		var mv = input.getMoveVector();
		Vec3 thrust = look.scale(mv.y).add(left.scale(mv.x));
		if (input.keyPresses.jump()) thrust = thrust.add(0, 1, 0);
		if (input.keyPresses.shift()) thrust = thrust.add(0, -1, 0);
		if (thrust.lengthSqr() > 1) thrust = thrust.normalize();
		if (com.dantemorrigan.gauntlet.client.cutscene.Cutscene.inputLocked()) thrust = Vec3.ZERO;
		velocity = velocity.scale(cfg.spaceDamping).add(thrust.scale(cfg.spaceThrust));
		// Soft boundary: drift back toward the scene when far away.
		Vec3 toCenter = SpaceManager.CENTER.subtract(player.position());
		if (toCenter.length() > 120) velocity = velocity.add(toCenter.normalize().scale(0.01));
		if (velocity.length() > cfg.spaceMaxSpeed) velocity = velocity.normalize().scale(cfg.spaceMaxSpeed);
		player.setDeltaMovement(velocity);
		player.fallDistance = 0;
	}

	// ---------------------------------------------------------------- build (cached)

	private static void build(ClientLevel level) {
		debris = new ArrayList<>();
		RandomSource rnd = RandomSource.create(record != null ? record.seed() : 1234);
		var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
		Holder<Biome> fallbackBiome = biomes.getOrThrow(Biomes.PLAINS);

		List<SpaceRecord.Fragment> fragments = record != null ? record.fragments() : List.of();
		Holder<Biome> sourceBiome = fragments.isEmpty() ? fallbackBiome
				: fragments.get(0).biome().flatMap(biomes::get).<Holder<Biome>>map(h -> h).orElse(fallbackBiome);

		// Real fragments in front of the player first (they face north toward the planet on arrival).
		int index = 0;
		for (SpaceRecord.Fragment f : fragments) {
			Holder<Biome> biome = f.biome().flatMap(biomes::get).<Holder<Biome>>map(h -> h).orElse(sourceBiome);
			BlockMeshes.Mesh mesh = f.blocks().isEmpty() ? blueprintMesh(f.blueprint().orElse("rock_fragment_00"), biome) : fragmentMesh(f, biome);
			// Below and to the sides of the line of sight to the planet, so they frame it instead of hiding it.
			double a = -Math.PI / 2 + (index - 1) * 0.9;
			Vec3 base = SpaceManager.CENTER.add(Math.cos(a) * 8, -2.5 - index * 0.4, Math.sin(a) * 8);
			debris.add(new Debris(mesh, base, randomDrift(rnd, 0.004), randomAxis(rnd), 0.25f + rnd.nextFloat() * 0.4f, rnd.nextFloat() * 360, 1f));
			index++;
		}
		// Prepared pieces from the pack around the player.
		String[] prepared = {"turf_fragment_00", "rock_fragment_00", "turf_fragment_01", "rock_fragment_01", "ruined_house_wall",
				"turf_fragment_02", "rock_fragment_02", "uprooted_trunk", "turf_fragment_03", "ruined_house_floor",
				"rock_fragment_00", "turf_fragment_01", "rock_fragment_02", "turf_fragment_00", "rock_fragment_01"};
		java.util.Map<String, BlockMeshes.Mesh> meshes = new java.util.HashMap<>();
		for (int piece = 0; piece < prepared.length * 4; piece++) {
			String id = prepared[piece % prepared.length];
			int shell = piece / prepared.length;
			double theta = rnd.nextDouble() * Math.PI * 2;
			double phi = Math.acos(2 * rnd.nextDouble() - 1);
			double r = 20 + shell * 65 + rnd.nextDouble() * 45;
			Vec3 dir = new Vec3(Math.sin(phi) * Math.cos(theta), Math.cos(phi) * 0.6, Math.sin(phi) * Math.sin(theta));
			debris.add(new Debris(meshes.computeIfAbsent(id, key -> blueprintMesh(key, sourceBiome)), SpaceManager.CENTER.add(dir.scale(r)), randomDrift(rnd, 0.008),
					randomAxis(rnd), 0.1f + rnd.nextFloat() * 0.5f, rnd.nextFloat() * 360, (0.65f + rnd.nextFloat() * 1.2f) * (1 + shell * .5f)));
		}

		planet = new ShatteredPlanet(record != null ? record.seed() : 1234);
	}

	private static BlockMeshes.Mesh fragmentMesh(SpaceRecord.Fragment f, Holder<Biome> biome) {
		List<BlockState> states = new ArrayList<>();
		List<BlockPos> pos = new ArrayList<>();
		for (SpaceRecord.BlockEntry e : f.blocks()) {
			states.add(e.state());
			pos.add(e.pos());
		}
		return BlockMeshes.mesh(states, pos, BlockMeshes.biomeTint(biome), BlockPos.ZERO, 0.5f, -0.5f, 0.5f);
	}

	private static BlockMeshes.Mesh blueprintMesh(String id, Holder<Biome> biome) {
		PackData.Blueprint bp = PackData.blueprint(id);
		return BlockMeshes.mesh(bp.states(), bp.positions(), BlockMeshes.biomeTint(biome), BlockPos.ZERO, bp.cx(), bp.cy(), bp.cz());
	}

	private static Vec3 randomDrift(RandomSource rnd, double speed) {
		return new Vec3(rnd.nextGaussian(), rnd.nextGaussian() * 0.5, rnd.nextGaussian()).normalize().scale(speed * (0.5 + rnd.nextDouble()));
	}

	private static Vector3f randomAxis(RandomSource rnd) {
		Vector3f v = new Vector3f(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f);
		return v.lengthSquared() < 1e-4f ? new Vector3f(0, 1, 0) : v.normalize();
	}

	private static Vec3 debrisPos(Debris d, float age) {
		float t = Math.min(age, 20000);
		return d.base.add(d.drift.scale(t)).add(0, Math.sin((age + d.phase) * 0.01) * 0.6, 0).add(d.kickOffset);
	}

	// ---------------------------------------------------------------- render

	private static void render(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (!active() || mc.level == null) return;
		if (debris == null || planet == null) build(mc.level);
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 cam = mc.gameRenderer.getMainCamera().position();
		PoseStack pose = ctx.poseStack();
		SubmitNodeCollector collector = ctx.submitNodeCollector();
		float age = age(partial);
		float depthFar = mc.options.getEffectiveRenderDistance() * 16 * 4f;

		// Planet: scale the whole layout down if the view distance is short so it stays inside the far plane.
		float fit = (float) Math.min(1.0, depthFar * 0.82 / (PLANET_OFFSET.length() + 350));
		Vec3 planetCenter = SpaceManager.CENTER.add(PLANET_OFFSET.scale(fit));
		renderPlanet(pose, collector, cam, planetCenter, age, BLOCKS_PER_LOGICAL * fit);
		planet.renderAmbient(pose, collector, cam, SpaceManager.CENTER, age, depthFar * .9f);

		for (Debris d : debris) {
			Vec3 p = debrisPos(d, age);
			if (p.distanceToSqr(cam) > Math.pow(Math.min(420, depthFar * .9), 2)) continue;
			pose.pushPose();
			pose.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
			pose.mulPose(Axis.of(d.axis).rotationDegrees(d.phase + age * d.spin));
			pose.scale(d.scale, d.scale, d.scale);
			d.mesh.submit(pose, collector, LightCoordsUtil.FULL_BRIGHT);
			pose.popPose();
		}
	}

	/**
	 * Six 512px faces drawn INSIDE the vanilla sky pass (dimension skybox "end", see SkyRendererMixin): behind every
	 * world object by construction. Previously a see-through world submit, which could paint black space over debris.
	 */
	public static void drawSkyPass() {
		JsonObject dirs = PackData.sky().has("pixel_directions") ? PackData.sky().getAsJsonObject("pixel_directions") : null;
		float inset = 0.5f / 512f; // clamp-to-edge equivalent: never sample across the face border
		float r = 100f;
		PoseStack pose = new PoseStack();
		for (int f = 0; f < 6; f++) {
			JsonArray spec = dirs != null && dirs.has(FACE_KEYS[f]) ? dirs.getAsJsonArray(FACE_KEYS[f]) : defaultFace(f);
			Identifier tex = InfinityGauntlet.id("textures/environment/dead_space/faces/" + FACES[f] + ".png");
			Vector3f[] c = {dir(spec, -1, -1), dir(spec, 1, -1), dir(spec, 1, 1), dir(spec, -1, 1)};
			float[][] uv = {{inset, inset}, {1 - inset, inset}, {1 - inset, 1 - inset}, {inset, 1 - inset}};
			com.dantemorrigan.gauntlet.client.celestial.CelestialSky.draw(pose, tex, buf -> {
				for (int i = 0; i < 4; i++) buf.addVertex(c[i].x * r, c[i].y * r, c[i].z * r).setUv(uv[i][0], uv[i][1]).setColor(0xFFFFFFFF);
				for (int i = 3; i >= 0; i--) buf.addVertex(c[i].x * r, c[i].y * r, c[i].z * r).setUv(uv[i][0], uv[i][1]).setColor(0xFFFFFFFF);
			});
		}
	}


	/** Direction for image coords u (right) and v (down) in -1..1, from strings like "1", "-v", "u". */
	private static Vector3f dir(JsonArray spec, float u, float v) {
		float[] c = new float[3];
		for (int i = 0; i < 3; i++) {
			String s = spec.get(i).getAsString();
			float sign = s.startsWith("-") ? -1 : 1;
			String k = s.replace("-", "").replace("+", "");
			c[i] = k.equals("u") ? sign * u : k.equals("v") ? sign * v : Float.parseFloat(s);
		}
		return new Vector3f(c[0], c[1], c[2]);
	}

	private static JsonArray defaultFace(int f) {
		String[][] def = {{"1", "-v", "-u"}, {"-1", "-v", "u"}, {"u", "1", "v"}, {"u", "-1", "-v"}, {"u", "-v", "1"}, {"-u", "-v", "-1"}};
		JsonArray a = new JsonArray();
		for (String s : def[f]) a.add(s);
		return a;
	}

	private static void renderPlanet(PoseStack pose, SubmitNodeCollector collector, Vec3 cam, Vec3 center, float age, float scale) {
		if (planet != null) planet.render(pose, collector, cam, center, age, scale);
	}

	private SpaceScene() {}
}
