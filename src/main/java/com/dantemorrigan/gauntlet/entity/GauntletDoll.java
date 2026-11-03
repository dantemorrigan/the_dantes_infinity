package com.dantemorrigan.gauntlet.entity;

import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.Stone;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Steve doll in a full Infinity Gauntlet (off hand), for testing the Infinity Extractor and the stones.
 * It wanders, notices players close by and pokes them lightly (half a heart), and every few seconds it "uses" one of
 * its stones for a while - the stone glows and the extractor cannot pull it until it stops.
 */
public class GauntletDoll extends PathfinderMob {
	/** Ticks between two uses of a stone, and how long a stone stays in use. */
	private static final int USE_EVERY = 20 * 14, USE_FOR = 20 * 5;

	private int useTimer = 20 * 6;
	private int usingFor;

	public GauntletDoll(EntityType<? extends GauntletDoll> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 40.0)
				.add(Attributes.MOVEMENT_SPEED, 0.24)
				.add(Attributes.ATTACK_DAMAGE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 12.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data) {
		SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
		ItemStack gauntlet = new ItemStack(ModItems.GAUNTLET);
		List<ItemStack> stones = new ArrayList<>();
		for (Stone stone : Stone.values()) stones.add(new ItemStack(stone.item()));
		gauntlet.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(stones));
		GauntletState.refresh(gauntlet);
		this.setItemSlot(EquipmentSlot.OFFHAND, gauntlet);
		this.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
		return result;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		ItemStack gauntlet = GauntletState.activeGauntlet(this);
		if (gauntlet.isEmpty()) return;
		if (usingFor > 0) {
			if (--usingFor == 0) GauntletState.setInUse(gauntlet, EnumSet.noneOf(Stone.class));
			return;
		}
		if (--useTimer > 0) return;
		useTimer = USE_EVERY;
		List<Stone> present = new ArrayList<>(GauntletState.inserted(gauntlet));
		if (present.isEmpty()) return;
		Stone stone = present.get(this.random.nextInt(present.size()));
		GauntletState.setInUse(gauntlet, EnumSet.of(stone));
		usingFor = USE_FOR;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}
}
