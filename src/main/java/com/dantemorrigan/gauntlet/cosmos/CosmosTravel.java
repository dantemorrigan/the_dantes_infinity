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
    private static final AttachmentType<Flight> SUSPENDED_FLIGHT = AttachmentRegistry.<Flight>builder()
        .persistent(Flight.CODEC).copyOnDeath()
        .buildAndRegister(InfinityGauntlet.id("suspended_cosmos_flight"));
    private static final Map<UUID, Navigation> NAV = new HashMap<>();
    /** Protect only the first descent after switching flight off, without changing gravity. */
    private static final Map<UUID, LandingGrace> LANDING_GRACE = new HashMap<>();
    private static final class LandingGrace {
        final int expires;
        int groundedTicks;
        LandingGrace(int expires) { this.expires = expires; }
    }
    private static class Navigation { int selected, planned = -1, warp, cooldown; long lastAction = -100; }
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
            if (com.dantemorrigan.gauntlet.boss.TribunalEncounter.awaitingReturn(p)) return;
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
        ServerPlayConnectionEvents.DISCONNECT.register((h,s) -> {
            NAV.remove(h.getPlayer().getUUID());
            LANDING_GRACE.remove(h.getPlayer().getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> { NAV.clear(); LANDING_GRACE.clear(); });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((e,s,a) -> {
            if (!(e instanceof ServerPlayer p) || !s.is(DamageTypeTags.IS_FALL)) return true;
            if (p.hasAttached(FLIGHT)) return false;
            LandingGrace grace = LANDING_GRACE.remove(p.getUUID());
            return grace == null || p.tickCount > grace.expires;
        });
    }
    public static void action(ServerPlayer p, int action) { action(p, action, false); }

    public static void cancelWarp(ServerPlayer player) {
        Navigation nav = NAV.get(player.getUUID());
        if (nav != null && nav.warp > 0) { nav.warp = 0; sync(player, nav); }
    }

    public static void warpSelected(ServerPlayer p) { action(p, 2, true); }

    private static void action(ServerPlayer p, int action, boolean useRoute) {
        if (com.dantemorrigan.gauntlet.boss.TribunalEncounter.awaitingReturn(p)) return;
        if (action < 0 || action > 2 || !p.isAlive() || p.isSpectator() || p.hasAttached(SpaceManager.RECORD)) return;
        Navigation n = NAV.computeIfAbsent(p.getUUID(), id -> new Navigation());
        long now = p.level().getServer().overworld().getGameTime();
        if (now - n.lastAction < 5) return;
        n.lastAction = now;
        if (action == 0 && p.hasAttached(FLIGHT)) {
            if (inCosmos(p) && CosmosRoutes.earthDistance(p.getX(),p.getY(),p.getZ()) > CosmosRoutes.RETURN_RANGE) {
                ModStatus.send(p, Component.translatable("cosmos.infinity_gauntlet.return_near_earth")); return;
            }
            if (inCosmos(p)) {
                n.warp = 0;
                n.planned = -1;
                reenter(p);
                sync(p,n);
                return;
            }
            stop(p, false); return;
        }
        if (!equipped(p)) { ModStatus.send(p, Component.translatable("cosmos.infinity_gauntlet.need_stone")); return; }
        if (action == 0 && !inCosmos(p) && flightDim(p)) {
            com.dantemorrigan.gauntlet.stormbreaker.Stormbreaker.releaseFlight(p);
            var a = p.getAbilities();
            p.setAttached(FLIGHT, new Flight(p.position().add(0,1,0), a.mayfly, a.flying, a.getFlyingSpeed()));
            enable(p);
        } else if (inCosmos(p) && p.hasAttached(FLIGHT)) {
            if (action == 2 && n.warp == 0 && n.cooldown == 0) {
                Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
                int target = useRoute && n.planned >= 0 ? n.planned
                    : CosmosRoutes.aim(eye.x, eye.y, eye.z, look.x, look.y, look.z, 2.5);
                if (target < 0) ModStatus.send(p, Component.translatable("cosmos.infinity_gauntlet.no_target"));
                else { n.selected = target; n.planned = -1; n.warp = CosmosRoutes.WARP_TICKS; }
            }
            sync(p, n);
        }
    }
    /** Re-enable flight immediately after a Tribunal respawn, before movement packets arrive. */
    public static void restoreFlight(ServerPlayer p, Flight flight) {
        p.setAttached(FLIGHT, flight);
        enable(p);
    }
    private static void enable(ServerPlayer p) {
        p.setNoGravity(true); p.fallDistance = 0;
        p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
        sync(p, NAV.computeIfAbsent(p.getUUID(), id -> new Navigation()));
    }
    private static void sync(ServerPlayer p, Navigation n) {
        ServerPlayNetworking.send(p, new ModNetwork.CosmosState(p.hasAttached(FLIGHT), n.warp > 0 ? n.selected : n.planned, n.warp, n.cooldown));
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
        if (p.isAlive()) LANDING_GRACE.put(p.getUUID(), new LandingGrace(p.tickCount + 300));
        else LANDING_GRACE.remove(p.getUUID());
        ServerPlayNetworking.send(p, new ModNetwork.CosmosState(false, 0, 0, 0));
    }
    public static void suspendForEvent(ServerPlayer player) {
        Flight flight = player.getAttached(FLIGHT);
        if (flight == null) return;
        player.setAttached(SUSPENDED_FLIGHT, flight);
        stop(player, false);
    }

    public static void resumeAfterEvent(ServerPlayer player) {
        Flight flight = player.removeAttached(SUSPENDED_FLIGHT);
        if (flight != null && equipped(player) && flightDim(player)) {
            player.setAttached(FLIGHT, flight);
            enable(player);
            sync(player, NAV.computeIfAbsent(player.getUUID(), id -> new Navigation()));
        } else if (inCosmos(player)) {
            if (flight != null) player.setAttached(FLIGHT, flight);
            rescue(player);
        }
    }

    private static void rescue(ServerPlayer p) { stop(p, true); }
    private static void tick(MinecraftServer server) {
        ServerLevel space = server.getLevel(DIMENSION);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            LandingGrace grace = LANDING_GRACE.get(p.getUUID());
            if (grace != null) {
                // The movement packet can set onGround before vanilla dispatches fall damage.
                // Keep a short landing window, then expire even on harmless/soft landings.
                if (p.onGround()) grace.groundedTicks++; else grace.groundedTicks = 0;
                if (!p.isAlive() || p.tickCount > grace.expires || grace.groundedTicks >= 4)
                    LANDING_GRACE.remove(p.getUUID());
            }
            if (com.dantemorrigan.gauntlet.boss.TribunalEncounter.awaitingReturn(p)) continue;
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
                { var e = CosmosRoutes.ALL.get(CosmosRoutes.EARTH);
                    p.teleport(new TeleportTransition(space, new Vec3(e.arrivalX(), e.arrivalY(), e.arrivalZ()), Vec3.ZERO, e.arrivalYaw(), 20, TeleportTransition.DO_NOTHING)); }
                sync(p,n);
            }
            if (inCosmos(p)) {
                if (n.warp > 0 && --n.warp == 0) {
                    var d = CosmosRoutes.ALL.get(n.selected);
                    Vec3 arrival = new Vec3(d.arrivalX(), d.arrivalY(), d.arrivalZ());
                    p.teleport(new TeleportTransition(p.level(), arrival, Vec3.ZERO, d.arrivalYaw(), 0, TeleportTransition.DO_NOTHING));
                    n.cooldown = 100; sync(p,n);
                }
                if(n.warp==0 && CosmosRoutes.entersEarth(p.getX(),p.getY(),p.getZ())) {
                    n.planned=-1;reenter(p);sync(p,n);continue;
                }
                constrainPosition(p);
            }
            if (p.hasAttached(FLIGHT) && p.tickCount % 20 == 0) sync(p,n);
        }
    }
    private static void constrainPosition(ServerPlayer p) {
        Vec3 position = p.position();
        Vec3 safe = bounded(position);
        for (var body : CosmosRoutes.ALL) {
            Vec3 center = new Vec3(body.x(), body.y(), body.z());
            Vec3 offset = safe.subtract(center);
            if (offset.lengthSqr() >= Math.pow(body.radius() + 16, 2)) continue;
            Vec3 direction = offset.lengthSqr() < 1 ? new Vec3(1, 0, 0) : offset.normalize();
            Vec3 outside = center.add(direction.scale(body.radius() + CosmosRoutes.BODY_CLEARANCE));
            // For a player inside the Sun near the vertical limit, exit toward its equator.
            if (outside.y < CosmosRoutes.MIN_Y || outside.y > CosmosRoutes.MAX_Y) {
                Vec3 horizontal = new Vec3(offset.x, 0, offset.z);
                direction = horizontal.lengthSqr() < 1 ? new Vec3(1, 0, 0) : horizontal.normalize();
                outside = center.add(direction.scale(body.radius() + CosmosRoutes.BODY_CLEARANCE));
            }
            safe = bounded(outside);
            break;
        }
        if (!position.equals(safe)) {
            p.teleport(new TeleportTransition(p.level(), safe, Vec3.ZERO, p.getYRot(), p.getXRot(), TeleportTransition.DO_NOTHING));
        }
    }

    private static Vec3 bounded(Vec3 pos) {
        return new Vec3(
            net.minecraft.util.Mth.clamp(pos.x, -CosmosRoutes.LIMIT, CosmosRoutes.LIMIT),
            net.minecraft.util.Mth.clamp(pos.y, CosmosRoutes.MIN_Y, CosmosRoutes.MAX_Y),
            net.minecraft.util.Mth.clamp(pos.z, -CosmosRoutes.LIMIT, CosmosRoutes.LIMIT));
    }

    private CosmosTravel() {}
    public static int warpCooldown(ServerPlayer p) { Navigation n=NAV.get(p.getUUID()); return n==null?0:Math.max(n.cooldown,n.warp); }
    public static int route(ServerPlayer p) { Navigation n=NAV.get(p.getUUID()); return n==null?0:n.planned>=0?n.planned:n.selected; }
    public static boolean selectDestination(ServerPlayer p,int index) {
        if(index<0 || index>=CosmosRoutes.ALL.size() || !inCosmos(p) || !p.hasAttached(FLIGHT) || !equipped(p)) return false;
        Navigation n=NAV.computeIfAbsent(p.getUUID(),id->new Navigation());
        if(n.warp>0) return false;
        n.planned=index;sync(p,n);return true;
    }

}
