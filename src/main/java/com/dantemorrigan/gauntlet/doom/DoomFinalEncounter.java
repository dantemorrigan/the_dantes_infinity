package com.dantemorrigan.gauntlet.doom;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.GauntletGesture;
import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.boss.ShipWreck;
import com.dantemorrigan.gauntlet.boss.Thanos;
import com.dantemorrigan.gauntlet.boss.ThanosEncounter;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Task 4 Storyline: When Doctor Doom steals the player's incomplete Infinity Gauntlet (<= 5 stones) in Latveria:
 * <ol>
 *   <li><b>Cutscene 1 (Theft & Teleport)</b>: Doom equips the Gauntlet on his left arm and teleports out of Latveria.</li>
 *   <li><b>Overworld Compass & Sandy-Fiery Battlefield</b>: A quest compass leads players in the Overworld to a massive
 *       sandy-fiery arena (radius 36) where Doom stands waiting with the Gauntlet.</li>
 *   <li><b>Approach Pushback & Cutscene 2 (Thanos Arrives & Dome Duel)</b>: Approaching Doom triggers a shockwave pushback
 *       and Thanos's arrival from Sanctuary II; Thanos focuses entirely on Doom, and an impenetrable Energy Dome (radius 20)
 *       rises over their duel while the player watches from outside.</li>
 *   <li><b>Stormbreaker Dome Shatter & Cutscene 3 (Thanos's Death & Ship Crash)</b>: Holding Stormbreaker near the dome
 *       unlocks a prompt/action to shatter the barrier; Doom then executes Thanos and shoots down Sanctuary II, which crashes.</li>
 *   <li><b>Final Battle (Gauntlet Doom vs Player)</b>: Doom turns to the player combining Gauntlet stone abilities and magic.</li>
 *   <li><b>Victory Cutscene (Green Dust & Gauntlet Return)</b>: Defeated Doom drops to one knee and dissolves into green dust
 *       while the Infinity Gauntlet flies along a smooth arc and equips directly onto the player's left hand (offhand).</li>
 * </ol>
 */
public final class DoomFinalEncounter {
	public static final int STAGE_NONE = 0;
	public static final int STAGE_THEFT_CUTSCENE = 1;
	public static final int STAGE_WAITING_OVERWORLD = 2;
	public static final int STAGE_THANOS_ARRIVAL = 3;
	public static final int STAGE_DOME_DUEL = 4;
	public static final int STAGE_KILL_THANOS_CUTSCENE = 5;
	public static final int STAGE_FINAL_BOSS = 6;
	public static final int STAGE_VICTORY_CUTSCENE = 7;
	public static final int STAGE_DONE = 8;

	public static final double ARENA_RADIUS = 36.0;
	public static final double DOME_RADIUS = 20.0;

	private record SavedState(int stage, BlockPos arenaPos, int stoneMask, boolean arenaBuilt, String ownerUuid,
			ItemStack gauntlet, String returnId, List<String> victors) {
		static final Codec<SavedState> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("stage").forGetter(SavedState::stage),
				BlockPos.CODEC.fieldOf("arenaPos").forGetter(SavedState::arenaPos),
				Codec.INT.fieldOf("stoneMask").forGetter(SavedState::stoneMask),
				Codec.BOOL.fieldOf("arenaBuilt").forGetter(SavedState::arenaBuilt),
				Codec.STRING.fieldOf("ownerUuid").forGetter(SavedState::ownerUuid),
				ItemStack.OPTIONAL_CODEC.optionalFieldOf("gauntlet", ItemStack.EMPTY).forGetter(SavedState::gauntlet),
				Codec.STRING.optionalFieldOf("returnId", "").forGetter(SavedState::returnId),
				Codec.STRING.listOf().optionalFieldOf("victors", List.of()).forGetter(SavedState::victors)
		).apply(i, SavedState::new));
	}

	private static final AttachmentType<SavedState> STATE = AttachmentRegistry.<SavedState>builder()
			.persistent(SavedState.CODEC)
			.buildAndRegister(InfinityGauntlet.id("doom_final_encounter"));

	// The receipt is stored in the same player save as the returned inventory stack.
	private static final AttachmentType<String> GAUNTLET_RECEIPT = AttachmentRegistry.<String>builder()
			.persistent(Codec.STRING).copyOnDeath().buildAndRegister(InfinityGauntlet.id("doom_gauntlet_receipt"));

	private static boolean loaded;
	private static int stage = STAGE_NONE;
	private static int stageTicks = 0;
	private static BlockPos arenaPos = BlockPos.ZERO;
	private static int stoneMask = 0x1F; // default 5 stones
	private static boolean arenaBuilt = false;
	private static UUID ownerId;
	private static String returnId = "";
	private static final Set<UUID> victors = new LinkedHashSet<>();
	private static Vec3 theftCenter = Vec3.ZERO;
	private static int theftDoomId = -1;
	private static ItemStack stolenGauntlet = ItemStack.EMPTY;

	private static DoctorDoom doom;
	private static Thanos thanos;
	private static Vec3 shipHover = Vec3.ZERO;
	private static Vec3 shipDir = new Vec3(0, 0, 1);
	private static float shipYaw = 0f;
	private static Vec3 crashSite = null;
	private static int crashAge = -1;
	private static CompletableFuture<List<ShipWreck.Part>> wreckPlan;
	private static ShipWreck.Build wreckBuild;

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(DoomFinalEncounter::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> resetRuntime());
		ServerPlayNetworking.registerGlobalReceiver(ModNetwork.DoomBreakDome.TYPE, (payload, ctx) -> tryBreakDome(ctx.player()));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			load(server);
			sync(handler.getPlayer());
		});

		// Also allow left-clicking or right-clicking with Stormbreaker near the dome to shatter it!
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (player instanceof ServerPlayer sp && stage == STAGE_DOME_DUEL && player.getItemInHand(hand).is(ModItems.STORMBREAKER)) {
				if (canBreakDome(sp) && tryBreakDome(sp)) {
					return InteractionResult.SUCCESS;
				}
			}
			return InteractionResult.PASS;
		});
		AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) -> {
			if (player instanceof ServerPlayer sp && stage == STAGE_DOME_DUEL && holdsStormbreaker(sp) && canBreakDome(sp)) {
				if (tryBreakDome(sp)) return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (player instanceof ServerPlayer sp && stage == STAGE_DOME_DUEL && holdsStormbreaker(sp) && canBreakDome(sp)) {
				if (tryBreakDome(sp)) return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}

	public static void resetRuntime() {
		loaded = false;
		stage = STAGE_NONE;
		stageTicks = 0;
		arenaPos = BlockPos.ZERO;
		stoneMask = 0x1F;
		arenaBuilt = false;
		ownerId = null;
		returnId = "";
		victors.clear();
		theftCenter = Vec3.ZERO;
		theftDoomId = -1;
		stolenGauntlet = ItemStack.EMPTY;
		firesCleared = false;
		doom = null;
		thanos = null;
		crashSite = null;
		crashAge = -1;
		wreckPlan = null;
		wreckBuild = null;
	}

	private static void load(MinecraftServer server) {
		if (loaded) return;
		loaded = true;
		SavedState s = server.overworld().getAttached(STATE);
		if (s != null) {
			stage = s.stage();
			arenaPos = s.arenaPos();
			stoneMask = s.stoneMask();
			arenaBuilt = s.arenaBuilt();
			ownerId = s.ownerUuid().isEmpty() ? null : UUID.fromString(s.ownerUuid());
			stolenGauntlet = s.gauntlet().isEmpty() ? buildGauntletFromMask(stoneMask) : s.gauntlet().copy();
			returnId = s.returnId();
			s.victors().stream().map(UUID::fromString).forEach(victors::add);
			if (stage == STAGE_THEFT_CUTSCENE || stage == STAGE_THANOS_ARRIVAL || stage == STAGE_DOME_DUEL
					|| stage == STAGE_KILL_THANOS_CUTSCENE) {
				stage = STAGE_WAITING_OVERWORLD;
			}
			if (stage == STAGE_VICTORY_CUTSCENE) {
				// Resume settlement without replaying a camera sequence whose entities may have unloaded.
				stageTicks = 209;
				if (ownerId != null) victors.add(ownerId);
			}
			if (returnId.isEmpty() && stage > STAGE_NONE && stage < STAGE_DONE) {
				returnId = (stage == STAGE_VICTORY_CUTSCENE ? "legacy:" : "") + UUID.randomUUID();
				save(server);
			}
		}
	}

	private static void save(MinecraftServer server) {
		server.overworld().setAttached(STATE, new SavedState(
				stage, arenaPos, stoneMask, arenaBuilt, ownerId == null ? "" : ownerId.toString(),
				stolenGauntlet.copy(), returnId, victors.stream().map(UUID::toString).toList()));
	}

	public static boolean isActive(MinecraftServer server) {
		load(server);
		return stage >= STAGE_THEFT_CUTSCENE && stage < STAGE_DONE;
	}

	public static int currentStage(MinecraftServer server) {
		load(server);
		return stage;
	}

	public static BlockPos arenaPosition(MinecraftServer server) {
		load(server);
		return arenaPos;
	}

	public static int packStoneMask(ItemStack gauntlet) {
		int mask = 0;
		Set<Stone> inserted = GauntletState.inserted(gauntlet);
		for (Stone st : inserted) mask |= (1 << st.ordinal());
		// A gauntlet with no stones in it is stolen empty and given back empty: the stones are never made up.
		return mask;
	}

	public static ItemStack buildGauntletFromMask(int mask) {
		ItemStack stack = new ItemStack(ModItems.GAUNTLET);
		Set<Stone> stones = EnumSet.noneOf(Stone.class);
		Stone[] all = Stone.values();
		for (int i = 0; i < all.length; i++) {
			if ((mask & (1 << i)) != 0) stones.add(all[i]);
		}
		GauntletState.setInserted(stack, stones);
		return stack;
	}

	/** Called when Doom steals the player's incomplete Gauntlet in Latveria. */
	public static void onGauntletStolen(MinecraftServer server, ServerPlayer victim, ItemStack stolen) {
		load(server);
		stolenGauntlet = stolen.copy();
		GauntletState.setInUse(stolenGauntlet, EnumSet.noneOf(Stone.class));
		stoneMask = packStoneMask(stolen);
		ownerId = victim == null ? null : victim.getUUID();
		returnId = UUID.randomUUID().toString();
		victors.clear();
		ServerLevel overworld = server.overworld();
		BlockPos origin = overworld.getRespawnData().pos();
		if (victim != null) {
			// Check if victim has a return pos in Overworld
			origin = victim.blockPosition();
			if (victim.level().dimension() != Level.OVERWORLD) {
				origin = overworld.getRespawnData().pos();
			}
		}
		// Pick a battlefield site ~200 blocks away in Overworld
		double angle = overworld.getRandom().nextDouble() * Math.TAU;
		int ax = origin.getX() + (int) Math.round(Math.cos(angle) * 210);
		int az = origin.getZ() + (int) Math.round(Math.sin(angle) * 210);
		arenaPos = new BlockPos(ax, 72, az);
		arenaBuilt = false;
		stage = STAGE_THEFT_CUTSCENE;
		stageTicks = 0;
		save(server);
	}

	public static void syncTheftCutscene(ServerLevel latveria, DoctorDoom bossEntity) {
		theftCenter = bossEntity.position();
		theftDoomId = bossEntity.getId();
		syncAll(latveria.getServer());
	}

	private static boolean holdsStormbreaker(ServerPlayer p) {
		return com.dantemorrigan.gauntlet.stormbreaker.Stormbreaker.guarded(p);
	}

	public static boolean canBreakDome(ServerPlayer p) {
		if (stage != STAGE_DOME_DUEL || p.level().dimension() != Level.OVERWORLD
				|| !p.isAlive() || p.isSpectator()) return false;
		Vec3 c = centre();
		double dist = p.position().distanceTo(c);
		return dist <= DOME_RADIUS + 8.5;
	}

	public static boolean tryBreakDome(ServerPlayer p) {
		if (!holdsStormbreaker(p) || !canBreakDome(p)) return false;
		ServerLevel level = (ServerLevel) p.level();
		stage = STAGE_KILL_THANOS_CUTSCENE;
		stageTicks = 0;
		save(level.getServer());
		Vec3 c = centre();
		// Lightning bolt from Stormbreaker onto the dome!
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
		if (bolt != null) {
			bolt.setVisualOnly(true);
			bolt.snapTo(c.x, c.y + DOME_RADIUS * 0.6, c.z);
			level.addFreshEntity(bolt);
		}
		level.playSound(null, arenaPos, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 3.0f, 0.8f);
		level.playSound(null, arenaPos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 3.0f, 0.55f);
		level.playSound(null, arenaPos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2.5f, 0.6f);
		for (ServerPlayer sp : level.players()) {
			if (sp.position().distanceTo(c) < 120) {
				DoomFx.send(sp, DoomFx.SHAKE, 0, c, 1.4f, 25);
				DoomFx.send(sp, DoomFx.FLASH, 0, c, 1.0f, 16);
			}
		}
		syncAll(level.getServer());
		return true;
	}

	public static void onDoomDefeatedStart(ServerLevel level, DoctorDoom defeatedDoom, ServerPlayer killer) {
		if (stage != STAGE_FINAL_BOSS) return;
		stage = STAGE_VICTORY_CUTSCENE;
		stageTicks = 0;
		victors.addAll(defeatedDoom.challengers());
		if (killer != null) {
			victors.add(killer.getUUID());
			if (ownerId == null) ownerId = killer.getUUID();
		}
		if (ownerId != null) victors.add(ownerId);
		save(level.getServer());
		syncAll(level.getServer());
	}

	private static void tick(MinecraftServer server) {
		load(server);
		if (stage == STAGE_NONE) return;
		if (stage == STAGE_DONE) {
			if (server.getTickCount() % 20 == 0) returnGauntlet(server);
			return;
		}
		stageTicks++;
		ServerLevel overworld = server.overworld();

		if (stageTicks % 15 == 0 && (stage != STAGE_VICTORY_CUTSCENE || stageTicks < 210)) syncAll(server);
		if (stage == STAGE_THEFT_CUTSCENE) {
			if (stageTicks >= 136) {
				stage = STAGE_WAITING_OVERWORLD;
				stageTicks = 0;
				save(server);
				syncAll(server);
			}
			return;
		}

		if (crashAge >= 0) tickShipCrash(overworld);
		if (stage == STAGE_VICTORY_CUTSCENE) {
			tickVictoryCutscene(overworld, centre(), stageTicks);
			return;
		}

		// Find nearest player in Overworld near the arena
		ServerPlayer nearest = null;
		double bestDist = Double.POSITIVE_INFINITY;
		for (ServerPlayer p : overworld.players()) {
			if (!p.isAlive() || p.isSpectator()) continue;
			double d = Math.hypot(p.getX() - (arenaPos.getX() + 0.5), p.getZ() - (arenaPos.getZ() + 0.5));
			if (d < bestDist) {
				bestDist = d;
				nearest = p;
			}
		}

		// Build the Sandy-Fiery Battlefield as soon as a player gets within 110 blocks
		if (!arenaBuilt && nearest != null && bestDist <= 110) {
			buildSandyFieryArena(overworld);
			arenaBuilt = true;
			save(server);
		}

		if (!arenaBuilt) return;
		if (!firesCleared && nearest != null && bestDist <= 110) {
			firesCleared = true;
			clearFires(overworld);
		}
		Vec3 center = centre();

		switch (stage) {
			case STAGE_WAITING_OVERWORLD -> {
				ensureDoomPresent(overworld, center);
				if (doom != null && stageTicks % 8 == 0) {
					overworld.sendParticles(ModParticles.DOOM_RUNE_MOTE, doom.getX(), doom.getY() + 1.5, doom.getZ(), 2, 0.4, 0.4, 0.4, 0.02);
				}
				if (nearest != null && nearest.position().distanceTo(center) <= 16.5) {
					// Push player back and start Cutscene 2 (Thanos Arrival & Dome Duel)!
					Vec3 dir = nearest.position().subtract(center);
					Vec3 flat = new Vec3(dir.x, 0, dir.z);
					flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
					Vec3 pushTo = center.add(flat.scale(23.5));
					nearest.teleportTo(pushTo.x, center.y + 0.5, pushTo.z);
					nearest.setDeltaMovement(flat.scale(1.1).add(0, 0.45, 0));
					nearest.hurtMarked = true;
					if (doom != null) doom.face(nearest.position());
					DoomFx.flat(overworld, ModParticles.DOOM_SLAM_WAVE, center, 8);
					DoomFx.sound(overworld, center, SoundEvents.WARDEN_SONIC_BOOM, 2.0f, 0.8f);
					DoomFx.line(overworld, center, 80, "doom.arena_push");
					beginThanosArrival(overworld, center, nearest);
				}
			}
			case STAGE_THANOS_ARRIVAL -> tickThanosArrival(overworld, center, stageTicks);
			case STAGE_DOME_DUEL -> tickDomeDuel(overworld, center);
			case STAGE_KILL_THANOS_CUTSCENE -> tickKillThanosCutscene(overworld, center, stageTicks);
			case STAGE_FINAL_BOSS -> tickFinalBoss(overworld, center, nearest);
		}
	}

	private static void ensureDoomPresent(ServerLevel overworld, Vec3 center) {
		if (!overworld.isLoaded(arenaPos)) return;
		// A Doom left sunk into the floor (an older save placed him inside the ground block) is lifted onto it.
		if (doom != null && !doom.isRemoved() && doom.isAlive() && doom.getY() < center.y - 0.4
				&& Math.hypot(doom.getX() - center.x, doom.getZ() - center.z) < ARENA_RADIUS) {
			doom.teleportTo(doom.getX(), center.y, doom.getZ());
		}
		if (doom == null || doom.isRemoved() || !doom.isAlive()) {
			doom = ModEntities.DOCTOR_DOOM.create(overworld, EntitySpawnReason.EVENT);
			if (doom == null) return;
			if (stolenGauntlet.isEmpty()) stolenGauntlet = buildGauntletFromMask(stoneMask);
			doom.setupArenaWait(center, stolenGauntlet);
			doom.snapTo(center.x, center.y, center.z, 0f, 0f);
			overworld.addFreshEntity(doom);
		}
	}

	private static void beginThanosArrival(ServerLevel overworld, Vec3 center, ServerPlayer triggerPlayer) {
		stage = STAGE_THANOS_ARRIVAL;
		stageTicks = 0;
		save(overworld.getServer());
		ensureDoomPresent(overworld, center);
		if (doom != null) {
			Vec3 doomSpot = center.add(-5.5, 0, 0);
			doom.teleportTo(doomSpot.x, center.y, doomSpot.z);
			doom.face(center.add(5.5, 0, 0));
		}
		shipHover = center.add(28, 76, 0);
		shipDir = new Vec3(-1, 0, 0);
		shipYaw = (float) Math.toDegrees(Math.atan2(-shipDir.x, -shipDir.z));
		for (ServerPlayer p : overworld.players()) {
			if (p.position().distanceTo(center) < 180) {
				ServerPlayNetworking.send(p, new ModNetwork.ThanosFx(ThanosEncounter.FX_SHIP_ARRIVE, 0, shipHover, shipDir, shipYaw, 90));
			}
		}
		overworld.playSound(null, arenaPos, SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 3.0f, 0.4f);
		syncAll(overworld.getServer());
	}

	private static void tickThanosArrival(ServerLevel overworld, Vec3 center, int t) {
		keepPlayersOutsideDome(overworld, center);
		Vec3 landing = center.add(5.5, 0, 0);
		if (t == 55) {
			for (ServerPlayer p : overworld.players()) {
				if (p.position().distanceTo(center) < 180) {
					ServerPlayNetworking.send(p, new ModNetwork.ThanosFx(ThanosEncounter.FX_BEAM, 0, landing, shipHover, shipYaw, 55));
				}
			}
			overworld.playSound(null, BlockPos.containing(landing), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 4f, 0.5f);
		}
		if (t == 68) {
			if (thanos != null && !thanos.isRemoved()) thanos.discard();
			thanos = ModEntities.THANOS.create(overworld, EntitySpawnReason.EVENT);
			if (thanos != null) {
				thanos.setHasGauntlet(false);
				thanos.snapTo(landing.x, landing.y + 22, landing.z, 90f, 0f);
				thanos.faceTowards(center.add(-5.5, 0, 0));
				thanos.script(Thanos.A_DESCEND, landing.add(0, 22, 0), landing, 36);
				overworld.addFreshEntity(thanos);
			}
		}
		if (t == 104 && thanos != null) {
			for (ServerPlayer p : overworld.players()) {
				if (p.position().distanceTo(center) < 180) {
					ServerPlayNetworking.send(p, new ModNetwork.ThanosFx(ThanosEncounter.FX_IMPACT, 0, landing, Vec3.ZERO, 1.4f, 0));
				}
			}
			overworld.playSound(null, BlockPos.containing(landing), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3f, 0.5f);
			thanos.faceTowards(center.add(-5.5, 0, 0));
			thanos.pose(Thanos.A_INTRO);
			ThanosEncounter.line(thanos, ThanosEncounter.LINE_GIVE_STONES);
		}
		if (t == 160) {
			DoomFx.line(overworld, center, 90, "doom.thanos_reply");
			if (doom != null) doom.face(landing);
		}
		if (t == 205) {
			// Dome rises around Doom and Thanos!
			overworld.playSound(null, arenaPos, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.HOSTILE, 3.0f, 0.6f);
			overworld.playSound(null, arenaPos, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3.0f, 0.8f);
			DoomFx.flat(overworld, ModParticles.DOOM_SLAM_WAVE, center, 16);
		}
		if (t >= 225) {
			stage = STAGE_DOME_DUEL;
			stageTicks = 0;
			save(overworld.getServer());
			if (doom != null && thanos != null) {
				doom.beginDuelWithThanos(thanos, center);
				thanos.beginDuelWithMob(doom);
			}
			syncAll(overworld.getServer());
		}
	}

	private static void tickDomeDuel(ServerLevel overworld, Vec3 center) {
		keepPlayersOutsideDome(overworld, center);
		if (!overworld.isLoaded(arenaPos)) return;
		ensureDoomPresent(overworld, center);
		if (thanos == null || thanos.isRemoved() || !thanos.isAlive()) {
			Vec3 landing = center.add(5.5, 0, 0);
			thanos = ModEntities.THANOS.create(overworld, EntitySpawnReason.EVENT);
			if (thanos != null) {
				thanos.setHasGauntlet(false);
				thanos.snapTo(landing.x, landing.y, landing.z, 90f, 0f);
				overworld.addFreshEntity(thanos);
			}
		}
		if (doom != null && thanos != null) {
			if (!doom.fighting()) doom.beginDuelWithThanos(thanos, center);
			if (!thanos.fighting()) thanos.beginDuelWithMob(doom);
			// Keep both combatants inside the dome radius (16.5 blocks) and healthy
			clampInsideDome(doom, center, 16.0);
			clampInsideDome(thanos, center, 16.0);
			doom.setHealth(doom.getMaxHealth());
			thanos.setHealth(thanos.getMaxHealth());
		}
	}

	private static void clampInsideDome(net.minecraft.world.entity.LivingEntity entity, Vec3 center, double maxR) {
		Vec3 off = entity.position().subtract(center);
		double d = Math.hypot(off.x, off.z);
		if (d > maxR) {
			Vec3 clamped = center.add(off.x * (maxR / d), 0, off.z * (maxR / d));
			entity.teleportTo(clamped.x, center.y, clamped.z);
		}
	}

	private static void keepPlayersOutsideDome(ServerLevel overworld, Vec3 center) {
		for (ServerPlayer p : overworld.players()) {
			if (!p.isAlive() || p.isSpectator()) continue;
			Vec3 off = p.position().subtract(center);
			double d = Math.hypot(off.x, off.z);
			if (d < DOME_RADIUS + 0.6 && off.y < DOME_RADIUS + 2) {
				Vec3 dir = d < 1e-3 ? new Vec3(0, 0, 1) : new Vec3(off.x / d, 0, off.z / d);
				Vec3 out = center.add(dir.scale(DOME_RADIUS + 1.2));
				p.teleportTo(out.x, Math.max(center.y, p.getY()), out.z);
				p.setDeltaMovement(dir.scale(0.55).add(0, 0.2, 0));
				p.hurtMarked = true;
				overworld.sendParticles(ParticleTypes.END_ROD, out.x, p.getY() + 1.0, out.z, 8, 0.3, 0.4, 0.3, 0.05);
				overworld.playSound(null, p.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.BLOCKS, 0.9f, 1.4f);
			}
		}
	}

	private static void tickKillThanosCutscene(ServerLevel overworld, Vec3 center, int t) {
		ensureDoomPresent(overworld, center);
		if (t == 1) {
			// Dome shatters into glowing fragments!
			for (int i = 0; i < 90; i++) {
				double theta = overworld.getRandom().nextDouble() * Math.TAU;
				double phi = overworld.getRandom().nextDouble() * (Math.PI * 0.48);
				double r = DOME_RADIUS * Math.cos(phi);
				double x = center.x + Math.cos(theta) * r;
				double y = center.y + DOME_RADIUS * Math.sin(phi);
				double z = center.z + Math.sin(theta) * r;
				overworld.sendParticles(ParticleTypes.END_ROD, x, y, z, 2, 0.3, 0.3, 0.3, 0.12);
				overworld.sendParticles(ModParticles.DOOM_RUNE_MOTE, x, y, z, 1, 0.2, 0.2, 0.2, 0.05);
			}
			if (doom != null) {
				doom.teleportTo(center.x - 3.5, center.y, center.z);
				if (thanos != null) doom.face(thanos.position());
			}
			if (thanos != null) {
				thanos.teleportTo(center.x + 3.5, center.y, center.z);
				thanos.faceTowards(center.add(-3.5, 0, 0));
				thanos.pose(Thanos.A_STAGGER);
			}
		}
		if (t == 30 && doom != null && thanos != null) {
			// Doom channels all stones + magic into Thanos!
			DoomVfx.on(overworld, DoomVfx.CHARGE, doom, 1.6f, 35, DoomVfx.SUN);
			DoomFx.sound(overworld, center, SoundEvents.BEACON_POWER_SELECT, 2.5f, 0.55f);
			DoomFx.line(overworld, center, 100, "doom.kill_thanos_1");
		}
		if (t >= 45 && t <= 85 && doom != null && thanos != null && t % 2 == 0) {
			Vec3 from = doom.position().add(0, 1.7, 0);
			Vec3 to = thanos.position().add(0, 1.5, 0);
			DoomVfx.line(overworld, DoomVfx.TRAIL, from, to, 0.65f, 4, DoomVfx.SUN);
			DoomVfx.line(overworld, DoomVfx.TRAIL, from, to, 0.38f, 4, DoomVfx.GREEN);
		}
		if (t == 86 && thanos != null) {
			Vec3 tPos = thanos.position();
			DoomVfx.at(overworld, DoomVfx.COLUMN, tPos.add(0, 0.1, 0), 3.2f, 20, DoomVfx.EDICT_GREEN);
			DoomVfx.at(overworld, DoomVfx.IMPACT, tPos.add(0, 1.5, 0), 3.4f, 18, DoomVfx.SUN);
			DoomFx.sound(overworld, tPos, SoundEvents.WARDEN_SONIC_BOOM, 3.0f, 0.6f);
			DoomFx.sound(overworld, tPos, SoundEvents.GENERIC_EXPLODE.value(), 2.5f, 0.55f);
			DoomFx.near(overworld, center, 100, DoomFx.SHAKE, 0, 1.5f, 20);
			DoomFx.near(overworld, center, 100, DoomFx.FLASH, 0, 0.9f, 14);
			thanos.killInCutscene(overworld);
		}
		if (t == 105 && doom != null) {
			// Doom fires a colossal beam into the sky at Sanctuary II!
			DoomFx.line(overworld, center, 100, "doom.kill_thanos_2");
			Vec3 from = doom.position().add(0, 2.0, 0);
			DoomVfx.line(overworld, DoomVfx.TRAIL, from, shipHover, 1.4f, 26, DoomVfx.EDICT_GREEN);
			DoomVfx.line(overworld, DoomVfx.TRAIL, from, shipHover, 0.8f, 26, DoomVfx.SUN);
			overworld.playSound(null, arenaPos, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 4.0f, 0.5f);
			for (ServerPlayer p : overworld.players()) {
				if (p.position().distanceTo(center) < 200) {
					ServerPlayNetworking.send(p, new ModNetwork.ThanosFx(ThanosEncounter.FX_DRAGON_RAY, 0, shipHover, from, 0, 20));
				}
			}
		}
		if (t == 120) {
			// Sanctuary II explodes and crashes down!
			startShipCrash(overworld, center);
		}
		if (t == 185 && doom != null) {
			DoomFx.line(overworld, center, 100, "doom.final_challenge");
		}
		if (t >= 250) {
			stage = STAGE_FINAL_BOSS;
			stageTicks = 0;
			save(overworld.getServer());
			if (doom != null) {
				doom.beginFinalPlayerFight(center);
			}
			syncAll(overworld.getServer());
		}
	}

	private static void startShipCrash(ServerLevel overworld, Vec3 center) {
		int cx = BlockPos.containing(shipHover).getX() + 45;
		int cz = BlockPos.containing(shipHover).getZ() + 25;
		int cy = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
		crashSite = new Vec3(cx + 0.5, cy, cz + 0.5);
		crashAge = 0;
		if (GauntletConfig.get().thanosShipWreck) {
			float yaw = shipYaw + 28f, roll = 19f;
			wreckPlan = CompletableFuture.supplyAsync(() -> ShipWreck.plan(yaw, -27f, roll));
		}
		for (ServerPlayer p : overworld.players()) {
			if (p.position().distanceTo(center) < 260) {
				ServerPlayNetworking.send(p, new ModNetwork.ThanosFx(
						ThanosEncounter.FX_SHIP_CRASH, 2, shipHover, crashSite, shipYaw, ThanosEncounter.CRASH_TICKS));
			}
		}
		overworld.playSound(null, BlockPos.containing(shipHover), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 8f, 0.4f);
	}

	private static void tickShipCrash(ServerLevel overworld) {
		crashAge++;
		if (crashAge == ThanosEncounter.CRASH_TICKS && crashSite != null) {
			overworld.explode(null, crashSite.x, crashSite.y + 2, crashSite.z, 7f, Level.ExplosionInteraction.NONE);
			overworld.playSound(null, BlockPos.containing(crashSite), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 14f, 0.3f);
		}
		if (crashAge > ThanosEncounter.CRASH_TICKS && wreckPlan != null && wreckBuild == null && wreckPlan.isDone() && !wreckPlan.isCompletedExceptionally()) {
			wreckBuild = new ShipWreck.Build(overworld, BlockPos.containing(crashSite), wreckPlan.join());
		}
		if (wreckBuild != null && !wreckBuild.done()) {
			wreckBuild.step(2000);
			if (wreckBuild.done()) {
				for (ServerPlayer p : overworld.players()) {
					if (p.position().distanceTo(crashSite) < 260) {
						ServerPlayNetworking.send(p, new ModNetwork.ThanosFx(ThanosEncounter.FX_WRECK_DONE, 0, crashSite, Vec3.ZERO, 0, 0));
					}
				}
			}
		}
		if (crashAge > ThanosEncounter.CRASH_TICKS + 400) {
			crashAge = -1;
		}
	}

	private static void tickFinalBoss(ServerLevel overworld, Vec3 center, ServerPlayer nearest) {
		if (!overworld.isLoaded(arenaPos)) return;
		if (doom == null || doom.isRemoved()) {
			if (nearest != null && nearest.position().distanceTo(center) < 64) {
				ensureDoomPresent(overworld, center);
				if (doom != null) doom.beginFinalPlayerFight(center);
			}
		}
	}

	private static void tickVictoryCutscene(ServerLevel overworld, Vec3 center, int t) {
		MinecraftServer server = overworld.getServer();
		if (t == 60) {
			overworld.playSound(null, arenaPos, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.5f, 1.2f);
		}
		if (t >= 114) returnGauntlet(server);
		if (t < 210) return;
		ThanosEncounter.markDefeated(server);
		ServerLevel latveria = server.getLevel(Latveria.LEVEL);
		if (latveria != null && !victors.isEmpty()) Latveria.conquer(latveria, victors);
		for (ServerPlayer p : overworld.players()) {
			if (p.position().distanceTo(center) < 128) {
				ServerPlayNetworking.send(p, new ModNetwork.QuestAlert(
						Component.translatable("msg.infinity_gauntlet.latveria.doom.final_victory_alert"), 0));
				ServerPlayNetworking.send(p, new ModNetwork.ThanosFx(ThanosEncounter.FX_END, 0, Vec3.ZERO, Vec3.ZERO, 0, 0));
			}
		}
		stage = STAGE_DONE;
		stageTicks = 0;
		save(server);
		syncAll(server);
	}

	private static void returnGauntlet(MinecraftServer server) {
		if (ownerId == null || returnId.isEmpty() || stolenGauntlet.isEmpty()) return;
		ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
		if (owner == null || !owner.isAlive() || owner.isSpectator() || returnId.equals(owner.getAttached(GAUNTLET_RECEIPT))) return;
		// Old victory saves do not record whether the old cutscene already delivered the reconstructed gauntlet.
		if (returnId.startsWith("legacy:") && hasGauntletWithSameStones(owner)) {
			owner.setAttached(GAUNTLET_RECEIPT, returnId);
			return;
		}
		boolean equipped = owner.getOffhandItem().isEmpty();
		if (equipped) {
			owner.setItemSlot(EquipmentSlot.OFFHAND, stolenGauntlet.copy());
		} else if (owner.getInventory().getFreeSlot() < 0 || !owner.getInventory().add(stolenGauntlet.copy())) {
			return; // Keep the claim pending until the owner frees a slot.
		}
		owner.setAttached(GAUNTLET_RECEIPT, returnId);
		owner.inventoryMenu.broadcastChanges();
		if (equipped) GauntletGesture.start(owner);
		owner.level().playSound(null, owner.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.8f, 0.85f);
		owner.level().playSound(null, owner.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.5f, 1.0f);
	}

	private static boolean hasGauntletWithSameStones(ServerPlayer owner) {
		var inventory = owner.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(ModItems.GAUNTLET) && packStoneMask(stack) == stoneMask) return true;
		}
		ItemStack offhand = owner.getOffhandItem();
		return offhand.is(ModItems.GAUNTLET) && packStoneMask(offhand) == stoneMask;
	}

	/** The middle of the battlefield, standing height: one above the floor block at {@link #arenaPos}, so nobody is placed inside the ground. */
	private static Vec3 centre() { return Vec3.atBottomCenterOf(arenaPos).add(0, 1, 0); }

	private static boolean firesCleared;

	/** Puts out the flames an older version of the battlefield set burning: they blocked the view and dragged the game down. */
	private static void clearFires(ServerLevel overworld) {
		int r = (int) ARENA_RADIUS + 3;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (!overworld.isLoaded(at.set(arenaPos.getX() + dx, arenaPos.getY(), arenaPos.getZ() + dz))) continue;
				for (int y = arenaPos.getY() + 1; y <= arenaPos.getY() + 8; y++) {
					at.set(arenaPos.getX() + dx, y, arenaPos.getZ() + dz);
					if (overworld.getBlockState(at).is(Blocks.FIRE)) overworld.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
	}

	/** Shapes the huge Sandy-Fiery Battlefield in the Overworld at {@link #arenaPos}. */
	private static void buildSandyFieryArena(ServerLevel overworld) {
		int cx = arenaPos.getX();
		int cz = arenaPos.getZ();
		overworld.getChunk(cx >> 4, cz >> 4);
		int baseY = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
		baseY = Mth.clamp(baseY, overworld.getSeaLevel() + 1, 140);
		arenaPos = new BlockPos(cx, baseY, cz);
		var rng = overworld.getRandom();
		int r = (int) ARENA_RADIUS;

		for (int dx = -r - 3; dx <= r + 3; dx++) {
			for (int dz = -r - 3; dz <= r + 3; dz++) {
				double dist = Math.hypot(dx, dz);
				if (dist > r + 2.5) continue;
				int x = cx + dx, z = cz + dz;
				overworld.getChunk(x >> 4, z >> 4);
				// Clear trees/leaves/obstacles above the arena
				for (int y = baseY + 1; y <= baseY + 18; y++) {
					BlockPos up = new BlockPos(x, y, z);
					if (!overworld.getBlockState(up).isAir()) {
						overworld.setBlock(up, Blocks.AIR.defaultBlockState(), 2);
					}
				}
				// Fill support underneath if over a dip or water
				for (int y = baseY - 4; y < baseY; y++) {
					BlockPos under = new BlockPos(x, y, z);
					overworld.setBlock(under, Blocks.SANDSTONE.defaultBlockState(), 2);
				}
				// Surface block: sandy-fiery mix of sand, red sand, magma, netherrack, and scorched blackstone
				BlockPos floor = new BlockPos(x, baseY, z);
				double noise = Math.sin(dx * 0.23) * Math.cos(dz * 0.23) + (rng.nextDouble() - 0.5) * 0.8;
				BlockState surface;
				if (dist > r - 1.8) {
					surface = rng.nextBoolean() ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState();
				} else if (noise > 0.55) {
					surface = Blocks.MAGMA_BLOCK.defaultBlockState();
				} else if (noise > 0.25) {
					surface = Blocks.RED_SAND.defaultBlockState();
				} else if (noise < -0.45) {
					surface = Blocks.NETHERRACK.defaultBlockState();
				} else if (noise < -0.2) {
					surface = Blocks.BLACKSTONE.defaultBlockState();
				} else {
					surface = Blocks.SAND.defaultBlockState();
				}
				overworld.setBlock(floor, surface, 3);

			}
		}

		// Scorched obsidian/blackstone spires around the rim of the battlefield
		for (int i = 0; i < 12; i++) {
			double a = i * (Math.TAU / 12.0);
			int px = cx + (int) Math.round(Math.cos(a) * (r - 1));
			int pz = cz + (int) Math.round(Math.sin(a) * (r - 1));
			int h = 3 + (i % 3);
			for (int dy = 1; dy <= h; dy++) {
				BlockPos spire = new BlockPos(px, baseY + dy, pz);
				overworld.setBlock(spire, dy == h ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState(), 3);
			}
		}
	}

	public static void syncAll(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) sync(p);
	}

	private static void sync(ServerPlayer viewer) {
		if (!ServerPlayNetworking.canSend(viewer, ModNetwork.DoomFinalSync.TYPE)) return;
		ServerPlayer owner = ownerId == null ? null : viewer.level().getServer().getPlayerList().getPlayer(ownerId);
		if (stage == STAGE_THEFT_CUTSCENE && viewer.level().dimension() == Latveria.LEVEL && theftDoomId >= 0) {
			int actor = owner != null && owner.level() == viewer.level() ? owner.getId() : -1;
			ServerPlayNetworking.send(viewer, new ModNetwork.DoomFinalSync(stage, stageTicks, theftCenter, theftDoomId, -1, stoneMask, actor));
			return;
		}
		if (stage < STAGE_WAITING_OVERWORLD || viewer.level().dimension() != Level.OVERWORLD) {
			ServerPlayNetworking.send(viewer, new ModNetwork.DoomFinalSync(STAGE_NONE, 0, Vec3.ZERO, -1, -1, 0, -1));
			return;
		}
		int dId = doom != null && !doom.isRemoved() ? doom.getId() : -1;
		int tId = thanos != null && !thanos.isRemoved() ? thanos.getId() : -1;
		int recipient = stage == STAGE_VICTORY_CUTSCENE && owner != null && owner.level() == viewer.level()
				&& owner.isAlive() && owner.position().distanceToSqr(centre()) <= 96 * 96 ? owner.getId() : -1;
		ServerPlayNetworking.send(viewer, new ModNetwork.DoomFinalSync(stage, stageTicks, centre(), dId, tId, stoneMask, recipient));
	}

	public static void debugTpArena(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		load(server);
		if (stage == STAGE_NONE || stage == STAGE_DONE || arenaPos.equals(BlockPos.ZERO)) {
			ItemStack g = buildGauntletFromMask(0x1F);
			onGauntletStolen(server, player, g);
			stage = STAGE_WAITING_OVERWORLD;
			save(server);
		}
		ServerLevel overworld = server.overworld();
		if (!arenaBuilt) {
			buildSandyFieryArena(overworld);
			arenaBuilt = true;
			save(server);
		}
		ensureDoomPresent(overworld, centre());
		Vec3 target = new Vec3(arenaPos.getX() + 0.5, arenaPos.getY() + 1.0, arenaPos.getZ() + DOME_RADIUS + 4.0);
		player.teleport(new net.minecraft.world.level.portal.TeleportTransition(
				overworld, target, Vec3.ZERO, 180f, 0f, net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
		syncAll(server);
	}

	public static boolean debugForceBreakDome(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		load(server);
		if (stage != STAGE_DOME_DUEL) return false;
		stage = STAGE_KILL_THANOS_CUTSCENE;
		stageTicks = 0;
		save(server);
		syncAll(server);
		return true;
	}

	public static void debugReset(MinecraftServer server) {
		if (doom != null && !doom.isRemoved()) doom.discard();
		if (thanos != null && !thanos.isRemoved()) thanos.discard();
		resetRuntime();
		loaded = true;
		save(server);
		syncAll(server);
	}

	private DoomFinalEncounter() {}
}
