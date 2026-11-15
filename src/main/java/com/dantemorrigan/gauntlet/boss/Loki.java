package com.dantemorrigan.gauntlet.boss;

import com.dantemorrigan.gauntlet.ModAdvancements;
import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.ModSounds;
import com.dantemorrigan.gauntlet.stones.MindArena;
import com.dantemorrigan.gauntlet.stones.MindQuest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.UUID;

public final class Loki extends Monster {
	public static final int INTRO_TICKS = 22;
	public static final int TRANSFORMATION_TICKS = 76;
	public static final int REVEAL_TICK = 39;
	public static final int DEATH_TICKS = 84;
	public static final int REVERT_TICK = 24;
	public static final int DISSOLVE_TICK = 52;
	public enum Attack {
		DAGGER(7, 5), BOLT(15, 8), GROUNDING(16, 8), BITE(10, 9), PAW(17, 12), ROAR(24, 13), BARRAGE(24, 12);

		public final int windup, recovery;
		Attack(int windup, int recovery) {
			this.windup = windup;
			this.recovery = recovery;
		}
	}

	private static final EntityDimensions FENRIR_SIZE = EntityDimensions.scalable(2.45f, 4.0f).withEyeHeight(2.85f);
	private static final double BARRAGE_RADIUS = 4.5;
	private static final float PHASE_THRESHOLD = 0.45f;
	private static final Attack[] ATTACKS = Attack.values();
	public static final int EXPERIENCE = 650;
	private static final int CLONE_LIFETIME = 105;
	private static final EntityDataAccessor<Boolean> ILLUSION = SynchedEntityData.defineId(Loki.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> ENRAGED = SynchedEntityData.defineId(Loki.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> INTRO = SynchedEntityData.defineId(Loki.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> STRIKE = SynchedEntityData.defineId(Loki.class, EntityDataSerializers.INT);

	private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(Loki.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> SHIFT = SynchedEntityData.defineId(Loki.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> WARD = SynchedEntityData.defineId(Loki.class, EntityDataSerializers.INT);

	private final ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("entity.infinity_gauntlet.loki"),
			BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
	private BlockPos arena;
	private UUID illusionOwner;
	private boolean taunted;
	private SoundEvent currentVoice;
	private long voiceEnds;
	private int teleportCooldown = 75;
	private int perchCooldown = 95;
	private int perchUntil;
	private Vec3 perchReturn;
	private int perchShots;
	private int cloneCooldown = 110;
	private int strikeCooldown = 20;
	private int lifetime = CLONE_LIFETIME;
	private int spellCooldown = 20;
	private int wardCooldown = 45;
	private int roarCooldown = 40;
	private int attackCycle;
	private UUID attackTarget;
	private Vec3 aim;
	private float attackYaw;

	public Loki(EntityType<? extends Loki> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 220)
				.add(Attributes.MOVEMENT_SPEED, 0.32).add(Attributes.ATTACK_DAMAGE, 8)
				.add(Attributes.ARMOR, 8).add(Attributes.ARMOR_TOUGHNESS, 4).add(Attributes.FOLLOW_RANGE, 44)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.22).add(Attributes.STEP_HEIGHT, 1);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ILLUSION, false).define(ENRAGED, false).define(INTRO, 0).define(STRIKE, 0).define(ATTACK, 0).define(WARD, 0).define(SHIFT, 0);
	}

	public void startEncounter(BlockPos city) {
		arena = city.immutable();
		entityData.set(INTRO, INTRO_TICKS);
	}

	public void greet(ServerLevel level) {
		playVoice(level, ModSounds.LOKI_GREETING, 46);
	}

	private void playVoice(ServerLevel level, SoundEvent sound, int duration) {
		long now = level.getGameTime();
		if (currentVoice != null && now < voiceEnds) ModSounds.stopNearby(level, position(), currentVoice);
		ModSounds.play(level, position().add(0, 1.55, 0), sound, 3.6f);
		currentVoice = sound;
		voiceEnds = now + duration;
	}

	public boolean isIllusion() { return entityData.get(ILLUSION); }
	public boolean isEnraged() { return entityData.get(ENRAGED); }
	public int introTicks() { return entityData.get(INTRO); }
	public int strikeTicks() { return entityData.get(STRIKE); }
	public Attack attack() { return ATTACKS[entityData.get(ATTACK)]; }
	public int transformationTicks() { return entityData.get(SHIFT); }
	public int wardTicks() { return entityData.get(WARD); }

	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		return isEnraged() ? FENRIR_SIZE : super.getDefaultDimensions(pose);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (ENRAGED.equals(key)) refreshDimensions();
	}

	@Override
	public boolean shouldBeSaved() {
		return !isIllusion() && super.shouldBeSaved();
	}

	@Override
	public boolean canUsePortal(boolean allowPassengers) {
		return false;
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		if (!isIllusion()) bar.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		bar.removePlayer(player);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (arena != null) output.store("LokiArena", BlockPos.CODEC, arena);
		output.putBoolean("LokiEnraged", isEnraged());
		output.putInt("LokiShift", transformationTicks());
		output.putBoolean("LokiTaunted", taunted);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		arena = input.read("LokiArena", BlockPos.CODEC).orElse(null);
		taunted = input.getBooleanOr("LokiTaunted", false);
		if (input.getBooleanOr("LokiEnraged", false)) applyRage();
		entityData.set(SHIFT, Mth.clamp(input.getIntOr("LokiShift", 0), 0, TRANSFORMATION_TICKS));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (arena != null && !isIllusion() && !MindQuest.isCurrentBoss(this)) {
			discard();
			return;
		}
		if (isIllusion() && (--lifetime == 0 || !(level.getEntity(illusionOwner) instanceof Loki owner) || !owner.isAlive())) {
			shatter(level);
			discard();
			return;
		}
		if (!isIllusion()) {
			bar.setProgress(getHealth() / getMaxHealth());
			if (!isEnraged() && transformationTicks() == 0 && getHealth() <= getMaxHealth() * PHASE_THRESHOLD) {
				beginTransformation(level);
			}
			if (wardTicks() > 0) entityData.set(WARD, wardTicks() - 1);
			if (wardCooldown > 0) wardCooldown--;
			if (perchCooldown > 0) perchCooldown--;
			if (transformationTicks() > 0) {
				tickTransformation(level);
				return;
			}
		}

		int intro = introTicks();
		if (intro > 0) {
			entityData.set(INTRO, intro - 1);
			Vec3 motion = getLookAngle().multiply(0.085, 0, 0.085);
			setDeltaMovement(motion.x, getDeltaMovement().y, motion.z);
			return;
		}

		if (strikeTicks() > 0) {
			int time = strikeTicks() - 1;
			entityData.set(STRIKE, time);
			if (attack() == Attack.GROUNDING && time == attack().recovery + 8) {
				if (level.getPlayerByUUID(attackTarget) instanceof ServerPlayer flyer && eligible(flyer)
						&& BossFlight.airborne(flyer) && hasLineOfSight(flyer)) {
					Vec3 lead = flyer.getDeltaMovement().scale(3);
					aim = flyer.position().add(lead.lengthSqr() > 16 ? lead.normalize().scale(4) : lead);
					attackYaw = yawTo(position(), aim);
					setYRot(attackYaw);
					setYBodyRot(attackYaw);
					LokiFx.send(level, LokiFx.Effect.BIND_MARK, aim.add(0, .5, 0), attackYaw);
				}
			}
			if (time == attack().recovery) finishAttack(level);
			return;
		}
		ServerPlayer target = selectTarget(level);
		setTarget(target);
		if (target == null) {
			getNavigation().stop();
			return;
		}
		if (!isIllusion() && !isEnraged() && !taunted && tickCount >= 75 && level.getGameTime() >= voiceEnds) {
			taunted = true;
			playVoice(level, ModSounds.LOKI_TAUNT, 51);
		}
		lookAt(target, 30, 30);
		double distance = distanceToSqr(target);
		boolean sight = hasLineOfSight(target);
		if (strikeCooldown > 0) strikeCooldown--;
		if (spellCooldown > 0) spellCooldown--;
		if (roarCooldown > 0) roarCooldown--;

		if (!isIllusion() && --cloneCooldown <= 0) {
			summonIllusions(level, target);
			cloneCooldown = isEnraged() ? 165 : 125;
		}
		if (sight && !isIllusion() && spellCooldown == 0 && distance <= 48 * 48 && BossFlight.airborne(target)) {
			startAttack(level, target, Attack.GROUNDING);
			spellCooldown = 58;
			return;
		}
		if (!isIllusion() && !isEnraged() && arena != null && tickPerch(level, target, sight)) return;
		if (isEnraged()) {
			if (sight && roarCooldown == 0 && distance < 13 * 13) {
				startAttack(level, target, Attack.ROAR);
				roarCooldown = 115;
			} else if (sight && distance < 8 * 8 && strikeCooldown == 0) {
				startAttack(level, target, attackCycle++ % 2 == 0 ? Attack.BITE : Attack.PAW);
				strikeCooldown = 12;
			} else if (distance > 12 || !sight) {
				if (tickCount % 5 == 0) getNavigation().moveTo(target, 1.3);
			} else {
				getNavigation().stop();
			}
			return;
		}

		if (sight && !isIllusion() && spellCooldown == 0 && distance > 10 && distance < 34 * 34) {
			startAttack(level, target, Attack.BOLT);
			spellCooldown = 29;
			return;
		}
		if (!isIllusion() && --teleportCooldown <= 0 && (distance > 16 || wardTicks() > 0)) {
			Vec3 to = findFlank(level, target, 4.5, 8);
			if (to != null) {
				teleportForAttack(level, target, to);
				teleportCooldown = 86;
				strikeCooldown = Math.max(strikeCooldown, 6);
				return;
			}
			teleportCooldown = 15;
		}

		if (distance <= 7.3 && sight && strikeCooldown == 0) {
			startAttack(level, target, Attack.DAGGER);
			strikeCooldown = isIllusion() ? 22 : 14;
		} else if (distance > 4 || !sight) {
			if (tickCount % 4 == 0) getNavigation().moveTo(target, 1.1);
		} else {
			getNavigation().stop();
		}
	}

	private boolean tickPerch(ServerLevel level, ServerPlayer target, boolean sight) {
		if (perchUntil > 0) {
			if (tickCount >= perchUntil || perchShots == 0 || getY() < arena.getY() + 3) {
				perchUntil = 0;
				perchCooldown = 165;
				Vec3 flank = findFlank(level, target, 2.2, 4.5);
				if (flank == null && safeFooting(level, perchReturn)) flank = perchReturn;
				perchReturn = null;
				if (flank != null) teleportForAttack(level, target, flank);
				strikeCooldown = Math.max(strikeCooldown, 8);
				spellCooldown = Math.max(spellCooldown, 28);
				teleportCooldown = Math.max(teleportCooldown, 28);
				return false;
			}
			getNavigation().stop();
			if (sight && spellCooldown == 0) {
				perchShots--;
				spellCooldown = 12;
				startAttack(level, target, Attack.BARRAGE);
			}
			return true;
		}
		if (perchCooldown > 0 || target.getY() > arena.getY() + 4) return false;
		Vec3 to = findPerch(level, target);
		if (to == null) {
			perchCooldown = 50;
			return false;
		}
		perchReturn = position();
		teleportForAttack(level, target, to);
		perchUntil = tickCount + 105;
		perchShots = 2;
		spellCooldown = 12;
		return true;
	}

	private Vec3 findPerch(ServerLevel level, ServerPlayer target) {
		Vec3 best = null;
		double bestScore = Double.POSITIVE_INFINITY;
		for (int x = -20; x <= 20; x += 2) {
			for (int z = -20; z <= 20; z += 2) {
				BlockPos column = arena.offset(x, 0, z);
				if (!level.hasChunkAt(column)) continue;
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
				Vec3 at = Vec3.atBottomCenterOf(top);
				double height = at.y - target.getY();
				double distance = target.position().distanceTo(at);
				if (height < 3 || height > 13 || distance < 7 || distance > 27 || !insideArena(at)) continue;
				double score = Math.abs(distance - 15) - height * .25;
				if (score >= bestScore || !safeFooting(level, at)) continue;
				if (level.clip(new ClipContext(at.add(0, 1.88, 0), target.getEyePosition(),
						ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) continue;
				bestScore = score;
				best = at;
			}
		}
		return best;
	}

	private void teleportForAttack(ServerLevel level, ServerPlayer target, Vec3 to) {
		getNavigation().stop();
		setDeltaMovement(Vec3.ZERO);
		LokiFx.send(level, LokiFx.Effect.TELEPORT_OUT, position(), getYRot());
		teleportTo(level, to.x, to.y, to.z, Set.of(), yawTo(to, target.position()), 0, false);
		LokiFx.send(level, LokiFx.Effect.TELEPORT_IN, position(), getYRot());
		level.playSound(null, blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1, 1.35f);
	}

	private void startAttack(ServerLevel level, ServerPlayer target, Attack next) {
		getNavigation().stop();
		attackTarget = target.getUUID();
		aim = target.position();
		attackYaw = yawTo(position(), aim);
		setYRot(attackYaw);
		setYBodyRot(attackYaw);
		entityData.set(ATTACK, next.ordinal());
		entityData.set(STRIKE, next.windup + next.recovery);
		switch (next) {
			case BOLT, GROUNDING -> {
				LokiFx.send(level, next == Attack.BOLT ? LokiFx.Effect.SPELL_MARK : LokiFx.Effect.BIND_MARK,
						aim.add(0, next == Attack.BOLT ? .9 : .5, 0), attackYaw);
				level.playSound(null, blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.2f, 1.35f);
			}
			case BARRAGE -> {
				LokiFx.send(level, LokiFx.Effect.BARRAGE_MARK, aim, attackYaw);
				level.playSound(null, blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.6f, .9f);
			}
			case ROAR -> {
				LokiFx.send(level, LokiFx.Effect.ROAR_WARN, position(), attackYaw);
				level.playSound(null, blockPosition(), SoundEvents.RAVAGER_STUNNED, SoundSource.HOSTILE, 3, .6f);
			}
			case PAW -> LokiFx.send(level, LokiFx.Effect.PAW_WARN, position(), attackYaw);
			case BITE, DAGGER -> {
				if (onGround()) {
					Vec3 lunge = aim.subtract(position()).multiply(1, 0, 1).normalize().scale(next == Attack.BITE ? .85 : .43);
					setDeltaMovement(lunge.x, .12, lunge.z);
				}
			}
		}
	}

	private void finishAttack(ServerLevel level) {
		ServerPlayer target = level.getPlayerByUUID(attackTarget) instanceof ServerPlayer player ? player : null;
		if (attack() == Attack.BARRAGE) {
			LokiFx.beam(level, getEyePosition(), aim.add(0, .35, 0));
			LokiFx.send(level, LokiFx.Effect.BARRAGE_IMPACT, aim, attackYaw);
			level.playSound(null, BlockPos.containing(aim), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 2, .75f);
			for (ServerPlayer player : level.players()) {
				Vec3 offset = player.position().subtract(aim);
				if (!eligible(player) || Math.abs(offset.y) > 2.8 || offset.horizontalDistanceSqr() > BARRAGE_RADIUS * BARRAGE_RADIUS) continue;
				if (level.clip(new ClipContext(aim.add(0, .45, 0), player.getEyePosition(),
						ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) continue;
				player.hurtServer(level, level.damageSources().indirectMagic(this, this), 12);
			}
			return;
		}
		if (attack() == Attack.BOLT || attack() == Attack.GROUNDING) {
			boolean grounding = attack() == Attack.GROUNDING;
			Vec3 end = aim.add(0, .75, 0);
			LokiFx.beam(level, getEyePosition(), end);
			if (eligible(target) && target.distanceToSqr(aim) < (grounding ? 36 : 6.25) && hasLineOfSight(target)
					&& (!grounding || BossFlight.airborne(target) && distanceToSqr(target) <= 52 * 52)) {
				if (target.hurtServer(level, level.damageSources().indirectMagic(this, this), grounding ? 10 : 11) && grounding) {
					BossFlight.interrupt(target, .9);
				}
			}
			return;
		}
		if (attack() == Attack.DAGGER) {
			LokiFx.send(level, LokiFx.Effect.DAGGER_SLASH, position().add(getLookAngle().multiply(.65, 0, .65)), attackYaw);
			level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1, 1.35f);
			if (eligible(target) && distanceToSqr(target) < 9 && hasLineOfSight(target)) doHurtTarget(level, target);
			return;
		}

		boolean roar = attack() == Attack.ROAR;
		boolean paw = attack() == Attack.PAW;
		Vec3 forward = Vec3.directionFromRotation(0, attackYaw);
		Vec3 center = position();
		LokiFx.send(level, roar ? LokiFx.Effect.ROAR : paw ? LokiFx.Effect.CLAW : LokiFx.Effect.FANGS,
				roar ? center : center.add(forward.scale(2.3)), attackYaw);
		level.playSound(null, blockPosition(), roar ? SoundEvents.RAVAGER_ROAR : SoundEvents.RAVAGER_ATTACK,
				SoundSource.HOSTILE, roar ? 4 : 2.6f, paw ? .65f : .8f);
		for (ServerPlayer player : level.players()) {
			Vec3 delta = player.position().subtract(center);
			double range = roar ? 12 : paw ? 6.5 : 5.3;
			if (!eligible(player) || delta.lengthSqr() > range * range || !hasLineOfSight(player)
					|| !roar && delta.multiply(1, 0, 1).normalize().dot(forward) < (paw ? .35 : .5)) continue;
			if (roar) {
				player.hurtServer(level, level.damageSources().indirectMagic(this, this), 6);
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 37, 4), this);
				player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 27), this);
			} else if (!player.hurtServer(level, level.damageSources().mobAttack(this), paw ? 18 : 15)) {
				continue;
			} else if (paw) {
				player.knockback(1.25, -delta.x, -delta.z);
				player.hurtMarked = true;
			} else {
				player.addEffect(new MobEffectInstance(MobEffects.WITHER, 55), this);
			}
		}
	}

