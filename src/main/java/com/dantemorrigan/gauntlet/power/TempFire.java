package com.dantemorrigan.gauntlet.power;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Fire the gauntlet sets burns out on its own. Every flame a power lights is remembered with a time to die (a few to a dozen
 * seconds, varied so a scorched field does not go dark all at once) and is put out then if it still burns; nothing it
 * lights can keep burning for ever on a block that will not burn away (Latveria's masonry and roofs, for one).
 */
public final class TempFire {
	private static final Map<ServerLevel, Map<Long, Long>> FIRES = new HashMap<>();
	/** Set while a power lights a flame: Latveria lets only these through, never fire that spreads by itself. */
	public static boolean placing;

	private TempFire() {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(TempFire::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			// Put everything out before the world is saved, so no flame outlives the session that lit it.
			for (var e : FIRES.entrySet()) for (long p : e.getValue().keySet()) douse(e.getKey(), BlockPos.of(p));
			FIRES.clear();
		});
	}

	/** Lights a flame that goes out by itself within {@code minTicks}..{@code minTicks + 120} ticks. */
	public static boolean light(ServerLevel level, BlockPos pos, BlockState fire, int minTicks) {
		placing = true;
		boolean set;
		try {
			set = level.setBlock(pos, fire, Block.UPDATE_ALL);
		} finally {
			placing = false;
		}
		if (set) FIRES.computeIfAbsent(level, l -> new HashMap<>()).put(pos.asLong(), level.getGameTime() + minTicks + level.getRandom().nextInt(120));
		return set;
	}

	public static boolean light(ServerLevel level, BlockPos pos) {
		return light(level, pos, BaseFireBlock.getState(level, pos), 100);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 10 != 0) return;
		for (var e : FIRES.entrySet()) {
			ServerLevel level = e.getKey();
			long now = level.getGameTime();
			for (Iterator<Map.Entry<Long, Long>> it = e.getValue().entrySet().iterator(); it.hasNext(); ) {
				var f = it.next();
				if (now < f.getValue()) continue;
				it.remove();
				douse(level, BlockPos.of(f.getKey()));
			}
		}
	}

	private static void douse(ServerLevel level, BlockPos pos) {
		if (!level.hasChunkAt(pos)) return;
		BlockState s = level.getBlockState(pos);
		if (s.getBlock() instanceof BaseFireBlock) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
	}
}
