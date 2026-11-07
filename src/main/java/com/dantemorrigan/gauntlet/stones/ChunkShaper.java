package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reshapes the Overworld chunks around a quest site as they load: a queue of loaded, not yet shaped chunks within
 * {@code radius} of the site, drained a few per tick, with the set of finished chunks persisted on the Overworld.
 * Shared by the Ruined City and the Temples of Soul and Time.
 */
final class ChunkShaper {
	interface Shape { void shape(ServerLevel level, BlockPos site, ChunkPos chunk); }

	private final AttachmentType<BlockPos> site;
	private final AttachmentType<List<Long>> shapedRecord;
	private final int radius, perTick;
	private final Shape shape;
	private final Deque<Long> queue = new ArrayDeque<>();
	private final Set<Long> queued = new HashSet<>();
	private Set<Long> shaped;

	/** Must be created during mod initialization: it registers the persistent record and the world listeners. */
	ChunkShaper(String recordId, AttachmentType<BlockPos> site, int radius, int perTick, Shape shape) {
		this.site = site;
		this.radius = radius;
		this.perTick = perTick;
		this.shape = shape;
		this.shapedRecord = AttachmentRegistry.<List<Long>>builder().persistent(Codec.LONG.listOf())
				.buildAndRegister(InfinityGauntlet.id(recordId));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearRuntime());
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, fresh) -> {
			if (level != level.getServer().overworld()) return;
			BlockPos at = level.getAttached(this.site);
			if (at != null && inRange(at, chunk.getPos())) enqueue(level, chunk.getPos().pack());
		});
	}

	private Set<Long> shaped(ServerLevel level) {
		if (shaped == null) {
			List<Long> list = level.getAttached(shapedRecord);
			shaped = new HashSet<>(list == null ? List.of() : list);
		}
		return shaped;
	}

	boolean isShaped(ServerLevel level, ChunkPos chunk) { return shaped(level).contains(chunk.pack()); }

	/** Called by the shaper once a chunk is finished. */
	void markShaped(ServerLevel level, ChunkPos chunk) {
		shaped(level).add(chunk.pack());
		level.setAttached(shapedRecord, new ArrayList<>(shaped(level)));
	}

	boolean allShaped(ServerLevel level, List<ChunkPos> chunks) {
		for (ChunkPos cp : chunks) if (!level.hasChunk(cp.x(), cp.z()) || !isShaped(level, cp)) return false;
		return true;
	}

	/** Shapes up to {@code perTick} queued chunks that are still loaded. */
	void drain(ServerLevel level, BlockPos at) {
		for (int n = 0; n < perTick && !queue.isEmpty(); n++) {
			long key = queue.poll();
			queued.remove(key);
			ChunkPos cp = ChunkPos.unpack(key);
			if (!level.hasChunk(cp.x(), cp.z()) || shaped(level).contains(key)) continue;
			shape.shape(level, at, cp);
		}
	}

	/** Queues every loaded chunk in range that is not shaped yet (chunks loaded before the site existed). */
	void rescan(ServerLevel level, BlockPos at) {
		int minX = (at.getX() - radius) >> 4, maxX = (at.getX() + radius) >> 4;
		int minZ = (at.getZ() - radius) >> 4, maxZ = (at.getZ() + radius) >> 4;
		for (int x = minX; x <= maxX; x++)
			for (int z = minZ; z <= maxZ; z++)
				if (level.hasChunk(x, z)) enqueue(level, new ChunkPos(x, z).pack());
	}

	/** Forgets all shaping (the site moved). */
	void reset(ServerLevel level) {
		level.removeAttached(shapedRecord);
		clearRuntime();
	}

	private void enqueue(ServerLevel level, long key) {
		if (!shaped(level).contains(key) && queued.add(key)) queue.add(key);
	}

	private boolean inRange(BlockPos at, ChunkPos cp) {
		return cp.x() >= (at.getX() - radius) >> 4 && cp.x() <= (at.getX() + radius) >> 4
				&& cp.z() >= (at.getZ() - radius) >> 4 && cp.z() <= (at.getZ() + radius) >> 4;
	}

	private void clearRuntime() {
		queue.clear();
		queued.clear();
		shaped = null;
	}
}
