package com.dantemorrigan.gauntlet.doom;

import com.dantemorrigan.gauntlet.CoopRules;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.ModParticles;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.ClipContext;
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
 * Doom's armor. The four pieces worn together open a wheel of six powers, each on its own cooldown: Gauntlet Bolt,
 * Iron Bastion, Jet Flight, Rift Step, Seven Suns and the Latverian Edict.
 *
 * <p>The powers never touch the player's flight abilities, so they do not fight with the Infinity Gauntlet or
 * Stormbreaker: the jets push the wearer along their line of sight and refuse to start while another flight is on.
 */
public final class DoomArmor {
	public static final ArmorMaterial MATERIAL = new ArmorMaterial(55,
			Map.of(ArmorType.HELMET, 4, ArmorType.CHESTPLATE, 9, ArmorType.LEGGINGS, 7, ArmorType.BOOTS, 4, ArmorType.BODY, 11),
			18, SoundEvents.ARMOR_EQUIP_NETHERITE, 3.5f, 0.15f,
			TagKey.create(Registries.ITEM, InfinityGauntlet.id("repairs_doom_armor")),
			ResourceKey.create(EquipmentAssets.ROOT_ID, InfinityGauntlet.id("doom")));
	public static final int BOLT = 0, BASTION = 1, JET = 2, RIFT = 3, SUNS = 4, EDICT = 5;
	public static final String[] NAMES = {"gauntlet_bolt", "iron_bastion", "jet_flight", "rift_step", "seven_suns", "latverian_edict"};
	private static final int[] COOLDOWN = {30, 500, 400, 140, 900, 1200};
	private static final int BASTION_TICKS = 120, JET_TICKS = 160;

