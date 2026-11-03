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
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Dead Space scene on the client: cube-map sky, the far planet breaking into eight parts with a full-bright
 * energy layer in the seams, slowly tumbling debris (real captured blocks + prepared blueprints) and inertial
 * zero-g movement. Geometry is cached per scene; nothing here is an entity.
 */
public final class SpaceScene {
	/** Planet placement relative to the scene center, and world blocks per logical blueprint block. */
	private static final Vec3 PLANET_OFFSET = new Vec3(0, 28, -230);
	private static final float BLOCKS_PER_LOGICAL = 6f;
	private static final String[] FACES = {"positive_x", "negative_x", "positive_y", "negative_y", "positive_z", "negative_z"};
	private static final String[] FACE_KEYS = {"+X", "-X", "+Y", "-Y", "+Z", "-Z"};
	private static final Identifier ENERGY = InfinityGauntlet.id("textures/environment/dead_space/energy_plane.png");

	private static SpaceRecord record;
	private static int ageAtReceive;
	private static int ticksSince;
	private static Vec3 velocity = Vec3.ZERO;

	private static List<Debris> debris;
	private static List<ItemStackRenderState> planetParts;
	private static int particleBudget;

	private record Debris(BlockMeshes.Mesh mesh, Vec3 base, Vec3 drift, Vector3f axis, float spin, float phase, float scale) {}

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
		record = r;
		ageAtReceive = eventAge;
		ticksSince = 0;
		velocity = Vec3.ZERO;
		debris = null; // rebuilt lazily (needs the new level's registries)
		planetParts = null;
		PackData.invalidate();
	}

	public static void leave() {
		record = null;
		debris = null;
		planetParts = null;
		velocity = Vec3.ZERO;
	}

	private static float age(float partial) {
		return ageAtReceive + ticksSince + partial;
	}

	// ---------------------------------------------------------------- tick / movement

	private static void tick(Minecraft mc) {
		if (!active() || mc.player == null) return;
		ticksSince++;
		move(mc.player);
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
		for (String id : prepared) {
			double theta = rnd.nextDouble() * Math.PI * 2;
			double phi = Math.acos(2 * rnd.nextDouble() - 1);
			double r = 14 + rnd.nextDouble() * 34;
			Vec3 dir = new Vec3(Math.sin(phi) * Math.cos(theta), Math.cos(phi) * 0.6, Math.sin(phi) * Math.sin(theta));
			debris.add(new Debris(blueprintMesh(id, sourceBiome), SpaceManager.CENTER.add(dir.scale(r)), randomDrift(rnd, 0.008),
					randomAxis(rnd), 0.1f + rnd.nextFloat() * 0.5f, rnd.nextFloat() * 360, 0.8f + rnd.nextFloat() * 1.4f));
		}

		planetParts = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			ItemStack stack = new ItemStack(Items.PAPER);
			stack.set(DataComponents.ITEM_MODEL, InfinityGauntlet.id("pd_planet_chunk_0" + i));
			ItemStackRenderState rs = new ItemStackRenderState();
			Minecraft.getInstance().getItemModelResolver().updateForTopItem(rs, stack, ItemDisplayContext.NONE, level, null, 0);
			planetParts.add(rs);
		}
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
		return d.base.add(d.drift.scale(t)).add(0, Math.sin((age + d.phase) * 0.01) * 0.6, 0);
	}

	// ---------------------------------------------------------------- render

	private static void render(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (!active() || mc.level == null) return;
		if (debris == null || planetParts == null) build(mc.level);
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 cam = mc.gameRenderer.getMainCamera().position();
		PoseStack pose = ctx.poseStack();
		SubmitNodeCollector collector = ctx.submitNodeCollector();
		float age = age(partial);
		float depthFar = mc.options.getEffectiveRenderDistance() * 16 * 4f;

		renderSky(pose, collector, Math.min(depthFar * 0.8f, 900f));

		// Planet: scale the whole layout down if the view distance is short so it stays inside the far plane.
		float fit = (float) Math.min(1.0, depthFar * 0.6 / (PLANET_OFFSET.length() + 80));
		Vec3 planetCenter = SpaceManager.CENTER.add(PLANET_OFFSET.scale(fit));
		renderPlanet(pose, collector, cam, planetCenter, age, BLOCKS_PER_LOGICAL * fit);

		for (Debris d : debris) {
			Vec3 p = debrisPos(d, age);
			if (p.distanceToSqr(cam) > 160 * 160) continue;
			pose.pushPose();
			pose.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
			pose.mulPose(Axis.of(d.axis).rotationDegrees(d.phase + age * d.spin));
			pose.scale(d.scale, d.scale, d.scale);
			d.mesh.submit(pose, collector, LightCoordsUtil.FULL_BRIGHT);
			pose.popPose();
		}
	}

	/** Six 512px faces at a large radius around the camera, oriented per sky_orientation.json; drawn first. */
	private static void renderSky(PoseStack pose, SubmitNodeCollector collector, float radius) {
		JsonObject dirs = PackData.sky().has("pixel_directions") ? PackData.sky().getAsJsonObject("pixel_directions") : null;
		float inset = 0.5f / 512f; // clamp-to-edge equivalent: never sample across the face border
		for (int f = 0; f < 6; f++) {
			JsonArray spec = dirs != null && dirs.has(FACE_KEYS[f]) ? dirs.getAsJsonArray(FACE_KEYS[f]) : defaultFace(f);
			Identifier tex = InfinityGauntlet.id("textures/environment/dead_space/faces/" + FACES[f] + ".png");
			Vector3f[] corners = {dir(spec, -1, -1), dir(spec, 1, -1), dir(spec, 1, 1), dir(spec, -1, 1)};
			float[][] uv = {{inset, inset}, {1 - inset, inset}, {1 - inset, 1 - inset}, {inset, 1 - inset}};
			pose.pushPose();
			pose.scale(radius, radius, radius);
			collector.order(-1000).submitCustomGeometry(pose, RenderTypes.text(tex), (p, buffer) -> {
				for (int i = 0; i < 4; i++) quadVertex(buffer, p, corners[i], uv[i]);
				for (int i = 3; i >= 0; i--) quadVertex(buffer, p, corners[i], uv[i]); // both windings
			});
			pose.popPose();
		}
	}

	private static void quadVertex(VertexConsumer buffer, PoseStack.Pose p, Vector3f v, float[] uv) {
		buffer.addVertex(p, v.x, v.y, v.z).setColor(0xFFFFFFFF).setUv(uv[0], uv[1]).setLight(LightCoordsUtil.FULL_BRIGHT);
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

	private static void renderPlanet(PoseStack pose, SubmitNodeCollector collector, Vec3 cam, Vec3 center, float age, float k) {
		List<PackData.Stage> stages = PackData.stages();
		if (stages.size() < 3 || planetParts == null) return;
		float reveal = PackData.timelineTick("reveal_planet", 80);
		if (age < reveal - 2) return;
		float breaking = 110, aftermath = 150;
		for (var e : PackData.timeline()) {
			if (e.event().equals("planet") && e.data().has("stage")) {
				String st = e.data().get("stage").getAsString();
				if (st.equals("breaking")) breaking = e.tick();
				if (st.equals("aftermath")) aftermath = e.tick();
			}
		}
		int from, to;
		float t;
		if (age < breaking) { from = 0; to = 1; t = (age - reveal) / (breaking - reveal); }
		else if (age < aftermath) { from = 1; to = 2; t = (age - breaking) / (aftermath - breaking); }
		else { from = 2; to = 2; t = 0; }
		t = smooth(Mth.clamp(t, 0, 1));
		float extra = Math.max(0, Math.min(age - aftermath, 24000)); // slow drift after the last stage
		float modelScale = PackData.modelScalePerLogicalBlock() * k;
		float maxSep = 0;
		for (int i = 0; i < Math.min(8, stages.get(0).parts().size()); i++) {
			PackData.PlanetPart a = stages.get(from).parts().get(i), b = stages.get(to).parts().get(i);
			float tx = Mth.lerp(t, a.tx(), b.tx()), ty = Mth.lerp(t, a.ty(), b.ty()), tz = Mth.lerp(t, a.tz(), b.tz());
			Vector3f out = new Vector3f(tx, ty, tz);
			if (out.lengthSquared() > 1e-4f) out.normalize().mul(extra * 0.0025f);
			tx += out.x; ty += out.y; tz += out.z;
			maxSep = Math.max(maxSep, Math.abs(tx));
			float spin = extra * 0.01f;
			pose.pushPose();
			pose.translate(center.x - cam.x + tx * k, center.y - cam.y + ty * k, center.z - cam.z + tz * k);
			pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(t, a.rx(), b.rx()) + Math.signum(a.rx()) * spin));
			pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(t, a.ry(), b.ry()) + spin * 0.5f));
			pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(t, a.rz(), b.rz()) + Math.signum(a.rz()) * spin));
			pose.scale(modelScale, modelScale, modelScale);
			planetParts.get(i).submit(pose, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
			pose.popPose();
		}
		renderEnergy(pose, collector, cam, center, age, reveal, (maxSep + 6) * k);
	}

	/** Separate full-bright layer: three planes through the planet center, visible in the gaps between parts. */
	private static void renderEnergy(PoseStack pose, SubmitNodeCollector collector, Vec3 cam, Vec3 center, float age, float reveal, float size) {
		float since = age - reveal;
		float intensity = since < 0 ? 0 : since < 10 ? since / 10f : Math.max(0.15f, (float) Math.exp(-(since - 10) / 90.0));
		intensity *= 0.85f + 0.15f * Mth.sin(age * 0.15f);
		if (intensity <= 0.01f) return;
		int color = ARGB.colorFromFloat(Math.min(1f, intensity), 1f, 1f, 1f);
		pose.pushPose();
		pose.translate(center.x - cam.x, center.y - cam.y, center.z - cam.z);
		pose.scale(size, size, size);
		collector.submitCustomGeometry(pose, RenderTypes.eyes(ENERGY), (p, buffer) -> {
			float[][][] planes = {
					{{-1, -1, 0}, {1, -1, 0}, {1, 1, 0}, {-1, 1, 0}},
					{{0, -1, -1}, {0, -1, 1}, {0, 1, 1}, {0, 1, -1}},
					{{-1, 0, -1}, {1, 0, -1}, {1, 0, 1}, {-1, 0, 1}}};
			float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
			for (float[][] q : planes) {
				for (int i = 0; i < 4; i++) energyVertex(buffer, p, q[i], uv[i], color);
				for (int i = 3; i >= 0; i--) energyVertex(buffer, p, q[i], uv[i], color);
			}
		});
		pose.popPose();
	}

	private static void energyVertex(VertexConsumer buffer, PoseStack.Pose p, float[] v, float[] uv, int color) {
		buffer.addVertex(p, v[0], v[1], v[2]).setColor(color).setUv(uv[0], uv[1]).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p, 0, 1, 0);
	}

	private static float smooth(float x) {
		return x * x * (3 - 2 * x);
	}

	private SpaceScene() {}
}
