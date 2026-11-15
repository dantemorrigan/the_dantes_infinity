package com.dantemorrigan.gauntlet.net;

import net.minecraft.core.BlockPos;
import java.util.List;
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

	/** C2S: Mind stone. 0 = grab / throw, 1 = (Shift) tear the held mob apart / drop the held blocks. */
	public record MindAction(int action) implements CustomPacketPayload {
		public static final Type<MindAction> TYPE = newType("mind_action");
		public static final StreamCodec<ByteBuf, MindAction> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, MindAction::action, MindAction::new);
		@Override public Type<MindAction> type() { return TYPE; }
	}

	/**
	 * S2C: Mind stone visuals. kind: 0 hold blocks (pos = origin centre, states/offsets = the cluster), 1 hold mob (id),
	 * 2 release, 3 throw (id = flight id, pos = start, vec = velocity, states/offsets), 4 impact (id = flight, pos),
	 * 5 tearing starts (id = mob), 6 rupture (id = mob, pos), 7 thrown mob hit (pos).
	 */
	public record MindFx(int kind, int ownerId, int id, Vec3 pos, Vec3 vec, List<Integer> states, List<BlockPos> offsets) implements CustomPacketPayload {
		public static final Type<MindFx> TYPE = newType("mind_fx");
		public static final StreamCodec<ByteBuf, MindFx> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, MindFx::kind, ByteBufCodecs.VAR_INT, MindFx::ownerId, ByteBufCodecs.VAR_INT, MindFx::id,
				Vec3.STREAM_CODEC, MindFx::pos, Vec3.STREAM_CODEC, MindFx::vec,
				ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), MindFx::states, BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), MindFx::offsets,
				MindFx::new);
		@Override public Type<MindFx> type() { return TYPE; }
	}

	/** C2S: Soul stone. 0 = extract the soul of the aimed mob, 1 = (Shift) mass extraction. */
	public record SoulAction(int action) implements CustomPacketPayload {
		public static final Type<SoulAction> TYPE = newType("soul_action");
		public static final StreamCodec<ByteBuf, SoulAction> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, SoulAction::action, SoulAction::new);
		@Override public Type<SoulAction> type() { return TYPE; }
	}

	/**
	 * S2C: Soul stone visuals. kind 0: a soul leaves targetId after delay ticks, is drawn out for extract ticks and flies
	 * to ownerId for flight ticks. kind 1: mass wave at pos, delay = radius.
	 */
	public record SoulFx(int kind, int ownerId, int targetId, Vec3 pos, int delay, int extract, int flight) implements CustomPacketPayload {
		public static final Type<SoulFx> TYPE = newType("soul_fx");
		public static final StreamCodec<ByteBuf, SoulFx> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, SoulFx::kind, ByteBufCodecs.VAR_INT, SoulFx::ownerId, ByteBufCodecs.VAR_INT, SoulFx::targetId,
				Vec3.STREAM_CODEC, SoulFx::pos, ByteBufCodecs.VAR_INT, SoulFx::delay, ByteBufCodecs.VAR_INT, SoulFx::extract,
				ByteBufCodecs.VAR_INT, SoulFx::flight, SoulFx::new);
		@Override public Type<SoulFx> type() { return TYPE; }
	}

	/** S2C: absorbed souls of the receiving player and the remaining buff time. */
	public record SoulBuff(int souls, int ticks) implements CustomPacketPayload {
		public static final Type<SoulBuff> TYPE = newType("soul_buff");
		public static final StreamCodec<ByteBuf, SoulBuff> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, SoulBuff::souls, ByteBufCodecs.VAR_INT, SoulBuff::ticks, SoulBuff::new);
		@Override public Type<SoulBuff> type() { return TYPE; }
	}

	/** C2S: Time stone rewind request. */
	public record TimeRewindRequest() implements CustomPacketPayload {
		public static final Type<TimeRewindRequest> TYPE = newType("time_rewind_request");
		public static final StreamCodec<ByteBuf, TimeRewindRequest> CODEC = StreamCodec.unit(new TimeRewindRequest());
		@Override public Type<TimeRewindRequest> type() { return TYPE; }
	}

	/** C2S: Time stone day/night switch request. */
	public record TimeShiftRequest() implements CustomPacketPayload {
		public static final Type<TimeShiftRequest> TYPE = newType("time_shift_request");
		public static final StreamCodec<ByteBuf, TimeShiftRequest> CODEC = StreamCodec.unit(new TimeShiftRequest());
		@Override public Type<TimeShiftRequest> type() { return TYPE; }
	}

	/** S2C: day/night switch visuals. */
	public record TimeShiftFx(int duration, boolean toNight) implements CustomPacketPayload {
		public static final Type<TimeShiftFx> TYPE = newType("time_shift_fx");
		public static final StreamCodec<ByteBuf, TimeShiftFx> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, TimeShiftFx::duration, ByteBufCodecs.BOOL, TimeShiftFx::toNight, TimeShiftFx::new);
		@Override public Type<TimeShiftFx> type() { return TYPE; }
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

	/** S2C: the server's values of the rules the client uses for its own visuals and local block changes (join, config reload). */
	public record RulesSync(boolean abilityBlockChanges, int realityRadius, int realityMaxBlocks, int redSkyRadius, int redSkyFalloff,
			float celestialAimSeconds, double soulHealthPerSoul, double soulDamagePerSoul) implements CustomPacketPayload {
		public static final Type<RulesSync> TYPE = newType("rules_sync");
		public static final StreamCodec<ByteBuf, RulesSync> CODEC = new StreamCodec<>() {
			@Override
			public RulesSync decode(ByteBuf buf) {
				return new RulesSync(buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
						buf.readFloat(), buf.readDouble(), buf.readDouble());
			}

			@Override
			public void encode(ByteBuf buf, RulesSync v) {
				buf.writeBoolean(v.abilityBlockChanges);
				buf.writeInt(v.realityRadius);
				buf.writeInt(v.realityMaxBlocks);
				buf.writeInt(v.redSkyRadius);
				buf.writeInt(v.redSkyFalloff);
				buf.writeFloat(v.celestialAimSeconds);
				buf.writeDouble(v.soulHealthPerSoul);
				buf.writeDouble(v.soulDamagePerSoul);
			}
		};
		@Override public Type<RulesSync> type() { return TYPE; }
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

	/** The Latveria apparition looks at the player who opened its portal. */
	public record LatveriaLook(int portalId, int playerId) implements CustomPacketPayload {
		public static final Type<LatveriaLook> TYPE = newType("latveria_look");
		public static final StreamCodec<ByteBuf, LatveriaLook> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, LatveriaLook::portalId, ByteBufCodecs.VAR_INT, LatveriaLook::playerId, LatveriaLook::new);
		@Override public Type<LatveriaLook> type() { return TYPE; }
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

	/** C2S: the player plays the "admire the gauntlet" hand gesture (lift and slightly turn the gauntlet). */
	public record GestureStart() implements CustomPacketPayload {
		public static final Type<GestureStart> TYPE = newType("gesture_start");
		public static final StreamCodec<ByteBuf, GestureStart> CODEC = StreamCodec.unit(new GestureStart());
		@Override public Type<GestureStart> type() { return TYPE; }
	}

	/** S2C: entityId starts a gauntlet hand gesture: kind 0 = admire (turn the gauntlet), 1 = the Infinity Snap. */
	public record GestureFx(int entityId, int kind) implements CustomPacketPayload {
		public static final Type<GestureFx> TYPE = newType("gesture_fx");
		public static final StreamCodec<ByteBuf, GestureFx> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, GestureFx::entityId, ByteBufCodecs.VAR_INT, GestureFx::kind, GestureFx::new);
		@Override public Type<GestureFx> type() { return TYPE; }
	}

	/**
	 * S2C: Infinity Snap screen effects. phase 0 = the universe fades to white over duration ticks, 1 = arrived in the
	 * White Void, 2 = the Time stone rewind started (duration), 3 = back in the restored world.
	 */
	public record SnapFx(int phase, int duration) implements CustomPacketPayload {
		public static final Type<SnapFx> TYPE = newType("snap_fx");
		public static final StreamCodec<ByteBuf, SnapFx> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, SnapFx::phase, ByteBufCodecs.VAR_INT, SnapFx::duration, SnapFx::new);
		@Override public Type<SnapFx> type() { return TYPE; }
	}

	/**
	 * S2C: Infinity Extractor visuals. kind: 0 = a cycle starts (tether from the target to the extractor),
	 * 1 = cycle done (cycles = how many are done), 2 = cycle failed / released, 3 = the stone is in use (blocked),
	 * 4 = the stone is captured into the extractor, 5 = the stone was returned into a gauntlet.
	 */
	public record ExtractorFx(int kind, int ownerId, int targetId, int stone, int cycles) implements CustomPacketPayload {
		public static final Type<ExtractorFx> TYPE = newType("extractor_fx");
		public static final StreamCodec<ByteBuf, ExtractorFx> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, ExtractorFx::kind, ByteBufCodecs.VAR_INT, ExtractorFx::ownerId, ByteBufCodecs.VAR_INT, ExtractorFx::targetId,
				ByteBufCodecs.VAR_INT, ExtractorFx::stone, ByteBufCodecs.VAR_INT, ExtractorFx::cycles, ExtractorFx::new);
		@Override public Type<ExtractorFx> type() { return TYPE; }
	}

	/**
	 * S2C: Meteor Stream (Power stone). kind: 0 = aim marker (sent to the caster only; valid = the ground point is usable),
	 * 1 = a stream starts or is re-announced to players near it (ticks = time left), 2 = a stream ended (center identifies it),
	 * 3 = the aim marker is off.
	 */
	public record MeteorStreamFx(int kind, int ownerId, boolean solar, Vec3 center, float radius, int ticks, boolean valid) implements CustomPacketPayload {
		public static final Type<MeteorStreamFx> TYPE = newType("meteor_stream_fx");
		public static final StreamCodec<ByteBuf, MeteorStreamFx> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, MeteorStreamFx::kind, ByteBufCodecs.VAR_INT, MeteorStreamFx::ownerId, ByteBufCodecs.BOOL, MeteorStreamFx::solar,
				Vec3.STREAM_CODEC, MeteorStreamFx::center, ByteBufCodecs.FLOAT, MeteorStreamFx::radius, ByteBufCodecs.VAR_INT, MeteorStreamFx::ticks,
				ByteBufCodecs.BOOL, MeteorStreamFx::valid, MeteorStreamFx::new);
		@Override public Type<MeteorStreamFx> type() { return TYPE; }
	}

	/** S2C: the Uru runes of entityId absorbed a full-gauntlet hit (rune ring flash on the chest). */
	public record UruGuardFx(int entityId) implements CustomPacketPayload {
		public static final Type<UruGuardFx> TYPE = newType("uru_guard_fx");
		public static final StreamCodec<ByteBuf, UruGuardFx> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, UruGuardFx::entityId, UruGuardFx::new);
		@Override public Type<UruGuardFx> type() { return TYPE; }
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

	/** S2C: the Time Stone beam has no charge phase and a separate visual style. */
	public record TimeFlowFx(BeamFx beam) implements CustomPacketPayload {
		public static final Type<TimeFlowFx> TYPE = newType("time_flow_fx");
		public static final StreamCodec<ByteBuf, TimeFlowFx> CODEC = StreamCodec.composite(
			BeamFx.CODEC, TimeFlowFx::beam, TimeFlowFx::new);
		@Override public Type<TimeFlowFx> type() { return TYPE; }
	}

	/** A HUD notice. It travels as a component so translations are resolved by the CLIENT (dedicated servers have no mod lang files). */
	/** S2C: visibility and shared world-space center of the Reality Stone mist. */
	public record RealityFogFx(boolean on, Vec3 center) implements CustomPacketPayload {
		public static final Type<RealityFogFx> TYPE = newType("reality_fog");
		public static final StreamCodec<ByteBuf, RealityFogFx> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, RealityFogFx::on,
				Vec3.STREAM_CODEC, RealityFogFx::center, RealityFogFx::new);
		@Override public Type<RealityFogFx> type() { return TYPE; }
	}

	/** S2C: Soul quest. kind 0 = the ritual at {@code pos} (summit height {@code top}, lasts {@code ticks}); kind 1 = arrival in the soul world (fade in). */
	public record SoulRitualFx(int kind, net.minecraft.core.BlockPos pos, int top, int ticks) implements CustomPacketPayload {
		public static final Type<SoulRitualFx> TYPE = newType("soul_ritual");
		public static final StreamCodec<ByteBuf, SoulRitualFx> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, SoulRitualFx::kind,
				net.minecraft.core.BlockPos.STREAM_CODEC, SoulRitualFx::pos, ByteBufCodecs.VAR_INT, SoulRitualFx::top, ByteBufCodecs.VAR_INT, SoulRitualFx::ticks, SoulRitualFx::new);
		@Override public Type<SoulRitualFx> type() { return TYPE; }
	}

	/** S2C: the Temple of Soul's site, and whether its Vormir light still holds (the Soul Stone has not been taken yet). */
	public record SoulVormir(net.minecraft.core.BlockPos site, boolean active) implements CustomPacketPayload {
		public static final Type<SoulVormir> TYPE = newType("soul_vormir");
		public static final StreamCodec<ByteBuf, SoulVormir> CODEC = StreamCodec.composite(net.minecraft.core.BlockPos.STREAM_CODEC, SoulVormir::site,
				ByteBufCodecs.BOOL, SoulVormir::active, SoulVormir::new);
		@Override public Type<SoulVormir> type() { return TYPE; }
	}

	/** S2C: a framed quest notice in the middle of the screen (Space Stone quest). */
	public record QuestAlert(net.minecraft.network.chat.Component text, int style) implements CustomPacketPayload {
		/** style: 0 = Space (blue frame), 1 = Soul (amber frame). */
		public QuestAlert(net.minecraft.network.chat.Component text) { this(text, 0); }
		public static final Type<QuestAlert> TYPE = newType("quest_alert");
		public static final StreamCodec<RegistryFriendlyByteBuf, QuestAlert> CODEC = StreamCodec.composite(
				net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC, QuestAlert::text, ByteBufCodecs.VAR_INT, QuestAlert::style, QuestAlert::new);
		@Override public Type<QuestAlert> type() { return TYPE; }
	}

	public record Status(net.minecraft.network.chat.Component text) implements CustomPacketPayload {
		public static final Type<Status> TYPE = newType("status");
		public static final StreamCodec<RegistryFriendlyByteBuf, Status> CODEC = StreamCodec.composite(
				net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC, Status::text, Status::new);
		@Override public Type<Status> type() { return TYPE; }
	}

	/** C2S: Reality stone fist closed (true) / opened (false). */
	public record RealityHold(boolean closed) implements CustomPacketPayload {
		public static final Type<RealityHold> TYPE = newType("reality_hold");
		public static final StreamCodec<ByteBuf, RealityHold> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, RealityHold::closed, RealityHold::new);
		@Override public Type<RealityHold> type() { return TYPE; }
	}

	/** C2S: short tap of the Reality key — strike the ground with the Reality stone. */
	public record RealityStrike() implements CustomPacketPayload {
		public static final Type<RealityStrike> TYPE = newType("reality_strike");
		public static final StreamCodec<ByteBuf, RealityStrike> CODEC = StreamCodec.unit(new RealityStrike());
		@Override public Type<RealityStrike> type() { return TYPE; }
	}

	/** S2C: Reality Inversion started/ended for an entity, around center. */
	public record RealityState(int entityId, boolean active, Vec3 center, int age, float yaw) implements CustomPacketPayload {
		public static final Type<RealityState> TYPE = newType("reality_state");
		public static final StreamCodec<ByteBuf, RealityState> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, RealityState::entityId, ByteBufCodecs.BOOL, RealityState::active, Vec3.STREAM_CODEC, RealityState::center,
				ByteBufCodecs.VAR_INT, RealityState::age, ByteBufCodecs.FLOAT, RealityState::yaw, RealityState::new);
		@Override public Type<RealityState> type() { return TYPE; }
	}

	/** S2C: kind 0 = mob became butterflies, 1 = projectile became petals, 2 = butterflies became a mob again. */
	public record RealityFx(int kind, Vec3 pos, Vec3 vel) implements CustomPacketPayload {
		public static final Type<RealityFx> TYPE = newType("reality_fx");
		public static final StreamCodec<ByteBuf, RealityFx> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, RealityFx::kind, Vec3.STREAM_CODEC, RealityFx::pos, Vec3.STREAM_CODEC, RealityFx::vel, RealityFx::new);
		@Override public Type<RealityFx> type() { return TYPE; }
	}

    /** C2S: toggle flight/return, next destination, warp. No client coordinates accepted. */
    public record CosmosAction(int action) implements CustomPacketPayload {
        public static final Type<CosmosAction> TYPE = newType("cosmos_action");
        public static final StreamCodec<ByteBuf,CosmosAction> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT,CosmosAction::action,CosmosAction::new);
        @Override public Type<CosmosAction> type() { return TYPE; }
    }
    public record CosmosState(boolean enabled,int destination,int warpTicks,int cooldown) implements CustomPacketPayload {
        public static final Type<CosmosState> TYPE = newType("cosmos_state");
        public static final StreamCodec<ByteBuf,CosmosState> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,CosmosState::enabled,ByteBufCodecs.VAR_INT,CosmosState::destination,
            ByteBufCodecs.VAR_INT,CosmosState::warpTicks,ByteBufCodecs.VAR_INT,CosmosState::cooldown,CosmosState::new);
        @Override public Type<CosmosState> type() { return TYPE; }
    }

    /** S2C: sonic transition (0) or physical flight impact (1), validated by the server. */
    public record FlightFx(int entityId,int kind,Vec3 pos,Vec3 direction,float strength) implements CustomPacketPayload {
        public static final Type<FlightFx> TYPE=newType("flight_fx");
        public static final StreamCodec<ByteBuf,FlightFx> CODEC=StreamCodec.composite(
            ByteBufCodecs.VAR_INT,FlightFx::entityId,ByteBufCodecs.VAR_INT,FlightFx::kind,
            Vec3.STREAM_CODEC,FlightFx::pos,Vec3.STREAM_CODEC,FlightFx::direction,ByteBufCodecs.FLOAT,FlightFx::strength,FlightFx::new);
        @Override public Type<FlightFx> type() { return TYPE; }
    }

    public record AbilitySelect(String id,int destination) implements CustomPacketPayload {
        public static final Type<AbilitySelect> TYPE=newType("ability_select");
        public static final StreamCodec<ByteBuf,AbilitySelect> CODEC=StreamCodec.composite(ByteBufCodecs.STRING_UTF8,AbilitySelect::id,ByteBufCodecs.VAR_INT,AbilitySelect::destination,AbilitySelect::new);
        @Override public Type<AbilitySelect> type() { return TYPE; }
    }
    public record AbilityAction(int action) implements CustomPacketPayload {
        public static final Type<AbilityAction> TYPE=newType("ability_action");
        public static final StreamCodec<ByteBuf,AbilityAction> CODEC=StreamCodec.composite(ByteBufCodecs.VAR_INT,AbilityAction::action,AbilityAction::new);
        @Override public Type<AbilityAction> type() { return TYPE; }
    }
    public record AbilityState(String selected,String states,int holding,int charge,int route,int portal) implements CustomPacketPayload {
        public static final Type<AbilityState> TYPE=newType("ability_state");
        public static final StreamCodec<ByteBuf,AbilityState> CODEC=StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,AbilityState::selected,ByteBufCodecs.STRING_UTF8,AbilityState::states,
            ByteBufCodecs.VAR_INT,AbilityState::holding,ByteBufCodecs.VAR_INT,AbilityState::charge,
            ByteBufCodecs.VAR_INT,AbilityState::route,ByteBufCodecs.VAR_INT,AbilityState::portal,AbilityState::new);
        @Override public Type<AbilityState> type() { return TYPE; }
    }

	/**
	 * S2C: Thanos scene effects. kind: see {@code ThanosEncounter.FX_*}. a/b = positions, f = yaw / scale, ticks = duration,
	 * id = entity id or line index or dimension kind, depending on the kind.
	 */
	public record ThanosFx(int kind, int id, Vec3 a, Vec3 b, float f, int ticks) implements CustomPacketPayload {
		public static final Type<ThanosFx> TYPE = newType("thanos_fx");
		public static final StreamCodec<ByteBuf, ThanosFx> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, ThanosFx::kind,
				ByteBufCodecs.VAR_INT, ThanosFx::id, Vec3.STREAM_CODEC, ThanosFx::a, Vec3.STREAM_CODEC, ThanosFx::b,
				ByteBufCodecs.FLOAT, ThanosFx::f, ByteBufCodecs.VAR_INT, ThanosFx::ticks, ThanosFx::new);
		@Override public Type<ThanosFx> type() { return TYPE; }
	}

	/** S2C: a Power meteor shower starts around {@code center}: the sky and the sun redden for {@code ticks}. */
	public record PowerShowerFx(BlockPos center, int ticks) implements CustomPacketPayload {
		public static final Type<PowerShowerFx> TYPE = newType("power_shower");
		public static final StreamCodec<ByteBuf, PowerShowerFx> CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, PowerShowerFx::center,
				ByteBufCodecs.VAR_INT, PowerShowerFx::ticks, PowerShowerFx::new);
		@Override public Type<PowerShowerFx> type() { return TYPE; }
	}

    /** A finite, server simulated energy bolt; zero duration removes an impacted bolt. */
    public record TribunalBolt(int id, Vec3 from, Vec3 to, int ticks) implements CustomPacketPayload {
        public static final Type<TribunalBolt> TYPE = newType("tribunal_bolt");
        public static final StreamCodec<ByteBuf, TribunalBolt> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT,TribunalBolt::id,
            Vec3.STREAM_CODEC,TribunalBolt::from,Vec3.STREAM_CODEC,TribunalBolt::to,ByteBufCodecs.VAR_INT,TribunalBolt::ticks,TribunalBolt::new);
        @Override public Type<TribunalBolt> type() { return TYPE; }
    }

    public record CosmicFx(int kind,Vec3 from,Vec3 to,float size,int color,int ticks) implements CustomPacketPayload {
        public static final Type<CosmicFx> TYPE=newType("cosmic_gauntlet_fx");
        public static final StreamCodec<ByteBuf,CosmicFx> CODEC=StreamCodec.composite(ByteBufCodecs.VAR_INT,CosmicFx::kind,
            Vec3.STREAM_CODEC,CosmicFx::from,Vec3.STREAM_CODEC,CosmicFx::to,ByteBufCodecs.FLOAT,CosmicFx::size,
            ByteBufCodecs.INT,CosmicFx::color,ByteBufCodecs.VAR_INT,CosmicFx::ticks,CosmicFx::new);
        @Override public Type<CosmicFx> type() {return TYPE;}
    }

	/** S2C: a one-off Latveria / Doctor Doom event on the client; kinds are {@code doom.DoomFx.*}. */
	public record DoomFx(int kind, int id, Vec3 at, float f, int ticks) implements CustomPacketPayload {
		public static final Type<DoomFx> TYPE = newType("doom_fx");
		public static final StreamCodec<ByteBuf, DoomFx> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, DoomFx::kind,
				ByteBufCodecs.VAR_INT, DoomFx::id, Vec3.STREAM_CODEC, DoomFx::at, ByteBufCodecs.FLOAT, DoomFx::f,
				ByteBufCodecs.VAR_INT, DoomFx::ticks, DoomFx::new);
		@Override public Type<DoomFx> type() { return TYPE; }
	}

	/** S2C: the Doom armor's state for the wheel and the HUD: "set;jet;c0,c1,c2,c3,c4,c5" (cooldowns in ticks). */
	public record DoomSync(String state) implements CustomPacketPayload {
		public static final Type<DoomSync> TYPE = newType("doom_sync");
		public static final StreamCodec<ByteBuf, DoomSync> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, DoomSync::state, DoomSync::new);
		@Override public Type<DoomSync> type() { return TYPE; }
	}

	/**
	 * S2C: one of Doom's world effects (kinds in {@code doom.DoomVfx}). The client simulates moving ones itself
	 * (bolts fly, shields and jets follow their entity), so nothing is sent per tick.
	 */
	public record DoomVfx(int kind, int id, int entity, int target, Vec3 a, Vec3 b, float size, float turn, int ticks, int style) implements CustomPacketPayload {
		public static final Type<DoomVfx> TYPE = newType("doom_vfx");
		public static final StreamCodec<ByteBuf, DoomVfx> CODEC = StreamCodec.of((buf, v) -> {
			ByteBufCodecs.VAR_INT.encode(buf, v.kind); ByteBufCodecs.VAR_INT.encode(buf, v.id);
			ByteBufCodecs.VAR_INT.encode(buf, v.entity); ByteBufCodecs.VAR_INT.encode(buf, v.target + 1);
			Vec3.STREAM_CODEC.encode(buf, v.a); Vec3.STREAM_CODEC.encode(buf, v.b);
			buf.writeFloat(v.size); buf.writeFloat(v.turn);
			ByteBufCodecs.VAR_INT.encode(buf, v.ticks); ByteBufCodecs.VAR_INT.encode(buf, v.style);
		}, buf -> new DoomVfx(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
				ByteBufCodecs.VAR_INT.decode(buf) - 1, Vec3.STREAM_CODEC.decode(buf), Vec3.STREAM_CODEC.decode(buf), buf.readFloat(), buf.readFloat(),
				ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));
		@Override public Type<DoomVfx> type() { return TYPE; }
	}

	/** C2S: the Doom armor's jets: on or off (the client flies, the server keeps the fuel and the fall damage). */
	public record DoomJet(boolean on) implements CustomPacketPayload {
		public static final Type<DoomJet> TYPE = newType("doom_jet");
		public static final StreamCodec<ByteBuf, DoomJet> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, DoomJet::on, DoomJet::new);
		@Override public Type<DoomJet> type() { return TYPE; }
	}

	/** C2S: cast an ability of the Doom armor (0..5). */
	public record DoomCast(int ability) implements CustomPacketPayload {
		public static final Type<DoomCast> TYPE = newType("doom_cast");
		public static final StreamCodec<ByteBuf, DoomCast> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, DoomCast::ability, DoomCast::new);
		@Override public Type<DoomCast> type() { return TYPE; }
	}

	/** S2C: opens the RPG dialog screen for a Latverian NPC (Old Mechanic Radovan, Tavern Keeper Mirko, or a citizen). */
	public record LatveriaDialogOpen(int entityId, int role, int flags) implements CustomPacketPayload {
		public static final Type<LatveriaDialogOpen> TYPE = newType("latveria_dialog_open");
		public static final StreamCodec<ByteBuf, LatveriaDialogOpen> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, LatveriaDialogOpen::entityId,
				ByteBufCodecs.VAR_INT, LatveriaDialogOpen::role,
				ByteBufCodecs.VAR_INT, LatveriaDialogOpen::flags,
				LatveriaDialogOpen::new);
		@Override public Type<LatveriaDialogOpen> type() { return TYPE; }
	}

	/** C2S: executes a quest action chosen in the Latverian RPG dialog screen. */
	public record LatveriaDialogAction(int entityId, int actionId) implements CustomPacketPayload {
		public static final Type<LatveriaDialogAction> TYPE = newType("latveria_dialog_action");
		public static final StreamCodec<ByteBuf, LatveriaDialogAction> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, LatveriaDialogAction::entityId,
				ByteBufCodecs.VAR_INT, LatveriaDialogAction::actionId,
				LatveriaDialogAction::new);
		@Override public Type<LatveriaDialogAction> type() { return TYPE; }
	}

	/**
	 * S2C: syncs the Overworld Doom-with-Gauntlet final encounter (sandy-fiery arena compass, Thanos vs Doom dome duel,
	 * cutscenes, and gauntlet return animation).
	 */
	public record DoomFinalSync(int stage, int ticks, Vec3 center, int doomId, int thanosId, int stoneMask, int recipientId) implements CustomPacketPayload {
		public static final Type<DoomFinalSync> TYPE = newType("doom_final_sync_v2");
		public static final StreamCodec<ByteBuf, DoomFinalSync> CODEC = StreamCodec.of((buf, payload) -> {
			ByteBufCodecs.VAR_INT.encode(buf, payload.stage());
			ByteBufCodecs.VAR_INT.encode(buf, payload.ticks());
			Vec3.STREAM_CODEC.encode(buf, payload.center());
			ByteBufCodecs.VAR_INT.encode(buf, payload.doomId());
			ByteBufCodecs.VAR_INT.encode(buf, payload.thanosId());
			ByteBufCodecs.VAR_INT.encode(buf, payload.stoneMask());
			ByteBufCodecs.VAR_INT.encode(buf, payload.recipientId());
		}, buf -> new DoomFinalSync(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
				Vec3.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
				ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));
		@Override public Type<DoomFinalSync> type() { return TYPE; }
	}

	/** C2S: player shatters the energy dome over Doom & Thanos using Stormbreaker. */
	public record DoomBreakDome() implements CustomPacketPayload {
		public static final Type<DoomBreakDome> TYPE = newType("doom_break_dome");
		public static final StreamCodec<ByteBuf, DoomBreakDome> CODEC = StreamCodec.unit(new DoomBreakDome());
		@Override public Type<DoomBreakDome> type() { return TYPE; }
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(LatveriaDialogOpen.TYPE, LatveriaDialogOpen.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(LatveriaDialogAction.TYPE, LatveriaDialogAction.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DoomFinalSync.TYPE, DoomFinalSync.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DoomBreakDome.TYPE, DoomBreakDome.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DoomFx.TYPE, DoomFx.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DoomSync.TYPE, DoomSync.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DoomCast.TYPE, DoomCast.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DoomVfx.TYPE, DoomVfx.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DoomJet.TYPE, DoomJet.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CosmicFx.TYPE,CosmicFx.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TribunalBolt.TYPE,TribunalBolt.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PowerShowerFx.TYPE, PowerShowerFx.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AbilitySelect.TYPE,AbilitySelect.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AbilityAction.TYPE,AbilityAction.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AbilityState.TYPE,AbilityState.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FlightFx.TYPE,FlightFx.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CosmosAction.TYPE,CosmosAction.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CosmosState.TYPE,CosmosState.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RealityHold.TYPE, RealityHold.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RealityStrike.TYPE, RealityStrike.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RealityState.TYPE, RealityState.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RealityFx.TYPE, RealityFx.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Status.TYPE, Status.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(QuestAlert.TYPE, QuestAlert.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SoulRitualFx.TYPE, SoulRitualFx.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SoulVormir.TYPE, SoulVormir.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ThanosFx.TYPE, ThanosFx.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RealityFogFx.TYPE, RealityFogFx.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(BeamAction.TYPE, BeamAction.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BeamFx.TYPE, BeamFx.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TimeFlowFx.TYPE, TimeFlowFx.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SpaceAction.TYPE, SpaceAction.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PortalOpen.TYPE, PortalOpen.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PortalClose.TYPE, PortalClose.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LatveriaLook.TYPE, LatveriaLook.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SpaceFx.TYPE, SpaceFx.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CelestialSync.TYPE, CelestialSync.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RulesSync.TYPE, RulesSync.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MeteorSpawn.TYPE, MeteorSpawn.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MeteorImpact.TYPE, MeteorImpact.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TimeRewindRequest.TYPE, TimeRewindRequest.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MindAction.TYPE, MindAction.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MindFx.TYPE, MindFx.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SoulAction.TYPE, SoulAction.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SoulFx.TYPE, SoulFx.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SoulBuff.TYPE, SoulBuff.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TimeRewindFx.TYPE, TimeRewindFx.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TimeShiftRequest.TYPE, TimeShiftRequest.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TimeShiftFx.TYPE, TimeShiftFx.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ToggleMode.TYPE, ToggleMode.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(GestureStart.TYPE, GestureStart.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(GestureFx.TYPE, GestureFx.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SnapFx.TYPE, SnapFx.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ExtractorFx.TYPE, ExtractorFx.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MeteorStreamFx.TYPE, MeteorStreamFx.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(UruGuardFx.TYPE, UruGuardFx.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PowerFx.TYPE, PowerFx.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(EffectDemo.TYPE, EffectDemo.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CutsceneStart.TYPE, CutsceneStart.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CutsceneStop.TYPE, CutsceneStop.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SpaceState.TYPE, SpaceState.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SpaceLeave.TYPE, SpaceLeave.CODEC);
	}

	private ModNetwork() {}
}