	private static final class Wearer {
		final long[] ready = new long[6];
		long bastionUntil, jetUntil;
		boolean hadSet;
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
		// Iron Bastion: four fifths of every blow stop on the shield.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (reentrant || !(entity instanceof ServerPlayer p)) return true;
			Wearer w = WEARERS.get(p.getUUID());
			if (w == null || p.level().getGameTime() >= w.bastionUntil || !fullSet(p)) return true;
			reentrant = true;
			try {
				p.hurtServer(p.level(), source, amount * 0.2f);
			} finally {
				reentrant = false;
			}
			DoomFx.bill(p.level(), ModParticles.DOOM_IRON_BASTION, p.position().add(p.getLookAngle().scale(0.9)).add(0, 1.1, 0), 1.6);
			DoomFx.sound(p.level(), p.position(), SoundEvents.SHIELD_BLOCK.value(), 1f, 0.7f);
			return false;
		});
	}

	public static void clear() {
		WEARERS.clear();
		TIMED.clear();
	}

	private static boolean canHit(ServerPlayer owner, LivingEntity e) {
		if (e == owner || e instanceof AbstractVillager || e instanceof Doombot b && b.allied()) return false;
		return !(e instanceof ServerPlayer) || CoopRules.canAffect(owner, e);
	}

	private static void later(ServerPlayer owner, int ticks, Runnable run) {
		TIMED.add(new Timed(owner.level().getGameTime() + ticks, owner.getUUID(), run));
	}

	public static void cast(ServerPlayer p, int ability) {
		if (ability < 0 || ability > 5 || !p.isAlive() || p.isSpectator()) return;
		if (!fullSet(p)) { DoomFx.tell(p, "armor.need_set"); return; }
		Wearer w = WEARERS.computeIfAbsent(p.getUUID(), id -> new Wearer());
		ServerLevel level = p.level();
		long now = level.getGameTime();
		if (ability == JET && now < w.jetUntil) { stopJet(p, w); sync(p, w); return; }
		if (now < w.ready[ability] && !p.isCreative()) return;
		Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
		switch (ability) {
			case BOLT -> {
				DoomCombat.fire(level, p, eye.add(look.scale(0.8)), look.scale(2.4), 14, 0.35, 1.2, 40, null, 0, ModParticles.DOOM_GAUNTLET_BOLT, 0.9, e -> canHit(p, e));
				DoomFx.sound(level, eye, SoundEvents.BLAZE_SHOOT, 0.9f, 1.2f);
			}
			case BASTION -> {
				w.bastionUntil = now + BASTION_TICKS;
				DoomFx.bill(level, ModParticles.DOOM_IRON_BASTION, eye.add(look.scale(1.2)), 0);
				DoomFx.sound(level, eye, SoundEvents.IRON_GOLEM_REPAIR, 1f, 0.6f);
			}
			case JET -> {
				if (p.getAbilities().flying || p.isFallFlying()) { DoomFx.tell(p, "armor.already_flying"); return; }
				w.jetUntil = now + JET_TICKS;
				DoomFx.bill(level, ModParticles.DOOM_JET_EXHAUST, p.position(), 0);
				DoomFx.sound(level, p.position(), SoundEvents.FIRECHARGE_USE, 1f, 0.7f);
			}
			case RIFT -> {
				BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(18)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
				Vec3 to = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(18)) : hit.getLocation().subtract(look.scale(1.2));
				BlockPos feet = BlockPos.containing(to.x, to.y - 1, to.z);
				for (int i = 0; i < 4 && !(free(level, feet) && free(level, feet.above())); i++) feet = feet.above();
				if (!free(level, feet) || !free(level, feet.above())) { DoomFx.tell(p, "armor.no_room"); return; }
				for (int i = 0; i < 6 && free(level, feet.below()); i++) feet = feet.below();
				Vec3 from = p.position();
				DoomFx.bill(level, ModParticles.DOOM_RIFT_STEP, from.add(0, 1.1, 0), 0.7);
				p.teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
				p.fallDistance = 0;
				DoomFx.bill(level, ModParticles.DOOM_RIFT_STEP, p.position().add(0, 1.1, 0), 0.7);
				DoomFx.sound(level, p.position(), SoundEvents.ENDERMAN_TELEPORT, 1f, 0.7f);
				DoomCombat.dodge(p);
			}
			case SUNS -> {
				DoomFx.bill(level, ModParticles.DOOM_SEVEN_SUNS, p.position().add(0, 2.2, 0), 0.6);
				DoomFx.sound(level, p.position(), SoundEvents.BEACON_ACTIVATE, 1.2f, 0.7f);
				for (int i = 0; i < 7; i++) {
					int n = i;
					later(p, 32 + i * 4, () -> {
						Vec3 at = p.position().add(Math.cos(n * 0.9) * 1.6, 2.6, Math.sin(n * 0.9) * 1.6);
						List<LivingEntity> foes = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(28, 12, 28),
								e -> e.isAlive() && canHit(p, e) && (e instanceof net.minecraft.world.entity.monster.Enemy || e instanceof Doombot || e instanceof ServerPlayer));
						foes.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
						LivingEntity target = foes.isEmpty() ? null : foes.get(n % foes.size());
						Vec3 dir = target == null ? p.getLookAngle() : target.position().add(0, 1, 0).subtract(at).normalize();
						DoomCombat.fire(level, p, at, dir.scale(1.1), 12, 0.4, 2.2, 70, target, 0.18, ModParticles.DOOM_SUN_PROJECTILE, 0, e -> canHit(p, e));
						DoomFx.sound(level, at, SoundEvents.FIRECHARGE_USE, 0.8f, 1.3f);
					});
				}
			}
			default -> {
				BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(28)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
				if (hit.getType() == HitResult.Type.MISS) { DoomFx.tell(p, "armor.no_target"); return; }
				Vec3 at = hit.getLocation();
				DoomFx.flat(level, ModParticles.DOOM_EDICT_WARNING, at, 1.05);
				DoomFx.sound(level, at, SoundEvents.BEACON_AMBIENT, 2f, 0.6f);
				later(p, 46, () -> {
					DoomFx.bill(level, ModParticles.DOOM_EDICT_COLUMN, at.add(0, 2.3, 0), 0);
					DoomFx.flat(level, ModParticles.DOOM_SLAM_WAVE, at, 7);
					DoomFx.sound(level, at, SoundEvents.WARDEN_SONIC_BOOM, 1.6f, 0.7f);
					for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(4.5, 6, 4.5), e -> e.isAlive() && canHit(p, e))) {
						if (e.position().subtract(at).horizontalDistance() <= 4.8) DoomCombat.hurt(level, e, p, 40);
					}
				});
			}
		}
		w.ready[ability] = now + COOLDOWN[ability];
		sync(p, w);
	}

	private static boolean free(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	private static void stopJet(ServerPlayer p, Wearer w) {
		w.jetUntil = 0;
		p.removeEffect(MobEffects.LEVITATION);
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 120, 0, true, false));
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
			Wearer w = WEARERS.get(p.getUUID());
			if (w == null) {
				if (!set) continue;
				w = new Wearer();
				WEARERS.put(p.getUUID(), w);
			}
			long now = p.level().getGameTime();
			if (w.jetUntil > 0) {
				if (!set || now >= w.jetUntil || !p.isAlive() || p.getAbilities().flying) {
					stopJet(p, w);
				} else {
					// Thrust along the line of sight; sneaking holds the wearer in the air.
					Vec3 thrust = p.isShiftKeyDown() ? Vec3.ZERO : p.getLookAngle().scale(0.72);
					p.setDeltaMovement(thrust);
					p.hurtMarked = true;
					p.fallDistance = 0;
					p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 10, 0, true, false, false));
					if (now % 3 == 0) DoomFx.bill(p.level(), ModParticles.DOOM_JET_EXHAUST, p.position(), 0);
				}
			}
			if (now < w.bastionUntil && now % 20 == 0 && set) DoomFx.bill(p.level(), ModParticles.DOOM_IRON_BASTION, p.position().add(p.getLookAngle().scale(1.0)).add(0, 1.1, 0), 1.8);
			if (set != w.hadSet || set && now % 10 == 0) {
				w.hadSet = set;
				sync(p, w);
			}
		}
	}

	private static void sync(ServerPlayer p, Wearer w) {
		if (!ServerPlayNetworking.canSend(p, ModNetwork.DoomSync.TYPE)) return;
		long now = p.level().getGameTime();
		StringBuilder s = new StringBuilder(fullSet(p) ? "1;" : "0;").append(Math.max(0, w.jetUntil - now)).append(';').append(Math.max(0, w.bastionUntil - now)).append(';');
		for (int i = 0; i < 6; i++) s.append(i == 0 ? "" : ",").append(Math.max(0, w.ready[i] - now)).append('/').append(COOLDOWN[i]);
		ServerPlayNetworking.send(p, new ModNetwork.DoomSync(s.toString()));
	}

	private DoomArmor() {}
}
