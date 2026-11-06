package com.dantemorrigan.gauntlet.power;

import com.dantemorrigan.gauntlet.CoopRules;
import com.dantemorrigan.gauntlet.boss.TribunalEncounter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.List;

/** Giant volumes span sections far above their feet; include the encounter actor by its actual bounding box. */
public final class CombatTargets {
    public static List<LivingEntity> inArea(ServerPlayer owner,AABB area) {
        List<LivingEntity> result=new ArrayList<>(owner.level().getEntitiesOfClass(LivingEntity.class,area,
            e->e!=owner && e.isAlive() && !e.isSpectator() && CoopRules.canAffect(owner,e)));
        var giant=TribunalEncounter.targetFor(owner);
        if(giant!=null && giant.getBoundingBox().intersects(area) && !result.contains(giant)) result.add(giant);
        return result;
    }
    private CombatTargets() {}
}
