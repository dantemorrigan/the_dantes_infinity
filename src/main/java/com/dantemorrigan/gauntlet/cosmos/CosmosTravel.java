package com.dantemorrigan.gauntlet.cosmos;

import com.dantemorrigan.gauntlet.*;
import com.dantemorrigan.gauntlet.net.*;
import com.dantemorrigan.gauntlet.space.SpaceManager;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Voluntary exploration is independent of the irreversible Dead Space record. */
public final class CosmosTravel {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, InfinityGauntlet.id("cosmos"));
    public record Flight(Vec3 home, boolean mayfly, boolean flying, float speed) {
        public static final Codec<Flight> CODEC = RecordCodecBuilder.create(i -> i.group(
            Vec3.CODEC.fieldOf("home").forGetter(Flight::home), Codec.BOOL.fieldOf("mayfly").forGetter(Flight::mayfly),
            Codec.BOOL.fieldOf("flying").forGetter(Flight::flying), Codec.FLOAT.fieldOf("speed").forGetter(Flight::speed)).apply(i, Flight::new));
    }
    public static final AttachmentType<Flight> FLIGHT = AttachmentRegistry.<Flight>builder().persistent(Flight.CODEC)
        .buildAndRegister(InfinityGauntlet.id("cosmos_flight"));
    private static final AttachmentType<Boolean> FIELDS_READY = AttachmentRegistry.<Boolean>builder().persistent(Codec.BOOL)
        .buildAndRegister(InfinityGauntlet.id("cosmos_fields_ready"));
    private static final Map<UUID, Navigation> NAV = new HashMap<>();
    private static final ArrayDeque<RockBlock> BUILD = new ArrayDeque<>();
    private record RockBlock(BlockPos pos, boolean ice) {}
    private static class Navigation { int selected, warp, cooldown; long lastAction = -100; }
    public static boolean inCosmos(ServerPlayer p) { return p.level().dimension().equals(DIMENSION); }
    /** Dimensions where J flight works: Overworld (with exit to space), Nether and End (bounded), and the cosmos itself. */
    private static boolean flightDim(ServerPlayer p) {
        var d = p.level().dimension();
        return d.equals(Level.OVERWORLD) || d.equals(Level.NETHER) || d.equals(Level.END) || d.equals(DIMENSION);
    }
    /** Re-entry from space: appear high above the take-off point and keep flying, so the descent is manual. */
    private static void reenter(ServerPlayer p) {
        Flight f = p.getAttached(FLIGHT);
        ServerLevel overworld = p.level().getServer().overworld();
        Vec3 home = f == null ? Vec3.atBottomCenterOf(overworld.getRespawnData().pos()) : f.home();
        double y = Math.min(CosmosRoutes.EXIT_HEIGHT - 80, Math.max(home.y + 200, 260));
        p.teleport(new TeleportTransition(overworld, new Vec3(home.x, y, home.z), Vec3.ZERO, p.getYRot(), 25, TeleportTransition.DO_NOTHING));
        p.fallDistance = 0;
    }
    public static boolean equipped(ServerPlayer p) {
        var g = GauntletState.activeGauntlet(p);
        return !g.isEmpty() && GauntletState.inserted(g).contains(Stone.SPACE);
    }
    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(ModNetwork.CosmosAction.TYPE, (packet, context) -> action(context.player(), packet.action()));
        ServerTickEvents.END_SERVER_TICK.register(CosmosTravel::tick);
        ServerPlayConnectionEvents.JOIN.register((h,s,server) -> {
            ServerPlayer p = h.getPlayer();
            if (p.hasAttached(FLIGHT)) {
                if (flightDim(p) && equipped(p) && !p.hasAttached(SpaceManager.RECORD)) enable(p);
                else stop(p, inCosmos(p));
            } else if (inCosmos(p)) rescue(p);
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((old,p,alive) -> {
            if (old.hasAttached(FLIGHT)) {
                p.setAttached(FLIGHT,old.getAttached(FLIGHT));
                stop(p,inCosmos(p));
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h,s) -> NAV.remove(h.getPlayer().getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> { NAV.clear(); BUILD.clear(); });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((e,s,a) -> !(e instanceof ServerPlayer p && p.hasAttached(FLIGHT)
            && s.is(DamageTypeTags.IS_FALL)));
    }
    public static void action(ServerPlayer p, int action) {
        if (action < 0 || action > 2 || !p.isAlive() || p.isSpectator() || p.hasAttached(SpaceManager.RECORD)) return;
        Navigation n = NAV.computeIfAbsent(p.getUUID(), id -> new Navigation());
        long now = p.level().getServer().overworld().getGameTime();
        if (now - n.lastAction < 5) return;
        n.lastAction = now;
        if (action == 0 && p.hasAttached(FLIGHT)) {
            if (inCosmos(p) && p.position().distanceTo(new Vec3(0,-400,0)) > 1100) {
                ModStatus.send(p, Component.translatable("cosmos.infinity_gauntlet.return_near_earth")); return;
            }
            if (inCosmos(p)) { reenter(p); return; }
            stop(p, false); return;
        }
        if (!equipped(p)) { ModStatus.send(p, Component.translatable("cosmos.infinity_gauntlet.need_stone")); return; }
        if (action == 0 && !inCosmos(p) && flightDim(p)) {
            var a = p.getAbilities();
            p.setAttached(FLIGHT, new Flight(p.position().add(0,1,0), a.mayfly, a.flying, a.getFlyingSpeed()));
            enable(p);
        } else if (inCosmos(p) && p.hasAttached(FLIGHT)) {
            if (action == 1 && n.warp == 0) n.selected = CosmosRoutes.next(n.selected);
            if (action == 2 && n.warp == 0 && n.cooldown == 0) n.warp = CosmosRoutes.WARP_TICKS;
            sync(p, n);
        }
    }
    private static void enable(ServerPlayer p) {
        p.setNoGravity(true); p.fallDistance = 0;
        p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
        sync(p, NAV.computeIfAbsent(p.getUUID(), id -> new Navigation()));
    }
    private static void sync(ServerPlayer p, Navigation n) {
        ServerPlayNetworking.send(p, new ModNetwork.CosmosState(p.hasAttached(FLIGHT), n.selected, n.warp, n.cooldown));
    }
    public static void stop(ServerPlayer p, boolean returnHome) {
        Flight f = p.removeAttached(FLIGHT);
        NAV.remove(p.getUUID());
        if (returnHome) {
            Vec3 home = f == null ? Vec3.atBottomCenterOf(p.level().getServer().overworld().getRespawnData().pos()).add(0,2,0) : f.home();
            p.teleport(new TeleportTransition(p.level().getServer().overworld(), home, Vec3.ZERO, p.getYRot(), p.getXRot(), TeleportTransition.DO_NOTHING));
        }
        p.setNoGravity(p.hasAttached(SpaceManager.RECORD));
        var a = p.getAbilities();
        // Do not revoke creative/spectator flight if the game mode changed during this trip.
        a.mayfly = p.isCreative() || p.isSpectator() || f != null && f.mayfly();
        a.flying = a.mayfly && (p.isSpectator() || f != null && f.flying());
        if (f != null) a.setFlyingSpeed(f.speed());
        p.onUpdateAbilities(); p.fallDistance = 0;
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 300, 0, false, false));
        ServerPlayNetworking.send(p, new ModNetwork.CosmosState(false, 0, 0, 0));
    }
    private static void rescue(ServerPlayer p) { stop(p, true); }
    private static void tick(MinecraftServer server) {
        ServerLevel space = server.getLevel(DIMENSION);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.hasAttached(FLIGHT)) { NAV.remove(p.getUUID()); continue; }
            if (!p.isAlive()) { stop(p, false); continue; }
            if (!equipped(p) || p.hasAttached(SpaceManager.RECORD) || !flightDim(p)) {
                stop(p, inCosmos(p)); continue;
            }
            p.setNoGravity(true); p.fallDistance = 0;
            if (!p.getAbilities().flying || !p.getAbilities().mayfly) enable(p);
            Navigation n = NAV.computeIfAbsent(p.getUUID(), id -> new Navigation());
            if (n.cooldown > 0) n.cooldown--;
            if (p.level().dimension().equals(Level.NETHER) || p.level().dimension().equals(Level.END)) {
                // No way out of these worlds: keep the player under the roof / build limit.
                double ceiling = p.level().dimension().equals(Level.NETHER) ? 126 : 250;
                if (p.getY() > ceiling) {
                    p.teleport(new TeleportTransition(p.level(), new Vec3(p.getX(), ceiling, p.getZ()), Vec3.ZERO, p.getYRot(), p.getXRot(), TeleportTransition.DO_NOTHING));
                }
            }
            if (p.level().dimension().equals(Level.OVERWORLD) && p.getY() >= CosmosRoutes.EXIT_HEIGHT && space != null) {
                p.teleport(new TeleportTransition(space, new Vec3(0,128,0), Vec3.ZERO, p.getYRot(), 30, TeleportTransition.DO_NOTHING));
                sync(p,n);
            }
            if (inCosmos(p)) {
                if (space != null && !Boolean.TRUE.equals(space.getAttached(FIELDS_READY)) && BUILD.isEmpty()) planFields();
                if (n.warp > 0 && --n.warp == 0) {
                    var d = CosmosRoutes.ALL.get(n.selected);
                    Vec3 arrival = new Vec3(d.arrivalX(), d.arrivalY(), d.arrivalZ());
                    p.teleport(new TeleportTransition(p.level(), arrival, Vec3.ZERO, 180, n.selected == 0 ? 60 : 8, TeleportTransition.DO_NOTHING));
                    n.cooldown = 100; sync(p,n);
                }
                // Planet surfaces are not generated in this first version. Earth has a real re-entry route.
                for (int i=0; i<4; i++) {
                    var d = CosmosRoutes.ALL.get(i); Vec3 center = new Vec3(d.x(),d.y(),d.z());
                    Vec3 delta = p.position().subtract(center);
                    if (delta.length() < d.radius()+16) {
                        if (i == 0) { reenter(p); sync(p,n); break; }
                        Vec3 out = delta.lengthSqr() < 1 ? new Vec3(0,1,0) : delta.normalize();
                        p.teleport(new TeleportTransition(p.level(),center.add(out.scale(d.radius()+28)),Vec3.ZERO,p.getYRot(),p.getXRot(),TeleportTransition.DO_NOTHING));
                    }
                }
                if (p.hasAttached(FLIGHT) && (Math.abs(p.getX())>12000 || Math.abs(p.getZ())>12000 || p.getY() < -900 || p.getY()>900)) {
                    p.teleport(new TeleportTransition(p.level(),new Vec3(0,128,0),Vec3.ZERO,p.getYRot(),p.getXRot(),TeleportTransition.DO_NOTHING));
                }
            }
            if (p.hasAttached(FLIGHT) && p.tickCount % 20 == 0) sync(p,n);
        }
        // Bounded world edits, real vanilla blocks. Completion survives relog and restart.
        if (space != null && !BUILD.isEmpty()) {
            for (int budget=0; budget<128 && !BUILD.isEmpty(); budget++) {
                RockBlock b = BUILD.removeFirst();
                if (space.getBlockState(b.pos()).isAir()) space.setBlock(b.pos(), (b.ice() ? Blocks.PACKED_ICE : Blocks.STONE).defaultBlockState(), 2);
            }
            if (BUILD.isEmpty()) space.setAttached(FIELDS_READY,true);
        }
    }
    private static void planFields() {
        Random random = new Random(0xC05A05);
        for (int field=4; field<6; field++) {
            var d = CosmosRoutes.ALL.get(field);
            for (int rock=0; rock<28; rock++) {
                double angle=random.nextDouble()*Math.PI*2, distance=24+random.nextDouble()*74;
                int cx=(int)(d.x()+Math.cos(angle)*distance), cy=(int)(d.y()+random.nextGaussian()*18), cz=(int)(d.z()+Math.sin(angle)*distance);
                int radius=2+random.nextInt(3);
                for(int x=-radius;x<=radius;x++) for(int y=-radius;y<=radius;y++) for(int z=-radius;z<=radius;z++)
                    if(x*x+y*y+z*z <= radius*radius && random.nextDouble()>.08)
                        BUILD.add(new RockBlock(new BlockPos(cx+x,cy+y,cz+z),field==5));
            }
        }
    }
    private CosmosTravel() {}
}
