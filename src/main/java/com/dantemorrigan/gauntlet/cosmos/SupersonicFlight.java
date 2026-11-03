package com.dantemorrigan.gauntlet.cosmos;

import com.dantemorrigan.gauntlet.CoopRules;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import java.util.*;

/** Server-owned gameplay sonic threshold: the final stage of the gauntlet's boost, not a real-world scale simulation. */
public final class SupersonicFlight {
    public static final double THRESHOLD = CosmosRoutes.BOOST_SPEED * .85;
    private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();
    private static final ArrayDeque<Blast> BLASTS = new ArrayDeque<>();
    private static class Flight {
        Vec3 previous, velocity = Vec3.ZERO;
        ServerLevel level;
        int fastTicks, lastFast = -100, cooldown, belowTicks;
        boolean sonic;
        Flight(ServerPlayer p) { previous=p.position(); level=p.level(); }
    }
    private record Blast(ServerPlayer owner, ServerLevel level, ArrayDeque<BlockPos> blocks, ArrayDeque<BlockPos> fire) {}
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(SupersonicFlight::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->FLIGHTS.remove(h.getPlayer().getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{FLIGHTS.clear();BLASTS.clear();});
    }
    private static void tick(MinecraftServer server) {
        for(ServerPlayer p:server.getPlayerList().getPlayers()) {
            if(!p.isAlive() || p.isSpectator() || !p.hasAttached(CosmosTravel.FLIGHT) || !CosmosTravel.equipped(p)) {
                FLIGHTS.remove(p.getUUID()); continue;
            }
            Flight f=FLIGHTS.computeIfAbsent(p.getUUID(),id->new Flight(p));
            Vec3 now=p.position(), movement=now.subtract(f.previous);
            if(f.level!=p.level() || movement.lengthSqr()>64) {
                FLIGHTS.put(p.getUUID(),new Flight(p)); continue; // dimension changes, warp and admin teleports are not impacts
            }
            if(f.cooldown>0) f.cooldown--;
            double speed=movement.length();
            if(speed>=THRESHOLD) {
                f.fastTicks++; f.belowTicks=0; f.velocity=movement; f.lastFast=p.tickCount;
                if(f.fastTicks>=3 && !f.sonic && f.cooldown==0) {
                    f.sonic=true;
                    fx(p,0,now.add(0,.9,0).add(movement.normalize().scale(2)),movement.normalize(),1);
                    p.level().playSound(null,p.blockPosition(),SoundEvents.GENERIC_EXPLODE.value(),SoundSource.PLAYERS,1.7f,.6f);
                }
            } else {
                f.fastTicks=0;
                // Hysteresis: the boom is one-shot per sustained run; re-arm only after a real slow-down (~2 s well below threshold).
                if(speed<THRESHOLD*.5) { if(++f.belowTicks>40) f.sonic=false; } else f.belowTicks=0;
            }
            if(f.sonic && f.cooldown==0 && p.tickCount-f.lastFast<=8) {
                pruneTrees(p,f.previous.add(0,.9,0),now.add(0,.9,0).add(f.velocity.normalize().scale(1.6)));
                BlockHitResult hit=collision(p,f.velocity);
                if(hit!=null) {
                    impact(p,hit.getLocation(),Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i()),f.velocity.length());
                    f.sonic=false;f.fastTicks=0;f.cooldown=40;f.lastFast=-100;
                }
            }
            f.previous=now;
        }
        // Global budget: bounded crater edits, no drops, no forced chunk generation.
        int budget=256;
        while(budget>0 && !BLASTS.isEmpty()) {
            Blast b=BLASTS.peekFirst();
            if (server.getPlayerList().getPlayer(b.owner().getUUID()) != b.owner()) {
                BLASTS.removeFirst();
                continue;
            }
            while(budget>0 && !b.blocks().isEmpty()) {
                BlockPos pos=b.blocks().removeFirst();budget--;
                if(!CoopRules.canEdit(b.owner(), b.level(), pos)) continue;
                var state=b.level().getBlockState(pos);
                if(!state.isAir() && !state.hasBlockEntity() && !state.is(Blocks.BEDROCK) && state.getDestroySpeed(b.level(),pos)>=0)
                    b.level().setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
            }
            while(budget>0 && b.blocks().isEmpty() && !b.fire().isEmpty()) {
                BlockPos pos=b.fire().removeFirst();budget--;
                var state=Blocks.FIRE.defaultBlockState();
                if(CoopRules.canEdit(b.owner(), b.level(), pos) && b.level().getBlockState(pos).isAir() && state.canSurvive(b.level(),pos))
                    b.level().setBlock(pos,state,Block.UPDATE_ALL);
            }
            if(b.blocks().isEmpty() && b.fire().isEmpty()) BLASTS.removeFirst();
        }
    }
    private static boolean vegetation(net.minecraft.world.level.block.state.BlockState s) {
        return s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES);
    }
    private static void pruneTrees(ServerPlayer p,Vec3 from,Vec3 to) {
        int budget=64;
        AABB area=new AABB(from,to).inflate(1.6);
        Vec3 segment=to.subtract(from); double length=segment.lengthSqr();
        for(BlockPos pos:BlockPos.betweenClosed((int)Math.floor(area.minX),(int)Math.floor(area.minY),(int)Math.floor(area.minZ),
                (int)Math.ceil(area.maxX),(int)Math.ceil(area.maxY),(int)Math.ceil(area.maxZ))) {
            if(!p.level().hasChunkAt(pos)) continue;
            Vec3 center=Vec3.atCenterOf(pos);
            double t=length<.001?0:Math.max(0,Math.min(1,center.subtract(from).dot(segment)/length));
            if(center.distanceToSqr(from.add(segment.scale(t)))>2.56) continue;
            var state=p.level().getBlockState(pos);
            if(CoopRules.canEdit(p, p.level(), pos) && vegetation(state) && state.getDestroySpeed(p.level(),pos)>=0) {
                p.level().setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
                if(--budget==0) break;
            }
        }
    }
    /** Probe the whole player height and width, including downward and sideways collisions. */
    private static BlockHitResult collision(ServerPlayer p,Vec3 velocity) {
        Vec3 direction=velocity.normalize();
        Vec3 right=direction.cross(new Vec3(0,1,0));
        right=right.lengthSqr()<.001?new Vec3(1,0,0):right.normalize();
        double reach=Math.min(1.4,velocity.length());
        BlockHitResult closest=null;
        for(double y:new double[]{.05,.9,1.7}) for(double side:new double[]{-.28,0,.28}) {
            Vec3 start=p.position().add(0,y,0).add(right.scale(side));
            var hit=p.level().clip(new ClipContext(start,start.add(direction.scale(reach)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
            if(hit.getType()!=HitResult.Type.BLOCK || vegetation(p.level().getBlockState(hit.getBlockPos()))) continue;
            if(closest==null || hit.getLocation().distanceToSqr(p.position())<closest.getLocation().distanceToSqr(p.position())) closest=hit;
        }
        return closest;
    }
    private static void impact(ServerPlayer p,Vec3 point,Vec3 normal,double speed) {
        ServerLevel level=p.level();
        double radius=Math.min(5.5,3+speed*.65);
        BlockPos center=BlockPos.containing(point);
        ArrayDeque<BlockPos> blocks=new ArrayDeque<>(),fire=new ArrayDeque<>();
        int r=(int)Math.ceil(radius);
        for(BlockPos pos:BlockPos.betweenClosed(center.offset(-r,-r,-r),center.offset(r,r,r))) {
            double distance=Vec3.atCenterOf(pos).distanceTo(point);
            if(distance<=radius && level.hasChunkAt(pos)) blocks.add(pos.immutable());
        }
        // Search the entire cavity after excavation, so ground and wall impacts can both ignite surviving surfaces.
        for(BlockPos pos:BlockPos.betweenClosed(center.offset(-r,-r-1,-r),center.offset(r,r,r)))
            if(Vec3.atCenterOf(pos).distanceTo(point)<=radius+1 && Math.floorMod(pos.getX()*31+pos.getY()*17+pos.getZ()*13,4)==0) fire.add(pos.immutable());
        if(BLASTS.size()<8) BLASTS.addLast(new Blast(p,level,blocks,fire));
        for(var entity:level.getEntities(p,new AABB(point,point).inflate(radius*1.8),e->e instanceof LivingEntity && e.isAlive() && !e.isSpectator())) {
            if (!CoopRules.canAffect(p, entity)) continue;
            Vec3 away=entity.position().subtract(point); double distance=away.length();
            double force=Math.max(0,1-distance/(radius*1.8));
            com.dantemorrigan.gauntlet.uru.GauntletDamage.hurt(level,entity,p,p.damageSources().playerAttack(p),(float)(28*force));
            Vec3 push=away.lengthSqr()<.01?normal:away.normalize();
            entity.push(push.x*force*1.8,Math.max(.25,push.y)*force*1.8,push.z*force*1.8);entity.hurtMarked=true;
        }
        p.setDeltaMovement(Vec3.ZERO);p.hurtMarked=true;p.fallDistance=0;
        fx(p,1,point.add(normal.scale(.15)),normal,(float)(radius/3));
        level.playSound(null,center,SoundEvents.GENERIC_EXPLODE.value(),SoundSource.PLAYERS,2.5f,.65f);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,point.x,point.y,point.z,1,0,0,0,0);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,point.x,point.y,point.z,48,2,1,2,.15);
    }
    private static void fx(ServerPlayer owner,int kind,Vec3 point,Vec3 direction,float strength) {
        var packet=new ModNetwork.FlightFx(owner.getId(),kind,point,direction,strength);
        for(ServerPlayer viewer:PlayerLookup.around(owner.level(),point,128)) ServerPlayNetworking.send(viewer,packet);
    }
    private SupersonicFlight() {}
}
