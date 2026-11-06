package com.dantemorrigan.gauntlet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Server-side checks shared by direct hits, delayed attacks and hostile interactions. */
public final class CoopRules {
    private CoopRules() {}

    public static boolean canAffect(ServerPlayer attacker, Entity target) {
        if (!(target instanceof ServerPlayer victim)) return true;
        return GauntletConfig.get().abilityPvp && attacker != null && attacker != victim
            && attacker.isAlive() && !attacker.isSpectator() && victim.isAlive()
            && !victim.isSpectator() && !victim.isCreative() && attacker.level() == victim.level()
            && attacker.canHarmPlayer(victim);
    }

    public static boolean canControlWithGauntlet(ServerPlayer attacker, Entity target) {
        if (!canAffect(attacker, target)) return false;
        if (target instanceof ServerPlayer player && com.dantemorrigan.gauntlet.stormbreaker.Stormbreaker.guarded(player)) {
            com.dantemorrigan.gauntlet.stormbreaker.Stormbreaker.guardFlash(player, attacker);
            return false;
        }
        return true;
    }

    public static boolean canEdit(ServerPlayer actor, ServerLevel level, BlockPos pos) {
        return GauntletConfig.get().abilityBlockChanges && actor != null && actor.isAlive()
            && !actor.isSpectator() && actor.level() == level && level.hasChunkAt(pos)
            && !com.dantemorrigan.gauntlet.stones.MindArena.protects(level, pos)
            && level.getWorldBorder().isWithinBounds(pos) && level.mayInteract(actor, pos);
    }
}
