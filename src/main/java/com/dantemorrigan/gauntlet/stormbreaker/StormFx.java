package com.dantemorrigan.gauntlet.stormbreaker;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public enum StormFx {
    COMBO_1(9), COMBO_2(9), COMBO_3(9),
    LIGHTNING_HIT(10, 1, 177f / 192, Facing.VERTICAL),
    WRATH_BOLT(12, 1, 241f / 256, Facing.VERTICAL),
    GROUND_WALL(10, 1, .5f + .31f * 96 / 224, Facing.DIRECTED),
    THROW_OUT(8, 1, .5f, Facing.TRAIL), THROW_RETURN(8, 1, .5f, Facing.TRAIL),
    FLIGHT_WAKE(8, 2, .5f, Facing.TRAIL),
    IMPACT(10), BLOCK_SHARDS(10), CATCH(10), RECLAIM(10),
    TAKEOFF(10, 1, .5f, Facing.FLAT),
    GAUNTLET_GUARD(10, 1, .5f, Facing.DIRECTED), OWNERSHIP_DENIED(9),
    EMBEDDED_IDLE(8, 3, .66f, Facing.VERTICAL),
    WRATH_TARGET(10, 2, .5f, Facing.FLAT);

    public enum Facing { CAMERA, VERTICAL, DIRECTED, FLAT, TRAIL }

    public final SimpleParticleType particle;
    public final int frames;
    public final int ticksPerFrame;
    public final float pivotY;
    public final Facing facing;

    StormFx(int frames) {
        this(frames, 1, .5f, Facing.CAMERA);
    }

    StormFx(int frames, int ticksPerFrame, float pivotY, Facing facing) {
        this.frames = frames;
        this.ticksPerFrame = ticksPerFrame;
        this.pivotY = pivotY;
        this.facing = facing;
        particle = Registry.register(BuiltInRegistries.PARTICLE_TYPE, InfinityGauntlet.id("stormbreaker/" + id()),
                FabricParticleTypes.simple(true));
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public void play(ServerLevel level, Vec3 at, double size) {
        play(level, at, size, new Vec3(0, 1, 0));
    }

    public void play(ServerLevel level, Vec3 at, double size, Vec3 direction) {
        if (this == WRATH_BOLT || this == LIGHTNING_HIT) {
            StormNetwork.visual(level, 0, this == WRATH_BOLT ? "sky_bolt_heavy" : "sky_bolt", 0, at);
            return;
        }
        Vec3 parameters = direction.normalize().scale(size);
        level.sendParticles(particle, false, false, at.x, at.y, at.z, 0, parameters.x, parameters.y, parameters.z, 1);
    }

    static void init() {}
}
