package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.ModBlocks;
import com.dantemorrigan.gauntlet.Stone;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

/** Puts a stone's source back to its starting state after the stone was destroyed. */
final class StoneSources {
	static void restore(MinecraftServer server, Stone stone) {
		switch (stone) {
			case POWER -> {
				ServerLevel overworld = server.overworld();
				BlockPos site = overworld.getAttached(StoneRegistry.POWER_ORB_SITE);
				if (site != null && overworld.hasChunkAt(site)) {
					overworld.setBlock(site, ModBlocks.POWER_ORB.defaultBlockState(), Block.UPDATE_ALL);
					overworld.setAttached(StoneRegistry.POWER_SITE, site);
				} else {
					overworld.removeAttached(StoneRegistry.NEXT_SHOWER); // showers start over
				}
			}
			case SPACE -> SpaceStoneQuest.restoreTesseract(server);
			default -> {} // Reality's mist simply comes back once the stone is no longer found
		}
	}

	private StoneSources() {}
}
