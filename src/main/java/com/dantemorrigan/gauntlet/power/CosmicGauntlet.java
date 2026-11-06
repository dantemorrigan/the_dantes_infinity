package com.dantemorrigan.gauntlet.power;

import com.dantemorrigan.gauntlet.*;
import com.dantemorrigan.gauntlet.ability.Ability;
import com.dantemorrigan.gauntlet.boss.TribunalSpace;
import com.dantemorrigan.gauntlet.cosmos.CosmosTravel;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.uru.GauntletDamage;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.*;
import java.util.*;

/** Zero-gravity variants: aim in 3D, affect bounding volumes, never edit empty space as terrain. */
public final class CosmicGauntlet {
    private record Hold(ServerPlayer owner, net.minecraft.world.item.ItemStack gauntlet, int started) {}
    private record Rock(ServerPlayer owner, net.minecraft.server.level.ServerLevel level, Vec3 from, Vec3 to, int impact) {}
    private static final Map<UUID,EnumMap<Ability,Long>> COOLDOWNS=new HashMap<>();
    private static final Map<UUID,Hold> HOLDS=new HashMap<>();
    private static final List<Rock> ROCKS=new ArrayList<>();
    public static boolean adapted(Ability a) { return a==Ability.REALITY_STRIKE || a==Ability.INVERSION || a==Ability.SOUL || a==Ability.SOUL_MASS || a==Ability.METEOR_STREAM || a==Ability.GRAB; }
    public static int cooldown(ServerPlayer p,Ability a) { return (int)Math.max(0,COOLDOWNS.getOrDefault(p.getUUID(),new EnumMap<>(Ability.class)).getOrDefault(a,0L)-p.level().getGameTime()); }
    private static void cool(ServerPlayer p,Ability a,int ticks) {COOLDOWNS.computeIfAbsent(p.getUUID(),k->new EnumMap<>(Ability.class)).put(a,p.level().getGameTime()+ticks);}
    public static boolean active(ServerPlayer p) {return HOLDS.containsKey(p.getUUID());}
    public static void stop(ServerPlayer p) {HOLDS.remove(p.getUUID());}
    private static TribunalSpace.Point point(Vec3 p) {return new TribunalSpace.Point(p.x,p.y,p.z);}
    public static double reach(ServerPlayer p,double wanted) {
        if(!CosmosTravel.inCosmos(p)) return wanted;
        Vec3 eye=p.getEyePosition();double result=0;
        for(double d=1;d<=wanted;d++) {if(!TribunalSpace.clear(point(eye),point(eye.add(p.getLookAngle().scale(d))),1)) break;result=d;}
        return result;
    }
    public static void fx(ServerPlayer p,int kind,Vec3 from,Vec3 to,float size,int color,int ticks) {
        var packet=new ModNetwork.CosmicFx(kind,from,to,size,color,ticks);
        for(var witness:p.level().players()) if(witness.position().distanceToSqr(from)<400*400) ServerPlayNetworking.send(witness,packet);
    }
    private static double distance(Vec3 at,AABB box) {
        double x=Math.max(box.minX,Math.min(box.maxX,at.x)), y=Math.max(box.minY,Math.min(box.maxY,at.y)), z=Math.max(box.minZ,Math.min(box.maxZ,at.z));
        return at.distanceTo(new Vec3(x,y,z));
    }
    private static void pulse(ServerPlayer p,Vec3 center,double radius,float damage,int color) {pulse(p,center,radius,damage,color,false);}
    private static void pulse(ServerPlayer p,Vec3 center,double radius,float damage,int color,boolean bossesOnly) {
        for(var e:p.level().getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius),e->e!=p && e.isAlive() && CoopRules.canAffect(p,e))) {
            if(bossesOnly && !MindStone.isBoss(e)) continue;
            if(distance(center,e.getBoundingBox())>radius) continue;
            if(GauntletDamage.hurt(p.level(),e,p,p.damageSources().playerAttack(p),damage) && !MindStone.isBoss(e)) {
                Vec3 direction=e.getBoundingBox().getCenter().subtract(center).normalize();e.push(direction.scale(.8));e.hurtMarked=true;
            }
        }
        fx(p,0,center,center,(float)radius,color,30);
    }
    public static void cast(ServerPlayer p,Ability ability,int charge) {
        if(!CosmosTravel.inCosmos(p) || !p.isAlive() || GauntletState.activeGauntlet(p).isEmpty() || cooldown(p,ability)>0) return;
        Vec3 target=PowerAbility.pick(p,reach(p,170)).getLocation();
        switch(ability) {
            case REALITY_STRIKE -> {pulse(p,target,24,35,0xffff486c);cool(p,ability,80);}
            case INVERSION -> {HOLDS.put(p.getUUID(),new Hold(p,GauntletState.activeGauntlet(p),p.tickCount));cool(p,ability,160);}
            case SOUL -> {
                var hit=PowerAbility.pick(p,reach(p,170));
                if(hit instanceof EntityHitResult e && MindStone.isBoss(e.getEntity())) pulse(p,target,8,38,0xffffa151);
                else SoulStone.action(p,0);
                cool(p,ability,100);
            }
            case SOUL_MASS -> {pulse(p,p.position(),155,22,0xffffa151,true);SoulStone.action(p,1);cool(p,ability,240);}
            case GRAB -> {
                // The cosmic judge cannot be lifted; a mind assault still damages his exposed consciousness.
                var hit=PowerAbility.pick(p,reach(p,170));
                if(hit instanceof EntityHitResult entity && MindStone.isBoss(entity.getEntity())) {pulse(p,hit.getLocation(),6,22,0xffffdf61);cool(p,ability,65);}
                else MindStone.action(p,0);
            }
            case METEOR_STREAM -> {
                Vec3 forward=p.getLookAngle();Vec3 side=forward.cross(new Vec3(0,1,0));if(side.lengthSqr()<.01) side=new Vec3(1,0,0);side=side.normalize();
                for(int i=0;i<Math.min(8,3+charge/15);i++) {
                    Vec3 from=p.getEyePosition().add(side.scale((i%2==0?1:-1)*(7+i))).add(forward.scale(5));
                    if(!TribunalSpace.clear(point(from),point(target),2)) continue;
                    int flight=25+i*4;ROCKS.add(new Rock(p,p.level(),from,target,p.tickCount+flight));fx(p,1,from,target,2.4f,0xffff8045,flight);
                }
                cool(p,ability,220);
            }
            default -> {}
        }
        p.swing(net.minecraft.world.InteractionHand.OFF_HAND,true);
    }
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server->{
            HOLDS.values().removeIf(h->{
                ServerPlayer p=h.owner();
                if(!p.isAlive() || !CosmosTravel.inCosmos(p) || GauntletState.activeGauntlet(p)!=h.gauntlet() || p.tickCount-h.started()>160) return true;
                if(p.tickCount%12==0) pulse(p,PowerAbility.pick(p,reach(p,160)).getLocation(),28,12,0xffd94dff);
                return false;
            });
            ROCKS.removeIf(r->{
                if(!r.owner().isAlive() || r.owner().level()!=r.level()) return true;
                if(r.owner().tickCount<r.impact()) return false;
                pulse(r.owner(),r.to(),12,24,0xffff8045);return true;
            });
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{stop(h.getPlayer());COOLDOWNS.remove(h.getPlayer().getUUID());ROCKS.removeIf(r->r.owner()==h.getPlayer());});
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{HOLDS.clear();ROCKS.clear();COOLDOWNS.clear();});
    }
    private CosmicGauntlet() {}
}
