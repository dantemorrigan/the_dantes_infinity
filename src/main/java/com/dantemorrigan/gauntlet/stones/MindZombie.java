package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.Stone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.dantemorrigan.gauntlet.uru.GauntletDamage;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A smart zombie roaming the Ruined City, infused with the yellow essence of the Mind Stone.
 * Immune to sunlight, fast, able to vault over ruins, coordinate telepathically with other Mind Zombies,
 * and lunge toward targets.
 */
public class MindZombie extends Zombie {
	private static final DustParticleOptions ESSENCE_DUST = new DustParticleOptions(Stone.MIND.color, 1.15f);
	private int lungeCooldown = 30;

	public MindZombie(EntityType<? extends MindZombie> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		if (this.getNavigation() instanceof GroundPathNavigation nav) {
			nav.setCanOpenDoors(true);
			nav.setCanWalkOverFences(true);
		}
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Zombie.createAttributes()
				.add(Attributes.MAX_HEALTH, 30.0)
				.add(Attributes.MOVEMENT_SPEED, 0.35)
				.add(Attributes.ATTACK_DAMAGE, 5.0)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.STEP_HEIGHT, 1.0)
				.add(Attributes.ARMOR, 4.0)
				// No vanilla reinforcements: zombies that call for help look like the quest's zombies coming back.
				.add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		this.goalSelector.addGoal(1, new OpenDoorGoal(this, false));
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
	}

	@Override
	protected boolean isSunSensitive() {
		return false;
	}

	@Override
	protected boolean convertsInWater() {
		return false;
	}

	@Override
	public boolean isBaby() {
		return false;
	}

	@Override
	public void setBaby(boolean baby) {
		super.setBaby(false);
	}

	@Override
	protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
		// Keep head and torso bare so the glowing Mind Stone forehead and chest veins remain clearly visible.
	}

	@Override
	public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof LivingEntity attacker) {
			alertCityHivemind(level, attacker);
		}
		return hurt;
	}

	/** Reality effects must trigger an attributed death before removing the model. */
	public boolean defeatedByReality(ServerLevel level, ServerPlayer caster) {
		return GauntletDamage.hurt(level, this, caster, caster.damageSources().playerAttack(caster), Float.MAX_VALUE)
				&& !isAlive();
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.lungeCooldown > 0) this.lungeCooldown--;

		if (!this.hasHome() && level == level.getServer().overworld()) {
			BlockPos site = MindCity.site(level.getServer());
			if (this.blockPosition().distSqr(site) <= (MindCity.R_BLEND + 16) * (MindCity.R_BLEND + 16)) {
				this.setHomeTo(site, MindCity.R_FLAT - 2);
			}
		}

		// Ambient yellow Mind Stone essence sparkles around the zombie.
		if (this.tickCount % 8 == 0) {
			level.sendParticles(ESSENCE_DUST, this.getX(), this.getY() + 1.15, this.getZ(), 2, 0.28, 0.45, 0.28, 0.01);
		}

		LivingEntity target = this.getTarget();
		if (target != null && target.isAlive()) {
			// Telepathic hive-mind: share target awareness with nearby Mind Zombies in the city.
			if (this.tickCount % 20 == 0) {
				alertCityHivemind(level, target);
			}
			// Smart obstacle vaulting over rubble.
			if (this.horizontalCollision && this.onGround()) {
				Vec3 v = this.getDeltaMovement();
				this.setDeltaMovement(v.x, 0.44, v.z);
			}
			// Smart psychic lunge when closing in on a target.
			double d2 = this.distanceToSqr(target);
			if (this.onGround() && this.lungeCooldown == 0 && d2 >= 9.0 && d2 <= 49.0 && this.hasLineOfSight(target)) {
				Vec3 dir = new Vec3(target.getX() - this.getX(), 0, target.getZ() - this.getZ());
				if (dir.lengthSqr() > 1e-4) {
					dir = dir.normalize().scale(0.58);
					this.setDeltaMovement(dir.x, 0.34, dir.z);
					this.lungeCooldown = 55;
					level.playSound(null, this.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, this.getSoundSource(), 1.0F, 1.3F);
					level.sendParticles(ModParticles.QUEST_MIND_ESSENCE, this.getX(), this.getY() + 1.0, this.getZ(), 1, 0, 0, 0, 0);
				}
			}
		}
	}

	private void alertCityHivemind(ServerLevel level, LivingEntity target) {
		if (target instanceof MindZombie) return;
		for (MindZombie other : level.getEntitiesOfClass(MindZombie.class, new AABB(this.blockPosition()).inflate(32.0),
				z -> z != this && z.isAlive() && z.getTarget() == null)) {
			other.setTarget(target);
		}
	}
}
