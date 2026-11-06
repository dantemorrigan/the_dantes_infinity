package com.dantemorrigan.gauntlet.boss;

import com.dantemorrigan.gauntlet.ModAdvancements;
import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.stones.MindArena;
import com.dantemorrigan.gauntlet.stones.MindQuest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.UUID;

public final class Loki extends Monster {
	public static final int INTRO_TICKS = 22;
	public static final int WINDUP = 7;
	public static final int EXPERIENCE = 650;
	private static final int CLONE_LIFETIME = 105;
	private static final EntityDataAccessor<Boolean> ILLUSION = SynchedEntityData.defineId(Loki.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> ENRAGED = SynchedEntityData.defineId(Loki.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> INTRO = SynchedEntityData.defineId(Loki.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> STRIKE = SynchedEntityData.defineId(Loki.class, EntityDataSerializers.INT);

	private final ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("entity.infinity_gauntlet.loki"),
			BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
	private BlockPos arena;
	private UUID illusionOwner;
	private int teleportCooldown = 75;
	private int cloneCooldown = 110;
	private int strikeCooldown = 20;
	private int lifetime = CLONE_LIFETIME;

	public Loki(EntityType<? extends Loki> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 144)
				.add(Attributes.MOVEMENT_SPEED, 0.32).add(Attributes.ATTACK_DAMAGE, 8)
				.add(Attributes.ARMOR, 4).add(Attributes.FOLLOW_RANGE, 44)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.22).add(Attributes.STEP_HEIGHT, 1);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ILLUSION, false).define(ENRAGED, false).define(INTRO, 0).define(STRIKE, 0);
	}

	public void startEncounter(BlockPos city) {
		arena = city.immutable();
		entityData.set(INTRO, INTRO_TICKS);
	}

	public boolean isIllusion() { return entityData.get(ILLUSION); }
	public boolean isEnraged() { return entityData.get(ENRAGED); }
	public int introTicks() { return entityData.get(INTRO); }
	public int strikeTicks() { return entityData.get(STRIKE); }

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
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		arena = input.read("LokiArena", BlockPos.CODEC).orElse(null);
		if (input.getBooleanOr("LokiEnraged", false)) applyRage();
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
			if (!isEnraged() && getHealth() <= getMaxHealth() * 0.45f) enrage(level);
		}

		int intro = introTicks();
		if (intro > 0) {
			entityData.set(INTRO, intro - 1);
			Vec3 motion = getLookAngle().multiply(0.085, 0, 0.085);
			setDeltaMovement(motion.x, getDeltaMovement().y, motion.z);
			return;
		}

		ServerPlayer target = selectTarget(level);
		setTarget(target);
		if (target == null) {
			entityData.set(STRIKE, 0);
			getNavigation().stop();
			return;
		}
		lookAt(target, 30, 30);
		double distance = distanceToSqr(target);
		boolean sight = hasLineOfSight(target);
		int strike = strikeTicks();
		if (strike > 0) {
			entityData.set(STRIKE, strike - 1);
			if (strike == 1) {
				LokiFx.send(level, LokiFx.Effect.DAGGER_SLASH, position().add(getLookAngle().multiply(0.65, 0, 0.65)), getYRot());
				level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1f, 1.35f);
				if (distance < 9 && sight) doHurtTarget(level, target);
				strikeCooldown = isIllusion() ? 29 : isEnraged() ? 14 : 23;
			}
			return;
		}

		if (!isIllusion()) {
			if (--cloneCooldown <= 0) {
				summonIllusions(level, target);
				cloneCooldown = isEnraged() ? 70 : 150;
			}
			if (--teleportCooldown <= 0 && (distance > 9 || isEnraged())) {
				Vec3 to = findFlank(level, target, 3.2, 6);
				if (to != null) {
					getNavigation().stop();
					LokiFx.send(level, LokiFx.Effect.TELEPORT_OUT, position(), getYRot());
					teleportTo(level, to.x, to.y, to.z, Set.of(), yawTo(to, target.position()), 0, false);
					LokiFx.send(level, LokiFx.Effect.TELEPORT_IN, position(), getYRot());
					level.playSound(null, blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1f, 1.35f);
					teleportCooldown = isEnraged() ? 43 : 95;
					strikeCooldown = Math.max(strikeCooldown, 6);
					return;
				}
				teleportCooldown = 15;
			}
		}

		if (strikeCooldown > 0) strikeCooldown--;
		if (distance <= 7.3 && sight && strikeCooldown == 0) {
			getNavigation().stop();
			entityData.set(STRIKE, WINDUP);
			if (onGround() && distance > 2.3) {
				Vec3 lunge = target.position().subtract(position()).multiply(1, 0, 1).normalize().scale(0.43);
				setDeltaMovement(lunge.x, 0.10, lunge.z);
			}
		} else if (distance > 4 || !sight) {
			if (tickCount % 4 == 0) getNavigation().moveTo(target, isEnraged() ? 1.36 : 1.1);
		} else {
			getNavigation().stop();
		}
	}

	private ServerPlayer selectTarget(ServerLevel level) {
		ServerPlayer closest = null;
		double best = 44 * 44;
		for (ServerPlayer player : level.players()) {
			if (!player.isAlive() || player.isSpectator() || player.isCreative()) continue;
			if (arena != null && !insideArena(player.position())) continue;
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
		getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10);
		bar.setColor(BossEvent.BossBarColor.GREEN);
		bar.setName(Component.translatable("boss.infinity_gauntlet.loki.rage"));
	}

	private void enrage(ServerLevel level) {
		applyRage();
		cloneCooldown = Math.min(cloneCooldown, 8);
		teleportCooldown = Math.min(teleportCooldown, 17);
		LokiFx.send(level, LokiFx.Effect.RAGE_PULSE, position(), getYRot());
		level.playSound(null, blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.6f, 0.8f);
	}

	private void summonIllusions(ServerLevel level, ServerPlayer target) {
		int count = isEnraged() ? 3 : 1;
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
		return boss;
	}

	public static boolean safeFooting(ServerLevel level, Vec3 at) {
		BlockPos pos = BlockPos.containing(at);
		if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
		BlockPos floor = pos.below();
		AABB box = new AABB(at.x - 0.45, at.y, at.z - 0.45, at.x + 0.45, at.y + 2.55, at.z + 0.45);
		return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
				&& level.noCollision(box) && !level.containsAnyLiquid(box);
	}

	public static float yawTo(Vec3 from, Vec3 to) {
		return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (!source.is(DamageTypes.GENERIC_KILL) && introTicks() > 0) return false;
		if (amount > 0) {
			if (isIllusion()) amount = Math.max(amount, 20);
			else if (source.is(DamageTypes.ARROW)) amount *= 1.6f;
			else if (source.is(DamageTypeTags.IS_EXPLOSION)) amount *= 1.7f;
		}
		return super.hurtServer(level, source, amount);
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
		bar.removeAllPlayers();
		MindQuest.onLokiDefeated(level, this);
	}

	private void shatter(ServerLevel level) {
		LokiFx.send(level, LokiFx.Effect.ILLUSION_BREAK, position(), getYRot());
		level.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 0.75f, 1.6f);
	}
}
