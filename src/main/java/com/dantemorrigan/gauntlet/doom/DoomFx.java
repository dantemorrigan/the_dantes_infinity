package com.dantemorrigan.gauntlet.doom;

import com.dantemorrigan.gauntlet.net.ModNetwork;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Small helpers shared by the boss, the Doombots and the armor: pack particles, sounds, lines, client events. */
public final class DoomFx {
	/** Client events. LOGO: the add-on's title card. CUTSCENE: letterbox bars for {@code ticks}. SHAKE: camera shake f. */
	public static final int LOGO = 1, CUTSCENE = 2, SHAKE = 3, FLASH = 4;
	/** Debug: play a theme now. id: 0 stop, 1 Echoes of Latveria, 2 Sovereign Sanctum, 3 the boss theme. */
	public static final int MUSIC = 5;

	/** A camera-facing pack effect; size 0 keeps the preset's own. */
	public static void bill(ServerLevel level, ParticleOptions type, Vec3 at, double size) {
		level.sendParticles(type, at.x, at.y, at.z, 0, size, 0, 0, 1);
	}

	/** A pack effect lying on the ground. */
	public static void flat(ServerLevel level, ParticleOptions type, Vec3 at, double size) {
		level.sendParticles(type, at.x, at.y + 0.08, at.z, 0, 0, size, 0, 1);
	}

	public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
	}

	public static void send(ServerPlayer player, int kind, int id, Vec3 at, float f, int ticks) {
		if (ServerPlayNetworking.canSend(player, ModNetwork.DoomFx.TYPE)) ServerPlayNetworking.send(player, new ModNetwork.DoomFx(kind, id, at, f, ticks));
	}

	public static void near(ServerLevel level, Vec3 at, double range, int kind, int id, float f, int ticks) {
		for (ServerPlayer p : level.players()) if (p.position().distanceToSqr(at) <= range * range) send(p, kind, id, at, f, ticks);
	}

	/** A spoken line, in chat. */
	public static void line(ServerLevel level, Vec3 at, double range, String key) {
		Component text = Component.translatable("msg.infinity_gauntlet.latveria." + key);
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(at) > range * range) continue;
			p.sendSystemMessage(text);
		}
	}

	public static void tell(ServerPlayer player, String key, Object... args) {
		player.sendSystemMessage(Component.translatable("msg.infinity_gauntlet.latveria." + key, args));
	}

	private DoomFx() {}
}
