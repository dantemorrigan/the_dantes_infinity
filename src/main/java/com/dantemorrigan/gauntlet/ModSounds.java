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
	public static final SoundEvent THANOS_MUSIC = register("thanos.battle_music");

	private static SoundEvent register(String name) {
		Identifier id = InfinityGauntlet.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void play(ServerLevel level, Vec3 at, SoundEvent sound, float volume) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, 1.0f);
	}

	public static void init() {}

	private ModSounds() {}
}
