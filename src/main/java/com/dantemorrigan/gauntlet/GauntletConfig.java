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
    // Wheel input options (read on the client); legacy worn-gauntlet RMB is opt-in.
    public boolean abilityWheelToggle = false;
    public boolean legacyRightClickPower = false;
	/** Show the top-left selected ability card and favorites bar while wearing the gauntlet (client). */
	public boolean showStatusHud = true;
	/** Mod UI language: "auto" (follow Minecraft), "en" or "ru". Applies to every string of the mod, book included. */
	public String language = "auto";
	/** Hand the field guide book to a player the first time they join a world. */
	public boolean giveGuideBook = true;
	// ---- Co-op server rules ----
	public boolean abilityPvp = true;
	public boolean abilityBlockChanges = true;
	public boolean allowPlanetDestruction = true;
	public boolean allowCelestialDestruction = true;
	public boolean allowInfinitySnap = true;
	public boolean allowMeteorStream = true;
	public boolean allowTimeShift = true;
	public boolean mindPlayerGrab = true;
	public boolean soulPlayerDrain = true;
	public boolean extractorPlayerTheft = true;

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
	/** Planet Destruction is seen by every player on the server in the Overworld or the Nether (never the End), and takes them all to Dead Space. */
	public boolean planetDestructionAllPlayers = true;
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

	// ---- Armor of Uru vs. a full gauntlet (server) ----
	/** Attacks of a six-stone gauntlet on players ignore ordinary armor and are multiplied by this. */
	public float fullGauntletDamageMultiplier = 1.5f;
	/** Share of such a hit that a complete Uru set lets through (at least 1 HP per hit). */
	public float uruDamageFactor = 0.25f;
	/** "Kill outright" effects of a full gauntlet count as this much incoming damage against Uru. */
	public float uruLethalIncoming = 40f;
	/** Max durability each piece loses per absorbed hit. */
	public int uruMaxWearPerHit = 12;

	// ---- Thanos, the final boss (server) ----
	/** Thanos comes for a player who carries this many different stones (loose, in a gauntlet or extractor, Orb, Tesseract). */
	public boolean thanosEnabled = true;
	public int thanosStoneThreshold = 5;
	/** Breather: Thanos comes only this long (game ticks; 12000 = half a day) after the player has gathered the stones. */
	public int thanosDelayTicks = 12000;
	/** Once beaten, Thanos never returns in this world. */
	public boolean thanosOnce = true;
	/** Seconds of warning ("something approaches from deep space") before the arrival scene. */
	public int thanosWarningSeconds = 10;
	/** Minutes before Thanos comes back after the player died, ran away or left. */
	public int thanosReturnMinutes = 5;
	public float thanosHealth = 600f;
	/** Base damage of his attacks before armor: sword swing, leap slam, thrown sword, gauntlet backhand. */
	public float thanosSwingDamage = 22f;
	public float thanosSlamDamage = 18f;
	public float thanosThrowDamage = 16f;
	public float thanosBackhandDamage = 12f;
	/** Ordinary armor softens his blows by at most this share (at 20 armor points). */
	public float thanosArmorShare = 0.35f;
	/** A complete Uru set takes only this share of his blows. */
	public float thanosUruFactor = 0.3f;
	/** Damage he takes: explosions, diamond / netherite swords, loose stones (no gauntlet), a gauntlet, everything else. */
	public float thanosExplosionTaken = 2.0f;
	/** Swords hurt him less than the gauntlet does: the gauntlet is the strongest thing against him. */
	public float thanosDiamondSwordMult = 0.8f;
	public float thanosNetheriteSwordMult = 1.0f;
	/** A bare stone in the hand (no gauntlet worn): stronger than a sword, weaker than the gauntlet. */
	public float thanosLooseStoneMult = 1.5f;
	public float thanosOtherTaken = 0.35f;
	/** Abilities of a worn gauntlet (power strike, reality strike, mind throw...): partial gauntlet / all six stones. */
	public float thanosGauntletMult = 3.0f;
	public float thanosFullGauntletMult = 5.0f;
	/** The Infinity Beam takes this share of his maximum health when it hits for its full length. */
	public float thanosBeamShare = 0.45f;
	/** Experience dropped on defeat. */
	public int thanosExperience = 3000;
	/** After his defeat his ship falls and burns (iron and netherite rain down); with the crater it digs one where it lands. */
	public boolean thanosShipCrash = true;
	public boolean thanosShipCrater = true;
	/** The fallen ship is built from real blocks (about 50-100k, placed over a few seconds) and stays in the world for good. */
	public boolean thanosShipWreck = true;

	// ---- Finding the Power Stone (server) ----
	/** Meteor showers that bring the Power Stone; they stop once it is found. */
	public boolean powerShowerEnabled = true;
	/** Minutes of world time before the first possible shower, then a random gap between min and max. */
	public int powerShowerFirstMinutes = 20;
	public int powerShowerMinMinutes = 20;
	public int powerShowerMaxMinutes = 50;
	/** How far from the chosen player the shower falls (blocks). */
	public int powerShowerMinDistance = 60;
	public int powerShowerMaxDistance = 110;
	public int powerShowerSeconds = 25;
	public float powerShowerMeteorsPerSecond = 1.5f;
	/** Ticks a bare Power Stone can be held in a hand before it kills (creative is spared). */
	public int powerStoneHoldTicks = 40;

	// ---- Finding the Reality Stone (server) ----
	public boolean realityFogEnabled = true;
	/** Chance, checked once a minute per player in the Nether, that the red mist comes. */
	public float realityFogChancePerMinute = 0.08f;
	/** How long the stone stays in the mist before it slips away. */
	public int realityFogSeconds = 75;

	// ---- Meteor Stream (Power stone, server) ----
	/** Ticks the gauntlet must stay on the sun/moon to lock on. */
	public int streamLockTicks = 30;
	/** How far (blocks) the ground target can be from the caster. */
	public int streamMaxRange = 160;
	/** The target must be at least this far from the caster. */
	public int streamMinRange = 12;
	/** Radius (blocks) of the bombarded circle. */
	public int streamRadius = 24;
	/** Length of the stream in seconds (game time). */
	public int streamSeconds = 60;
	public float streamMeteorsPerSecond = 5f;
	/** Meteors in flight at once, for all streams together. */
	public int streamMaxActiveMeteors = 90;
	/** Streams running at once on the server. */
	public int streamMaxConcurrent = 3;
	public int streamCooldownSeconds = 150;
	public float streamCraterPowerMin = 1.6f;
	public float streamCraterPowerMax = 3.0f;

	// ---- Reality stone: Reality Inversion ----
	/** Radius of the inverted area (blocks). */
	public int realityRadius = 16;
	/** Max blocks the client folds/twists (closest first). */
	public int realityMaxBlocks = 2600;
	/** Longest hold before the fist opens by itself (ticks). */
	public int realityMaxHoldTicks = 600;
	public int realityCooldownTicks = 40;
	/** Reality strike (short tap): radius of the red-stone-and-fire ground and of the butterfly dissolve. */
	public int realityStrikeRadius = 10;
	public int realityStrikeCooldownTicks = 60;

	// ---- Space stone (server) ----
	public int portalLifetimeSeconds = 30;
	public int portalCooldownTicks = 60;
	/** Max distance of the look-teleport. */
	public int blinkRange = 96;
	public int blinkCooldownTicks = 20;

	// ---- All-six-stones Infinity Beam (server) ----
	public int beamChargeTicks = 40;
	public int beamFireTicks = 120;
	/** Version marker for the one-time migration of the original 2-second default. */
	public int beamTimingVersion = 1;
	public int beamCooldownTicks = 900;
	public double beamRange = 72;
	public double beamHitRadius = 1.2;
	public float beamDamage = 160;
	public boolean beamBreakBlocks = true;
	public double beamDrillRadius = 4.5;
	public int beamBlocksPerTick = 512;

	// ---- Time stone rewind (server) ----
	/** How far back the personal rewind goes (seconds of recorded history). */
	public int timeRewindSeconds = 5;
	/** Duration of the visible rewind playback (ticks). */
	public int timeRewindPlaybackTicks = 30;
	/** Duration of the world (planet) rewind before players are returned (ticks). */
	public int timeWorldRewindTicks = 70;
	public int timeCooldownTicks = 400;
	/** Day/night switch: how long the sun/moon race lasts (ticks) and its cooldown. */
	public int timeShiftTicks = 120;
	public int timeShiftCooldownTicks = 600;

	// ---- Mind stone: telekinesis (server) ----
	public double mindRange = 40;
	/** Radius of the chunk of terrain torn out around the aimed block. */
	public double mindGrabRadius = 2.6;
	public int mindMaxBlocks = 64;
	public double mindThrowSpeed = 2.0;
	/** Impact damage of a thrown cluster (grows with its size). */
	public float mindThrowDamage = 10f;
	public double mindMobThrowSpeed = 2.2;
	public float mindMobThrowDamage = 12f;
	/** Ticks the held mob is torn apart before it bursts. */
	public int mindRuptureTicks = 24;
	public int mindCooldownTicks = 20;

	// ---- Soul stone: extraction (server) ----
	public double soulRange = 32;
	public int soulPointCooldownTicks = 60;
	public double soulMassRadius = 24;
	public int soulMassMaxTargets = 48;
	public int soulMassCooldownTicks = 900;
	/** Absorbed souls stack up to this; each adds max health and attack damage for soulBuffTicks (refreshed per soul). */
	public int soulMaxStacks = 20;
	public double soulHealthPerSoul = 2;
	public double soulDamagePerSoul = 0.5;
	public int soulBuffTicks = 1200;

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

	/** Writes the current values back to {@code config/infinity_gauntlet.json}. */
	public static void save() {
		if (instance == null) return;
		Path path = FabricLoader.getInstance().getConfigDir().resolve("infinity_gauntlet.json");
		try (Writer writer = Files.newBufferedWriter(path)) {
			new GsonBuilder().setPrettyPrinting().create().toJson(instance, writer);
		} catch (IOException e) {
			InfinityGauntlet.LOGGER.warn("Could not write {}", path, e);
		}
	}

	private static GauntletConfig load() {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		Path path = FabricLoader.getInstance().getConfigDir().resolve("infinity_gauntlet.json");
		GauntletConfig config = new GauntletConfig();
		try {
			if (Files.exists(path)) {
				try (Reader reader = Files.newBufferedReader(path)) {
					com.google.gson.JsonElement json = com.google.gson.JsonParser.parseReader(reader);
					GauntletConfig read = gson.fromJson(json, GauntletConfig.class);
					if (read != null) {
						config = read;
						if (json.isJsonObject() && !json.getAsJsonObject().has("beamTimingVersion") && config.beamFireTicks == 40)
							config.beamFireTicks = 120;
					}
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
