package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModBlocks;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.ModParticles;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.net.ModStatus;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Space Stone quest. Once the Ender Dragon has been killed, a large icy rift opens on the End's main island.
 * It leads to a small frozen asteroid far beyond the galaxy ({@link #FROST}): without at least a full set of iron
 * armor the cold kills within seconds. On top of the ancient ice tower lies the Tesseract. Taking it shows
 * "Use the cube to get home"; right-click sends its holder to Earth. Thrown into lava it explodes, the lava around
 * turns to obsidian and the glowing Space Stone appears in its place.
 */
public final class SpaceStoneQuest {
	public static final ResourceKey<Level> FROST = ResourceKey.create(Registries.DIMENSION, InfinityGauntlet.id("frost_asteroid"));
	public static final TagKey<Item> COLD_ARMOR = TagKey.create(Registries.ITEM, InfinityGauntlet.id("cold_protection"));

	/** Asteroid center and the tower on it (fixed: the dimension holds nothing else). */
	private static final int CX = 0, CY = 100, CZ = 0, RX = 24, RY = 12, RZ = 20;
	private static final int TOWER_X = 0, TOWER_Z = 6, TOWER_R = 6, TOWER_H = 46;
	private static final int PAD_Z = -13, RETURN_Z = -17;
	/** End rift position (plane X/Y, facing ±Z). */
	private static final int END_X = 0, END_Z = 26;

	private static final AttachmentType<Boolean> ASTEROID_BUILT = AttachmentRegistry.<Boolean>builder()
			.persistent(Codec.BOOL).buildAndRegister(InfinityGauntlet.id("asteroid_built"));
	private static final AttachmentType<BlockPos> ARRIVAL = AttachmentRegistry.<BlockPos>builder()
			.persistent(BlockPos.CODEC).buildAndRegister(InfinityGauntlet.id("asteroid_arrival"));
	private static final AttachmentType<BlockPos> END_RIFT = AttachmentRegistry.<BlockPos>builder()
			.persistent(BlockPos.CODEC).buildAndRegister(InfinityGauntlet.id("end_rift"));

	private static final AttachmentType<BlockPos> TESSERACT_POS = AttachmentRegistry.<BlockPos>builder()
			.persistent(BlockPos.CODEC).buildAndRegister(InfinityGauntlet.id("tesseract_pos"));

	/** Where the Tesseract was placed on the asteroid (tests, debug). */
	public static BlockPos tesseractPos(ServerLevel frost) { return frost.getAttached(TESSERACT_POS); }

	private static final Map<UUID, Long> PORTAL_COOLDOWN = new HashMap<>();
	private static final List<ItemEntity> TESSERACTS = new ArrayList<>();
	private static final List<ItemEntity> GLOWING_STONES = new ArrayList<>();
	private static final Map<UUID, Long> COLD_WARNED = new HashMap<>();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SpaceStoneQuest::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item && item.getItem().is(ModItems.QUEST_SPACE_TESSERACT)) TESSERACTS.add(item);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> { TESSERACTS.clear(); GLOWING_STONES.clear(); PORTAL_COOLDOWN.clear(); COLD_WARNED.clear(); });
	}

	// ------------------------------------------------------------------ tick

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (now % 40 == 0) openEndRiftIfDue(server);
		ServerLevel frost = server.getLevel(FROST);
		if (frost != null && now % 10 == 0) for (ServerPlayer p : frost.players()) cold(p, now);
		for (Iterator<ItemEntity> it = TESSERACTS.iterator(); it.hasNext(); ) {
			ItemEntity item = it.next();
			if (item.isRemoved() || !item.getItem().is(ModItems.QUEST_SPACE_TESSERACT)) { it.remove(); continue; }
			if (item.isInLava() || item.level().getFluidState(item.blockPosition()).is(FluidTags.LAVA)) {
				it.remove();
				breakOpen((ServerLevel) item.level(), item);
			}
		}
		for (Iterator<ItemEntity> it = GLOWING_STONES.iterator(); it.hasNext(); ) {
			ItemEntity item = it.next();
			if (item.isRemoved()) { it.remove(); continue; }
			if (now % 6 == 0) ((ServerLevel) item.level()).sendParticles(ModParticles.SPACE_ENERGY_WISPS, item.getX(), item.getY() + 0.4, item.getZ(), 1, 0.2, 0.2, 0.2, 0);
		}
	}

	// ------------------------------------------------------------------ the End rift

	private static void openEndRiftIfDue(MinecraftServer server) {
		ServerLevel end = server.getLevel(Level.END);
		if (end == null || end.getAttached(END_RIFT) != null || end.getDragonFight() == null || !end.getDragonFight().hasPreviouslyKilledDragon()) return;
		if (!end.hasChunkAt(new BlockPos(END_X, 0, END_Z))) return;
		buildEndRift(end);
	}

	/** Builds the large rift in the End (also the debug command). Returns the bottom-center portal block. */
	public static BlockPos buildEndRift(ServerLevel end) {
		BlockPos existing = end.getAttached(END_RIFT);
		if (existing != null) return existing;
		end.getChunk(END_X >> 4, END_Z >> 4);
		int ground = end.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(END_X, 0, END_Z)).getY();
		if (ground <= end.getMinY() + 1) ground = 64;
		BlockPos base = new BlockPos(END_X, ground, END_Z);
		// Frame 9 wide, 11 tall; the rift itself 7 x 9.
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -2; dz <= 2; dz++) fill(end, base.offset(dx, -1, dz), Blocks.END_STONE_BRICKS.defaultBlockState());
			for (int dy = 0; dy <= 10; dy++) {
				boolean frame = Math.abs(dx) == 4 || dy == 0 || dy == 10;
				BlockState s = frame ? ((dx + dy) % 3 == 0 ? Blocks.BLUE_ICE : (Math.abs(dx) == 4 && (dy == 0 || dy == 10)) ? ModBlocks.QUEST_SPACE_CARVED_ICE : Blocks.PACKED_ICE).defaultBlockState()
						: ModBlocks.FROST_PORTAL.defaultBlockState();
				end.setBlock(base.offset(dx, dy, 0), s, Block.UPDATE_CLIENTS);
				if (!frame) {
					end.setBlock(base.offset(dx, dy, 1), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					end.setBlock(base.offset(dx, dy, -1), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		BlockPos rift = base.above();
		end.setAttached(END_RIFT, rift);
		end.playSound(null, rift, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 4.0F, 0.7F);
		for (ServerPlayer p : end.players()) alert(p, Component.translatable("msg.infinity_gauntlet.space.rift_opened"));
		return rift;
	}

	private static void fill(ServerLevel level, BlockPos p, BlockState s) {
		if (level.getBlockState(p).isAir()) level.setBlock(p, s, Block.UPDATE_CLIENTS);
	}

	// ------------------------------------------------------------------ travel

	static void enterPortal(ServerPlayer p) {
		long now = p.level().getServer().overworld().getGameTime();
		Long until = PORTAL_COOLDOWN.get(p.getUUID());
		if (until != null && now < until) return;
		PORTAL_COOLDOWN.put(p.getUUID(), now + 60);
		MinecraftServer server = p.level().getServer();
		if (p.level().dimension().equals(FROST)) {
			ServerLevel end = server.getLevel(Level.END);
			BlockPos rift = end == null ? null : end.getAttached(END_RIFT);
			if (end == null || rift == null) { sendHome(p); return; }
			p.teleport(new TeleportTransition(end, new Vec3(rift.getX() + 0.5, rift.getY(), rift.getZ() - 3.5), Vec3.ZERO, 180, 0, TeleportTransition.DO_NOTHING));
			return;
		}
		goToAsteroid(p);
	}

	public static void goToAsteroid(ServerPlayer p) {
		ServerLevel frost = p.level().getServer().getLevel(FROST);
		if (frost == null) return;
		BlockPos arrival = ensureAsteroid(frost);
		p.teleport(new TeleportTransition(frost, Vec3.atBottomCenterOf(arrival), Vec3.ZERO, 0, 10, TeleportTransition.DO_NOTHING));
		frost.sendParticles(ModParticles.QUEST_SPACE_RETURN_FOLD, arrival.getX() + 0.5, arrival.getY() + 1, arrival.getZ() + 0.5, 1, 0, 0, 0, 0);
		frost.playSound(null, arrival, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.0F, 0.5F);
		COLD_WARNED.put(p.getUUID(), p.level().getServer().overworld().getGameTime() + 40);
		alert(p, Component.translatable(protectedFromCold(p) ? "msg.infinity_gauntlet.space.arrive" : "msg.infinity_gauntlet.space.arrive_cold"));
	}

	/** Back to Earth: the player's bed/anchor if it is in the Overworld, otherwise the world spawn. */
	static void sendHome(ServerPlayer p) {
		ServerLevel overworld = p.level().getServer().overworld();
		TeleportTransition t = p.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
		if (t == null || t.newLevel() != overworld) {
			t = new TeleportTransition(overworld, Vec3.atBottomCenterOf(overworld.getRespawnData().pos()), Vec3.ZERO, p.getYRot(), 0, TeleportTransition.DO_NOTHING);
		}
		((ServerLevel) p.level()).sendParticles(ModParticles.QUEST_SPACE_RETURN_FOLD, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
		p.setTicksFrozen(0);
		p.teleport(t);
		ServerLevel to = (ServerLevel) p.level();
		to.sendParticles(ModParticles.QUEST_SPACE_RETURN_FOLD, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
		to.playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.2F, 0.6F);
	}

	// ------------------------------------------------------------------ the cold

	public static boolean protectedFromCold(ServerPlayer p) {
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			if (!p.getItemBySlot(slot).is(COLD_ARMOR)) return false;
		}
		return true;
	}

	private static void cold(ServerPlayer p, long now) {
		if (!p.isAlive() || p.isCreative() || p.isSpectator()) return;
		if (protectedFromCold(p)) {
			p.setTicksFrozen(Math.min(p.getTicksFrozen(), 60));
			return;
		}
		p.setTicksFrozen(p.getTicksRequiredToFreeze() + 20);
		p.hurtServer((ServerLevel) p.level(), p.level().damageSources().freeze(), 1.0F);
		Long last = COLD_WARNED.get(p.getUUID());
		if (last == null || now - last > 60) {
			COLD_WARNED.put(p.getUUID(), now);
			ModStatus.send(p, Component.translatable("msg.infinity_gauntlet.space.freezing"));
		}
	}

	// ------------------------------------------------------------------ the Tesseract

	public static void takeTesseract(ServerPlayer p, BlockPos pos) {
		ServerLevel level = (ServerLevel) p.level();
		level.removeBlock(pos, false);
		level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.5F, 1.4F);
		level.sendParticles(ModParticles.SPACE_TELEPORT_FLASH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
		ItemStack cube = new ItemStack(ModItems.QUEST_SPACE_TESSERACT);
		if (!p.getInventory().add(cube)) p.drop(cube, false);
		StoneRegistry.markFound(level.getServer(), Stone.SPACE);
		alert(p, Component.translatable("message.infinity_gauntlet.quest.space.home"));
	}

	public static void useTesseract(ServerPlayer p, InteractionHand hand) {
		if (p.getCooldowns().isOnCooldown(p.getItemInHand(hand))) return;
		p.getCooldowns().addCooldown(p.getItemInHand(hand), 60);
		if (p.level().dimension().equals(Level.OVERWORLD)) {
			ModStatus.send(p, Component.translatable("msg.infinity_gauntlet.space.already_home"));
			return;
		}
		sendHome(p);
	}

	/** Lava cracks the cube: blast, the lava around turns to obsidian, the Space Stone shines in the middle. */
	private static void breakOpen(ServerLevel level, ItemEntity cube) {
		Vec3 at = cube.position();
		BlockPos center = cube.blockPosition();
		cube.discard();
		level.explode(null, at.x, at.y + 0.5, at.z, 3.0F, false, Level.ExplosionInteraction.NONE);
		int r = 6;
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
			if (p.distSqr(center) <= r * r && level.getFluidState(p).is(FluidTags.LAVA)) {
				level.setBlock(p, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		BlockPos top = center;
		while (!level.getBlockState(top).isAir() && top.getY() < center.getY() + 4) top = top.above();
		level.sendParticles(ModParticles.QUEST_SPACE_LAVA_RELEASE, at.x, at.y + 0.6, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ModParticles.QUEST_SPACE_OBSIDIAN_WAVE, at.x, top.getY() + 0.05, at.z, 0, 0, 1, 0, 1.0);
		level.playSound(null, center, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 3.0F, 0.6F);
		level.playSound(null, center, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2.0F, 1.3F);
		if (!StoneRegistry.markReleased(level.getServer(), Stone.SPACE)) return; // the Space Stone already exists
		ItemEntity stone = new ItemEntity(level, at.x, top.getY() + 0.6, at.z, new ItemStack(ModItems.SPACE_STONE), 0, 0, 0);
		stone.setGlowingTag(true);
		stone.setNoGravity(true);
		stone.setPickUpDelay(40);
		level.addFreshEntity(stone);
		GLOWING_STONES.add(stone);
		for (ServerPlayer p : PlayerLookup.around(level, at, 48)) alert(p, Component.translatable("msg.infinity_gauntlet.space.released"));
	}

	// ------------------------------------------------------------------ helpers

	static void alert(ServerPlayer p, Component text) {
		ServerPlayNetworking.send(p, new ModNetwork.QuestAlert(text));
	}

	// ------------------------------------------------------------------ the asteroid

	/** Builds the asteroid and the tower the first time; returns the arrival point (feet). */
	public static BlockPos ensureAsteroid(ServerLevel level) {
		BlockPos arrival = level.getAttached(ARRIVAL);
		if (Boolean.TRUE.equals(level.getAttached(ASTEROID_BUILT)) && arrival != null) return arrival;
		for (int cx = (CX - RX - 8) >> 4; cx <= (CX + RX + 8) >> 4; cx++)
			for (int cz = (CZ - RZ - 8) >> 4; cz <= (CZ + RZ + 8) >> 4; cz++) level.getChunk(cx, cz);
		buildRock(level);
		int towerBase = top(level, TOWER_X, TOWER_Z);
		buildTower(level, new BlockPos(TOWER_X, towerBase, TOWER_Z));
		int padY = top(level, 0, PAD_Z) + 1;
		for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
			level.setBlock(new BlockPos(dx, padY - 1, PAD_Z + dz), ModBlocks.QUEST_SPACE_CARVED_ICE.defaultBlockState(), Block.UPDATE_CLIENTS);
		buildReturnRift(level, new BlockPos(0, top(level, 0, RETURN_Z) + 1, RETURN_Z));
		arrival = new BlockPos(0, padY, PAD_Z);
		level.setAttached(ARRIVAL, arrival);
		level.setAttached(ASTEROID_BUILT, true);
		return arrival;
	}

	private static double noise(int x, int y, int z) {
		return Math.sin(x * 0.31 + y * 0.17) * Math.cos(z * 0.27 - y * 0.13) * 0.5
				+ Math.sin(x * 0.11 - z * 0.19 + 1.7) * 0.35 + Math.cos(y * 0.41 + x * 0.07) * 0.15;
	}

	private static void buildRock(ServerLevel level) {
		BlockState snow = Blocks.SNOW_BLOCK.defaultBlockState(), packed = Blocks.PACKED_ICE.defaultBlockState(),
				blue = Blocks.BLUE_ICE.defaultBlockState(), stone = Blocks.STONE.defaultBlockState(),
				tuff = Blocks.TUFF.defaultBlockState(), ice = Blocks.ICE.defaultBlockState();
		for (int x = -RX - 4; x <= RX + 4; x++) {
			for (int z = -RZ - 4; z <= RZ + 4; z++) {
				// The top is flattened a little so the tower and the pad stand level.
				for (int y = -RY - 4; y <= RY; y++) {
					double d = (double) x * x / (RX * RX) + (double) y * y / (RY * RY) * (y > 0 ? 2.2 : 1.0) + (double) z * z / (RZ * RZ);
					if (d + noise(x, y, z) * 0.18 > 1) continue;
					double inner = (double) x * x / ((RX - 3.0) * (RX - 3.0)) + (double) y * y / ((RY - 3.0) * (RY - 3.0)) * (y > 0 ? 2.2 : 1.0) + (double) z * z / ((RZ - 3.0) * (RZ - 3.0));
					double n = noise(x * 2, y * 2, z * 2);
					BlockState s = inner > 1 ? (n > 0.45 ? blue : y < -4 ? (n < -0.3 ? tuff : stone) : packed)
							: (n > 0.5 ? blue : n < -0.35 ? stone : n < -0.1 ? tuff : packed);
					if (y < -RY + 2 && n > 0.2) s = ice;
					level.setBlock(new BlockPos(CX + x, CY + y, CZ + z), s, Block.UPDATE_CLIENTS);
				}
			}
		}
		// Snow crust on top.
		for (int x = -RX; x <= RX; x++) {
			for (int z = -RZ; z <= RZ; z++) {
				int t = top(level, x, z);
				if (t > CY - RY - 4 && level.getBlockState(new BlockPos(x, t, z)).is(Blocks.PACKED_ICE)) level.setBlock(new BlockPos(x, t, z), snow, Block.UPDATE_CLIENTS);
			}
		}
	}

	/** Y of the highest solid block in the column (asteroid range only). */
	private static int top(ServerLevel level, int x, int z) {
		for (int y = CY + RY + 2; y > CY - RY - 6; y--) if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) return y;
		return CY;
	}

	private static void buildTower(ServerLevel level, BlockPos base) {
		BlockState carved = ModBlocks.QUEST_SPACE_CARVED_ICE.defaultBlockState(), packed = Blocks.PACKED_ICE.defaultBlockState(),
				blue = Blocks.BLUE_ICE.defaultBlockState(), lantern = Blocks.SEA_LANTERN.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
		java.util.Random rnd = new java.util.Random(42);
		int R = TOWER_R, H = TOWER_H;
		// Foundation disc.
		for (int dx = -R - 2; dx <= R + 2; dx++) for (int dz = -R - 2; dz <= R + 2; dz++) {
			if (dx * dx + dz * dz > (R + 2.4) * (R + 2.4)) continue;
			for (int dy = -4; dy <= 0; dy++) level.setBlock(base.offset(dx, dy, dz), dy == 0 ? carved : packed, Block.UPDATE_CLIENTS);
		}
		// Walls, windows, bands, pillars; the room on top is 5 high.
		for (int dy = 1; dy <= H + 5; dy++) {
			for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				BlockPos p = base.offset(dx, dy, dz);
				if (d > R + 0.45) continue;
				if (d > R - 0.55) {
					double ang = Math.toDegrees(Math.atan2(dz, dx));
					boolean pillar = Math.abs(((ang + 360) % 45) - 22.5) > 19;
					boolean window = !pillar && (dy % 8 == 4 || dy % 8 == 5) && Math.abs(((ang + 360) % 90) - 45) < 9;
					boolean door = dz < 0 && Math.abs(dx) <= 1 && dy <= 3;
					boolean bigWindow = dy > H && !pillar && Math.abs(((ang + 360) % 90) - 45) < 16 && dy < H + 4;
					BlockState s = pillar ? packed : dy % 8 == 0 ? blue : carved;
					if (door || window || bigWindow) s = air;
					else if (!pillar && dy > 6 && dy < H && rnd.nextFloat() < 0.04f) s = air; // ancient, crumbling
					level.setBlock(p, s, Block.UPDATE_CLIENTS);
				} else {
					level.setBlock(p, air, Block.UPDATE_CLIENTS);
				}
			}
		}
		// Central column with lanterns, and the spiral staircase around it (20 steps a turn).
		for (int dy = 1; dy <= H; dy++) level.setBlock(base.offset(0, dy, 0), dy % 6 == 3 ? lantern : blue, Block.UPDATE_CLIENTS);
		List<int[]> lastSteps = new ArrayList<>();
		for (int k = 0; k < H; k++) {
			double a = k * Math.PI * 2 / 20 + Math.PI / 2;
			for (double r = 1.3; r <= R - 1.0; r += 0.5) {
				for (double da = -0.09; da <= 0.09; da += 0.09) {
					int x = (int) Math.round(Math.cos(a + da) * r), z = (int) Math.round(Math.sin(a + da) * r);
					if (x == 0 && z == 0) continue;
					level.setBlock(base.offset(x, k + 1, z), carved, Block.UPDATE_CLIENTS);
					if (k >= H - 4) lastSteps.add(new int[]{x, z});
				}
			}
		}
		// Top floor with an opening where the stairs arrive.
		for (int dx = -R + 1; dx <= R - 1; dx++) for (int dz = -R + 1; dz <= R - 1; dz++) {
			if (dx * dx + dz * dz > (R - 0.5) * (R - 0.5)) continue;
			boolean hole = false;
			for (int[] s : lastSteps) if (Math.abs(s[0] - dx) <= 1 && Math.abs(s[1] - dz) <= 1) hole = true;
			if (!hole) level.setBlock(base.offset(dx, H, dz), (dx + dz) % 2 == 0 ? carved : packed, Block.UPDATE_CLIENTS);
		}
		// Pedestal and the Tesseract.
		BlockPos ped = base.offset(0, H + 1, 0);
		level.setBlock(ped, blue, Block.UPDATE_CLIENTS);
		for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) level.setBlock(ped.offset(o[0], 0, o[1]), carved, Block.UPDATE_CLIENTS);
		level.setBlock(ped.above(), ModBlocks.TESSERACT.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setAttached(TESSERACT_POS, ped.above());
		for (int[] o : new int[][]{{3, 3}, {-3, 3}, {3, -3}, {-3, -3}}) level.setBlock(base.offset(o[0], H + 1, o[1]), lantern, Block.UPDATE_CLIENTS);
		// Spire.
		for (int dy = 0; dy <= 10; dy++) {
			double rr = (R + 0.4) * (1 - dy / 10.5);
			for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d <= rr && d > rr - 1.3) level.setBlock(base.offset(dx, H + 6 + dy, dz), dy % 3 == 0 ? blue : packed, Block.UPDATE_CLIENTS);
			}
		}
	}

	private static void buildReturnRift(ServerLevel level, BlockPos base) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = 0; dy <= 5; dy++) {
				boolean frame = Math.abs(dx) == 2 || dy == 0 || dy == 5;
				level.setBlock(base.offset(dx, dy, 0), frame ? (dy == 0 ? ModBlocks.QUEST_SPACE_CARVED_ICE : Blocks.BLUE_ICE).defaultBlockState()
						: ModBlocks.FROST_PORTAL.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
	}

	private SpaceStoneQuest() {}
}
