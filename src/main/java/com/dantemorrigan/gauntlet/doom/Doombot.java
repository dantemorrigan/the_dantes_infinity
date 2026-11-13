package com.dantemorrigan.gauntlet.doom;

import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.ModParticles;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * A Doombot in one of six roles. Hostile ones hold Castle Doom; once the castle is taken, every survivor obeys the
 * victors: it follows or stands guard and fights monsters instead of players.
 *
 * <p>Soldier shoots from the gauntlet, Bastion bashes and raises a shield that covers nearby Doombots, Interceptor
 * hovers and dives, Arcanist casts homing bolts and wards allies, Engineer repairs the damaged, Royal Guard strikes
 * three times with a halberd.
 */
public class Doombot extends PathfinderMob {
	public static final int SOLDIER = 0, BASTION = 1, INTERCEPTOR = 2, ARCANIST = 3, ENGINEER = 4, ROYAL_GUARD = 5;
	public static final String[] NAMES = {"soldier", "bastion", "interceptor", "arcanist", "engineer", "royal_guard"};
	public static final int A_IDLE = 0, A_ALERT = 1, A_ATTACK = 2, A_STAGGER = 3, A_SALUTE = 4, A_ACTIVATE = 5;
	public static final int FLAG_ALLIED = 1, FLAG_SHIELD = 2, FLAG_WARD = 4, FLAG_FLYING = 8;
	private static final int[] ATTACK_TICKS = {24, 48, 44, 40, 60, 48};
	private static final double[] HEALTH = {90, 320, 80, 90, 100, 240};
	private static final double[] SPEED = {0.27, 0.2, 0.3, 0.23, 0.28, 0.29};
	private static final double[] ARMOR = {12, 24, 8, 8, 12, 20};
	/** Body size: all of them stand 1 to 1.5 blocks over a player, the Bastion towers over the rest. */
	public static final double[] SCALE = {1.65, 2.2, 1.55, 1.6, 1.5, 1.8};
	/** Blows: a Doombot hits hard enough to matter for a player in iron or diamond. */
	private static final float[] BLOW = {10, 16, 14, 11, 7, 15};
	/** The most health one blow can take, as a share of the maximum: a nuke does not delete a robot. */
	private static final float MAX_SHARE = 0.34f;

	private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(Doombot.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(Doombot.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> FLAGS = SynchedEntityData.defineId(Doombot.class, EntityDataSerializers.INT);

	/** Client: tickCount when the current action began. */
	public int actionStartTick;
	private int actionTicks, cooldown = 20, casts, wardTicks, retarget;
	private int squad = -1;
	private UUID commander, targetId, patient;
	private boolean guarding;
	private Vec3 aim, home;

	public Doombot(EntityType<? extends Doombot> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.xpReward = 6;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 90).add(Attributes.MOVEMENT_SPEED, 0.27)
				.add(Attributes.FOLLOW_RANGE, 44).add(Attributes.ARMOR, 12).add(Attributes.ARMOR_TOUGHNESS, 4)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.5).add(Attributes.STEP_HEIGHT, 1.8).add(Attributes.SCALE, 1.65);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(VARIANT, SOLDIER);
		builder.define(ACTION, A_IDLE);
		builder.define(FLAGS, 0);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (ACTION.equals(accessor)) this.actionStartTick = this.tickCount;
	}

	public int variant() { return this.entityData.get(VARIANT); }

	public int action() { return this.entityData.get(ACTION); }

	public boolean flag(int f) { return (this.entityData.get(FLAGS) & f) != 0; }

	private void setFlag(int f, boolean on) {
		int v = this.entityData.get(FLAGS);
		this.entityData.set(FLAGS, on ? v | f : v & ~f);
	}

	public void setup(int variant, int squad) {
		this.entityData.set(VARIANT, variant);
		this.squad = squad;
		applyVariant();
		this.setHealth(this.getMaxHealth());
	}

