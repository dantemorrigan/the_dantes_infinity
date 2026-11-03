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

	/** S2C: celestial event state (sent on change, periodically and on join). phase: 0 none, 1 fracture, 2 rain, 3 ending. */
	public record CelestialSync(int phase, String body, Vec3 center, int eventTicks, int rainRemainingMs, boolean sunDestroyed, boolean moonDestroyed)
			implements CustomPacketPayload {
		public static final Type<CelestialSync> TYPE = newType("celestial_sync");
		public static final StreamCodec<ByteBuf, CelestialSync> CODEC = new StreamCodec<>() {
			@Override
			public CelestialSync decode(ByteBuf buf) {
				return new CelestialSync(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), Vec3.STREAM_CODEC.decode(buf),
						ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.BOOL.decode(buf));
			}

			@Override
			public void encode(ByteBuf buf, CelestialSync v) {
				ByteBufCodecs.VAR_INT.encode(buf, v.phase);
				ByteBufCodecs.STRING_UTF8.encode(buf, v.body);
				Vec3.STREAM_CODEC.encode(buf, v.center);
				ByteBufCodecs.VAR_INT.encode(buf, v.eventTicks);
				ByteBufCodecs.VAR_INT.encode(buf, v.rainRemainingMs);
				ByteBufCodecs.BOOL.encode(buf, v.sunDestroyed);
				ByteBufCodecs.BOOL.encode(buf, v.moonDestroyed);
			}
		};
		@Override public Type<CelestialSync> type() { return TYPE; }
	}

	/** S2C: a physical meteor was launched (server decides where it lands). */
	public record MeteorSpawn(int id, Vec3 from, Vec3 to, int flightTicks, int size, boolean solar) implements CustomPacketPayload {
		public static final Type<MeteorSpawn> TYPE = newType("meteor_spawn");
		public static final StreamCodec<ByteBuf, MeteorSpawn> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, MeteorSpawn::id, Vec3.STREAM_CODEC, MeteorSpawn::from, Vec3.STREAM_CODEC, MeteorSpawn::to,
				ByteBufCodecs.VAR_INT, MeteorSpawn::flightTicks, ByteBufCodecs.VAR_INT, MeteorSpawn::size, ByteBufCodecs.BOOL, MeteorSpawn::solar, MeteorSpawn::new);
		@Override public Type<MeteorSpawn> type() { return TYPE; }
	}

	/** S2C: meteor hit the ground (visuals; the crater/fire/damage were done by the server). */
	public record MeteorImpact(int id, Vec3 pos, int size, int groundState) implements CustomPacketPayload {
		public static final Type<MeteorImpact> TYPE = newType("meteor_impact");
		public static final StreamCodec<ByteBuf, MeteorImpact> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, MeteorImpact::id, Vec3.STREAM_CODEC, MeteorImpact::pos, ByteBufCodecs.VAR_INT, MeteorImpact::size,
				ByteBufCodecs.VAR_INT, MeteorImpact::groundState, MeteorImpact::new);
		@Override public Type<MeteorImpact> type() { return TYPE; }
	}

	/** C2S: Space stone action. 0 = open/close portal, 1 = next destination, 2 = teleport where looking. */
	public record SpaceAction(int action) implements CustomPacketPayload {
		public static final Type<SpaceAction> TYPE = newType("space_action");
		public static final StreamCodec<ByteBuf, SpaceAction> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, SpaceAction::action, SpaceAction::new);
		@Override public Type<SpaceAction> type() { return TYPE; }
	}

	/** S2C: a portal is open at pos (center), facing yaw; dest = destination id; ticksLeft until it closes. */
	public record PortalOpen(int id, Vec3 pos, float yaw, String dest, int age, int ticksLeft) implements CustomPacketPayload {
		public static final Type<PortalOpen> TYPE = newType("portal_open");
		public static final StreamCodec<ByteBuf, PortalOpen> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, PortalOpen::id, Vec3.STREAM_CODEC, PortalOpen::pos, ByteBufCodecs.FLOAT, PortalOpen::yaw,
				ByteBufCodecs.STRING_UTF8, PortalOpen::dest, ByteBufCodecs.VAR_INT, PortalOpen::age, ByteBufCodecs.VAR_INT, PortalOpen::ticksLeft, PortalOpen::new);
		@Override public Type<PortalOpen> type() { return TYPE; }
	}

	public record PortalClose(int id) implements CustomPacketPayload {
		public static final Type<PortalClose> TYPE = newType("portal_close");
		public static final StreamCodec<ByteBuf, PortalClose> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, PortalClose::id, PortalClose::new);
		@Override public Type<PortalClose> type() { return TYPE; }
	}

	/** S2C: teleport visuals (flash at both ends + wisp trail). */
	public record SpaceFx(Vec3 from, Vec3 to) implements CustomPacketPayload {
		public static final Type<SpaceFx> TYPE = newType("space_fx");
		public static final StreamCodec<ByteBuf, SpaceFx> CODEC = StreamCodec.composite(Vec3.STREAM_CODEC, SpaceFx::from, Vec3.STREAM_CODEC, SpaceFx::to, SpaceFx::new);
		@Override public Type<SpaceFx> type() { return TYPE; }
	}

	/** C2S: H pressed/released. Server validates six stones and owns the full cast. */
	public record BeamAction(boolean start) implements CustomPacketPayload {
		public static final Type<BeamAction> TYPE = newType("beam_action");
		public static final StreamCodec<ByteBuf, BeamAction> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, BeamAction::start, BeamAction::new);
		@Override public Type<BeamAction> type() { return TYPE; }
	}
	/** S2C: authoritative beam endpoints; age=-1 ends the cast. */
	public record BeamFx(int entityId, Vec3 from, Vec3 to, int age, int charge, int duration) implements CustomPacketPayload {
		public static final Type<BeamFx> TYPE = newType("beam_fx");
		public static final StreamCodec<ByteBuf, BeamFx> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, BeamFx::entityId, Vec3.STREAM_CODEC, BeamFx::from, Vec3.STREAM_CODEC, BeamFx::to,
			ByteBufCodecs.VAR_INT, BeamFx::age, ByteBufCodecs.VAR_INT, BeamFx::charge, ByteBufCodecs.VAR_INT, BeamFx::duration, BeamFx::new);
		@Override public Type<BeamFx> type() { return TYPE; }
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(BeamAction.TYPE, BeamAction.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BeamFx.TYPE, BeamFx.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SpaceAction.TYPE, SpaceAction.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PortalOpen.TYPE, PortalOpen.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PortalClose.TYPE, PortalClose.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SpaceFx.TYPE, SpaceFx.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CelestialSync.TYPE, CelestialSync.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MeteorSpawn.TYPE, MeteorSpawn.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MeteorImpact.TYPE, MeteorImpact.CODEC);
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
