package com.dantemorrigan.gauntlet.client.fx;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.client.cutscene.Cutscene;
import com.dantemorrigan.gauntlet.client.render.BlockMeshes;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reality Inversion on the client. Hold G (closed fist): after the charge and the cracks, the area around you is
 * taken apart into its real BlockStates (current resource pack models, real biome tint) and turned inside out —
 * the ground on both sides of your gaze folds up into walls (turning your head moves the parting), buildings twist
 * around the center, water hangs in the air. Release G (open fist): everything flows back and the real blocks
 * return. Nothing changes on the server; the originals are only hidden on this client and always put back.
 */
public final class RealityClient {
	public static final KeyMapping KEY = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.infinity_gauntlet.reality", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, ClientPower.CATEGORY));

	private static final int T_CRACKS = 12, T_FOLD = 20, FOLD_TICKS = 20, RELEASE_TICKS = 14;
	private static final float INNER = 2.5f; // the caster's own patch stays solid

	private enum Kind { GROUND, BUILT, WATER }

	private record Entry(BlockPos pos, BlockState state, BlockMeshes.Baked baked, Kind kind, float seed, int waterColor) {}

	// ---- local caster state ----
	private static boolean keyDown;
	private static boolean active;
	private static Vec3 center = Vec3.ZERO;
	private static int age;
	private static int releaseAge = -1;
	private static float releaseFrom;
	private static float yaw, prevYaw;
	private static ClientLevel level;
	private static final List<Entry> ENTRIES = new ArrayList<>();
	private static final Map<BlockPos, BlockState> HIDDEN = new HashMap<>();
	/** Other entities currently casting (for the charge aura). */
	private static final Set<Integer> CASTERS = new HashSet<>();
	private record Inversion(Vec3 center, long started, long expires, float yaw) {}
	private static final Map<Integer, Inversion> INVERSIONS = new HashMap<>();
	private static ClientLevel inversionLevel;
	private static int renderedCaster = -1;
	private static int liveBudget;

