package com.dantemorrigan.gauntlet.doom;

import com.dantemorrigan.gauntlet.ModParticles;
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
	/** The castle searchlights: f 1 burning, 0 out. ALARM: they hold on entity {@code id} and burn red for {@code ticks}. */
	public static final int LIGHTS = 6, ALARM = 7;
	/** The ascension of God Emperor Doom: id is Doom's entity id, ticks the scene's length. */
	public static final int GOD = 8;

	/**
	 * An effect at a point. The pack's flat sprites hung in the air and lagged, so the bursts, rifts, waves and
	 * columns are played by {@link DoomVfx} as real geometry; only decorative sprites (the gate, the time platform)
	 * stay particles. {@code size} is a multiplier (0 = the usual size).
	 */
	public static void bill(ServerLevel level, ParticleOptions type, Vec3 at, double size) {
		if (!asVfx(level, type, at, size <= 0 ? 1 : (float) size, false)) level.sendParticles(type, at.x, at.y, at.z, 0, size, 0, 0, 1);
	}

	/** The same, lying on the ground. */
	public static void flat(ServerLevel level, ParticleOptions type, Vec3 at, double size) {
		if (!asVfx(level, type, at, size <= 0 ? 1 : (float) size, true)) level.sendParticles(type, at.x, at.y + 0.08, at.z, 0, 0, size, 0, 1);
	}

	private static boolean asVfx(ServerLevel level, ParticleOptions type, Vec3 at, float k, boolean ground) {
		if (type == ModParticles.DOOM_DOOM_SMOKE) {
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, (int) (10 * k), 0.4 * k, 0.5 * k, 0.4 * k, 0.02);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, (int) (6 * k), 0.3 * k, 0.4 * k, 0.3 * k, 0.03);
			return true;
		}
		int kind = DoomVfx.IMPACT, style = DoomVfx.GOLD, ticks = 10;
		float size = k;
		if (type == ModParticles.DOOM_ARMORED_COMBO) size = 1.5f * k;
		else if (type == ModParticles.DOOM_RUNE_MOTE) { size = 0.7f * k; style = DoomVfx.GREEN; }
		else if (type == ModParticles.DOOM_IRON_BASTION || type == ModParticles.DOOM_PHASE_SHIFT) { size = 1.8f * k; style = DoomVfx.GREEN; }
		else if (type == ModParticles.DOOM_JET_EXHAUST) size = 0.9f * k;
		else if (type == ModParticles.DOOM_JET_SLAM) size = 2.6f * k;
		else if (type == ModParticles.DOOM_SEVEN_SUNS || type == ModParticles.DOOM_SUN_PROJECTILE) { size = 1.6f * k; style = DoomVfx.SUN; }
		else if (type == ModParticles.DOOM_IMPACT || type == ModParticles.DOOM_GAUNTLET_BOLT || type == ModParticles.DOOM_SORCERY_BOLT) size = 1.1f * k;
		else if (type == ModParticles.DOOM_LATVERIAN_EDICT) { size = 3.5f * k; style = DoomVfx.EDICT_GREEN; }
		else if (type == ModParticles.DOOM_RIFT_STEP) { kind = DoomVfx.RIFT; size = 1.2f * k; ticks = 14; style = DoomVfx.GREEN; }
		else if (type == ModParticles.DOOM_SLAM_WAVE) { kind = DoomVfx.SLAM; size = 3.5f * k; ticks = 14; }
		else if (type == ModParticles.DOOM_EDICT_WARNING || type == ModParticles.DOOM_DROP_MARKER) {
			kind = DoomVfx.EDICT; size = (type == ModParticles.DOOM_DROP_MARKER ? 2f : 4.5f) * k; ticks = 30;
			style = type == ModParticles.DOOM_DROP_MARKER ? DoomVfx.GOLD : DoomVfx.EDICT_GREEN;
		}
		else if (type == ModParticles.DOOM_EDICT_COLUMN || type == ModParticles.DOOM_DOOMBOT_DROP) {
			kind = DoomVfx.COLUMN; size = type == ModParticles.DOOM_DOOMBOT_DROP ? 1.2f : 3.2f; ticks = 14;
			style = type == ModParticles.DOOM_DOOMBOT_DROP ? DoomVfx.GOLD : DoomVfx.EDICT_GREEN;
			at = new Vec3(at.x, Math.floor(at.y - 2.2) + 0.05, at.z);
		}
		else return false;
		if (ground && kind != DoomVfx.IMPACT) at = new Vec3(at.x, at.y + 0.05, at.z);
		DoomVfx.at(level, kind, at, size, ticks, style);
		return true;
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
