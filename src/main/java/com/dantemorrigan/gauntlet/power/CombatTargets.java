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
        if(giant!=null && box(giant).intersects(area) && !result.contains(giant)) result.add(giant);
        return result;
    }
    public static AABB box(LivingEntity target) {
        return target instanceof com.dantemorrigan.gauntlet.boss.LivingTribunal b?com.dantemorrigan.gauntlet.boss.TribunalHitbox.bounds(b):target.getBoundingBox();
    }
    public static java.util.Optional<net.minecraft.world.phys.Vec3> clip(LivingEntity target,net.minecraft.world.phys.Vec3 from,net.minecraft.world.phys.Vec3 to,double radius) {
        if(target instanceof com.dantemorrigan.gauntlet.boss.LivingTribunal b)return com.dantemorrigan.gauntlet.boss.TribunalHitbox.clip(b,from,to,radius);
        var bounds=target.getBoundingBox().inflate(radius);return bounds.contains(from)?java.util.Optional.of(from):bounds.clip(from,to);
    }
    public static net.minecraft.world.phys.Vec3 impact(LivingEntity target,net.minecraft.world.phys.Vec3 from,net.minecraft.world.phys.Vec3 to,double radius) {
        if(target instanceof com.dantemorrigan.gauntlet.boss.LivingTribunal b) {
            var core=com.dantemorrigan.gauntlet.boss.TribunalHitbox.core(b).inflate(radius);
            if(core.contains(from) || core.clip(from,to).isPresent())return com.dantemorrigan.gauntlet.boss.TribunalHitbox.core(b).getCenter();
        }
        return clip(target,from,to,radius).orElse(null);
    }
    public static boolean near(LivingEntity target,net.minecraft.world.phys.Vec3 point,double radius) {
        var boxes=target instanceof com.dantemorrigan.gauntlet.boss.LivingTribunal b?com.dantemorrigan.gauntlet.boss.TribunalHitbox.parts(b):java.util.List.of(target.getBoundingBox());
        for(var box:boxes) {
            double x=Math.max(box.minX,Math.min(box.maxX,point.x)),y=Math.max(box.minY,Math.min(box.maxY,point.y)),z=Math.max(box.minZ,Math.min(box.maxZ,point.z));
            if(point.distanceToSqr(new net.minecraft.world.phys.Vec3(x,y,z))<=radius*radius)return true;
        }
        return false;
    }
    private CombatTargets() {}
}
