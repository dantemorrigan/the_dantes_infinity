package com.dantemorrigan.gauntlet.stormbreaker;

import com.dantemorrigan.gauntlet.CoopRules;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModComponents;
import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.cosmos.CosmosTravel;
import com.dantemorrigan.gauntlet.net.ModStatus;
import com.dantemorrigan.gauntlet.power.PowerAbility;
import com.dantemorrigan.gauntlet.snap.InfinitySnap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The overworld record is authoritative, including while the owner or the death chunk is offline. */
public final class Stormbreaker {
    public static final ResourceKey<DamageType> DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, InfinityGauntlet.id("stormbreaker"));
    public static final Identifier CHARGED = InfinityGauntlet.id("stormbreaker_charged");
    private static final Identifier BASE = InfinityGauntlet.id("stormbreaker");
    private static final Identifier STRENGTH = InfinityGauntlet.id("stormbreaker_strength");
    private static final int HELD = 0, IN_FLIGHT = 1, IN_GROUND = 2, FALLING = 3;
    private static final int SKY_FALL_TICKS = 28, PULL_TICKS = 12;
    private static final int[] STRIKES = {36, 50, 65, 83};
    private static final StormFx[] COMBO = {StormFx.COMBO_1, StormFx.COMBO_2, StormFx.COMBO_3};
    private static final int[] SWING_TICKS = {9, 10, 16};
    private static final int[] SWING_CONTACT = {6, 7, 10};

    private record Relic(String owner, String seal, int phase, ResourceKey<Level> dimension, Vec3 position) {
        static final Codec<Relic> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("owner").forGetter(Relic::owner),
                Codec.STRING.fieldOf("seal").forGetter(Relic::seal),
                Codec.INT.fieldOf("phase").forGetter(Relic::phase),
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Relic::dimension),
                Vec3.CODEC.fieldOf("position").forGetter(Relic::position)).apply(i, Relic::new));

        boolean belongsTo(ServerPlayer p) { return owner.equals(p.getStringUUID()); }

        static Relic at(ServerPlayer p, int phase) {
            return new Relic(p.getStringUUID(), UUID.randomUUID().toString(), phase, p.level().dimension(), p.position());
        }
    }

    private static final AttachmentType<Relic> RELIC = AttachmentRegistry.<Relic>builder()
            .persistent(Relic.CODEC).buildAndRegister(BASE);
    private static final AttachmentType<Boolean> FLIGHT_GRANT = AttachmentRegistry.<Boolean>builder()
            .persistent(Codec.BOOL).buildAndRegister(InfinityGauntlet.id("stormbreaker_flight"));
    private static final Map<MinecraftServer, Session> SESSIONS = new HashMap<>();

    private static final class Session {
        StormbreakerEntity display;
        Flight flight;
        Wave wave;
        Cast cast;
        Release release;
        Swing swing;
        Pull pull;
        int fallStart;
        int combo, lastSwing = -100, nextSwing, lightningReady, waveReady, throwReady, wrathReady, plantUntil;
        boolean flying, wasFlying;
    }

    private static final class Flight {
        final Vec3 direction;
        final Set<UUID> hit = new HashSet<>();
        int age;
        Flight(Vec3 direction) { this.direction = direction; }
    }

    private record Swing(ServerLevel level, int start, int combo, Vec3 direction, UUID target) {}

    private record Pull(UUID player, int finish) {}

    private record Release(ServerLevel level, int tick, Vec3 direction) {}

    private static final class Wave {
        final ServerLevel level;
        final Vec3 start, direction;
        final int tick;
        final Set<UUID> hit = new HashSet<>();
        Wave(ServerLevel level, Vec3 start, Vec3 direction, int tick) {
            this.level = level;
            this.start = start;
            this.direction = direction;
            this.tick = tick;
        }
    }

    private record Cast(ServerLevel level, int tick, Vec3 point, UUID target, boolean wrath) {}

    public static void init() {
        StormFx.init();
        StormNetwork.init();
        ServerTickEvents.END_SERVER_TICK.register(Stormbreaker::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(SESSIONS::remove);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> stopFlight(handler.getPlayer(), SESSIONS.get(server)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> stopFlight(handler.getPlayer(), SESSIONS.get(server)));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer p) onDeath(p);
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ItemEntity item && sealed(item.getItem())) item.discard();
        });
        AttackEntityCallback.EVENT.register((player, level, hand, target, result) -> {
            if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().is(ModItems.STORMBREAKER)
                    || !(player instanceof ServerPlayer p) || p.isSpectator()) return InteractionResult.PASS;
            if (target instanceof LivingEntity living && p.getEyePosition().distanceToSqr(living.getBoundingBox().getCenter()) < 36) {
                swing(p, living);
            }
            return InteractionResult.FAIL;
        });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, side) -> {
            if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().is(ModItems.STORMBREAKER)
                    || !(player instanceof ServerPlayer p) || p.isSpectator()) return InteractionResult.PASS;
            HitResult picked = PowerAbility.pick(p, 5);
            if (side == Direction.UP && picked instanceof BlockHitResult block
                    && block.getType() == HitResult.Type.BLOCK && pos.equals(block.getBlockPos())) {
                slam(p, block.getLocation());
            } else {
                swing(p, null);
            }
            return InteractionResult.FAIL;
        });
    }

    private static MinecraftServer server(ServerPlayer p) { return p.level().getServer(); }
    private static Session session(ServerPlayer p) { return SESSIONS.computeIfAbsent(server(p), key -> new Session()); }
    private static Relic record(ServerPlayer p) { return server(p).overworld().getAttached(RELIC); }

    private static boolean sealed(ItemStack stack) {
        return stack.is(ModItems.STORMBREAKER) && stack.has(ModComponents.STORMBREAKER_SEAL);
    }

    private static boolean matches(ItemStack stack, Relic relic) {
        return sealed(stack) && relic.seal().equals(stack.get(ModComponents.STORMBREAKER_SEAL));
    }

    public static boolean guarded(ServerPlayer p) {
        Relic relic = record(p);
        return relic != null && relic.phase() == HELD && relic.belongsTo(p)
                && (matches(p.getMainHandItem(), relic) || matches(p.getOffhandItem(), relic));
    }

    public static void guardFlash(ServerPlayer p, Entity attacker) {
        Vec3 direction = attacker == null ? p.getLookAngle()
                : attacker.position().subtract(p.position()).multiply(1, 0, 1).normalize();
        if (direction.lengthSqr() < .001) direction = new Vec3(0, 0, 1);
        StormFx.GAUNTLET_GUARD.play(p.level(), p.position().add(0, 1.1, 0).add(direction.scale(.55)), 1.85, direction);
    }

    private static boolean mainHand(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && !InfinitySnap.inVoid(p)
                && p.getMainHandItem().is(ModItems.STORMBREAKER);
    }

    public static boolean exists(MinecraftServer server) {
        return server.overworld().hasAttached(RELIC);
    }

    public static ItemStack craftingPreview(ServerLevel level, ItemStack stack) {
        if (!stack.is(ModItems.STORMBREAKER)) return stack;
        if (exists(level.getServer())) return ItemStack.EMPTY;
        // The result slot carries the same seal into the cursor or inventory on every kind of click.
        stack.set(ModComponents.STORMBREAKER_SEAL, UUID.randomUUID().toString());
        return stack;
    }

    public static void finishCraft(ServerPlayer p, ItemStack stack) {
        if (!sealed(stack) || record(p) != null) return;
        var relic = new Relic(p.getStringUUID(), stack.get(ModComponents.STORMBREAKER_SEAL), HELD,
                p.level().dimension(), p.position());
        storeRelic(server(p), relic);
        status(p, "bound");
    }

    private static void storeRelic(MinecraftServer server, Relic relic) {
        boolean first = !exists(server);
        server.overworld().setAttached(RELIC, relic);
        if (!first) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.containerMenu instanceof AbstractCraftingMenu menu && menu.getResultSlot().getItem().is(ModItems.STORMBREAKER)
                    && !matches(menu.getResultSlot().getItem(), relic)) {
                menu.getResultSlot().set(ItemStack.EMPTY);
                menu.broadcastChanges();
            }
        }
    }

    private static boolean ready(ServerPlayer p) {
        if (!mainHand(p)) return false;
        ItemStack stack = p.getMainHandItem();
        Relic relic = record(p);
        if (relic == null) {
            relic = Relic.at(p, HELD);
            stack.set(ModComponents.STORMBREAKER_SEAL, relic.seal());
            storeRelic(server(p), relic);
            status(p, "bound");
            return true;
        }
        if (relic.phase() == HELD && relic.belongsTo(p) && matches(stack, relic)) return true;
        status(p, "already_bound");
        return false;
    }

    static void action(ServerPlayer p, int action) {
        if (!mainHand(p)) return;
        switch (action) {
            case StormNetwork.THROW -> startThrow(p);
            case StormNetwork.WRATH -> beginCast(p, true);
            case StormNetwork.FLIGHT -> toggleFlight(p);
            case StormNetwork.AIR_SWING -> {
                if (PowerAbility.pick(p, 4.5).getType() == HitResult.Type.MISS) swing(p, null);
            }
            default -> { }
        }
    }

    private static void swing(ServerPlayer p, LivingEntity direct) {
        if (!ready(p)) return;
        Session s = session(p);
        int tick = server(p).getTickCount();
        if (tick < s.nextSwing || s.release != null) return;
        int index = tick - s.lastSwing > 24 ? 0 : s.combo;
        s.lastSwing = tick;
        s.nextSwing = tick + SWING_TICKS[index];
        s.combo = (index + 1) % COMBO.length;
        s.swing = new Swing(p.level(), tick, index, p.getLookAngle(), direct == null ? null : direct.getUUID());
        StormNetwork.visual(p, "combo_" + (index + 1), 0, p.position());
    }

    private static void tickSwing(ServerPlayer p, Session s, int now) {
        Swing swing = s.swing;
        if (swing.level != p.level() || !mainHand(p) || !guarded(p)) { s.swing = null; return; }
        int index = swing.combo;
        int age = now - swing.start;
        Vec3 forward = swing.direction;
        Vec3 arc = p.getEyePosition().add(forward.scale(1.4)).add(0, -.38, 0);
        if (age == (index == 2 ? 7 : 4)) {
            COMBO[index].play(p.level(), arc, 2.4 + .2 * index);
            sound(p.level(), p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.1f, 1.35f - index * .21f);
        }
        if (age < SWING_CONTACT[index]) return;
        s.swing = null;
        Entity direct = swing.target == null ? null : p.level().getEntity(swing.target);
        if (direct instanceof LivingEntity living && living.isAlive() && p.hasLineOfSight(living)
                && p.distanceToSqr(living) < 30.25 && strike(p, living, 10 + index * 4, forward, .28 + index * .16)) {
            StormFx.IMPACT.play(p.level(), direct.getBoundingBox().getCenter(), .85 + index * .3);
        }
        if (index != 1) return;
        for (LivingEntity target : targets(p.level(), arc, 2.1, 1.4)) {
            Vec3 delta = target.position().subtract(p.position());
            if (target != direct && delta.lengthSqr() < 12 && delta.normalize().dot(forward) > .15 && p.hasLineOfSight(target)) {
                strike(p, target, 8, forward, .3);
            }
        }
    }

    private static void slam(ServerPlayer p, Vec3 ground) {
        if (!ready(p)) return;
        Session s = session(p);
        int now = server(p).getTickCount();
        if (now < s.waveReady || now < s.nextSwing || s.release != null) return;
        s.waveReady = now + 65;
        s.nextSwing = Math.max(s.nextSwing, now + 20);
        Vec3 direction = Vec3.directionFromRotation(0, p.getYRot()).normalize();
        s.wave = new Wave(p.level(), ground, direction, now);
        StormNetwork.visual(p, "ground_slam", 0, ground);
    }

    private static void tickWave(ServerPlayer p, Session s, int now) {
        Wave w = s.wave;
        int age = now - w.tick;
        if (w.level != p.level() || age > 24) { s.wave = null; return; }
        if (age == 10) {
            sound(w.level, w.start, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.3f, .7f);
            StormFx.IMPACT.play(w.level, w.start.add(0, .15, 0), 2.2);
            StormFx.BLOCK_SHARDS.play(w.level, w.start.add(0, .22, 0), 1.7);
        }
        if (age < 11 || age > 21 || (age & 1) == 0) return;
        Vec3 center = w.start.add(w.direction.scale(1.5 + (age - 11) * .8));
        Vec3 lateral = new Vec3(-w.direction.z, 0, w.direction.x);
        for (int lane = -1; lane <= 1; lane++) {
            Vec3 sample = center.add(lateral.scale(lane * 2));
            Vec3 surface = surface(w.level, sample, 3, 5);
            if (surface == null) continue;
            StormFx.GROUND_WALL.play(w.level, surface, 2.6, w.direction);
            for (LivingEntity target : targets(w.level, surface.add(0, .75, 0), 1.5, 1.45)) {
                if (!w.hit.contains(target.getUUID()) && strike(p, target, 15, w.direction, .85)) w.hit.add(target.getUUID());
            }
        }
    }

    static void lightning(ServerPlayer p) { beginCast(p, false); }

    private static void beginCast(ServerPlayer p, boolean wrath) {
        if (!ready(p)) return;
        Session s = session(p);
        int now = server(p).getTickCount();
        if (s.cast != null || s.release != null || now < (wrath ? s.wrathReady : s.lightningReady)) return;
        HitResult picked = PowerAbility.pick(p, wrath ? 52 : 36);
        Entity target = picked instanceof EntityHitResult hit && CoopRules.canAffect(p, hit.getEntity()) ? hit.getEntity() : null;
        Vec3 point = target == null ? picked.getLocation() : target.position();
        if (wrath) s.wrathReady = now + 440;
        else s.lightningReady = now + 42;
        s.cast = new Cast(p.level(), now, point, target == null ? null : target.getUUID(), wrath);
        StormNetwork.visual(p, wrath ? "heavens_wrath" : "lightning_cast", 0, point);
        sound(p.level(), p.position(), SoundEvents.TRIDENT_RIPTIDE_3.value(), 1, wrath ? .63f : 1.1f);
    }

    private static void tickCast(ServerPlayer p, Session s, int now) {
        Cast cast = s.cast;
        if (cast.level != p.level()) {
            StormNetwork.visual(p, "stop", 0, p.position());
            s.cast = null;
            return;
        }
        int age = now - cast.tick;
        if (age > (cast.wrath ? 112 : 18)) { s.cast = null; return; }
        Entity target = cast.target == null ? null : cast.level.getEntity(cast.target);
        Vec3 point = target != null && target.isAlive() && CoopRules.canAffect(p, target)
                && target.position().distanceToSqr(cast.point) <= 48 * 48 ? target.position() : cast.point;
        Vec3 ground = surface(cast.level, point, 2, 7);
        if (ground == null) ground = point;
        if (cast.wrath ? age >= 12 && age <= 82 && (age - 12) % 10 == 0 : age == 7) {
            StormFx.WRATH_TARGET.play(cast.level, ground.add(0, .05, 0), cast.wrath ? 3.8 : 1.8);
        }
        if (cast.wrath && age % 20 == 0) StormNetwork.visual(p, "heavens_wrath", age, cast.point);
        if (!cast.wrath && age == 10) bolt(p, ground, point, -1);
        if (!cast.wrath) return;
        for (int i = 0; i < STRIKES.length; i++) {
            if (age == STRIKES[i]) bolt(p, ground, point, i);
        }
    }

    private static void bolt(ServerPlayer p, Vec3 ground, Vec3 target, int stage) {
        boolean wrath = stage >= 0;
        double radius = wrath ? 2.3 + stage * .62 : 1.9;
        (wrath ? StormFx.WRATH_BOLT : StormFx.LIGHTNING_HIT).play(p.level(), ground, wrath ? 10 + stage * 3.4 : 6.0);
        StormFx.IMPACT.play(p.level(), ground.add(0, .25, 0), radius);
        sound(p.level(), ground, SoundEvents.LIGHTNING_BOLT_THUNDER, wrath ? 2.1f : 1.25f, wrath ? .91f - stage * .1f : 1.22f);
        sound(p.level(), ground, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.2f, .92f);
        for (LivingEntity living : targets(p.level(), target, radius, 2.6)) {
            Vec3 push = living.position().subtract(target);
            strike(p, living, wrath ? 11 + stage * 4 : 16, push, wrath ? .36 : .52);
        }
    }

    private static void startThrow(ServerPlayer p) {
        if (!ready(p)) return;
        Session s = session(p);
        int now = server(p).getTickCount();
        if (s.release != null || now < s.throwReady) return;
        s.release = new Release(p.level(), now + 8, p.getLookAngle());
        s.nextSwing = Math.max(s.nextSwing, now + 12);
        StormNetwork.visual(p, "throw_release", 0, p.position());
    }

    private static void launch(ServerPlayer p, Session s, Release release) {
        if (release.level != p.level() || !mainHand(p) || !guarded(p)
                || !matches(p.getMainHandItem(), record(p))) return;
        Vec3 origin = p.getEyePosition().add(release.direction.scale(.75)).add(0, -.24, 0);
        if (!p.level().hasChunkAt(BlockPos.containing(origin))) return;
        StormbreakerEntity model = spawn(p.level(), origin, StormbreakerEntity.OUTBOUND);
        if (model == null) return;
        s.display = model;
        s.flight = new Flight(release.direction);
        s.throwReady = server(p).getTickCount() + 42;
        server(p).overworld().setAttached(RELIC, Relic.at(p, IN_FLIGHT));
        p.getMainHandItem().setCount(0);
        sound(p.level(), origin, SoundEvents.TRIDENT_THROW.value(), 1.2f, .72f);
    }

    private static void tickThrow(ServerPlayer p, Session s) {
        StormbreakerEntity axe = s.display;
        Flight flight = s.flight;
        if (axe == null || flight == null || axe.isRemoved() || axe.level() != p.level()
                || !p.level().hasChunkAt(axe.blockPosition()) || ++flight.age > 64) {
            returnToOwner(p, s, false);
            return;
        }
        Vec3 pos = axe.position();
        Vec3 hand = p.getEyePosition().add(0, -.4, 0);
        if (flight.age >= 16 && axe.phase() == StormbreakerEntity.OUTBOUND) {
            turn(axe, flight);
        }
        boolean returning = axe.phase() == StormbreakerEntity.RETURNING;
        Vec3 delta = returning ? hand.subtract(pos) : flight.direction;
        if (returning && delta.lengthSqr() < 2.9) { returnToOwner(p, s, true); return; }
        Vec3 direction = delta.normalize();
        Vec3 next = pos.add(direction.scale(returning ? 2.0 : 1.65));
        if (!p.level().hasChunkAt(BlockPos.containing(next))) {
            if (returning) returnToOwner(p, s, false);
            else turn(axe, flight);
            return;
        }
        if (!clearPath(p, pos, next)) {
            if (returning) returnToOwner(p, s, false);
            else turn(axe, flight);
            return;
        }
        axe.setDeltaMovement(next.subtract(pos));
        axe.setPos(next.x, next.y, next.z);
        if (flight.age % 3 == 1) {
            (returning ? StormFx.THROW_RETURN : StormFx.THROW_OUT).play(p.level(), next.subtract(direction.scale(1.05)), 2.65, direction);
        }
        for (LivingEntity target : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(pos, next).inflate(.7), Entity::isAlive)) {
            if (flight.hit.contains(target.getUUID()) || target.getBoundingBox().inflate(.7).clip(pos, next).isEmpty()
                    && !target.getBoundingBox().inflate(.7).contains(pos)) continue;
            if (strike(p, target, returning ? 13 : 19, direction, .65)) {
                flight.hit.add(target.getUUID());
                StormFx.IMPACT.play(p.level(), target.getBoundingBox().getCenter(), 1.25);
            }
        }
    }

    private static boolean clearPath(ServerPlayer p, Vec3 start, Vec3 end) {
        ServerLevel level = p.level();
        for (int count = 0; count < 6; count++) {
            BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (hit.getType() == HitResult.Type.MISS) return true;
            BlockPos pos = hit.getBlockPos();
            if (!CoopRules.canEdit(p, level, pos) || level.getBlockState(pos).getDestroySpeed(level, pos) < 0
                    || !level.destroyBlock(pos, false, p)) return false;
            StormFx.BLOCK_SHARDS.play(level, hit.getLocation(), 1.55);
            StormFx.IMPACT.play(level, hit.getLocation(), .88);
        }
        return false;
    }

    private static void turn(StormbreakerEntity axe, Flight flight) {
        axe.phase(StormbreakerEntity.RETURNING);
        flight.hit.clear();
        sound((ServerLevel) axe.level(), axe.position(), SoundEvents.TRIDENT_RETURN, 1.2f, .72f);
    }

    private static void returnToOwner(ServerPlayer p, Session s, boolean caught) {
        removeDisplay(s);
        s.flight = null;
        if (!restore(p)) return; // Full inventory: keep the return in the world record and retry.
        if (caught) {
            StormFx.CATCH.play(p.level(), p.getEyePosition().add(p.getLookAngle().scale(.45)).add(0, -.45, 0), 1.2);
            StormNetwork.visual(p, "throw_catch", 0, p.position());
            sound(p.level(), p.position(), SoundEvents.TRIDENT_RETURN, .9f, 1.2f);
        }
    }

    private static boolean restore(ServerPlayer p) {
        Relic fresh = Relic.at(p, HELD);
        ItemStack stack = new ItemStack(ModItems.STORMBREAKER);
        stack.set(ModComponents.STORMBREAKER_SEAL, fresh.seal());
        int slot = p.getInventory().getFreeSlot();
        if (slot < 0) return false;
        p.getInventory().setItem(slot, stack);
        server(p).overworld().setAttached(RELIC, fresh);
        return true;
    }

    private static void onDeath(ServerPlayer p) {
        Relic relic = record(p);
        if (relic == null || !relic.belongsTo(p) || relic.phase() == IN_GROUND) return;
        Session s = session(p);
        stopFlight(p, s);
        s.cast = null;
        s.wave = null;
        s.swing = null;
        s.release = null;
        s.flight = null;
        removeDisplay(s);
        Inventory inventory = p.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (sealed(inventory.getItem(i))) inventory.setItem(i, ItemStack.EMPTY);
        }
        if (sealed(p.containerMenu.getCarried())) p.containerMenu.setCarried(ItemStack.EMPTY);
        Vec3 site = surface(p.level(), p.position(), 1, 12);
        if (site == null) site = p.position();
        server(p).overworld().setAttached(RELIC, new Relic(relic.owner, UUID.randomUUID().toString(),
                IN_GROUND, p.level().dimension(), site));
        if (p.level().hasChunkAt(BlockPos.containing(site))) {
            s.display = spawn(p.level(), site, StormbreakerEntity.PLANTING);
            s.plantUntil = server(p).getTickCount() + 10;
        }
    }

    static void reclaim(ServerPlayer p, StormbreakerEntity entity) {
        Relic relic = record(p);
        Session s = session(p);
        if (relic == null || relic.phase() != IN_GROUND || s.display != entity || !p.isAlive() || p.isSpectator()
                || p.distanceToSqr(entity) > 36) return;
        if (!relic.owner.isEmpty() && !relic.belongsTo(p)) {
            Vec3 spark = entity.position().add(0, .6, 0);
            p.level().sendParticles(p, StormFx.OWNERSHIP_DENIED.particle, false, false,
                    spark.x, spark.y, spark.z, 0, 0, .8, 0, 1);
            status(p, "already_bound");
            return;
        }
        if (s.pull != null || entity.phase() != StormbreakerEntity.EMBEDDED) return;
        if (p.getInventory().getFreeSlot() < 0) { status(p, "inventory_full"); return; }
        s.pull = new Pull(p.getUUID(), server(p).getTickCount() + PULL_TICKS);
        entity.phase(StormbreakerEntity.PULLING);
        p.swing(InteractionHand.MAIN_HAND, true);
        StormNetwork.visual(p, "reclaim_hand", 0, entity.position());
        StormFx.RECLAIM.play(p.level(), entity.position().add(0, .55, 0), 1.4);
        sound(p.level(), entity.position(), SoundEvents.TRIDENT_RETURN, 1.1f, .85f);
    }

    private static void tickPull(MinecraftServer server, Session s, ServerLevel level, int now) {
        if (s.pull == null) return;
        ServerPlayer p = server.getPlayerList().getPlayer(s.pull.player);
        boolean nearby = s.display != null && !s.display.isRemoved() && p != null && p.isAlive()
                && !p.isSpectator() && p.level() == level && p.distanceToSqr(s.display) <= 36;
        if (nearby && now < s.pull.finish) return;
        s.pull = null;
        if (nearby && restore(p)) {
            status(p, "bound");
            removeDisplay(s);
        } else if (s.display != null) {
            s.display.phase(StormbreakerEntity.EMBEDDED);
        }
    }

    private static void rollSkyfall(ServerLevel level, int now) {
        if (now % 20 != 0 || !level.isThundering() || level.getRandom().nextInt(180) != 0) return;
        var players = level.getPlayers(p -> p.isAlive() && !p.isSpectator());
        if (players.isEmpty()) return;
        var random = level.getRandom();
        var watcher = players.get(random.nextInt(players.size()));
        for (int i = 0; i < 10; i++) {
            double angle = random.nextDouble() * Math.TAU;
            double range = 28 + random.nextDouble() * 34;
            BlockPos column = BlockPos.containing(watcher.getX() + Math.cos(angle) * range, watcher.getY(),
                    watcher.getZ() + Math.sin(angle) * range);
            if (!level.hasChunkAt(column) || !level.getWorldBorder().isWithinBounds(column)) continue;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
            if (!level.isRainingAt(top) || !level.getBlockState(top).isAir()
                    || !level.getFluidState(top.below()).isEmpty() || !level.getBlockState(top.below()).isSolidRender()) continue;
            Vec3 landing = Vec3.atBottomCenterOf(top);
            if (!level.hasChunkAt(BlockPos.containing(fallingPosition(landing, 0)))) continue;
            storeRelic(level.getServer(), new Relic("", UUID.randomUUID().toString(), FALLING, level.dimension(), landing));
            return;
        }
    }

    private static Vec3 fallingPosition(Vec3 landing, double progress) {
        double remaining = 1 - progress * progress;
        return landing.add(-8 * remaining, 38 * remaining, 5 * remaining);
    }

    private static void tickSkyfall(ServerLevel level, Relic relic, Session s, int now) {
        Vec3 landing = relic.position;
        if (!level.hasChunkAt(BlockPos.containing(landing))) { removeDisplay(s); return; }
        if (s.display == null || s.display.isRemoved()) {
            // Never load an extra chunk just for the falling prop.
            Vec3 origin = fallingPosition(landing, 0);
            if (!level.hasChunkAt(BlockPos.containing(origin))) return;
            s.display = spawn(level, origin, StormbreakerEntity.SKYFALL);
            if (s.display == null) return;
            s.fallStart = now;
            sound(level, landing, SoundEvents.LIGHTNING_BOLT_THUNDER, 4f, .68f);
            StormFx.WRATH_BOLT.play(level, origin.add(0, -.5, 0), 16);
        }
        double progress = Math.min(1, (now - s.fallStart) / (double) SKY_FALL_TICKS);
        Vec3 at = fallingPosition(landing, progress);
        Vec3 motion = at.subtract(s.display.position());
        s.display.setDeltaMovement(motion);
        s.display.setPos(at.x, at.y, at.z);
        if (now % 2 == 0 && motion.lengthSqr() > .001) {
            StormFx.THROW_OUT.play(level, at.subtract(motion.normalize()), 4.5, motion);
        }
        if (progress < 1) return;
        s.display.setDeltaMovement(Vec3.ZERO);
        s.display.phase(StormbreakerEntity.EMBEDDED);
        storeRelic(level.getServer(), new Relic("", relic.seal, IN_GROUND, relic.dimension, landing));
        StormFx.LIGHTNING_HIT.play(level, landing, 12);
        StormFx.IMPACT.play(level, landing.add(0, .2, 0), 4.5);
        StormFx.BLOCK_SHARDS.play(level, landing.add(0, .3, 0), 3.4);
        sound(level, landing, SoundEvents.LIGHTNING_BOLT_IMPACT, 2.4f, .73f);
        sound(level, landing, SoundEvents.ANVIL_PLACE, 1.4f, .76f);
    }

    private static StormbreakerEntity spawn(ServerLevel level, Vec3 at, int phase) {
        StormbreakerEntity model = new StormbreakerEntity(ModEntities.STORMBREAKER, level);
        model.phase(phase);
        model.setPos(at.x, at.y, at.z);
        return level.addFreshEntity(model) ? model : null;
    }

    private static void removeDisplay(Session s) {
        if (s.display != null) s.display.discard();
        s.display = null;
    }

    private static void toggleFlight(ServerPlayer p) {
        if (!ready(p)) return;
        Session s = session(p);
        if (s.flying) { stopFlight(p, s); return; }
        if (p.hasAttached(CosmosTravel.FLIGHT) || p.isPassenger()) return;
        s.flying = true;
        var abilities = p.getAbilities();
        s.wasFlying = abilities.flying;
        if (!abilities.mayfly) {
            p.setAttached(FLIGHT_GRANT, true);
            abilities.mayfly = true;
        }
        abilities.flying = true;
        p.onUpdateAbilities();
        if (!s.wasFlying) {
            p.setDeltaMovement(p.getDeltaMovement().add(0, .43, 0));
            p.hurtMarked = true;
        }
        StormFx.TAKEOFF.play(p.level(), p.position().add(0, .06, 0), 2.8);
        StormNetwork.visual(p, "takeoff", 0, p.position());
        sound(p.level(), p.position(), SoundEvents.TRIDENT_RIPTIDE_3.value(), 1, .9f);
    }

    public static void releaseFlight(ServerPlayer p) {
        stopFlight(p, SESSIONS.get(server(p)));
    }

    private static void stopFlight(ServerPlayer p, Session s) {
        boolean active = s != null && s.flying;
        if (active) s.flying = false;
        boolean grant = p.hasAttached(FLIGHT_GRANT);
        if (grant) p.removeAttached(FLIGHT_GRANT);
        if (p.hasAttached(CosmosTravel.FLIGHT) || p.isSpectator()) return;
        var abilities = p.getAbilities();
        if (grant && !p.isCreative()) abilities.mayfly = false;
        if (active || grant) {
            abilities.flying = active && s.wasFlying && abilities.mayfly;
            p.onUpdateAbilities();
            p.fallDistance = 0;
        }
    }

    private static void tick(MinecraftServer server) {
        Relic relic = server.overworld().getAttached(RELIC);
        int now = server.getTickCount();
        if (relic == null) {
            if (now % 20 == 0 && !claimLegacyStack(server)) rollSkyfall(server.overworld(), now);
            return;
        }
        Session s = SESSIONS.computeIfAbsent(server, key -> new Session());
        if (relic.phase == FALLING) {
            tickSkyfall(server.overworld(), relic, s, now);
            return;
        }
        ServerPlayer owner = relic.owner.isEmpty() ? null : server.getPlayerList().getPlayer(UUID.fromString(relic.owner));

        if (relic.phase() == IN_GROUND) {
            ServerLevel level = server.getLevel(relic.dimension);
            if (level != null && level.hasChunkAt(BlockPos.containing(relic.position))) {
                if (s.display == null || s.display.isRemoved()) {
                    s.display = spawn(level, relic.position, StormbreakerEntity.EMBEDDED);
                }
                if (s.display != null && s.display.phase() == StormbreakerEntity.PLANTING && now >= s.plantUntil) {
                    s.display.phase(StormbreakerEntity.EMBEDDED);
                    StormFx.IMPACT.play(level, relic.position.add(0, .16, 0), 1.1);
                    sound(level, relic.position, SoundEvents.ANVIL_PLACE, .8f, 1.25f);
                }
                if (s.pull != null) { tickPull(server, s, level, now); return; }
                if (now % 48 == 0) StormFx.EMBEDDED_IDLE.play(level, relic.position.add(.18, .62, 0), .95);
            } else {
                s.pull = null;
                removeDisplay(s);
            }
        }

        // Only the current seal can survive in an inventory. Creative copies and stale return tokens are inert.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean found = cleanInventory(player, relic);
            if (player != owner || !player.isAlive()) continue;
            boolean equipped = guarded(player);
            var strength = player.getAttribute(Attributes.ATTACK_DAMAGE);
            if (strength != null) {
                if (equipped) strength.addOrUpdateTransientModifier(new AttributeModifier(STRENGTH, 5, AttributeModifier.Operation.ADD_VALUE));
                else strength.removeModifier(STRENGTH);
            }
            if (relic.phase() == HELD && !found && player.containerMenu == player.inventoryMenu) restore(player);
        }
        if (owner == null || !owner.isAlive() || InfinitySnap.inVoid(owner)) {
            if (owner != null) stopFlight(owner, s);
            if (relic.phase() == IN_FLIGHT) { removeDisplay(s); s.flight = null; }
            s.release = null;
            s.cast = null;
            s.wave = null;
            s.swing = null;
            return;
        }
        if (!mainHand(owner) || !guarded(owner) || owner.hasAttached(CosmosTravel.FLIGHT) || InfinitySnap.inVoid(owner)) stopFlight(owner, s);
        if (s.flying && owner.getAbilities().flying && now % 5 == 0) {
            Vec3 move = owner.getDeltaMovement();
            if (move.lengthSqr() < .025) move = owner.getLookAngle();
            StormFx.FLIGHT_WAKE.play(owner.level(), owner.position().add(0, .65, 0).subtract(move.normalize()), 2.8, move);
        }
        boolean charged = s.flying || s.cast != null || s.release != null || s.wave != null || s.swing != null;
        setCharged(owner.getMainHandItem(), relic, charged);
        setCharged(owner.getOffhandItem(), relic, s.flying);
        if (s.release != null && now >= s.release.tick) {
            Release release = s.release;
            s.release = null;
            launch(owner, s, release);
        }
        if (s.swing != null) tickSwing(owner, s, now);
        if (s.wave != null) tickWave(owner, s, now);
        if (s.cast != null) tickCast(owner, s, now);
        if (relic.phase() == IN_FLIGHT) tickThrow(owner, s);
    }

    // Before crafting bound the axe immediately, legitimate copies could still be unsealed.
    private static boolean claimLegacyStack(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            Inventory inv = p.getInventory();
            for (int slot = 0; slot < inv.getContainerSize(); slot++) {
                ItemStack stack = inv.getItem(slot);
                if (!stack.is(ModItems.STORMBREAKER) || sealed(stack)) continue;
                Relic relic = Relic.at(p, HELD);
                stack.set(ModComponents.STORMBREAKER_SEAL, relic.seal());
                storeRelic(server, relic);
                status(p, "bound");
                return true;
            }
        }
        return false;
    }

    private static boolean cleanInventory(ServerPlayer player, Relic relic) {
        boolean keep = relic.belongsTo(player) && relic.phase() == HELD;
        ItemStack carried = player.containerMenu.getCarried();
        boolean found = keep && matches(carried, relic);
        if (carried.is(ModItems.STORMBREAKER) && !found) player.containerMenu.setCarried(ItemStack.EMPTY);
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.is(ModItems.STORMBREAKER)) continue;
            if (!found && keep && matches(stack, relic)) found = true;
            else inv.setItem(i, ItemStack.EMPTY);
        }
        return found;
    }

    private static void setCharged(ItemStack stack, Relic relic, boolean charged) {
        if (!matches(stack, relic)) return;
        Identifier model = charged ? CHARGED : BASE;
        if (!model.equals(stack.get(DataComponents.ITEM_MODEL))) stack.set(DataComponents.ITEM_MODEL, model);
    }

    private static Vec3 surface(ServerLevel level, Vec3 at, int above, int below) {
        if (!level.hasChunkAt(BlockPos.containing(at))) return null;
        BlockHitResult hit = level.clip(new ClipContext(at.add(0, above, 0), at.add(0, -below, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : null;
    }

    private static Iterable<LivingEntity> targets(ServerLevel level, Vec3 center, double radius, double height) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius, height, radius), Entity::isAlive);
    }

    private static boolean strike(ServerPlayer p, LivingEntity target, float damage, Vec3 direction, double push) {
        if (target == p || target.isSpectator() || !CoopRules.canAffect(p, target)) return false;
        var type = p.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE);
        if (!target.hurtServer(p.level(), new DamageSource(type, p), damage)) return false;
        Vec3 kick = direction.multiply(1, 0, 1).normalize().scale(push);
        target.push(kick.x, .18, kick.z);
        target.hurtMarked = true;
        return true;
    }

    private static void status(ServerPlayer p, String key) {
        ModStatus.send(p, Component.translatable("stormbreaker.infinity_gauntlet." + key));
    }

    private static void sound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private Stormbreaker() {}
}
