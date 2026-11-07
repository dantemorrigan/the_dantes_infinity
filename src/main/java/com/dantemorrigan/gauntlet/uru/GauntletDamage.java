package com.dantemorrigan.gauntlet.uru;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.stormbreaker.Stormbreaker;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The one door through which gauntlet attacks hurt living things.
 *
 * <ul>
 *   <li>Partial gauntlet, or a mob target: the old path (normal damage source, normal armor).</li>
 *   <li>A gauntlet with all six stones against a player (or anyone in Uru): the "infinity_gauntlet" damage type,
 *       which ignores ordinary armor, enchantments and shields, scaled by {@link GauntletConfig#fullGauntletDamageMultiplier}.</li>
 *   <li>...and if that target wears a complete Uru set: only {@link GauntletConfig#uruDamageFactor} of it (at least 1 HP),
 *       instant-kill effects become a finite heavy hit, and the set wears out.</li>
 * </ul>
 * Callers still check {@code CoopRules} first; this never decides PvP. Wear and the rune flash happen only if the hit
 * was actually accepted (invulnerable or protected targets get nothing).
 */
public final class GauntletDamage {
	public static final ResourceKey<DamageType> INFINITY = ResourceKey.create(Registries.DAMAGE_TYPE, InfinityGauntlet.id("infinity_gauntlet"));
	/** Amounts at or above this are "kill outright" effects (Float.MAX_VALUE in the stones' code). */
	private static final float LETHAL = 1000f;

    public static boolean hurt(ServerLevel level,Entity target,ServerPlayer attacker,DamageSource normal,float amount) {
        return hurt(level,target,attacker,normal,amount,null);
    }
    public static boolean hurt(ServerLevel level, Entity target, ServerPlayer attacker, DamageSource normal, float amount,net.minecraft.world.phys.Vec3 impact) {
		if(target instanceof com.dantemorrigan.gauntlet.boss.LivingTribunal tribunal && attacker!=null && !GauntletState.activeGauntlet(attacker).isEmpty())
            return tribunal.hurtByGauntlet(level,attacker,amount,impact);
        // Thanos fears the gauntlet above all else: every ability of a worn gauntlet hits him hard (see Thanos#hurtByGauntlet).
		if (target instanceof com.dantemorrigan.gauntlet.boss.Thanos thanos && attacker != null && !GauntletState.activeGauntlet(attacker).isEmpty()) {
			return thanos.hurtByGauntlet(level, attacker, amount, fullGauntlet(attacker));
		}
		if (target instanceof com.dantemorrigan.gauntlet.boss.Loki loki && attacker != null) {
			return loki.hurtByGauntlet(level, normal, amount < LETHAL ? amount * 1.65f : amount);
		}
		if (target instanceof ServerPlayer player && player != attacker && Stormbreaker.guarded(player)) {
			boolean full = attacker != null && fullGauntlet(attacker);
			GauntletConfig cfg = GauntletConfig.get();
			float incoming = amount >= LETHAL ? cfg.uruLethalIncoming
					: amount * (full ? Math.max(0, cfg.fullGauntletDamageMultiplier) : 1);
			if (incoming <= 0) return false;
			DamageSource source = full ? new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(INFINITY), attacker) : normal;
			boolean uru = full && cfg.uruDamageFactor < .36f && UruArmor.fullSet(player);
			float taken = Math.max(1, incoming * (uru ? cfg.uruDamageFactor : .36f));
			if (!player.hurtServer(level, source, taken)) return false;
			if (uru) UruArmor.absorbed(level, player, incoming - taken);
			Stormbreaker.guardFlash(player, attacker);
			return true;
		}
		if (!(target instanceof LivingEntity living) || attacker == null || !fullGauntlet(attacker)
				|| !(living instanceof Player || UruArmor.fullSet(living))) {
			return target.hurtServer(level, normal, amount);
		}
		GauntletConfig cfg = GauntletConfig.get();
		boolean lethal = amount >= LETHAL;
		float incoming = lethal ? amount : amount * Math.max(0, cfg.fullGauntletDamageMultiplier);
		DamageSource infinity = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(INFINITY), attacker);
		if (!UruArmor.fullSet(living)) return living.hurtServer(level, infinity, incoming);
		if (lethal) incoming = Math.max(1, cfg.uruLethalIncoming);
		float taken = Math.max(1f, incoming * cfg.uruDamageFactor);
		if (!living.hurtServer(level, infinity, taken)) return false;
		UruArmor.absorbed(level, living, incoming - taken);
		return true;
	}

	public static boolean fullGauntlet(ServerPlayer player) {
		ItemStack gauntlet = GauntletState.activeGauntlet(player);
		return !gauntlet.isEmpty() && GauntletState.inserted(gauntlet).size() == 6;
	}

	private GauntletDamage() {}
}
