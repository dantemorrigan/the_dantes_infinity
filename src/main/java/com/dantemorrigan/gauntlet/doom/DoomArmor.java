package com.dantemorrigan.gauntlet.doom;

import com.dantemorrigan.gauntlet.CoopRules;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.mixin.ServerGamePacketListenerAccessor;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Doom's armor. The four pieces worn together open six powers:
 * <ul>
 *   <li>Gauntlet Bolt — a fast bolt from the hand that bursts on impact;</li>
 *   <li>Iron Bastion — a shell around the wearer: arrows stop dead, blows lose four fifths, attackers are thrown back;</li>
 *   <li>Jet Flight — toggled; the client flies (so it answers at once), the server keeps the fuel and the fall damage;</li>
 *   <li>Rift Step — a jump to where the wearer looks, slipping through attacks for a second;</li>
 *   <li>Seven Suns — seven suns orbit the wearer; each further cast sends one at the crosshair (hold to keep firing);</li>
 *   <li>Latverian Edict — a seal falls where the wearer looks and a column of light crushes and throws up what is in it.</li>
 * </ul>
 * None of them touches the player's flight abilities, so the armor never fights the Infinity Gauntlet or Stormbreaker.
 *
 * <p>The full set is also a body of its own: {@link #SET_HEALTH} more health, Resistance, Regeneration, Fire Resistance,
 * a stronger arm, a firmer stance, a longer safe fall, and a last stand: a blow that would kill the wearer leaves them
 * on their feet instead, once in {@link #LAST_STAND_COOLDOWN} ticks.
 */
public final class DoomArmor {
	public static final ArmorMaterial MATERIAL = new ArmorMaterial(55,
			Map.of(ArmorType.HELMET, 4, ArmorType.CHESTPLATE, 9, ArmorType.LEGGINGS, 7, ArmorType.BOOTS, 4, ArmorType.BODY, 11),
			18, SoundEvents.ARMOR_EQUIP_NETHERITE, 3.5f, 0.15f,
			TagKey.create(Registries.ITEM, InfinityGauntlet.id("repairs_doom_armor")),
			ResourceKey.create(EquipmentAssets.ROOT_ID, InfinityGauntlet.id("doom")));
	public static final int BOLT = 0, BASTION = 1, JET = 2, RIFT = 3, SUNS = 4, EDICT = 5;
	public static final String[] NAMES = {"gauntlet_bolt", "iron_bastion", "jet_flight", "rift_step", "seven_suns", "latverian_edict"};
	private static final int[] COOLDOWN = {10, 300, 20, 50, 400, 500};
	public static final int BASTION_TICKS = 120, FUEL = 240, SUN_TICKS = 300, SUN_COUNT = 7, EDICT_DELAY = 20;
	public static final double RIFT_RANGE = 24, EDICT_RANGE = 40, EDICT_RADIUS = 5;
	/** What the whole armor gives its wearer: health (30 = fifteen hearts), a strong arm, a firm stance, a fast stride, a long fall. */
	public static final double SET_HEALTH = 30, SET_DAMAGE = 3, SET_KNOCKBACK = 0.6, SET_SPEED = 0.1, SET_FALL = 6;
	/** A blow that would kill the wearer is survived once in this many ticks (5 minutes); they get this share of health back. */
	public static final int LAST_STAND_COOLDOWN = 6000;
	public static final float LAST_STAND_HEALTH = 0.4f;
	private static final Identifier HEALTH_ID = InfinityGauntlet.id("doom_armor_health"), DAMAGE_ID = InfinityGauntlet.id("doom_armor_damage"),
			KNOCKBACK_ID = InfinityGauntlet.id("doom_armor_knockback"), SPEED_ID = InfinityGauntlet.id("doom_armor_speed"),
			FALL_ID = InfinityGauntlet.id("doom_armor_fall");

	private static final class Wearer {
		final long[] ready = new long[6];
		long bastionUntil, sunsUntil, nextSun, lastStandReady;
		int fuel = FUEL, suns;
		boolean jet, hadSet;
	}

	private record Timed(long at, UUID owner, Runnable run) {}

	private static final Map<UUID, Wearer> WEARERS = new HashMap<>();
	private static final List<Timed> TIMED = new ArrayList<>();
	private static boolean reentrant;

	private static Item piece(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> ModItems.DOOM_HELMET;
			case CHEST -> ModItems.DOOM_CHESTPLATE;
			case LEGS -> ModItems.DOOM_LEGGINGS;
			default -> ModItems.DOOM_BOOTS;
		};
	}

	public static boolean fullSet(LivingEntity entity) {
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			if (!entity.getItemBySlot(slot).is(piece(slot))) return false;
		}
		return true;
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(ModNetwork.DoomCast.TYPE, (payload, context) -> cast(context.player(), payload.ability()));
		ServerPlayNetworking.registerGlobalReceiver(ModNetwork.DoomJet.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
			Wearer w = WEARERS.get(p.getUUID());
			if (w != null && !payload.on() && w.jet) stopJet(p, w);
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (reentrant || !(entity instanceof ServerPlayer p)) return true;
			Wearer w = WEARERS.get(p.getUUID());
			ServerLevel level = p.level();
			if (w == null || level.getGameTime() >= w.bastionUntil || !fullSet(p)) return true;
			DoomVfx.on(level, DoomVfx.SHIELD_HIT, p, 1.6f, 8, DoomVfx.GREEN);
			DoomFx.sound(level, p.position(), SoundEvents.SHIELD_BLOCK.value(), 1f, 0.7f);
			if (source.getDirectEntity() instanceof Projectile projectile) {
				projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-0.3));
				return false;
			}
			if (source.getEntity() instanceof LivingEntity attacker && attacker != p && attacker.distanceTo(p) < 5) {
				Vec3 d = attacker.position().subtract(p.position()).normalize();
				attacker.knockback(1.4, -d.x, -d.z);
			}
			reentrant = true;
			try {
				p.hurtServer(level, source, amount * 0.2f);
			} finally {
				reentrant = false;
			}
			return false;
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> !(entity instanceof ServerPlayer p && lastStand(p, source)));
	}

	/** The last stand: the whole armor refuses a killing blow once in a while (never /kill, the void, or Doom's own victory). */
	private static boolean lastStand(ServerPlayer p, net.minecraft.world.damagesource.DamageSource source) {
		if (!fullSet(p) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
		ServerLevel level = p.level();
		long now = level.getGameTime();
		Wearer w = WEARERS.computeIfAbsent(p.getUUID(), id -> new Wearer());
		if (now < w.lastStandReady && !p.isCreative()) return false;
		// Doom beating a bearer of the whole Infinity Gauntlet is the ascension (see Latveria), not a death to be refused.
		var gauntlet = com.dantemorrigan.gauntlet.GauntletState.activeGauntlet(p);
		if (level.dimension() == Latveria.LEVEL && !gauntlet.isEmpty() && com.dantemorrigan.gauntlet.GauntletState.inserted(gauntlet).size() >= 6
				&& (source.getEntity() instanceof DoctorDoom || source.getEntity() instanceof Doombot b && !b.allied())) return false;
		w.lastStandReady = now + LAST_STAND_COOLDOWN;
		p.setHealth(Math.max(1f, p.getMaxHealth() * LAST_STAND_HEALTH));
		p.removeAllEffects();
		p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 3, false, true));
		p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 4, false, true));
		p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 2, false, true));
		p.invulnerableTime = 40;
		DoomVfx.on(level, DoomVfx.SHIELD, p, 1.7f, 60, DoomVfx.GREEN);
		DoomVfx.at(level, DoomVfx.SLAM, p.position(), 5f, 14, DoomVfx.GREEN);
		DoomFx.sound(level, p.position(), SoundEvents.TOTEM_USE, 1f, 0.7f);
		DoomFx.sound(level, p.position(), SoundEvents.IRON_GOLEM_REPAIR, 1.2f, 0.5f);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(5, 2, 5), e -> canHit(p, e))) {
			Vec3 d = e.position().subtract(p.position()).normalize();
			e.knockback(1.6, -d.x, -d.z);
		}
		DoomFx.tell(p, "armor.last_stand");
		sync(p, w);
		return true;
	}

	/** The armor's body: attributes while the set is on, effects renewed every second; taken off, the attributes go. */
	private static void body(ServerPlayer p, boolean set) {
		boolean had = p.getAttribute(Attributes.MAX_HEALTH) != null && p.getAttribute(Attributes.MAX_HEALTH).getModifier(HEALTH_ID) != null;
		modify(p, Attributes.MAX_HEALTH, HEALTH_ID, set ? SET_HEALTH : 0, AttributeModifier.Operation.ADD_VALUE);
		modify(p, Attributes.ATTACK_DAMAGE, DAMAGE_ID, set ? SET_DAMAGE : 0, AttributeModifier.Operation.ADD_VALUE);
		modify(p, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, set ? SET_KNOCKBACK : 0, AttributeModifier.Operation.ADD_VALUE);
		modify(p, Attributes.MOVEMENT_SPEED, SPEED_ID, set ? SET_SPEED : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		modify(p, Attributes.SAFE_FALL_DISTANCE, FALL_ID, set ? SET_FALL : 0, AttributeModifier.Operation.ADD_VALUE);
		if (set && !had) p.heal((float) SET_HEALTH); // the new hearts arrive full
		if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
		if (set && p.tickCount % 20 == 0) {
			p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 0, true, false, true));
			p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false, true));
			p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, true, false, true));
		}
	}

	private static void modify(ServerPlayer p, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation op) {
		AttributeInstance inst = p.getAttribute(attribute);
		if (inst == null) return;
		AttributeModifier current = inst.getModifier(id);
		if (amount == 0) {
			if (current != null) inst.removeModifier(id);
		} else if (current == null || current.amount() != amount) {
			inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
		}
	}

	public static void clear() {
		WEARERS.clear();
		TIMED.clear();
	}

	/** Debug: every power is ready again, the jets are full. */
	public static void resetCooldowns(ServerPlayer p) {
		Wearer w = WEARERS.computeIfAbsent(p.getUUID(), id -> new Wearer());
		java.util.Arrays.fill(w.ready, 0);
		w.fuel = FUEL;
		sync(p, w);
	}

	private static boolean canHit(ServerPlayer owner, LivingEntity e) {
		if (e == owner || e instanceof AbstractVillager || e instanceof Doombot b && b.allied() || !e.isAlive()) return false;
		return !(e instanceof ServerPlayer) || CoopRules.canAffect(owner, e);
	}

	/** Something the suns go after on their own: monsters, hostile Doombots and Doom, never players or animals. */
	private static boolean hostile(ServerPlayer owner, LivingEntity e) {
		return canHit(owner, e) && (e instanceof Enemy || e instanceof Doombot b && !b.allied() || e instanceof DoctorDoom);
	}

	private static void later(ServerPlayer owner, int ticks, Runnable run) {
		TIMED.add(new Timed(owner.level().getGameTime() + ticks, owner.getUUID(), run));
	}

	private static Vec3 hand(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
		return p.getEyePosition().add(look.scale(0.7)).add(right.scale(0.32)).add(0, -0.3, 0);
	}

	/** What the wearer aims at: the first living thing along the look within reach, or the block, or the far point. */
	private static Vec3 aim(ServerPlayer p, double reach) {
		Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(reach));
		BlockHitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, stop).inflate(1.5), e -> canHit(p, e))) {
			var hit = e.getBoundingBox().inflate(0.6).clip(eye, stop);
			if (hit.isPresent() && hit.get().distanceToSqr(eye) < bestD) { bestD = hit.get().distanceToSqr(eye); best = e; }
		}
		return best != null ? best.position().add(0, best.getBbHeight() * 0.55, 0) : stop;
	}

	public static void cast(ServerPlayer p, int ability) {
		if (ability < 0 || ability > 5 || !p.isAlive() || p.isSpectator()) return;
		if (!fullSet(p)) { DoomFx.tell(p, "armor.need_set"); return; }
		Wearer w = WEARERS.computeIfAbsent(p.getUUID(), id -> new Wearer());
		ServerLevel level = p.level();
		long now = level.getGameTime();
		if (ability == JET && w.jet) { stopJet(p, w); return; }
		if (ability == SUNS && w.suns > 0) { launchOne(p, w); return; }
		if (now < w.ready[ability] && !p.isCreative()) return;
		Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
		switch (ability) {
			case BOLT -> {
				Vec3 from = hand(p), dir = aim(p, 64).subtract(from).normalize();
				p.swing(InteractionHand.MAIN_HAND, true);
				DoomVfx.on(level, DoomVfx.CHARGE, p, 0.5f, 5, DoomVfx.GOLD);
				DoomCombat.fire(level, p, from, dir.scale(3.0), 12, 0.4, 1.4, 30, null, 0, DoomVfx.GOLD, 0.34f, e -> canHit(p, e));
				DoomFx.sound(level, eye, SoundEvents.BLAZE_SHOOT, 0.8f, 1.5f);
				DoomFx.sound(level, eye, SoundEvents.BEACON_POWER_SELECT, 0.4f, 2f);
			}
			case BASTION -> {
				w.bastionUntil = now + BASTION_TICKS;
				DoomVfx.on(level, DoomVfx.SHIELD, p, 1.7f, BASTION_TICKS, DoomVfx.GREEN);
				DoomVfx.at(level, DoomVfx.SLAM, p.position(), 4.5f, 12, DoomVfx.GREEN);
				DoomFx.sound(level, eye, SoundEvents.IRON_GOLEM_REPAIR, 1f, 0.6f);
				DoomFx.sound(level, eye, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.6f);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(4.5, 2, 4.5), e -> canHit(p, e))) {
					Vec3 d = e.position().subtract(p.position()).normalize();
					DoomCombat.hurt(level, e, p, 4);
					e.knockback(1.3, -d.x, -d.z);
				}
			}
			case JET -> {
				if (p.getAbilities().flying || p.isFallFlying()) { DoomFx.tell(p, "armor.already_flying"); return; }
				if (w.fuel < 20) { DoomFx.tell(p, "armor.no_fuel"); return; }
				w.jet = true;
				p.removeEffect(MobEffects.SLOW_FALLING);
				DoomVfx.on(level, DoomVfx.JET, p, 1, 20000, DoomVfx.GOLD);
				DoomFx.sound(level, p.position(), SoundEvents.FIRECHARGE_USE, 1f, 0.6f);
			}
			case RIFT -> {
				Vec3 to = aim(p, RIFT_RANGE);
				BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(RIFT_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
				Vec3 target = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(RIFT_RANGE)) : hit.getLocation().subtract(look.scale(0.8));
				if (to.distanceToSqr(eye) < target.distanceToSqr(eye)) target = to.subtract(look.scale(1.6));
				BlockPos feet = BlockPos.containing(target.x, target.y - 0.9, target.z);
				for (int i = 0; i < 4 && !(free(level, feet) && free(level, feet.above())); i++) feet = feet.above();
				if (!free(level, feet) || !free(level, feet.above())) { DoomFx.tell(p, "armor.no_room"); return; }
				Vec3 from = p.position();
				Vec3 dest = new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
				p.teleportTo(level, dest.x, dest.y, dest.z, java.util.Set.of(), p.getYRot(), p.getXRot(), false);
				p.fallDistance = 0;
				DoomVfx.at(level, DoomVfx.RIFT, from.add(0, 1, 0), 1.1f, 14, DoomVfx.GREEN);
				DoomVfx.at(level, DoomVfx.RIFT, dest.add(0, 1, 0), 1.1f, 14, DoomVfx.GREEN);
				DoomVfx.line(level, DoomVfx.TRAIL, from.add(0, 1, 0), dest.add(0, 1, 0), 0.6f, 10, DoomVfx.GREEN);
				DoomFx.sound(level, from, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 0.8f);
				DoomFx.sound(level, dest, SoundEvents.ENDERMAN_TELEPORT, 1f, 0.6f);
				DoomCombat.dodge(p);
			}
			case SUNS -> {
				w.suns = SUN_COUNT;
				w.sunsUntil = now + SUN_TICKS;
				w.nextSun = now + 14;
				DoomVfx.on(level, DoomVfx.ORBIT, p, SUN_COUNT, SUN_TICKS, DoomVfx.SUN);
				DoomFx.sound(level, p.position(), SoundEvents.BEACON_ACTIVATE, 1.2f, 0.7f);
				DoomFx.sound(level, p.position(), SoundEvents.FIRECHARGE_USE, 0.8f, 0.5f);
			}
			default -> {
				Vec3 at = aim(p, EDICT_RANGE);
				BlockHitResult down = level.clip(new ClipContext(at.add(0, 0.5, 0), at.add(0, -24, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
				if (down.getType() == HitResult.Type.MISS) { DoomFx.tell(p, "armor.no_target"); return; }
				Vec3 ground = down.getLocation();
				p.swing(InteractionHand.MAIN_HAND, true);
				DoomVfx.at(level, DoomVfx.EDICT, ground, (float) EDICT_RADIUS, EDICT_DELAY, DoomVfx.EDICT_GREEN);
				DoomFx.sound(level, ground, SoundEvents.BEACON_AMBIENT, 2f, 0.8f);
				DoomFx.sound(level, ground, SoundEvents.EVOKER_PREPARE_SUMMON, 1.2f, 0.8f);
				later(p, EDICT_DELAY, () -> {
					DoomVfx.at(level, DoomVfx.COLUMN, ground, (float) EDICT_RADIUS, 16, DoomVfx.EDICT_GREEN);
					DoomVfx.at(level, DoomVfx.SLAM, ground, (float) EDICT_RADIUS * 1.8f, 14, DoomVfx.EDICT_GREEN);
					DoomFx.sound(level, ground, SoundEvents.WARDEN_SONIC_BOOM, 1.6f, 0.7f);
					DoomFx.sound(level, ground, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.6f);
					DoomFx.near(level, ground, 32, DoomFx.SHAKE, 0, 0.9f, 10);
					for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(ground, ground).inflate(EDICT_RADIUS, 6, EDICT_RADIUS), e -> canHit(p, e))) {
						if (e.position().subtract(ground).horizontalDistance() > EDICT_RADIUS + 0.4) continue;
						if (DoomCombat.hurt(level, e, p, 34)) {
							e.setDeltaMovement(e.getDeltaMovement().add(0, 0.9, 0));
							e.hurtMarked = true;
						}
					}
				});
			}
		}
		w.ready[ability] = now + COOLDOWN[ability];
		sync(p, w);
	}

	/** One sun leaves the orbit and flies where the wearer looks; the last one starts the cooldown. */
	private static void launchOne(ServerPlayer p, Wearer w) {
		long now = p.level().getGameTime();
		if (now < w.nextSun) return;
		Vec3 target = aim(p, 64);
		LivingEntity lock = null;
		for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(target, target).inflate(1.4), e -> canHit(p, e))) lock = e;
		w.suns--;
		w.nextSun = now + 5;
		launch(p, w.suns, target, lock);
		DoomVfx.on(p.level(), DoomVfx.ORBIT, p, w.suns, (int) Math.max(1, w.sunsUntil - now), DoomVfx.SUN);
		if (w.suns == 0) { w.sunsUntil = 0; w.ready[SUNS] = now + COOLDOWN[SUNS]; }
		sync(p, w);
	}

	private static Vec3 sunAt(ServerPlayer p, int i) {
		double a = p.level().getGameTime() * 0.12 + i * Math.TAU / SUN_COUNT;
		return p.position().add(Math.cos(a) * 1.5, 1.9 + Math.sin(a * 2) * 0.2, Math.sin(a) * 1.5);
	}

	private static void launch(ServerPlayer p, int i, Vec3 target, LivingEntity lock) {
		Vec3 from = sunAt(p, i);
		Vec3 dir = (lock != null ? lock.position().add(0, lock.getBbHeight() * 0.55, 0) : target).subtract(from).normalize();
		DoomCombat.fire(p.level(), p, from, dir.scale(2.0), 16, 0.5, 2.6, 60, lock, lock == null ? 0 : 0.18, DoomVfx.SUN, 0.6f, e -> canHit(p, e));
		DoomFx.sound(p.level(), from, SoundEvents.FIRECHARGE_USE, 0.7f, 1.4f);
	}

	private static boolean free(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	private static void stopJet(ServerPlayer p, Wearer w) {
		if (!w.jet) return;
		w.jet = false;
		w.ready[JET] = p.level().getGameTime() + COOLDOWN[JET];
		if (!p.onGround()) p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 50, 0, true, false));
		DoomVfx.on(p.level(), DoomVfx.JET, p, 0, 0, DoomVfx.GOLD);
		sync(p, w);
	}

	public static void tick(MinecraftServer server) {
		for (Iterator<Timed> it = TIMED.iterator(); it.hasNext();) {
			Timed t = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(t.owner());
			if (owner == null || !owner.isAlive()) { it.remove(); continue; }
			if (owner.level().getGameTime() < t.at()) continue;
			it.remove();
			t.run().run();
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			boolean set = fullSet(p);
			if (p.isAlive()) body(p, set);
			Wearer w = WEARERS.get(p.getUUID());
			if (w == null) {
				if (!set) continue;
				w = new Wearer();
				WEARERS.put(p.getUUID(), w);
			}
			long now = p.level().getGameTime();
			boolean changed = set != w.hadSet;
			if (w.jet) {
				if (!set || !p.isAlive() || p.getAbilities().flying || w.fuel <= 0) {
					stopJet(p, w);
				} else {
					w.fuel--;
					p.fallDistance = 0;
					((ServerGamePacketListenerAccessor) p.connection).infinityGauntlet$setAboveGroundTickCount(0);
					if (w.fuel % 20 == 0) changed = true;
				}
			} else if (w.fuel < FUEL && (p.onGround() || now % 3 == 0)) {
				w.fuel = Math.min(FUEL, w.fuel + 2);
				if (w.fuel % 40 == 0) changed = true;
			}
			if (w.suns > 0) {
				if (!set || now >= w.sunsUntil || !p.isAlive()) {
					w.suns = 0;
					w.ready[SUNS] = now + COOLDOWN[SUNS];
					DoomVfx.on(p.level(), DoomVfx.ORBIT, p, 0, 0, DoomVfx.SUN);
					changed = true;
				}
			}
			if (changed || set && now % 20 == 0) {
				w.hadSet = set;
				sync(p, w);
			}
		}
	}

	private static void sync(ServerPlayer p, Wearer w) {
		if (!ServerPlayNetworking.canSend(p, ModNetwork.DoomSync.TYPE)) return;
		long now = p.level().getGameTime();
		StringBuilder s = new StringBuilder(fullSet(p) ? "1;" : "0;").append(w.jet ? 1 : 0).append(';').append(w.fuel).append(';')
				.append(Math.max(0, w.bastionUntil - now)).append(';').append(w.suns).append(';');
		for (int i = 0; i < 6; i++) s.append(i == 0 ? "" : ",").append(Math.max(0, w.ready[i] - now)).append('/').append(COOLDOWN[i]);
		ServerPlayNetworking.send(p, new ModNetwork.DoomSync(s.toString()));
	}

	private DoomArmor() {}
}
