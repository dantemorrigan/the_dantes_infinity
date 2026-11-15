package com.dantemorrigan.gauntlet.boss;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Thanos's double-bladed sword, thrown like a boomerang: it spins flat toward the target point, bites into the first
 * wall or reaches its range, then flies back to his hand. It hurts whoever it passes, once per flight.
 */
public class ThanosSword extends Entity {
	private Thanos owner;
	static final double AERIAL_SPEED = 2.25;
	private Vec3 aim;
	private boolean aerial;
	private boolean returning;
	private int age;
	private final java.util.Set<java.util.UUID> hitOut = new java.util.HashSet<>(), hitBack = new java.util.HashSet<>();

	public ThanosSword(EntityType<? extends ThanosSword> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	void launch(Thanos owner, Vec3 from, Vec3 target, boolean aerial) {
		this.owner = owner;
		this.aerial = aerial;
		this.setPos(from.x, from.y, from.z);
		Vec3 dir = target.subtract(from);
		double len = Math.max(1, dir.length());
		this.aim = from.add(dir.scale(Math.min(aerial ? 54 : 30, len + 5) / len));
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) return;
		age++;
		if (owner == null || !owner.isAlive() || owner.isDeadOrDying() || age > 120) {
			if (owner != null) owner.swordReturned();
			this.discard();
			return;
		}
		Vec3 pos = this.position();
		Vec3 goal = returning ? owner.position().add(0, 2.2, 0) : aim;
		Vec3 to = goal.subtract(pos);
		double speed = returning ? Math.min(2.2, 0.9 + age * 0.03) : aerial ? AERIAL_SPEED : 1.45;
		if (!returning && (to.length() < speed || age > 40)) {
			returning = true;
			level.playSound(null, BlockPos.containing(pos), SoundEvents.TRIDENT_RETURN, SoundSource.HOSTILE, 2f, 0.5f);
		}
		if (returning && to.length() < 1.6) {
			owner.swordReturned();
			this.discard();
			return;
		}
		Vec3 step = to.length() < 1e-4 ? Vec3.ZERO : to.normalize().scale(Math.min(speed, to.length()));
		Vec3 next = pos.add(step);
		// Outbound, the first solid block stops it: sparks, a clang, then it comes back.
		boolean outbound = !returning;
		var wall = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		if (outbound && wall.getType() != HitResult.Type.MISS) {
			returning = true;
			level.sendParticles(ModParticles.THANOS_SWORD_SPARKS, pos.x, pos.y, pos.z, 0, 0.8, 0, 0, 1);
			level.playSound(null, BlockPos.containing(pos), SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 1.4f, 0.7f);
			next = wall.getLocation();
		}
		this.setDeltaMovement(next.subtract(pos));
		this.setPos(next.x, next.y, next.z);
		if (age % 4 == 0) level.playSound(null, BlockPos.containing(next), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.0f, 1.6f);

		var hits = outbound ? hitOut : hitBack;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos, next).inflate(.9),
				e -> e != owner && e.isAlive() && !(e instanceof Thanos))) {
			var box = e.getBoundingBox().inflate(.85);
			if (!box.contains(pos) && box.clip(pos, next).isEmpty()) continue;
			if (level.clip(new ClipContext(pos, e.getEyePosition(), ClipContext.Block.COLLIDER,
					ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS || !hits.add(e.getUUID())) continue;
			Vec3 push = step.lengthSqr() < 1e-6 ? Vec3.ZERO : new Vec3(step.x, 0, step.z).normalize().scale(0.9).add(0, 0.35, 0);
			if (owner.hit(level, e, GauntletConfig.get().thanosThrowDamage, push, true)) {
				if (e instanceof ServerPlayer player) BossFlight.interrupt(player, .95);
				Vec3 p = e.position().add(0, e.getBbHeight() * 0.6, 0);
				level.sendParticles(ModParticles.THANOS_SWORD_SPARKS, p.x, p.y, p.z, 0, 1.0, 0, 0, 1);
			}
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 128 * 128;
	}

	public boolean returning() { return returning; }

}
