package com.dantemorrigan.gauntlet.net;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.space.SpaceRecord;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** All custom packets. Server is authoritative; the client only plays visuals for what it is told. */
public final class ModNetwork {
	private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> newType(String name) {
		return new CustomPacketPayload.Type<>(InfinityGauntlet.id(name));
	}

	/** C2S: toggle Power stone mode (strike / Planet Destruction). */
	public record ToggleMode() implements CustomPacketPayload {
		public static final Type<ToggleMode> TYPE = newType("toggle_mode");
		public static final StreamCodec<ByteBuf, ToggleMode> CODEC = StreamCodec.unit(new ToggleMode());
		@Override public Type<ToggleMode> type() { return TYPE; }
	}

	/** S2C: power strike visuals at a point. normal = surface normal (ground ring orientation). */
	public record PowerFx(Vec3 pos, Vec3 normal, float strength) implements CustomPacketPayload {
		public static final Type<PowerFx> TYPE = newType("power_fx");
		public static final StreamCodec<ByteBuf, PowerFx> CODEC = StreamCodec.composite(
				Vec3.STREAM_CODEC, PowerFx::pos, Vec3.STREAM_CODEC, PowerFx::normal, ByteBufCodecs.FLOAT, PowerFx::strength, PowerFx::new);
		@Override public Type<PowerFx> type() { return TYPE; }
	}

	/** S2C: debug — play one named effect at a position. */
	public record EffectDemo(String effect, Vec3 pos) implements CustomPacketPayload {
		public static final Type<EffectDemo> TYPE = newType("effect_demo");
		public static final StreamCodec<ByteBuf, EffectDemo> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, EffectDemo::effect, Vec3.STREAM_CODEC, EffectDemo::pos, EffectDemo::new);
		@Override public Type<EffectDemo> type() { return TYPE; }
	}

	/** S2C: planet destruction cutscene starts (elapsed = ticks already passed, for late sync). */
	public record CutsceneStart(int eventId, Vec3 origin, int elapsed, int transferTick) implements CustomPacketPayload {
		public static final Type<CutsceneStart> TYPE = newType("cutscene_start");
		public static final StreamCodec<ByteBuf, CutsceneStart> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, CutsceneStart::eventId, Vec3.STREAM_CODEC, CutsceneStart::origin,
				ByteBufCodecs.VAR_INT, CutsceneStart::elapsed, ByteBufCodecs.VAR_INT, CutsceneStart::transferTick, CutsceneStart::new);
		@Override public Type<CutsceneStart> type() { return TYPE; }
	}

	/** S2C: cutscene aborted (death, disconnect of activator, dimension change...). Client must clean up. */
	public record CutsceneStop(int eventId) implements CustomPacketPayload {
		public static final Type<CutsceneStop> TYPE = newType("cutscene_stop");
		public static final StreamCodec<ByteBuf, CutsceneStop> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, CutsceneStop::eventId, CutsceneStop::new);
		@Override public Type<CutsceneStop> type() { return TYPE; }
	}

	/** S2C: you are in Dead Space. eventAge = ticks since the destruction started (drives planet stages). */
	public record SpaceState(SpaceRecord record, int eventAge) implements CustomPacketPayload {
		public static final Type<SpaceState> TYPE = newType("space_state");
		public static final StreamCodec<RegistryFriendlyByteBuf, SpaceState> CODEC = StreamCodec.composite(
				ByteBufCodecs.fromCodecWithRegistries(SpaceRecord.CODEC), SpaceState::record, ByteBufCodecs.VAR_INT, SpaceState::eventAge, SpaceState::new);
		@Override public Type<SpaceState> type() { return TYPE; }
	}

	/** S2C: left Dead Space (debug return). Client drops the space scene. */
	public record SpaceLeave() implements CustomPacketPayload {
		public static final Type<SpaceLeave> TYPE = newType("space_leave");
		public static final StreamCodec<ByteBuf, SpaceLeave> CODEC = StreamCodec.unit(new SpaceLeave());
		@Override public Type<SpaceLeave> type() { return TYPE; }
	}

	/** C2S: Time stone rewind request. */
	public record TimeRewindRequest() implements CustomPacketPayload {
		public static final Type<TimeRewindRequest> TYPE = newType("time_rewind_request");
		public static final StreamCodec<ByteBuf, TimeRewindRequest> CODEC = StreamCodec.unit(new TimeRewindRequest());
		@Override public Type<TimeRewindRequest> type() { return TYPE; }
	}

	/** S2C: play rewind visuals. world = planet reassembly in Dead Space; entityId = who rewinds. */
	public record TimeRewindFx(int entityId, boolean world, int duration) implements CustomPacketPayload {
		public static final Type<TimeRewindFx> TYPE = newType("time_rewind_fx");
		public static final StreamCodec<ByteBuf, TimeRewindFx> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, TimeRewindFx::entityId, ByteBufCodecs.BOOL, TimeRewindFx::world, ByteBufCodecs.VAR_INT, TimeRewindFx::duration, TimeRewindFx::new);
		@Override public Type<TimeRewindFx> type() { return TYPE; }
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(TimeRewindRequest.TYPE, TimeRewindRequest.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TimeRewindFx.TYPE, TimeRewindFx.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ToggleMode.TYPE, ToggleMode.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PowerFx.TYPE, PowerFx.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(EffectDemo.TYPE, EffectDemo.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CutsceneStart.TYPE, CutsceneStart.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CutsceneStop.TYPE, CutsceneStop.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SpaceState.TYPE, SpaceState.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SpaceLeave.TYPE, SpaceLeave.CODEC);
	}

	private ModNetwork() {}
}
