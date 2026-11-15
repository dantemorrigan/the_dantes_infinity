package com.dantemorrigan.gauntlet.doom;

import com.dantemorrigan.gauntlet.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The East Wing's anti-gravity lift and the generator's beacon.
 *
 * <p>The lift is an open shaft of green light from the ground-floor hall down to the generator vault. Step over its
 * edge and the beam catches you and lowers you, gently, to the emerald pad below; stand on the pad for a moment and it
 * carries you back up. Nothing to press, nothing to fall from.
 *
 * <p>The generator glows with sparks, carries a floating label, and tells whoever comes near what to do with it.
 */
final class LatveriaLift {
	private static final int F = LatveriaPlan.FLOOR, GY = F - 13;
	/** The shaft, in world coordinates (x 30..33, z -280..-277 are open blocks). */
	private static final double X0 = 30, X1 = 34, Z0 = -280, Z1 = -276, CX = 32, CZ = -278;
	private static final double TOP = F, BOTTOM = GY;
	private static final int WAIT_TICKS = 24;
	private static final String LABEL_TAG = "latveria_generator_label";

	private static final class Ride {
		final boolean down;
		final double from, to;
		int age;
		final int length;

		Ride(boolean down, double from, double to) {
			this.down = down;
			this.from = from;
			this.to = to;
			this.length = Math.max(20, (int) (Math.abs(to - from) / 0.42));
		}
	}

	private static final Map<UUID, Ride> RIDES = new HashMap<>();
	private static final Map<UUID, Integer> WAIT = new HashMap<>();

	private LatveriaLift() {}

	static void clear() {
		RIDES.clear();
		WAIT.clear();
	}

	static void tick(ServerLevel level) {
		long now = level.getGameTime();
		for (ServerPlayer p : level.players()) {
			if (!p.isAlive() || p.isSpectator() || p.isPassenger()) { RIDES.remove(p.getUUID()); WAIT.remove(p.getUUID()); continue; }
			Ride ride = RIDES.get(p.getUUID());
			if (ride != null) { carry(level, p, ride); continue; }
			Vec3 at = p.position();
			boolean inShaft = at.x > X0 + 0.05 && at.x < X1 - 0.05 && at.z > Z0 + 0.05 && at.z < Z1 - 0.05;
			if (inShaft && at.y < TOP - 0.05 && at.y > BOTTOM + 0.7) {
				start(level, p, true);
			} else if (inShaft && at.y <= BOTTOM + 0.7 && at.y > BOTTOM - 1) {
				int waited = WAIT.merge(p.getUUID(), 1, Integer::sum);
				if (waited == 1) DoomFx.tell(p, "lift.hint");
				if (waited % 6 == 0) level.sendParticles(ParticleTypes.END_ROD, CX, BOTTOM + 0.2, CZ, 6, 0.8, 0.1, 0.8, 0.02);
				if (waited >= WAIT_TICKS) { WAIT.remove(p.getUUID()); start(level, p, false); }
			} else WAIT.remove(p.getUUID());
		}
		if (now % 3 == 0) shaftGlow(level, now);
		if (now % 10 == 0) generator(level, now);
	}

