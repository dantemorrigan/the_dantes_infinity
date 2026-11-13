package com.dantemorrigan.gauntlet.doom;

import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.stormbreaker.Stormbreaker;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Victor von Doom in his throne room. Three stages: armor, gauntlet bolts and Doombots; then rifts, sorcery and the
 * Seven Suns; and at the end the Latverian Edict, which marks the whole hall except the sector whose seal node was
 * broken. Every heavy attack leaves him exposed for a moment, and that is when blows count for more.
 *
 * <p>He is never saved. If every challenger dies or leaves, {@link Latveria} removes him and the fight starts over.
 */
public class DoctorDoom extends Monster {
	public static final int A_IDLE = 0, A_INTRO = 1, A_COMBO = 2, A_BOLT = 3, A_BASTION = 4, A_DROP = 5, A_SLAM = 6, A_SHIFT = 7,
			A_RIFT = 8, A_SORCERY = 9, A_HEX = 10, A_SUNS = 11, A_NOVA = 12, A_EDICT = 13, A_LIGHTNING = 14, A_CLEAVE = 15,
			A_SMASH = 16, A_STAGGER = 17, A_DEFEAT = 18, A_TAUNT = 19;
	/** Clip of each action, in action order. */
	public static final String[] CLIPS = {"idle", "intro", "armored_combo", "gauntlet_bolt", "iron_bastion", "doombot_drop", "jet_slam",
			"phase_shift", "rift_step", "sorcery_bolt", "hex_barrage", "seven_suns", "arcane_nova", "latverian_edict", "lightning_call",
			"cleave", "overhead_smash", "stagger_heavy", "defeat", "taunt"};
	private static final int[] TICKS = {0, 100, 39, 38, 87, 53, 59, 42, 38, 55, 48, 119, 60, 126, 52, 30, 32, 32, 72, 48};
	public static final int FLAG_FIGHT = 1, FLAG_SHIELD = 2, FLAG_EXPOSED = 4, FLAG_CASTING = 8;
	public static final int EDICT_BLAST = 80;
	private static final float MAX_HIT = 45;