    @Override
    protected net.minecraft.world.entity.LivingEntity asValidTarget(net.minecraft.world.entity.LivingEntity target) {
        // Mob's vanilla target accessor also discards creative players after custom selection.
        if(target instanceof ServerPlayer player && player.isCreative() && eligible(player))return player;
        return super.asValidTarget(target);
    }

	private boolean eligible(ServerPlayer player) {
		return player != null && player.isAlive() && !player.isSpectator()
				&& (arena == null || insideArena(player.position()));
	}

	private ServerPlayer selectTarget(ServerLevel level) {
		ServerPlayer closest = null;
		double best = 48 * 48;
		for (ServerPlayer player : level.players()) {
			if (!eligible(player)) continue;
			double distance = distanceToSqr(player);
			if (distance < best) {
				closest = player;
				best = distance;
			}
		}
		return closest;
	}

	public boolean isCityEncounter() {
		return arena != null;
	}

	private boolean insideArena(Vec3 at) {
		return MindArena.inside(arena, at);
	}

	private void applyRage() {
		entityData.set(ENRAGED, true);
		getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.42);
		getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(14);
		getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(.8);
		getAttribute(Attributes.STEP_HEIGHT).setBaseValue(1.25);
		bar.setColor(BossEvent.BossBarColor.GREEN);
		bar.setName(Component.translatable("boss.infinity_gauntlet.loki.rage"));
	}

	private void beginTransformation(ServerLevel level) {
		getNavigation().stop();
		setTarget(null);
		setPos(findTransformationSpace(level));
		setDeltaMovement(Vec3.ZERO);
		perchUntil = 0;
		perchReturn = null;
		entityData.set(INTRO, 0);
		entityData.set(STRIKE, 0);
		entityData.set(WARD, 0);
		entityData.set(SHIFT, TRANSFORMATION_TICKS);
		LokiFx.send(level, LokiFx.Effect.TRANSFORM, position(), getYRot());
		playVoice(level, ModSounds.LOKI_TRANSFORMATION, 42);
		level.playSound(null, blockPosition(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.HOSTILE, 2.4f, .57f);
	}

	private void tickTransformation(ServerLevel level) {
		int left = transformationTicks() - 1;
		entityData.set(SHIFT, left);
		setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
		int age = TRANSFORMATION_TICKS - left;
		if (age == REVEAL_TICK && !isEnraged()) {
			applyRage();
			LokiFx.send(level, LokiFx.Effect.TRANSFORM_REVEAL, position(), getYRot());
			level.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 3.2f, .58f);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
					getX(), getY() + 1.7, getZ(), 48, 2.2, 1.25, 2.2, .04);
		}
		if (age == 61) {
			LokiFx.send(level, LokiFx.Effect.TRANSFORM_ROAR, position(), getYRot());
			level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 4, .52f);
		}
		if (left == 0) {
			cloneCooldown = 12;
			roarCooldown = 12;
		}
	}

	private Vec3 findTransformationSpace(ServerLevel level) {
		if (canTransformAt(level, position())) return position();
		Vec3 origin = position();
		for (int radius = 2; radius <= 14; radius += 2) {
			for (int i = 0; i < 12; i++) {
				double angle = i * Math.TAU / 12;
				BlockPos base = BlockPos.containing(origin.add(radius * Math.cos(angle), 0, radius * Math.sin(angle)));
				for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3}) {
					Vec3 at = Vec3.atBottomCenterOf(base.above(dy));
					if (canTransformAt(level, at)) return at;
				}
			}
		}
		if (arena != null) {
			Vec3 plaza = Vec3.atBottomCenterOf(arena.above());
			if (canTransformAt(level, plaza)) return plaza;
		}
		return origin;
	}

	private boolean canTransformAt(ServerLevel level, Vec3 at) {
		BlockPos pos = BlockPos.containing(at);
		if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
				|| arena != null && !insideArena(at)) return false;
		BlockPos floor = pos.below();
		AABB box = FENRIR_SIZE.makeBoundingBox(at);
		return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
				&& level.noCollision(box) && !level.containsAnyLiquid(box);
	}

	private void summonIllusions(ServerLevel level, ServerPlayer target) {
		int count = isEnraged() ? 2 : 1;
		for (int i = 0; i < count; i++) {
			Vec3 at = findFlank(level, target, 3.6, 8);
			if (at == null) continue;
			Loki copy = ModEntities.LOKI.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (copy == null) continue;
			copy.entityData.set(ILLUSION, true);
			copy.illusionOwner = getUUID();
			copy.arena = arena;
			copy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1);
			copy.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(3);
			copy.setHealth(1);
			copy.snapTo(at.x, at.y, at.z, yawTo(at, target.position()), 0);
			if (level.addFreshEntity(copy)) LokiFx.send(level, LokiFx.Effect.ILLUSION_SPAWN, at, copy.getYRot());
		}
	}

	private Vec3 findFlank(ServerLevel level, ServerPlayer target, double near, double far) {
		for (int i = 0; i < 18; i++) {
			double angle = getRandom().nextDouble() * Math.TAU;
			double radius = Mth.lerp(getRandom().nextDouble(), near, far);
			BlockPos base = BlockPos.containing(target.getX() + Math.cos(angle) * radius, target.getY(),
					target.getZ() + Math.sin(angle) * radius);
			if (!level.hasChunkAt(base)) continue;
			for (int dy : new int[]{0, 1, -1, 2, -2, -3}) {
				Vec3 at = Vec3.atBottomCenterOf(base.above(dy));
				if ((arena == null || insideArena(at)) && safeFooting(level, at) && distanceToSqr(at) > 3
						&& level.getEntitiesOfClass(Loki.class, new AABB(at, at).inflate(1.4), Loki::isAlive).isEmpty()) return at;
			}
		}
		return null;
	}

	/** Starts an independent fight at the command's location, outside the Mind Stone quest. */
	public static Loki spawnStandalone(ServerLevel level, Vec3 origin, float facing) {
		if (level.getDifficulty() == Difficulty.PEACEFUL) return null;
		Loki boss = ModEntities.LOKI.create(level, EntitySpawnReason.COMMAND);
		if (boss == null) return null;
		Vec3 forward = Vec3.directionFromRotation(0, facing);
		for (int turn : new int[]{0, 90, -90, 180}) {
			Vec3 offset = forward.yRot(turn * Mth.DEG_TO_RAD).scale(3);
			BlockPos base = BlockPos.containing(origin.add(offset));
			for (int dy : new int[]{0, 1, -1, 2, -2}) {
				Vec3 at = Vec3.atBottomCenterOf(base.above(dy));
				if (safeFooting(level, at)) return placeStandalone(level, boss, at, yawTo(at, origin));
			}
		}
		// The command also works in mid-air when the execution position has no nearby floor.
		return placeStandalone(level, boss, origin, facing);
	}

	private static Loki placeStandalone(ServerLevel level, Loki boss, Vec3 at, float yaw) {
		BlockPos pos = BlockPos.containing(at);
		if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) return null;
		boss.snapTo(at.x, at.y, at.z, yaw, 0);
		boss.setYBodyRot(yaw);
		if (!level.noCollision(boss.getBoundingBox()) || level.containsAnyLiquid(boss.getBoundingBox())) return null;
		if (!level.addFreshEntity(boss)) return null;
		LokiFx.send(level, LokiFx.Effect.TELEPORT_IN, at, yaw);
		boss.greet(level);
		return boss;
	}

	public static boolean safeFooting(ServerLevel level, Vec3 at) {
		BlockPos pos = BlockPos.containing(at);
		if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
		BlockPos floor = pos.below();
		AABB box = new AABB(at.x - 0.45, at.y, at.z - 0.45, at.x + 0.45, at.y + 2.25, at.z + 0.45);
		return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
				&& level.noCollision(box) && !level.containsAnyLiquid(box);
	}

	public static float yawTo(Vec3 from, Vec3 to) {
		return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90;
	}

	private boolean gauntletBlow;

	/** An ability of a worn gauntlet: exempt from the ordinary-weapon cap. */
	public boolean hurtByGauntlet(ServerLevel level, DamageSource source, float amount) {
		gauntletBlow = true;
		try { return hurtServer(level, source, amount); } finally { gauntletBlow = false; }
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.is(DamageTypes.GENERIC_KILL)) return super.hurtServer(level, source, amount);
		if (introTicks() > 0 || transformationTicks() > 0 || !Float.isFinite(amount)) return false;
		if (amount <= 0) return super.hurtServer(level, source, amount);
		if (isIllusion()) return super.hurtServer(level, source, Math.max(amount, 20));
		boolean weakPoint = source.is(DamageTypes.ARROW) || source.is(DamageTypeTags.IS_EXPLOSION);
		if (source.is(DamageTypes.ARROW)) amount *= 1.6f;
		else if (source.is(DamageTypeTags.IS_EXPLOSION)) amount *= 1.7f;
		// Buffed swords, crits and maces cannot melt him: ordinary hits are capped (arrows and blasts exploit the weak point).
		if (!gauntletBlow && !source.is(com.dantemorrigan.gauntlet.doom.DoomCombat.ARMOR_DAMAGE)) amount = Math.min(amount, weakPoint ? (isEnraged() ? 16f : 24f) : (isEnraged() ? 8f : 12f)) * (weakPoint ? 1f : .75f);
		if (wardTicks() > 0) amount *= weakPoint ? .85f : .4f;
		if (isEnraged()) {
			amount *= .55f;
		} else {
			float untilTransformation = getHealth() - getMaxHealth() * PHASE_THRESHOLD;
			if (untilTransformation <= 0) return false;
			amount = Math.min(amount, untilTransformation + 0.5f);
		}
		boolean damaged = super.hurtServer(level, source, amount);
		if (damaged && isAlive() && !isEnraged() && wardCooldown == 0 && source.getEntity() instanceof ServerPlayer) {
			wardCooldown = 115;
			entityData.set(WARD, 40);
			LokiFx.ward(level, this);
			level.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.6f, .75f);
		}
		return damaged;
	}

	@Override
	public void die(DamageSource source) {
		if (dead) return;
		super.die(source);
		if (!dead || !(level() instanceof ServerLevel level)) return;
		if (isIllusion()) {
			shatter(level);
			if (source.getEntity() instanceof ServerPlayer player) ModAdvancements.award(player, "shatter_loki_illusion");
			discard();
			return;
		}
        if(isCityEncounter() && MindQuest.isCurrentBoss(this) && source.getEntity() instanceof ServerPlayer killer)
            ModAdvancements.award(killer,"defeat_loki");
		bar.removeAllPlayers();
		getNavigation().stop();
		entityData.set(SHIFT, 0);
		entityData.set(STRIKE, 0);
		entityData.set(WARD, 0);
		LokiFx.send(level, LokiFx.Effect.DEFEAT_CINEMATIC, position(), getYRot());
	}

	@Override
	protected void tickDeath() {
		deathTime++;
		setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
		if (!(level() instanceof ServerLevel level)) return;
		if (deathTime == REVERT_TICK) {
			entityData.set(ENRAGED, false);
			LokiFx.send(level, LokiFx.Effect.TRANSFORM_REVEAL, position(), getYRot());
			level.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2, .65f);
		}
		if (deathTime == REVERT_TICK + 5) playVoice(level, ModSounds.LOKI_DEFEAT, 38);
		if (deathTime == 41) level.playSound(null, blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.HOSTILE, 1.3f, .65f);
		if (deathTime == DISSOLVE_TICK) LokiFx.send(level, LokiFx.Effect.DEFEAT_DUST, position(), getYRot());
		if (deathTime >= DISSOLVE_TICK && deathTime % 3 == 0) {
			var gold = new net.minecraft.core.particles.DustParticleOptions(0xffdb76, 1.15f);
			var jade = new net.minecraft.core.particles.DustParticleOptions(0x52df8e, 1);
			float rise = (deathTime - DISSOLVE_TICK) / (float) (DEATH_TICKS - DISSOLVE_TICK);
			level.sendParticles(gold, getX(), getY() + .28 + rise * 1.05, getZ(), 20, .38, .19, .31, .05);
			level.sendParticles(jade, getX(), getY() + .45 + rise * .9, getZ(), 13, .4, .24, .32, .04);
		}
		if (deathTime >= DEATH_TICKS && !isRemoved()) {
            for(ServerPlayer player:level.players()) if(player.isAlive() && !player.isSpectator() && player.distanceToSqr(this)<=80*80) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,new com.dantemorrigan.gauntlet.net.ModNetwork.QuestAlert(Component.translatable("msg.infinity_gauntlet.loki.defeated"),2));
                level.playSound(null,player.blockPosition(),SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,net.minecraft.sounds.SoundSource.PLAYERS,1,1.15f);
            }
			MindQuest.onLokiDefeated(level, this);
			remove(RemovalReason.KILLED);
		}
	}

	private void shatter(ServerLevel level) {
		LokiFx.send(level, LokiFx.Effect.ILLUSION_BREAK, position(), getYRot());
		level.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 0.75f, 1.6f);
	}
}
