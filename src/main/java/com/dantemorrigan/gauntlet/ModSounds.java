package com.dantemorrigan.gauntlet;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Effect sounds cut from the Infinity War gauntlet scenes (no dialogue). */
public final class ModSounds {
	/** A stone clicks into the gauntlet (three variants: space, reality, time). */
	public static final SoundEvent STONE_INSERT = register("stone_insert");
	/** The sixth stone: the gauntlet surges awake. */
	public static final SoundEvent GAUNTLET_COMPLETE = register("gauntlet_complete");
	/** Ominous drone: a stone is near (quest events, the Power Stone's curse). */
	public static final SoundEvent STONE_OMINOUS = register("stone_ominous");
	/** Sun/Moon destruction: the gauntlet seizes the body, then the body breaks and falls. */
	public static final SoundEvent CELESTIAL_CAPTURE = register("celestial_capture");
	public static final SoundEvent CELESTIAL_SHATTER = register("celestial_shatter");

	// Thanos (voiced lines and the battle theme).
	public static final SoundEvent THANOS_ARRIVAL = register("thanos.arrival");
	public static final SoundEvent THANOS_GIVE_STONES = register("thanos.give_stones");
	public static final SoundEvent THANOS_UNIVERSE = register("thanos.universe");
	public static final SoundEvent THANOS_YOU_WILL_DIE = register("thanos.you_will_die");
	public static final SoundEvent THANOS_DEFEAT_1 = register("thanos.defeat_1");
	public static final SoundEvent THANOS_DEFEAT_2 = register("thanos.defeat_2");
	public static final SoundEvent LOKI_MUSIC = register("loki.battle_music");
	public static final SoundEvent LOKI_GREETING = register("loki.greeting");
	public static final SoundEvent LOKI_TAUNT = register("loki.taunt");
	public static final SoundEvent LOKI_TRANSFORMATION = register("loki.transformation");
	public static final SoundEvent LOKI_DEFEAT = register("loki.defeat");
	public static final SoundEvent THANOS_MUSIC = register("thanos.battle_music");

	public static final SoundEvent TRIBUNAL_ARRIVAL = register("tribunal.arrival");
	public static final SoundEvent TRIBUNAL_SURRENDER = register("tribunal.surrender");
	public static final SoundEvent TRIBUNAL_DEFEAT = register("tribunal.defeat");
	public static final SoundEvent TRIBUNAL_MUSIC = register("tribunal.battle_music");
	public static final SoundEvent DOOM_WARNING = register("doctor_doom.latveria_warning");

	private static SoundEvent register(String name) {
		Identifier id = InfinityGauntlet.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** Cuts these sounds for everyone who can hear them, so a new one never stacks on the tail of the last. */
	public static void stopNearby(ServerLevel level, Vec3 at, SoundEvent... sounds) {
		for (net.minecraft.server.level.ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(at) > 96 * 96) continue;
			for (SoundEvent s : sounds) {
				p.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(s.location(), SoundSource.PLAYERS));
			}
		}
	}

	public static void play(ServerLevel level, Vec3 at, SoundEvent sound, float volume) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, 1.0f);
	}

	public static void init() {}

	private ModSounds() {}
}
