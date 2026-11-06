package com.dantemorrigan.gauntlet.power;

import com.dantemorrigan.gauntlet.CoopRules;
import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.snap.InfinitySnap;
import com.dantemorrigan.gauntlet.space.PlanetDestruction;
import com.dantemorrigan.gauntlet.space.SpaceManager;
import com.dantemorrigan.gauntlet.uru.GauntletDamage;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Fixed-duration, server-authoritative attack started by the ability wheel. */
public final class TimeFlow {
    private static final int DURATION = 100;
    private static final int COOLDOWN = 200;
    private static final int PULSE_INTERVAL = 20;
    private static final float PULSE_DAMAGE = 3;
    private static final int RANGE = 32;
    private static final Map<UUID, Cast> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();

    private static final class Cast {
        final ServerPlayer owner;
        final ServerLevel level;
        final ItemStack gauntlet;
        int age;

        Cast(ServerPlayer owner, ItemStack gauntlet) {
            this.owner = owner;
            this.level = owner.level();
            this.gauntlet = gauntlet;
        }
    }

    public static boolean active(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static int cooldown(ServerPlayer player) {
        return (int) Math.max(0, COOLDOWNS.getOrDefault(player.getUUID(), 0L)
                - player.level().getServer().overworld().getGameTime());
    }

    public static void start(ServerPlayer player, ItemStack gauntlet) {
        ACTIVE.put(player.getUUID(), new Cast(player, gauntlet));
        GauntletState.setInUse(gauntlet, EnumSet.of(Stone.TIME));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, .65f, 1.5f);
    }

    public static void cancel(ServerPlayer player) {
        Cast cast = ACTIVE.remove(player.getUUID());
        if (cast != null) finish(cast);
    }

    public static void clear() {
        ACTIVE.clear();
        COOLDOWNS.clear();
    }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        COOLDOWNS.values().removeIf(until -> until <= now);
        var casts = ACTIVE.values().iterator();
        while (casts.hasNext()) {
            Cast cast = casts.next();
            ServerPlayer player = cast.owner;
            if (server.getPlayerList().getPlayer(player.getUUID()) != player || player.level() != cast.level
                    || !player.isAlive() || player.isSpectator() || player.containerMenu != player.inventoryMenu
                    || GauntletState.activeGauntlet(player) != cast.gauntlet
                    || !GauntletState.inserted(cast.gauntlet).contains(Stone.TIME)
                    || PlanetDestruction.isBusy(player) || TimeAbility.isRewinding(player) || SpaceManager.inSpace(player)
                    || InfinitySnap.inVoid(player) || InfinitySnap.casting(player) || player.hasAttached(InfinitySnap.RECORD)) {
                casts.remove();
                finish(cast);
                continue;
            }

            Vec3 from = player.getEyePosition();
            Vec3 direction = player.getLookAngle();
            Vec3 limit = from;
            for (double distance = .5; distance <= RANGE; distance += .5) {
                Vec3 point = from.add(direction.scale(distance));
                if (!cast.level.hasChunkAt(BlockPos.containing(point))) break;
                limit = point;
            }
            Vec3 end = cast.level.clip(new ClipContext(from, limit, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
            if (cast.age % PULSE_INTERVAL == 0 && from.distanceToSqr(end) > 0.0001) damage(cast, from, end);
            if (cast.age % 2 == 0) send(cast, from, end, cast.age);
            if (++cast.age >= DURATION) {
                casts.remove();
                finish(cast);
            }
        }
    }

    private static void damage(Cast cast, Vec3 from, Vec3 end) {
        ServerPlayer owner = cast.owner;
        for (LivingEntity target : cast.level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end),
                e -> e != owner && e.isAlive() && !e.isSpectator() && CoopRules.canAffect(owner, e))) {
            AABB box = target.getBoundingBox();
            if (box.contains(from) || box.clip(from, end).isPresent()) {
                GauntletDamage.hurt(cast.level, target, owner, owner.damageSources().playerAttack(owner), PULSE_DAMAGE);
            }
        }
    }

    private static void finish(Cast cast) {
        GauntletState.setInUse(cast.gauntlet, EnumSet.noneOf(Stone.class));
        COOLDOWNS.put(cast.owner.getUUID(), cast.level.getServer().overworld().getGameTime() + COOLDOWN);
        send(cast, Vec3.ZERO, Vec3.ZERO, -1);
    }

    private static void send(Cast cast, Vec3 from, Vec3 end, int age) {
        var packet = new ModNetwork.TimeFlowFx(new ModNetwork.BeamFx(cast.owner.getId(), from, end, age, 0, DURATION));
        for (ServerPlayer viewer : PlayerLookup.around(cast.level, cast.owner.position(), 96)) {
            ServerPlayNetworking.send(viewer, packet);
        }
    }

    private TimeFlow() {}
}
