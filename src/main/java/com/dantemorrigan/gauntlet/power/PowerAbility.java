package com.dantemorrigan.gauntlet.power;

import com.dantemorrigan.gauntlet.CoopRules;
import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.ModComponents;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.space.PlanetDestruction;
import com.dantemorrigan.gauntlet.space.SpaceManager;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server side of the Power stone: charged strike and the Planet Destruction trigger. */
public final class PowerAbility {
	private static final Map<UUID, Long> ULTIMATE_COOLDOWN = new HashMap<>();

	public static boolean canUse(ServerPlayer player, ItemStack gauntlet) {
		return GauntletState.activeGauntlet(player) == gauntlet && GauntletState.inserted(gauntlet).contains(Stone.POWER);
	}

	public static final int MODE_STRIKE = 0, MODE_PLANET = 1, MODE_CELESTIAL = 2;
	private static final int MODE_COUNT = 3;

	public static int mode(ItemStack gauntlet) {
		Integer m = gauntlet.get(ModComponents.POWER_MODE);
		if (m != null) return m;
		return Boolean.TRUE.equals(gauntlet.get(ModComponents.ULTIMATE_MODE)) ? MODE_PLANET : MODE_STRIKE;
	}

	public static boolean isUltimate(ItemStack gauntlet) {
		return mode(gauntlet) == MODE_PLANET;
	}

