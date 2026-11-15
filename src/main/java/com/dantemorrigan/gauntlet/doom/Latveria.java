package com.dantemorrigan.gauntlet.doom;

import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModAdvancements;
import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.power.SpaceStone;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The road into Latveria and the assault on Castle Doom.
 *
 * <p>Access: the Space Stone shows the land once and Doom seals the portal. When the traveller has beaten Loki and
 * carries the Mind Stone, the stone reads the seal and the way opens. Whoever goes in takes along everyone standing
 * next to them. The gate in the valley, the Latveria Key and the command lead back out.
 *
 * <p>Assault: the gate squad holds the portcullis and the yard squad holds the throne room doors. The other way is
 * the service door on the east wall: it leads to the laboratory, whose generator feeds Doom's armor and the Bastions'
 * shields. Switching it off also opens the side door into the throne room.
 *
 * <p>The state of the assault is stored with the world; Doom himself is not, so an abandoned fight starts afresh.
 */
public final class Latveria {
	public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, InfinityGauntlet.id("latveria"));
	private static final int GATE = 1, THRONE = 2, GENERATOR = 4, CONQUERED = 8, ALARMED = 16;
	private static final int[][] SQUADS = {
			{Doombot.SOLDIER, Doombot.SOLDIER},
			{Doombot.BASTION, Doombot.SOLDIER, Doombot.SOLDIER, Doombot.SOLDIER, Doombot.ENGINEER},
			{Doombot.SOLDIER, Doombot.SOLDIER, Doombot.SOLDIER, Doombot.INTERCEPTOR, Doombot.INTERCEPTOR, Doombot.ARCANIST, Doombot.ENGINEER},
			{Doombot.ENGINEER, Doombot.ENGINEER, Doombot.ARCANIST, Doombot.SOLDIER}};
	private static final Vec3[] SQUAD_AT = {
			new Vec3(0.5, LatveriaPlan.roadHeight(-120) + 1.6, -120.5), new Vec3(0.5, LatveriaPlan.FLOOR, -195.5),
			new Vec3(0.5, LatveriaPlan.FLOOR, -231.5), new Vec3(31.5, LatveriaPlan.FLOOR, -264.5)};
	public static final int SQUAD_ROAD = 0, SQUAD_GATE = 1, SQUAD_YARD = 2, SQUAD_LAB = 3;

	private record Save(int spawned, List<Integer> alive, int flags, List<String> owners, List<Long> furnished) {
		static final Codec<Save> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("spawned").forGetter(Save::spawned), Codec.INT.listOf().fieldOf("alive").forGetter(Save::alive),
				Codec.INT.fieldOf("flags").forGetter(Save::flags), Codec.STRING.listOf().fieldOf("owners").forGetter(Save::owners),
				Codec.LONG.listOf().fieldOf("furnished").forGetter(Save::furnished)).apply(i, Save::new));
	}

	private static final AttachmentType<Save> STATE = AttachmentRegistry.<Save>builder().persistent(Save.CODEC)
			.buildAndRegister(InfinityGauntlet.id("latveria_state"));
	/** The seal is read (or a Castle Seal was accepted): this player may travel to Latveria. */
	private static final AttachmentType<Boolean> ACCESS = AttachmentRegistry.<Boolean>builder().persistent(Codec.BOOL).copyOnDeath()
			.buildAndRegister(InfinityGauntlet.id("latveria_access"));
	private static final AttachmentType<Boolean> LOGO_SEEN = AttachmentRegistry.<Boolean>builder().persistent(Codec.BOOL).copyOnDeath()
			.buildAndRegister(InfinityGauntlet.id("latveria_logo_seen"));
	private static final AttachmentType<Boolean> REWARDED = AttachmentRegistry.<Boolean>builder().persistent(Codec.BOOL).copyOnDeath()
			.buildAndRegister(InfinityGauntlet.id("doom_rewarded"));
	private static final AttachmentType<String> RETURN_DIM = AttachmentRegistry.<String>builder().persistent(Codec.STRING).copyOnDeath()
			.buildAndRegister(InfinityGauntlet.id("latveria_return_dim"));
	private static final AttachmentType<BlockPos> RETURN_POS = AttachmentRegistry.<BlockPos>builder().persistent(BlockPos.CODEC).copyOnDeath()
			.buildAndRegister(InfinityGauntlet.id("latveria_return_pos"));

	// Runtime view of the saved state.
	private static boolean loaded;
	private static int spawned, flags;
	private static final int[] alive = new int[SQUADS.length];
	private static final Set<UUID> owners = new HashSet<>();
	private static final Set<Long> furnished = new HashSet<>();
	private static final Set<Long> furnitureChunks = new HashSet<>();
	private static final Deque<Long> toFurnish = new ArrayDeque<>();
	private static final Map<UUID, Integer> inGate = new HashMap<>(), logoDue = new HashMap<>();
	private static DoctorDoom boss;
	private static int emptyArena;
	/** Debug: Doom stays on his throne until the hall is empty or he is summoned by command. */
	private static boolean held;

	// ------------------------------------------------------------------ the city cannot be broken

	private static int editing;
	private static boolean bypass;
	/** Blocks visitors put down in Latveria: theirs to take back, unlike the city's own. Kept on the Latveria level. */
	private static final AttachmentType<List<Long>> PLACED = AttachmentRegistry.<List<Long>>builder().persistent(Codec.LONG.listOf())
			.buildAndRegister(com.dantemorrigan.gauntlet.InfinityGauntlet.id("latveria_placed"));
	private static it.unimi.dsi.fastutil.longs.LongOpenHashSet placed;
	private static boolean placedDirty;

	private static it.unimi.dsi.fastutil.longs.LongOpenHashSet placed(ServerLevel level) {
		if (placed == null) {
			placed = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
			List<Long> saved = level.getAttached(PLACED);
			if (saved != null) placed.addAll(saved);
		}
		return placed;
	}

	private static void flushPlaced(MinecraftServer server) {
		ServerLevel level = server.getLevel(LEVEL);
		if (!placedDirty || placed == null || level == null) return;
		placedDirty = false;
		level.setAttached(PLACED, List.copyOf(placed));
	}

	/** Runs Latveria's own block changes (gates, generator, furniture, Doom's seal nodes) past the protection. */
	public static void edit(Runnable change) {
		editing++;
		try { change.run(); } finally { editing--; }
	}

	/**
	 * True when {@code next} may not replace the block at {@code pos}: in Latveria the city, the castle and the ground stay
	 * as built, whatever beam, blast or stone hits them, so the route of the assault cannot be cut short. Air, grass, fire
	 * and other replaceable blocks may change, and so may a block's own state (doors, trapdoors, levers).
	 */
	public static boolean protects(net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.level.block.state.BlockState next) {
		if (editing > 0 || level.isClientSide() || level.dimension() != LEVEL || !(level instanceof ServerLevel sl)) return false;
		net.minecraft.world.level.block.state.BlockState old = level.getBlockState(pos);
		// Fire does not spread here: only a power lights a flame, and it goes out by itself (TempFire).
		if (next.getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock && !com.dantemorrigan.gauntlet.power.TempFire.placing) return true;
		if (old.isAir() || old.getBlock() == next.getBlock()) return false;
		if (bypass || placed(sl).contains(pos.asLong())) return false;
		// Vines, flowers, grass and water are Latveria's too: a visitor may build over them, but no blast, flame or beam clears them.
		if (old.canBeReplaced()) return next.isAir() || next.getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock || !next.getFluidState().isEmpty();
		return true;
	}

	/** Bookkeeping after a change was let through: a block set down in the open becomes the visitor's, a cleared one is forgotten. */
	public static void changed(net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.level.block.state.BlockState old, net.minecraft.world.level.block.state.BlockState next) {
		if (level.isClientSide() || level.dimension() != LEVEL || !(level instanceof ServerLevel sl)) return;
		if (next.isAir() || next.canBeReplaced()) {
			// A cleared spot is forgotten whoever cleared it, Latveria's own edits included.
			if (placed(sl).remove(pos.asLong())) placedDirty = true;
			return;
		}
		// Only what a visitor sets down counts as theirs: not Latveria's own edits, not ice or snow the weather makes.
		if (editing > 0 || next.is(net.minecraft.tags.BlockTags.ICE) || next.is(net.minecraft.tags.BlockTags.SNOW)) return;
		if ((old.isAir() || old.canBeReplaced()) && placed(sl).add(pos.asLong())) placedDirty = true;
	}

	public static void init() {
		DoomItems.Heart.init();
		LatveriaQuests.init();
		// The whole gauntlet beaten: a bearer who would fall to Doom is spared for the ascension instead.
		// If the bearer has <= 5 stones, Doom steals the gauntlet and teleports to the Overworld!
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer p) || p.level().dimension() != LEVEL || boss == null || !boss.isAlive()) return true;
			if (boss.ascending() || boss.stealingGauntlet()) { p.setHealth(2); return false; }
			if (!boss.fighting() || !(source.getEntity() instanceof DoctorDoom || source.getEntity() instanceof Doombot b && !b.allied())) return true;
			var g = com.dantemorrigan.gauntlet.GauntletState.activeGauntlet(p);
			if (!g.isEmpty() && com.dantemorrigan.gauntlet.GauntletState.inserted(g).size() >= 6) {
				p.setHealth(1);
				boss.ascend(p.level(), p);
				return false;
			}
			if (DoctorDoom.canStealGauntletFrom(p)) {
				p.setHealth(6);
				boss.stealGauntletAndFlee(p.level(), p);
				return false;
			}
			return true;
		});
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof ServerPlayer && entity.level().dimension() == LEVEL && boss != null && (boss.ascending() || boss.stealingGauntlet())));
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			if (level.dimension() != LEVEL) return true;
			if (player.isCreative()) { bypass = true; return true; }
			return !protects(level, pos, Blocks.AIR.defaultBlockState());
		});
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> bypass = false);
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.CANCELED.register((level, player, pos, state, be) -> bypass = false);
		ServerTickEvents.START_SERVER_TICK.register(server -> bypass = false);
		for (LatveriaPlan.Op op : LatveriaPlan.FURNITURE) furnitureChunks.add(ChunkPos.pack(op.x0() >> 4, op.z0() >> 4));
		for (BlockPos p : LatveriaPlan.VILLAGERS) furnitureChunks.add(ChunkPos.pack(p.getX() >> 4, p.getZ() >> 4));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> { DoomItems.TimePlatforms.tick(true); flushPlaced(server); });
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			loaded = false;
			boss = null;
			held = false;
			toFurnish.clear();
			placed = null;
			Searchlights.clear();
			LatveriaHunt.clear();
			GauntletSilence.clear();
			LatveriaCourt.clear();
			placedDirty = false;
			inGate.clear();
			logoDue.clear();
			DoomCombat.clear();
			DoomArmor.clear();
			LatveriaQuests.resetRuntime();
		});
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, fresh) -> {
			if (level.dimension() == LEVEL && furnitureChunks.contains(chunk.getPos().pack())) toFurnish.add(chunk.getPos().pack());
		});
		ServerTickEvents.END_SERVER_TICK.register(Latveria::tick);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(player instanceof ServerPlayer sp) || level.dimension() != LEVEL) return InteractionResult.PASS;
			BlockPos p = hit.getBlockPos(), g = LatveriaPlan.GENERATOR;
			if (LatveriaCourt.isThrone(p) && LatveriaCourt.sit(sp)) return InteractionResult.SUCCESS;
			if (LatveriaQuests.isGeneratorBlock(p) && (sp.getMainHandItem().is(ModItems.GENERATOR_DISRUPTOR)
					|| sp.getOffhandItem().is(ModItems.GENERATOR_DISRUPTOR)
					|| LatveriaQuests.UNDER_CASTLE_GEN_BOX.contains(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5))) {
				return LatveriaQuests.attachDisruptor(sp, p) ? InteractionResult.SUCCESS : InteractionResult.PASS;
			}
			if (Math.abs(p.getX() - g.getX()) > 2 || Math.abs(p.getZ() - g.getZ()) > 2 || Math.abs(p.getY() - g.getY()) > 5) return InteractionResult.PASS;
			return shutGenerator(sp) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		AttackBlockCallback.EVENT.register((player, level, hand, pos, side) ->
				level instanceof ServerLevel sl && level.dimension() == LEVEL && boss != null && boss.breakNode(sl, pos)
						? InteractionResult.SUCCESS : InteractionResult.PASS);
	}

	// ------------------------------------------------------------------ saved state

	private static void load(MinecraftServer server) {
		if (loaded) return;
		loaded = true;
		Save s = server.overworld().getAttached(STATE);
		spawned = s == null ? 0 : s.spawned();
		flags = s == null ? 0 : s.flags();
		for (int i = 0; i < alive.length; i++) alive[i] = s != null && i < s.alive().size() ? s.alive().get(i) : 0;
		owners.clear();
		furnished.clear();
		if (s != null) {
			for (String id : s.owners()) owners.add(UUID.fromString(id));
			furnished.addAll(s.furnished());
		}
	}

	private static void save(MinecraftServer server) {
		List<Integer> counts = new ArrayList<>();
		for (int a : alive) counts.add(a);
		server.overworld().setAttached(STATE, new Save(spawned, counts, flags, owners.stream().map(UUID::toString).toList(), List.copyOf(furnished)));
	}

	public static boolean conquered(MinecraftServer server) { load(server); return (flags & CONQUERED) != 0; }

	public static boolean generatorOff(MinecraftServer server) { load(server); return (flags & GENERATOR) != 0; }

	public static boolean gateOpen(MinecraftServer server) { load(server); return (flags & GATE) != 0; }

	/** The searchlights found someone during this assault. */
	static void markAlarmed(MinecraftServer server) {
		load(server);
		if ((flags & ALARMED) != 0) return;
		flags |= ALARMED;
		save(server);
	}

	public static boolean isOwner(Player player) {
		if (player.level().getServer() == null) return false;
		load(player.level().getServer());
		return owners.contains(player.getUUID());
	}

	public static ServerPlayer anyOwner(MinecraftServer server) {
		load(server);
		for (UUID id : owners) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) return p;
		}
		return null;
	}

	public static boolean hasAccess(ServerPlayer player) {
		return Boolean.TRUE.equals(player.getAttached(ACCESS)) || isOwner(player);
	}

	public static void grantAccess(ServerPlayer player) { player.setAttached(ACCESS, true); }

	public static DoctorDoom boss() { return boss; }

	/** A line for {@code /gauntlet latveria status}. */
	public static String status(MinecraftServer server) {
		load(server);
		return "gate=" + ((flags & GATE) != 0) + " throne=" + ((flags & THRONE) != 0) + " generatorOff=" + ((flags & GENERATOR) != 0)
				+ " conquered=" + ((flags & CONQUERED) != 0) + " squadsAlive=" + java.util.Arrays.toString(alive) + " spawnedMask=" + spawned
				+ " owners=" + owners.size() + " boss=" + (boss == null ? "none" : "phase " + boss.phase() + " hp " + (int) boss.getHealth());
	}

	/** Debug: forget the assault so it can be played again. Players keep what they were already given. */
	public static void reset(MinecraftServer server) {
		load(server);
		ServerLevel level = server.getLevel(LEVEL);
		if (level != null) {
			callOff(level);
			for (Doombot b : level.getEntitiesOfClass(Doombot.class, new net.minecraft.world.phys.AABB(-400, 0, -500, 400, 320, 400))) b.discard();
			set(level, LatveriaPlan.GATE, Blocks.IRON_BARS);
			set(level, LatveriaPlan.THRONE_DOOR, Blocks.GREEN_STAINED_GLASS);
			set(level, LatveriaPlan.LAB_DOOR, Blocks.GREEN_STAINED_GLASS);
			edit(() -> level.setBlockAndUpdate(LatveriaPlan.GENERATOR, Blocks.SEA_LANTERN.defaultBlockState()));
		}
		spawned = 0;
		flags = 0;
		java.util.Arrays.fill(alive, 0);
		owners.clear();
		save(server);
	}

	/** Loads and furnishes every chunk that holds furniture or townsfolk (debug, and the furniture check of the tests). */
	public static int furnishAll(ServerLevel level) {
		int n = 0;
		for (long chunk : furnitureChunks) {
			level.getChunk(ChunkPos.getX(chunk), ChunkPos.getZ(chunk));
			furnish(level, chunk);
			n++;
		}
		return n;
	}

	/** Debug: destroys a squad as if the players had beaten it. */
	public static int clearSquad(MinecraftServer server, int squad) {
		ServerLevel level = server.getLevel(LEVEL);
		if (level == null || squad < 0 || squad >= SQUADS.length) return 0;
		load(server);
		int killed = 0;
		for (Doombot b : level.getEntitiesOfClass(Doombot.class, new net.minecraft.world.phys.AABB(-400, 0, -500, 400, 320, 400), b -> b.isAlive() && b.squad() == squad)) {
			b.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
			killed++;
		}
		spawned |= 1 << squad;
		alive[squad] = 0;
		save(server);
		return killed;
	}

	/** Debug: shows the title card again. */
	public static void showLogo(ServerPlayer player) {
		DoomFx.send(player, DoomFx.LOGO, 0, player.position(), 0, 200);
	}

	// ------------------------------------------------------------------ debug tools

	/** Named spots for {@code /gauntlet latveria tp}: x, y, z, yaw. */
	public static final Map<String, double[]> PLACES = new java.util.LinkedHashMap<>();
	static {
		int f = LatveriaPlan.FLOOR;
		PLACES.put("arrival", new double[]{0.5, LatveriaPlan.VALLEY_Y + 1, 122.5, 180});
		PLACES.put("square", new double[]{0.5, LatveriaPlan.VALLEY_Y + 1, 45.5, 180});
		PLACES.put("workshop", new double[]{-27.5, LatveriaPlan.VALLEY_Y + 1, 16.5, 90});
		PLACES.put("tavern", new double[]{27.5, LatveriaPlan.VALLEY_Y + 1, 15.5, 270});
		PLACES.put("cellar", new double[]{32.5, LatveriaPlan.VALLEY_Y - 4, 15.5, 270});
		PLACES.put("under_generator", new double[]{0.5, LatveriaPlan.FLOOR - 13, -248.5, 0});
		PLACES.put("watchtower", new double[]{11.5, LatveriaPlan.VALLEY_Y + 18, -59.5, 180});
		PLACES.put("road", new double[]{0.5, LatveriaPlan.roadHeight(-120) + 1.6, -112.5, 180});
		PLACES.put("gate", new double[]{0.5, f, -186.5, 180});
		PLACES.put("yard", new double[]{0.5, f, -212.5, 180});
		PLACES.put("barracks", new double[]{-33.5, f, -230.5, 90});
		PLACES.put("forge", new double[]{33.5, f, -230.5, 270});
		PLACES.put("service_door", new double[]{50.5, f, -283.5, 90});
		PLACES.put("lab", new double[]{34.5, f, -270.5, 135});
		PLACES.put("hall", new double[]{0.5, f, -256.5, 180});
		PLACES.put("throne", new double[]{0.5, f + 2, -289.5, 0});
		PLACES.put("armory", new double[]{-33.5, f, -284.5, 90});
		PLACES.put("dining", new double[]{-31.5, f, -264.5, 90});
		PLACES.put("bedrooms", new double[]{-35.5, f + 10, -270.5, 90});
		PLACES.put("library", new double[]{32.5, f + 10, -278.5, 270});
		PLACES.put("master_bedroom", new double[]{32.5, f + 10, -262.5, 270});
		PLACES.put("roof", new double[]{0.5, f + 19, -273.5, 180});
		PLACES.put("overlook", new double[]{70.5, 160, -180.5, 135});
	}

	public static boolean tp(ServerPlayer player, String place) {
		double[] at = PLACES.get(place);
		ServerLevel level = player.level().getServer().getLevel(LEVEL);
		if (at == null || level == null) return false;
		if (player.level().dimension() != LEVEL) {
			player.setAttached(RETURN_DIM, player.level().dimension().identifier().toString());
			player.setAttached(RETURN_POS, player.blockPosition());
		}
		player.teleport(new TeleportTransition(level, new Vec3(at[0], at[1], at[2]), Vec3.ZERO, (float) at[3], 0f, TeleportTransition.DO_NOTHING));
		return true;
	}

	/** Brings up every squad that has not been met yet, wherever the players are. */
	public static int spawnSquads(ServerLevel level) {
		load(level.getServer());
		int before = spawned;
		for (int s = 0; s < SQUADS.length; s++) level.getChunk(BlockPos.containing(SQUAD_AT[s]));
		for (int s = 0; s < SQUADS.length; s++) {
			if ((spawned & 1 << s) != 0) continue;
			Vec3 at = SQUAD_AT[s];
			int count = 0;
			for (int i = 0; i < SQUADS[s].length; i++) {
				Doombot bot = ModEntities.DOOMBOT.create(level, EntitySpawnReason.STRUCTURE);
				if (bot == null) continue;
				bot.setup(SQUADS[s][i], s);
				bot.snapTo(at.x + (i % 4 - 1.5) * 2.2, at.y + (SQUADS[s][i] == Doombot.INTERCEPTOR ? 5 : 0), at.z + (i / 4) * 2.4 - 1.2, 180f, 0);
				if (level.addFreshEntity(bot)) count++;
			}
			spawned |= 1 << s;
			alive[s] = count;
		}
		save(level.getServer());
		return Integer.bitCount(spawned & ~before);
	}

	/** Switches the laboratory's generator off or back on. */
	public static void setGenerator(ServerLevel level, boolean off) {
		load(level.getServer());
		if (off) flags |= GENERATOR; else flags &= ~GENERATOR;
		save(level.getServer());
		level.getChunk(LatveriaPlan.GENERATOR);
		if (off) barriers(level);
		else {
			edit(() -> level.setBlockAndUpdate(LatveriaPlan.GENERATOR, Blocks.SEA_LANTERN.defaultBlockState()));
			set(level, LatveriaPlan.LAB_DOOR, Blocks.GREEN_STAINED_GLASS);
		}
	}

	/** Opens the portcullis and the throne room as if the squads had fallen. */
	public static void openGates(ServerLevel level) {
		load(level.getServer());
		flags |= GATE | THRONE;
		save(level.getServer());
		level.getChunk(new BlockPos(0, 0, -205));
		level.getChunk(new BlockPos(0, 0, -253));
		barriers(level);
	}

	/** Forgets everything Latveria knows about a player: access, title card, spoils, ownership. */
	public static void forget(ServerPlayer player) {
		load(player.level().getServer());
		player.removeAttached(ACCESS);
		player.removeAttached(LOGO_SEEN);
		player.removeAttached(REWARDED);
		owners.remove(player.getUUID());
		save(player.level().getServer());
	}

	/** Lets the title card play again on the next arrival. */
	public static void forgetLogo(ServerPlayer player) { player.removeAttached(LOGO_SEEN); }

	/** Lets the victor's spoils be paid once more. */
	public static void forgetReward(ServerPlayer player) { player.removeAttached(REWARDED); }

	/** One of every item of the add-on. */
	public static void giveAll(ServerPlayer p) {
		for (net.minecraft.world.item.Item item : List.of(ModItems.DOOM_HELMET, ModItems.DOOM_CHESTPLATE, ModItems.DOOM_LEGGINGS, ModItems.DOOM_BOOTS,
				ModItems.DOOM_GAUNTLET, ModItems.DOOM_GRIMOIRE, ModItems.LATVERIA_KEY, ModItems.CASTLE_SEAL, ModItems.DOOMBOT_CONTROLLER,
				ModItems.MECHANICAL_HEART, ModItems.TIME_PLATFORM, ModItems.ARC_REACTOR, ModItems.DOOM_CIRCUIT, ModItems.JADE_SHARD,
				ModItems.REPAIR_TOOL, ModItems.LATVERIA_BANNER)) {
			ItemStack stack = new ItemStack(item, item == ModItems.CASTLE_SEAL || item == ModItems.MECHANICAL_HEART ? 4 : 1);
			if (!p.getInventory().add(stack)) p.drop(stack, false);
		}
	}

	// ------------------------------------------------------------------ travelling

	private static boolean hasMind(ServerPlayer p) {
		return GauntletState.inserted(GauntletState.activeGauntlet(p)).contains(Stone.MIND) || p.getInventory().contains(new ItemStack(ModItems.MIND_STONE));
	}

	/** Brings the player (and whoever stands within four blocks) to Latveria. */
	public static boolean enter(ServerPlayer player, boolean toCastle) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = server.getLevel(LEVEL);
		if (level == null) return false;
		Vec3 to = toCastle && conquered(server) && isOwner(player) ? LatveriaPlan.CASTLE_HALL : LatveriaPlan.ARRIVAL;
		List<ServerPlayer> party = new ArrayList<>(List.of(player));
		for (ServerPlayer other : player.level().players()) {
			if (other != player && other.isAlive() && !other.isSpectator() && other.distanceToSqr(player) <= 16) party.add(other);
		}
		int i = 0;
		for (ServerPlayer p : party) {
			if (p.level().dimension() != LEVEL) {
				p.setAttached(RETURN_DIM, p.level().dimension().identifier().toString());
				p.setAttached(RETURN_POS, p.blockPosition());
			}
			if (p != player) DoomFx.tell(p, "brought", player.getName());
			Vec3 at = to.add((i % 3 - 1) * 1.2, 0, i / 3 * 1.2);
			i++;
			p.teleport(new TeleportTransition(level, at, Vec3.ZERO, 180f, 0f, TeleportTransition.DO_NOTHING));
			// Time to look round, and (the first time) to read the welcome, before anyone notices the newcomer.
			LatveriaHunt.grace(p, Boolean.TRUE.equals(p.getAttached(LOGO_SEEN)) ? 200 : 2400);
			p.level().playSound(null, p.blockPosition(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.4f, 1.3f);
			if (!Boolean.TRUE.equals(p.getAttached(LOGO_SEEN))) {
				p.setAttached(LOGO_SEEN, true);
				logoDue.put(p.getUUID(), 20);
			}
			ModAdvancements.award(p, "enter_latveria");
		}
		return true;
	}

	/** Returns the player to where they left for Latveria from. */
	public static void leave(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		String dim = player.getAttached(RETURN_DIM);
		BlockPos pos = player.getAttached(RETURN_POS);
		ServerLevel to = dim == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(dim)));
		if (to == null || pos == null || to.dimension() == LEVEL) {
			to = server.overworld();
			pos = to.getRespawnData().pos();
		}
		player.teleport(new TeleportTransition(to, Vec3.atBottomCenterOf(pos), Vec3.ZERO, player.getYRot(), 0f, TeleportTransition.DO_NOTHING));
		to.playSound(null, pos, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.4f, 1.3f);
	}

	// ------------------------------------------------------------------ ticking

	private static void tick(MinecraftServer server) {
		load(server);
		DoomCombat.tick();
		DoomArmor.tick(server);
		DoomItems.TimePlatforms.tick(false);
		long time = server.overworld().getGameTime();
		if (time % 100 == 0) flushPlaced(server);
		if (time % 20 == 0) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (Boolean.TRUE.equals(p.getAttached(ACCESS)) || !SpaceStone.latveriaSealed(p) || !ModAdvancements.completed(p, "defeat_loki") || !hasMind(p)) continue;
				grantAccess(p);
				DoomFx.tell(p, "deciphered");
				p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.5f);
				ModAdvancements.award(p, "decipher_seal");
			}
		}
		ServerLevel level = server.getLevel(LEVEL);
		if (level == null || level.players().isEmpty()) {
			if (boss != null && level != null) callOff(level);
			return;
		}
		for (int n = 0; n < 2 && !toFurnish.isEmpty(); n++) furnish(level, toFurnish.poll());
		LatveriaQuests.ensureNpcs(level);
		Searchlights.tick(level);
		LatveriaHunt.tick(level);
		LatveriaCourt.tick(level);
		// A copy: leaving through the gate takes the player out of this level's list while we walk it.
		for (ServerPlayer p : List.copyOf(level.players())) {
			LatveriaQuests.checkCloakProximity(level, p);
			Integer due = logoDue.get(p.getUUID());
			if (due != null) {
				if (due <= 0) { logoDue.remove(p.getUUID()); DoomFx.send(p, DoomFx.LOGO, 1, p.position(), 0, 200); }
				else logoDue.put(p.getUUID(), due - 1);
			}
			if (LatveriaPlan.RETURN_GATE.contains(p.position()) && p.isAlive()) {
				int held = inGate.merge(p.getUUID(), 1, Integer::sum);
				if (held == 1) DoomFx.tell(p, "gate.leaving");
				if (held >= 40) { inGate.remove(p.getUUID()); leave(p); }
			} else inGate.remove(p.getUUID());
		}
		if (time % 10 != 0) return;
		squads(level);
		barriers(level);
		fight(level);
	}

	private static void squads(ServerLevel level) {
		if ((flags & CONQUERED) != 0) return;
		for (int s = 0; s < SQUADS.length; s++) {
			if ((spawned & 1 << s) != 0) continue;
			Vec3 at = SQUAD_AT[s];
			if (!level.isLoaded(BlockPos.containing(at))) continue;
			boolean near = false;
			for (ServerPlayer p : level.players()) near |= !p.isSpectator() && p.position().distanceToSqr(at) < 56 * 56;
			if (!near) continue;
			int count = 0;
			for (int i = 0; i < SQUADS[s].length; i++) {
				Doombot bot = ModEntities.DOOMBOT.create(level, EntitySpawnReason.STRUCTURE);
				if (bot == null) continue;
				bot.setup(SQUADS[s][i], s);
				double x = at.x + (i % 4 - 1.5) * 2.2, z = at.z + (i / 4) * 2.4 - 1.2;
				bot.snapTo(x, at.y + (SQUADS[s][i] == Doombot.INTERCEPTOR ? 5 : 0), z, 180f, 0);
				if (level.addFreshEntity(bot)) count++;
			}
			spawned |= 1 << s;
			alive[s] = count;
			save(level.getServer());
		}
	}

	public static void onBotDeath(MinecraftServer server, int squad) {
		load(server);
		if (squad < 0 || squad >= alive.length || alive[squad] <= 0) return;
		alive[squad]--;
		save(server);
	}

	private static boolean cleared(int squad) {
		return (spawned & 1 << squad) != 0 && alive[squad] <= 0;
	}

	/** Opens what the assault has earned, and re-applies it to chunks that were not loaded at the time. */
	private static void barriers(ServerLevel level) {
		MinecraftServer server = level.getServer();
		if ((flags & GATE) == 0 && cleared(SQUAD_GATE)) {
			flags |= GATE;
			save(server);
			announce(level, "gate.open", SoundEvents.IRON_DOOR_OPEN);
			// Through the gate without a searchlight ever finding you.
			if ((flags & ALARMED) == 0) for (ServerPlayer p : level.players()) ModAdvancements.award(p, "unseen_in_latveria");
		}
		if ((flags & THRONE) == 0 && cleared(SQUAD_YARD)) { flags |= THRONE; save(server); announce(level, "throne.open", SoundEvents.BEACON_DEACTIVATE); }
		if ((flags & GATE) != 0) open(level, LatveriaPlan.GATE, Blocks.IRON_BARS);
		if ((flags & THRONE) != 0) open(level, LatveriaPlan.THRONE_DOOR, Blocks.GREEN_STAINED_GLASS);
		if ((flags & GENERATOR) != 0) {
			open(level, LatveriaPlan.LAB_DOOR, Blocks.GREEN_STAINED_GLASS);
			if (level.isLoaded(LatveriaPlan.GENERATOR) && level.getBlockState(LatveriaPlan.GENERATOR).is(Blocks.SEA_LANTERN)) {
				edit(() -> level.setBlockAndUpdate(LatveriaPlan.GENERATOR, Blocks.COAL_BLOCK.defaultBlockState()));
			}
			BlockPos ug = LatveriaQuests.UNDER_CASTLE_GEN;
			if (level.isLoaded(ug) && level.getBlockState(new BlockPos(0, LatveriaPlan.FLOOR - 11, -242)).is(Blocks.SEA_LANTERN)) {
				edit(() -> {
					int gy = LatveriaPlan.FLOOR - 13;
					for (BlockPos p : BlockPos.betweenClosed(-3, gy + 2, -245, 3, gy + 7, -239)) {
						if (level.getBlockState(p).is(Blocks.SEA_LANTERN)) level.setBlockAndUpdate(p, Blocks.COAL_BLOCK.defaultBlockState());
					}
				});
			}
		}
	}

	private static void announce(ServerLevel level, String key, net.minecraft.sounds.SoundEvent sound) {
		for (ServerPlayer p : level.players()) {
			DoomFx.tell(p, key);
			level.playSound(null, p.blockPosition(), sound, SoundSource.BLOCKS, 1.2f, 0.7f);
		}
	}

	private static void open(ServerLevel level, int[] box, Block barrier) {
		BlockPos corner = new BlockPos(box[0], box[1], box[2]);
		if (!level.isLoaded(corner) || !level.getBlockState(corner).is(barrier)) return;
		set(level, box, Blocks.AIR);
	}

	private static void set(ServerLevel level, int[] box, Block block) {
		if (!level.isLoaded(new BlockPos(box[0], box[1], box[2]))) return;
		edit(() -> {
			for (BlockPos p : BlockPos.betweenClosed(box[0], box[1], box[2], box[3], box[4], box[5])) {
				level.setBlock(p, block.defaultBlockState(), Block.UPDATE_ALL);
			}
		});
	}

	/** Switches the generator off (a right click on it does this). False if it was off already. */
	public static boolean shutGenerator(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (generatorOff(server)) return false;
		flags |= GENERATOR;
		save(server);
		ServerLevel level = player.level();
		Vec3 at = LatveriaPlan.GENERATOR.getCenter();
		DoomFx.bill(level, ModParticles.DOOM_PHASE_SHIFT, at, 1.6);
		DoomFx.bill(level, ModParticles.DOOM_DOOM_SMOKE, at.add(0, 1, 0), 1.6);
		announce(level, "generator.off", SoundEvents.BEACON_DEACTIVATE);
		for (ServerPlayer p : level.players()) ModAdvancements.award(p, "sabotage_generator");
		barriers(level);
		return true;
	}

	// ------------------------------------------------------------------ the fight

	private static boolean inArena(ServerPlayer p) {
		return p.isAlive() && !p.isSpectator() && LatveriaPlan.ARENA_BOX.contains(p.position());
	}

	private static void fight(ServerLevel level) {
		if (boss != null && boss.isRemoved()) boss = null;
		if ((flags & CONQUERED) != 0 || DoomFinalEncounter.isActive(level.getServer())) return;
		boolean anyone = false;
		for (ServerPlayer p : level.players()) anyone |= inArena(p);
		if (boss == null) {
			if (!anyone) held = false;
			if (anyone && !held && level.isLoaded(BlockPos.containing(LatveriaPlan.THRONE))) summon(level);
			return;
		}
		emptyArena = anyone ? 0 : emptyArena + 10;
		if (emptyArena >= 100 && !boss.isDeadOrDying()) {
			callOff(level);
			for (ServerPlayer p : level.players()) DoomFx.tell(p, "fight.reset");
		}
	}

	/**
	 * After the ascension: the multiverse is gone, and the Time Stone pulls everyone back to the moment before. They
	 * stand in the yard, whole again, the drained gauntlet still silent; the duel starts over when they return.
	 */
	public static void afterAscension(ServerLevel level, java.util.UUID victim) {
		for (ServerPlayer p : List.copyOf(level.players())) {
			p.setHealth(p.getMaxHealth());
			p.removeAllEffects();
			DoomFx.tell(p, "god.end");
			ModAdvancements.award(p, "god_emperor_doom");
			if (LatveriaPlan.ARENA_BOX.inflate(16).contains(p.position())) tp(p, "yard");
		}
		ServerPlayer v = victim == null ? null : level.getServer().getPlayerList().getPlayer(victim);
		if (v != null) GauntletSilence.silence(v, 600);
		callOff(level);
	}

	/**
	 * After Doom steals the incomplete Infinity Gauntlet (<= 5 stones) and teleports out of Latveria: opens the castle
	 * doors, calms the hunt, gives a Latveria Key if needed, and alerts the challengers to return to the Overworld.
	 */
	public static void onDoomStolenFled(ServerLevel level, java.util.UUID victim) {
		openGates(level);
		LatveriaHunt.clear();
		for (ServerPlayer p : List.copyOf(level.players())) {
			p.setHealth(p.getMaxHealth());
			p.removeAllEffects();
			grantAccess(p);
			if (!p.getInventory().contains(new ItemStack(ModItems.LATVERIA_KEY))) {
				ItemStack key = new ItemStack(ModItems.LATVERIA_KEY);
				if (!p.getInventory().add(key)) p.drop(key, false);
			}
			DoomFx.tell(p, "doom.stolen_fled_hint");
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
					new com.dantemorrigan.gauntlet.net.ModNetwork.QuestAlert(
							net.minecraft.network.chat.Component.translatable("msg.infinity_gauntlet.latveria.doom.stolen_alert"), 1));
		}
		callOff(level);
	}

	/** Debug: calls the fight off and keeps Doom away until everyone leaves the hall. */
	public static void hold(ServerLevel level) {
		callOff(level);
		held = true;
	}

	public static DoctorDoom summon(ServerLevel level) {
		held = false;
		if (boss != null && !boss.isRemoved()) return boss;
		DoctorDoom doom = ModEntities.DOCTOR_DOOM.create(level, EntitySpawnReason.EVENT);
		if (doom == null) return null;
		doom.snapTo(LatveriaPlan.THRONE.x, LatveriaPlan.THRONE.y, LatveriaPlan.THRONE.z, 0f, 0);
		doom.yBodyRot = doom.yHeadRot = 0f;
		if (!level.addFreshEntity(doom)) return null;
		doom.intro();
		boss = doom;
		emptyArena = 0;
		for (int side = -1; side <= 1; side += 2) {
			Doombot guard = ModEntities.DOOMBOT.create(level, EntitySpawnReason.EVENT);
			if (guard == null) continue;
			guard.setup(Doombot.ROYAL_GUARD, -1);
			guard.snapTo(side * 6 + 0.5, LatveriaPlan.FLOOR, -284.5, 0f, 0);
			level.addFreshEntity(guard);
		}
		for (ServerPlayer p : level.players()) if (p.position().distanceToSqr(LatveriaPlan.ARENA) < 60 * 60) DoomFx.send(p, DoomFx.CUTSCENE, doom.getId(), doom.position(), 0, 100);
		return doom;
	}

	/** Ends an unfinished fight: Doom and the Doombots he brought are gone, the hall is as it was. */
	public static void callOff(ServerLevel level) {
		if (boss != null) {
			boss.cleanup(level);
			boss.discard();
			boss = null;
		}
		for (Doombot b : level.getEntitiesOfClass(Doombot.class, LatveriaPlan.ARENA_BOX.inflate(8), b -> !b.allied() && b.squad() < 0)) b.discard();
		emptyArena = 0;
	}

	public static void onDoomDefeated(ServerLevel level, DoctorDoom doom) {
		boss = null;
		conquer(level, doom.challengers());
	}

	/** The castle falls to {@code victors}: spoils, ownership, open doors, and the Doombots change sides. */
	public static void conquer(ServerLevel level, java.util.Collection<UUID> victors) {
		MinecraftServer server = level.getServer();
		load(server);
		if ((flags & CONQUERED) != 0) {
			for (UUID id : victors) {
				ServerPlayer p = server.getPlayerList().getPlayer(id);
				if (p != null) { owners.add(id); reward(p); }
			}
			save(server);
			return;
		}
		flags |= CONQUERED | GATE | THRONE | GENERATOR;
		ServerPlayer first = null;
		for (UUID id : victors) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null) continue;
			owners.add(id);
			if (first == null || p.level() == level) first = p;
			reward(p);
		}
		save(server);
		barriers(level);
		for (ServerPlayer p : level.players()) {
			DoomFx.tell(p, "conquered");
			level.playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 0.9f);
		}
		if (first == null) return;
		LatveriaHunt.clear();
		for (Doombot b : level.getEntitiesOfClass(Doombot.class, new net.minecraft.world.phys.AABB(-400, 0, -500, 400, 320, 400), b -> b.isAlive() && !b.isForged())) {
			b.becomeStatue(first);
		}
		for (int side = -1; side <= 1; side += 2) {
			Doombot guard = ModEntities.DOOMBOT.create(level, EntitySpawnReason.EVENT);
			if (guard == null) continue;
			guard.setup(Doombot.ROYAL_GUARD, -1);
			guard.snapTo(side * 4 + 0.5, LatveriaPlan.FLOOR, -286.5, 0f, 0);
			level.addFreshEntity(guard);
			guard.becomeStatue(first);
		}
		// The whole army falls in, in ranks in the throne room, and stands as eternal grey statues.
		LatveriaCourt.parade(level, level.getEntitiesOfClass(Doombot.class, new net.minecraft.world.phys.AABB(-400, 0, -500, 400, 320, 400),
				b -> b.isAlive() && b.allied() && !b.decoy()));
	}

	/** The victor's spoils, once per player however many times the fight is won. */
	private static void reward(ServerPlayer p) {
		grantAccess(p);
		ModAdvancements.award(p, "defeat_doom");
		if (Boolean.TRUE.equals(p.getAttached(REWARDED))) return;
		p.setAttached(REWARDED, true);
		for (ItemStack stack : List.of(new ItemStack(ModItems.DOOM_HELMET), new ItemStack(ModItems.DOOM_CHESTPLATE), new ItemStack(ModItems.DOOM_LEGGINGS),
				new ItemStack(ModItems.DOOM_BOOTS), new ItemStack(ModItems.DOOM_GAUNTLET), new ItemStack(ModItems.LATVERIA_KEY),
				new ItemStack(ModItems.DOOMBOT_CONTROLLER), new ItemStack(ModItems.CASTLE_SEAL, 4), new ItemStack(ModItems.DOOM_GRIMOIRE),
				new ItemStack(ModItems.TIME_PLATFORM), new ItemStack(ModItems.LATVERIA_BANNER))) {
			if (!p.getInventory().add(stack)) p.drop(stack, false);
		}
	}

	// ------------------------------------------------------------------ furniture and townsfolk

	/** "The Chronicle of Victor von Doom", as his servant Boris wrote it: four pages of who Doom is and why. */
	public static ItemStack chronicle() {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		List<net.minecraft.server.network.Filterable<net.minecraft.network.chat.Component>> pages = new ArrayList<>();
		for (int i = 1; i <= 4; i++) pages.add(net.minecraft.server.network.Filterable.passThrough(
				net.minecraft.network.chat.Component.translatable("book.infinity_gauntlet.chronicle.p" + i)));
		book.set(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT, new net.minecraft.world.item.component.WrittenBookContent(
				net.minecraft.server.network.Filterable.passThrough("The Chronicle of Victor von Doom"), "Boris", 0, pages, true));
		book.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.translatable("book.infinity_gauntlet.chronicle.title")
				.withStyle(st -> st.withItalic(false).withColor(net.minecraft.ChatFormatting.DARK_GREEN).withBold(true)));
		return book;
	}

	static void furnish(ServerLevel level, long chunk) {
		if (furnished.contains(chunk)) return;
		int cx = ChunkPos.getX(chunk), cz = ChunkPos.getZ(chunk);
		if (!level.hasChunk(cx, cz)) return;
		furnished.add(chunk);
		save(level.getServer());
		for (LatveriaPlan.Op op : LatveriaPlan.FURNITURE) {
			if (op.x0() >> 4 != cx || op.z0() >> 4 != cz) continue;
			edit(() -> level.setBlock(new BlockPos(op.x0(), op.y0(), op.z0()), op.state(), Block.UPDATE_CLIENTS));
			// The castle library's lectern holds Boris's chronicle of his master.
			if (op.state().is(Blocks.LECTERN) && op.z0() < -200) {
				BlockPos at = new BlockPos(op.x0(), op.y0(), op.z0());
				edit(() -> net.minecraft.world.level.block.LecternBlock.tryPlaceBook(null, level, at, level.getBlockState(at), chronicle()));
			}
		}
		for (int[] c : LatveriaPlan.CHESTS) {
			if (c[0] >> 4 != cx || c[2] >> 4 != cz || !(level.getBlockEntity(new BlockPos(c[0], c[1], c[2])) instanceof Container chest)) continue;
			ItemStack[] loot = switch (c[3]) {
				case 0 -> new ItemStack[]{new ItemStack(Items.BREAD, 8), new ItemStack(Items.COOKED_BEEF, 6), new ItemStack(Items.APPLE, 4), new ItemStack(Items.CANDLE, 4)};
				case 1 -> new ItemStack[]{new ItemStack(Items.ARROW, 16), new ItemStack(Items.IRON_INGOT, 5), new ItemStack(ModItems.DOOM_CIRCUIT, 2), new ItemStack(Items.SHIELD)};
				case 2 -> new ItemStack[]{new ItemStack(ModItems.DOOM_CIRCUIT, 3), new ItemStack(ModItems.JADE_SHARD, 3), new ItemStack(Items.REDSTONE, 12), new ItemStack(ModItems.REPAIR_TOOL)};
				default -> new ItemStack[]{new ItemStack(ModItems.JADE_SHARD, 6), new ItemStack(Items.GOLD_INGOT, 8), new ItemStack(Items.EMERALD, 10), new ItemStack(ModItems.ARC_REACTOR)};
			};
			for (int i = 0; i < loot.length; i++) chest.setItem(4 + i * 5, loot[i]);
		}
		for (BlockPos p : LatveriaPlan.VILLAGERS) {
			if (p.getX() >> 4 != cx || p.getZ() >> 4 != cz) continue;
			var villager = EntityType.VILLAGER.create(level, EntitySpawnReason.STRUCTURE);
			if (villager == null) continue;
			villager.snapTo(p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 0f, 0);
			villager.setPersistenceRequired();
			LatveriaFolk.dress(villager);
			level.addFreshEntity(villager);
		}
	}

	private Latveria() {}
}
