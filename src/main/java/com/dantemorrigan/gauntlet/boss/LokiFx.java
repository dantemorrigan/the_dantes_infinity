package com.dantemorrigan.gauntlet.boss;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public record LokiFx(Effect effect, Vec3 position, float yaw) implements CustomPacketPayload {
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
		MIND_STONE_REVEAL(46, true, 2.35f, 1.2f);

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
			},
			buf -> new LokiFx(buf.readEnum(Effect.class), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat()));

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	public static void send(ServerLevel level, Effect effect, Vec3 at, float yaw) {
		LokiFx payload = new LokiFx(effect, at, yaw);
		for (var player : level.players()) {
			if (player.distanceToSqr(at) <= 80 * 80 && ServerPlayNetworking.canSend(player, TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	@Override
	public Type<LokiFx> type() {
		return TYPE;
	}
}
