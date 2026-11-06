package com.dantemorrigan.gauntlet.boss;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class LivingTribunal extends Monster {
	public static final int ARRIVAL = 0, BATTLE = 1, VERDICT = 2, DEPARTURE = 3;
	public static final int INTRO_TICKS = 130, VERDICT_TICKS = 160, SHATTER_TICK = 105, WINDUP = 34;
	private static final EntityDataAccessor<Integer> CHALLENGER = SynchedEntityData.defineId(LivingTribunal.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(LivingTribunal.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(LivingTribunal.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(LivingTribunal.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Vector3fc> MARK = SynchedEntityData.defineId(LivingTribunal.class, EntityDataSerializers.VECTOR3);
	private final net.minecraft.server.level.ServerBossEvent bar = new net.minecraft.server.level.ServerBossEvent(
			java.util.UUID.randomUUID(), net.minecraft.network.chat.Component.translatable("entity.infinity_gauntlet.living_tribunal"),
			net.minecraft.world.BossEvent.BossBarColor.PURPLE, net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_12);
	int attackCycle;
	int nextAttack = 18;
	long exposedUntil;

	public LivingTribunal(EntityType<? extends LivingTribunal> type, Level level) {
		super(type, level);
		setNoGravity(true);
		noPhysics = true;
		setPersistenceRequired();
		setNoAi(true);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 1024)
				.add(Attributes.MOVEMENT_SPEED, 0).add(Attributes.KNOCKBACK_RESISTANCE, 1);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(CHALLENGER, -1).define(PHASE, ARRIVAL).define(AGE, 0).define(ATTACK, 0).define(MARK, new Vector3f());
	}

	public int challenger() { return entityData.get(CHALLENGER); }
	void challenger(int id) { entityData.set(CHALLENGER, id); }
	public int phase() { return entityData.get(PHASE); }
	public int sceneAge() { return entityData.get(AGE); }
	public int attack() { return entityData.get(ATTACK); }
	public Vec3 mark() { return new Vec3(entityData.get(MARK)); }
	void phase(int value) { entityData.set(PHASE, value); age(0); attack(0, Vec3.ZERO); }
	void age(int value) { entityData.set(AGE, value); }
	void attack(int value, Vec3 mark) {
		entityData.set(ATTACK, value);
		entityData.set(MARK, mark.toVector3f());
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		bar.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		bar.removePlayer(player);
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel) bar.setProgress(getHealth() / getMaxHealth());
		setDeltaMovement(Vec3.ZERO);
	}

	@Override public void remove(RemovalReason reason) { bar.removeAllPlayers(); super.remove(reason); }
	@Override public boolean removeWhenFarAway(double distance) { return false; }
	@Override public boolean isPushable() { return false; }
	@Override public boolean canUsePortal(boolean passengers) { return false; }

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (phase() != BATTLE || !(source.getEntity() instanceof ServerPlayer player)
				|| !TribunalEncounter.isChallenger(this, player) || !(amount > 0) || !Float.isFinite(amount)) return false;
		float resistance = level.getGameTime() < exposedUntil ? .72f : .14f;
		return super.hurtServer(level, source, Math.min(amount, 85) * resistance);
	}

	@Override
	public void die(DamageSource source) {
		if (dead) return;
		super.die(source);
		if (level() instanceof ServerLevel && dead) TribunalEncounter.defeated(this);
	}

	@Override
	protected void tickDeath() {
		if (++deathTime >= 55 && level() instanceof ServerLevel) discard();
	}
}
