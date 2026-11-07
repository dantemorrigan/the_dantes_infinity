package com.dantemorrigan.gauntlet.boss;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.stormbreaker.Stormbreaker;
import com.dantemorrigan.gauntlet.uru.UruArmor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Thanos, the Mad Titan: the final boss. The encounter ({@link ThanosEncounter}) scripts his arrival and exit; once the
 * fight begins he runs his own state machine: double-sword swings, a gauntlet backhand that hurls the player away, a
 * high leap that ends in a ground slam, and a boomerang sword throw. Below half health he is enraged (faster, shorter
 * pauses, heavier blows, damage resistance and two ultimate attacks). Defeat: he falls to one knee, speaks, and turns to dust; then he leaves his empty gauntlet and experience.
 *
 * <p>He shrugs off ordinary weapons, while Stormbreaker, diamond and netherite swords, explosions and Infinity Stones
 * can deal meaningful damage. His blows ignore most of ordinary armor; only a full Uru set really holds them.
 */
public class Thanos extends Monster {
	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, InfinityGauntlet.id("thanos"));

	// Actions (synced; the client plays the matching animation from the moment the value changes).
	public static final int A_IDLE = 0, A_DESCEND = 1, A_RIFT = 2, A_INTRO = 3, A_SWING = 4, A_THROW = 5, A_LEAP = 6,
			A_SLAM = 7, A_BACKHAND = 8, A_STAGGER = 9, A_KNEEL = 10, A_LEAVE = 11, A_ROAR = 12,
			A_RAGE_QUAKE = 13, A_RAGE_BARRAGE = 14;
	public static final int FLAG_GAUNTLET = 1, FLAG_SWORD_OUT = 2, FLAG_ENRAGED = 4, FLAG_FIGHT = 8;
	/** Ticks of the defeat: kneel and speak, then turn to dust. */
	public static final int DEATH_TICKS = 230, DUST_START = 150;
	public static final int QUAKE_WINDUP = 28, BARRAGE_INTERVAL = 24, BARRAGE_WINDUP = 20, BARRAGE_STRIKES = 3;
	private static final float RAGE_HEALTH = .5f, RAGE_DAMAGE = 1.35f, RAGE_TAKEN = .6f;
	private static final int RAGE_COOLDOWN = 160, QUAKE_TRAVEL = 14;
	private static final double QUAKE_RADIUS = 14, BARRAGE_RADIUS = 4.5;
	private static final DustParticleOptions RAGE_DUST = new DustParticleOptions(0xFF7837, 1.8f);

	private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(Thanos.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> FLAGS = SynchedEntityData.defineId(Thanos.class, EntityDataSerializers.INT);

	/** Client: tickCount when the current action began. */
	public int actionStartTick;
	private int actionTicks;
	private int cooldown = 30;
	private UUID targetId;
	private boolean scripted = true;
	private Vec3 leapFrom, leapTo;
	private int leapDuration;
	private final Set<UUID> hitThisAction = new HashSet<>();
	private int unreachableTicks;
	private boolean taunt70, taunt35;
	private int staggerGuard;
	private int nextRageAttack;
	private boolean barrageNext;
	private Vec3 barrageMark;
	/** Scripted movement (descent in the beam, walk out of the rift, rise when leaving). */
	private Vec3 scriptFrom, scriptTo;
	private int scriptTicks, scriptAge;
	private ThanosSword thrown;
	private Runnable onDefeatDone;

	public Thanos(EntityType<? extends Thanos> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.xpReward = 0;
		// He wades through water like on land (a Titan does not stop at a pond).
		this.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER, 0f);
		this.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER_BORDER, 0f);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 600.0)
				.add(Attributes.MOVEMENT_SPEED, 0.31)
				.add(Attributes.ATTACK_DAMAGE, 22.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 96.0)
				.add(Attributes.STEP_HEIGHT, 1.6)
				.add(Attributes.ARMOR, 0.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ACTION, A_IDLE);
		builder.define(FLAGS, 0);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (ACTION.equals(accessor)) this.actionStartTick = this.tickCount;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
	}

	// ------------------------------------------------------------------ synced state

	public int action() { return this.entityData.get(ACTION); }

	public boolean flag(int f) { return (this.entityData.get(FLAGS) & f) != 0; }

	private void setFlag(int f, boolean on) {
		int v = this.entityData.get(FLAGS);
		this.entityData.set(FLAGS, on ? v | f : v & ~f);
	}

	public boolean hasGauntlet() { return flag(FLAG_GAUNTLET); }

	public boolean fighting() { return flag(FLAG_FIGHT); }

	public void setHasGauntlet(boolean v) { setFlag(FLAG_GAUNTLET, v); }

	private void setAction(int a) {
		this.entityData.set(ACTION, a, true);
		this.actionTicks = 0;
		this.hitThisAction.clear();
	}

	public ServerPlayer target() {
		if (targetId == null || !(this.level() instanceof ServerLevel sl)) return null;
		return sl.getServer().getPlayerList().getPlayer(targetId);
	}

	// ------------------------------------------------------------------ script (driven by the encounter)

	/** Glides from {@code from} to {@code to} over {@code ticks}, playing the given action. Invulnerable meanwhile. */
	public void script(int action, Vec3 from, Vec3 to, int ticks) {
		this.scripted = true;
		this.scriptFrom = from;
		this.scriptTo = to;
		this.scriptTicks = Math.max(1, ticks);
		this.scriptAge = 0;
		this.setNoGravity(true);
		this.noPhysics = true;
		this.setInvulnerable(true);
		this.snapTo(from.x, from.y, from.z, this.getYRot(), 0);
		setAction(action);
	}

	public void pose(int action) {
		this.scripted = true;
		this.setInvulnerable(true);
		setAction(action);
	}

	public void faceTowards(Vec3 p) {
		float yaw = (float) (Mth.atan2(p.z - this.getZ(), p.x - this.getX()) * Mth.RAD_TO_DEG) - 90f;
		this.setYRot(yaw);
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
		this.yRotO = yaw;
		this.yBodyRotO = yaw;
		this.yHeadRotO = yaw;
	}

	public void beginFight(ServerPlayer player) {
		this.targetId = player.getUUID();
		this.scripted = false;
		this.scriptFrom = this.scriptTo = null;
		this.setNoGravity(false);
		this.noPhysics = false;
		this.setInvulnerable(false);
		this.setTarget(player);
		setFlag(FLAG_FIGHT, true);
		this.cooldown = 20;
		setAction(A_IDLE);
	}

	public void setTargetPlayer(ServerPlayer player) { this.targetId = player.getUUID(); }

	public void onDefeatDone(Runnable r) { this.onDefeatDone = r; }

	// ------------------------------------------------------------------ ticking

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) return;
		if (scriptFrom != null && scriptTo != null) {
			scriptAge++;
			float k = Mth.clamp(scriptAge / (float) scriptTicks, 0, 1);
			float e = k * k * (3 - 2 * k);
			Vec3 p = scriptFrom.lerp(scriptTo, e);
			this.setDeltaMovement(Vec3.ZERO);
			this.setPos(p.x, p.y, p.z);
			if (scriptAge >= scriptTicks) {
				scriptFrom = scriptTo = null;
				this.setNoGravity(false);
				this.noPhysics = false;
			}
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		actionTicks++;
		if (scripted || this.isDeadOrDying()) return;
		ServerPlayer target = target();
		if (target == null || !target.isAlive() || target.level() != level) {
			this.getNavigation().stop();
			return;
		}
		GauntletConfig cfg = GauntletConfig.get();
		if (this.getHealth() <= this.getMaxHealth() * RAGE_HEALTH && !flag(FLAG_ENRAGED)) {
			setFlag(FLAG_ENRAGED, true);
			if (this.distanceTo(target) <= 30) startRageAttack(level, target);
			else roar();
			return;
		}
		boolean enraged = flag(FLAG_ENRAGED);
		this.setTarget(target);
		this.getLookControl().setLookAt(target, 30f, 30f);

		// Mid-fight taunts (the line plays over the fight, he keeps attacking).
		float hp = this.getHealth() / this.getMaxHealth();
		if (!taunt70 && hp < 0.7f) { taunt70 = true; ThanosEncounter.line(this, ThanosEncounter.LINE_UNIVERSE); }
		if (!taunt35 && hp < 0.35f && action() == A_IDLE) { taunt35 = true; ThanosEncounter.line(this, ThanosEncounter.LINE_DIE); roar(); return; }

		int action = action();
		if (action == A_RAGE_QUAKE || action == A_RAGE_BARRAGE) {
			this.setDeltaMovement(0, Math.min(0, this.getDeltaMovement().y), 0);
		}
		switch (action) {
			case A_SWING -> tickSwing(level, target, cfg);
			case A_BACKHAND -> tickBackhand(level, target, cfg);
			case A_THROW -> tickThrow(level, target);
			case A_LEAP -> tickLeap(level, target);
			case A_SLAM -> { if (actionTicks >= 22) setAction(A_IDLE); }
			case A_STAGGER -> { if (actionTicks >= 12) setAction(A_IDLE); }
			case A_ROAR -> { this.getNavigation().stop(); if (actionTicks >= 36) setAction(A_IDLE); }
			case A_RAGE_QUAKE -> tickQuake(level, cfg);
			case A_RAGE_BARRAGE -> tickBarrage(level, target, cfg);
			default -> tickChase(level, target, enraged);
		}
		if (staggerGuard > 0) staggerGuard--;
	}

	private void roar() {
		setAction(A_ROAR);
		this.getNavigation().stop();
		if (this.level() instanceof ServerLevel sl) {
			sl.playSound(null, this.getX(), this.getY() + 2, this.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3f, 0.55f);
			sl.sendParticles(ModParticles.THANOS_SLAM_WAVE, this.getX(), this.getY() + 0.1, this.getZ(), 0, 0, 1.6, 0, 1);
		}
	}

	/** Water never holds him: he walks along the bottom toward the target and heaves himself out at the edge. */
	private void wade(ServerPlayer target) {
		if (!this.isInWater()) { stuckInWater = 0; return; }
		Vec3 to = horizontal(target.position().subtract(this.position()));
		Vec3 v = this.getDeltaMovement();
		boolean deep = this.isEyeInFluid(net.minecraft.tags.FluidTags.WATER);
		stuckInWater++;
		double up = deep ? 0.16 : this.horizontalCollision ? 0.5 : 0.1;
		// Held in the water for long (a pit, a steep bank): a great leap out.
		if (stuckInWater > 60) up = 0.9;
		this.setDeltaMovement(v.x * 0.75 + to.x * 0.12, Math.max(v.y, up), v.z * 0.75 + to.z * 0.12);
		this.hurtMarked = true;
		if (stuckInWater > 60) stuckInWater = 0;
	}

	private int stuckInWater;

	private void tickChase(ServerLevel level, ServerPlayer target, boolean enraged) {
		wade(target);
		double d = this.distanceTo(target);
		double dy = target.getY() - this.getY();
		if (cooldown > 0) cooldown--;
		boolean swordHome = !flag(FLAG_SWORD_OUT);
		boolean stuck = this.getNavigation().isDone() && d > 4.5;
		unreachableTicks = (stuck || Math.abs(dy) > 3.5 || !target.onGround() && dy > 2) ? unreachableTicks + 1 : Math.max(0, unreachableTicks - 2);

		if (enraged && tickCount >= nextRageAttack && d <= 30) {
			startRageAttack(level, target);
			return;
		}
		if (cooldown <= 0) {
			int pause = enraged ? 14 : 22;
			if (d <= 3.6) {
				if (swordHome && this.random.nextFloat() < 0.62f) { setAction(A_SWING); cooldown = pause; return; }
				setAction(A_BACKHAND);
				cooldown = pause;
				return;
			}
			if (d <= 30 && (unreachableTicks > 30 || d > 7) && this.random.nextFloat() < (enraged ? 0.08f : 0.05f) + unreachableTicks * 0.004f) {
				if (swordHome && (this.random.nextBoolean() || !target.onGround()) && this.hasLineOfSight(target)) {
					setAction(A_THROW);
				} else {
					startLeap(target);
				}
				cooldown = pause + 10;
				unreachableTicks = 0;
				return;
			}
		}
		if (d > 2.4) {
			if (this.tickCount % 5 == 0) this.getNavigation().moveTo(target, enraged ? 1.25 : 1.0);
		} else {
			this.getNavigation().stop();
		}
		// Heavy footsteps.
		if (this.walkAnimation.isMoving() && this.tickCount % 9 == 0) {
			level.playSound(null, this.blockPosition(), SoundEvents.IRON_GOLEM_STEP, SoundSource.HOSTILE, 1.4f, 0.55f);
		}
	}

	private void startRageAttack(ServerLevel level, ServerPlayer target) {
		boolean barrage = barrageNext || !this.onGround() || this.distanceTo(target) > QUAKE_RADIUS;
		setAction(barrage ? A_RAGE_BARRAGE : A_RAGE_QUAKE);
		barrageNext = !barrage;
		barrageMark = null;
		nextRageAttack = tickCount + RAGE_COOLDOWN;
		this.getNavigation().stop();
		faceTowards(target.position());
		level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3f, .53f);
	}

	private void tickQuake(ServerLevel level, GauntletConfig cfg) {
		Vec3 center = this.position();
		if (actionTicks < QUAKE_WINDUP) {
			if (actionTicks % 7 == 0) rageRing(level, center, QUAKE_RADIUS);
			return;
		}
		int travel = actionTicks - QUAKE_WINDUP;
		if (travel >= QUAKE_TRAVEL) {
			if (travel >= QUAKE_TRAVEL + 18) finishRageAttack();
			return;
		}
		if (travel == 0) rageImpact(level, center, 6);
		double inner = travel * QUAKE_RADIUS / QUAKE_TRAVEL;
		double outer = inner + QUAKE_RADIUS / QUAKE_TRAVEL;
		if (travel % 2 == 0) rageRing(level, center, outer);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
				new AABB(center, center).inflate(outer, 3, outer), e -> e != this && e.isAlive())) {
			Vec3 offset = victim.position().subtract(center);
			double distance = offset.horizontalDistance();
			if (distance > outer || distance < inner - 1 || offset.y > 1.15 || offset.y < -2.5
					|| hitThisAction.contains(victim.getUUID()) || !this.hasLineOfSight(victim)) continue;
			hit(level, victim, cfg.thanosSlamDamage * 1.6f, horizontal(offset).scale(2.1).add(0, .85, 0), false);
		}
	}

	private void tickBarrage(ServerLevel level, ServerPlayer target, GauntletConfig cfg) {
		int strike = (actionTicks - 1) / BARRAGE_INTERVAL;
		int beat = (actionTicks - 1) % BARRAGE_INTERVAL;
		if (strike >= BARRAGE_STRIKES) {
			if (actionTicks >= BARRAGE_STRIKES * BARRAGE_INTERVAL + 14) finishRageAttack();
			return;
		}
		if (beat == 0) {
			// Lock the impact point at the warning, so a player can step clear.
			barrageMark = target.position();
			faceTowards(barrageMark);
			level.playSound(null, BlockPos.containing(barrageMark), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.HOSTILE, 2f, .55f);
		}
		if (barrageMark == null) return;
		if (beat < BARRAGE_WINDUP && beat % 5 == 0) rageRing(level, barrageMark, BARRAGE_RADIUS);
		if (beat != BARRAGE_WINDUP) return;
		rageImpact(level, barrageMark, 3.8);
		Vec3 center = barrageMark;
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
				new AABB(center, center).inflate(BARRAGE_RADIUS, 3, BARRAGE_RADIUS), e -> e != this && e.isAlive())) {
			Vec3 offset = victim.position().subtract(center);
			if (offset.lengthSqr() <= BARRAGE_RADIUS * BARRAGE_RADIUS) {
				hit(level, victim, cfg.thanosSlamDamage * 1.35f, horizontal(offset).scale(1.3).add(0, .55, 0), false);
			}
		}
	}

	private void finishRageAttack() {
		setAction(A_IDLE);
		barrageMark = null;
		cooldown = Math.max(cooldown, 14);
	}

	private static void rageRing(ServerLevel level, Vec3 center, double radius) {
		int points = radius >= 8 ? 24 : 14;
		for (int i = 0; i < points; i++) {
			double angle = i * Math.TAU / points;
			level.sendParticles(RAGE_DUST, center.x + Math.cos(angle) * radius, center.y + .18,
					center.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
		}
	}

	private static void rageImpact(ServerLevel level, Vec3 at, double size) {
		level.sendParticles(ModParticles.THANOS_SLAM_WAVE, at.x, at.y + .12, at.z, 0, 0, size, 0, 1);
		level.sendParticles(ModParticles.THANOS_SLAM_DUST, at.x, at.y + .24, at.z, 0, 0, size, 0, 1);
		level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.8f, .55f);
		ThanosEncounter.shake(level, at, 1.15f);
	}

	private void tickSwing(ServerLevel level, ServerPlayer target, GauntletConfig cfg) {
		this.getNavigation().stop();
		if (actionTicks < 8) faceTowards(target.position());
		if (actionTicks == 6) level.playSound(null, this.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2f, 0.5f);
		if (actionTicks == 10) {
			Vec3 fwd = forward();
			Vec3 arc = this.position().add(0, 1.7, 0).add(fwd.scale(2.0));
			level.sendParticles(ModParticles.THANOS_SWORD_ARC, arc.x, arc.y, arc.z, 0, 1.0, 0, 0, 1);
			for (LivingEntity e : victims(level, 4.8, 80)) {
				Vec3 push = horizontal(e.position().subtract(this.position())).scale(1.3).add(0, 0.45, 0);
				if (hit(level, e, cfg.thanosSwingDamage, push, true)) {
					Vec3 p = e.position().add(0, e.getBbHeight() * 0.6, 0);
					level.sendParticles(ModParticles.THANOS_SWORD_SPARKS, p.x, p.y, p.z, 0, 1.0, 0, 0, 1);
				}
			}
		}
		if (actionTicks >= 25) {
			// Even an enraged follow-up leaves a brief opening after the first swing.
			if (flag(FLAG_ENRAGED) && this.random.nextFloat() < 0.4f && this.distanceTo(target) < 4.0) setAction(A_SWING);
			else setAction(A_IDLE);
		}
	}

	private void tickBackhand(ServerLevel level, ServerPlayer target, GauntletConfig cfg) {
		this.getNavigation().stop();
		if (actionTicks < 6) faceTowards(target.position());
		if (actionTicks == 7) {
			level.playSound(null, this.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 2f, 0.5f);
			for (LivingEntity e : victims(level, 4.2, 70)) {
				Vec3 push = horizontal(e.position().subtract(this.position())).scale(2.9).add(0, 0.95, 0);
				if (hit(level, e, cfg.thanosBackhandDamage, push, false)) {
					level.playSound(null, e.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0f, 0.6f);
				}
			}
		}
		if (actionTicks >= 20) setAction(A_IDLE);
	}

	private void tickThrow(ServerLevel level, ServerPlayer target) {
		this.getNavigation().stop();
		faceTowards(target.position());
		if (actionTicks == 9 && !flag(FLAG_SWORD_OUT)) {
			ThanosSword sword = new ThanosSword(ModEntities.THANOS_SWORD, level);
			Vec3 hand = this.position().add(0, 2.4, 0).add(forward().scale(0.6));
			Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0);
			sword.launch(this, hand, aim);
			level.addFreshEntity(sword);
			this.thrown = sword;
			setFlag(FLAG_SWORD_OUT, true);
			level.playSound(null, this.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.HOSTILE, 2.2f, 0.45f);
		}
		if (actionTicks >= 22) setAction(A_IDLE);
	}

	/** The thrown sword came back to his hand (or was lost). */
	void swordReturned() {
		setFlag(FLAG_SWORD_OUT, false);
		this.thrown = null;
		if (this.level() instanceof ServerLevel sl) {
			sl.playSound(null, this.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.HOSTILE, 1.6f, 0.6f);
		}
	}

	private void startLeap(ServerPlayer target) {
		ServerLevel level = (ServerLevel) this.level();
		// Lead the target a little; land on the ground under that point.
		Vec3 aim = target.position().add(target.getDeltaMovement().multiply(6, 0, 6));
		Vec3 flat = horizontal(aim.subtract(this.position()));
		double dist = Math.min(26, horizontalDistance(aim, this.position()));
		Vec3 land = this.position().add(flat.scale(Math.max(0, dist - 1.2)));
		double y = target.onGround() ? target.getY() : groundY(level, land, target.getY());
		this.leapFrom = this.position();
		this.leapTo = new Vec3(land.x, y, land.z);
		this.leapDuration = Mth.clamp((int) (12 + dist * 0.7), 14, 30);
		setAction(A_LEAP);
		this.getNavigation().stop();
	}

	private static double groundY(ServerLevel level, Vec3 at, double fallback) {
		BlockPos p = BlockPos.containing(at.x, fallback + 4, at.z);
		for (int i = 0; i < 24; i++, p = p.below()) {
			if (!level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty() && level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return p.getY();
		}
		if (level.dimension() == Level.OVERWORLD) return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at.x), (int) Math.floor(at.z));
		return fallback;
	}

	private void tickLeap(ServerLevel level, ServerPlayer target) {
		int windup = 8;
		if (actionTicks < windup) {
			faceTowards(leapTo);
			this.setDeltaMovement(Vec3.ZERO);
			return;
		}
		if (actionTicks == windup) {
			level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.4f, 0.8f);
			level.sendParticles(ModParticles.THANOS_SLAM_DUST, this.getX(), this.getY() + 0.2, this.getZ(), 0, 0.8, 0, 0, 1);
		}
		int t = actionTicks - windup;
		float k = Math.min(1f, (t + 1) / (float) leapDuration);
		double apex = 7 + horizontalDistance(leapFrom, leapTo) * 0.25;
		Vec3 next = leapFrom.lerp(leapTo, k).add(0, 4 * apex * k * (1 - k), 0);
		this.setDeltaMovement(next.subtract(this.position()));
		this.hurtMarked = true;
		this.fallDistance = 0;
		if (k > 0.5f) faceTowards(leapTo);
		boolean landed = k >= 1f || (k > 0.6f && this.onGround());
		if (landed) slam(level);
	}

	private void slam(ServerLevel level) {
		GauntletConfig cfg = GauntletConfig.get();
		this.setDeltaMovement(Vec3.ZERO);
		setAction(A_SLAM);
		Vec3 c = this.position();
		level.sendParticles(ModParticles.THANOS_SLAM_WAVE, c.x, c.y + 0.15, c.z, 0, 0, 2.2, 0, 1);
		level.sendParticles(ModParticles.THANOS_SLAM_DUST, c.x, c.y + 0.3, c.z, 0, 2.2, 0, 0, 1);
		var below = level.getBlockState(this.blockPosition().below());
		if (!below.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), c.x, c.y + 0.2, c.z, 80, 2.5, 0.3, 2.5, 0.3);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.5f, 0.6f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2f, 0.4f);
		ThanosEncounter.shake(level, c, 0.9f);
		double r = 6.5;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r, 3, r), e -> e != this && e.isAlive())) {
			double d = e.position().distanceTo(c);
			if (d > r) continue;
			float falloff = (float) (1 - d / r * 0.6);
			Vec3 push = horizontal(e.position().subtract(c)).scale(1.5 * falloff).add(0, 0.85 * falloff, 0);
			hit(level, e, cfg.thanosSlamDamage * falloff, push, false);
		}
	}

	// ------------------------------------------------------------------ hitting

	private Vec3 forward() {
		float yaw = this.getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
	}

	private static Vec3 horizontal(Vec3 v) {
		Vec3 h = new Vec3(v.x, 0, v.z);
		return h.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : h.normalize();
	}

	private static double horizontalDistance(Vec3 a, Vec3 b) {
		return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z));
	}

	private java.util.List<LivingEntity> victims(ServerLevel level, double range, double halfAngle) {
		Vec3 fwd = forward();
		double cos = Math.cos(Math.toRadians(halfAngle));
		return level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(range, 1.5, range), e -> {
			if (e == this || !e.isAlive() || e instanceof Thanos) return false;
			if (hitThisAction.contains(e.getUUID())) return false;
			Vec3 to = e.position().subtract(this.position());
			double d = Math.sqrt(to.x * to.x + to.z * to.z);
			if (d > range + e.getBbWidth() * 0.5) return false;
			return d < 1.2 || horizontal(to).dot(fwd) >= cos;
		});
	}

	public DamageSource damageSource() {
		return new DamageSource(this.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE), this);
	}

	/**
	 * One blow. Ordinary armor softens it by at most {@code thanosArmorShare}; a full Uru set takes only
	 * {@code thanosUruFactor} and its runes flash. A raised shield facing him stops a sword swing, but he knocks it aside.
	 */
	boolean hit(ServerLevel level, LivingEntity e, float base, Vec3 push, boolean blockable) {
		GauntletConfig cfg = GauntletConfig.get();
		hitThisAction.add(e.getUUID());
		if (blockable && e instanceof Player p && p.isBlocking()) {
			Vec3 look = p.getLookAngle();
			Vec3 toMe = this.position().subtract(p.position()).normalize();
			if (look.x * toMe.x + look.z * toMe.z > 0.3) {
				ItemStack shield = p.getUseItem();
				p.stopUsingItem();
				if (!shield.isEmpty()) p.getCooldowns().addCooldown(shield, 120);
				level.playSound(null, p.blockPosition(), SoundEvents.SHIELD_BREAK.value(), SoundSource.PLAYERS, 1.2f, 0.8f);
				e.setDeltaMovement(push.scale(0.6));
				e.hurtMarked = true;
				return false;
			}
		}
		if (flag(FLAG_ENRAGED)) base *= RAGE_DAMAGE;
		float amount;
		boolean uru = UruArmor.fullSet(e);
		if (uru) {
			amount = base * cfg.thanosUruFactor;
		} else {
			amount = base * (1 - Math.min(20, e.getArmorValue()) / 20f * cfg.thanosArmorShare);
		}
		if (!e.hurtServer(level, damageSource(), amount)) return false;
		if (uru) {
			UruArmor.absorbed(level, e, base - amount);
			Vec3 p = e.position().add(0, e.getBbHeight() * 0.6, 0);
			level.sendParticles(ModParticles.THANOS_ARMOR_HIT, p.x, p.y, p.z, 0, 1.0, 0, 0, 1);
		}
		e.setDeltaMovement(push.scale(uru ? 0.7 : 1.0));
		e.hurtMarked = true;
		return true;
	}

	// ------------------------------------------------------------------ taking damage

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.is(DamageTypes.GENERIC_KILL)) return super.hurtServer(level, source, amount); // /kill
		if (!Float.isFinite(amount)) return false; // NaN / infinity must never reach the health value
		if (this.isInvulnerable() || scripted || !fighting() || this.isDeadOrDying()) return false;
		if (source.is(DamageTypes.FALL) || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.IN_WALL)
				|| source.is(DamageTypes.DROWN) || source.is(DamageTypes.CACTUS) || source.is(DamageTypes.FREEZE)
				|| source.is(DamageTypes.SWEET_BERRY_BUSH) || source.is(DamageTypes.HOT_FLOOR) || source.is(DamageTypes.WITHER)) {
			return false;
		}
		boolean stormbreaker = source.is(Stormbreaker.DAMAGE);
		float scaled = rawDamage || stormbreaker ? amount : amount * takenMultiplier(source);
		if (!rawDamage && !stormbreaker && amount >= 1000f) scaled = Math.min(scaled, looseStone(source) ? 90f : 60f);
		// Sharpness, Strength, crits, maces and TNT stack up: an ordinary weapon never takes more than a sliver per hit.
		if (!rawDamage && !stormbreaker) scaled = Math.min(scaled, source.is(DamageTypeTags.IS_EXPLOSION) ? 20f : looseStone(source) ? 10f : 6f);
		float beforeRage = this.getHealth() - this.getMaxHealth() * RAGE_HEALTH;
		if (flag(FLAG_ENRAGED) || beforeRage <= 0) scaled *= RAGE_TAKEN;
		else scaled = Math.min(scaled, beforeRage); // A single burst cannot skip the second phase.
		// The axe already limits hits per swing, wave and throw leg. Let each distinct hit land on the Titan.
		if (stormbreaker) this.invulnerableTime = 0;
		boolean hurt = super.hurtServer(level, source, scaled);
		if (hurt) {
			Vec3 p = this.position().add(0, 1.6, 0);
			level.sendParticles(ModParticles.THANOS_ARMOR_HIT, p.x, p.y, p.z, 0, 0.6, 0, 0, 1);
			if (source.is(DamageTypeTags.IS_EXPLOSION) && scaled >= 16 && staggerGuard == 0 && !this.isDeadOrDying()
					&& action() != A_LEAP && action() != A_ROAR && action() != A_RAGE_QUAKE && action() != A_RAGE_BARRAGE) {
				setAction(A_STAGGER);
				staggerGuard = 80;
			}
		}
		return hurt;
	}

	/** True while {@link #hurtByGauntlet} applies a blow whose size is already final (no sword / stone multiplier on top). */
	private boolean rawDamage;

	/**
	 * A blow of a worn gauntlet (every ability goes through {@code GauntletDamage}): multiplied by the gauntlet's strength
	 * (all six stones: more), and it always lands: his hurt-immunity frames would otherwise swallow quick or repeated hits.
	 */
	public boolean hurtByGauntlet(ServerLevel level, ServerPlayer attacker, float amount, boolean fullGauntlet) {
		GauntletConfig cfg = GauntletConfig.get();
		float base = amount >= 1000f ? 40f : amount;
		return rawBlow(level, attacker, Math.min(220f, base * (fullGauntlet ? cfg.thanosFullGauntletMult : cfg.thanosGauntletMult)));
	}

	/** A slice of the Infinity Beam: already the final size. */
	public boolean hurtByBeam(ServerLevel level, ServerPlayer attacker, float amount) {
		return rawBlow(level, attacker, amount);
	}

	private boolean rawBlow(ServerLevel level, ServerPlayer attacker, float amount) {
		this.invulnerableTime = 0;
		this.rawDamage = true;
		try {
			return this.hurtServer(level, level.damageSources().playerAttack(attacker), amount);
		} finally {
			this.rawDamage = false;
		}
	}

	private float takenMultiplier(DamageSource source) {
		GauntletConfig cfg = GauntletConfig.get();
		if (source.is(DamageTypeTags.IS_EXPLOSION)) return cfg.thanosExplosionTaken;
		if (looseStone(source)) return cfg.thanosLooseStoneMult;
		Entity attacker = source.getEntity();
		boolean melee = source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK);
		if (melee && attacker instanceof LivingEntity l && source.getDirectEntity() == l) {
			ItemStack weapon = l.getMainHandItem();
			if (weapon.is(Items.NETHERITE_SWORD)) return cfg.thanosNetheriteSwordMult;
			if (weapon.is(Items.DIAMOND_SWORD)) return cfg.thanosDiamondSwordMult;
		}
		return cfg.thanosOtherTaken;
	}

	/** The attacker holds a bare Infinity Stone (no gauntlet on): the stones' own power, which he fears. */
	private static boolean looseStone(DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer p)) return false;
		if (!GauntletState.activeGauntlet(p).isEmpty()) return false;
		for (Stone s : Stone.values()) {
			if (s.matches(p.getMainHandItem()) || s.matches(p.getOffhandItem())) return true;
		}
		return false;
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		return false;
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canUsePortal(boolean ignorePassenger) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return null;
	}

	@Override
	protected float getSoundVolume() {
		return 1.6f;
	}

	@Override
	public float getVoicePitch() {
		return 0.55f;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return null;
	}

	// ------------------------------------------------------------------ defeat

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level && this.dead) {
			this.getNavigation().stop();
			if (thrown != null) { thrown.discard(); thrown = null; }
			setFlag(FLAG_SWORD_OUT, false);
			setAction(A_KNEEL);
			if (source.getEntity() instanceof ServerPlayer killer) com.dantemorrigan.gauntlet.ModAdvancements.award(killer, "defeat_thanos");
			for (ServerPlayer p : level.players()) {
				if (p.distanceTo(this) < 128) com.dantemorrigan.gauntlet.ModAdvancements.award(p, "defeat_thanos");
			}
			ThanosEncounter.defeated(this);
		}
	}

	@Override
	protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
		// Loot comes after the dust (see tickDeath).
	}

	@Override
	protected void tickDeath() {
		this.deathTime++;
		this.setDeltaMovement(0, Math.min(0, this.getDeltaMovement().y), 0);
		if (!(this.level() instanceof ServerLevel level)) return;
		if (deathTime == 4) {
			level.playSound(null, this.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.4f, 0.45f);
			level.sendParticles(ModParticles.THANOS_SLAM_DUST, this.getX(), this.getY() + 0.2, this.getZ(), 0, 1.2, 0, 0, 1);
		}
		if (deathTime == 10) ThanosEncounter.line(this, ThanosEncounter.LINE_DEFEAT_1);
		if (deathTime == 100) ThanosEncounter.line(this, ThanosEncounter.LINE_DEFEAT_2);
		if (deathTime >= DUST_START && deathTime % 3 == 0) {
			float k = (deathTime - DUST_START) / (float) (DEATH_TICKS - DUST_START);
			level.sendParticles(ParticleTypes.ASH, this.getX(), this.getY() + 1.4 + k, this.getZ(), 40, 0.7, 1.0, 0.7, 0.02);
			if (deathTime == DUST_START) level.playSound(null, this.blockPosition(), SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 2f, 0.5f);
			if (deathTime % 12 == 0) level.playSound(null, this.blockPosition(), SoundEvents.SAND_STEP, SoundSource.HOSTILE, 1.5f, 0.6f);
		}
		if (deathTime >= DEATH_TICKS && !this.isRemoved()) {
			if (hasGauntlet()) {
				var item = new net.minecraft.world.entity.item.ItemEntity(level, this.getX(), this.getY() + 1, this.getZ(),
						new ItemStack(com.dantemorrigan.gauntlet.ModItems.GAUNTLET));
				item.setDeltaMovement(0, 0.25, 0);
				item.setUnlimitedLifetime();
				item.setGlowingTag(true);
				level.addFreshEntity(item);
			}
			net.minecraft.world.entity.ExperienceOrb.award(level, this.position().add(0, 1, 0), GauntletConfig.get().thanosExperience);
			level.playSound(null, this.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 2f, 0.6f);
			this.remove(RemovalReason.KILLED);
			if (onDefeatDone != null) onDefeatDone.run();
		}
	}

	// The encounter lives in memory only: a Thanos is never saved (a restart simply lets him come again).
	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
	}
}
