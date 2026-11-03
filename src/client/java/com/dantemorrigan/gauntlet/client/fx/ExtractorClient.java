package com.dantemorrigan.gauntlet.client.fx;

import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.extractor.StoneExtraction;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Infinity Extractor visuals: a thread of light from the target's gauntlet to the extractor while a cycle runs,
 * gather / success / mote / seal flashes, and the progress ring (or the "blocked" lock) at the crosshair.
 */
public final class ExtractorClient {
	private record Tether(int target, int stone) {}

	private static final Map<Integer, Tether> TETHERS = new HashMap<>();
	/** Local HUD timers: blocked lock, short flash after a cycle. */
	private static int blocked, flash;
	/** The crosshair is on someone wearing a gauntlet within the extractor's range. */
	private static boolean aimed;

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.ExtractorFx.TYPE, (fx, ctx) -> onFx(fx));
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> {TETHERS.clear(); blocked = flash = 0; aimed = false;});
		ClientTickEvents.END_CLIENT_TICK.register(ExtractorClient::tick);
		HudElementRegistry.addLast(InfinityGauntlet.id("infinity_extractor"), (g, d) -> hud(g));
	}

	/** Where the extractor's claws point: in front of the owner's main hand. */
	public static Vec3 muzzle(Entity owner, float partial) {
		float yaw = owner.getViewYRot(partial) * Mth.DEG_TO_RAD;
		Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		if (owner instanceof Player p && p.getMainArm() == HumanoidArm.LEFT) right = right.scale(-1);
		Vec3 look = owner.getViewVector(partial);
		return owner.getEyePosition(partial).add(look.scale(0.7)).add(right.scale(0.28)).add(0, -0.3, 0).add(forward.scale(0.05));
	}

	private static void onFx(ModNetwork.ExtractorFx fx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		Entity owner = mc.level.getEntity(fx.ownerId());
		Entity target = fx.targetId() >= 0 ? mc.level.getEntity(fx.targetId()) : null;
		boolean local = mc.player != null && fx.ownerId() == mc.player.getId();
		switch (fx.kind()) {
			case 0 -> {
				TETHERS.put(fx.ownerId(), new Tether(fx.targetId(), fx.stone()));
				if (target != null) mc.level.addParticle(ModParticles.EXTRACTOR_GATHER, handOf(target).x, handOf(target).y, handOf(target).z, 1.0, 0, 0);
			}
			case 1 -> {
				TETHERS.remove(fx.ownerId());
				if (owner != null) {
					Vec3 m = muzzle(owner, 1f);
					mc.level.addParticle(ModParticles.EXTRACTOR_SUCCESS, m.x, m.y, m.z, 1.0, 0, 0);
				}
				if (local) flash = 14;
			}
			case 2 -> TETHERS.remove(fx.ownerId());
			case 3 -> {
				TETHERS.remove(fx.ownerId());
				if (local) blocked = 30;
			}
			case 4 -> {
				TETHERS.remove(fx.ownerId());
				if (owner != null) {
					Vec3 from = target != null ? handOf(target) : muzzle(owner, 1f);
					for (int i = 0; i < 6; i++) {
						mc.level.addParticle(ModParticles.EXTRACTOR_MOTE, from.x + mc.level.getRandom().nextGaussian() * 0.15, from.y + mc.level.getRandom().nextGaussian() * 0.15,
								from.z + mc.level.getRandom().nextGaussian() * 0.15, fx.ownerId(), 0, 0);
					}
					Vec3 m = muzzle(owner, 1f);
					mc.level.addParticle(ModParticles.EXTRACTOR_SEAL, m.x, m.y, m.z, 1.0, 0, 0);
				}
				if (local) flash = 20;
			}
			case 5 -> {
				if (owner != null) {
					Vec3 m = handOf(owner);
					mc.level.addParticle(ModParticles.EXTRACTOR_SEAL, m.x, m.y, m.z, 1.0, 0, 0);
				}
				if (local) flash = 14;
			}
			default -> {}
		}
	}

	private static Vec3 handOf(Entity entity) {
		return FistPosition.of(entity, 1f);
	}

	private static void tick(Minecraft mc) {
		if (blocked > 0) blocked--;
		if (flash > 0) flash--;
		if (mc.level == null || mc.player == null) {
			aimed = false;
			return;
		}
		aimed = holding(mc.player) && aimedTarget(mc.player) != null;
		long time = mc.level.getGameTime();
		TETHERS.entrySet().removeIf(entry -> {
			Entity owner = mc.level.getEntity(entry.getKey());
			Entity target = mc.level.getEntity(entry.getValue().target());
			if (owner == null || target == null || !target.isAlive()) return true;
			if (time % 2 != 0) return false;
			// The thread: short pulses along the line, shifted a little every tick so they run toward the extractor.
			Vec3 a = handOf(target), b = muzzle(owner, 1f);
			double length = a.distanceTo(b);
			if (length < 0.3) return false;
			Vec3 dir = b.subtract(a).scale(1 / length);
			int n = Math.max(1, (int) Math.round(length / 1.1));
			double step = length / n, shift = ((time % 8) / 8.0) * step;
			for (int i = 0; i < n; i++) {
				double d = Math.min(length - 0.2, i * step + shift + step * 0.5);
				Vec3 p = a.add(dir.scale(d));
				mc.level.addParticle(ModParticles.EXTRACTOR_TETHER, p.x, p.y, p.z, dir.x, dir.y, dir.z);
			}
			return false;
		});
	}

	private static boolean holding(Player player) {
		return player.getMainHandItem().is(ModItems.INFINITY_EXTRACTOR);
	}

	private static LivingEntity aimedTarget(Player p) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getViewVector(1f);
		int range = StoneExtraction.RANGE;
		AABB box = p.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, eye.add(look.scale(range)), box,
				e -> e != p && !e.isSpectator() && e.isAlive() && e instanceof LivingEntity le && !GauntletState.activeGauntlet(le).isEmpty(), range * range);
		return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
	}

	/** Cycles shown on the ring for an extractor stack (model state 0..4). */
	private static int cycles(ItemStack stack) {
		CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		Float state = data == null ? null : data.getFloat(0);
		int s = state == null ? 0 : state.intValue();
		return s == 4 ? StoneExtraction.CYCLES : Math.max(0, s - 1);
	}

	private static void hud(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || !holding(mc.player)) return;
		boolean active = TETHERS.containsKey(mc.player.getId());
		if (!aimed && !active && blocked <= 0 && flash <= 0) return;
		String name = blocked > 0 ? "blocked" : "cycles_" + cycles(mc.player.getMainHandItem());
		float size = 40f * (flash > 0 ? 1f + 0.12f * (flash / 14f) : 1f);
		float x = g.guiWidth() / 2f - size / 2f, y = g.guiHeight() / 2f - size / 2f;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(size / 64f, size / 64f);
		g.blit(RenderPipelines.GUI_TEXTURED, InfinityGauntlet.id("textures/gui/extractor/" + name + ".png"), 0, 0, 0f, 0f, 64, 64, 64, 64);
		g.pose().popMatrix();
	}

	private ExtractorClient() {}
}
