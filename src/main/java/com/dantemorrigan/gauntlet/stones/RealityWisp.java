package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.ModParticles;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;


/**
 * The Reality Stone loose in the red mist: darts around the group and flees from the closest player. A melee hit
 * or a player-fired arrow catches it (see RealityFog). Never saved: the mist ends with the session.
 */
public final class RealityWisp extends Entity {
	private Vec3 goal;
	private int retarget;

	public RealityWisp(EntityType<? extends RealityWisp> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			if (tickCount % 2 == 0) level().addParticle(ModParticles.QUEST_REALITY_STONE_TRAIL, getX(), getY() + 0.25, getZ(), 0, 0, 0);
			return;
		}
		ServerPlayer p = RealityFog.nearestPlayer(this);
		if (p == null) { discard(); return; }
		Vec3 pos = position();
		double toPlayer = pos.distanceTo(p.getEyePosition());
		if (toPlayer < 2.5) {
			// Too close: dart away, a little sideways so it is hard to follow.
			Vec3 away = pos.subtract(p.getEyePosition()).normalize();
			Vec3 side = away.cross(new Vec3(0, 1, 0)).normalize().scale(random.nextBoolean() ? 0.7 : -0.7);
			goal = pos.add(away.scale(4)).add(side.scale(2)).add(0, random.nextDouble() * 2 - 0.5, 0);
			retarget = 10;
		} else if (goal == null || --retarget <= 0 || pos.distanceToSqr(goal) < 1.5) {
			double a = random.nextDouble() * Math.PI * 2, r = 5 + random.nextDouble() * 7;
			goal = p.position().add(Math.cos(a) * r, 1 + random.nextDouble() * 4, Math.sin(a) * r);
			retarget = 20 + random.nextInt(25);
		}
		Vec3 v = getDeltaMovement().scale(0.86).add(goal.subtract(pos).normalize().scale(0.06));
		if (v.length() > 0.3) v = v.normalize().scale(0.3);
		v = v.add(0, Math.sin(tickCount * 0.35) * 0.03, 0);
		setDeltaMovement(v);
		setPos(pos.add(v));
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (isRemoved()) return false;
		if (!(source.getEntity() instanceof ServerPlayer player)) return false;
		Entity direct = source.getDirectEntity();
		if (direct instanceof net.minecraft.world.entity.projectile.Projectile) return RealityFog.caught(this, player, true);
		return (direct == player || direct == null) && RealityFog.caught(this, player, false);
	}

	@Override public boolean isPickable() { return true; }
	/** Generous aim assist: melee and arrows register even when slightly off. */
	@Override public float getPickRadius() { return 0.9f; }
	@Override public boolean isAttackable() { return true; }
	@Override public boolean shouldBeSaved() { return false; }
	@Override public boolean isNoGravity() { return true; }
	@Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
	@Override protected void readAdditionalSaveData(ValueInput input) {}
	@Override protected void addAdditionalSaveData(ValueOutput output) {}
}
