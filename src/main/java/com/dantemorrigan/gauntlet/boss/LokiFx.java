package com.dantemorrigan.gauntlet.boss;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public record LokiFx(Effect effect, Vec3 position, float yaw, int recipient) implements CustomPacketPayload {
	public enum Effect {
		GOLD_WORLD_WASH(85, false, 14, 0),
		PORTAL_OPEN(22, false, 4.65f, 2.1f),
		PORTAL_LOOP(42, true, 4.65f, 2.1f),
		TELEPORT_OUT(9, false, 2.8f, 1.25f),
		TELEPORT_IN(9, false, 2.8f, 1.25f),
		ILLUSION_SPAWN(11, false, 2.85f, 1.25f),
		ILLUSION_BREAK(10, false, 2.85f, 1.25f),
		DAGGER_SLASH(8, false, 3.2f, 1.1f),
		RAGE_PULSE(14, false, 4, 1.1f),
		ESSENCE_COLLECT(22, false, 4.2f, 1.2f),
		MIND_STONE_REVEAL(52, false, 2.35f, 0),
		WARD(40, true, 2.8f, 1.25f),
		SPELL_MARK(15, false, 3.5f, 0),
		BIND_MARK(18, false, 4.7f, 0),
		TRANSFORM(28, false, 8.1f, 2.1f),
		ROAR_WARN(24, false, 24, .12f),
		ROAR(20, false, 24, .18f),
		PAW_WARN(17, false, 13, .12f),
		CLAW(12, false, 7.3f, 1.35f),
		FANGS(11, false, 5.2f, 1.28f),
		BARRAGE_MARK(24, false, 12.2f, .08f),
		BARRAGE_IMPACT(14, false, 9.6f, .14f);

		public final int ticks;
		public final boolean loop;
		public final float size, yOffset;

		Effect(int ticks, boolean loop, float size, float yOffset) {
			this.ticks = ticks;
			this.loop = loop;
			this.size = size;
			this.yOffset = yOffset;
		}
	}

	public static final Type<LokiFx> TYPE = new Type<>(InfinityGauntlet.id("loki_fx"));
	private static final StreamCodec<RegistryFriendlyByteBuf, LokiFx> CODEC = StreamCodec.of(
			(buf, fx) -> {
				buf.writeEnum(fx.effect);
				buf.writeDouble(fx.position.x);
				buf.writeDouble(fx.position.y);
				buf.writeDouble(fx.position.z);
				buf.writeFloat(fx.yaw);
				buf.writeVarInt(fx.recipient);
			},
			buf -> new LokiFx(buf.readEnum(Effect.class), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readVarInt()));

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	public static void send(ServerLevel level, Effect effect, Vec3 at, float yaw) {
		broadcast(level, new LokiFx(effect, at, yaw, -1));
	}

	public static void ward(ServerLevel level, Loki loki) {
		broadcast(level, new LokiFx(Effect.WARD, loki.position(), 0, loki.getId()));
	}

	public static void reward(ServerLevel level, Vec3 at, ServerPlayer player) {
		broadcast(level, new LokiFx(Effect.MIND_STONE_REVEAL, at, 0, player.getId()));
	}

	public static void beam(ServerLevel level, Vec3 start, Vec3 end) {
		DustParticleOptions gold = new DustParticleOptions(0xffdb5b, 1.55f);
		DustParticleOptions emerald = new DustParticleOptions(0x48ef88, 1.15f);
		int segments = Math.min(48, (int) (start.distanceTo(end) * 2) + 1);
		for (int i = 0; i <= segments; i++) {
			Vec3 point = start.lerp(end, i / (double) segments);
			level.sendParticles(i % 3 == 0 ? gold : emerald, point.x, point.y, point.z, 1, .1, .12, .1, 0);
		}
		level.sendParticles(gold, end.x, end.y, end.z, 28, .42, .58, .42, 0);
	}

	private static void broadcast(ServerLevel level, LokiFx payload) {
		for (var player : level.players()) {
			if (player.distanceToSqr(payload.position()) <= 80 * 80 && ServerPlayNetworking.canSend(player, TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	@Override
	public Type<LokiFx> type() {
		return TYPE;
	}
}
