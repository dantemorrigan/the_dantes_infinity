package com.dantemorrigan.gauntlet.client.fx;

import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.client.cutscene.Cutscene;
import com.dantemorrigan.gauntlet.client.render.BlockMeshes;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mind stone telekinesis, client side: the key, the held cluster (real block models following the view smoothly),
 * thrown clusters (same ballistic step as the server), impacts, and a torn-apart mob drawn as its own model parts
 * flying away.
 */
public final class MindClient {
	public static final KeyMapping GRAB = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.infinity_gauntlet.mind", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, ClientPower.CATEGORY));
	private static final int LIFT_TICKS = 14;

	private static final class Held {
		List<BlockMeshes.Baked> blocks = List.of();
		BlockState sample;
		Vec3 origin;
		double radius;
		int mobId = -1;
		int age;
		boolean tearing;
	}

	private static final class Flying {
		List<BlockMeshes.Baked> blocks;
		BlockState sample;
		Vec3 pos, prev, vel;
		Vector3f axis;
		double radius;
		int age;
	}

	private static final class Gib {
		ModelPart part;
		RenderType type;
		Vec3 base, offset = Vec3.ZERO, prevOffset = Vec3.ZERO, vel;
		Vector3f axis;
		float bodyRot, scale, height, spin;
		int age;
	}

