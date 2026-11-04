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

	// Ability cues (cut from the film): played when the ability fires.
	public static final SoundEvent ABILITY_POWER_STRIKE = register("ability_power_strike");
	public static final SoundEvent ABILITY_POWER_CHARGE = register("ability_power_charge");
	public static final SoundEvent ABILITY_PLANET_DESTRUCTION = register("ability_planet_destruction");
	public static final SoundEvent ABILITY_METEOR_STREAM = register("ability_meteor_stream");
	public static final SoundEvent ABILITY_SPACE_PORTAL = register("ability_space_portal");
	public static final SoundEvent ABILITY_SPACE_BLINK = register("ability_space_blink");
	public static final SoundEvent ABILITY_REALITY_STRIKE = register("ability_reality_strike");
	public static final SoundEvent ABILITY_REALITY_INVERSION = register("ability_reality_inversion");
	public static final SoundEvent ABILITY_TIME_REWIND = register("ability_time_rewind");
	public static final SoundEvent ABILITY_TIME_WORLD_REWIND = register("ability_time_world_rewind");
	public static final SoundEvent ABILITY_TIME_SHIFT = register("ability_time_shift");
	public static final SoundEvent ABILITY_MIND_GRAB = register("ability_mind_grab");
	public static final SoundEvent ABILITY_MIND_TEAR = register("ability_mind_tear");
	public static final SoundEvent ABILITY_INFINITY_BEAM = register("ability_infinity_beam");

	/** The cue for an ability that starts on key press, or null if it has none (power abilities are cued elsewhere). */
	public static SoundEvent cue(com.dantemorrigan.gauntlet.ability.Ability a) {
		return switch (a) {
			case STRIKE, PLANET -> ABILITY_POWER_CHARGE;
			case METEOR_STREAM -> ABILITY_METEOR_STREAM;
			case PORTAL -> ABILITY_SPACE_PORTAL;
			case BLINK -> ABILITY_SPACE_BLINK;
			case REALITY_STRIKE -> ABILITY_REALITY_STRIKE;
			case INVERSION -> ABILITY_REALITY_INVERSION;
			case REWIND -> ABILITY_TIME_REWIND;
			case DAY_NIGHT -> ABILITY_TIME_SHIFT;
			case GRAB -> ABILITY_MIND_GRAB;
			case TEAR -> ABILITY_MIND_TEAR;
			case SOUL, SOUL_MASS -> STONE_OMINOUS;
			case BEAM -> ABILITY_INFINITY_BEAM;
			default -> null;
		};
	}

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