	/** A press shorter than this (ticks) is a tap → Reality strike. */
	private static final int TAP_TICKS = 5;
	private static int downTicks;

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(RealityClient::tick);
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.RealityState.TYPE, (s, ctx) -> state(s.entityId(), s.active(), s.center(), s.age(), s.yaw()));
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.RealityFx.TYPE, (f, ctx) -> fx(f.kind(), f.pos(), f.vel()));
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(() -> restore(true)));
		LevelRenderEvents.COLLECT_SUBMITS.register(RealityClient::render);
		HudElementRegistry.addLast(InfinityGauntlet.id("reality_tint"), (g, d) -> {
			float p = progress(d.getGameTimeDeltaPartialTick(false));
			if (p > 0.01f) g.fill(0, 0, g.guiWidth(), g.guiHeight(), ARGB.colorFromFloat(0.13f * p, 0.75f, 0.02f, 0.08f));
		});
	}

	/** Movement is frozen while reality is turned (you steer with your gaze — the hand). */
	public static boolean inputLocked() {
		var player = Minecraft.getInstance().player;
		return player != null && renderedCaster == player.getId() && (active || releaseAge >= 0);
	}

	public static Vec3 fistWhileCharging(Entity e) {
		return CASTERS.contains(e.getId()) ? FistPosition.of(e, 1f) : null;
	}

	// ------------------------------------------------------------------ state

	private static void state(int entityId, boolean on, Vec3 c, int remoteAge, float remoteYaw) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		if (inversionLevel != mc.level) {
			restore(true);
			inversionLevel = mc.level;
		}
		if (on) {
			if (!INVERSIONS.containsKey(entityId) && remoteAge < T_CRACKS) {
				ClientPower.spawn(ModParticles.REALITY_CHARGE, c.add(0, 1, 0), entityId, 0, 0);
			}
			long now = mc.level.getGameTime();
			Inversion previous = INVERSIONS.get(entityId);
			long started = previous == null ? now - remoteAge : previous.started();
			INVERSIONS.put(entityId, new Inversion(c, started, now + 60, remoteYaw));
			CASTERS.add(entityId);
		} else {
			INVERSIONS.remove(entityId);
			CASTERS.remove(entityId);
		}
	}

	private static void chooseInversion(Minecraft mc) {
		if (inversionLevel != mc.level) {
			restore(true);
			inversionLevel = mc.level;
		}
		INVERSIONS.entrySet().removeIf(entry -> {
			if (entry.getValue().expires() > mc.level.getGameTime()) return false;
			CASTERS.remove(entry.getKey());
			return true;
		});
		int selected = -1;
		double best = 64 * 64;
		for (var entry : INVERSIONS.entrySet()) {
			int id = entry.getKey();
			if (id == mc.player.getId()) {
				selected = id;
				break;
			}
			double distance = mc.player.position().distanceToSqr(entry.getValue().center());
			if (distance < best) {
				selected = id;
				best = distance;
			}
		}
		if (selected == renderedCaster && active) return;
		if (selected < 0) {
			if (active) {
				releaseFrom = foldProgress(0);
				active = false;
				releaseAge = 0;
				ClientPower.spawn(ModParticles.REALITY_RESTORE, center.add(0, 0.06, 0), 0, 1.4, 0);
			}
			return;
		}
		restore(false);
		renderedCaster = selected;
		active = true;
		Inversion inversion = INVERSIONS.get(selected);
		center = inversion.center();
		level = mc.level;
		age = (int) Math.max(0, mc.level.getGameTime() - inversion.started());
		Entity caster = mc.level.getEntity(selected);
		yaw = prevYaw = caster == null ? inversion.yaw() : caster.getYRot();
		if (age >= T_FOLD) capture(mc);
	}

	private static void fx(int kind, Vec3 pos, Vec3 vel) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		RandomSource rnd = mc.level.getRandom();
		switch (kind) {
			case 0 -> { // mob -> red butterflies
				ClientPower.spawn(ModParticles.REALITY_REWRITE, pos, 0.8, 0, 0);
				for (int i = 0; i < 5; i++) ClientPower.spawn(ModParticles.REALITY_BUTTERFLY, pos.add(rnd.nextGaussian() * 0.3, rnd.nextGaussian() * 0.3, rnd.nextGaussian() * 0.3), 0, 0, 0);
			}
			case 1 -> { // projectile -> petals
				for (int i = 0; i < 7; i++) {
					Vec3 v = vel.scale(0.25).add(rnd.nextGaussian() * 0.05, rnd.nextDouble() * 0.05, rnd.nextGaussian() * 0.05);
					ClientPower.spawn(ModParticles.REALITY_PETAL, pos, v.x, v.y, v.z);
				}
			}
			case 2 -> ClientPower.spawn(ModParticles.REALITY_REWRITE, pos, 1.0, 0, 0); // butterflies gather back into the mob
			case 3 -> { // strike: mob -> butterflies that scatter upward and dissolve
				int n = 6 + (int) Math.min(10, vel.x * vel.y * 4);
				ClientPower.spawn(ModParticles.REALITY_REWRITE, pos, 0.9, 0, 0);
				for (int i = 0; i < n; i++) {
					Vec3 p = pos.add(rnd.nextGaussian() * vel.x * 0.4, rnd.nextGaussian() * vel.y * 0.3, rnd.nextGaussian() * vel.x * 0.4);
					ClientPower.spawn(ModParticles.REALITY_BUTTERFLY, p, rnd.nextGaussian() * 0.03, 0.05 + rnd.nextDouble() * 0.05, rnd.nextGaussian() * 0.03);
				}
				for (int i = 0; i < 6; i++) ClientPower.spawn(ModParticles.REALITY_GLASS, pos, rnd.nextGaussian() * 0.15, rnd.nextDouble() * 0.2, rnd.nextGaussian() * 0.15);
			}
			case 4 -> strikeFx(mc, pos, (float) vel.x);
			default -> {}
		}
	}

	// ------------------------------------------------------------------ tick

	private static void tick(Minecraft mc) {
		// Short tap = strike the ground; held past TAP_TICKS = close the fist (Inversion).
		boolean down = KEY.isDown() && mc.player != null && mc.screen == null;
		if (down) {
			if (++downTicks == TAP_TICKS && mc.player != null) {
				keyDown = true;
				ClientPlayNetworking.send(new ModNetwork.RealityHold(true));
			}
		} else if (downTicks > 0) {
			if (mc.player != null) {
				if (keyDown) ClientPlayNetworking.send(new ModNetwork.RealityHold(false));
				else ClientPlayNetworking.send(new ModNetwork.RealityStrike());
			}
			keyDown = false;
			downTicks = 0;
		}
		if (mc.level == null || mc.player == null) {
			if (!HIDDEN.isEmpty() || active) restore(true);
			return;
		}
		if ((active || releaseAge >= 0) && (mc.level != level || !mc.player.isAlive())) {
			restore(true);
			return;
		}
		chooseInversion(mc);
		liveBudget = Math.max(0, liveBudget - 4);
		prevYaw = yaw;
		if (active) {
			age++;
			Entity caster = mc.level.getEntity(renderedCaster);
			float targetYaw = caster == null ? INVERSIONS.get(renderedCaster).yaw() : caster.getYRot();
			yaw += Mth.wrapDegrees(targetYaw - yaw) * 0.15f;
			if (age == T_CRACKS) cracks(mc);
			if (age == T_FOLD) {
				capture(mc);
				Cutscene.shake(0.45f, 14);
			}
			if (age > T_FOLD) ambient(mc);
		} else if (releaseAge >= 0) {
			releaseAge++;
			if (releaseAge >= RELEASE_TICKS) restore(false);
		}
	}

	/** The fist hits the ground: red fracture + ring under the caster, cubic shards and sparks thrown out. */
	private static void strikeFx(Minecraft mc, Vec3 c, float radius) {
		RandomSource rnd = mc.level.getRandom();
		Vec3 ground = surface(mc.level, c.x, c.z);
		ClientPower.spawn(ModParticles.REALITY_FRACTURE, ground.add(0, 0.05, 0), 0, 1.8, 0);
		ClientPower.spawn(ModParticles.REALITY_RESTORE, ground.add(0, 0.07, 0), 0, 1.6, 0);
		ClientPower.spawn(ModParticles.REALITY_REWRITE, c.add(0, 0.6, 0), 1.6, 0, 0);
		for (int i = 0; i < 40; i++) {
			double a = rnd.nextDouble() * Math.PI * 2, s = 0.25 + rnd.nextDouble() * 0.45;
			ClientPower.spawn(ModParticles.REALITY_GLASS, ground.add(0, 0.4, 0), Math.cos(a) * s, 0.12 + rnd.nextDouble() * 0.3, Math.sin(a) * s);
		}
		for (int i = 0; i < 6; i++) {
			double a = rnd.nextDouble() * Math.PI * 2, d = 2 + rnd.nextDouble() * (radius - 2);
			Vec3 p = surface(mc.level, c.x + Math.cos(a) * d, c.z + Math.sin(a) * d);
			ClientPower.spawn(ModParticles.REALITY_FRACTURE, p.add(0, 0.05, 0), 0, 0.7 + rnd.nextDouble() * 0.5, 0);
		}
		for (int i = 0; i < 8; i++) ClientPower.spawn(ModParticles.REALITY_AETHER, ground.add(rnd.nextGaussian() * 2, 0.3, rnd.nextGaussian() * 2), 0, 0, 0);
		Cutscene.shake(0.7f, 14);
	}

	private static void cracks(Minecraft mc) {
		RandomSource rnd = mc.level.getRandom();
		float r = GauntletConfig.get().realityRadius;
		ClientPower.spawn(ModParticles.REALITY_FRACTURE, center.add(0, 0.05, 0), 0, 1.6, 0);
		for (int i = 0; i < 7; i++) {
			double a = rnd.nextDouble() * Math.PI * 2, d = INNER + 2 + rnd.nextDouble() * (r - INNER - 2);
			Vec3 p = surface(mc.level, center.x + Math.cos(a) * d, center.z + Math.sin(a) * d);
			ClientPower.spawn(ModParticles.REALITY_FRACTURE, p.add(0, 0.05, 0), 0, 0.6 + rnd.nextDouble() * 0.5, 0);
			ClientPower.spawn(ModParticles.REALITY_FRACTURE, p.add(0, 2.2, 0), 0.7, 0, 0); // upright tear
		}
	}

	private static void ambient(Minecraft mc) {
		RandomSource rnd = mc.level.getRandom();
		int max = 192;
		if (liveBudget > max) return;
		float r = GauntletConfig.get().realityRadius;
		// Glass shards along the distortion boundary, aether ribbons inside it.
		double a = rnd.nextDouble() * Math.PI * 2;
		Vec3 edge = center.add(Math.cos(a) * r, 1 + rnd.nextDouble() * 4, Math.sin(a) * r);
		Vec3 out = new Vec3(Math.cos(a), 0.2, Math.sin(a)).scale(0.08);
		ClientPower.spawn(ModParticles.REALITY_GLASS, edge, out.x, out.y, out.z);
		if (age % 2 == 0) {
			Vec3 in = center.add(rnd.nextGaussian() * r * 0.4, 1 + rnd.nextDouble() * 6, rnd.nextGaussian() * r * 0.4);
			ClientPower.spawn(ModParticles.REALITY_AETHER, in, 0, 0, 0);
		}
		liveBudget += 12;
	}

	private static Vec3 surface(ClientLevel lvl, double x, double z) {
		BlockPos top = lvl.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, center.y, z));
		double y = Math.abs(top.getY() - center.y) < 8 ? top.getY() : center.y;
		return new Vec3(x, y, z);
	}

	private static boolean isBuilt(BlockState s) {
		return s.is(BlockTags.PLANKS) || s.is(BlockTags.STONE_BRICKS) || s.is(BlockTags.WOOL) || s.is(BlockTags.WOODEN_STAIRS)
				|| s.is(BlockTags.WOODEN_SLABS) || s.is(BlockTags.WOODEN_FENCES) || s.is(BlockTags.DOORS) || s.is(BlockTags.TERRACOTTA)
				|| s.is(Blocks.BRICKS) || s.is(Blocks.GLASS) || s.is(Blocks.GLASS_PANE) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.SMOOTH_STONE)
				|| s.is(BlockTags.WALLS) || s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.LANTERN) || s.is(Blocks.CRAFTING_TABLE);
	}

	/** Take the area apart: record the real states, hide them here, and keep their baked models. */
	private static void capture(Minecraft mc) {
		ClientLevel lvl = mc.level;
		GauntletConfig cfg = GauntletConfig.get();
		int r = cfg.realityRadius;
		BlockPos c = BlockPos.containing(center);
		List<BlockPos> columns = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > r || d < INNER) continue;
				columns.add(c.offset(dx, 0, dz));
			}
		}
		columns.sort((a, b) -> Double.compare(a.distSqr(c), b.distSqr(c)));
		RandomSource rnd = RandomSource.create(c.asLong());
		int budget = cfg.realityMaxBlocks;
		for (BlockPos col : columns) {
			int skin = 0;
			for (int y = c.getY() + 14; y >= c.getY() - 8 && skin < 3; y--) {
				BlockPos p = new BlockPos(col.getX(), y, col.getZ());
				BlockState s = lvl.getBlockState(p);
				if (s.isAir()) {
					if (skin > 0) skin++; // a cave right under the skin: the skin is done
					continue;
				}
				if (s.hasBlockEntity()) continue;
				boolean water = s.getFluidState().is(FluidTags.WATER) && s.is(Blocks.WATER);
				Kind kind = water ? Kind.WATER : isBuilt(s) ? Kind.BUILT : Kind.GROUND;
				if (kind == Kind.GROUND && s.isSolidRender() || water) skin++;
				int waterColor = water ? BiomeColors.getAverageWaterColor(lvl, p) : 0;
				BlockMeshes.Baked baked = water ? null : BlockMeshes.bake(s, BlockPos.ZERO, lvl, p);
				ENTRIES.add(new Entry(p.immutable(), s, baked, kind, rnd.nextFloat() * 100, waterColor));
				HIDDEN.put(p.immutable(), s);
				if (--budget <= 0) break;
			}
			if (budget <= 0) break;
		}
		for (BlockPos p : HIDDEN.keySet()) lvl.setBlock(p, Blocks.AIR.defaultBlockState(), 18);
		// Cubic rewrite bursts over a sample of the taken blocks.
		for (int i = 0; i < Math.min(28, ENTRIES.size()); i++) {
			Entry e = ENTRIES.get(rnd.nextInt(ENTRIES.size()));
			ClientPower.spawn(ModParticles.REALITY_REWRITE, Vec3.atCenterOf(e.pos()), 0.7 + rnd.nextDouble() * 0.5, 0, 0);
		}
		InfinityGauntlet.LOGGER.debug("Reality inversion took {} blocks", ENTRIES.size());
	}

	/** Keep a server block update that arrives while this block is hidden for the illusion. */
	public static void invalidateHiddenBlock(BlockPos pos) {
		if (HIDDEN.remove(pos) != null) ENTRIES.removeIf(entry -> entry.pos().equals(pos));
	}

	/** Put every real block back (immediately). */
	private static void restore(boolean abort) {
		Minecraft mc = Minecraft.getInstance();
		if (!HIDDEN.isEmpty() && level != null && mc.level == level) {
			HIDDEN.forEach((p, s) -> {
				if (level.hasChunkAt(p) && level.getBlockState(p).isAir()) level.setBlock(p, s, 18);
			});
		}
		HIDDEN.clear();
		ENTRIES.clear();
		active = false;
		releaseAge = -1;
		age = 0;
		renderedCaster = -1;
		if (abort) {
			CASTERS.clear();
			INVERSIONS.clear();
		}
	}

	// ------------------------------------------------------------------ the inversion

	private static float foldProgress(float partial) {
		float t = Mth.clamp((age + partial - T_FOLD) / FOLD_TICKS, 0, 1);
		return t * t * (3 - 2 * t);
	}

	private static float progress(float partial) {
		if (active) return foldProgress(partial);
		if (releaseAge >= 0) {
			float t = Mth.clamp((releaseAge + partial) / RELEASE_TICKS, 0, 1);
			return releaseFrom * (1 - t * t * (3 - 2 * t));
		}
		return 0;
	}

	private record Pose(Vec3 center, Quaternionf rot) {}

	/** Where a taken block is drawn at progress p (0 = its real place). */
	private static Pose transform(Entry e, float p, float yawDeg, float time) {
		Vec3 pos = Vec3.atCenterOf(e.pos());
		Quaternionf rot = new Quaternionf();
		if (p <= 0) return new Pose(pos, rot);
		double groundY = center.y - 0.5;
		float yawRad = yawDeg * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yawRad), 0, Mth.cos(yawRad));
		Vec3 right = new Vec3(-fwd.z, 0, fwd.x);

		if (e.kind() == Kind.WATER) {
			double lift = p * (2.5 + Math.sin(time * 0.08 + e.seed()) * 0.6);
			rot.rotateY(Mth.sin(time * 0.05f + e.seed()) * 0.25f * p).rotateX(Mth.cos(time * 0.04f + e.seed()) * 0.2f * p);
			return new Pose(pos.add(0, lift, 0), rot);
		}
		// Buildings twist around the vertical axis through the caster, more the higher they are.
		if (e.kind() == Kind.BUILT) {
			double h = Mth.clamp((pos.y - groundY) / 6.0, 0, 1.6);
			float twist = (float) (p * Math.toRadians(40) * h);
			Vec3 rel = pos.subtract(center.x, 0, center.z);
			Vec3 turned = new Vec3(rel.x * Math.cos(twist) - rel.z * Math.sin(twist), rel.y, rel.x * Math.sin(twist) + rel.z * Math.cos(twist));
			pos = turned.add(center.x, 0, center.z);
			rot.rotateY(-twist);
		}
		// Ground on both sides of the gaze folds up into walls around a hinge parallel to the gaze.
		Vec3 relXZ = pos.subtract(center.x, pos.y, center.z);
		double a = relXZ.dot(right);
		double s = Math.signum(a);
		double over = Math.abs(a) - INNER;
		if (over > 0 && s != 0) {
			float k = (float) Mth.clamp(over / 7.0, 0, 1);
			float theta = (float) (-s * p * Math.toRadians(88) * (k * k * (3 - 2 * k)));
			Vec3 hinge = new Vec3(center.x, groundY, center.z).add(right.scale(s * INNER));
			Vector3f v = new Vector3f((float) (pos.x - hinge.x), (float) (pos.y - hinge.y), (float) (pos.z - hinge.z));
			Quaternionf fold = new Quaternionf().rotationAxis(theta, (float) fwd.x, 0, (float) fwd.z);
			fold.transform(v);
			pos = new Vec3(hinge.x + v.x, hinge.y + v.y, hinge.z + v.z);
			rot = fold.mul(rot);
		}
		// A slight living ripple so the inverted world never sits still.
		pos = pos.add(0, Math.sin(time * 0.1 + e.seed()) * 0.05 * p, 0);
		return new Pose(pos, rot);
	}

	private static void render(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (ENTRIES.isEmpty() || mc.level == null || mc.level != level) return;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float p = progress(partial);
		float yawNow = Mth.rotLerp(partial, prevYaw, yaw);
		float time = mc.level.getGameTime() + partial;
		Vec3 cam = mc.gameRenderer.getMainCamera().position();
		PoseStack pose = ctx.poseStack();
		List<Entry> water = new ArrayList<>();
		for (Entry e : ENTRIES) {
			if (e.kind() == Kind.WATER) {
				water.add(e);
				continue;
			}
			Pose t = transform(e, p, yawNow, time);
			pose.pushPose();
			pose.translate(t.center().x - cam.x, t.center().y - cam.y, t.center().z - cam.z);
			pose.mulPose(t.rot());
			pose.translate(-0.5f, -0.5f, -0.5f);
			e.baked().state().submit(pose, ctx.submitNodeCollector(), LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
			pose.popPose();
		}
		if (!water.isEmpty()) renderWater(ctx, water, p, yawNow, time, cam);
	}

	/** Hanging water: cubes with the real water texture and biome water colour. */
	private static void renderWater(LevelRenderContext ctx, List<Entry> water, float p, float yawNow, float time, Vec3 cam) {
		TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
		TextureAtlasSprite sprite = atlas.getSprite(Identifier.withDefaultNamespace("block/water_still"));
		PoseStack pose = ctx.poseStack();
		for (Entry e : water) {
			Pose t = transform(e, p, yawNow, time);
			int c = e.waterColor();
			int color = ARGB.colorFromFloat(0.8f, ((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f);
			pose.pushPose();
			pose.translate(t.center().x - cam.x, t.center().y - cam.y, t.center().z - cam.z);
			pose.mulPose(t.rot());
			float h = 0.5f - 0.02f;
			ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS), (pp, buf) -> cube(buf, pp, h, sprite, color));
			pose.popPose();
		}
	}

	private static void cube(VertexConsumer b, PoseStack.Pose p, float h, TextureAtlasSprite s, int color) {
		float u0 = s.getU0(), u1 = s.getU1(), v0 = s.getV0(), v1 = s.getV1();
		float[][][] faces = {
				{{-h, h, -h}, {-h, h, h}, {h, h, h}, {h, h, -h}, {0, 1, 0}},
				{{-h, -h, h}, {-h, -h, -h}, {h, -h, -h}, {h, -h, h}, {0, -1, 0}},
				{{-h, -h, -h}, {-h, h, -h}, {h, h, -h}, {h, -h, -h}, {0, 0, -1}},
				{{h, -h, h}, {h, h, h}, {-h, h, h}, {-h, -h, h}, {0, 0, 1}},
				{{-h, -h, h}, {-h, h, h}, {-h, h, -h}, {-h, -h, -h}, {-1, 0, 0}},
				{{h, -h, -h}, {h, h, -h}, {h, h, h}, {h, -h, h}, {1, 0, 0}}};
		float[][] uv = {{u0, v1}, {u0, v0}, {u1, v0}, {u1, v1}};
		for (float[][] f : faces) {
			for (int i = 0; i < 4; i++) {
				b.addVertex(p, f[i][0], f[i][1], f[i][2]).setColor(color).setUv(uv[i][0], uv[i][1]).setOverlay(OverlayTexture.NO_OVERLAY)
						.setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p, f[4][0], f[4][1], f[4][2]);
			}
		}
	}

	private RealityClient() {}
}