	private static final Map<Integer, Held> HELD = new HashMap<>();
	private static final Map<Integer, Flying> FLYING = new HashMap<>();
	private static final List<Gib> GIBS = new ArrayList<>();

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(MindClient::tick);
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.MindFx.TYPE, (fx, ctx) -> handle(fx));
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> mc.execute(MindClient::reset));
		LevelRenderEvents.COLLECT_SUBMITS.register(MindClient::render);
	}

	private static void reset() {
		HELD.clear();
		FLYING.clear();
		GIBS.clear();
	}

	/** Follow target of the hold brackets (mind_hold particle, xa = owner id): null once nothing is held. */
	public static Vec3 holdCenter(Entity owner) {
		Held h = HELD.get(owner.getId());
		if (h == null) return null;
		if (h.mobId >= 0) {
			Entity mob = owner.level().getEntity(h.mobId);
			return mob == null ? null : mob.position().add(0, mob.getBbHeight() / 2, 0);
		}
		return center(owner, h, 1f);
	}

	private static Vec3 center(Entity owner, Held h, float partial) {
		Vec3 target = owner.getEyePosition(partial).add(owner.getViewVector(partial).scale(3.0 + h.radius * 1.2));
		float k = Mth.clamp((h.age + partial) / LIFT_TICKS, 0f, 1f);
		k = k * k * (3 - 2 * k);
		return h.origin.lerp(target, k);
	}

	private static List<BlockMeshes.Baked> bake(List<Integer> states, List<BlockPos> offsets, Vec3 at) {
		Minecraft mc = Minecraft.getInstance();
		List<BlockMeshes.Baked> list = new ArrayList<>(states.size());
		BlockPos tint = BlockPos.containing(at);
		for (int i = 0; i < states.size() && i < offsets.size(); i++) {
			BlockState s = Block.stateById(states.get(i));
			if (s.isAir() || s.getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL) continue;
			list.add(BlockMeshes.bake(s, offsets.get(i), mc.level, tint));
		}
		return list;
	}

	private static void handle(ModNetwork.MindFx fx) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) return;
		RandomSource rnd = level.getRandom();
		switch (fx.kind()) {
			case 0 -> { // blocks torn out
				Held h = new Held();
				h.origin = fx.pos();
				h.blocks = bake(fx.states(), fx.offsets(), fx.pos());
				h.sample = fx.states().isEmpty() ? null : Block.stateById(fx.states().getFirst());
				double maxD = 0;
				for (BlockPos o : fx.offsets()) maxD = Math.max(maxD, Math.sqrt(o.distSqr(BlockPos.ZERO)));
				h.radius = maxD + 0.8;
				HELD.put(fx.ownerId(), h);
				ClientPower.spawn(ModParticles.MIND_LIFT_FRACTURE, fx.pos().add(0, 0.02, 0), 0, Math.max(0.6, h.radius / 2.2), 0);
				ClientPower.spawn(ModParticles.MIND_HOLD, fx.pos(), fx.ownerId(), h.radius * 2 / 1.8, 0);
				if (h.sample != null) burst(level, fx.pos(), h.sample, (int) (20 + h.radius * 10), h.radius);
				shakeNear(fx.pos(), 0.35f, 10);
			}
			case 1 -> { // mob lifted
				Entity mob = level.getEntity(fx.id());
				if (mob == null) return;
				Held h = new Held();
				h.mobId = fx.id();
				h.origin = mob.position();
				h.radius = Math.max(mob.getBbWidth(), mob.getBbHeight()) * 0.6 + 0.4;
				HELD.put(fx.ownerId(), h);
				ClientPower.spawn(ModParticles.MIND_HOLD, mob.position(), fx.ownerId(), Math.max(0.8, mob.getBbHeight() * 1.2 / 1.8), 0);
			}
			case 2 -> HELD.remove(fx.ownerId());
			case 3 -> { // thrown cluster
				HELD.remove(fx.ownerId());
				Flying f = new Flying();
				f.blocks = bake(fx.states(), fx.offsets(), fx.pos());
				f.sample = fx.states().isEmpty() ? null : Block.stateById(fx.states().getFirst());
				f.pos = f.prev = fx.pos();
				f.vel = fx.vec();
				double maxD = 0;
				for (BlockPos o : fx.offsets()) maxD = Math.max(maxD, Math.sqrt(o.distSqr(BlockPos.ZERO)));
				f.radius = maxD + 0.8;
				Vector3f v = fx.vec().toVector3f();
				f.axis = v.lengthSquared() < 1e-6 ? new Vector3f(1, 0, 0) : new Vector3f(v).cross(0, 1, 0);
				if (f.axis.lengthSquared() < 1e-6) f.axis.set(1, 0, 0);
				f.axis.normalize();
				FLYING.put(fx.id(), f);
			}
			case 4 -> { // impact
				Flying f = FLYING.remove(fx.id());
				double r = f != null ? f.radius : 2;
				ClientPower.spawn(ModParticles.MIND_RUPTURE, fx.pos(), 1 + r * 0.5, 0, 0);
				ClientPower.spawn(ModParticles.POWER_SPARKS_SMOKE, fx.pos(), 1 + r * 0.3, 0, 0);
				if (f != null && f.sample != null) burst(level, fx.pos(), f.sample, (int) (30 + r * 14), r);
				shakeNear(fx.pos(), 0.8f, 14);
			}
			case 5 -> {
				Held h = HELD.get(fx.ownerId());
				if (h != null) h.tearing = true;
			}
			case 6 -> { // torn apart
				HELD.remove(fx.ownerId());
				Entity e = level.getEntity(fx.id());
				float size = e != null ? Math.max(e.getBbWidth(), e.getBbHeight()) : 1f;
				ClientPower.spawn(ModParticles.MIND_RUPTURE, fx.pos(), 0.6 + size * 0.6, 0, 0);
				for (int i = 0; i < 40 + size * 20; i++) {
					level.addParticle(new DustParticleOptions(i % 3 == 0 ? 0x5A0000 : 0x9A0A0A, 1.4f), fx.pos().x + rnd.nextGaussian() * size * 0.3,
							fx.pos().y + rnd.nextGaussian() * size * 0.3, fx.pos().z + rnd.nextGaussian() * size * 0.3, 0, 0, 0);
				}
				if (e instanceof LivingEntity living) tearApart(living);
				shakeNear(fx.pos(), 0.5f, 10);
			}
			case 7 -> {
				ClientPower.spawn(ModParticles.MIND_RUPTURE, fx.pos(), 1.0, 0, 0);
				shakeNear(fx.pos(), 0.4f, 8);
			}
			default -> {}
		}
	}

	private static void shakeNear(Vec3 at, float strength, int ticks) {
		var p = Minecraft.getInstance().player;
		if (p == null) return;
		double d = p.position().distanceTo(at);
		if (d < 32) Cutscene.shake(strength * (float) (1 - d / 32), ticks);
	}

	private static void burst(ClientLevel level, Vec3 at, BlockState state, int count, double r) {
		RandomSource rnd = level.getRandom();
		BlockParticleOption opt = new BlockParticleOption(ParticleTypes.BLOCK, state);
		for (int i = 0; i < count; i++) {
			level.addParticle(opt, at.x + rnd.nextGaussian() * r * 0.5, at.y + rnd.nextGaussian() * r * 0.4, at.z + rnd.nextGaussian() * r * 0.5,
					rnd.nextGaussian() * 0.3, rnd.nextDouble() * 0.4, rnd.nextGaussian() * 0.3);
		}
	}

	/** Snapshot the mob's own model parts (current resource pack texture) and send them flying apart. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void tearApart(LivingEntity mob) {
		Minecraft mc = Minecraft.getInstance();
		EntityRenderer<?, ?> renderer = mc.getEntityRenderDispatcher().getRenderer(mob);
		if (!(renderer instanceof LivingEntityRenderer lr)) return;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		LivingEntityRenderState state = (LivingEntityRenderState) lr.createRenderState(mob, partial);
		EntityModel model = lr.getModel();
		RenderType type = model.renderType(lr.getTextureLocation(state));
		ModelPart root = model.root();
		Set<String> names = new LinkedHashSet<>();
		root.visit(new PoseStack(), (pose, path, index, cube) -> {
			for (String seg : path.split("/")) {
				if (!seg.isEmpty()) {
					names.add(seg);
					break;
				}
			}
		});
		List<ModelPart> parts = new ArrayList<>();
		for (String n : names) if (root.hasChild(n)) parts.add(root.getChild(n));
		if (parts.isEmpty()) parts.add(root);
		RandomSource rnd = mob.getRandom();
		Vec3 base = mob.getPosition(partial);
		for (ModelPart part : parts) {
			Gib g = new Gib();
			g.part = part;
			g.type = type;
			g.base = base;
			g.bodyRot = state.bodyRot;
			g.scale = state.scale;
			g.height = mob.getBbHeight();
			// Fly away from the body centre, roughly in the direction the part sits.
			double yaw = Math.toRadians(-state.bodyRot);
			double px = -part.x / 16.0, pz = part.z / 16.0;
			Vec3 side = new Vec3(px * Math.cos(yaw) - pz * Math.sin(yaw), 0, px * Math.sin(yaw) + pz * Math.cos(yaw));
			Vec3 dir = side.lengthSqr() < 1e-4 ? new Vec3(rnd.nextGaussian(), 0, rnd.nextGaussian()) : side;
			dir = dir.normalize().add(rnd.nextGaussian() * 0.35, 0, rnd.nextGaussian() * 0.35).normalize();
			g.vel = dir.scale(0.16 + rnd.nextDouble() * 0.14).add(0, 0.22 + rnd.nextDouble() * 0.2, 0);
			g.axis = new Vector3f((float) rnd.nextGaussian(), (float) rnd.nextGaussian(), (float) rnd.nextGaussian());
			if (g.axis.lengthSquared() < 1e-4) g.axis.set(1, 0, 0);
			g.axis.normalize();
			g.spin = (float) (8 + rnd.nextDouble() * 14) * (rnd.nextBoolean() ? 1 : -1);
			GIBS.add(g);
		}
	}

	private static void tick(Minecraft mc) {
		while (GRAB.consumeClick()) {
			if (mc.player != null && mc.screen == null) ClientPlayNetworking.send(new ModNetwork.MindAction(mc.player.isShiftKeyDown() ? 1 : 0));
		}
		ClientLevel level = mc.level;
		if (level == null || mc.isPaused()) return;
		RandomSource rnd = level.getRandom();
		for (Iterator<Map.Entry<Integer, Held>> it = HELD.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, Held> e = it.next();
			Held h = e.getValue();
			h.age++;
			Entity owner = level.getEntity(e.getKey());
			if (owner == null || h.age > 20 * 60 * 10) {
				it.remove();
				continue;
			}
			if (h.tearing && h.mobId >= 0) {
				Entity mob = level.getEntity(h.mobId);
				if (mob != null) {
					Vec3 c = mob.position().add(0, mob.getBbHeight() / 2, 0);
					level.addParticle(new DustParticleOptions(0x9A0A0A, 1.0f), c.x + rnd.nextGaussian() * 0.3, c.y + rnd.nextGaussian() * 0.3, c.z + rnd.nextGaussian() * 0.3, 0, 0, 0);
					if (h.age % 4 == 0) ClientPower.spawn(ModParticles.MIND_RUPTURE, c, 0.25 + rnd.nextDouble() * 0.2, 0, 0);
				}
			} else if (h.mobId < 0 && h.age < LIFT_TICKS && h.sample != null && rnd.nextInt(2) == 0) {
				Vec3 c = center(owner, h, 1f);
				level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, h.sample), c.x + rnd.nextGaussian() * h.radius * 0.4, c.y - h.radius * 0.5, c.z + rnd.nextGaussian() * h.radius * 0.4, 0, 0, 0);
			}
		}
		for (Iterator<Flying> it = FLYING.values().iterator(); it.hasNext(); ) {
			Flying f = it.next();
			f.prev = f.pos;
			f.pos = f.pos.add(f.vel);
			f.vel = f.vel.scale(0.99).add(0, -0.05, 0);
			if (++f.age > 220) {
				it.remove();
				continue;
			}
			Vector3f v = f.vel.toVector3f();
			for (int i = 0; i < 2; i++) {
				Vec3 at = f.pos.add(rnd.nextGaussian() * f.radius * 0.35, rnd.nextGaussian() * f.radius * 0.35, rnd.nextGaussian() * f.radius * 0.35);
				ClientPower.spawn(ModParticles.MIND_THROW_TRAIL, at, v.x, v.y, v.z);
			}
		}
		for (Iterator<Gib> it = GIBS.iterator(); it.hasNext(); ) {
			Gib g = it.next();
			if (++g.age > 50) {
				it.remove();
				continue;
			}
			g.prevOffset = g.offset;
			Vec3 next = g.offset.add(g.vel);
			BlockPos below = BlockPos.containing(g.base.add(next));
			if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
				g.vel = new Vec3(g.vel.x * 0.5, Math.abs(g.vel.y) * 0.25, g.vel.z * 0.5);
				g.spin *= 0.6f;
				next = g.offset.add(g.vel.x, 0, g.vel.z);
			}
			g.offset = next;
			g.vel = g.vel.add(0, -0.045, 0);
			if (g.age % 3 == 0) {
				Vec3 at = g.base.add(g.offset).add(0, g.height * 0.5, 0);
				level.addParticle(new DustParticleOptions(0x7A0505, 0.9f), at.x, at.y, at.z, 0, 0, 0);
			}
		}
	}

	private static void render(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || HELD.isEmpty() && FLYING.isEmpty() && GIBS.isEmpty()) return;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 cam = mc.gameRenderer.getMainCamera().position();
		PoseStack pose = ctx.poseStack();
		float time = level.getGameTime() + partial;
		for (Map.Entry<Integer, Held> e : HELD.entrySet()) {
			Held h = e.getValue();
			if (h.blocks.isEmpty()) continue;
			Entity owner = level.getEntity(e.getKey());
			if (owner == null) continue;
			Vec3 c = center(owner, h, partial).add(0, Math.sin(time * 0.12) * 0.08, 0);
			Quaternionf rot = Axis.YP.rotationDegrees(time * 1.6f).mul(Axis.XP.rotationDegrees((float) Math.sin(time * 0.05) * 8f));
			submit(ctx, pose, h.blocks, c, rot, cam, level);
		}
		for (Flying f : FLYING.values()) {
			Vec3 c = f.prev.lerp(f.pos, partial);
			Quaternionf rot = new Quaternionf().rotateAxis((f.age + partial) * 0.22f, f.axis.x, f.axis.y, f.axis.z);
			submit(ctx, pose, f.blocks, c, rot, cam, level);
		}
		for (Gib g : GIBS) {
			Vec3 at = g.base.add(g.prevOffset.lerp(g.offset, partial));
			float shrink = g.age > 40 ? Math.max(0.05f, 1f - (g.age - 40 + partial) / 10f) : 1f;
			pose.pushPose();
			pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
			pose.rotateAround(new Quaternionf().rotateAxis((float) Math.toRadians((g.age + partial) * g.spin), g.axis.x, g.axis.y, g.axis.z), 0, g.height * 0.5f, 0);
			pose.translate(0, g.height * 0.5f * (1 - shrink), 0);
			pose.scale(g.scale * shrink, g.scale * shrink, g.scale * shrink);
			pose.mulPose(Axis.YP.rotationDegrees(180f - g.bodyRot));
			pose.scale(-1f, -1f, 1f);
			pose.translate(0f, -1.501f, 0f);
			int light = net.minecraft.client.renderer.LevelRenderer.getLightCoords(level, BlockPos.containing(at.add(0, 0.5, 0)));
			ctx.submitNodeCollector().submitModelPart(g.part, pose, g.type, light, OverlayTexture.NO_OVERLAY, null);
			pose.popPose();
		}
	}

	private static void submit(LevelRenderContext ctx, PoseStack pose, List<BlockMeshes.Baked> blocks, Vec3 c, Quaternionf rot, Vec3 cam, ClientLevel level) {
		int light = net.minecraft.client.renderer.LevelRenderer.getLightCoords(level, BlockPos.containing(c));
		pose.pushPose();
		pose.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);
		pose.mulPose(rot);
		for (BlockMeshes.Baked b : blocks) {
			pose.pushPose();
			pose.translate(b.pos().getX() - 0.5f, b.pos().getY() - 0.5f, b.pos().getZ() - 0.5f);
			b.state().submit(pose, ctx.submitNodeCollector(), light, OverlayTexture.NO_OVERLAY, 0);
			pose.popPose();
		}
		pose.popPose();
	}

	private MindClient() {}
}
