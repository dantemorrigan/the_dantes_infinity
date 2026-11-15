package com.dantemorrigan.gauntlet.doom;

import com.dantemorrigan.gauntlet.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.List;

/**
 * Stylized humanoid Latverian Quest NPCs:
 * - {@link #ROLE_MECHANIC}: Old Mechanic Radovan in the resistance workshop.
 * - {@link #ROLE_TAVERN_KEEPER}: Tavern Trader Mirko by the dark cellar entrance in the tavern.
 */
public class LatveriaNpc extends PathfinderMob {
	public static final int ROLE_MECHANIC = 0;
	public static final int ROLE_TAVERN_KEEPER = 1;
	public static final int ROLE_CITIZEN_1 = 2;
	public static final int ROLE_CITIZEN_2 = 3;
	public static final int ROLE_CITIZEN_3 = 4;
	public static final int ROLE_CITIZEN_4 = 5;

	private static final EntityDataAccessor<Integer> ROLE = SynchedEntityData.defineId(LatveriaNpc.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> BOWING = SynchedEntityData.defineId(LatveriaNpc.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> TALK_TICKS = SynchedEntityData.defineId(LatveriaNpc.class, EntityDataSerializers.INT);

	private BlockPos anchorPos = BlockPos.ZERO;
	private int greetCooldown;

	public LatveriaNpc(EntityType<? extends LatveriaNpc> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 40.0)
				.add(Attributes.MOVEMENT_SPEED, 0.22)
				.add(Attributes.FOLLOW_RANGE, 16.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ROLE, ROLE_MECHANIC);
		builder.define(BOWING, false);
		builder.define(TALK_TICKS, 0);
	}

	public int role() {
		return entityData.get(ROLE);
	}

	public boolean bowing() {
		return entityData.get(BOWING);
	}

	public int talkTicks() {
		return entityData.get(TALK_TICKS);
	}

	public void triggerTalk(int ticks) {
		entityData.set(TALK_TICKS, ticks);
	}

	public void setup(int role, BlockPos home) {
		entityData.set(ROLE, role);
		this.anchorPos = home != null ? home : blockPosition();
		this.setHomeTo(this.anchorPos, 2);
		refreshNameAndGear();
	}

	/** A quest giver's name: bold, coloured and set between two stars. */
	public static Component fancy(String key, net.minecraft.ChatFormatting color) {
		return Component.literal("✦ ").withStyle(color)
				.append(Component.translatable(key).withStyle(net.minecraft.ChatFormatting.BOLD, color))
				.append(Component.literal(" ✦").withStyle(color));
	}

	private void refreshNameAndGear() {
		int r = role();
		if (r == ROLE_MECHANIC) {
			setCustomName(fancy("entity.infinity_gauntlet.latveria_npc.mechanic", net.minecraft.ChatFormatting.GOLD));
			setCustomNameVisible(true);
			setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.REPAIR_TOOL));
			setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		} else if (r == ROLE_TAVERN_KEEPER) {
			setCustomName(fancy("entity.infinity_gauntlet.latveria_npc.tavern_keeper", net.minecraft.ChatFormatting.GREEN));
			setCustomNameVisible(true);
			setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.HONEY_BOTTLE));
			setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		} else {
			setCustomName(null);
			setCustomNameVisible(false);
		}
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MoveTowardsRestrictionGoal(this, 0.85));
		this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 10.0F));
		this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		int r = role();
		// Only Quest NPCs (Mechanic & Tavern Keeper) remain humanoid LatveriaNpc; remove any old wandering citizens.
		if (r != ROLE_MECHANIC && r != ROLE_TAVERN_KEEPER) {
			this.discard();
			return;
		}
		// Self-deduplicate if an older save spawned two NPCs of the same role.
		if (this.tickCount % 20 == 1) {
			List<LatveriaNpc> nearby = level.getEntitiesOfClass(LatveriaNpc.class, getBoundingBox().inflate(24.0),
					other -> other != this && other.isAlive() && other.role() == r);
			for (LatveriaNpc other : nearby) {
				if (other.getId() < this.getId()) {
					this.discard();
					return;
				}
			}
		}

		int tt = talkTicks();
		if (tt > 0) {
			entityData.set(TALK_TICKS, tt - 1);
		}
		if (greetCooldown > 0) greetCooldown--;

		if (!anchorPos.equals(BlockPos.ZERO)) {
			if (blockPosition().distSqr(anchorPos) > 6.0) {
				getNavigation().moveTo(anchorPos.getX() + 0.5, anchorPos.getY(), anchorPos.getZ() + 0.5, 0.85);
			}
		}

		// When Doom is defeated, quest NPCs bow respectfully to the new King when nearby.
		boolean conquered = Latveria.conquered(level.getServer());
		if (conquered) {
			Player nearest = level.getNearestPlayer(this, 6.5);
			boolean shouldBow = nearest != null && !nearest.isSpectator();
			entityData.set(BOWING, shouldBow);
			if (shouldBow) {
				getLookControl().setLookAt(nearest, 30.0F, 30.0F);
				getNavigation().stop();
				if (greetCooldown == 0 && random.nextInt(90) == 0) {
					greetCooldown = 200;
					playVoiceMurmur(level, true);
					level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.9, getZ(), 3, 0.25, 0.2, 0.25, 0.02);
				}
			}
		} else if (bowing()) {
			entityData.set(BOWING, false);
		}
	}

	public void playVoiceMurmur(ServerLevel level, boolean happy) {
		int r = role();
		float basePitch = switch (r) {
			case ROLE_MECHANIC -> 0.76f;
			case ROLE_TAVERN_KEEPER -> 0.88f;
			default -> 0.96f;
		};
		float pitch = basePitch + (random.nextFloat() - 0.5f) * 0.10f;
		SoundEvent primary = happy ? SoundEvents.VILLAGER_CELEBRATE
				: (r == ROLE_TAVERN_KEEPER ? SoundEvents.WANDERING_TRADER_YES : SoundEvents.VILLAGER_TRADE);
		level.playSound(null, getX(), getY(), getZ(), primary, SoundSource.NEUTRAL, 0.85f, pitch);
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
		if (level() instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) {
			if (!LatveriaQuests.openDialog(sp, this, role())) return InteractionResult.SUCCESS_SERVER;
			getLookControl().setLookAt(sp, 35.0F, 35.0F);
			triggerTalk(30);
			boolean conquered = Latveria.conquered(serverLevel.getServer());
			playVoiceMurmur(serverLevel, conquered);
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.VILLAGER_AMBIENT;
	}

	@Override
	public float getVoicePitch() {
		return switch (role()) {
			case ROLE_MECHANIC -> 0.78f;
			case ROLE_TAVERN_KEEPER -> 0.88f;
			default -> 0.96f;
		};
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) return false;
		if (role() == ROLE_MECHANIC || role() == ROLE_TAVERN_KEEPER) {
			return !(source.getEntity() instanceof Player p && p.isCreative());
		}
		return super.isInvulnerableTo(level, source);
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
		super.addAdditionalSaveData(out);
		out.putInt("Role", role());
		out.putInt("HomeX", anchorPos.getX());
		out.putInt("HomeY", anchorPos.getY());
		out.putInt("HomeZ", anchorPos.getZ());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		super.readAdditionalSaveData(in);
		int r = in.getIntOr("Role", ROLE_MECHANIC);
		int hx = in.getIntOr("HomeX", blockPosition().getX());
		int hy = in.getIntOr("HomeY", blockPosition().getY());
		int hz = in.getIntOr("HomeZ", blockPosition().getZ());
		setup(r, new BlockPos(hx, hy, hz));
	}
}
