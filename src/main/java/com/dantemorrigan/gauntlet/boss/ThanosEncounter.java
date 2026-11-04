package com.dantemorrigan.gauntlet.boss;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModComponents;
import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.ModSounds;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.net.ModStatus;
import com.dantemorrigan.gauntlet.stones.StoneKeeper;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The final encounter. A player carrying {@code thanosStoneThreshold} different stones draws Thanos from deep space:
 * <ul>
 *   <li>Overworld: Sanctuary II glides into the sky, the sky and the sun turn red, a blue beam lowers Thanos.</li>
 *   <li>Nether (and deep underground): a rift in reality tears open and he steps out of it.</li>
 *   <li>End: the ship arrives, the End brightens; a living dragon is struck down by the ship's cannon first.</li>
 * </ul>
 * He speaks, the battle theme starts and the fight begins. If the player dies, flees or leaves, he withdraws and
 * returns later. Once beaten (falls to one knee, turns to dust) he leaves his empty gauntlet - if he still had one,
 * i.e. the player had not crafted their own - and a lot of experience, and never returns.
 *
 * <p>Everything visual is sent to clients as {@link ModNetwork.ThanosFx}; the server only keeps the timeline.
 */
public final class ThanosEncounter {
	public static final int FX_SHIP_ARRIVE = 0, FX_SHIP_LEAVE = 1, FX_BEAM = 2, FX_DRAGON_CHARGE = 3, FX_DRAGON_RAY = 4,
			FX_RIFT_OPEN = 5, FX_RIFT_CLOSE = 6, FX_LINE = 7, FX_MUSIC_START = 8, FX_MUSIC_STOP = 9, FX_IMPACT = 10,
			FX_SHAKE = 11, FX_END = 12, FX_BEAM_UP = 13, FX_SHIP_CRASH = 14, FX_WRECK_DONE = 15;
	public static final int LINE_ARRIVAL = 0, LINE_GIVE_STONES = 1, LINE_UNIVERSE = 2, LINE_DIE = 3, LINE_DEFEAT_1 = 4, LINE_DEFEAT_2 = 5;
	/** Length of each line in ticks (the recordings). */
	public static final int[] LINE_TICKS = {50, 46, 75, 52, 75, 43};
	/** Ticks the ship falls before it hits the ground; the wreck burns this long after. */
	public static final int CRASH_TICKS = 110, WRECK_TICKS = 600;
	public static final int SHIP_APPROACH = 120, BEAM_TICKS = 70, RIFT_OPEN = 46, DRAGON_CHARGE = 28;
	/** How far the scene is sent (blocks). */
	private static final double FX_RANGE = 640;

	private static final AttachmentType<Boolean> DEFEATED = AttachmentRegistry.<Boolean>builder()
			.persistent(Codec.BOOL).buildAndRegister(InfinityGauntlet.id("thanos_defeated"));

	/** Game time at which this player first carried enough stones (cleared when they no longer do): the breather timer. */
	private static final AttachmentType<Long> ARMED_AT = AttachmentRegistry.<Long>builder()
			.persistent(Codec.LONG).copyOnDeath().buildAndRegister(InfinityGauntlet.id("thanos_armed_at"));

	private enum Mode { SKY, RIFT }

	private enum Stage { WARNING, ARRIVAL, FIGHT, LEAVING, DEFEAT, DEPARTING, CRASH }

	private static final class Encounter {
		UUID playerId;
		ServerLevel level;
		Mode mode;
		Stage stage = Stage.WARNING;
		int t;
		int stageStart;
		Vec3 ship, shipDir, landing, rift;
		float shipYaw, riftYaw;
		boolean hasGauntlet;
		boolean dragon;
		int beamAt, landAt, fightAt;
		Thanos boss;
		int awayTicks;
		boolean shipShown;
		Vec3 site;
		int carveX = Integer.MIN_VALUE;
		int spin = 1;
		java.util.concurrent.CompletableFuture<java.util.List<ShipWreck.Part>> plan;
		ShipWreck.Build build;
		int builtAt = -1;
		boolean firesLit;
	}

	/** Where the last fallen ship lies and how many blocks it was built from (for tests and the debug command). */
	public static BlockPos wreckSite;
	public static int wreckBlocks;

