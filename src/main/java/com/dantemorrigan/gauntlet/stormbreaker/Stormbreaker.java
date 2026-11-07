package com.dantemorrigan.gauntlet.stormbreaker;

import com.dantemorrigan.gauntlet.CoopRules;
import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModAdvancements;
import com.dantemorrigan.gauntlet.ModComponents;
import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.boss.Thanos;
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
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.util.Mth;
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
    private static final Identifier WIELDER_JUMP = InfinityGauntlet.id("stormbreaker_jump"), WIELDER_HEALTH = InfinityGauntlet.id("stormbreaker_health");
    private static final Identifier STRENGTH = InfinityGauntlet.id("stormbreaker_strength");
    private static final int HELD = 0, IN_FLIGHT = 1, IN_GROUND = 2, FALLING = 3, CREATIVE_MISSING = 4;
    private static final int SKY_FALL_TICKS = 28, PULL_TICKS = 12;
    public static final int GROUND_MAX_CHARGE = 24;
    private static final int GROUND_MIN_CHARGE = 6;
    private static final double WRATH_RADIUS = 20, LIGHTNING_RADIUS = 7;
    private static final int[] STRIKES = {36, 50, 65, 83};
    private static final StormFx[] COMBO = {StormFx.COMBO_1, StormFx.COMBO_2, StormFx.COMBO_3};
    private static final int[] SWING_TICKS = {9, 10, 16};
    private static final int[] SWING_CONTACT = {6, 7, 10};
    private static final double WAVE_STEP = .92;

    private record Relic(String owner, String seal, int phase, ResourceKey<Level> dimension, Vec3 position) {
        static final Codec<Relic> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("owner").forGetter(Relic::owner),
                Codec.STRING.fieldOf("seal").forGetter(Relic::seal),
                Codec.INT.fieldOf("phase").forGetter(Relic::phase),
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Relic::dimension),
                Vec3.CODEC.fieldOf("position").forGetter(Relic::position)).apply(i, Relic::new));

        boolean belongsTo(ServerPlayer p) { return owner.equals(p.getStringUUID()); }

        boolean acceptsStack() { return phase == HELD || phase == CREATIVE_MISSING; }

        Relic withPhase(int phase) { return new Relic(owner, seal, phase, dimension, position); }

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
        GroundCharge groundCharge;
        Cast cast;
        Release release;
        Swing swing;
        Pull pull;
        int fallStart;
        int combo, lastSwing = -100, nextSwing, lightningReady, waveReady, throwReady, wrathReady, plantUntil;
        boolean flying, wasFlying;
    }

    private static final class Flight {
        final boolean recall, crossDimensionRecall;
        final Set<UUID> hit = new HashSet<>();
        float yaw;
        int age;
        Flight(float yaw, boolean recall, boolean crossDimensionRecall) {
            this.yaw = yaw;
            this.recall = recall;
            this.crossDimensionRecall = crossDimensionRecall;
        }
    }

    private record Swing(ServerLevel level, int start, int combo, Vec3 direction, UUID target) {}

    private record Pull(UUID player, int finish) {}

    private record Release(ServerLevel level, int tick, float yaw) {}
    private record GroundCharge(ServerLevel level, Vec3 ground, int started) {}

    private static final class Wave {
        final ServerLevel level;
        final Vec3 start, direction;
        final int tick;
        final int crests;
        final float power;
        final Set<UUID> hit = new HashSet<>();
        Wave(ServerLevel level, Vec3 start, Vec3 direction, int tick, float power) {
            this.level = level;
            this.start = start;
            this.direction = direction;
            this.tick = tick;
            this.power = power;
            this.crests = 9 + Math.round(power * 7);
        }
    }

    private record Cast(ServerLevel level, int tick, Vec3 point, UUID target, boolean wrath) {}

    public static void init() {
        StormFx.init();
        StormNetwork.init();
        ServerTickEvents.END_SERVER_TICK.register(Stormbreaker::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(SESSIONS::remove);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            stopFlight(player, SESSIONS.get(server));
            Relic relic = record(player);
            if (relic != null && relic.belongsTo(player)) ModAdvancements.award(player, "stormbreaker_wielder");
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> stopFlight(handler.getPlayer(), SESSIONS.get(server)));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer p && !com.dantemorrigan.gauntlet.boss.TribunalEncounter.awaitingReturn(p)) onDeath(p);
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
            if (relic.belongsTo(p)) ModAdvancements.award(p, "stormbreaker_wielder");
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
        if (relic.acceptsStack() && relic.belongsTo(p) && matches(stack, relic)) {
            if (relic.phase() == CREATIVE_MISSING) storeRelic(server(p), relic.withPhase(HELD));
            return true;
        }
        status(p, "already_bound");
        return false;
    }

    static void action(ServerPlayer p, int action) {
        if (action == StormNetwork.RECALL) {
            recall(p);
            return;
        }
        if (action == StormNetwork.PRIMARY_CANCEL) {
            Session s = SESSIONS.get(server(p));
            if (s != null && s.groundCharge != null) cancelGroundCharge(p, s);
            return;
        }
        if (!mainHand(p)) return;
        switch (action) {
            case StormNetwork.THROW -> startThrow(p);
            case StormNetwork.WRATH -> beginCast(p, true);
            case StormNetwork.FLIGHT -> toggleFlight(p);
            case StormNetwork.AIR_SWING -> {
                if (PowerAbility.pick(p, 4.5).getType() == HitResult.Type.MISS) swing(p, null);
            }
            case StormNetwork.PRIMARY_START -> primary(p);
            case StormNetwork.PRIMARY_RELEASE -> releaseGroundCharge(p);
            default -> { }
        }
    }

    private static void primary(ServerPlayer p) {
        HitResult hit = PowerAbility.pick(p, 5);
        if (hit instanceof EntityHitResult entity && entity.getEntity() instanceof LivingEntity living) {
            swing(p, living);
        } else if (hit instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK
                && block.getDirection() == Direction.UP) {
            startGroundCharge(p, block.getLocation());
        } else {
            swing(p, null);
        }
    }

    private static void swing(ServerPlayer p, LivingEntity direct) {
        if (!ready(p)) return;
        Session s = session(p);
        int tick = server(p).getTickCount();
        if (tick < s.nextSwing || s.release != null || s.groundCharge != null) return;
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
        Vec3 arc = p.getEyePosition().add(forward.scale(1.8)).add(0, -.42, 0);
        if (age == SWING_CONTACT[index] - 2) {
            COMBO[index].play(p.level(), arc.add(0, .25, 0), 3.15 + .32 * index);
            sound(p.level(), p.position(), SoundEvents.TRIDENT_RIPTIDE_1.value(), .65f, 1.20f - index * .16f);
            sound(p.level(), arc, SoundEvents.WIND_CHARGE_THROW, .48f, 1.35f - index * .17f);
        }
        if (age < SWING_CONTACT[index]) return;
        s.swing = null;
        Entity direct = swing.target == null ? null : p.level().getEntity(swing.target);
        if (direct instanceof LivingEntity living && living.isAlive() && p.hasLineOfSight(living)
                && p.distanceToSqr(living) < 30.25 && strike(p, living, 10 + index * 4, forward, .28 + index * .16)) {
            Vec3 impact = direct.getBoundingBox().getCenter();
            StormFx.IMPACT.play(p.level(), impact, 1.05 + index * .35);
            sound(p.level(), impact, SoundEvents.TRIDENT_HIT, .9f, .91f - index * .12f);
            sound(p.level(), impact, index == 2 ? SoundEvents.MACE_SMASH_GROUND_HEAVY : SoundEvents.ANVIL_HIT,
                    index == 2 ? 1.2f : .6f, .74f + index * .06f);
            if (index == 2) StormFx.LIGHTNING_HIT.play(p.level(), living.position(), 4.5);
        }
        // The Living Tribunal is too large for a normal hit test: use his real hitbox. The axe only chips him.
        var giant = com.dantemorrigan.gauntlet.boss.TribunalEncounter.targetFor(p);
        if (giant != null && com.dantemorrigan.gauntlet.power.CombatTargets.near(giant, arc, 3.4)
                && strike(p, giant, 8 + index * 3, forward, 0)) {
            StormFx.IMPACT.play(p.level(), arc, 1.4 + index * .3);
            sound(p.level(), arc, SoundEvents.TRIDENT_HIT, .9f, .8f);
        }
        // The Ender Dragon is made of separate parts: a swing that reaches any of them hits the dragon.
        for (var dragon : p.level().getEntitiesOfClass(net.minecraft.world.entity.boss.enderdragon.EnderDragon.class,
                new AABB(arc, arc).inflate(24), Entity::isAlive)) {
            for (var part : dragon.getSubEntities()) {
                if (dragon != direct && part.getBoundingBox().inflate(1.6).contains(arc)) {
                    if (strike(p, dragon, 10 + index * 4, forward, 0)) {
                        StormFx.IMPACT.play(p.level(), arc, 1.2 + index * .3);
                        sound(p.level(), arc, SoundEvents.TRIDENT_HIT, .9f, .8f);
                    }
                    break;
                }
            }
        }
        if (index != 1) return;
        for (LivingEntity target : targets(p.level(), arc, 2.1, 1.4)) {
            Vec3 delta = target.position().subtract(p.position());
            if (target != direct && delta.lengthSqr() < 12 && delta.normalize().dot(forward) > .66 && p.hasLineOfSight(target)) {
                strike(p, target, 8, forward, .3);
            }
        }
    }

    private static void startGroundCharge(ServerPlayer p, Vec3 ground) {
        if (!ready(p)) return;
        Session s = session(p);
        int now = server(p).getTickCount();
        if (now < s.waveReady || now < s.nextSwing || s.release != null || s.groundCharge != null) return;
        s.groundCharge = new GroundCharge(p.level(), ground, now);
        StormNetwork.visual(p, "ground_charge", 0, ground);
        sound(p.level(), p.position(), SoundEvents.TRIDENT_RIPTIDE_1.value(), .85f, .55f);
    }

    private static void cancelGroundCharge(ServerPlayer p, Session s) {
        s.groundCharge = null;
        StormNetwork.visual(p, "ground_cancel", 0, p.position());
    }

    private static void releaseGroundCharge(ServerPlayer p) {
        Session s = session(p);
        GroundCharge charge = s.groundCharge;
        if (charge == null) return;
        cancelGroundCharge(p, s);
        if (charge.level != p.level() || !ready(p) || p.distanceToSqr(charge.ground) > 49) return;
        int now = server(p).getTickCount();
        int held = now - charge.started;
        if (held < GROUND_MIN_CHARGE) {
            swing(p, null);
            return;
        }
        float power = Math.min(1, (held - GROUND_MIN_CHARGE) / (float) (GROUND_MAX_CHARGE - GROUND_MIN_CHARGE));
        s.waveReady = now + 65;
        s.nextSwing = Math.max(s.nextSwing, now + 20);
        Vec3 direction = Vec3.directionFromRotation(0, p.getYRot());
        s.wave = new Wave(p.level(), charge.ground, direction, now, power);
        StormNetwork.visual(p, "ground_slam", 0, charge.ground);
    }

    private static void tickGroundCharge(ServerPlayer p, Session s, int now) {
        GroundCharge charge = s.groundCharge;
        if (charge.level != p.level() || !mainHand(p) || !guarded(p) || p.distanceToSqr(charge.ground) > 49) {
            cancelGroundCharge(p, s);
            return;
        }
        int age = now - charge.started;
        if (age % 7 == 0) StormFx.TAKEOFF.play(p.level(), charge.ground.add(0, .06, 0),
                1.2 + Math.min(age, GROUND_MAX_CHARGE) / 12.0);
    }

    private static void tickWave(ServerPlayer p, Session s, int now) {
        Wave w = s.wave;
        int age = now - w.tick;
        int crest = age - 7;
        if (w.level != p.level() || crest >= w.crests * 2) { s.wave = null; return; }
        if (crest == 0) {
            sound(w.level, w.start, SoundEvents.MACE_SMASH_GROUND_HEAVY, 2.2f, .54f);
            sound(w.level, w.start, SoundEvents.GENERIC_EXPLODE.value(), 1.5f, .76f);
            StormFx.IMPACT.play(w.level, w.start.add(0, .2, 0), 4.2);
            StormFx.TAKEOFF.play(w.level, w.start.add(0, .06, 0), 5.5);
            StormFx.LIGHTNING_HIT.play(w.level, w.start, 8);
        }
        if (crest < 0) return;
        Vec3 center = w.start.add(w.direction.scale(crest * WAVE_STEP));
        boolean blockChanges = GauntletConfig.get().abilityBlockChanges;
        Vec3 lateral = new Vec3(-w.direction.z, 0, w.direction.x);
        double halfWidth = 3.8 + 1.4 * w.power;
        if ((crest & 1) != 0) {
            if (blockChanges) carveCraters(p, w, lateral, crest, halfWidth);
            return;
        }
        for (int lane = -1; lane <= 1; lane++) {
            Vec3 sample = center.add(w.direction.scale(.72)).add(lateral.scale(lane * halfWidth * .72));
            Vec3 surface = surface(w.level, sample, 4, 7);
            if (surface == null) continue;
            StormFx.GROUND_WALL.play(w.level, surface, 4.5 + w.power, w.direction);
            if (blockChanges) {
                var block = w.level.getBlockState(BlockPos.containing(surface.add(0, -.04, 0)));
                if (!block.isAir()) w.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block),
                        surface.x, surface.y + .28, surface.z, 12, .55, .33, .55, .28);
            }
            for (LivingEntity target : targets(w.level, surface.add(0, .8, 0), 2.65, 2.2)) {
                Vec3 offset = target.position().subtract(center);
                if (Math.abs(offset.dot(lateral)) > halfWidth || Math.abs(offset.dot(w.direction)) > 1.2
                        || w.hit.contains(target.getUUID())) continue;
                if (strike(p, target, 15 + 9 * w.power, w.direction, 1.15)) {
                    w.hit.add(target.getUUID());
                    if (w.hit.size() == 3) ModAdvancements.award(p, "stormbreaker_wave");
                }
            }
        }
        if (blockChanges) carveCraters(p, w, lateral, crest, halfWidth);
        if (crest % 6 == 0) sound(w.level, center, SoundEvents.WIND_CHARGE_BURST.value(), .85f, .62f);
    }

    private static void carveCraters(ServerPlayer player, Wave wave, Vec3 lateral, int crest, double halfWidth) {
        ServerLevel level = wave.level;
        double from = crest * WAVE_STEP;
        double to = from + WAVE_STEP;
        Vec3 center = wave.start.add(wave.direction.scale((from + to) * .5));
        double xRadius = Math.abs(lateral.x) * halfWidth + Math.abs(wave.direction.x) * WAVE_STEP * .5;
        double zRadius = Math.abs(lateral.z) * halfWidth + Math.abs(wave.direction.z) * WAVE_STEP * .5;
        int ground = Mth.floor(wave.start.y - .04);
        var pos = new BlockPos.MutableBlockPos();
        // One slice per tick. Overlapping bowls leave deeper pockets joined by a torn channel.
        for (int x = Mth.floor(center.x - xRadius); x <= Mth.floor(center.x + xRadius); x++) {
            for (int z = Mth.floor(center.z - zRadius); z <= Mth.floor(center.z + zRadius); z++) {
                Vec3 column = new Vec3(x + .5, wave.start.y, z + .5);
                Vec3 offset = column.subtract(wave.start);
                double forward = offset.dot(wave.direction);
                if (forward < from || forward >= to || !level.hasChunkAt(pos.set(x, ground, z))) continue;
                double side = offset.dot(lateral) / halfWidth;
                double along = Math.IEEEremainder(forward - 2.76, 7.36) / 4.6;
                double bowl = 1 - side * side - along * along;
                if (bowl <= 0) continue;

                Vec3 terrain = surface(level, column, 1, 7);
                int floor = terrain == null ? ground : Math.min(ground, Mth.floor(terrain.y - .04));
                int depth = 1 + (int) Math.ceil((1 + 3 * wave.power) * bowl);
                int clearance = 2 + (int) Math.ceil((1 + 3 * wave.power) * bowl);
                for (int y = ground + clearance; y > floor - depth; y--) {
                    pos.set(x, y, z);
                    if (level.isOutsideBuildHeight(pos)) continue;
                    var block = level.getBlockState(pos);
                    if (!block.isAir() && !block.hasBlockEntity() && block.getFluidState().isEmpty()
                            && block.getDestroySpeed(level, pos) >= 0 && CoopRules.canEdit(player, level, pos)) {
                        level.removeBlock(pos, false);
                    }
                }
            }
        }
    }

    static void lightning(ServerPlayer p) { beginCast(p, false); }

    private static void beginCast(ServerPlayer p, boolean wrath) {
        if (!ready(p)) return;
        Session s = session(p);
        int now = server(p).getTickCount();
        if (s.cast != null || s.release != null || s.groundCharge != null || now < (wrath ? s.wrathReady : s.lightningReady)) return;
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
        Vec3 ground = surface(cast.level, point, 3, 80);
        if (ground == null) ground = point;
        if (cast.wrath ? age >= 12 && age <= 82 && (age - 12) % 10 == 0 : age == 7) {
            StormFx.WRATH_TARGET.play(cast.level, ground.add(0, .05, 0),
                    2 * (cast.wrath ? WRATH_RADIUS : LIGHTNING_RADIUS));
        }
        if (cast.wrath && age % 20 == 0) StormNetwork.visual(p, "heavens_wrath", age, cast.point);
        if (!cast.wrath && age == 10) bolt(p, ground, -1);
        if (!cast.wrath) return;
        for (int i = 0; i < STRIKES.length; i++) {
            if (age == STRIKES[i]) bolt(p, ground, i);
        }
    }

    private static void bolt(ServerPlayer p, Vec3 ground, int stage) {
        boolean wrath = stage >= 0;
        double radius = wrath ? WRATH_RADIUS : LIGHTNING_RADIUS;
        strikeVisual(p.level(), ground, wrath, wrath ? 23 + stage * 3 : 13, wrath ? 7 : 4.5);
        int branches = wrath ? 6 : 3;
        for (int i = 0; i < branches; i++) {
            double angle = i * Math.TAU / branches + (stage + 1) * .47;
            double offset = radius * (i % 2 == 0 ? .46 : .82);
            Vec3 sample = ground.add(Math.cos(angle) * offset, 0, Math.sin(angle) * offset);
            Vec3 impact = surface(p.level(), sample, 12, 20);
            if (impact != null) strikeVisual(p.level(), impact, wrath, wrath ? 18 + stage * 2 : 8.5, wrath ? 4.5 : 2.7);
        }
        sound(p.level(), ground, SoundEvents.LIGHTNING_BOLT_THUNDER, wrath ? 3.2f : 1.7f, wrath ? .84f - stage * .08f : 1.1f);
        sound(p.level(), ground, SoundEvents.LIGHTNING_BOLT_IMPACT, wrath ? 2.0f : 1.3f, .83f);
        int marked = 0;
        for (LivingEntity living : targets(p.level(), ground, radius, wrath ? 15 : 8)) {
            Vec3 push = living.position().subtract(ground);
            if (push.x * push.x + push.z * push.z > radius * radius) continue;
            if (!strike(p, living, wrath ? 11 + stage * 4 : 16, push, wrath ? .55 : .66)) continue;
            if (wrath && living instanceof Thanos) ModAdvancements.award(p, "stormbreaker_titan");
            if (marked++ >= 8) continue;
            StormFx.IMPACT.play(p.level(), living.getBoundingBox().getCenter(), wrath ? 2.8 : 1.8);
            if (wrath && push.horizontalDistanceSqr() > 12) {
                StormFx.LIGHTNING_HIT.play(p.level(), living.position(), 11);
            }
        }
    }

    private static void strikeVisual(ServerLevel level, Vec3 ground, boolean wrath, double height, double size) {
        (wrath ? StormFx.WRATH_BOLT : StormFx.LIGHTNING_HIT).play(level, ground, height);
        StormFx.IMPACT.play(level, ground.add(0, .25, 0), size);
        StormFx.TAKEOFF.play(level, ground.add(0, .05, 0), size * 1.25);
    }

    private static void startThrow(ServerPlayer p) {
        if (!ready(p)) return;
        if (CosmosTravel.inCosmos(p)) { status(p, "space_locked"); return; }
        Session s = session(p);
        int now = server(p).getTickCount();
        if (s.release != null || s.groundCharge != null || now < s.throwReady) return;
        s.release = new Release(p.level(), now + 8, p.getYRot());
        s.nextSwing = Math.max(s.nextSwing, now + 12);
        StormNetwork.visual(p, "throw_release", 0, p.position());
    }

    private static void launch(ServerPlayer p, Session s, Release release) {
        if (release.level != p.level() || !mainHand(p) || !guarded(p)
                || !matches(p.getMainHandItem(), record(p))) return;
        Vec3 direction = Vec3.directionFromRotation(0, release.yaw);
        Vec3 origin = p.getEyePosition().add(direction.scale(.75)).add(0, -.24, 0);
        if (!p.level().hasChunkAt(BlockPos.containing(origin))) return;
        StormbreakerEntity model = spawn(p.level(), origin, StormbreakerEntity.OUTBOUND);
        if (model == null) return;
        s.display = model;
        s.flight = new Flight(release.yaw, false, false);
        model.setYRot(release.yaw);
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
        Vec3 toHand = hand.subtract(pos);
        double horizontal = toHand.horizontalDistance();
        boolean lifting = flight.recall && flight.age <= 5;
        if (returning && !lifting && toHand.lengthSqr() < 2.9) { returnToOwner(p, s, true); return; }
        if (returning && horizontal > .05) {
            float desired = (float) Math.toDegrees(Math.atan2(-toHand.x, toHand.z));
            flight.yaw = horizontal < 5 ? desired
                    : flight.yaw + Mth.clamp(Mth.wrapDegrees(desired - flight.yaw), -24f, 24f);
        }
        Vec3 direction = Vec3.directionFromRotation(0, flight.yaw);
        double rise = returning && (flight.recall || horizontal < 6)
                ? Mth.clamp(toHand.y * .32, -.95, .95) : 0;
        double speed = returning ? Math.min(2.45, horizontal) : 1.65;
        Vec3 next = lifting ? pos.add(direction.scale(.20)).add(0, .45, 0)
                : pos.add(direction.scale(speed)).add(0, rise, 0);
        if (!p.level().hasChunkAt(BlockPos.containing(next))) {
            if (returning) returnToOwner(p, s, false);
            else turn(axe, flight);
            return;
        }
        if (!flight.recall && !clearPath(p, pos, next)) {
            if (returning) returnToOwner(p, s, false);
            else turn(axe, flight);
            return;
        }
        axe.setYRot(flight.yaw);
        axe.setDeltaMovement(next.subtract(pos));
        axe.setPos(next.x, next.y, next.z);
        if (flight.age % 3 == 1) {
            (returning ? StormFx.THROW_RETURN : StormFx.THROW_OUT).play(p.level(), next.subtract(direction.scale(1.05)), 2.65, direction);
        }
        if (flight.recall) return;
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
        if (!restore(p)) return; // Full inventory: keep the pending flight and retry.
        if (s.flight != null && s.flight.crossDimensionRecall) ModAdvancements.award(p, "stormbreaker_recall");
        s.flight = null;
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
        // The axe lands straight in the main hand; whatever was there moves to a free slot.
        ItemStack held = p.getMainHandItem();
        if (!held.isEmpty()) {
            int slot = p.getInventory().getFreeSlot();
            if (slot < 0) return false;
            p.getInventory().setItem(slot, held.copy());
        }
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        p.swing(InteractionHand.MAIN_HAND, true);
        sound(p.level(), p.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, .7f, 1.5f);
        StormFx.RECLAIM.play(p.level(), p.position().add(0, 1.1, 0), 1.2);
        p.inventoryMenu.broadcastChanges();
        server(p).overworld().setAttached(RELIC, fresh);
        return true;
    }

    private static void onDeath(ServerPlayer p) {
        Relic relic = record(p);
        if (relic == null || !relic.belongsTo(p) || relic.phase() == IN_GROUND || relic.phase() == CREATIVE_MISSING) return;
        Session s = session(p);
        stopFlight(p, s);
        s.cast = null;
        s.wave = null;
        s.groundCharge = null;
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
        plant(p, relic, s, site);
    }

    public static boolean drop(ServerPlayer p, ItemStack stack) {
        Relic relic = record(p);
        if (CosmosTravel.inCosmos(p) && p.isAlive() && relic != null && relic.belongsTo(p) && matches(stack, relic)) {
            // Nothing to plant it in, and it must never be lost in the void: it stays with its owner.
            if (p.getMainHandItem().isEmpty()) p.setItemInHand(InteractionHand.MAIN_HAND, stack);
            else p.getInventory().add(stack);
            status(p, "space_locked");
            return true;
        }
        if (!p.isAlive() || p.isSpectator() || relic == null || !relic.acceptsStack()
                || !relic.belongsTo(p) || !matches(stack, relic)) return false;
        Vec3 front = p.position().add(Vec3.directionFromRotation(0, p.getYRot()).scale(2.3));
        Vec3 site = surface(p.level(), front, 1, 64);
        if (site == null) site = surface(p.level(), p.position(), 1, 64);
        plant(p, relic, session(p), site == null ? p.position() : site);
        ModStatus.send(p, Component.translatable("stormbreaker.infinity_gauntlet.planted",
                Component.keybind("key.infinity_gauntlet.stormbreaker_recall")));
        return true;
    }

    private static void plant(ServerPlayer p, Relic relic, Session s, Vec3 site) {
        stopFlight(p, s);
        if (s.cast != null || s.groundCharge != null) StormNetwork.visual(p, "stop", 0, p.position());
        s.cast = null;
        s.wave = null;
        s.groundCharge = null;
        s.release = null;
        s.swing = null;
        server(p).overworld().setAttached(RELIC, new Relic(relic.owner, UUID.randomUUID().toString(),
                IN_GROUND, p.level().dimension(), site));
        if (!p.level().isOutsideBuildHeight(BlockPos.containing(site)) && p.level().hasChunkAt(BlockPos.containing(site))) {
            s.display = spawn(p.level(), site, StormbreakerEntity.PLANTING);
            s.plantUntil = server(p).getTickCount() + 10;
        }
    }

    private static void recall(ServerPlayer p) {
        Relic relic = record(p);
        if (!p.isAlive() || p.isSpectator() || InfinitySnap.inVoid(p) || relic == null
                || (relic.phase != IN_GROUND && relic.phase != CREATIVE_MISSING) || !relic.belongsTo(p)) return;
        Session s = session(p);
        if (s.pull != null) return;
        if (p.getInventory().getFreeSlot() < 0) { status(p, "inventory_full"); return; }
        Vec3 hand = p.getEyePosition();
        boolean local = relic.phase == IN_GROUND && relic.dimension == p.level().dimension() && !p.level().isOutsideBuildHeight(BlockPos.containing(relic.position))
                && hand.distanceToSqr(relic.position) <= 80 * 80
                && surface(p.level(), relic.position, 1, 2) != null;
        Vec3 from = local ? relic.position.add(0, 1, 0)
                : hand.add(Vec3.directionFromRotation(0, p.getYRot()).scale(18)).add(0, 5, 0);
        if (!p.level().hasChunkAt(BlockPos.containing(from))) from = hand.add(0, 6, 0);
        StormbreakerEntity axe = spawn(p.level(), from, StormbreakerEntity.RETURNING);
        if (axe == null) return;
        removeDisplay(s);
        Vec3 toHand = hand.subtract(from);
        float yaw = toHand.horizontalDistanceSqr() > .001
                ? (float) Math.toDegrees(Math.atan2(-toHand.x, toHand.z)) : p.getYRot();
        axe.setYRot(yaw);
        s.display = axe;
        s.flight = new Flight(yaw, true, relic.phase == IN_GROUND && relic.dimension != p.level().dimension());
        storeRelic(server(p), Relic.at(p, IN_FLIGHT));
        StormFx.LIGHTNING_HIT.play(p.level(), from.add(0, -.5, 0), 10);
        StormFx.RECLAIM.play(p.level(), from, local ? 3.2 : 5.5);
        sound(p.level(), from, SoundEvents.TRIDENT_THUNDER.value(), 1.6f, .78f);
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
        Relic relic = server.overworld().getAttached(RELIC);
        if (nearby && restore(p)) {
            if (relic.owner.isEmpty()) {
                ModAdvancements.award(p, "stormbreaker_wielder");
                ModAdvancements.award(p, "stormbreaker_skyfall");
                StormNetwork.compass(level, relic.position, false);
                clearDebugStorm(server); // the storm summoned by the debug command ends when the axe is claimed
            }
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

    /** Debug: calls a thunderstorm and drops the world's Stormbreaker ~30 blocks from the player (the real skyfall). Returns false if one already exists or there is no room. */
    public static boolean debugSkyfall(ServerPlayer player) {
        var level = player.level();
        if (level.dimension() != Level.OVERWORLD) return false;
        Relic current = level.getServer().overworld().getAttached(RELIC);
        // A creative copy that was deleted leaves a placeholder record: it must not block the event.
        if (current != null && current.phase() == CREATIVE_MISSING) level.getServer().overworld().removeAttached(RELIC);
        else if (current != null) return false;
        var random = level.getRandom();
        for (int i = 0; i < 16; i++) {
            double angle = random.nextDouble() * Math.TAU, range = 24 + random.nextDouble() * 12;
            BlockPos column = BlockPos.containing(player.getX() + Math.cos(angle) * range, player.getY(), player.getZ() + Math.sin(angle) * range);
            if (!level.hasChunkAt(column) || !level.getWorldBorder().isWithinBounds(column)) continue;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
            if (!level.getBlockState(top).isAir() || !level.getFluidState(top.below()).isEmpty() || !level.getBlockState(top.below()).isSolidRender()) continue;
            Vec3 landing = Vec3.atBottomCenterOf(top);
            if (!level.hasChunkAt(BlockPos.containing(fallingPosition(landing, 0)))) continue;
            // The full event: a thunderstorm rolls in, then the axe streaks down with thunder, lightning and the compass.
            runWeather(level.getServer(), "thunder");
            debugStormUntil = level.getServer().getTickCount() + 6000L; // never left on forever, even with weather cycle disabled
            storeRelic(level.getServer(), new Relic("", UUID.randomUUID().toString(), FALLING, level.dimension(), landing));
            return true;
        }
        return false;
    }

    /** Tick by which a storm summoned by the debug command is cleared again (0 = none). */
    private static long debugStormUntil;

    private static void runWeather(MinecraftServer server, String weather) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "weather " + weather);
    }

    private static void clearDebugStorm(MinecraftServer server) {
        if (debugStormUntil == 0) return;
        debugStormUntil = 0;
        runWeather(server, "clear");
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
        StormNetwork.compass(level, landing, true);
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

    public static boolean isFlying(ServerPlayer p) {
        Session session = SESSIONS.get(server(p));
        return session != null && session.flying && guarded(p);
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
        if (debugStormUntil != 0 && now > debugStormUntil) clearDebugStorm(server);
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
            if (level != null && relic.owner.isEmpty() && now % 20 == 0) {
                StormNetwork.compass(level, relic.position, true);
            }
            if (level != null && !level.isOutsideBuildHeight(BlockPos.containing(relic.position))
                    && level.hasChunkAt(BlockPos.containing(relic.position))) {
                if (s.display == null || s.display.isRemoved()) {
                    s.display = spawn(level, relic.position, StormbreakerEntity.EMBEDDED);
                }
                if (s.display != null && s.display.phase() == StormbreakerEntity.PLANTING && now >= s.plantUntil) {
                    s.display.phase(StormbreakerEntity.EMBEDDED);
                    StormFx.IMPACT.play(level, relic.position.add(0, .16, 0), 1.1);
                    sound(level, relic.position, SoundEvents.ANVIL_PLACE, .8f, 1.25f);
                }
                if (s.pull != null) { tickPull(server, s, level, now); return; }
                if (now % 14 == 0) StormFx.EMBEDDED_IDLE.play(level, relic.position.add(.18, .65, 0), 2.5);
                if (s.display != null && s.display.phase() == StormbreakerEntity.EMBEDDED && now % 50 == 0) {
                    Vec3 socket = relic.position.add(0, .35, 0);
                    StormFx.WRATH_BOLT.play(level, socket, 20);
                    StormFx.RECLAIM.play(level, socket, 2.8);
                    sound(level, socket, SoundEvents.LIGHTNING_BOLT_IMPACT, .85f, 1.14f);
                }
            } else {
                s.pull = null;
                removeDisplay(s);
            }
        }

        // Only the current seal can survive in an inventory. Creative copies and stale return tokens are inert.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean found = cleanInventory(player, relic);
            if (player != owner || !player.isAlive()) continue;
            if (found && relic.phase() == CREATIVE_MISSING) {
                relic = relic.withPhase(HELD);
                storeRelic(server, relic);
            } else if (!found && relic.phase() == CREATIVE_MISSING && !player.isCreative()) {
                // The axe was taken from the creative menu and deleted: the world forgets it, so it can fall again.
                server.overworld().removeAttached(RELIC);
                return;
            } else if (!found && relic.phase() == HELD && player.containerMenu == player.inventoryMenu) {
                if (player.isCreative()) {
                    // Creative drags use a client-only cursor. Keep the seal valid without replacing an absent stack.
                    relic = relic.withPhase(CREATIVE_MISSING);
                    storeRelic(server, relic);
                } else {
                    restore(player);
                }
            }
            boolean equipped = guarded(player);
            var strength = player.getAttribute(Attributes.ATTACK_DAMAGE);
            if (strength != null) {
                if (equipped) strength.addOrUpdateTransientModifier(new AttributeModifier(STRENGTH, 5, AttributeModifier.Operation.ADD_VALUE));
                else strength.removeModifier(STRENGTH);
            }
            // Carrying the bound axe anywhere in the inventory empowers its wielder: a higher jump and extra hearts.
            boolean carrying = found && relic.belongsTo(player);
            var jump = player.getAttribute(Attributes.JUMP_STRENGTH);
            if (jump != null) {
                if (carrying) jump.addOrUpdateTransientModifier(new AttributeModifier(WIELDER_JUMP, .14, AttributeModifier.Operation.ADD_VALUE));
                else jump.removeModifier(WIELDER_JUMP);
            }
            var health = player.getAttribute(Attributes.MAX_HEALTH);
            if (health != null) {
                if (carrying) health.addOrUpdateTransientModifier(new AttributeModifier(WIELDER_HEALTH, 10, AttributeModifier.Operation.ADD_VALUE));
                else if (health.removeModifier(WIELDER_HEALTH) && player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
            }
        }
        if (owner == null || !owner.isAlive() || InfinitySnap.inVoid(owner)) {
            if (owner != null) stopFlight(owner, s);
            if (relic.phase() == IN_FLIGHT) { removeDisplay(s); s.flight = null; }
            s.release = null;
            s.cast = null;
            s.wave = null;
            s.groundCharge = null;
            s.swing = null;
            return;
        }
        if (!mainHand(owner) || !guarded(owner) || owner.hasAttached(CosmosTravel.FLIGHT) || InfinitySnap.inVoid(owner)) stopFlight(owner, s);
        if (s.flying && owner.getAbilities().flying && now % 5 == 0) {
            Vec3 move = owner.getDeltaMovement();
            if (move.lengthSqr() < .025) move = owner.getLookAngle();
            StormFx.FLIGHT_WAKE.play(owner.level(), owner.position().add(0, .65, 0).subtract(move.normalize()), 2.8, move);
        }
        if (s.groundCharge != null) tickGroundCharge(owner, s, now);
        boolean charged = s.groundCharge != null || s.flying || s.cast != null || s.release != null || s.wave != null || s.swing != null;
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
        boolean keep = relic.belongsTo(player) && relic.acceptsStack();
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
        if (player.containerMenu == player.inventoryMenu) {
            for (var slot : player.inventoryMenu.getInputGridSlots()) {
                ItemStack stack = slot.getItem();
                if (!stack.is(ModItems.STORMBREAKER)) continue;
                if (!found && keep && matches(stack, relic)) found = true;
                else slot.set(ItemStack.EMPTY);
            }
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
        DamageSource source = new DamageSource(type, p);
        // The Ender Dragon takes a quarter damage on any part but the head: a Stormbreaker blow counts as a head hit.
        boolean landed = target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon
                ? dragon.hurt(p.level(), dragon.head, source, damage)
                : target.hurtServer(p.level(), source, damage);
        if (!landed) return false;
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