	private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(DoctorDoom.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> FLAGS = SynchedEntityData.defineId(DoctorDoom.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(DoctorDoom.class, EntityDataSerializers.INT);

	private final ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("entity.infinity_gauntlet.doctor_doom"),
			BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
	/** Client: tickCount when the current action began. */
	public int actionStartTick;
	private boolean passive;
	private int actionTicks, cooldown = 30, exposed, blowsTaken, sinceSuns = 200, sinceEdict = 400, sinceDrop = 100;
	private float castDamage;
	private UUID targetId;
	private Vec3 aim, from;
	private final boolean[] safe = new boolean[4];
	private boolean nodesUp;
	private final List<Vec3> suns = new ArrayList<>();
	private final Set<UUID> challengers = new HashSet<>();

	public DoctorDoom(EntityType<? extends DoctorDoom> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.xpReward = 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 480).add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.FOLLOW_RANGE, 64).add(Attributes.STEP_HEIGHT, 1.2);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ACTION, A_IDLE);
		builder.define(FLAGS, 0);
		builder.define(PHASE, 1);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (ACTION.equals(accessor)) this.actionStartTick = this.tickCount;
	}

	public int action() { return this.entityData.get(ACTION); }

	public int phase() { return this.entityData.get(PHASE); }

	public boolean flag(int f) { return (this.entityData.get(FLAGS) & f) != 0; }

	public boolean fighting() { return flag(FLAG_FIGHT); }

	public Set<UUID> challengers() { return challengers; }

	private void setFlag(int f, boolean on) {
		int v = this.entityData.get(FLAGS);
		this.entityData.set(FLAGS, on ? v | f : v & ~f);
	}

	private void setAction(int a) {
		this.entityData.set(ACTION, a, true);
		this.actionTicks = 0;
		this.aim = null;
		setFlag(FLAG_SHIELD, false);
		setFlag(FLAG_CASTING, false);
	}

	/** He rises from the throne and speaks; the fight opens when the scene ends. */
	public void intro() {
		this.setInvulnerable(true);
		setAction(A_INTRO);
	}

	private void expose(int ticks) {
		exposed = Math.max(exposed, ticks);
		setFlag(FLAG_EXPOSED, true);
	}

	private ServerPlayer target(ServerLevel level) {
		ServerPlayer current = targetId == null ? null : level.getServer().getPlayerList().getPlayer(targetId);
		if (current != null && valid(current)) return current;
		ServerPlayer best = null;
		for (ServerPlayer p : level.players()) {
			if (valid(p) && (best == null || p.distanceToSqr(this) < best.distanceToSqr(this))) best = p;
		}
		targetId = best == null ? null : best.getUUID();
		return best;
	}

	private boolean valid(ServerPlayer p) {
		return p.isAlive() && !p.isSpectator() && !p.isCreative() && p.level() == this.level() && LatveriaPlan.ARENA_BOX.inflate(3).contains(p.position());
	}

	private boolean foe(LivingEntity e) {
		return e instanceof Player p && !p.isSpectator() && !p.isCreative() || e instanceof Doombot b && b.allied();
	}

	private void face(Vec3 p) {
		float yaw = (float) (Mth.atan2(p.z - this.getZ(), p.x - this.getX()) * Mth.RAD_TO_DEG) - 90f;
		this.setYRot(yaw);
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
	}

	private Vec3 hand() {
		Vec3 look = Vec3.directionFromRotation(0, this.getYRot());
		return this.position().add(look.scale(1.1)).add(0, 1.7, 0);
	}

	// ------------------------------------------------------------------ ticking

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		actionTicks++;
		bar.setProgress(this.getHealth() / this.getMaxHealth());
		if (this.isDeadOrDying()) return;
		int action = action();
		if (action == A_INTRO) {
			this.getNavigation().stop();
			if (actionTicks == 12) DoomFx.line(level, this.position(), 64, "doom.intro_1");
			if (actionTicks == 62) DoomFx.line(level, this.position(), 64, "doom.intro_2");
			if (actionTicks >= TICKS[A_INTRO]) {
				this.setInvulnerable(false);
				setFlag(FLAG_FIGHT, true);
				setAction(A_IDLE);
			}
			return;
		}
		if (!fighting()) return;
		if (exposed > 0 && --exposed == 0) setFlag(FLAG_EXPOSED, false);
		sinceSuns++;
		sinceEdict++;
		sinceDrop++;
		ServerPlayer target = target(level);
		for (ServerPlayer p : level.players()) if (valid(p)) challengers.add(p.getUUID());
		if (action != A_IDLE) {
			this.getNavigation().stop();
			act(level, target, action, actionTicks);
			if (action() == action && actionTicks >= TICKS[action]) setAction(A_IDLE);
			return;
		}
		// The stages turn at two thirds and one third of his health.
		float hp = this.getHealth() / this.getMaxHealth();
		if (phase() == 1 && hp <= 0.66f) { this.entityData.set(PHASE, 2); DoomFx.line(level, this.position(), 64, "doom.phase_2"); setAction(A_SHIFT); return; }
		if (phase() == 2 && hp <= 0.33f) { this.entityData.set(PHASE, 3); DoomFx.line(level, this.position(), 64, "doom.phase_3"); sinceEdict = 340; setAction(A_NOVA); return; }
		if (target == null) {
			this.getNavigation().stop();
			return;
		}
		this.getLookControl().setLookAt(target, 30, 30);
		if (passive) { this.getNavigation().stop(); return; }
		double d = this.distanceTo(target);
		if (d > 3.2) this.getNavigation().moveTo(target, 1.0);
		else this.getNavigation().stop();
		if (cooldown > 0) { cooldown--; return; }
		choose(level, target, d);
	}

	private void choose(ServerLevel level, ServerPlayer target, double d) {
		int phase = phase();
		float r = this.random.nextFloat();
		cooldown = phase == 1 ? 26 : phase == 2 ? 20 : 14;
		face(target.position());
		if (phase == 3 && sinceEdict > 420) { sinceEdict = 0; setAction(A_EDICT); return; }
		if (phase >= 2 && sinceSuns > (phase == 2 ? 480 : 700)) { sinceSuns = 0; setAction(A_SUNS); return; }
		if (phase == 1 && blowsTaken >= 5) { blowsTaken = 0; setAction(A_BASTION); return; }
		if (d <= 4.4) {
			setAction(r < 0.5f ? A_COMBO : r < 0.75f || phase == 1 ? A_SMASH : A_CLEAVE);
			return;
		}
		if (phase == 1) {
			long bots = level.getEntitiesOfClass(Doombot.class, LatveriaPlan.ARENA_BOX, b -> b.isAlive() && !b.allied()).size();
			if (sinceDrop > 300 && bots < 3 && r < 0.4f) { sinceDrop = 0; setAction(A_DROP); }
			else setAction(r < 0.6f ? A_BOLT : A_SLAM);
		} else if (phase == 2) {
			setAction(r < 0.3f ? A_RIFT : r < 0.55f ? A_SORCERY : r < 0.8f ? A_HEX : A_BOLT);
		} else {
			setAction(r < 0.3f ? A_LIGHTNING : r < 0.5f ? A_HEX : r < 0.7f ? A_RIFT : r < 0.85f ? A_SLAM : A_SORCERY);
		}
	}

	private void act(ServerLevel level, ServerPlayer target, int action, int t) {
		Vec3 at = this.position();
		if (target != null && action != A_SLAM && action != A_EDICT && action != A_SUNS && action != A_STAGGER && t < 20) face(target.position());
		switch (action) {
			case A_COMBO -> {
				if (t == 8) DoomFx.sound(level, at, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.6f);
				if (t == 12 || t == 17 || t == 20) {
					DoomFx.bill(level, ModParticles.DOOM_ARMORED_COMBO, hand(), 0);
					strike(level, hand(), 3.4, t == 20 ? 14 : 9, t == 20 ? 1.2 : 0.4);
				}
			}
			case A_BOLT -> {
				if (t == 10 && target != null) {
					aim = target.position().add(0, 1, 0);
					DoomVfx.on(level, DoomVfx.CHARGE, this, 0.6f, 9, DoomVfx.GOLD);
					DoomFx.sound(level, at, SoundEvents.BEACON_POWER_SELECT, 1f, 1.6f);
				}
				if (t == 18 && aim != null) {
					Vec3 dir = aim.subtract(hand()).normalize();
					DoomCombat.fire(level, this, hand(), dir.scale(2.2), 12, 0.35, 1.5, 40, null, 0, DoomVfx.GOLD, 0.32f, this::foe);
					DoomFx.sound(level, at, SoundEvents.BLAZE_SHOOT, 1.3f, 0.8f);
				}
			}
			case A_BASTION -> {
				if (t == 12) {
					setFlag(FLAG_SHIELD, true);
					DoomFx.line(level, at, 64, "doom.bastion");
					DoomFx.sound(level, at, SoundEvents.IRON_GOLEM_REPAIR, 1.4f, 0.5f);
				}
				if (t == 12) DoomVfx.on(level, DoomVfx.SHIELD, this, 2.0f, 49, DoomVfx.GREEN);
				if (t == 61) {
					setFlag(FLAG_SHIELD, false);
					DoomFx.bill(level, ModParticles.DOOM_DOOM_SMOKE, at.add(0, 1.4, 0), 1.6);
					expose(46);
				}
			}
			case A_DROP -> {
				if (t == 12) {
					suns.clear();
					for (int i = 0; i < 2; i++) {
						Vec3 spot = clampToArena(at.add((this.random.nextDouble() - 0.5) * 12, 0, (this.random.nextDouble() - 0.5) * 12));
						suns.add(spot);
						DoomFx.flat(level, ModParticles.DOOM_DROP_MARKER, spot, 0);
					}
					DoomFx.line(level, at, 64, "doom.doombots");
				}
				if (t == 28) for (Vec3 spot : suns) DoomFx.bill(level, ModParticles.DOOM_DOOMBOT_DROP, spot.add(0, 2, 0), 0);
				if (t == 32) for (Vec3 spot : suns) {
					Doombot bot = ModEntities.DOOMBOT.create(level, EntitySpawnReason.EVENT);
					if (bot == null) continue;
					bot.setup(Doombot.SOLDIER, -1);
					bot.snapTo(spot.x, spot.y, spot.z, this.getYRot(), 0);
					level.addFreshEntity(bot);
					DoomFx.flat(level, ModParticles.DOOM_SLAM_WAVE, spot, 3);
				}
			}
			case A_SLAM -> {
				if (t == 7) { DoomVfx.on(level, DoomVfx.JET, this, 1, 22, DoomVfx.GOLD); DoomFx.sound(level, at, SoundEvents.FIRECHARGE_USE, 1.4f, 0.6f); }
				if (t == 8) { from = at; aim = target == null ? at : clampToArena(target.position()); DoomFx.flat(level, ModParticles.DOOM_DROP_MARKER, aim, 2.4); }
				if (t > 8 && t <= 28 && aim != null && from != null) {
					float k = (t - 8) / 20f;
					Vec3 p = from.lerp(aim, k).add(0, Math.sin(k * Math.PI) * 5, 0);
					this.setDeltaMovement(Vec3.ZERO);
					this.setPos(p.x, p.y, p.z);
					face(aim);
				}
				if (t == 28) {
					DoomFx.flat(level, ModParticles.DOOM_SLAM_WAVE, this.position(), 0);
					DoomFx.bill(level, ModParticles.DOOM_JET_SLAM, this.position().add(0, 1, 0), 0);
					DoomFx.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 0.7f);
					DoomFx.near(level, at, 40, DoomFx.SHAKE, 0, 1.2f, 14);
					strike(level, this.position(), 5.5, 16, 1.4);
					expose(50);
				}
			}
			case A_SHIFT, A_NOVA -> {
				this.setInvulnerable(t < (action == A_SHIFT ? 33 : 29));
				if (t == 5) DoomFx.bill(level, ModParticles.DOOM_PHASE_SHIFT, at.add(0, 1.4, 0), 0);
				if (t == 18) DoomFx.sound(level, at, SoundEvents.WARDEN_SONIC_BOOM, 1.2f, 1.2f);
				if (t == (action == A_SHIFT ? 33 : 29)) {
					DoomFx.bill(level, ModParticles.DOOM_PHASE_SHIFT, at.add(0, 1.4, 0), 2);
					DoomFx.flat(level, ModParticles.DOOM_SLAM_WAVE, at, 9);
					DoomFx.near(level, at, 40, DoomFx.FLASH, 0, 0.6f, 12);
					for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(9, 3, 9), this::foe)) {
						if (DoomCombat.hurt(level, e, this, 8)) {
							Vec3 push = e.position().subtract(at).normalize();
							e.knockback(1.6, -push.x, -push.z);
						}
					}
				}
			}
			case A_RIFT -> {
				if (t == 7) { DoomFx.bill(level, ModParticles.DOOM_RIFT_STEP, at.add(0, 1.4, 0), 0); DoomFx.sound(level, at, SoundEvents.ENDERMAN_TELEPORT, 1.2f, 0.6f); }
				if (t == 14) {
					Vec3 to = target == null ? LatveriaPlan.ARENA : clampToArena(target.position().add(Vec3.directionFromRotation(0, target.getYRot()).scale(-6)));
					this.teleportTo(to.x, LatveriaPlan.FLOOR, to.z);
					DoomFx.bill(level, ModParticles.DOOM_RIFT_STEP, to.add(0, 1.4, 0), 0);
					if (target != null) face(target.position());
				}
				if (t == 21) DoomFx.bill(level, ModParticles.DOOM_DOOM_SMOKE, at.add(0, 1, 0), 2);
				if (t == 30) cooldown = 0;
			}
			case A_SORCERY -> {
				if (t == 12) { DoomVfx.on(level, DoomVfx.CHARGE, this, 0.7f, 10, DoomVfx.GREEN); DoomFx.sound(level, at, SoundEvents.EVOKER_PREPARE_ATTACK, 1.2f, 0.9f); }
				if (t == 22) {
					Vec3 fwd = Vec3.directionFromRotation(0, this.getYRot());
					for (int i = -1; i <= 1; i++) {
						Vec3 dir = fwd.yRot(i * 0.5f).add(0, 0.15, 0).normalize();
						DoomCombat.fire(level, this, hand(), dir.scale(0.62), 8, 0.35, 0, 110, target, 0.08, DoomVfx.GREEN, 0.40f, this::foe);
					}
					DoomFx.sound(level, at, SoundEvents.EVOKER_CAST_SPELL, 1.3f, 0.8f);
				}
			}
			case A_HEX -> {
				if ((t == 8 || t == 18 || t == 28 || t == 38) && target != null) {
					Vec3 lead = target.position().add(0, 1, 0).add(target.getDeltaMovement().scale(6));
					for (int i = 0; i < (t == 38 ? 2 : 1); i++) {
						Vec3 dir = lead.subtract(hand()).normalize().yRot(i == 0 ? 0 : 0.25f);
						DoomCombat.fire(level, this, hand(), dir.scale(1.7), 7, 0.3, 0, 50, null, 0, DoomVfx.GREEN, 0.32f, this::foe);
					}
					DoomFx.sound(level, at, SoundEvents.SHULKER_SHOOT, 1f, 1.3f);
				}
			}
			case A_SUNS -> {
				if (t == 1) { DoomFx.line(level, at, 64, "doom.seven_suns"); suns.clear(); }
				if (t == 18) { DoomVfx.on(level, DoomVfx.ORBIT, this, 7, 70, DoomVfx.SUN); DoomFx.sound(level, at, SoundEvents.BEACON_ACTIVATE, 1.6f, 0.6f); }
				if (t >= 36 && t <= 78 && (t - 36) % 7 == 0 && target != null) {
					Vec3 mark = clampToArena(target.position().add(target.getDeltaMovement().multiply(8, 0, 8)));
					suns.add(new Vec3(mark.x, t + 13, mark.z));
					Vec3 ground = new Vec3(mark.x, LatveriaPlan.FLOOR, mark.z);
					DoomVfx.at(level, DoomVfx.EDICT, ground.add(0, 0.05, 0), 1.7f, 13, DoomVfx.SUN);
					// One of the seven leaves its orbit and falls on the mark in 13 ticks.
					int left = 7 - (t - 36) / 7;
					Vec3 from = at.add(Math.cos(t * 0.3) * 1.6, 3.2, Math.sin(t * 0.3) * 1.6);
					DoomVfx.bolt(level, DoomVfx.nextId(), this, null, from, ground.add(0, 0.6, 0).subtract(from).scale(1 / 13.0), 0.6f, 0, 13, DoomVfx.SUN);
					DoomVfx.on(level, DoomVfx.ORBIT, this, left - 1, Math.max(1, 92 - t), DoomVfx.SUN);
				}
				for (int i = suns.size() - 1; i >= 0; i--) {
					Vec3 s = suns.get(i);
					if ((int) s.y != t) continue;
					suns.remove(i);
					Vec3 hit = new Vec3(s.x, LatveriaPlan.FLOOR, s.z);
					DoomVfx.at(level, DoomVfx.IMPACT, hit.add(0, 0.5, 0), 2.6f, 10, DoomVfx.SUN);
					DoomVfx.at(level, DoomVfx.SLAM, hit.add(0, 0.05, 0), 3.2f, 12, DoomVfx.SUN);
					DoomFx.sound(level, hit, SoundEvents.GENERIC_EXPLODE.value(), 1f, 1.3f);
					strike(level, hit, 2.7, 14, 0.5);
				}
				if (t == 92) expose(46);
			}
			case A_EDICT -> edict(level, t);
			case A_LIGHTNING -> {
				if (t == 12 && target != null) {
					aim = clampToArena(target.position());
					DoomFx.flat(level, ModParticles.DOOM_DROP_MARKER, aim, 2);
				}
				if (t == 31 && aim != null) {
					LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
					if (bolt != null) { bolt.setVisualOnly(true); bolt.snapTo(aim.x, aim.y, aim.z); level.addFreshEntity(bolt); }
					strike(level, aim, 3.2, 16, 0.6);
					expose(20);
				}
			}
			case A_CLEAVE -> {
				if (t == 14) { DoomFx.bill(level, ModParticles.DOOM_ARMORED_COMBO, hand(), 2.2); strike(level, hand(), 4.2, 12, 1.0); }
			}
			case A_SMASH -> {
				if (t == 14) {
					Vec3 p = this.position().add(Vec3.directionFromRotation(0, this.getYRot()).scale(1.6));
					DoomFx.flat(level, ModParticles.DOOM_SLAM_WAVE, p, 5);
					DoomFx.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1.1f, 0.9f);
					strike(level, p, 4, 15, 1.1);
					expose(24);
				}
			}
			default -> {}
		}
	}

	/** Blows at a point: everyone in reach on the players' side is hurt and thrown back. */
	private void strike(ServerLevel level, Vec3 at, double reach, float damage, double push) {
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(reach, 2.5, reach), this::foe)) {
			if (e.position().subtract(at).horizontalDistance() > reach + 0.4) continue;
			if (!DoomCombat.hurt(level, e, this, damage)) continue;
			Vec3 dir = e.position().subtract(this.position()).normalize();
			e.knockback(push, -dir.x, -dir.z);
		}
	}

	private static Vec3 clampToArena(Vec3 p) {
		var box = LatveriaPlan.ARENA_BOX.deflate(2.5);
		return new Vec3(Mth.clamp(p.x, box.minX, box.maxX), LatveriaPlan.FLOOR, Mth.clamp(p.z, box.minZ + 6, box.maxZ));
	}

	// ------------------------------------------------------------------ the Latverian Edict

	private static Vec3 sector(int i) {
		BlockPos n = LatveriaPlan.SEAL_NODES[i];
		Vec3 c = LatveriaPlan.ARENA;
		return new Vec3(c.x + (n.getX() + 0.5 - c.x) * 0.62, LatveriaPlan.FLOOR, c.z + (n.getZ() + 0.5 - c.z) * 0.62);
	}

	/** 0 north, 1 east, 2 south, 3 west of the arena's centre. */
	public static int sectorOf(Vec3 p) {
		double dx = p.x - LatveriaPlan.ARENA.x, dz = p.z - LatveriaPlan.ARENA.z;
		return Math.abs(dz) >= Math.abs(dx) ? (dz < 0 ? 0 : 2) : (dx > 0 ? 1 : 3);
	}

	private void edict(ServerLevel level, int t) {
		if (t == 1) {
			this.teleportTo(LatveriaPlan.ARENA.x, LatveriaPlan.FLOOR, LatveriaPlan.ARENA.z);
			DoomFx.bill(level, ModParticles.DOOM_RIFT_STEP, this.position().add(0, 1.4, 0), 0);
			DoomFx.line(level, this.position(), 64, "doom.edict");
			castDamage = 0;
			setFlag(FLAG_CASTING, true);
			java.util.Arrays.fill(safe, false);
		}
		if (t == 10) placeNodes(level, true);
		if (t >= 10 && t < EDICT_BLAST && (t - 10) % 30 == 0) {
			for (int i = 0; i < 4; i++) if (!safe[i]) DoomFx.flat(level, ModParticles.DOOM_EDICT_WARNING, sector(i), 1.5);
			DoomFx.sound(level, this.position(), SoundEvents.BEACON_AMBIENT, 2f, 0.5f + t / 160f);
		}
		if (t == EDICT_BLAST) {
			setFlag(FLAG_CASTING, false);
			placeNodes(level, false);
			DoomFx.bill(level, ModParticles.DOOM_LATVERIAN_EDICT, this.position().add(0, 3.2, 0), 0);
			for (int i = 0; i < 4; i++) if (!safe[i]) {
				DoomFx.bill(level, ModParticles.DOOM_EDICT_COLUMN, sector(i).add(0, 2.3, 0), 0);
				DoomFx.flat(level, ModParticles.DOOM_SLAM_WAVE, sector(i), 8);
			}
			DoomFx.sound(level, this.position(), SoundEvents.WARDEN_SONIC_BOOM, 2f, 0.6f);
			DoomFx.near(level, this.position(), 48, DoomFx.SHAKE, 0, 1.6f, 20);
			DoomFx.near(level, this.position(), 48, DoomFx.FLASH, 0, 0.8f, 14);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, LatveriaPlan.ARENA_BOX, this::foe)) {
				if (!safe[sectorOf(e.position())]) DoomCombat.hurt(level, e, this, 30);
			}
			expose(46);
		}
	}

	private void placeNodes(ServerLevel level, boolean up) {
		nodesUp = up;
		for (int i = 0; i < 4; i++) {
			BlockPos p = LatveriaPlan.SEAL_NODES[i];
			level.setBlockAndUpdate(p, up ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(p.below(), up ? Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState() : Blocks.AIR.defaultBlockState());
			if (up) DoomFx.bill(level, ModParticles.DOOM_RUNE_MOTE, p.getCenter(), 4);
		}
	}

	/** A challenger struck a seal node: its sector of the hall is spared by the Edict. */
	public boolean breakNode(ServerLevel level, BlockPos pos) {
		if (!nodesUp || action() != A_EDICT) return false;
		for (int i = 0; i < 4; i++) {
			BlockPos p = LatveriaPlan.SEAL_NODES[i];
			if (safe[i] || !(p.equals(pos) || p.below().equals(pos))) continue;
			safe[i] = true;
			level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(p.below(), Blocks.AIR.defaultBlockState());
			DoomFx.bill(level, ModParticles.DOOM_IMPACT, p.getCenter(), 2.5);
			DoomFx.sound(level, p.getCenter(), SoundEvents.GLASS_BREAK, 1.5f, 0.7f);
			DoomFx.sound(level, p.getCenter(), SoundEvents.BEACON_DEACTIVATE, 1.5f, 1.4f);
			for (ServerPlayer player : level.players()) if (valid(player)) DoomFx.tell(player, "edict.safe");
			return true;
		}
		return false;
	}

	private void interrupt(ServerLevel level) {
		placeNodes(level, false);
		DoomFx.line(level, this.position(), 64, "doom.interrupted");
		DoomFx.bill(level, ModParticles.DOOM_DOOM_SMOKE, this.position().add(0, 1.5, 0), 1.6);
		DoomFx.sound(level, this.position(), SoundEvents.BEACON_DEACTIVATE, 2f, 0.7f);
		setAction(A_STAGGER);
		expose(60);
	}

	// ------------------------------------------------------------------ debug

	/** Debug: starts an action right now (its name is the clip's, e.g. latverian_edict). False if unknown or he is busy dying. */
	public boolean force(String clip) {
		int a = java.util.Arrays.asList(CLIPS).indexOf(clip);
		if (a <= 0 || a == A_INTRO || a == A_DEFEAT || this.isDeadOrDying()) return false;
		if (!fighting()) { this.setInvulnerable(false); setFlag(FLAG_FIGHT, true); }
		ServerPlayer target = this.level() instanceof ServerLevel sl ? target(sl) : null;
		if (target != null) face(target.position());
		setAction(a);
		return true;
	}

	/** Debug: jumps to a stage (1-3) by setting his health to that stage's share. */
	public void stage(int n) {
		n = Mth.clamp(n, 1, 3);
		this.setHealth(this.getMaxHealth() * (n == 1 ? 1f : n == 2 ? 0.65f : 0.32f));
		// Forward: he notices on his next breath and plays the change of stage. Backward: at once.
		if (n <= phase()) this.entityData.set(PHASE, n);
		if (n == 3) sinceEdict = 400;
		if (n >= 2) sinceSuns = 470;
	}

	/** Debug: he stands still and only defends himself with nothing (a training dummy). */
	public boolean togglePassive() {
		passive = !passive;
		return passive;
	}

	public void heal() { this.setHealth(this.getMaxHealth()); }

	/** The Time Stone was turned back next to him: he hangs in the rewound moment, open to blows. */
	public void timeLock(ServerLevel level) {
		if (!fighting() || this.isDeadOrDying() || this.isInvulnerable()) return;
		if (nodesUp) placeNodes(level, false);
		setAction(A_STAGGER);
		expose(70);
		DoomFx.bill(level, ModParticles.DOOM_TIME_PLATFORM, this.position().add(0, 0.2, 0), 0);
		DoomFx.line(level, this.position(), 64, "doom.time");
	}

	/** Removes what a cast left in the hall; called when the fight is called off. */
	public void cleanup(ServerLevel level) {
		if (nodesUp) placeNodes(level, false);
		bar.removeAllPlayers();
	}

	// ------------------------------------------------------------------ damage and defeat

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.is(DamageTypes.GENERIC_KILL)) return super.hurtServer(level, source, amount);
		if (!(amount > 0) || !Float.isFinite(amount) || this.isInvulnerable() || !fighting() || this.isDeadOrDying()) return false;
		if (source.is(DamageTypes.FALL) || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN)
				|| source.is(DamageTypes.LIGHTNING_BOLT) || source.getEntity() instanceof Doombot b && !b.allied()) return false;
		boolean axe = source.is(Stormbreaker.DAMAGE);
		float scaled = Math.min(amount, MAX_HIT);
		if (flag(FLAG_SHIELD)) {
			scaled *= 0.1f;
			DoomFx.sound(level, this.position(), SoundEvents.SHIELD_BLOCK.value(), 1.2f, 0.6f);
		} else if (phase() == 1) scaled *= Latveria.generatorOff(level.getServer()) ? 0.8f : 0.45f;
		if (flag(FLAG_EXPOSED)) scaled *= 1.6f;
		// One blow never skips a stage.
		float floor = this.getMaxHealth() * (phase() == 1 ? 0.66f : phase() == 2 ? 0.33f : 0);
		if (this.getHealth() > floor && floor > 0) scaled = Math.min(scaled, this.getHealth() - floor + 0.5f);
		if (axe) this.invulnerableTime = 0;
		boolean hurt = super.hurtServer(level, source, scaled);
		if (!hurt) return false;
		blowsTaken++;
		DoomFx.bill(level, ModParticles.DOOM_IMPACT, this.position().add(0, 1.6, 0), 1.2);
		if (action() == A_EDICT && actionTicks < EDICT_BLAST && !this.isDeadOrDying()) {
			castDamage += scaled;
			if (axe || castDamage >= 24) interrupt(level);
		}
		return true;
	}

	@Override
	public void die(DamageSource source) {
		boolean was = this.dead;
		super.die(source);
		if (was || !(this.level() instanceof ServerLevel level)) return;
		// Everyone standing in the hall at the end shares the victory, even if the last blow came at once.
		for (ServerPlayer p : level.players()) {
			if (p.isAlive() && !p.isSpectator() && LatveriaPlan.ARENA_BOX.inflate(3).contains(p.position())) challengers.add(p.getUUID());
		}
		if (nodesUp) placeNodes(level, false);
		this.entityData.set(ACTION, A_DEFEAT, true);
		bar.removeAllPlayers();
		DoomFx.line(level, this.position(), 64, "doom.defeat_1");
	}

	@Override
	protected void tickDeath() {
		this.deathTime++;
		this.setDeltaMovement(0, Math.min(0, this.getDeltaMovement().y), 0);
		if (!(this.level() instanceof ServerLevel level)) return;
		if (this.deathTime == 50) DoomFx.line(level, this.position(), 64, "doom.defeat_2");
		if (this.deathTime == 90) {
			DoomFx.bill(level, ModParticles.DOOM_PHASE_SHIFT, this.position().add(0, 1.2, 0), 0);
			DoomFx.bill(level, ModParticles.DOOM_DOOM_SMOKE, this.position().add(0, 1.2, 0), 2);
			DoomFx.sound(level, this.position(), SoundEvents.ENDERMAN_TELEPORT, 1.6f, 0.5f);
		}
		if (this.deathTime >= 100 && !this.isRemoved()) {
			Latveria.onDoomDefeated(level, this);
			this.remove(RemovalReason.KILLED);
		}
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		if (!this.isDeadOrDying()) bar.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		bar.removePlayer(player);
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
	public boolean removeWhenFarAway(double distance) {
		return false;
	}
}
