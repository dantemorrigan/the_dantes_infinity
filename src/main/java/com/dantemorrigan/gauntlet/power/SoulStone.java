package com.dantemorrigan.gauntlet.power;

import com.dantemorrigan.gauntlet.CoopRules;
import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.net.ModStatus;
import com.dantemorrigan.gauntlet.space.SpaceManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Soul stone — soul extraction.
 * <ul>
 *   <li>O on a mob: its soul is drawn out (the body hangs frozen), the mob dies soulless and the soul flies into the
 *       player: +max health and +attack damage per soul, stacking, for a while (refreshed by every new soul).</li>
 *   <li>Shift+O: a wave rolls out and pulls the souls of every enemy in a large radius, killing them.</li>
 * </ul>
 * The bonus uses transient attribute modifiers, so it is never saved into the player data.
 */
public final class SoulStone {
	private static final Identifier HEALTH_ID = InfinityGauntlet.id("soul_health");
	private static final Identifier DAMAGE_ID = InfinityGauntlet.id("soul_damage");
	private static final double WAVE_SPEED = 0.8; // blocks per tick
	private static final int POINT_EXTRACT = 30, POINT_FLIGHT = 22, MASS_EXTRACT = 16, MASS_FLIGHT = 26;

	private static final class Extraction {
		final UUID owner;
		final LivingEntity target;
		final ServerLevel level;
		final int delay, extract, flight;
		boolean frozen, hadNoAi, hadNoGravity, killed;
		int tick;

		Extraction(UUID owner, LivingEntity target, int delay, int extract, int flight) {
			this.owner = owner;
			this.target = target;
			this.level = (ServerLevel) target.level();
			this.delay = delay;
			this.extract = extract;
			this.flight = flight;
		}
	}

	private record Buff(int souls, long until) {}

