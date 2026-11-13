package com.dantemorrigan.gauntlet.doom;

import com.dantemorrigan.gauntlet.ModParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Bolts and blows shared by Doom, the Doombots and the armor. A bolt is not an entity: the server moves a point,
 * draws the pack's sprite on it every tick and checks what it touches, so nothing is left behind after a restart.
 */
public final class DoomCombat {
	private static final class Bolt {
		ServerLevel level;
		Vec3 pos, vel;
		UUID target;
		double turn, radius, blast;
		float damage;
		int life;
		ParticleOptions trail;
		double size;
		Entity owner;
		Predicate<LivingEntity> hits;
	}

	private static final List<Bolt> BOLTS = new ArrayList<>();
	/** Players who just blinked with the Space Stone: Doom's attacks pass through them until this game time. */
	private static final Map<UUID, Long> DODGE = new HashMap<>();

	public static void clear() {
		BOLTS.clear();
		DODGE.clear();
	}

	/**
	 * @param turn   how hard it steers at {@code target} each tick (0 = flies straight)
	 * @param blast  radius hurt around the point of impact (0 = only what it touched)
	 */
	public static void fire(ServerLevel level, Entity owner, Vec3 from, Vec3 velocity, float damage, double radius, double blast,
			int life, LivingEntity target, double turn, ParticleOptions trail, double size, Predicate<LivingEntity> hits) {
		Bolt b = new Bolt();
		b.level = level; b.owner = owner; b.pos = from; b.vel = velocity; b.damage = damage; b.radius = radius; b.blast = blast;
		b.life = life; b.target = target == null ? null : target.getUUID(); b.turn = turn; b.trail = trail; b.size = size; b.hits = hits;
		BOLTS.add(b);
	}

	public static void tick() {
		for (Iterator<Bolt> it = BOLTS.iterator(); it.hasNext();) {
			Bolt b = it.next();
			if (--b.life < 0 || b.owner == null || b.owner.isRemoved() || b.owner.level() != b.level) { it.remove(); continue; }
			if (b.target != null && b.turn > 0 && b.level.getEntity(b.target) instanceof LivingEntity t && t.isAlive()) {
				Vec3 want = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(b.pos).normalize().scale(b.vel.length());
				b.vel = b.vel.lerp(want, b.turn);
			}
			Vec3 next = b.pos.add(b.vel);
			BlockHitResult wall = b.level.clip(new ClipContext(b.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, b.owner));
			boolean stopped = wall.getType() != HitResult.Type.MISS;
			if (stopped) next = wall.getLocation();
			LivingEntity struck = null;
			AABB path = new AABB(b.pos, next).inflate(b.radius);
			for (LivingEntity e : b.level.getEntitiesOfClass(LivingEntity.class, path, e -> e != b.owner && e.isAlive() && b.hits.test(e))) {
				if (e.getBoundingBox().inflate(b.radius).clip(b.pos, next).isPresent() || e.getBoundingBox().inflate(b.radius).contains(next)) { struck = e; break; }
			}
			b.pos = next;
			DoomFx.bill(b.level, b.trail, b.pos, b.size);
			if (struck == null && !stopped) continue;
			it.remove();
			DoomFx.bill(b.level, ModParticles.DOOM_IMPACT, b.pos, Math.max(1, b.blast * 0.8));
			DoomFx.sound(b.level, b.pos, SoundEvents.FIREWORK_ROCKET_BLAST, 0.8f, 0.7f);
			if (struck != null) hurt(b.level, struck, b.owner, b.damage);
			if (b.blast > 0) {
				for (LivingEntity e : b.level.getEntitiesOfClass(LivingEntity.class, new AABB(b.pos, b.pos).inflate(b.blast),
						e -> e != b.owner && e != null && e.isAlive() && b.hits.test(e))) {
					if (e != struck && e.position().add(0, e.getBbHeight() * 0.5, 0).distanceTo(b.pos) <= b.blast + 0.6) hurt(b.level, e, b.owner, b.damage * 0.7f);
				}
			}
		}
	}

	/** After a Space Stone blink the player slips through Doom's attacks for a second. */
	public static void dodge(ServerPlayer player) {
		DODGE.put(player.getUUID(), player.level().getGameTime() + 20);
	}

	public static boolean dodging(LivingEntity e) {
		Long until = DODGE.get(e.getUUID());
		return until != null && e.level().getGameTime() <= until;
	}

	/** One blow from Doom's side or from the armor. Returns false if it was dodged or refused. */
	public static boolean hurt(ServerLevel level, LivingEntity victim, Entity owner, float amount) {
		if (victim instanceof Player && dodging(victim)) {
			DoomFx.bill(level, ModParticles.DOOM_RUNE_MOTE, victim.position().add(0, 1, 0), 2);
			return false;
		}
		DamageSource source = owner instanceof Player p ? level.damageSources().indirectMagic(p, p)
				: owner instanceof LivingEntity l ? level.damageSources().mobAttack(l) : level.damageSources().magic();
		return victim.hurtServer(level, source, amount);
	}

	private DoomCombat() {}
}
