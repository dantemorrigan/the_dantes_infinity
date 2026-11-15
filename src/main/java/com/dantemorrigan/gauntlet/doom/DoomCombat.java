package com.dantemorrigan.gauntlet.doom;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.boss.LivingTribunal;
import com.dantemorrigan.gauntlet.boss.Loki;
import com.dantemorrigan.gauntlet.boss.Thanos;
import com.dantemorrigan.gauntlet.power.CombatTargets;
import com.dantemorrigan.gauntlet.power.CosmicGauntlet;
import com.dantemorrigan.gauntlet.cosmos.CosmosTravel;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
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
	public static final ResourceKey<DamageType> ARMOR_DAMAGE =
			ResourceKey.create(Registries.DAMAGE_TYPE, InfinityGauntlet.id("doom_armor"));
	private static final float BOSS_ARMOR_MULTIPLIER = 1.5f;
	private static final float BOSS_ARMOR_CAP = 52f;

	private static final class Bolt {
		ServerLevel level;
		Vec3 pos, vel;
		UUID target;
		double turn, radius, blast;
		float damage;
		int life;
		int id, style;
		float size;
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
	/**
	 * A bolt: the client is told once and flies it itself (same steering), the server moves the real one and decides
	 * what it hits. {@code style} is a {@link DoomVfx} colour, {@code size} the glow's radius in blocks.
	 */
	public static void fire(ServerLevel level, Entity owner, Vec3 from, Vec3 velocity, float damage, double radius, double blast,
			int life, LivingEntity target, double turn, int style, float size, Predicate<LivingEntity> hits) {
		Bolt b = new Bolt();
		b.level = level; b.owner = owner; b.pos = from; b.vel = velocity; b.damage = damage; b.radius = radius; b.blast = blast;
		b.life = life; b.target = target == null ? null : target.getUUID(); b.turn = turn; b.style = style; b.size = size; b.hits = hits;
		b.id = DoomVfx.nextId();
		BOLTS.add(b);
		DoomVfx.bolt(level, b.id, owner, target, from, velocity, size, (float) turn, life, style);
	}

	public static void tick() {
		for (Iterator<Bolt> it = BOLTS.iterator(); it.hasNext();) {
			Bolt b = it.next();
			if (--b.life < 0 || b.owner == null || b.owner.isRemoved() || b.owner.level() != b.level) {
				it.remove();
				DoomVfx.boltEnd(b.level, b.id, b.pos, 0, b.style);
				continue;
			}
			if (b.target != null && b.turn > 0 && b.level.getEntity(b.target) instanceof LivingEntity t && t.isAlive()) {
				Vec3 want = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(b.pos).normalize().scale(b.vel.length());
				b.vel = b.vel.lerp(want, b.turn);
			}
			Vec3 next = b.pos.add(b.vel);
			ServerPlayer pilot = b.owner instanceof ServerPlayer p && CosmosTravel.inCosmos(p) ? p : null;
			boolean cosmicWall = pilot != null && !CosmicGauntlet.clearPath(b.pos, next, 0);
			if (cosmicWall) {
				it.remove();
				DoomVfx.boltEnd(b.level, b.id, b.pos, (float) Math.max(1.2, b.blast), b.style);
				CosmicGauntlet.fx(pilot, 0, b.pos, b.pos, (float) (1.5 + b.blast), 0xffffb752, 14);
				continue;
			}
			BlockHitResult wall = b.level.clip(new ClipContext(b.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, b.owner));
			boolean stopped = wall.getType() != HitResult.Type.MISS;
			if (stopped) next = wall.getLocation();
			LivingEntity struck = null;
			AABB path = new AABB(b.pos, next).inflate(b.radius);
			List<LivingEntity> targets = b.owner instanceof ServerPlayer player
					? CombatTargets.inArea(player, path)
					: b.level.getEntitiesOfClass(LivingEntity.class, path, e -> e != b.owner && e.isAlive());
			double closest = Double.POSITIVE_INFINITY;
			for (LivingEntity target : targets) {
				if (!b.hits.test(target)) continue;
				Vec3 contact = CombatTargets.clip(target, b.pos, next, b.radius).orElse(null);
				if (contact == null && target.getBoundingBox().inflate(b.radius).contains(next)) contact = next;
				if (contact == null) continue;
				double distance = contact.distanceToSqr(b.pos);
				if (distance < closest) {
					closest = distance;
					struck = target;
				}
			}
			b.pos = pilot != null && struck != null
					? b.pos.add(b.vel.normalize().scale(Math.sqrt(closest))) : next;
			if (struck == null && !stopped) continue;
			it.remove();
			DoomVfx.boltEnd(b.level, b.id, b.pos, (float) Math.max(1.2, b.blast + 0.6), b.style);
			if (pilot != null) CosmicGauntlet.fx(pilot, 0, b.pos, b.pos, (float) (1.5 + b.blast), 0xffffb752, 14);
			DoomFx.sound(b.level, b.pos, b.blast > 1 ? SoundEvents.GENERIC_EXPLODE.value() : SoundEvents.FIREWORK_ROCKET_BLAST, b.blast > 1 ? 0.7f : 0.9f, b.blast > 1 ? 1.5f : 0.8f);
			if (struck != null && hurt(b.level, struck, b.owner, b.damage)) {
				Vec3 push = b.vel.normalize();
				double strength = .35 + b.blast * .15;
				if (pilot == null) struck.knockback(strength, -push.x, -push.z);
				else if (struck.isPushable()) {
					struck.push(push.scale(strength));
					struck.hurtMarked = true;
				}
			}
			if (b.blast > 0 && pilot != null) {
				for (LivingEntity target : CombatTargets.inArea(pilot, new AABB(b.pos, b.pos).inflate(b.blast))) {
					if (target == struck || !b.hits.test(target) || !CombatTargets.near(target, b.pos, b.blast)) continue;
					Vec3 center = target.getBoundingBox().getCenter();
					Vec3 contact = CombatTargets.impact(target, b.pos, center, 0);
					if (CosmicGauntlet.visible(pilot, b.pos, contact == null ? center : contact)) {
						hurt(b.level, target, b.owner, b.damage * .7f);
					}
				}
			} else if (b.blast > 0) {
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
			DoomVfx.at(level, DoomVfx.RIFT, victim.position().add(0, 1, 0), 0.8f, 8, DoomVfx.GREEN);
			return false;
		}
		if (owner instanceof ServerPlayer p && isBoss(victim)) {
			var type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ARMOR_DAMAGE);
			var source = new DamageSource(type, p);
			float damage = Math.min(amount * BOSS_ARMOR_MULTIPLIER, BOSS_ARMOR_CAP);
			return victim instanceof EnderDragon dragon ? dragon.hurt(level, dragon.head, source, damage)
					: victim.hurtServer(level, source, damage);
		}
		DamageSource source = owner instanceof Player p ? level.damageSources().indirectMagic(p, p)
				: owner instanceof LivingEntity l ? level.damageSources().mobAttack(l) : level.damageSources().magic();
		return victim.hurtServer(level, source, amount);
	}

	private static boolean isBoss(LivingEntity target) {
		return target instanceof Thanos || target instanceof Loki || target instanceof DoctorDoom
				|| target instanceof LivingTribunal || target instanceof EnderDragon || target instanceof WitherBoss;
	}

	private DoomCombat() {}
}
