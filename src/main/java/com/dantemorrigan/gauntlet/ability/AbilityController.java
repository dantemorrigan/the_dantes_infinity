package com.dantemorrigan.gauntlet.ability;

import com.dantemorrigan.gauntlet.*;
import com.dantemorrigan.gauntlet.net.*;
import com.dantemorrigan.gauntlet.power.*;
import com.dantemorrigan.gauntlet.cosmos.*;
import com.dantemorrigan.gauntlet.space.*;
import com.dantemorrigan.gauntlet.celestial.CelestialApocalypse;
import com.dantemorrigan.gauntlet.menu.GauntletMenu;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.*;

/** A chosen ability never executes until a separate activation packet. Server owns charge and readiness. */
public final class AbilityController {
    private static final AttachmentType<String> SELECTED=AttachmentRegistry.<String>builder().persistent(Codec.STRING)
        .buildAndRegister(InfinityGauntlet.id("selected_ability"));
    private record Charge(Ability ability,ItemStack gauntlet,ServerLevel level,int started) {}
    private static final Map<UUID,Charge> CHARGES=new HashMap<>();
    private static final Map<UUID,Ability> HELD=new HashMap<>();
    private static final Map<UUID,Long> LAST_START=new HashMap<>();
    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(ModNetwork.AbilitySelect.TYPE,(p,c)->select(c.player(),p.id(),p.destination()));
        ServerPlayNetworking.registerGlobalReceiver(ModNetwork.AbilityAction.TYPE,(p,c)->action(c.player(),p.action()));
        ServerTickEvents.END_SERVER_TICK.register(AbilityController::tick);
        ServerPlayConnectionEvents.JOIN.register((h,s,server)->sync(h.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{cancel(h.getPlayer());LAST_START.remove(h.getPlayer().getUUID());});
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{CHARGES.clear();HELD.clear();LAST_START.clear();TimeFlow.clear();});
    }
    public static Ability selected(ServerPlayer p) {
        Ability a=Ability.byId(p.getAttached(SELECTED));return a==null?Ability.STRIKE:a;
    }
    public static int chargeTicks(ServerPlayer p) {
        Charge c=CHARGES.get(p.getUUID());return c==null?0:Math.max(0,p.tickCount-c.started());
    }
    public static int heldKind(ServerPlayer p) { return MindStone.holdingMob(p)?2:MindStone.holding(p)?1:0; }
    public static int cooldown(ServerPlayer p,Ability a) {
        if(a==Ability.TIME_FLOW) return TimeFlow.cooldown(p);
        if(p.isCreative()) return 0;
        return switch(a) {
            case STRIKE,PLANET,CELESTIAL -> PowerAbility.cooldown(p,a==Ability.PLANET);
            case PORTAL -> SpaceStone.cooldown(p,false);
            case BLINK -> SpaceStone.cooldown(p,true);
            case WARP -> CosmosTravel.warpCooldown(p);
            case INVERSION -> RealityStone.cooldown(p);
            case REALITY_STRIKE -> RealityStrike.cooldown(p);
            case REWIND -> TimeAbility.cooldown(p);
            case DAY_NIGHT -> TimeShift.cooldown(p);
            case GRAB -> MindStone.holding(p)?0:MindStone.cooldown(p);
            case SOUL -> SoulStone.cooldown(p,false);
            case SOUL_MASS -> SoulStone.cooldown(p,true);
            case BEAM -> InfinityBeam.cooldown(p);
            case METEOR_STREAM -> com.dantemorrigan.gauntlet.meteor.MeteorStream.cooldown(p);
            default -> 0;
        };
    }
    public static String reason(ServerPlayer p,Ability a) {
        if(!p.isAlive() || p.isSpectator() || com.dantemorrigan.gauntlet.boss.TribunalEncounter.awaitingReturn(p)) return "unavailable";
        GauntletConfig config = GauntletConfig.get();
        if (a == Ability.PLANET && !config.allowPlanetDestruction
                || a == Ability.CELESTIAL && !config.allowCelestialDestruction
                || a == Ability.SNAP && !config.allowInfinitySnap
                || a == Ability.METEOR_STREAM && !config.allowMeteorStream
                || a == Ability.DAY_NIGHT && !config.allowTimeShift) return "disabled";
        if(a==Ability.PLANET) {
            if(PlanetDestruction.bossBlocksDestruction(p)) return "boss_fight";
            if(p.level().dimension().equals(Level.END)) return "planet_end";
        }
        if(p.containerMenu!=p.inventoryMenu) return "busy";
        if(com.dantemorrigan.gauntlet.snap.InfinitySnap.snapped(GauntletState.wornGauntlet(p))) return "broken";
        ItemStack g=GauntletState.activeGauntlet(p);
        if(g.isEmpty()) return "gauntlet";
        if(com.dantemorrigan.gauntlet.snap.InfinitySnap.inVoid(p)) return "dimension";
        if(com.dantemorrigan.gauntlet.snap.InfinitySnap.casting(p)
                || p.hasAttached(com.dantemorrigan.gauntlet.snap.InfinitySnap.RECORD)) return "busy";
        var stones=GauntletState.inserted(g);
        if(a.stone!=null && !stones.contains(a.stone) || (a==Ability.BEAM || a==Ability.SNAP) && stones.size()!=6) return "stone";
        if(a==Ability.PLANET && !PlanetDestruction.hasReturnStone(p)) return "planet_time";
        if(a==Ability.REWIND && TimeAbility.canRestorePlanet(p) && !TimeAbility.isRewinding(p)) return "";
        if(PlanetDestruction.isBusy(p) || TimeAbility.isRewinding(p)) return "busy";
        if(a==Ability.TIME_FLOW && (TimeFlow.active(p) || InfinityBeam.active(p))) return "busy";
        if(SpaceManager.inSpace(p) && a!=Ability.REWIND) return "dimension";
        if(a==Ability.BEAM && GauntletEffects.state(p).phase!=GauntletEffects.Phase.AWAKENED) return "awakening";
        if((a==Ability.DAY_NIGHT || a==Ability.CELESTIAL || a==Ability.METEOR_STREAM) && !p.level().dimension().equals(Level.OVERWORLD)) return "dimension";
        if(a==Ability.METEOR_STREAM && CelestialApocalypse.eventActive(p.level().getServer()) && !com.dantemorrigan.gauntlet.meteor.MeteorStream.aiming(p)) return "sky";
        if((a==Ability.WARP || a==Ability.DESTINATIONS) && !CosmosTravel.inCosmos(p)) return "cosmos";
        if((a==Ability.PORTAL || a==Ability.BLINK) && CosmosTravel.inCosmos(p)) return "dimension";
        if(a==Ability.FLIGHT && CosmosTravel.inCosmos(p) && com.dantemorrigan.gauntlet.cosmos.CosmosRoutes.earthDistance(p.getX(),p.getY(),p.getZ())>com.dantemorrigan.gauntlet.cosmos.CosmosRoutes.RETURN_RANGE) return "earth";
        if(a==Ability.TEAR && !MindStone.holdingMob(p)) return "mob";
        if(a==Ability.DROP && !MindStone.holding(p)) return "hold";
        if(cooldown(p,a)>0) return "cooldown";
        return "";
    }
    public static void select(ServerPlayer p,String id,int destination) {
        if(id.equals("inventory")) {
            var g=GauntletState.wornGauntlet(p);if(g.isEmpty() || !p.isAlive()) return;
            cancel(p);MindStone.drop(p);
            p.openMenu(new SimpleMenuProvider((window,inv,player)->new GauntletMenu(window,inv,g,40),Component.translatable("container.infinity_gauntlet.gauntlet")));
            return;
        }
        Ability a=Ability.byId(id);if(a==null || a==Ability.DESTINATIONS) { sync(p);return; }
        // Selection is always available; readiness is checked only when casting.
        if(!p.isAlive() || GauntletState.activeGauntlet(p).isEmpty()) {sync(p);return;}
        if(destination!=-1) {
            if(a==Ability.PORTAL) { if(!SpaceStone.selectDestination(p,destination)) return; }
            else if(a==Ability.WARP) { if(!CosmosTravel.selectDestination(p,destination)) return; }
            else return;
        }
        cancel(p);
        if(a.stone!=Stone.MIND) MindStone.drop(p);
        p.setAttached(SELECTED,a.id);sync(p);
    }
    /** 0 start, 1 release, 2 cancel, 3 contextual alternate. No client timing, targets or world coordinates are accepted. */
    public static void action(ServerPlayer p,int action) {
        if(action==2) {cancel(p);sync(p);return;}
        if(action==1) {release(p);sync(p);return;}
        if(action==3) {
            Ability current=selected(p);
            if(current==Ability.PORTAL) {
                String why=reason(p,current);
                if(why.isEmpty() || why.equals("cooldown")) SpaceStone.action(p,1);
                sync(p);return;
            }
            if(current!=Ability.GRAB || !reason(p,Ability.TEAR).isEmpty()) {sync(p);return;}
            MindStone.action(p,1);sync(p);return;
        }
        if(action!=0 || TimeFlow.active(p) || HELD.containsKey(p.getUUID()) || CHARGES.containsKey(p.getUUID())) return;
        long now=p.level().getServer().overworld().getGameTime();
        if(now-LAST_START.getOrDefault(p.getUUID(),-100L)<3) return;
        LAST_START.put(p.getUUID(),now);
        Ability a=selected(p);String why=reason(p,a);
        if(!why.isEmpty()) {ModStatus.send(p,Component.translatable("ability.infinity_gauntlet.reason."+why));sync(p);return;}
        cancel(p);
        ItemStack g=GauntletState.activeGauntlet(p);
        if(p.isUsingItem()) p.stopUsingItem();
        if(a.power()) {
            int mode=a==Ability.STRIKE?PowerAbility.MODE_STRIKE:a==Ability.PLANET?PowerAbility.MODE_PLANET:PowerAbility.MODE_CELESTIAL;
            g.set(ModComponents.POWER_MODE,mode);g.remove(ModComponents.ULTIMATE_MODE);
            CHARGES.put(p.getUUID(),new Charge(a,g,p.level(),p.tickCount));
            GauntletState.setInUse(g,EnumSet.of(Stone.POWER));
        } else switch(a) {
            case PORTAL -> SpaceStone.action(p,0);
            case BLINK -> SpaceStone.action(p,2);
            case FLIGHT -> CosmosTravel.action(p,0);
            case WARP -> CosmosTravel.warpSelected(p);
            case REALITY_STRIKE -> RealityStrike.strike(p);
            case INVERSION -> {RealityStone.hold(p,true);if(RealityStone.active(p)) HELD.put(p.getUUID(),a);}
            case REWIND -> TimeAbility.request(p);
            case DAY_NIGHT -> TimeShift.request(p);
            case TIME_FLOW -> TimeFlow.start(p,g);
            case GRAB -> MindStone.action(p,0);
            case TEAR -> MindStone.action(p,1);
            case DROP -> MindStone.drop(p);
            case SOUL -> SoulStone.action(p,0);
            case SOUL_MASS -> SoulStone.action(p,1);
            case SNAP -> com.dantemorrigan.gauntlet.snap.InfinitySnap.cast(p);
            case BEAM -> {InfinityBeam.request(p,true);if(InfinityBeam.active(p)) HELD.put(p.getUUID(),a);}
            case METEOR_STREAM -> {com.dantemorrigan.gauntlet.meteor.MeteorStream.begin(p);if(com.dantemorrigan.gauntlet.meteor.MeteorStream.aiming(p)) HELD.put(p.getUUID(),a);}
            default -> {}
        }
        sync(p);
    }
    private static void release(ServerPlayer p) {
        Charge c=CHARGES.remove(p.getUUID());
        if(c!=null) {
            CelestialApocalypse.resetAim(p);
            if(p.isAlive() && c.level()==p.level() && GauntletState.activeGauntlet(p)==c.gauntlet())
                PowerAbility.release(p,c.gauntlet(),Math.max(0,p.tickCount-c.started()));
            else GauntletState.setInUse(c.gauntlet(),EnumSet.noneOf(Stone.class));
        }
        Ability a=HELD.remove(p.getUUID());
        if(a==Ability.BEAM) InfinityBeam.cancel(p);
        if(a==Ability.INVERSION) RealityStone.stop(p);
        if(a==Ability.METEOR_STREAM) com.dantemorrigan.gauntlet.meteor.MeteorStream.release(p);
    }
    public static void cancel(ServerPlayer p) {
        Charge c=CHARGES.remove(p.getUUID());
        if(c!=null) GauntletState.setInUse(c.gauntlet(),EnumSet.noneOf(Stone.class));
        HELD.remove(p.getUUID());CelestialApocalypse.resetAim(p);
        TimeFlow.cancel(p);InfinityBeam.cancel(p);RealityStone.stop(p);com.dantemorrigan.gauntlet.meteor.MeteorStream.cancel(p);
        if(p.isUsingItem() && p.getUseItem().is(ModItems.GAUNTLET)) p.stopUsingItem();
    }
    private static void tick(MinecraftServer server) {
        TimeFlow.tick(server);
        for(ServerPlayer p:server.getPlayerList().getPlayers()) {
            Charge c=CHARGES.get(p.getUUID());
            if(c!=null) {
                if(!p.isAlive() || p.isSpectator() || c.level()!=p.level() || GauntletState.activeGauntlet(p)!=c.gauntlet()
                    || !GauntletState.inserted(c.gauntlet()).contains(Stone.POWER) || PlanetDestruction.isBusy(p) || TimeAbility.isRewinding(p)) cancel(p);
                else if(c.ability()==Ability.CELESTIAL && CelestialApocalypse.aimTick(p,c.gauntlet(),chargeTicks(p))) cancel(p);
                else if(chargeTicks(p)>1200) cancel(p);
            }
            Ability held=HELD.get(p.getUUID());
            if(held!=null && (!p.isAlive() || !reasonIgnoringCooldown(p,held))) cancel(p);
            if(p.tickCount%5==0) sync(p);
        }
    }
    private static boolean reasonIgnoringCooldown(ServerPlayer p,Ability a) {
        String r=reason(p,a);return r.isEmpty() || r.equals("cooldown");
    }
    private static void sync(ServerPlayer p) {
        JsonObject state=new JsonObject();
        for(Ability a:Ability.values()) {
            JsonObject entry=new JsonObject();entry.addProperty("reason",reason(p,a));entry.addProperty("cooldown",cooldown(p,a));state.add(a.id,entry);
        }
        ServerPlayNetworking.send(p,new ModNetwork.AbilityState(selected(p).id,state.toString(),heldKind(p),selected(p)==Ability.BEAM?InfinityBeam.chargeAge(p):chargeTicks(p),
            CosmosTravel.route(p),SpaceStone.destination(GauntletState.activeGauntlet(p),p.level())));
    }
    private AbilityController() {}
}