	private static void start(ServerLevel level, ServerPlayer p, boolean down) {
		RIDES.put(p.getUUID(), down ? new Ride(true, p.getY(), BOTTOM) : new Ride(false, BOTTOM, TOP));
		level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.9f, down ? 1.5f : 1.1f);
		if (down) DoomFx.tell(p, "lift.down");
	}

	/** One tick of the ride: ease in, glide, ease out, then step off the beam. */
	private static void carry(ServerLevel level, ServerPlayer p, Ride r) {
		r.age++;
		float t = Mth.clamp(r.age / (float) r.length, 0, 1);
		float eased = t * t * (3 - 2 * t);
		double y = Mth.lerp(eased, r.from, r.to);
		Vec3 now = p.position();
		double x = Mth.clamp(Mth.lerp(0.18, now.x, CX), X0 + 0.4, X1 - 0.4), z = Mth.clamp(Mth.lerp(0.18, now.z, CZ), Z0 + 0.4, Z1 - 0.4);
		p.connection.teleport(x, y, z, p.getYRot(), p.getXRot());
		p.setDeltaMovement(Vec3.ZERO);
		p.fallDistance = 0;
		if (r.age % 4 == 0) {
			level.sendParticles(ParticleTypes.END_ROD, x, y + 0.2, z, 3, 0.5, 0.1, 0.5, 0.01);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y + 1.0, z, 2, 0.6, 0.6, 0.6, 0);
		}
		if (r.age % 10 == 0) level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6f, 0.8f + 0.9f * eased);
		if (r.age < r.length) return;
		RIDES.remove(p.getUUID());
		// Off the beam, onto the floor beside it: nothing to trigger the lift again by accident.
		double exitZ = -274.2;
		p.connection.teleport(CX, r.down ? BOTTOM : TOP, exitZ, 0, p.getXRot());
		p.fallDistance = 0;
		level.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.8f, 1.4f);
	}

	/** The shaft's light: slow green motes rising along its four corners and a ring that climbs. */
	private static void shaftGlow(ServerLevel level, long now) {
		boolean near = false;
		for (ServerPlayer p : level.players()) if (p.distanceToSqr(CX, (TOP + BOTTOM) / 2, CZ) < 28 * 28) { near = true; break; }
		if (!near) return;
		double sweep = BOTTOM + ((now * 0.35) % (TOP - BOTTOM));
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4 + now * 0.02;
			level.sendParticles(ParticleTypes.END_ROD, CX + Math.cos(a) * 1.5, sweep, CZ + Math.sin(a) * 1.5, 1, 0, 0.02, 0, 0);
		}
		if (now % 12 == 0) {
			double y = BOTTOM + level.getRandom().nextDouble() * (TOP - BOTTOM);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, CX + (level.getRandom().nextDouble() - 0.5) * 3, y, CZ + (level.getRandom().nextDouble() - 0.5) * 3, 1, 0, 0.05, 0, 0);
		}
	}

	// ------------------------------------------------------------------ the generator's beacon

	private static void generator(ServerLevel level, long now) {
		Vec3 core = Vec3.atCenterOf(LatveriaPlan.GENERATOR);
		boolean off = Latveria.generatorOff(level.getServer());
		boolean near = false, vault = false;
		for (ServerPlayer p : level.players()) {
			double d = p.distanceToSqr(core);
			if (d < 28 * 28) near = true;
			boolean inVault = p.getY() < F - 3;
			if (inVault) vault = true;
			if (inVault && d < 9 * 9 && !off && now % 20 == 0) {
				boolean disruptor = LatveriaQuests.hasItem(p, ModItems.GENERATOR_DISRUPTOR);
				p.sendSystemMessage(Component.translatable(disruptor ? "msg.infinity_gauntlet.latveria.generator.hint_ready"
						: "msg.infinity_gauntlet.latveria.generator.hint_need"), true);
			}
		}
		// The label and the sparks belong to the vault: not to the throne room above it, and not once the generator is dead.
		if (off || !vault) { removeLabel(level, core.add(0, 6.2, 0)); if (off) return; }
		if (!near || !vault) return;
		if (!off) {
			// Sparks pour off the core and a bright ring marks it as something to use.
			for (int i = 0; i < 10; i++) {
				double a = now * 0.07 + i * Math.PI / 5;
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, core.x + Math.cos(a) * 4.8, core.y + 1 + Math.sin(now * 0.05 + i) * 2.5, core.z + Math.sin(a) * 4.8, 1, 0.1, 0.1, 0.1, 0.02);
			}
			level.sendParticles(ParticleTypes.END_ROD, core.x, core.y + 5.5, core.z, 2, 0.6, 0.3, 0.6, 0.01);
		}
		label(level, core.add(0, 6.2, 0), false);
	}

	private static void removeLabel(ServerLevel level, Vec3 at) {
		for (ArmorStand s : level.getEntitiesOfClass(ArmorStand.class, new AABB(BlockPos.containing(at)).inflate(3), e -> e.entityTags().contains(LABEL_TAG))) s.discard();
	}

	/** The floating name over the generator, made once and re-worded when the generator dies. */
	private static void label(ServerLevel level, Vec3 at, boolean off) {
		var stands = level.getEntitiesOfClass(ArmorStand.class, new AABB(BlockPos.containing(at)).inflate(3), s -> s.entityTags().contains(LABEL_TAG));
		ArmorStand stand = stands.isEmpty() ? null : stands.get(0);
		for (int i = 1; i < stands.size(); i++) stands.get(i).discard();
		if (stand == null) {
			stand = new ArmorStand(level, at.x, at.y, at.z);
			stand.setInvisible(true);
			stand.setNoGravity(true);
			stand.setInvulnerable(true);
			stand.setSilent(true);
			stand.addTag(LABEL_TAG);
			stand.setCustomNameVisible(true);
			level.addFreshEntity(stand);
		}
		Component name = Component.translatable(off ? "msg.infinity_gauntlet.latveria.generator.label_off" : "msg.infinity_gauntlet.latveria.generator.label");
		if (!name.equals(stand.getCustomName())) stand.setCustomName(name);
	}
}
