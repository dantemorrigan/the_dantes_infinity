package com.dantemorrigan.gauntlet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tunables, stored in {@code config/infinity_gauntlet.json}. Missing fields keep defaults; the file is
 * rewritten on load so new options appear. Server fields are read on the server, client fields on the client.
 */
public final class GauntletConfig {
	// ---- Power strike (server) ----
	/** Damage at full charge to the directly hit target. */
	public float strikeDamage = 18f;
	/** Shockwave radius (blocks) at full charge. */
	public float strikeRadius = 4.5f;
	/** Horizontal knockback strength at full charge. */
	public float strikeKnockback = 2.2f;
	/** Ticks of charge that count as "full". */
	public int strikeFullChargeTicks = 20;
	/** Releases shorter than this do nothing. */
	public int strikeMinChargeTicks = 4;
	public int strikeCooldownTicks = 20;
	public double strikeReach = 6.0;

	// ---- Planet destruction (server) ----
	/** Charge needed for the ultimate (ticks). */
	public int ultimateChargeTicks = 40;
	public int ultimateCooldownTicks = 1200;
	/** Players within this radius of the impact join the event and are transferred. */
	public double participantRadius = 32.0;
	/** If true, every player in the activator's dimension is transferred. Off by default. */
	public boolean transferWholeDimension = false;
	/** Cutscene tick at which the server transfers participants (matches cinematic_timeline.json). */
	public int transferTick = 68;
	/** Max real blocks captured per fragment (ground, stone, building). */
	public int fragmentMaxBlocks = 27;

	// ---- Celestial Apocalypse (server) ----
	/** Seconds the gauntlet must stay aimed at the sun/moon to lock on. */
	public float celestialAimSeconds = 3f;
	/** Aim tolerance in degrees around the body's center. */
	public float celestialAimDegrees = 9f;
	/** Meteor rain length in REAL seconds (monotonic deadline, survives lag and restarts). */
	public int meteorRainSeconds = 300;
	public float meteorImpactsPerSecond = 1.2f;
	/** Ground impacts happen within this radius of the event center (loaded chunks only). */
	public int meteorImpactRadius = 96;
	public int maxActiveMeteors = 24;
	public float craterPowerMin = 3.0f;
	public float craterPowerMax = 6.0f;
	/** Red sky radius around the event center; the color fades out over the extra falloff. */
	public int redSkyRadius = 256;
	public int redSkyFalloff = 64;
	/** Meteors don't target ground closer than this to a player. */
	public int meteorPlayerSafeRadius = 6;

	// ---- Space stone (server) ----
	public int portalLifetimeSeconds = 30;
	public int portalCooldownTicks = 60;
	/** Max distance of the look-teleport. */
	public int blinkRange = 96;
	public int blinkCooldownTicks = 20;

	// ---- Time stone rewind (server) ----
	/** How far back the personal rewind goes (seconds of recorded history). */
	public int timeRewindSeconds = 5;
	/** Duration of the visible rewind playback (ticks). */
	public int timeRewindPlaybackTicks = 30;
	/** Duration of the world (planet) rewind before players are returned (ticks). */
	public int timeWorldRewindTicks = 70;
	public int timeCooldownTicks = 400;

	// ---- Client presentation ----
	public float cameraShakeScale = 1.0f;
	public float cameraShakeMaxDegrees = 4.0f;
	/** 0..1 multiplier for the white flash. */
	public float flashIntensity = 1.0f;
	/** Space movement acceleration per tick and damping (0..1). */
	public float spaceThrust = 0.012f;
	public float spaceDamping = 0.965f;
	public float spaceMaxSpeed = 0.6f;
	public int maxSpaceParticles = 40;

	private static GauntletConfig instance;

	public static GauntletConfig get() {
		if (instance == null) instance = load();
		return instance;
	}

	public static GauntletConfig reload() {
		instance = load();
		return instance;
	}

	private static GauntletConfig load() {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		Path path = FabricLoader.getInstance().getConfigDir().resolve("infinity_gauntlet.json");
		GauntletConfig config = new GauntletConfig();
		try {
			if (Files.exists(path)) {
				try (Reader reader = Files.newBufferedReader(path)) {
					GauntletConfig read = gson.fromJson(reader, GauntletConfig.class);
					if (read != null) config = read;
				}
			}
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				gson.toJson(config, writer);
			}
		} catch (IOException | RuntimeException e) {
			InfinityGauntlet.LOGGER.warn("Could not read/write {}, using defaults", path, e);
		}
		return config;
	}

	private GauntletConfig() {}
}