	private void applyVariant() {
		int v = variant();
		this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH[v]);
		this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(SPEED[v]);
		this.getAttribute(Attributes.ARMOR).setBaseValue(ARMOR[v]);
		this.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(v == BASTION ? 8 : 4);
		this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(v == BASTION ? 0.95 : v == ROYAL_GUARD ? 0.7 : 0.5);
		this.getAttribute(Attributes.SCALE).setBaseValue(SCALE[v]);
		this.getAttribute(Attributes.STEP_HEIGHT).setBaseValue(1.1 * SCALE[v]);
		this.refreshDimensions();
		boolean flies = v == INTERCEPTOR;
		this.setNoGravity(flies);
		setFlag(FLAG_FLYING, flies);
		this.setCustomName(null);
	}

	@Override
	protected Component getTypeName() {
		return Component.translatable("entity.infinity_gauntlet.doombot." + NAMES[variant()]);
	}

	public int squad() { return squad; }

	public boolean allied() { return flag(FLAG_ALLIED); }

	public UUID commander() { return commander; }

	/** The survivor changes sides: it salutes and from now on obeys {@code player}. */
	public void obey(ServerPlayer player) {
		this.commander = player.getUUID();
		this.targetId = null;
		this.squad = -1;
		setFlag(FLAG_ALLIED, true);
		this.home = this.position();
		setAction(A_SALUTE);
	}

	public boolean guarding() { return guarding; }

	public void setGuarding(boolean guard) {
		this.guarding = guard;
		this.home = this.position();
	}

	private void setAction(int a) {
		this.entityData.set(ACTION, a, true);
		this.actionTicks = 0;
	}

	// ------------------------------------------------------------------ brain

	private boolean foe(LivingEntity e) {
		if (e == this || !e.isAlive()) return false;
		if (allied()) return e instanceof Enemy || e instanceof Doombot b && !b.allied() || e instanceof DoctorDoom;
		return e instanceof Player p && !p.isCreative() && !p.isSpectator() || e instanceof Doombot b && b.allied();
	}

	private LivingEntity target() {
		return targetId != null && this.level() instanceof ServerLevel sl && sl.getEntity(targetId) instanceof LivingEntity e && foe(e) ? e : null;
	}

	private LivingEntity findTarget(ServerLevel level) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(26, 12, 26), this::foe)) {
			double d = e.distanceToSqr(this);
			if (d > 12 * 12 && !this.hasLineOfSight(e)) continue;
			if (d < bestD) { bestD = d; best = e; }
		}
		return best;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		actionTicks++;
		if (this.isDeadOrDying()) return;
		if (!allied() && Latveria.conquered(level.getServer())) {
			ServerPlayer owner = Latveria.anyOwner(level.getServer());
			if (owner != null && owner.level() == level) obey(owner);
		}
		if (wardTicks > 0 && --wardTicks == 0) setFlag(FLAG_WARD, false);
		int action = action();
		if (action == A_ALERT || action == A_STAGGER || action == A_SALUTE || action == A_ACTIVATE) {
			this.getNavigation().stop();
			if (actionTicks >= (action == A_STAGGER ? 10 : action == A_ALERT ? 14 : action == A_SALUTE ? 36 : 50)) setAction(A_IDLE);
			return;
		}
		if (--retarget <= 0) {
			retarget = 12;
			LivingEntity had = target();
			LivingEntity found = findTarget(level);
			if (had == null && found != null && action == A_IDLE && !allied()) setAction(A_ALERT);
			targetId = found == null ? null : found.getUUID();
		}
		LivingEntity target = target();
		if (cooldown > 0) cooldown--;
		if (action == A_ATTACK) {
			tickAttack(level, target);
			return;
		}
		if (variant() == ENGINEER && tryRepair(level)) return;
		if (target == null) {
			idle(level);
			return;
		}
		this.getLookControl().setLookAt(target, 30, 30);
		double d = this.distanceTo(target);
		switch (variant()) {
			case SOLDIER, ARCANIST -> {
				if (d > 13) this.getNavigation().moveTo(target, 1.0);
				else if (d < 5) retreat(target);
				else this.getNavigation().stop();
				if (cooldown <= 0 && d <= 20 && this.hasLineOfSight(target)) begin(variant() == SOLDIER ? 30 : 52);
			}
			case BASTION -> {
				this.getNavigation().moveTo(target, 1.0);
				if (cooldown <= 0 && (d <= 6.5 || d <= 14)) begin(70);
			}
			case INTERCEPTOR -> {
				hover(target.position().add(Math.cos(this.tickCount * 0.05) * 6, 9, Math.sin(this.tickCount * 0.05) * 6), 0.36);
				if (cooldown <= 0 && d <= 24) begin(56);
			}
			case ENGINEER -> {
				if (d > 3.6) this.getNavigation().moveTo(target, 1.1);
				if (cooldown <= 0 && d <= 4.4) {
					DoomCombat.hurt(level, target, this, BLOW[ENGINEER]);
					this.swing(InteractionHand.MAIN_HAND);
					cooldown = 20;
				}
			}
			default -> {
				this.getNavigation().moveTo(target, 1.2);
				if (cooldown <= 0 && d <= 7) begin(20);
			}
		}
	}

	private void begin(int nextCooldown) {
		setAction(A_ATTACK);
		cooldown = nextCooldown + ATTACK_TICKS[variant()];
		aim = null;
		if (variant() != INTERCEPTOR) this.getNavigation().stop();
	}

	private void retreat(LivingEntity from) {
		Vec3 away = this.position().subtract(from.position());
		away = new Vec3(away.x, 0, away.z).normalize().scale(5);
		this.getNavigation().moveTo(this.getX() + away.x, this.getY(), this.getZ() + away.z, 1.0);
	}

	private void hover(Vec3 to, double speed) {
		Vec3 d = to.subtract(this.position());
		double len = d.length();
		this.setDeltaMovement(len < 0.4 ? Vec3.ZERO : d.scale(Math.min(speed, len) / len));
	}

	private void idle(ServerLevel level) {
		if (allied() && !guarding && commander != null && level.getEntity(commander) instanceof ServerPlayer owner) {
			double d = this.distanceTo(owner);
			if (d > 34) this.teleportTo(owner.getX() + (this.random.nextDouble() - 0.5) * 4, owner.getY(), owner.getZ() + (this.random.nextDouble() - 0.5) * 4);
			else if (variant() == INTERCEPTOR) hover(owner.position().add(Math.cos(this.getId()) * 3, 4, Math.sin(this.getId()) * 3), 0.3);
			else if (d > 6) this.getNavigation().moveTo(owner, 1.15);
			else this.getNavigation().stop();
			return;
		}
		if (home == null) home = this.position();
		if (variant() == INTERCEPTOR) hover(home.add(Math.cos(this.tickCount * 0.03) * 4, 5, Math.sin(this.tickCount * 0.03) * 4), 0.2);
		else if (this.position().distanceToSqr(home) > 36 && this.getNavigation().isDone()) this.getNavigation().moveTo(home.x, home.y, home.z, 0.9);
	}

	/** The Engineer walks to the most damaged friend in reach and welds it back together. */
	private boolean tryRepair(ServerLevel level) {
		Doombot hurt = null;
		for (Doombot b : level.getEntitiesOfClass(Doombot.class, this.getBoundingBox().inflate(14, 6, 14),
				b -> b != this && b.isAlive() && b.allied() == allied() && b.getHealth() < b.getMaxHealth() * 0.85f)) {
			if (hurt == null || b.getHealth() / b.getMaxHealth() < hurt.getHealth() / hurt.getMaxHealth()) hurt = b;
		}
		if (hurt == null) return false;
		if (this.distanceTo(hurt) > 3) {
			this.getNavigation().moveTo(hurt, 1.2);
			return true;
		}
		if (cooldown > 0) return false;
		patient = hurt.getUUID();
		begin(40);
		return true;
	}

	private void tickAttack(ServerLevel level, LivingEntity target) {
		int t = actionTicks, v = variant();
		Vec3 eye = this.position().add(0, this.getBbHeight() * 0.8, 0);
		if (target != null && v != ENGINEER) this.getLookControl().setLookAt(target, 40, 40);
		switch (v) {
			case SOLDIER -> {
				if (t == 6 && target != null) aim = target.position().add(0, target.getBbHeight() * 0.6, 0);
				if (t == 6) DoomVfx.on(level, DoomVfx.CHARGE, this, 0.4f, 8, DoomVfx.GOLD);
				if (t == 13 && aim != null) {
					Vec3 dir = aim.subtract(eye).normalize();
					DoomCombat.fire(level, this, eye.add(dir.scale(0.8)), dir.scale(1.9), BLOW[SOLDIER], 0.3, 1.0, 40, null, 0, DoomVfx.GOLD, 0.34f, this::foe);
					DoomFx.sound(level, eye, SoundEvents.BLAZE_SHOOT, 0.8f, 1.4f);
				}
				if (t == 19 && target != null) {
					Vec3 dir = target.position().add(0, target.getBbHeight() * 0.6, 0).subtract(eye).normalize();
					DoomCombat.fire(level, this, eye.add(dir.scale(0.8)), dir.scale(1.9), BLOW[SOLDIER], 0.3, 1.0, 40, null, 0, DoomVfx.GOLD, 0.34f, this::foe);
					DoomFx.sound(level, eye, SoundEvents.BLAZE_SHOOT, 0.8f, 1.6f);
				}
			}
			case BASTION -> {
				if (t == 16) {
					setFlag(FLAG_SHIELD, true);
					DoomVfx.on(level, DoomVfx.SHIELD, this, 1.3f, 24, DoomVfx.GREEN);
					DoomFx.sound(level, eye, SoundEvents.IRON_GOLEM_REPAIR, 1f, 0.6f);
					DoomVfx.at(level, DoomVfx.SLAM, this.position(), 5.5f, 12, DoomVfx.GREEN);
					for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(4.5, 1.5, 4.5), this::foe)) {
						if (DoomCombat.hurt(level, e, this, BLOW[BASTION])) {
							Vec3 push = e.position().subtract(this.position()).normalize();
							e.knockback(1.5, -push.x, -push.z);
						}
					}
				}
				if (t == 40) setFlag(FLAG_SHIELD, false);
			}
			case INTERCEPTOR -> {
				if (t == 1) DoomVfx.on(level, DoomVfx.JET, this, 1, 36, DoomVfx.GOLD);
				if (t <= 16) hover(this.position().add(0, 3, 0), 0.3);
				if (t == 20 && target != null) aim = target.position();
				if (t > 23 && t <= 33 && aim != null) hover(aim.add(0, 0.6, 0), 1.2);
				if (t == 33 && aim != null) {
					DoomFx.flat(level, ModParticles.DOOM_SLAM_WAVE, this.position(), 4.5);
					DoomFx.sound(level, this.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 1.4f);
					for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(3.4, 2.5, 3.4), this::foe)) {
						DoomCombat.hurt(level, e, this, BLOW[INTERCEPTOR]);
					}
				}
				if (t > 33) hover(this.position().add(0, 4, 0), 0.35);
			}
			case ARCANIST -> {
				if (t == 10) DoomFx.bill(level, ModParticles.DOOM_RUNE_MOTE, eye, 3);
				if (t == 24) {
					casts++;
					Doombot friend = casts % 3 == 0 ? level.getEntitiesOfClass(Doombot.class, this.getBoundingBox().inflate(10),
							b -> b != this && b.isAlive() && b.allied() == allied() && !b.flag(FLAG_WARD)).stream().findFirst().orElse(null) : null;
					if (friend != null) {
						friend.ward();
					} else if (target != null) {
						Vec3 dir = target.position().add(0, 1, 0).subtract(eye).normalize();
						DoomCombat.fire(level, this, eye.add(dir), dir.scale(0.8), BLOW[ARCANIST], 0.35, 1.2, 80, target, 0.1, DoomVfx.GREEN, 0.42f, this::foe);
						DoomFx.sound(level, eye, SoundEvents.EVOKER_CAST_SPELL, 0.9f, 1.3f);
					}
				}
			}
			case ENGINEER -> {
				if (patient != null && level.getEntity(patient) instanceof Doombot b && b.isAlive() && (t == 22 || t == 32 || t == 42)) {
					b.heal(6);
					DoomFx.bill(level, ModParticles.DOOM_RUNE_MOTE, b.position().add(0, 1.2, 0), 2.5);
					DoomFx.sound(level, b.position(), SoundEvents.IRON_GOLEM_REPAIR, 0.8f, 1.5f);
				}
			}
			default -> {
				if (target != null && (t == 12 || t == 26 || t == 36)) {
					boolean lunge = t == 36;
					if (lunge) {
						Vec3 d = target.position().subtract(this.position());
						this.setDeltaMovement(new Vec3(d.x, 0, d.z).normalize().scale(0.9).add(0, 0.1, 0));
					}
					DoomFx.bill(level, ModParticles.DOOM_ARMORED_COMBO, eye.add(this.getLookAngle().scale(1.6)), 1.6);
					DoomFx.sound(level, eye, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.7f);
					if (this.distanceTo(target) <= (lunge ? 8.0 : 5.8)) DoomCombat.hurt(level, target, this, lunge ? BLOW[ROYAL_GUARD] * 1.3f : BLOW[ROYAL_GUARD]);
				}
			}
		}
		if (t >= ATTACK_TICKS[v]) {
			setFlag(FLAG_SHIELD, false);
			setAction(A_IDLE);
		}
	}

	/** An Arcanist's ward: the next blow is swallowed whole. */
	public void ward() {
		wardTicks = 160;
		setFlag(FLAG_WARD, true);
		if (this.level() instanceof ServerLevel sl) DoomFx.bill(sl, ModParticles.DOOM_PHASE_SHIFT, this.position().add(0, 1, 0), 1.6);
	}

	// ------------------------------------------------------------------ damage, death, saving

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.is(DamageTypes.GENERIC_KILL)) return super.hurtServer(level, source, amount);
		if (source.is(DamageTypes.FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN)) return false;
		if (source.getEntity() instanceof Doombot other && other.allied() == allied()) return false;
		if (allied() && source.getEntity() instanceof Player p && Latveria.isOwner(p)) return false;
		if (flag(FLAG_WARD)) {
			wardTicks = 0;
			setFlag(FLAG_WARD, false);
			DoomFx.bill(level, ModParticles.DOOM_PHASE_SHIFT, this.position().add(0, 1, 0), 1.6);
			DoomFx.sound(level, this.position(), SoundEvents.AMETHYST_BLOCK_BREAK, 1f, 0.8f);
			return false;
		}
		if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
			if (flag(FLAG_SHIELD)) amount *= 0.25f;
			else if (!Latveria.generatorOff(level.getServer()) && !level.getEntitiesOfClass(Doombot.class, this.getBoundingBox().inflate(7),
					b -> b != this && b.isAlive() && b.flag(FLAG_SHIELD) && b.allied() == allied()).isEmpty()) {
				amount *= 0.4f;
				DoomFx.bill(level, ModParticles.DOOM_IRON_BASTION, this.position().add(0, 1, 0), 1.4);
			}
		}
		amount = Math.min(amount, this.getMaxHealth() * MAX_SHARE);
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof LivingEntity attacker && foe(attacker)) targetId = attacker.getUUID();
		if (hurt && amount >= 24 && variant() != BASTION && action() == A_IDLE && !this.isDeadOrDying()) setAction(A_STAGGER);
		return hurt;
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (!(player instanceof ServerPlayer sp) || !allied()) return super.mobInteract(player, hand);
		if (held.is(ModItems.REPAIR_TOOL) && this.getHealth() < this.getMaxHealth()) {
			this.heal(12);
			DoomFx.bill(sp.level(), ModParticles.DOOM_RUNE_MOTE, this.position().add(0, 1.2, 0), 2.5);
			DoomFx.sound(sp.level(), this.position(), SoundEvents.IRON_GOLEM_REPAIR, 0.8f, 1.4f);
			return InteractionResult.SUCCESS;
		}
		if (held.is(ModItems.DOOMBOT_CONTROLLER) && Latveria.isOwner(sp)) {
			this.commander = sp.getUUID();
			setGuarding(!guarding);
			setAction(A_SALUTE);
			DoomFx.tell(sp, guarding ? "bot.guard" : "bot.follow", this.getName());
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public void die(DamageSource source) {
		boolean was = this.dead;
		super.die(source);
		if (was || !(this.level() instanceof ServerLevel level)) return;
		Latveria.onBotDeath(level.getServer(), squad);
		DoomFx.bill(level, ModParticles.DOOM_IMPACT, this.position().add(0, 1, 0), 2);
		DoomFx.sound(level, this.position(), SoundEvents.IRON_GOLEM_DEATH, 0.9f, 1.3f);
		if (allied()) return;
		this.spawnAtLocation(level, new ItemStack(ModItems.DOOM_CIRCUIT, 1 + this.random.nextInt(2)));
		int v = variant();
		if (v == BASTION || v == ROYAL_GUARD) this.spawnAtLocation(level, new ItemStack(ModItems.ARC_REACTOR));
		if (v == ARCANIST) this.spawnAtLocation(level, new ItemStack(ModItems.JADE_SHARD, 2));
		if (v == ENGINEER && this.random.nextFloat() < 0.5f) this.spawnAtLocation(level, new ItemStack(ModItems.REPAIR_TOOL));
		if (this.random.nextFloat() < 0.12f) this.spawnAtLocation(level, new ItemStack(ModItems.MECHANICAL_HEART));
	}

	/** The shutdown: it sags for two seconds before it is gone. */
	@Override
	protected void tickDeath() {
		this.deathTime++;
		this.setDeltaMovement(0, this.isNoGravity() ? -0.2 : Math.min(0, this.getDeltaMovement().y), 0);
		if (this.deathTime >= 40 && !this.level().isClientSide() && !this.isRemoved()) {
			if (this.level() instanceof ServerLevel level) DoomFx.bill(level, ModParticles.DOOM_DOOM_SMOKE, this.position().add(0, 0.6, 0), 1.6);
			this.remove(RemovalReason.KILLED);
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("Variant", variant());
		output.putInt("Squad", squad);
		output.putBoolean("Allied", allied());
		output.putBoolean("Guarding", guarding);
		if (commander != null) output.putString("Commander", commander.toString());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(VARIANT, Math.clamp(input.getIntOr("Variant", 0), 0, 5));
		squad = input.getIntOr("Squad", -1);
		setFlag(FLAG_ALLIED, input.getBooleanOr("Allied", false));
		guarding = input.getBooleanOr("Guarding", false);
		String id = input.getStringOr("Commander", "");
		commander = id.isEmpty() ? null : UUID.fromString(id);
		applyVariant();
	}
}
