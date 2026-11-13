package com.dantemorrigan.gauntlet.power;

import com.dantemorrigan.gauntlet.CoopRules;
import com.dantemorrigan.gauntlet.*;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.space.PlanetDestruction;
import com.dantemorrigan.gauntlet.space.SpaceManager;
import com.dantemorrigan.gauntlet.uru.GauntletDamage;
import com.dantemorrigan.gauntlet.uru.UruArmor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
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
        if (active(p) || TimeFlow.active(p)) return;
        ItemStack g = GauntletState.activeGauntlet(p);
        if (!ready(p, g) || p.isUsingItem()) {
            com.dantemorrigan.gauntlet.net.ModStatus.send(p, Component.translatable("msg.infinity_gauntlet.need.beam"));
            return;
        }
        long now = p.level().getServer().overworld().getGameTime();
        long until = COOLDOWNS.getOrDefault(p.getUUID(), 0L);
        if (!p.isCreative() && now < until) {
            com.dantemorrigan.gauntlet.net.ModStatus.send(p, Component.translatable("msg.infinity_gauntlet.beam.cooldown", String.valueOf((until - now + 19) / 20)));
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
        int budget = Math.max(512, Math.min(4096, GauntletConfig.get().beamBlocksPerTick));
        double radius = Math.max(1, Math.min(8, GauntletConfig.get().beamDrillRadius));
        Set<Long> visited = new HashSet<>();
        List<BlockPos> scorchCandidates = new ArrayList<>();
        var rnd = c.level.getRandom();
        for (double d = 1.5; d <= distance; d += 1.0) {
            Vec3 center = from.add(dir.scale(d));
            double r = Math.min(radius, 1.8 + d * .45);
            double rSq = r * r;
            double rimSq = (r + 1.0) * (r + 1.0);
            BlockPos origin = BlockPos.containing(center);
            int bound = (int) Math.ceil(r + 1.0);
            for (int x = -bound; x <= bound; x++) for (int y = -bound; y <= bound; y++) for (int z = -bound; z <= bound; z++) {
                BlockPos pos = origin.offset(x, y, z);
                double distSq = pos.getCenter().distanceToSqr(center);
                if (distSq > rimSq || pos.getCenter().distanceToSqr(c.owner.position()) < 6) continue;
                if (distSq > rSq) {
                    if (y <= 0 && rnd.nextFloat() < 0.14f) scorchCandidates.add(pos.immutable());
                    continue;
                }
                if (!visited.add(pos.asLong()) || !CoopRules.canEdit(c.owner, c.level, pos)) continue;
                var block = c.level.getBlockState(pos);
                if (block.isAir() || block.getDestroySpeed(c.level, pos) < 0) continue;
                // No drops, falling-block entities or hundreds of separate explosions per tick.
                if (c.level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL) && --budget <= 0) return;
            }
            if ((int) d % 4 == 0) {
                c.level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 4, r * 0.4, r * 0.4, r * 0.4, 0.04);
            }
        }
        for (BlockPos pos : scorchCandidates) {
            BlockPos above = pos.above();
            if (!c.level.getBlockState(pos).isAir() && c.level.getBlockState(above).isAir()
                    && CoopRules.canEdit(c.owner, c.level, above)
                    && BaseFireBlock.canBePlacedAt(c.level, above, net.minecraft.core.Direction.UP)) {
                c.level.setBlock(above, BaseFireBlock.getState(c.level, above), Block.UPDATE_ALL);
            }
        }
    }
    private static boolean intersects(AABB box, Vec3 from, Vec3 to) {
        return box.contains(from) || box.clip(from, to).isPresent();
    }
    private static void damage(Cast c, Vec3 from, Vec3 to) {
        double radius = Math.max(2.5, Math.min(6.0, Math.max(GauntletConfig.get().beamHitRadius, GauntletConfig.get().beamDrillRadius * 0.75)));
        AABB area = new AABB(from, to).inflate(radius + 16);
        for (LivingEntity target : CombatTargets.inArea(c.owner,area)) {
            if (!CoopRules.canAffect(c.owner, target)) continue;
            boolean hit = CombatTargets.clip(target,from,to,radius).isPresent();
            if (target instanceof EnderDragon dragon) {
                for (var part : dragon.getSubEntities()) hit |= intersects(part.getBoundingBox().inflate(radius), from, to);
            }
            if (!hit) continue;
            boolean dragon = target instanceof EnderDragon;
            boolean thanos = target instanceof com.dantemorrigan.gauntlet.boss.Thanos;
            if (dragon) {
                float cap = target.getMaxHealth() * .70f;
                float spent = c.damage.getOrDefault(target.getUUID(), 0f);
                float amount = Math.min(cap - spent, cap / c.duration);
                if (amount <= 0) continue;
                // Dragon body parts and sitting phases otherwise reduce/cap conventional damage.
                // One parent UUID budget makes a full connected cast remove exactly 70% max HP.
                float before = target.getHealth();
                target.setHealth(Math.max(0, before - amount));
                c.damage.put(target.getUUID(), spent + Math.min(amount, before));
            } else if (thanos) {
                float cap = target.getMaxHealth() * Math.max(0, GauntletConfig.get().thanosBeamShare);
                float spent = c.damage.getOrDefault(target.getUUID(), 0f);
                float amount = Math.min(cap - spent, cap / c.duration);
                if (amount <= 0) continue;
                // Limit the beam budget before phase-specific mitigation. Each slice bypasses hurt-immunity frames.
                if ((c.age - c.charge) % 4 == 0) {
                    float pulse = Math.min(cap - spent, amount * 4);
                    if (((com.dantemorrigan.gauntlet.boss.Thanos) target).hurtByBeam(c.level, c.owner, pulse)) c.damage.put(target.getUUID(), spent + pulse);
                }
            } else if (target instanceof com.dantemorrigan.gauntlet.doom.DoctorDoom || target instanceof com.dantemorrigan.gauntlet.doom.Doombot) {
                // Doom and his robots take the beam as heavy blows through their own guards and caps, never as a delete.
                if ((c.age - c.charge) % 4 == 0) {
                    target.invulnerableTime = 0;
                    GauntletDamage.hurt(c.level, target, c.owner, c.owner.damageSources().playerAttack(c.owner), 40, CombatTargets.impact(target, from, to, radius));
                }
            } else if (target instanceof com.dantemorrigan.gauntlet.boss.LivingTribunal) {
                if ((c.age - c.charge) % 8 == 0)
                    GauntletDamage.hurt(c.level, target, c.owner, c.owner.damageSources().playerAttack(c.owner), 48,CombatTargets.impact(target,from,to,radius));
            } else if (!(target instanceof Player) && !UruArmor.fullSet(target)) {
                // Every non-boss mob caught in the all-stones beam is incinerated and killed on the very first hit.
                target.invulnerableTime = 0;
                target.igniteForSeconds(10);
                Vec3 body = target.position().add(0, target.getBbHeight() * 0.5, 0);
                c.level.sendParticles(ParticleTypes.FLAME, body.x, body.y, body.z, 18, 0.35, 0.45, 0.35, 0.08);
                c.level.sendParticles(ParticleTypes.LAVA, body.x, body.y, body.z, 6, 0.25, 0.3, 0.25, 0.02);
                var src = c.owner.damageSources().playerAttack(c.owner);
                GauntletDamage.hurt(c.level, target, c.owner, src, Float.MAX_VALUE);
                if (target.isAlive() && !(target instanceof com.dantemorrigan.gauntlet.stones.MindZombie)) {
                    target.setHealth(0f);
                    target.die(src);
                    if (target.isAlive()) target.kill(c.level);
                }
            } else if (!UruArmor.fullSet(target) || (c.age - c.charge) % 4 == 0) {
                target.invulnerableTime = 0;
                target.igniteForSeconds(10);
                GauntletDamage.hurt(c.level, target, c.owner, c.owner.damageSources().playerAttack(c.owner), Float.MAX_VALUE);
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
    public static int cooldown(ServerPlayer p) { return (int)Math.max(0, COOLDOWNS.getOrDefault(p.getUUID(),0L)-p.level().getServer().overworld().getGameTime()); }

    public static int chargeAge(ServerPlayer p) { Cast c=ACTIVE.get(p.getUUID());return c==null?0:c.age; }

}
