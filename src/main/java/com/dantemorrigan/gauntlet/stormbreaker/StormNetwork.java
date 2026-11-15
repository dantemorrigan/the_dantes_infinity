package com.dantemorrigan.gauntlet.stormbreaker;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class StormNetwork {
    public static final int THROW = 0, WRATH = 1, FLIGHT = 2, AIR_SWING = 3;

    public static final int PRIMARY_START = 4, PRIMARY_RELEASE = 5, PRIMARY_CANCEL = 6, RECALL = 7;

    public record Action(int action) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(InfinityGauntlet.id("stormbreaker_action"));
        public static final StreamCodec<ByteBuf, Action> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, Action::action, Action::new);
        @Override public Type<Action> type() { return TYPE; }
    }

    public record Visual(int entity, String clip, int elapsed, Vec3 origin) implements CustomPacketPayload {
        public static final Type<Visual> TYPE = new Type<>(InfinityGauntlet.id("stormbreaker_visual"));
        public static final StreamCodec<ByteBuf, Visual> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Visual::entity, ByteBufCodecs.STRING_UTF8, Visual::clip,
                ByteBufCodecs.VAR_INT, Visual::elapsed, Vec3.STREAM_CODEC, Visual::origin, Visual::new);
        @Override public Type<Visual> type() { return TYPE; }
    }

    public record Compass(Vec3 site, boolean active) implements CustomPacketPayload {
        public static final Type<Compass> TYPE = new Type<>(InfinityGauntlet.id("stormbreaker_compass"));
        public static final StreamCodec<ByteBuf, Compass> CODEC = StreamCodec.composite(
                Vec3.STREAM_CODEC, Compass::site, ByteBufCodecs.BOOL, Compass::active, Compass::new);
        @Override public Type<Compass> type() { return TYPE; }
    }

    public static final int COMPASS_RANGE = 110;

    static void init() {
        PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Visual.TYPE, Visual.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Compass.TYPE, Compass.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Action.TYPE, (packet, context) ->
                Stormbreaker.action(context.player(), packet.action()));
    }

    static void flightStatus(ServerPlayer owner, int ticks) {
        if (ServerPlayNetworking.canSend(owner, Visual.TYPE)) {
            ServerPlayNetworking.send(owner, new Visual(owner.getId(), "flight_timer", ticks, owner.position()));
        }
    }

    static void visual(ServerPlayer owner, String clip, int elapsed, Vec3 origin) {
        visual(owner.level(), owner.getId(), clip, elapsed, origin);
    }

    static void visual(ServerLevel level, int entity, String clip, int elapsed, Vec3 origin) {
        var payload = new Visual(entity, clip, elapsed, origin);
        for (ServerPlayer viewer : PlayerLookup.around(level, origin, 110)) {
            if (ServerPlayNetworking.canSend(viewer, Visual.TYPE)) ServerPlayNetworking.send(viewer, payload);
        }
    }

    static void compass(ServerLevel level, Vec3 site, boolean active) {
        var payload = new Compass(site, active);
        for (ServerPlayer viewer : PlayerLookup.around(level, site, COMPASS_RANGE)) {
            if (!viewer.isSpectator() && ServerPlayNetworking.canSend(viewer, Compass.TYPE)) {
                ServerPlayNetworking.send(viewer, payload);
            }
        }
    }

    private StormNetwork() {}
}