	private static Encounter active;
	private static final Map<UUID, Long> RETURN_AT = new HashMap<>();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ThanosEncounter::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> { active = null; RETURN_AT.clear(); });
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> {
			if (active != null && handler.player.getUUID().equals(active.playerId)) withdraw(false);
		}));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (active != null && entity instanceof ServerPlayer p && p.getUUID().equals(active.playerId)) {
				send(p, FX_MUSIC_STOP, 0, Vec3.ZERO, Vec3.ZERO, 0, 60);
				withdraw(true);
			}
		});
	}

	// ------------------------------------------------------------------ public API

	public static boolean defeatedForever(MinecraftServer server) {
		return Boolean.TRUE.equals(server.overworld().getAttached(DEFEATED));
	}

	public static boolean running() { return active != null; }

	/** The stones a player carries anywhere in the inventory (loose, gauntlet, extractor, the Orb, the Tesseract). */
	public static Set<Stone> carried(ServerPlayer player) {
		Set<Stone> set = EnumSet.noneOf(Stone.class);
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) collect(inv.getItem(i), set);
		collect(player.containerMenu.getCarried(), set);
		return set;
	}

	private static void collect(ItemStack stack, Set<Stone> set) {
		if (stack.isEmpty()) return;
		set.addAll(StoneKeeper.stonesIn(stack));
		if (stack.is(ModItems.POWER_ORB)) set.add(Stone.POWER);
		if (stack.is(ModItems.QUEST_SPACE_TESSERACT)) set.add(Stone.SPACE);
	}

	/** True if the player owns a working gauntlet of their own: then Thanos comes without his. */
	private static boolean ownsGauntlet(ServerPlayer player) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(ModItems.GAUNTLET) && !Boolean.TRUE.equals(s.get(ModComponents.SNAPPED))) return true;
		}
		return false;
	}

	/** Starts the encounter for this player now (command or trigger). Returns false if one is already running. */
	public static boolean start(ServerPlayer player, boolean skipWarning) {
		if (active != null) return false;
		Encounter e = new Encounter();
		e.playerId = player.getUUID();
		e.level = (ServerLevel) player.level();
		e.hasGauntlet = !ownsGauntlet(player);
		active = e;
		if (skipWarning) {
			beginArrival(e, player);
		} else {
			e.stage = Stage.WARNING;
			e.stageStart = 0;
			ModNetwork.QuestAlert alert = new ModNetwork.QuestAlert(Component.translatable("msg.infinity_gauntlet.thanos.warning"), 1);
			ServerPlayNetworking.send(player, alert);
			e.level.playSound(null, player.blockPosition(), ModSounds.STONE_OMINOUS, SoundSource.HOSTILE, 1.6f, 0.6f);
			e.level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2f, 0.5f);
		}
		return true;
	}

	/** Thanos leaves: he returns after {@code thanosReturnMinutes}. */
	public static void stop() {
		if (active != null) withdraw(true);
	}

	public static void reset(MinecraftServer server) {
		server.overworld().removeAttached(DEFEATED);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) p.removeAttached(ARMED_AT);
		RETURN_AT.clear();
		// A scene still running (fight, falling ship...) is cut short.
		if (active != null) {
			if (active.boss != null && !active.boss.isRemoved()) active.boss.discard();
			sendAll(active, FX_MUSIC_STOP, 0, Vec3.ZERO, Vec3.ZERO, 0, 20);
			sendAll(active, FX_END, 0, Vec3.ZERO, Vec3.ZERO, 0, 0);
			active = null;
		}
	}

	// ------------------------------------------------------------------ triggering

	private static void tick(MinecraftServer server) {
		if (active != null) {
			try {
				run(active);
			} catch (RuntimeException ex) {
				InfinityGauntlet.LOGGER.error("Thanos encounter failed", ex);
				if (active != null && active.boss != null) active.boss.discard();
				active = null;
			}
			return;
		}
		GauntletConfig cfg = GauntletConfig.get();
		if (!cfg.thanosEnabled || server.overworld().getGameTime() % 40 != 0) return;
		if (cfg.thanosOnce && defeatedForever(server)) return;
		long now = server.overworld().getGameTime();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.isCreative() || p.isSpectator() || !p.isAlive() || p.isSleeping()) continue;
			if (carried(p).size() < cfg.thanosStoneThreshold) {
				p.removeAttached(ARMED_AT);
				continue;
			}
			// The breather: he comes only after the player has had the stones for a while (half a day by default).
			Long armed = p.getAttached(ARMED_AT);
			if (armed == null || armed > now) {
				p.setAttached(ARMED_AT, now);
				ModStatus.send(p, Component.translatable("msg.infinity_gauntlet.thanos.sensed"));
				continue;
			}
			if (now - armed < cfg.thanosDelayTicks) continue;
			if (!arena(p.level())) continue;
			Long back = RETURN_AT.get(p.getUUID());
			if (back != null && now < back) continue;
			RETURN_AT.remove(p.getUUID());
			start(p, false);
			return;
		}
	}

	private static boolean arena(Level level) {
		return level.dimension() == Level.OVERWORLD || level.dimension() == Level.NETHER || level.dimension() == Level.END;
	}

	// ------------------------------------------------------------------ the timeline

	private static ServerPlayer player(Encounter e) {
		return e.level.getServer().getPlayerList().getPlayer(e.playerId);
	}

	private static void run(Encounter e) {
		e.t++;
		ServerPlayer p = player(e);
		int age = e.t - e.stageStart;
		switch (e.stage) {
			case WARNING -> {
				if (p == null || !p.isAlive()) { active = null; return; }
				if (age % 40 == 0) e.level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.6f, 0.5f);
				if (age >= GauntletConfig.get().thanosWarningSeconds * 20) {
					if (!arena(p.level())) { active = null; return; } // gone somewhere he can't follow: next check tries again
					beginArrival(e, p);
				}
			}
			case ARRIVAL -> arrival(e, p, age);
			case FIGHT -> fight(e, p);
			case LEAVING -> leaving(e, age);
			case DEFEAT -> { /* Thanos.tickDeath drives the defeat; onDefeatDone moves us on */ if (e.boss == null || e.boss.isRemoved() && age > Thanos.DEATH_TICKS + 40) depart(e); }
			case CRASH -> crash(e, age);
			case DEPARTING -> {
				if (age >= SHIP_APPROACH + 20) {
					sendAll(e, FX_END, 0, Vec3.ZERO, Vec3.ZERO, 0, 0);
					active = null;
				}
			}
		}
	}

	private static void setStage(Encounter e, Stage s) {
		e.stage = s;
		e.stageStart = e.t;
	}

	private static void beginArrival(Encounter e, ServerPlayer p) {
		e.level = (ServerLevel) p.level();
		setStage(e, Stage.ARRIVAL);
		ServerLevel level = e.level;
		boolean underground = level.dimension() == Level.OVERWORLD
				&& level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getBlockX(), p.getBlockZ()) > p.getY() + 8;
		e.mode = level.dimension() == Level.NETHER || underground ? Mode.RIFT : Mode.SKY;
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		e.landing = findLanding(level, p, flat);
		Vec3 toLanding = new Vec3(e.landing.x - p.getX(), 0, e.landing.z - p.getZ());
		Vec3 dir = toLanding.lengthSqr() < 1e-3 ? flat : toLanding.normalize();
		if (e.mode == Mode.SKY) {
			double top = Math.max(p.getY(), e.landing.y);
			if (level.dimension() == Level.OVERWORLD) top = Math.max(top, level.getSeaLevel());
			// Ship hovers beyond the landing point, high up, its nose toward the player.
			e.ship = new Vec3(p.getX() + dir.x * 70, top + (level.dimension() == Level.END ? 62 : 78), p.getZ() + dir.z * 70);
			e.shipDir = new Vec3(-dir.x, 0, -dir.z);
			e.shipYaw = (float) Math.toDegrees(Math.atan2(-e.shipDir.x, -e.shipDir.z));
			EnderDragon dragon = level.dimension() == Level.END ? livingDragon(level) : null;
			e.dragon = dragon != null;
			e.beamAt = SHIP_APPROACH + (e.dragon ? DRAGON_CHARGE + 40 : 10);
			e.landAt = e.beamAt + 46;
			sendAll(e, FX_SHIP_ARRIVE, level.dimension() == Level.END ? 2 : 0, e.ship, e.shipDir, e.shipYaw, SHIP_APPROACH);
			e.shipShown = true;
			level.playSound(null, p.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 3f, 0.4f);
		} else {
			e.rift = e.landing;
			e.riftYaw = (float) Math.toDegrees(Math.atan2(-dir.x, -dir.z));
			e.landAt = RIFT_OPEN + 30;
			sendAll(e, FX_RIFT_OPEN, 0, e.rift, dir, e.riftYaw, RIFT_OPEN);
			level.playSound(null, BlockPos.containing(e.rift), SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 2.5f, 0.5f);
		}
	}

	private static void arrival(Encounter e, ServerPlayer p, int age) {
		if (p == null) { withdraw(false); return; }
		ServerLevel level = e.level;
		GauntletConfig cfg = GauntletConfig.get();
		if (e.mode == Mode.SKY) {
			if (age == SHIP_APPROACH / 2) level.playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.2f, 0.4f);
			if (e.dragon && age == SHIP_APPROACH + 5) {
				EnderDragon d = livingDragon(level);
				if (d != null) {
					sendAll(e, FX_DRAGON_CHARGE, d.getId(), e.ship, d.position(), 0, DRAGON_CHARGE);
					level.playSound(null, BlockPos.containing(d.position()), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 4f, 0.4f);
				} else e.dragon = false;
			}
			if (e.dragon && age == SHIP_APPROACH + 5 + DRAGON_CHARGE) {
				EnderDragon d = livingDragon(level);
				if (d != null) strikeDragon(e, d);
			}
			if (age == e.beamAt) {
				sendAll(e, FX_BEAM, 0, e.landing, e.ship, e.shipYaw, BEAM_TICKS);
				level.playSound(null, BlockPos.containing(e.landing), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 4f, 0.5f);
				level.playSound(null, BlockPos.containing(e.landing), SoundEvents.CONDUIT_ACTIVATE, SoundSource.HOSTILE, 3f, 0.5f);
			}
			if (age == e.beamAt + 14) {
				e.boss = spawnBoss(e, p, e.landing.add(0, 24, 0));
				e.boss.script(Thanos.A_DESCEND, e.landing.add(0, 24, 0), e.landing, e.landAt - age);
			}
		} else {
			if (age == 18) {
				// He waits just behind the rift and walks out toward the player.
				Vec3 behind = e.rift.add(new Vec3(p.getX() - e.rift.x, 0, p.getZ() - e.rift.z).normalize().scale(-1.6));
				e.boss = spawnBoss(e, p, behind);
				Vec3 out = e.rift.add(new Vec3(p.getX() - e.rift.x, 0, p.getZ() - e.rift.z).normalize().scale(1.4));
				e.boss.script(Thanos.A_RIFT, behind, out, 26);
			}
			if (age == RIFT_OPEN + 14) {
				sendAll(e, FX_RIFT_CLOSE, 0, e.rift, Vec3.ZERO, e.riftYaw, 20);
				level.playSound(null, BlockPos.containing(e.rift), SoundEvents.PORTAL_TRAVEL, SoundSource.HOSTILE, 0.6f, 1.6f);
			}
		}
		if (e.boss == null) return;
		if (age == e.landAt) {
			// He touches the ground: impact, the battle theme, then the line.
			Vec3 c = e.boss.position();
			if (e.mode == Mode.SKY) {
				sendAll(e, FX_IMPACT, 0, c, Vec3.ZERO, 1.4f, 0);
				level.playSound(null, BlockPos.containing(c), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3f, 0.5f);
				level.playSound(null, BlockPos.containing(c), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2f, 0.35f);
			}
			e.boss.faceTowards(p.position());
			e.boss.pose(Thanos.A_INTRO);
			sendNear(e, c, 128, FX_MUSIC_START, 0, Vec3.ZERO, Vec3.ZERO, 0, 0);
		}
		if (age == e.landAt + 12) line(e.boss, LINE_ARRIVAL);
		int ready = e.landAt + 12 + LINE_TICKS[LINE_ARRIVAL] + 4;
		if (e.hasGauntlet && age == ready) {
			line(e.boss, LINE_GIVE_STONES);
		}
		if (e.hasGauntlet) ready += LINE_TICKS[LINE_GIVE_STONES];
		if (age > e.landAt && age < ready && e.boss.isAlive()) e.boss.faceTowards(p.position());
		if (age >= ready) {
			e.boss.beginFight(p);
			setStage(e, Stage.FIGHT);
		}
		if (cfg.thanosEnabled && !p.isAlive()) withdraw(true);
	}

	private static Thanos spawnBoss(Encounter e, ServerPlayer p, Vec3 at) {
		Thanos boss = ModEntities.THANOS.create(e.level, EntitySpawnReason.EVENT);
		if (boss == null) throw new IllegalStateException("Thanos could not be created");
		GauntletConfig cfg = GauntletConfig.get();
		var hp = boss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
		if (hp != null) hp.setBaseValue(cfg.thanosHealth);
		boss.setHealth(boss.getMaxHealth());
		boss.setHasGauntlet(e.hasGauntlet);
		boss.snapTo(at.x, at.y, at.z, 0, 0);
		boss.faceTowards(p.position());
		boss.setTargetPlayer(p);
		boss.setInvulnerable(true);
		boss.onDefeatDone(() -> {
			if (active != null && active.boss == boss) depart(active);
		});
		e.level.addFreshEntity(boss);
		return boss;
	}

	private static void fight(Encounter e, ServerPlayer p) {
		Thanos boss = e.boss;
		if (boss == null || boss.isRemoved()) { withdraw(false); return; }
		if (p == null) { withdraw(false); return; }
		boolean away = p.level() != boss.level() || p.distanceTo(boss) > 96;
		// Left the arena (another dimension stops every sound on the client): cut the theme for them explicitly.
		if (away && e.awayTicks == 0) send(p, FX_MUSIC_STOP, 0, Vec3.ZERO, Vec3.ZERO, 0, 40);
		// Keep the battle theme alive for everyone near the fight (clients ignore it while the track already plays).
		if ((e.t - e.stageStart) % 100 == 0) sendNear(e, boss.position(), 128, FX_MUSIC_START, 0, Vec3.ZERO, Vec3.ZERO, 0, 0);
		e.awayTicks = away ? e.awayTicks + 1 : 0;
		if (e.awayTicks > 200) {
			send(p, FX_MUSIC_STOP, 0, Vec3.ZERO, Vec3.ZERO, 0, 60);
			withdraw(true);
			return;
		}
		// Fell into the void / got stuck far away: the ship (or a rift) puts him back next to the player.
		if (!away && (boss.getY() < boss.level().getMinY() + 4 || boss.distanceTo(p) > 48 && boss.tickCount % 100 == 0)) {
			Vec3 to = findLanding(e.level, p, p.getLookAngle());
			boss.teleportTo(to.x, to.y, to.z);
			boss.setDeltaMovement(Vec3.ZERO);
			sendAll(e, FX_IMPACT, 0, to, Vec3.ZERO, 1.0f, 0);
		}
	}

	/** Thanos was struck down: the encounter waits for the dust, then the ship leaves. */
	static void defeated(Thanos boss) {
		if (active == null || active.boss != boss) return;
		setStage(active, Stage.DEFEAT);
		boss.level().getServer().overworld().setAttached(DEFEATED, true);
		for (ServerPlayer p : boss.level().getServer().getPlayerList().getPlayers()) {
			if (p.level() == boss.level() && p.distanceTo(boss) < 128) {
				send(p, FX_MUSIC_STOP, 0, Vec3.ZERO, Vec3.ZERO, 0, Thanos.DEATH_TICKS);
			}
		}
		RETURN_AT.clear();
	}

	private static void depart(Encounter e) {
		if (e.stage == Stage.DEPARTING || e.stage == Stage.CRASH) return;
		ServerPlayer p = player(e);
		boolean victory = e.boss != null && e.boss.isRemoved() && e.boss.isDeadOrDying();
		if (victory && e.shipShown && GauntletConfig.get().thanosShipCrash) {
			// His ship, left without its master, goes down: it falls beyond the battlefield and burns.
			setStage(e, Stage.CRASH);
			e.site = crashSite(e);
			e.spin = e.level.getRandom().nextBoolean() ? 1 : -1;
			if (e.site != null && GauntletConfig.get().thanosShipWreck) {
				// Voxelize the wreck while the ship is still falling (pure math, off the server thread).
				float yaw = e.shipYaw + e.spin * 28f, roll = e.spin * 19f;
				e.plan = java.util.concurrent.CompletableFuture.supplyAsync(() -> ShipWreck.plan(yaw, -27f, roll));
			}
			sendAll(e, FX_SHIP_CRASH, (e.site == null ? 1 : 0) | (e.spin > 0 ? 2 : 0), e.ship, e.site != null ? e.site : e.ship.add(0, -160, 0), e.shipYaw, CRASH_TICKS);
			e.level.playSound(null, BlockPos.containing(e.ship), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 8f, 0.4f);
		} else {
			setStage(e, Stage.DEPARTING);
			if (e.shipShown) sendAll(e, FX_SHIP_LEAVE, 0, e.ship, e.shipDir, e.shipYaw, SHIP_APPROACH);
		}
		if (p != null && victory) {
			ServerPlayNetworking.send(p, new ModNetwork.QuestAlert(Component.translatable("msg.infinity_gauntlet.thanos.defeated"), 1));
			p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
		}
	}

	/** He withdraws (player died, fled or left). With {@code later}, he comes back after the configured minutes. */
	private static void withdraw(boolean later) {
		Encounter e = active;
		if (e == null) return;
		if (later) {
			long back = e.level.getServer().overworld().getGameTime() + GauntletConfig.get().thanosReturnMinutes * 1200L;
			RETURN_AT.put(e.playerId, back);
		}
		sendAll(e, FX_MUSIC_STOP, 0, Vec3.ZERO, Vec3.ZERO, 0, 60);
		ServerPlayer owner = player(e);
		if (owner != null && owner.level() != e.level) send(owner, FX_MUSIC_STOP, 0, Vec3.ZERO, Vec3.ZERO, 0, 60);
		if (e.boss != null && !e.boss.isRemoved() && !e.boss.isDeadOrDying()) {
			setStage(e, Stage.LEAVING);
			Vec3 c = e.boss.position();
			if (e.mode == Mode.SKY && e.shipShown) {
				sendAll(e, FX_BEAM_UP, 0, c, e.ship, e.shipYaw, 50);
				e.boss.script(Thanos.A_LEAVE, c, c.add(0, 26, 0), 45);
				e.level.playSound(null, BlockPos.containing(c), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 3f, 0.5f);
			} else {
				sendAll(e, FX_RIFT_OPEN, 1, c, Vec3.ZERO, e.boss.getYRot(), 20);
				e.boss.pose(Thanos.A_LEAVE);
				e.level.playSound(null, BlockPos.containing(c), SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 2f, 0.6f);
			}
			return;
		}
		if (e.boss != null && !e.boss.isRemoved()) e.boss.discard();
		if (e.shipShown) depart(e);
		else {
			sendAll(e, FX_END, 0, Vec3.ZERO, Vec3.ZERO, 0, 0);
			active = null;
		}
	}

	private static void leaving(Encounter e, int age) {
		if (age == 40 && e.mode != Mode.SKY && e.boss != null) {
			sendAll(e, FX_RIFT_CLOSE, 0, e.boss.position(), Vec3.ZERO, e.boss.getYRot(), 20);
		}
		if (age >= 46) {
			if (e.boss != null && !e.boss.isRemoved()) e.boss.discard();
			if (e.shipShown) depart(e);
			else {
				sendAll(e, FX_END, 0, Vec3.ZERO, Vec3.ZERO, 0, 0);
				active = null;
			}
		}
	}

	// ------------------------------------------------------------------ the ship goes down

	/** Where the ship falls: about under its hover point (~60 blocks from the player), on loaded ground. */
	private static Vec3 crashSite(Encounter e) {
		Vec3 away = new Vec3(-e.shipDir.x, 0, -e.shipDir.z);
		for (double d : new double[]{-8, 4, 14, -18}) {
			Vec3 c = e.ship.add(away.scale(d));
			BlockPos column = BlockPos.containing(c.x, 0, c.z);
			if (!e.level.hasChunkAt(column)) continue;
			int y = e.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
			if (y <= e.level.getMinY() + 1) continue; // the End's void
			return new Vec3(column.getX() + 0.5, y, column.getZ() + 0.5);
		}
		return null;
	}

	/** The falling ship's center, k = 0..1 (shared with the client so drops fall from where it is drawn). */
	public static Vec3 crashPos(Vec3 hover, Vec3 site, float k) {
		float e = k * k;
		Vec3 mid = new Vec3((hover.x + site.x) / 2, hover.y + 6, (hover.z + site.z) / 2);
		Vec3 a = hover.lerp(mid, e), b = mid.lerp(site, e);
		return a.lerp(b, e);
	}

	private static void crash(Encounter e, int age) {
		ServerLevel level = e.level;
		Vec3 target = e.site != null ? e.site : e.ship.add(0, -120, 0);
		if (age < CRASH_TICKS) {
			Vec3 at = crashPos(e.ship, target, age / (float) CRASH_TICKS);
			if (age % 15 == 0) {
				level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 10f, 0.4f + age * 0.002f);
			}
			// Pieces of the hull rain down: iron, now and then netherite.
			if (e.site != null && age % 5 == 0 && age > 10) {
				for (int i = 0; i < 2; i++) {
					var r = level.getRandom();
					Vec3 p = at.add((r.nextDouble() - 0.5) * 40, -8, (r.nextDouble() - 0.5) * 30);
					ItemStack loot = r.nextInt(8) == 0 ? new ItemStack(net.minecraft.world.item.Items.NETHERITE_SCRAP)
							: new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 2 + r.nextInt(4));
					drop(level, p, loot, new Vec3((r.nextDouble() - 0.5) * 0.3, -0.2, (r.nextDouble() - 0.5) * 0.3));
				}
			}
			return;
		}
		if (age == CRASH_TICKS) {
			if (e.site == null) return;
			impact(e);
		}
		// Carve the crater one slice per tick, then the fires; then the ship itself is built where it lies.
		boolean carving = e.site != null && GauntletConfig.get().thanosShipCrater && e.carveX <= CRATER_R;
		if (carving) {
			carveSlice(e);
			if (e.carveX > CRATER_R) fires(e);
		}
		if (e.site != null && !carving && !e.firesLit) { e.firesLit = true; fires(e); }
		if (e.plan != null && !carving && e.build == null && e.plan.isDone() && !e.plan.isCompletedExceptionally()) {
			e.build = new ShipWreck.Build(level, BlockPos.containing(e.site), e.plan.join());
			InfinityGauntlet.LOGGER.info("Thanos ship wreck: {} blocks to place at {}", e.build.total(), BlockPos.containing(e.site).toShortString());
		}
		if (e.build != null && !e.build.done()) e.build.step(2500);
		boolean noWreck = e.plan == null || e.plan.isCompletedExceptionally() || (e.plan.isDone() && e.build == null && !carving && age > CRASH_TICKS + 600);
		if (e.builtAt < 0 && ((e.build != null && e.build.done()) || noWreck || age > CRASH_TICKS + 1500)) {
			e.builtAt = age;
			// The model on the clients can fade now: the real thing lies there.
			sendAll(e, FX_WRECK_DONE, 0, e.site == null ? Vec3.ZERO : e.site, Vec3.ZERO, 0, 0);
			if (e.build != null) {
				InfinityGauntlet.LOGGER.info("Thanos ship wreck built: {} blocks", e.build.placed());
				wreckSite = BlockPos.containing(e.site);
				wreckBlocks = e.build.placed();
			}
		}
		if (e.builtAt >= 0 && age >= e.builtAt + 120) {
			sendAll(e, FX_END, 0, Vec3.ZERO, Vec3.ZERO, 0, 0);
			active = null;
		}
	}

	private static final int CRATER_R = 18, CRATER_D = 8;

	private static void impact(Encounter e) {
		ServerLevel level = e.level;
		Vec3 c = e.site;
		level.explode(null, c.x, c.y + 2, c.z, 8f, Level.ExplosionInteraction.NONE);
		level.playSound(null, BlockPos.containing(c), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 16f, 0.3f);
		level.playSound(null, BlockPos.containing(c), SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.HOSTILE, 16f, 0.4f);
		level.playSound(null, BlockPos.containing(c), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 12f, 0.2f);
		for (ServerPlayer p : level.players()) {
			double d = p.position().distanceTo(c);
			if (d < 260) send(p, FX_SHAKE, 0, c, Vec3.ZERO, (float) Math.max(0.35, 1.6 * (1 - d / 260)), 50);
		}
		// The ship spills its metal: lots of iron, some netherite.
		var r = level.getRandom();
		for (int i = 0; i < 40; i++) {
			Vec3 v = new Vec3((r.nextDouble() - 0.5) * 1.6, 0.6 + r.nextDouble() * 0.8, (r.nextDouble() - 0.5) * 1.6);
			drop(level, c.add(0, 6, 0), new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 3 + r.nextInt(6)), v);
		}
		for (int i = 0; i < 10; i++) {
			Vec3 v = new Vec3((r.nextDouble() - 0.5) * 1.2, 0.7 + r.nextDouble() * 0.6, (r.nextDouble() - 0.5) * 1.2);
			drop(level, c.add(0, 6, 0), new ItemStack(net.minecraft.world.item.Items.NETHERITE_SCRAP, 1 + r.nextInt(2)), v);
		}
		for (int i = 0; i < 3; i++) {
			Vec3 v = new Vec3((r.nextDouble() - 0.5) * 0.8, 0.9, (r.nextDouble() - 0.5) * 0.8);
			drop(level, c.add(0, 6, 0), new ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT), v);
		}
		for (int i = 0; i < 6; i++) {
			Vec3 v = new Vec3((r.nextDouble() - 0.5) * 1.0, 0.8, (r.nextDouble() - 0.5) * 1.0);
			drop(level, c.add(0, 6, 0), new ItemStack(net.minecraft.world.item.Items.IRON_BLOCK, 1 + r.nextInt(2)), v);
		}
		e.carveX = -CRATER_R;
	}

	/** Loot that survives the fire it lands in. */
	private static void drop(ServerLevel level, Vec3 at, ItemStack stack, Vec3 velocity) {
		var item = new net.minecraft.world.entity.item.ItemEntity(level, at.x, at.y, at.z, stack);
		item.setDeltaMovement(velocity);
		item.setInvulnerable(true);
		level.addFreshEntity(item);
	}

	private static boolean breakable(ServerLevel level, BlockPos pos) {
		var state = level.getBlockState(pos);
		return !state.isAir() && !state.hasBlockEntity() && state.getDestroySpeed(level, pos) >= 0;
	}

	/** One x-slice of the bowl: everything above the crater surface goes, the floor becomes scorched rock and fire. */
	private static void carveSlice(Encounter e) {
		ServerLevel level = e.level;
		var r = level.getRandom();
		int dx = e.carveX++;
		BlockPos c = BlockPos.containing(e.site);
		for (int dz = -CRATER_R; dz <= CRATER_R; dz++) {
			double h = (dx * dx + dz * dz) / (double) (CRATER_R * CRATER_R);
			if (h > 1) continue;
			int depth = (int) Math.round(CRATER_D * Math.sqrt(1 - h));
			int x = c.getX() + dx, z = c.getZ() + dz;
			// Clear the column from well above the ground down to the bowl.
			for (int y = c.getY() + 14; y > c.getY() - depth; y--) {
				BlockPos p = new BlockPos(x, y, z);
				if (breakable(level, p) || !level.getFluidState(p).isEmpty()) level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
			}
			BlockPos floor = new BlockPos(x, c.getY() - depth, z);
			if (!breakable(level, floor)) continue;
			var rock = switch (r.nextInt(6)) {
				case 0 -> net.minecraft.world.level.block.Blocks.MAGMA_BLOCK;
				case 1, 2 -> net.minecraft.world.level.block.Blocks.BLACKSTONE;
				case 3 -> net.minecraft.world.level.block.Blocks.BASALT;
				case 4 -> net.minecraft.world.level.block.Blocks.COAL_BLOCK;
				default -> net.minecraft.world.level.block.Blocks.NETHERRACK;
			};
			level.setBlock(floor, rock.defaultBlockState(), 3);
			if (rock == net.minecraft.world.level.block.Blocks.NETHERRACK && r.nextInt(2) == 0) {
				level.setBlock(floor.above(), net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState(), 3);
			}
		}
	}

	/** The land around the crater is on fire. */
	private static void fires(Encounter e) {
		ServerLevel level = e.level;
		var r = level.getRandom();
		// Fires all around the crater.
		BlockPos s = BlockPos.containing(e.site);
		for (int i = 0; i < 260; i++) {
			double a = r.nextDouble() * Math.PI * 2, d = CRATER_R - 2 + r.nextDouble() * 18;
			int x = (int) Math.floor(s.getX() + Math.cos(a) * d), z = (int) Math.floor(s.getZ() + Math.sin(a) * d);
			if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos p = new BlockPos(x, y, z);
			if (level.getBlockState(p).isAir() && level.getFluidState(p.below()).isEmpty() && !level.getBlockState(p.below()).isAir()) {
				level.setBlock(p, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState(), 3);
			}
		}
	}

	// ------------------------------------------------------------------ the End dragon

	private static EnderDragon livingDragon(ServerLevel level) {
		for (EnderDragon d : level.getDragons()) if (d.isAlive() && !d.isDeadOrDying()) return d;
		return null;
	}

	private static void strikeDragon(Encounter e, EnderDragon d) {
		ServerLevel level = e.level;
		Vec3 at = d.position().add(0, 2, 0);
		sendAll(e, FX_DRAGON_RAY, d.getId(), e.ship, at, 0, 14);
		level.playSound(null, BlockPos.containing(at), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 6f, 0.5f);
		level.playSound(null, BlockPos.containing(at), SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 5f, 0.8f);
		level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 6f, 0.5f);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 6, 4, 2, 4, 0);
		ExperienceOrb.award(level, at, 500);
		d.kill(level);
	}

	// ------------------------------------------------------------------ helpers

	/** A free, standable spot 9-13 blocks from the player, preferably in {@code dir}, at about their height. */
	static Vec3 findLanding(ServerLevel level, ServerPlayer p, Vec3 dir) {
		Vec3 flat = new Vec3(dir.x, 0, dir.z);
		flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		for (int attempt = 0; attempt < 16; attempt++) {
			double ang = (attempt % 2 == 0 ? 1 : -1) * (attempt / 2) * 0.45;
			double dist = 11 - (attempt % 3);
			Vec3 d = new Vec3(flat.x * Math.cos(ang) - flat.z * Math.sin(ang), 0, flat.x * Math.sin(ang) + flat.z * Math.cos(ang));
			int x = Mth.floor(p.getX() + d.x * dist), z = Mth.floor(p.getZ() + d.z * dist);
			for (int dy = 4; dy >= -6; dy--) {
				BlockPos feet = new BlockPos(x, p.getBlockY() + dy, z);
				if (standable(level, feet)) return new Vec3(x + 0.5, feet.getY(), z + 0.5);
			}
		}
		return p.position().add(flat.scale(3));
	}

	private static boolean standable(ServerLevel level, BlockPos feet) {
		if (level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) return false;
		if (!level.getFluidState(feet.below()).isEmpty() && level.getFluidState(feet.below()).is(net.minecraft.tags.FluidTags.LAVA)) return false;
		for (int i = 0; i < 3; i++) {
			BlockPos b = feet.above(i);
			if (!level.getBlockState(b).getCollisionShape(level, b).isEmpty()) return false;
		}
		return true;
	}

	public static void line(Thanos boss, int line) {
		if (!(boss.level() instanceof ServerLevel level)) return;
		SoundEvent sound = switch (line) {
			case LINE_ARRIVAL -> ModSounds.THANOS_ARRIVAL;
			case LINE_GIVE_STONES -> ModSounds.THANOS_GIVE_STONES;
			case LINE_UNIVERSE -> ModSounds.THANOS_UNIVERSE;
			case LINE_DIE -> ModSounds.THANOS_YOU_WILL_DIE;
			case LINE_DEFEAT_1 -> ModSounds.THANOS_DEFEAT_1;
			default -> ModSounds.THANOS_DEFEAT_2;
		};
		level.playSound(null, boss.getX(), boss.getY() + 2.4, boss.getZ(), sound, SoundSource.HOSTILE, 4f, 1f);
		for (ServerPlayer p : level.players()) {
			if (p.distanceTo(boss) < 96) send(p, FX_LINE, line, boss.position(), Vec3.ZERO, 0, LINE_TICKS[line]);
		}
	}

	static void shake(ServerLevel level, Vec3 at, float strength) {
		for (ServerPlayer p : level.players()) {
			double d = p.position().distanceTo(at);
			if (d < 32) send(p, FX_SHAKE, 0, at, Vec3.ZERO, (float) (strength * (1 - d / 32)), 12);
		}
	}

	private static void send(ServerPlayer p, int kind, int id, Vec3 a, Vec3 b, float f, int ticks) {
		ServerPlayNetworking.send(p, new ModNetwork.ThanosFx(kind, id, a, b, f, ticks));
	}

	private static void sendAll(Encounter e, int kind, int id, Vec3 a, Vec3 b, float f, int ticks) {
		Vec3 center = e.landing != null ? e.landing : a;
		for (ServerPlayer p : e.level.players()) {
			if (p.position().distanceTo(center) < FX_RANGE) send(p, kind, id, a, b, f, ticks);
		}
	}

	private static void sendNear(Encounter e, Vec3 c, double r, int kind, int id, Vec3 a, Vec3 b, float f, int ticks) {
		for (ServerPlayer p : e.level.players()) {
			if (p.position().distanceTo(c) < r) send(p, kind, id, a, b, f, ticks);
		}
	}

	/** Debug: a Thanos right here, already fighting (no scene). */
	public static Thanos spawnNow(ServerPlayer p) {
		if (active != null) return null;
		Encounter e = new Encounter();
		e.playerId = p.getUUID();
		e.level = (ServerLevel) p.level();
		e.hasGauntlet = !ownsGauntlet(p);
		e.mode = Mode.RIFT;
		e.landing = findLanding(e.level, p, p.getLookAngle());
		active = e;
		e.boss = spawnBoss(e, p, e.landing);
		e.boss.beginFight(p);
		setStage(e, Stage.FIGHT);
		send(p, FX_MUSIC_START, 0, Vec3.ZERO, Vec3.ZERO, 0, 0);
		return e.boss;
	}

	private ThanosEncounter() {}
}
