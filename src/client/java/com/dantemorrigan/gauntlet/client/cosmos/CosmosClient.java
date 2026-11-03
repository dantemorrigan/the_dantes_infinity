package com.dantemorrigan.gauntlet.client.cosmos;

import com.dantemorrigan.gauntlet.*;
import com.dantemorrigan.gauntlet.cosmos.*;
import com.dantemorrigan.gauntlet.client.StatusHud;
import com.dantemorrigan.gauntlet.client.fx.*;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/** Local prediction uses ordinary player movement packets; destinations and warp completion belong to the server. */
public final class CosmosClient {
    public static final KeyMapping FLIGHT = key("cosmos_flight", GLFW.GLFW_KEY_J);
    public static final KeyMapping NEXT = key("cosmos_next", GLFW.GLFW_KEY_K);
    public static final KeyMapping WARP = key("cosmos_warp", GLFW.GLFW_KEY_U);
    private static boolean enabled;
    private static int selected, warp, cooldown, ticks, impactPause;
    private static int sonicBooms, impactBursts;
    public static int sonicBooms() { return sonicBooms; }
    public static int impactBursts() { return impactBursts; }
    private static Vec3 velocity = Vec3.ZERO, previous;
    private static float intensity;
    private static boolean lastBoost;
    private static net.minecraft.resources.ResourceKey<Level> lastDimension;
    private static KeyMapping key(String name, int code) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.infinity_gauntlet."+name, InputConstants.Type.KEYSYM, code, ClientPower.CATEGORY));
    }
    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetwork.FlightFx.TYPE,(p,c)->{
            boolean local=Minecraft.getInstance().player!=null && Minecraft.getInstance().player.getId()==p.entityId();
            Vec3 n=p.direction().normalize().scale(p.kind()==0?1.2:2.2);
            ClientPower.spawn(ModParticles.COSMOS_WARP_RING,p.pos(),n.x,n.y,n.z);
            ClientPower.spawn(ModParticles.COSMOS_BOOST_FLASH,p.pos(),p.kind()==0?1.4:3,0,0);
            if(p.kind()==0) {
                if(local) sonicBooms++;
                if(local) com.dantemorrigan.gauntlet.client.cutscene.Cutscene.shake(.22f,10);
            } else {
                if(local) { impactBursts++;velocity=Vec3.ZERO;impactPause=10; }
                double distance=Minecraft.getInstance().player==null?128:Minecraft.getInstance().player.position().distanceTo(p.pos());
                if(distance<64) com.dantemorrigan.gauntlet.client.cutscene.Cutscene.shake((float)(.8*(1-distance/64)),18);
                ClientPower.spawn(ModParticles.DETONATION_FLASH,p.pos(),.65*p.strength(),0,0);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(ModNetwork.CosmosState.TYPE, (p,c) -> {
            if (!p.enabled()) clear();
            else {
                enabled=true; selected=Math.floorMod(p.destination(), CosmosRoutes.ALL.size());
                if (warp>0 && p.warpTicks()==0) { velocity=Vec3.ZERO; flash(); }
                warp=p.warpTicks(); cooldown=p.cooldown();
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h,mc)->clear());
        ClientTickEvents.END_CLIENT_TICK.register(CosmosClient::tick);
        CosmosRenderer.init();
    }
    private static void clear() {
        enabled=false; velocity=Vec3.ZERO; previous=null; intensity=0; warp=0; cooldown=0;
        lastBoost=false; lastDimension=null; impactPause=0;sonicBooms=0;impactBursts=0;
        StatusHud.persistent("cosmos",null); StatusHud.persistent("cosmos_keys",null);
    }
    public static boolean inCosmos() {
        var l=Minecraft.getInstance().level; return l!=null && l.dimension().equals(CosmosTravel.DIMENSION);
    }
    public static float atmosphere() {
        var mc=Minecraft.getInstance();
        return mc.level!=null && mc.player!=null && mc.level.dimension().equals(Level.OVERWORLD)
            ? (float)CosmosRoutes.atmosphere(mc.gameRenderer.getMainCamera().position().y) : 0;
    }
    public static float shake(float partial, boolean pitch) {
        if (!enabled || Minecraft.getInstance().screen!=null) return 0;
        return (float)Math.sin((ticks+partial)*(pitch?1.83:2.31))*intensity*.12f;
    }
    private static void flash() {
        var p=Minecraft.getInstance().player;
        if(p!=null) ClientPower.spawn(ModParticles.COSMOS_BOOST_FLASH, FistPosition.of(p,1),1,0,0);
    }
    private static void tick(Minecraft mc) {
        if(mc.player==null || mc.level==null) { clear(); return; }
        if(mc.screen==null) {
            while(FLIGHT.consumeClick()) ClientPlayNetworking.send(new ModNetwork.CosmosAction(0));
            while(NEXT.consumeClick()) if(inCosmos()) ClientPlayNetworking.send(new ModNetwork.CosmosAction(1));
            while(WARP.consumeClick()) if(inCosmos()) ClientPlayNetworking.send(new ModNetwork.CosmosAction(2));
        }
        if(!enabled) return;
        if(mc.isPaused()) return;
        ticks++;
        if(impactPause>0) impactPause--;
        if(cooldown>0) cooldown--;
        if(warp>1) warp--; // only the server clears the final tick / completes travel
        var p=mc.player;
        if(lastDimension!=mc.level.dimension() || previous!=null && previous.distanceToSqr(p.position())>144) {
            velocity=Vec3.ZERO; lastDimension=mc.level.dimension();
        }
        previous=p.position();
        boolean locked=impactPause>0 || mc.screen!=null || com.dantemorrigan.gauntlet.client.cutscene.Cutscene.inputLocked()
            || TimeRewindClient.inputLocked() || RealityClient.inputLocked();
        var input=p.input;
        Vec3 look=p.getViewVector(1);
        double yaw=Math.toRadians(p.getYRot());
        Vec3 left=new Vec3(Math.cos(yaw),0,Math.sin(yaw));
        var move=input.getMoveVector();
        Vec3 thrust=look.scale(move.y).add(left.scale(move.x));
        if(input.keyPresses.jump()) thrust=thrust.add(0,1,0);
        if(input.keyPresses.shift()) thrust=thrust.add(0,-1,0);
        if(locked || warp>0) thrust=Vec3.ZERO;
        if(thrust.lengthSqr()>1) thrust=thrust.normalize();
        boolean boost=!locked && warp==0 && mc.options.keySprint.isDown() && thrust.lengthSqr()>.01;
        double cap=boost?CosmosRoutes.BOOST_SPEED:CosmosRoutes.CRUISE_SPEED;
        velocity=velocity.scale(thrust.lengthSqr()<.01?.8:.94).add(thrust.scale(boost?.23:.10));
        if(velocity.length()>cap) velocity=velocity.normalize().scale(cap);
        if(locked) velocity=Vec3.ZERO;
        p.setDeltaMovement(velocity); p.fallDistance=0;
        intensity+=( (boost||warp>0?1:0)-intensity)*.16f;
        if(boost && !lastBoost) flash();
        lastBoost=boost;
        if(intensity>.15f || inCosmos() || atmosphere()>.2f) effects(mc,look);
        String speed=String.format(java.util.Locale.ROOT,"%.0f",velocity.length()*20);
        if(inCosmos()) {
            var d=CosmosRoutes.ALL.get(selected);
            int distance=(int)p.position().distanceTo(new Vec3(d.arrivalX(),d.arrivalY(),d.arrivalZ()));
            StatusHud.persistent("cosmos",Component.translatable("cosmos.infinity_gauntlet.navigation",Component.translatable("cosmos.infinity_gauntlet."+d.id()),distance,speed));
            StatusHud.persistent("cosmos_keys",warp>0?Component.translatable("cosmos.infinity_gauntlet.warp",String.format(java.util.Locale.ROOT,"%.1f",warp/20f)):
                cooldown>0?Component.translatable("cosmos.infinity_gauntlet.cooldown",(cooldown+19)/20):
                Component.translatable("cosmos.infinity_gauntlet.controls",NEXT.getTranslatedKeyMessage(),WARP.getTranslatedKeyMessage(),FLIGHT.getTranslatedKeyMessage()));
        } else {
            StatusHud.persistent("cosmos",mc.level.dimension().equals(Level.OVERWORLD)?Component.translatable("cosmos.infinity_gauntlet.ascent",(int)p.getY(),(int)CosmosRoutes.EXIT_HEIGHT,speed):null);
            StatusHud.persistent("cosmos_keys",Component.translatable("cosmos.infinity_gauntlet.flight_controls",FLIGHT.getTranslatedKeyMessage()));
        }
    }
    private static void effects(Minecraft mc, Vec3 look) {
        var p=mc.player; var rnd=mc.level.getRandom();
        // <= 4 transient streaks/tick; each expires after 12 ticks. No particles across the universe.
        if(intensity>.15f) {
            Vec3 right=look.cross(new Vec3(0,1,0));
            if(right.lengthSqr()<.01) right=new Vec3(1,0,0); else right=right.normalize();
            Vec3 up=right.cross(look).normalize();
            int count=switch (mc.options.particles().get()) { case MINIMAL -> 1; default -> 4; };
            for(int i=0;i<count;i++) {
                double a=rnd.nextDouble()*Math.PI*2, r=2+rnd.nextDouble()*5;
                Vec3 pos=p.getEyePosition().add(look.scale(4+rnd.nextDouble()*18)).add(right.scale(Math.cos(a)*r)).add(up.scale(Math.sin(a)*r));
                Vec3 v=look.scale(-.8-velocity.length());
                ClientPower.spawn(ModParticles.COSMOS_SPEED_STREAK,pos,v.x,v.y,v.z);
            }
            if(ticks%2==0) {
                ClientPower.spawn(ModParticles.COSMOS_STAR_DUST,FistPosition.of(p,1),-velocity.x*.15,-velocity.y*.15,-velocity.z*.15);
                ClientPower.spawn(ModParticles.SPACE_ENERGY_WISPS,FistPosition.of(p,1),.7,0,0);
            }
            if(warp>0 && ticks%8==0) ClientPower.spawn(ModParticles.COSMOS_WARP_RING,p.getEyePosition().add(look.scale(9)),look.x,look.y,look.z);
        } else if(inCosmos() && ticks%6==0) {
            Vec3 pos=p.getEyePosition().add(rnd.nextGaussian()*12,rnd.nextGaussian()*8,rnd.nextGaussian()*12);
            ClientPower.spawn(ModParticles.COSMOS_STAR_DUST,pos,0,0,0);
        }
    }
    private CosmosClient() {}
}