	private static final List<Extraction> ACTIVE = new ArrayList<>();
	private static final Map<UUID, Buff> BUFFS = new HashMap<>();
	private static final Map<UUID, Long> POINT_CD = new HashMap<>(), MASS_CD = new HashMap<>();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SoulStone::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> BUFFS.remove(h.getPlayer().getUUID()));
		ServerPlayerEvents.AFTER_RESPAWN.register((old, p, alive) -> {
			BUFFS.remove(p.getUUID());
			apply(p, 0);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			for (Extraction e : ACTIVE) unfreeze(e);
			ACTIVE.clear();
			BUFFS.clear();
			POINT_CD.clear();
			MASS_CD.clear();
		});
	}

	public static void action(ServerPlayer player, int action) {
		ItemStack g = GauntletState.activeGauntlet(player);
		if (g.isEmpty() || !GauntletState.inserted(g).contains(Stone.SOUL)) {
			ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.need.soul"));
			return;
		}
		if (SpaceManager.inSpace(player) || player.isSpectator()) return;
		ServerLevel level = player.level();
		long now = level.getGameTime();
		GauntletConfig cfg = GauntletConfig.get();
		if (action == 1) {
			Long cd = MASS_CD.get(player.getUUID());
			if (cd != null && now < cd && !player.isCreative()) {
				ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.soul.mass_cooldown", String.valueOf((cd - now) / 20)));
				return;
			}
			mass(player, level, cfg);
			MASS_CD.put(player.getUUID(), now + cfg.soulMassCooldownTicks);
		} else {
			if (now < POINT_CD.getOrDefault(player.getUUID(), 0L) && !player.isCreative()) return;
			if (burnDoom(player, cfg.soulRange, 30f)) {
				POINT_CD.put(player.getUUID(), now + cfg.soulPointCooldownTicks);
				GauntletState.setInUse(g, EnumSet.of(Stone.SOUL));
				return;
			}
			LivingEntity target = MindStone.lookTarget(player, com.dantemorrigan.gauntlet.cosmos.CosmosTravel.inCosmos(player)?CosmicGauntlet.reach(player,170):cfg.soulRange, cfg.soulPlayerDrain);
			if (target == null || busy(target) || !player.hasLineOfSight(target)) return;
			if (soulless(target)) { ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.soul.none")); return; }
			POINT_CD.put(player.getUUID(), now + cfg.soulPointCooldownTicks);
			if (target instanceof ServerPlayer victim) {
				if (victim.isShiftKeyDown() || !CoopRules.canControlWithGauntlet(player, victim)) return;
				ACTIVE.add(new Extraction(player.getUUID(), target, 0, POINT_EXTRACT, 0));
				ModStatus.send(victim, Component.translatable("ability.infinity_gauntlet.soul_escape"));
			}
			else start(player, target, 0, POINT_EXTRACT, POINT_FLIGHT);
			level.playSound(null, target.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 2.0F, 0.6F);
		}
		GauntletState.setInUse(g, EnumSet.of(Stone.SOUL));
		player.swing(net.minecraft.world.InteractionHand.OFF_HAND, true);
	}

	public static boolean extractLoose(ServerPlayer player) {
		if (SpaceManager.inSpace(player) || player.isSpectator()) return false;
		// Thanos's soul can't be torn out, but the bare stone burns it: a heavy blow instead.
		Vec3 look = player.getViewVector(1f);
		for (com.dantemorrigan.gauntlet.boss.Thanos thanos : player.level().getEntitiesOfClass(com.dantemorrigan.gauntlet.boss.Thanos.class,
				player.getBoundingBox().inflate(10), t -> t.isAlive() && player.hasLineOfSight(t))) {
			Vec3 to = thanos.position().add(0, 1.6, 0).subtract(player.getEyePosition());
			if (to.length() > 10 || to.normalize().dot(look) < 0.94) continue;
			if (!thanos.hurtServer(player.level(), player.damageSources().playerAttack(player), 24f)) return false;
			player.level().sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL, thanos.getX(), thanos.getY() + 1.6, thanos.getZ(), 40, 0.6, 0.9, 0.6, 0.05);
			player.level().playSound(null, thanos.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 2.0F, 0.5F);
			return true;
		}
		if (burnDoom(player, 10, 20f)) return true;
		LivingEntity target = MindStone.lookTarget(player, 8);
		if (target == null || busy(target) || !player.hasLineOfSight(target)) return false;
		if (soulless(target)) { ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.soul.none")); return false; }
		start(player, target, 0, POINT_EXTRACT, POINT_FLIGHT);
		player.level().playSound(null, target.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 2.0F, 0.6F);
		return true;
	}

	private static void mass(ServerPlayer player, ServerLevel level, GauntletConfig cfg) {
		double r = com.dantemorrigan.gauntlet.cosmos.CosmosTravel.inCosmos(player)?155:cfg.soulMassRadius;
		Vec3 c = player.position();
		List<LivingEntity> targets = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r, com.dantemorrigan.gauntlet.cosmos.CosmosTravel.inCosmos(player)?r:r*.5, r),
				e -> e instanceof Enemy && e.isAlive() && !MindStone.isBoss(e) && !soulless(e) && !e.hasCustomName() && !busy(e) && e.position().distanceTo(c) <= r));
		targets.sort(Comparator.comparingDouble(e -> e.position().distanceToSqr(c)));
		if (targets.size() > cfg.soulMassMaxTargets) targets = targets.subList(0, cfg.soulMassMaxTargets);
		ModNetwork.SoulFx wave = new ModNetwork.SoulFx(1, player.getId(), -1, c, (int) r, 0, 0);
		for (ServerPlayer p : PlayerLookup.around(level, c, 160)) ServerPlayNetworking.send(p, wave);
		for (LivingEntity t : targets) start(player, t, (int) (t.position().distanceTo(c) / WAVE_SPEED), MASS_EXTRACT, MASS_FLIGHT);
		level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.6F, 0.5F);
		level.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 3.0F, 0.5F);
	}

	/** Machines have no soul to take: the Doombots are cogs and circuits, whoever commands them. */
	public static boolean soulless(LivingEntity e) {
		return e instanceof com.dantemorrigan.gauntlet.doom.Doombot;
	}

	private static boolean busy(LivingEntity e) {
		return e.entityTags().contains(RealityStone.TAG) || MindStone.holdsEntity(e) || isExtracting(e);
	}

	public static boolean extractingWithGauntlet(ServerPlayer player) {
		return ACTIVE.stream().anyMatch(extraction -> extraction.owner.equals(player.getUUID()));
	}

	public static boolean isExtracting(LivingEntity e) {
		for (Extraction x : ACTIVE) if (x.target == e) return true;
		return false;
	}

	/**
	 * Doctor Doom's soul is his own and stays where it is: like Thanos, he cannot be frozen and drained, so the stone
	 * strikes it instead, a heavy blow that goes through his armor's guard rules like any other.
	 */
	private static boolean burnDoom(ServerPlayer player, double range, float damage) {
		Vec3 look = player.getViewVector(1f);
		for (com.dantemorrigan.gauntlet.doom.DoctorDoom doom : player.level().getEntitiesOfClass(com.dantemorrigan.gauntlet.doom.DoctorDoom.class,
				player.getBoundingBox().inflate(range), d -> d.isAlive() && player.hasLineOfSight(d))) {
			Vec3 to = doom.position().add(0, 1.6, 0).subtract(player.getEyePosition());
			if (to.length() > range || to.normalize().dot(look) < 0.94) continue;
			doom.hurtServer(player.level(), player.damageSources().playerAttack(player), damage);
			player.level().sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL, doom.getX(), doom.getY() + 1.6, doom.getZ(), 40, 0.6, 0.9, 0.6, 0.05);
			player.level().playSound(null, doom.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 2.0F, 0.5F);
			return true;
		}
		return false;
	}

	private static void start(ServerPlayer owner, LivingEntity target, int delay, int extract, int flight) {
		ACTIVE.add(new Extraction(owner.getUUID(), target, delay, extract, flight));
		ModNetwork.SoulFx fx = new ModNetwork.SoulFx(0, owner.getId(), target.getId(), target.position(), delay, extract, flight);
		for (ServerPlayer p : PlayerLookup.around(owner.level(), target.position(), 160)) ServerPlayNetworking.send(p, fx);
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (Iterator<Extraction> it = ACTIVE.iterator(); it.hasNext(); ) {
			Extraction e = it.next();
			e.tick++;
			ServerPlayer owner = server.getPlayerList().getPlayer(e.owner);
			if (owner == null || !owner.isAlive() || owner.level() != e.level || e.target.level() != e.level) {
				unfreeze(e);
				it.remove();
				continue;
			}
			LivingEntity t = e.target;
			if (t instanceof ServerPlayer victim) {
				if (stepPlayerDrain(owner, victim, e.tick)) it.remove();
				continue;
			}
			if (e.tick < e.delay) {
				if (!t.isAlive()) it.remove();
				continue;
			}
			if (!e.killed) {
				if (!t.isAlive()) { // died otherwise during the extraction: no soul
					unfreeze(e);
					it.remove();
					continue;
				}
				if (!e.frozen) freeze(e);
				// The body hangs, lifted a little, while the soul is pulled out of it.
				t.setDeltaMovement(new Vec3(0, 0.015, 0));
				t.hurtMarked = true;
				if ((e.tick - e.delay) % 8 == 0) t.level().playSound(null, t.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.0F, 0.7F);
				if (e.tick >= e.delay + e.extract) {
					unfreeze(e);
					ServerLevel level = e.level;
					com.dantemorrigan.gauntlet.uru.GauntletDamage.hurt(level, t, owner, level.damageSources().playerAttack(owner), Float.MAX_VALUE);
					e.killed = !t.isAlive();
					if (!e.killed) it.remove();
				}
				continue;
			}
			if (e.tick >= e.delay + e.extract + e.flight) {
				it.remove();
				absorb(owner, now);
			}
		}
		// Expired buffs
		for (Iterator<Map.Entry<UUID, Buff>> it = BUFFS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Buff> b = it.next();
			if (now < b.getValue().until()) continue;
			it.remove();
			ServerPlayer p = server.getPlayerList().getPlayer(b.getKey());
			if (p != null) {
				apply(p, 0);
				ServerPlayNetworking.send(p, new ModNetwork.SoulBuff(0, 0));
				p.level().playSound(null, p.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 0.4F);
			}
		}
		// Clear the stone glow once nothing is being extracted for that player.
		if (now % 10 == 0) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				boolean any = false;
				for (Extraction e : ACTIVE) if (e.owner.equals(p.getUUID())) { any = true; break; }
				ItemStack g = GauntletState.activeGauntlet(p);
				if (!any && !g.isEmpty() && GauntletState.inUse(g).contains(Stone.SOUL)) GauntletState.setInUse(g, EnumSet.noneOf(Stone.class));
			}
		}
	}

	private static boolean stepPlayerDrain(ServerPlayer owner, ServerPlayer victim, int tick) {
		GauntletConfig config = GauntletConfig.get();
		ItemStack gauntlet = GauntletState.activeGauntlet(owner);
		if (!config.soulPlayerDrain || gauntlet.isEmpty() || !GauntletState.inserted(gauntlet).contains(Stone.SOUL)
				|| owner.level().getEntity(victim.getUUID()) != victim || !CoopRules.canControlWithGauntlet(owner, victim)
				|| victim.isShiftKeyDown() || victim.distanceToSqr(owner) > Math.pow(com.dantemorrigan.gauntlet.cosmos.CosmosTravel.inCosmos(owner)?170:config.soulRange,2)
				|| !owner.hasLineOfSight(victim)) return true;
		Vec3 from = victim.getEyePosition(), to = owner.getEyePosition();
		Vec3 point = from.lerp(to, (tick % 10) / 10.0);
		owner.level().sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL,
				point.x, point.y, point.z, 3, 0.08, 0.08, 0.08, 0.01);
		if (tick < POINT_EXTRACT) return false;
		float before = victim.getHealth();
		float damage = Math.min(4f, before - 1f);
		if (damage > 0 && com.dantemorrigan.gauntlet.uru.GauntletDamage.hurt(owner.level(), victim, owner, owner.damageSources().playerAttack(owner), damage)) {
			owner.heal(Math.max(0, before - victim.getHealth()));
		}
		return true;
	}

	private static void freeze(Extraction e) {
		e.frozen = true;
		e.hadNoGravity = e.target.isNoGravity();
		e.target.setNoGravity(true);
		if (e.target instanceof Mob m) {
			e.hadNoAi = m.isNoAi();
			m.setNoAi(true);
		}
	}

	private static void unfreeze(Extraction e) {
		if (!e.frozen) return;
		e.frozen = false;
		e.target.setNoGravity(e.hadNoGravity);
		if (e.target instanceof Mob m) m.setNoAi(e.hadNoAi);
	}

	private static void absorb(ServerPlayer p, long now) {
		GauntletConfig cfg = GauntletConfig.get();
		Buff old = BUFFS.get(p.getUUID());
		int souls = Math.min(cfg.soulMaxStacks, (old != null ? old.souls() : 0) + 1);
		BUFFS.put(p.getUUID(), new Buff(souls, now + cfg.soulBuffTicks));
		apply(p, souls);
		p.heal((float) cfg.soulHealthPerSoul + 2f);
		ServerPlayNetworking.send(p, new ModNetwork.SoulBuff(souls, cfg.soulBuffTicks));
		p.level().playSound(null, p.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.9F, 1.2F + souls * 0.02F);
	}

	private static void apply(ServerPlayer p, int souls) {
		GauntletConfig cfg = GauntletConfig.get();
		modifier(p, Attributes.MAX_HEALTH, HEALTH_ID, souls * cfg.soulHealthPerSoul);
		modifier(p, Attributes.ATTACK_DAMAGE, DAMAGE_ID, souls * cfg.soulDamagePerSoul);
		if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
	}

	private static void modifier(ServerPlayer p, Holder<Attribute> attribute, Identifier id, double amount) {
		AttributeInstance inst = p.getAttribute(attribute);
		if (inst == null) return;
		if (amount <= 0) inst.removeModifier(id);
		else inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
	}

	private SoulStone() {}
    public static int cooldown(ServerPlayer p, boolean mass) { return (int)Math.max(0, (mass?MASS_CD:POINT_CD).getOrDefault(p.getUUID(),0L)-p.level().getGameTime()); }

}
