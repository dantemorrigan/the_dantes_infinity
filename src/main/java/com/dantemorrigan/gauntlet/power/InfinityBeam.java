package com.dantemorrigan.gauntlet.power;

import com.dantemorrigan.gauntlet.*;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.space.PlanetDestruction;
import com.dantemorrigan.gauntlet.space.SpaceManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** A single server-owned cast; client ribbons never cause damage or change blocks. */
public final class InfinityBeam {
    private static final Map<UUID, Cast> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();
    private static final class Cast {
        final ServerPlayer owner;
        final ServerLevel level;
        final ItemStack gauntlet;
        final int charge, duration;
        final Map<UUID, Float> damage = new HashMap<>();
        int age;
        Cast(ServerPlayer p, ItemStack g) {
            owner = p; level = p.level(); gauntlet = g;
            charge = Math.max(1, Math.min(200, GauntletConfig.get().beamChargeTicks));
            duration = Math.max(1, Math.min(200, GauntletConfig.get().beamFireTicks));
        }
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(InfinityBeam::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> { ACTIVE.clear(); COOLDOWNS.clear(); });
    }
    public static boolean active(ServerPlayer p) { return ACTIVE.containsKey(p.getUUID()); }
    private static boolean ready(ServerPlayer p, ItemStack g) {
        return p.isAlive() && !p.isSpectator() && !g.isEmpty()
                && GauntletState.activeGauntlet(p) == g
                && GauntletState.inserted(g).size() == Stone.values().length
                && GauntletEffects.state(p).phase != GauntletEffects.Phase.RITUAL
                && !PlanetDestruction.isBusy(p) && !TimeAbility.isRewinding(p) && !SpaceManager.inSpace(p);
    }
    public static void request(ServerPlayer p, boolean start) {
        if (!start) { cancel(p); return; }
        if (active(p)) return;
        ItemStack g = GauntletState.activeGauntlet(p);
        if (!ready(p, g) || p.isUsingItem()) {
            p.sendOverlayMessage(Component.literal("Нужна пробуждённая перчатка со всеми 6 камнями в левой руке"));
            return;
        }
        long now = p.level().getServer().overworld().getGameTime();
        long until = COOLDOWNS.getOrDefault(p.getUUID(), 0L);
        if (!p.isCreative() && now < until) {
            p.sendOverlayMessage(Component.literal("Луч: перезарядка " + ((until - now + 19) / 20) + " с"));
            return;
        }
        ACTIVE.put(p.getUUID(), new Cast(p, g));
        GauntletState.setInUse(g, EnumSet.allOf(Stone.class));
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.4f, .6f);
    }
    public static void cancel(ServerPlayer p) {
        Cast cast = ACTIVE.remove(p.getUUID());
        if (cast != null) finish(cast);
    }
    private static void finish(Cast c) {
        GauntletState.setInUse(c.gauntlet, EnumSet.noneOf(Stone.class));
        sync(c, c.owner.getEyePosition(), c.owner.getEyePosition(), -1);
        // Releasing early cannot bypass cooldown or repeatedly get a free first damage pulse.
        long now = c.level.getServer().overworld().getGameTime();
        COOLDOWNS.merge(c.owner.getUUID(), now + (c.age >= c.charge ? Math.max(20, GauntletConfig.get().beamCooldownTicks) : 10), Math::max);
    }
    private static void tick(MinecraftServer server) {
        for (Cast c : new ArrayList<>(ACTIVE.values())) {
            ServerPlayer p = c.owner;
            if (server.getPlayerList().getPlayer(p.getUUID()) != p || p.level() != c.level || !ready(p, c.gauntlet)) {
                ACTIVE.remove(p.getUUID()); finish(c); continue;
            }
            GauntletState.setInUse(c.gauntlet, EnumSet.allOf(Stone.class));
            Vec3 from = p.getEyePosition().add(0, -.15, 0);
            Vec3 direction = p.getLookAngle().normalize();
            Vec3 end = from;
            if (c.age >= c.charge) {
                double range = Math.max(8, Math.min(128, GauntletConfig.get().beamRange));
                // Never force-load chunks or tunnel through unbreakable blocks.
                double distance = 0;
                for (double d = .5; d <= range; d += .5) {
                    BlockPos pos = BlockPos.containing(from.add(direction.scale(d)));
                    if (!c.level.hasChunkAt(pos) || (!c.level.getBlockState(pos).isAir()
                            && c.level.getBlockState(pos).getDestroySpeed(c.level, pos) < 0)) break;
                    distance = d;
                }
                end = from.add(direction.scale(distance));
                if (GauntletConfig.get().beamBreakBlocks) drill(c, from, direction, distance);
                // Surfaces remaining after this tick's excavation still occlude the beam and damage.
                end = c.level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getLocation();
                damage(c, from, end);
                if ((c.age - c.charge) % 10 == 0)
                    c.level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2f, .7f);
            }
            if (c.age % 2 == 0) sync(c, from, end, c.age);
            if (++c.age >= c.charge + c.duration) { ACTIVE.remove(p.getUUID()); finish(c); }
        }
    }
    private static void drill(Cast c, Vec3 from, Vec3 dir, double distance) {
        int budget = Math.max(1, Math.min(2048, GauntletConfig.get().beamBlocksPerTick));
        double radius = Math.max(1, Math.min(8, GauntletConfig.get().beamDrillRadius));
        Set<Long> visited = new HashSet<>();
        for (double d = 3; d <= distance; d += 2.5) {
            Vec3 center = from.add(dir.scale(d));
            double r = Math.min(radius, 1.2 + d * .35);
            BlockPos origin = BlockPos.containing(center);
            int bound = (int) Math.ceil(r);
            for (int x = -bound; x <= bound; x++) for (int y = -bound; y <= bound; y++) for (int z = -bound; z <= bound; z++) {
                BlockPos pos = origin.offset(x, y, z);
                if (!visited.add(pos.asLong()) || pos.getCenter().distanceToSqr(center) > r * r
                        || pos.getCenter().distanceToSqr(c.owner.position()) < 9 || !c.level.hasChunkAt(pos)) continue;
                var block = c.level.getBlockState(pos);
                if (block.isAir() || block.getDestroySpeed(c.level, pos) < 0) continue;
                // No drops, falling-block entities or hundreds of separate explosions per tick.
                if (c.level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL) && --budget <= 0) return;
            }
        }
    }
    private static boolean intersects(AABB box, Vec3 from, Vec3 to) {
        return box.contains(from) || box.clip(from, to).isPresent();
    }
    private static void damage(Cast c, Vec3 from, Vec3 to) {
        double radius = Math.max(.2, Math.min(4, GauntletConfig.get().beamHitRadius));
        AABB area = new AABB(from, to).inflate(radius + 16);
        for (LivingEntity target : c.level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != c.owner && e.isAlive() && !e.isSpectator())) {
            boolean hit = intersects(target.getBoundingBox().inflate(radius), from, to);
            if (target instanceof EnderDragon dragon) {
                for (var part : dragon.getSubEntities()) hit |= intersects(part.getBoundingBox().inflate(radius), from, to);
            }
            if (!hit) continue;
            boolean dragon = target instanceof EnderDragon;
            float cap = dragon ? target.getMaxHealth() * .70f : Math.max(0, GauntletConfig.get().beamDamage);
            float spent = c.damage.getOrDefault(target.getUUID(), 0f);
            float amount = Math.min(cap - spent, cap / c.duration);
            if (amount <= 0) continue;
            if (dragon) {
                // Dragon body parts and sitting phases otherwise reduce/cap conventional damage.
                // One parent UUID budget makes a full connected cast remove exactly 70% max HP.
                float before = target.getHealth();
                target.setHealth(Math.max(0, before - amount));
                c.damage.put(target.getUUID(), spent + Math.min(amount, before));
            } else if ((c.age - c.charge) % 4 == 0) {
                float pulse = Math.min(cap - spent, amount * 4);
                if (target.hurtServer(c.level, c.owner.damageSources().playerAttack(c.owner), pulse)) {
                    c.damage.put(target.getUUID(), spent + pulse);
                }
            }
        }
    }
    private static void sync(Cast c, Vec3 from, Vec3 to, int age) {
        ModNetwork.BeamFx packet = new ModNetwork.BeamFx(c.owner.getId(), from, to, age, c.charge, c.duration);
        for (ServerPlayer viewer : c.level.players()) {
            if (viewer == c.owner || viewer.distanceToSqr(c.owner) < 192 * 192) ServerPlayNetworking.send(viewer, packet);
        }
    }
    private InfinityBeam() {}
}