	/** Toggle strike / ultimate. Requested by the client keybind. */
	public static void toggleMode(ServerPlayer player) {
		ItemStack gauntlet = GauntletState.activeGauntlet(player);
		if (gauntlet.isEmpty() || !GauntletState.inserted(gauntlet).contains(Stone.POWER)) {
			com.dantemorrigan.gauntlet.net.ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.need.power"));
			return;
		}
		int mode = (mode(gauntlet) + 1) % MODE_COUNT;
		gauntlet.set(ModComponents.POWER_MODE, mode);
		gauntlet.remove(ModComponents.ULTIMATE_MODE);
		com.dantemorrigan.gauntlet.net.ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.power.mode." + mode));
		player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 1.4F - mode * 0.4F);
	}

	/** Called when the player releases the charge. */
	public static void release(ServerPlayer player, ItemStack gauntlet, int chargeTicks) {
		GauntletConfig cfg = GauntletConfig.get();
		GauntletState.setInUse(gauntlet, EnumSet.noneOf(Stone.class));
		if (!canUse(player, gauntlet)) return;
		if (mode(gauntlet) == MODE_CELESTIAL) {
			com.dantemorrigan.gauntlet.celestial.CelestialApocalypse.resetAim(player);
			return;
		}
		if (chargeTicks < cfg.strikeMinChargeTicks) return;
		if (player.getCooldowns().isOnCooldown(gauntlet)) return;

		if (mode(gauntlet) == MODE_CELESTIAL) {
			com.dantemorrigan.gauntlet.celestial.CelestialApocalypse.resetAim(player);
			return; // celestial mode works while charging (aim at the sky), release does nothing
		}
		HitResult hit = pick(player, com.dantemorrigan.gauntlet.cosmos.CosmosTravel.inCosmos(player)?CosmicGauntlet.reach(player,170):cfg.strikeReach);
		if (isUltimate(gauntlet)) {
			tryUltimate(player, gauntlet, chargeTicks, hit);
			return;
		}
		strike(player, gauntlet, Math.min(1f, chargeTicks / (float) cfg.strikeFullChargeTicks), hit);
	}

	private static void tryUltimate(ServerPlayer player, ItemStack gauntlet, int chargeTicks, HitResult hit) {
		GauntletConfig cfg = GauntletConfig.get();
		if (chargeTicks < cfg.ultimateChargeTicks) {
			com.dantemorrigan.gauntlet.net.ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.power.not_charged", String.valueOf(chargeTicks), String.valueOf(cfg.ultimateChargeTicks)));
			return;
		}
		if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK || block.getDirection() != Direction.UP) {
			com.dantemorrigan.gauntlet.net.ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.power.strike_ground"));
			return;
		}
		if (SpaceManager.inSpace(player)) {
			com.dantemorrigan.gauntlet.net.ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.power.nothing_to_destroy"));
			return;
		}
		long now = player.level().getServer().overworld().getGameTime();
		Long until = ULTIMATE_COOLDOWN.get(player.getUUID());
		if (until != null && now < until && !player.isCreative()) {
			com.dantemorrigan.gauntlet.net.ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.power.cooldown", String.valueOf((until - now) / 20)));
			return;
		}
		Vec3 impact = block.getLocation();
		if (PlanetDestruction.start(player, impact)) {
			ULTIMATE_COOLDOWN.put(player.getUUID(), now + cfg.ultimateCooldownTicks);
			GauntletState.setInUse(gauntlet, EnumSet.of(Stone.POWER));
			player.getCooldowns().addCooldown(gauntlet, cfg.strikeCooldownTicks * 3);
			sendFx(player.level(), impact, new Vec3(0, 1, 0), 2.0f);
		}
	}

	/** Normal power strike: damage + knockback around the impact, then visuals for everyone nearby. */
	public static void strike(ServerPlayer player, ItemStack gauntlet, float charge, HitResult hit) {
		GauntletConfig cfg = GauntletConfig.get();
		ServerLevel level = player.level();
		Vec3 impact = hit.getLocation();
		Vec3 normal = hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK
				? Vec3.atLowerCornerOf(b.getDirection().getUnitVec3i())
				: player.getLookAngle().reverse();
		float radius = cfg.strikeRadius * (0.5f + 0.5f * charge);
		float damage = cfg.strikeDamage * (0.35f + 0.65f * charge);

		Entity direct = hit instanceof EntityHitResult eh ? eh.getEntity() : null;
		AABB area = new AABB(impact, impact).inflate(radius);
		for (Entity e : level.getEntities(player, area, e -> e instanceof LivingEntity le && le.isAlive() && !e.isSpectator())) {
			if (!CoopRules.canAffect(player, e)) continue;
			double dist = e.position().distanceTo(impact);
			if (e != direct && dist > radius) continue;
			float falloff = e == direct ? 1f : (float) Math.max(0.25, 1.0 - dist / radius);
			com.dantemorrigan.gauntlet.uru.GauntletDamage.hurt(level, e, player, player.damageSources().playerAttack(player), damage * falloff);
			Vec3 push = e.getBoundingBox().getCenter().subtract(impact).multiply(1, com.dantemorrigan.gauntlet.cosmos.CosmosTravel.inCosmos(player)?1:0, 1);
			if (push.lengthSqr() < 1.0e-4) push = player.getLookAngle().multiply(1, com.dantemorrigan.gauntlet.cosmos.CosmosTravel.inCosmos(player)?1:0, 1);
			push = push.normalize().scale(cfg.strikeKnockback * charge * falloff);
            double brace = e instanceof ServerPlayer guarded && com.dantemorrigan.gauntlet.stormbreaker.Stormbreaker.guarded(guarded) ? .36 : 1;
			e.push(push.x * brace, (com.dantemorrigan.gauntlet.cosmos.CosmosTravel.inCosmos(player)?push.y:0.35 + 0.3 * charge * falloff) * brace, push.z * brace);
			e.hurtMarked = true;
		}
		player.getCooldowns().addCooldown(gauntlet, cfg.strikeCooldownTicks);
		level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F + charge, 1.3F - 0.4F * charge);
		level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0F, 0.8F);
		if(com.dantemorrigan.gauntlet.cosmos.CosmosTravel.inCosmos(player)) CosmicGauntlet.fx(player,0,impact,impact,radius,0xffb675ff,25);
        else sendFx(level, impact, normal, 0.6f + charge);
	}

	public static void sendFx(ServerLevel level, Vec3 pos, Vec3 normal, float strength) {
		ModNetwork.PowerFx fx = new ModNetwork.PowerFx(pos, normal, strength);
		for (ServerPlayer p : PlayerLookup.around(level, pos, 96)) ServerPlayNetworking.send(p, fx);
	}

	/** Block or entity in front of the player within reach; misses resolve to the max-reach point. */
	public static HitResult pick(ServerPlayer player, double reach) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(reach));
		BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		double max = block.getType() == HitResult.Type.MISS ? reach : block.getLocation().distanceTo(eye);
		Vec3 entityEnd = eye.add(player.getLookAngle().scale(max));
		AABB box = player.getBoundingBox().expandTowards(player.getLookAngle().scale(max)).inflate(1.0);
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, eye, entityEnd, box,
				e -> !e.isSpectator() && e.isPickable() && e instanceof LivingEntity, max * max);
		return entity != null ? entity : block;
	}

	private PowerAbility() {}
    public static int cooldown(ServerPlayer p, boolean planet) {
        var g=GauntletState.activeGauntlet(p);
        int item=(int)Math.ceil(p.getCooldowns().getCooldownPercent(g,0f)*GauntletConfig.get().strikeCooldownTicks);
        return planet?Math.max(item,(int)Math.max(0,ULTIMATE_COOLDOWN.getOrDefault(p.getUUID(),0L)-p.level().getServer().overworld().getGameTime())):item;
    }

}
