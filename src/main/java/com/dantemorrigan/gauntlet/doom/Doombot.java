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
	public static final int FLAG_ALLIED = 1, FLAG_SHIELD = 2, FLAG_WARD = 4, FLAG_FLYING = 8, FLAG_DECOY = 16;
	/** What a hunter knows: something is there ("?") or it has the intruder ("!"). */
	public static final int FLAG_SUSPICIOUS = 32, FLAG_ALERTED = 64, FLAG_DORMANT = 128;
	/** A decoy stands as tall as Doom himself (2.4 blocks) and only lasts so long. */
	public static final double DECOY_SCALE = 2.4 / 1.95;
	public static final int DECOY_LIFE = 600;
	public static final float DECOY_HEALTH = 40;
	private static final int[] ATTACK_TICKS = {24, 56, 44, 40, 60, 48};
	private static final double[] HEALTH = {90, 170, 80, 90, 100, 190};
	private static final double[] SPEED = {0.27, 0.2, 0.3, 0.23, 0.28, 0.29};
	private static final double[] ARMOR = {12, 8, 8, 8, 12, 14};
	/** Body size: all of them stand 1 to 1.5 blocks over a player, the Bastion towers over the rest. */
	public static final double[] SCALE = {1.65, 2.0, 1.55, 1.6, 1.5, 1.8};
	/** Blows: a Doombot hits hard enough to matter for a player in iron or diamond. */
	private static final float[] BLOW = {10, 16, 14, 11, 7, 15};
	/** The most health one blow can take, as a share of the maximum: a nuke does not delete a robot. */
	private static final float MAX_SHARE = 0.34f;

	private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(Doombot.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(Doombot.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> FLAGS = SynchedEntityData.defineId(Doombot.class, EntityDataSerializers.INT);

	/** Client: tickCount when the current action began. */
	public int actionStartTick;
	private int decoyAge;
	private int actionTicks, cooldown = 20, casts, wardTicks, retarget, airTimer = 90, groundTimer, meleeCd;
		private final java.util.Set<UUID> struck = new java.util.HashSet<>();
	private int squad = -1;
	private UUID commander, targetId, patient;
	private boolean guarding;
	/** On parade: the bot stands at its place in the ranks ({@link #home}) and faces this way; NaN when it is not in the ranks. */
	private float parkYaw = Float.NaN;
	private boolean saluted;
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
		this.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2);
		this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(v == BASTION ? 0.7 : v == ROYAL_GUARD ? 0.6 : 0.5);
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
		if (decoy()) return Component.translatable("entity.infinity_gauntlet.doctor_doom");
		return Component.translatable("entity.infinity_gauntlet.doombot." + NAMES[variant()]);
	}

	public int squad() { return squad; }

	public boolean decoy() { return flag(FLAG_DECOY); }

	private boolean forged;

	/** Built at an anvil: a fifth stronger than one put together in the field. */
	public void forge() {
		forged = true;
		this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH[variant()] * 1.2);
		this.setHealth(this.getMaxHealth());
	}

	/**
	 * "It was a Doombot!": a robot in Doom's likeness, left behind in his place when he shifts away. It fights like a
	 * soldier, wears his face and name, and has none of his strength: struck down, it bursts and gives itself away.
	 */
	public void makeDecoy() {
		setup(SOLDIER, -1);
		setFlag(FLAG_DECOY, true);
		this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(DECOY_HEALTH);
		this.getAttribute(Attributes.SCALE).setBaseValue(DECOY_SCALE);
		this.getAttribute(Attributes.ARMOR).setBaseValue(4);
		this.refreshDimensions();
		this.setHealth(DECOY_HEALTH);
		this.decoyAge = 0;
	}

	public boolean allied() { return flag(FLAG_ALLIED); }

	public boolean dormant() { return flag(FLAG_DORMANT); }

	/** Moves a powered-down statue to a new standing place (and makes it its home). */
	public void statueTo(double x, double z) {
		this.teleportTo(x, this.getY(), z);
		this.home = this.position();
	}

	public boolean isForged() { return forged; }

	/**
	 * Permanently powers down the Doombot: its eyes go out, its body turns grey, its head bows down,
	 * and it stands motionless as a statue forever, never despawning.
	 */
	public void becomeStatue(ServerPlayer owner) {
		if (owner != null) {
			this.commander = owner.getUUID();
			setFlag(FLAG_ALLIED, true);
		}
		if (squad >= 0 && this.level() instanceof ServerLevel sl) {
			Latveria.onBotDeath(sl.getServer(), squad);
		}
		this.targetId = null;
		this.squad = -1;
		this.guarding = true;
		this.home = this.position();
		setFlag(FLAG_DORMANT, true);
		setFlag(FLAG_SHIELD, false);
		setFlag(FLAG_WARD, false);
		setFlag(FLAG_FLYING, false);
		setFlag(FLAG_SUSPICIOUS, false);
		setFlag(FLAG_ALERTED, false);
		this.setNoGravity(false);
		this.getNavigation().stop();
		this.setDeltaMovement(Vec3.ZERO);
		this.yBodyRot = this.getYRot();
		this.yHeadRot = this.getYRot();
		this.setXRot(34.0F);
		setAction(A_IDLE);
	}

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

	/** Salutes (the new monarch on the throne, for instance) unless busy fighting. */
	public void salute() {
		if (action() == A_IDLE) setAction(A_SALUTE);
	}

	public boolean guarding() { return guarding; }

	public void setGuarding(boolean guard) {
		this.guarding = guard;
		this.home = this.position();
		this.parkYaw = Float.NaN;
	}

	public boolean parked() { return !Float.isNaN(parkYaw); }

	// ------------------------------------------------------------------ the hunt drives these (see LatveriaHunt)

	/** 0 nothing, 1 senses something ("?"), 2 has the intruder ("!"). */
	public int awareness() { return flag(FLAG_ALERTED) ? 2 : flag(FLAG_SUSPICIOUS) ? 1 : 0; }

	public void setAwareness(int a) {
		if (a == awareness()) return;
		setFlag(FLAG_SUSPICIOUS, a == 1);
		setFlag(FLAG_ALERTED, a == 2);
		if (a == 1 && this.level() instanceof ServerLevel sl) DoomFx.sound(sl, this.getEyePosition(), SoundEvents.IRON_GOLEM_HURT, 0.5f, 0.4f);
	}

	public void engage(LivingEntity target) {
		if (!target.getUUID().equals(targetId)) this.targetId = target.getUUID();
	}

	public void disengage() {
		this.targetId = null;
		if (action() == A_ATTACK) setAction(A_IDLE);
	}

	/** Flies toward a point under its own jets, easing in, body turned the way it goes. */
	public void huntFly(Vec3 to, double speed) {
		if (!airborne()) {
			setFlag(FLAG_FLYING, true);
			this.setNoGravity(true);
			this.getNavigation().stop();
			if (this.level() instanceof ServerLevel sl) {
				DoomVfx.on(sl, DoomVfx.JET, this, 1, 40, DoomVfx.GOLD);
				DoomFx.sound(sl, this.position(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.2f, 0.7f);
			}
		}
		Vec3 d = to.subtract(this.position());
		double len = d.length();
		Vec3 want = len < 0.3 ? Vec3.ZERO : d.scale(Math.min(speed, len * 0.25) / len);
		this.setDeltaMovement(this.getDeltaMovement().lerp(want, 0.12));
		if (d.horizontalDistanceSqr() > 1) face(this.position().add(d.x, 0, d.z));
		// The jets roar and keep their flame while it flies.
		if (this.level() instanceof ServerLevel sl && this.tickCount % 24 == 0) {
			DoomVfx.on(sl, DoomVfx.JET, this, 1, 30, DoomVfx.GOLD);
			DoomFx.sound(sl, this.position(), SoundEvents.BLAZE_BURN, 1.4f, 0.45f);
		}
	}

	/** Lands (if it flies) and walks to a point: heavy steps, the ground shaking under it. */
	public void huntWalk(Vec3 to, double speed) {
		if (airborne()) {
			setFlag(FLAG_FLYING, false);
			this.setNoGravity(false);
			hardLanding = true;
			return;
		}
		if (this.getNavigation().isDone() || this.tickCount % 20 == 0) this.getNavigation().moveTo(to.x, to.y, to.z, speed);
	}

	public void huntStop() {
		this.getNavigation().stop();
		if (airborne()) this.setDeltaMovement(this.getDeltaMovement().scale(0.7));
	}

	public void huntFace(Vec3 at) {
		face(at);
		this.getLookControl().setLookAt(at.x, at.y, at.z, 30, 30);
	}

	public void huntLook(float yaw) {
		this.setYRot(yaw);
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
	}

	private boolean hardLanding;

	/** Every step of a three-block robot is felt: low iron steps, and a shudder close by. */
	@Override
	protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
		this.playSound(SoundEvents.IRON_GOLEM_STEP, 1.1f, 0.55f + this.random.nextFloat() * 0.1f);
		if (this.level() instanceof ServerLevel sl && this.tickCount % 2 == 0) DoomFx.near(sl, this.position(), 9, DoomFx.SHAKE, 0, 0.16f, 4);
	}

	@Override
	public void tick() {
		super.tick();
		// Landing hard from the air: the thud, the dust and a jolt for whoever is near.
		if (hardLanding && this.onGround() && this.level() instanceof ServerLevel sl) {
			hardLanding = false;
			DoomFx.sound(sl, this.position(), SoundEvents.ANVIL_LAND, 0.9f, 0.5f);
			DoomFx.sound(sl, this.position(), SoundEvents.GENERIC_BIG_FALL, 1.4f, 0.6f);
			DoomFx.flat(sl, ModParticles.DOOM_SLAM_WAVE, this.position(), 0.6);
			DoomFx.near(sl, this.position(), 16, DoomFx.SHAKE, 0, 0.55f, 8);
		}
	}

	/** Takes its place in the ranks: stands there, facing {@code yaw}, and waits (it still fights what attacks it, then returns). */
	public void park(Vec3 slot, float yaw) {
		this.guarding = true;
		this.home = slot;
		this.parkYaw = yaw;
		this.saluted = false;
		if (airborne()) land();
		this.getNavigation().stop();
		this.teleportTo(slot.x, slot.y, slot.z);
		this.setYRot(yaw);
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
	}

	/** On parade: back to the place in the ranks if shoved off, then straight and still, facing the throne. */
	private void paradeIdle(ServerLevel level) {
		if (airborne()) land();
		double dx = home.x - this.getX(), dz = home.z - this.getZ(), d2 = dx * dx + dz * dz;
		if (d2 > 2500) {
			this.teleportTo(home.x, home.y, home.z);
			return;
		}
		if (d2 > 1.0) {
			if (this.getNavigation().isDone()) this.getNavigation().moveTo(home.x, home.y, home.z, 1.0);
			return;
		}
		this.getNavigation().stop();
		// The last half block is a slide, so every rank is dead straight.
		if (d2 > 0.0004) this.setPos(this.getX() + dx * 0.3, this.getY(), this.getZ() + dz * 0.3);
		this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
		this.setYRot(parkYaw);
		this.yBodyRot = parkYaw;
		this.yHeadRot = parkYaw;
		if (!saluted) {
			saluted = true;
			salute();
		}
	}

	private void setAction(int a) {
		this.entityData.set(ACTION, a, true);
		this.actionTicks = 0;
	}

	// ------------------------------------------------------------------ brain

	private boolean foe(LivingEntity e) {
		if (e == this || !e.isAlive()) return false;
		if (allied()) return e instanceof Enemy || e instanceof Doombot b && !b.allied() || e instanceof DoctorDoom;
		return e instanceof Player p && !p.isSpectator() || e instanceof Doombot b && b.allied();
	}

	private LivingEntity target() {
		return targetId != null && this.level() instanceof ServerLevel sl && sl.getEntity(targetId) instanceof LivingEntity e && foe(e) ? e : null;
	}

	private LivingEntity findTarget(ServerLevel level) {
		if (dormant()) return null;
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(26, 12, 26), this::foe)) {
			// A player disguised in the Latverian Citizen Cloak is ignored unless they come too close or break order.
			if (e instanceof ServerPlayer sp && !allied() && LatveriaQuests.isCloakActive(level, sp)) {
				if (!LatveriaQuests.checkCloakProximity(level, sp)) continue;
			}
			// A hunter only fights the intruder once it has found them (the hunt decides), not on a glimpse.
			if (e instanceof Player pl && LatveriaHunt.hunter(this) && !LatveriaHunt.engaged(pl)) continue;
			double d = e.distanceToSqr(this);
			if (d > 12 * 12 && !this.hasLineOfSight(e)) continue;
			if (d < bestD) { bestD = d; best = e; }
		}
		return best;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		// The generator is dead: every castle robot outside the throne hall powers down, including those that were far away (in
		// chunks that were not loaded) when it happened and those that spawn afterwards.
		if (!dormant() && !allied() && !decoy() && !forged && this.tickCount % 20 == 0
				&& level.dimension().equals(Latveria.LEVEL) && Latveria.generatorOff(level.getServer())
				&& !LatveriaPlan.ARENA_BOX.inflate(6).contains(this.position())) {
			becomeStatue(null);
			DoomFx.bill(level, ModParticles.DOOM_DOOM_SMOKE, this.position().add(0, 1.2, 0), 1.2);
		}
		if (dormant()) {
			this.getNavigation().stop();
			this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
			this.yHeadRot = this.yBodyRot;
			this.setXRot(34.0F);
			return;
		}
		actionTicks++;
			if (this.isDeadOrDying()) return;
			if (Latveria.boss() != null && Latveria.boss().ascending()) {
				// Everything in the hall stops to watch a god being made.
				this.getNavigation().stop();
				return;
			}
			if (decoy()) {
				DoctorDoom doom = Latveria.boss();
				if (++decoyAge > DECOY_LIFE || doom == null || !doom.isAlive() || doom.isDeadOrDying()) {
					DoomFx.bill(level, ModParticles.DOOM_DOOM_SMOKE, this.position().add(0, 1.2, 0), 1.6);
					DoomFx.sound(level, this.position(), SoundEvents.FIRE_EXTINGUISH, 1f, 0.6f);
					this.discard();
					return;
				}
			}
		if (!forged && Latveria.conquered(level.getServer())) {
			ServerPlayer owner = Latveria.anyOwner(level.getServer());
			becomeStatue(owner);
			return;
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
			if (LatveriaHunt.hunter(this) && LatveriaHunt.drive(level, this)) return;
			idle(level);
			return;
		}
		this.getLookControl().setLookAt(target, 30, 30);
		double d = this.distanceTo(target);
		switch (variant()) {
			case SOLDIER, ARCANIST -> {
				if (d > 13) this.getNavigation().moveTo(target, 1.0);
				else if (d < 5) retreat(target);
				else if (this.getNavigation().isDone() && this.random.nextInt(25) == 0) strafe(target);
					if (cooldown <= 0 && d <= 20 && this.hasLineOfSight(target) && mayAttack(level, target)) begin(variant() == SOLDIER ? 40 : 70);
			}
			case BASTION -> {
				this.getNavigation().moveTo(target, 1.0);
				if (cooldown <= 0 && d <= 13 && mayAttack(level, target)) begin(50);
			}
			case INTERCEPTOR -> {
				flyer(level, target);
			}
			case ENGINEER -> {
				if (d > 3.6) this.getNavigation().moveTo(target, 1.1);
				if (cooldown <= 0 && d <= 4.4 && mayAttack(level, target)) {
					DoomCombat.hurt(level, target, this, BLOW[ENGINEER]);
					this.swing(InteractionHand.MAIN_HAND);
					cooldown = 32 + this.random.nextInt(16);
				}
			}
			default -> {
				this.getNavigation().moveTo(target, 1.2);
				if (cooldown <= 0 && d <= 7 && mayAttack(level, target)) begin(40);
			}
		}
	}

	private void begin(int nextCooldown) {
		setAction(A_ATTACK);
		cooldown = nextCooldown + this.random.nextInt(nextCooldown / 2 + 1) + ATTACK_TICKS[variant()];
		aim = null;
		struck.clear();
			if (variant() != INTERCEPTOR) this.getNavigation().stop();
		}

		private boolean airborne() { return flag(FLAG_FLYING); }

		/** Turns the whole body, not only the head: a hovering robot has no legs to drag the body round. */
		private void face(Vec3 at) {
			float yaw = (float) Math.toDegrees(Math.atan2(-(at.x - this.getX()), at.z - this.getZ()));
			this.setYRot(yaw);
			this.yBodyRot = yaw;
			this.yHeadRot = yaw;
		}

		/**
		 * The Interceptor's rhythm: it circles low enough to be shot at, dives, lands in a daze (the window for the player),
		 * scrambles on foot for a moment and takes off again.
		 */
		private void flyer(ServerLevel level, LivingEntity target) {
			if (airborne()) {
				face(target.position());
				double a = this.tickCount * 0.05 + this.getId();
				hover(target.position().add(Math.cos(a) * 7, 5, Math.sin(a) * 7), 0.3);
				if (--airTimer <= 0 && cooldown <= 0 && this.distanceTo(target) <= 26 && mayAttack(level, target)) begin(8);
				return;
			}
			groundStep(level, target);
		}

		private void groundStep(ServerLevel level, LivingEntity target) {
			if (meleeCd > 0) meleeCd--;
			if (--groundTimer <= 0) {
				takeOff(level);
				return;
			}
			if (target == null || groundTimer > 40) {
				this.getNavigation().stop();
				if (target != null) face(target.position());
				return;
			}
			this.getNavigation().moveTo(target, 1.2);
			if (meleeCd <= 0 && this.distanceTo(target) <= 3.6) {
				DoomCombat.hurt(level, target, this, BLOW[INTERCEPTOR] * 0.7f);
				this.swing(InteractionHand.MAIN_HAND);
				meleeCd = 26;
			}
		}

		private void takeOff(ServerLevel level) {
			setFlag(FLAG_FLYING, true);
			this.setNoGravity(true);
			airTimer = 100 + this.random.nextInt(50);
			this.getNavigation().stop();
			this.setDeltaMovement(0, 0.7, 0);
			DoomVfx.on(level, DoomVfx.JET, this, 1, 30, DoomVfx.GOLD);
			DoomFx.sound(level, this.position(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.8f, 1.2f);
		}

		private void land() {
			setFlag(FLAG_FLYING, false);
			this.setNoGravity(false);
			groundTimer = 80;
			meleeCd = 20;
		}

	/**
	 * Attack tokens: at most two robots swing or fire at the same target at once, the rest hold, reposition and wait their
	 * turn, so a squad comes in waves with gaps a player can use instead of one wall of blows.
	 */
	private boolean mayAttack(ServerLevel level, LivingEntity target) {
		int busy = 0;
		for (Doombot b : level.getEntitiesOfClass(Doombot.class, target.getBoundingBox().inflate(32), b -> b != this && b.isAlive() && b.allied() == allied())) {
			if (b.action() == A_ATTACK && target.getUUID().equals(b.targetId) && ++busy >= 2) {
				cooldown = 10 + this.random.nextInt(20);
				return false;
			}
		}
		return true;
	}

	/** Sidesteps across the line of fire between volleys. */
	private void strafe(LivingEntity target) {
		Vec3 to = target.position().subtract(this.position());
		Vec3 side = new Vec3(-to.z, 0, to.x).normalize().scale(this.random.nextBoolean() ? 4 : -4);
		this.getNavigation().moveTo(this.getX() + side.x, this.getY(), this.getZ() + side.z, 0.9);
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
		if (allied() && guarding && parked() && home != null) {
			paradeIdle(level);
			return;
		}
		if (allied() && !guarding && commander != null && level.getEntity(commander) instanceof ServerPlayer owner) {
			double d = this.distanceTo(owner);
			if (d > 34) this.teleportTo(owner.getX() + (this.random.nextDouble() - 0.5) * 4, owner.getY(), owner.getZ() + (this.random.nextDouble() - 0.5) * 4);
			else if (variant() == INTERCEPTOR && !airborne()) groundStep(level, null);
			else if (variant() == INTERCEPTOR) hover(owner.position().add(Math.cos(this.getId()) * 3, 4, Math.sin(this.getId()) * 3), 0.3);
			else if (d > 6) this.getNavigation().moveTo(owner, 1.15);
			else this.getNavigation().stop();
			return;
		}
		if (home == null) home = this.position();
		if (variant() == INTERCEPTOR && !airborne()) groundStep(level, null);
		else if (variant() == INTERCEPTOR) hover(home.add(Math.cos(this.tickCount * 0.03) * 4, 5, Math.sin(this.tickCount * 0.03) * 4), 0.2);
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
					// guard up, charge behind the shield, slam, then a long open recovery
					if (t == 4) {
						setFlag(FLAG_SHIELD, true);
						DoomVfx.on(level, DoomVfx.SHIELD, this, 1.3f, 26, DoomVfx.GREEN);
						DoomFx.sound(level, eye, SoundEvents.IRON_GOLEM_REPAIR, 1f, 0.6f);
					}
					if (t == 14 && target != null) {
						Vec3 d = target.position().subtract(this.position());
						aim = new Vec3(d.x, 0, d.z).normalize();
						DoomFx.sound(level, eye, SoundEvents.RAVAGER_ROAR, 0.7f, 0.8f);
					}
					if (t >= 14 && t < 24 && aim != null) {
						this.setDeltaMovement(aim.x * 0.62, this.getDeltaMovement().y, aim.z * 0.62);
						for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(1.1, 0.2, 1.1), this::foe)) {
							if (struck.add(e.getUUID()) && DoomCombat.hurt(level, e, this, BLOW[BASTION])) e.knockback(1.8, -aim.x, -aim.z);
						}
					}
					if (t == 25) {
						DoomVfx.at(level, DoomVfx.SLAM, this.position(), 5.0f, 12, DoomVfx.GREEN);
						DoomFx.sound(level, this.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.2f);
						for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(4.0, 1.5, 4.0), this::foe)) {
							if (struck.add(e.getUUID()) && DoomCombat.hurt(level, e, this, BLOW[BASTION] * 0.7f)) {
								Vec3 push = e.position().subtract(this.position()).normalize();
								e.knockback(1.2, -push.x, -push.z);
							}
						}
					}
					if (t == 30) setFlag(FLAG_SHIELD, false);
				}
				case INTERCEPTOR -> {
					if (t == 1) DoomVfx.on(level, DoomVfx.JET, this, 1, 36, DoomVfx.GOLD);
					if (target != null && t <= 33) face(target.position());
					if (t <= 16) hover(this.position().add(0, 3, 0), 0.3);
					if (t == 20 && target != null) aim = target.position();
					if (t > 23 && t <= 33 && aim != null) hover(aim.add(0, 0.3, 0), 1.2);
					if (t == 33) {
						land();
						DoomFx.flat(level, ModParticles.DOOM_SLAM_WAVE, this.position(), 4.5);
						DoomFx.sound(level, this.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 1.4f);
						for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(3.4, 2.5, 3.4), this::foe)) {
							DoomCombat.hurt(level, e, this, BLOW[INTERCEPTOR]);
						}
					}
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

	/** The shield covers the front half only: a blow from behind lands in full. */
	private boolean fromFront(DamageSource source) {
		Vec3 at = source.getSourcePosition();
		if (at == null) return true;
		Vec3 to = new Vec3(at.x - this.getX(), 0, at.z - this.getZ());
		if (to.lengthSqr() < 1e-4) return true;
		float yaw = this.yBodyRot * ((float) Math.PI / 180f);
		Vec3 look = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		return look.dot(to.normalize()) > 0.1;
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
			if (flag(FLAG_SHIELD) && fromFront(source)) {
				amount *= 0.3f;
				DoomFx.sound(level, this.position().add(0, 1.2, 0), SoundEvents.SHIELD_BLOCK.value(), 0.9f, 0.8f);
			}
			else if (!Latveria.generatorOff(level.getServer()) && !level.getEntitiesOfClass(Doombot.class, this.getBoundingBox().inflate(7),
					b -> b != this && b.isAlive() && b.flag(FLAG_SHIELD) && b.allied() == allied()).isEmpty()) {
				amount *= 0.6f;
				DoomFx.bill(level, ModParticles.DOOM_IRON_BASTION, this.position().add(0, 1, 0), 1.4);
			}
		}
		if (!decoy()) amount = Math.min(amount, this.getMaxHealth() * MAX_SHARE);
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof LivingEntity attacker && foe(attacker)) targetId = attacker.getUUID();
		if (hurt && LatveriaHunt.hunter(this) && source.getEntity() instanceof ServerPlayer sp) LatveriaHunt.attacked(level, sp);
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
			if (decoy()) {
				// The mask cracks: sparks, a burst of smoke and Doom's laughter from wherever he really is.
				Vec3 at = this.position().add(0, 1.4, 0);
				DoomVfx.at(level, DoomVfx.IMPACT, at, 1.4f, 14, DoomVfx.GOLD);
				DoomFx.bill(level, ModParticles.DOOM_DOOM_SMOKE, at, 2);
				DoomFx.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 1.6f);
				DoomFx.sound(level, at, SoundEvents.IRON_GOLEM_DEATH, 1f, 1.6f);
				DoomFx.line(level, at, 64, "doom.decoy_revealed");
				this.spawnAtLocation(level, new ItemStack(ModItems.DOOM_CIRCUIT));
				return;
			}
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

	/** Taken out of the world some other way (a command, another mod): its squad still counts it as fallen. */
	@Override
	public void remove(RemovalReason reason) {
		if (reason == RemovalReason.DISCARDED && !this.dead && !allied() && this.level() instanceof ServerLevel level) Latveria.onBotDeath(level.getServer(), squad);
		super.remove(reason);
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
		if (parked() && home != null) {
			output.putFloat("ParkYaw", parkYaw);
			output.putDouble("HomeX", home.x);
			output.putDouble("HomeY", home.y);
			output.putDouble("HomeZ", home.z);
		}
		output.putBoolean("Decoy", decoy());
		output.putBoolean("Forged", forged);
		output.putBoolean("Dormant", dormant());
		if (commander != null) output.putString("Commander", commander.toString());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(VARIANT, Math.clamp(input.getIntOr("Variant", 0), 0, 5));
		squad = input.getIntOr("Squad", -1);
		setFlag(FLAG_ALLIED, input.getBooleanOr("Allied", false));
		setFlag(FLAG_DORMANT, input.getBooleanOr("Dormant", false));
		guarding = input.getBooleanOr("Guarding", false);
		float savedYaw = input.getFloatOr("ParkYaw", Float.NaN);
		if (!Float.isNaN(savedYaw)) {
			parkYaw = savedYaw;
			home = new Vec3(input.getDoubleOr("HomeX", this.getX()), input.getDoubleOr("HomeY", this.getY()), input.getDoubleOr("HomeZ", this.getZ()));
			saluted = true;
		}
		String id = input.getStringOr("Commander", "");
		commander = id.isEmpty() ? null : UUID.fromString(id);
		applyVariant();
		if (dormant()) {
			setFlag(FLAG_FLYING, false);
			this.setNoGravity(false);
			this.setXRot(34.0F);
		}
		if (input.getBooleanOr("Decoy", false)) makeDecoy();
		if (input.getBooleanOr("Forged", false)) {
			forged = true;
			this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH[variant()] * 1.2);
		}
	}
}
