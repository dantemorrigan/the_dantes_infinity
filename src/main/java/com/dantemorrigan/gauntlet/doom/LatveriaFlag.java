package com.dantemorrigan.gauntlet.doom;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static com.dantemorrigan.gauntlet.doom.LatveriaKit.set;

/**
 * The flag of Latveria in blocks: a green field crossed by a black cross edged in red, and in the middle a black disc in a
 * red ring with the green horned emblem (the reference is art/latveria/flag_of_latveria.png). Drawn in wool, 25 by 13 at full
 * size, or smaller by taking every second (or third) cell, on any vertical plane facing any way.
 */
final class LatveriaFlag {
	static final int W = 25, H = 13;
	/** G green, K black, R red; the top row first. */
	private static final String[] CELLS = {
		"GGGGRKKKRGGGGGGGGGGGGGGGG",
		"GGGGRKKKRGGGGGGGGGGGGGGGG",
		"GGGGRRRRRGGGGGGGGGGGGGGGG",
		"GGGRRKKKRRGGGGGGGGGGGGGGG",
		"RRRRGKKKGRRRRRRRRRRRRRRRR",
		"KKRGGKKKGGRKKKKKKKKKKKKKK",
		"KKRKGGKGGKRKKKKKKKKKKKKKK",
		"KKRKKGGGKKRKKKKKKKKKKKKKK",
		"RRRRKGKGKRRRRRRRRRRRRRRRR",
		"GGGRRKKKRRGGGGGGGGGGGGGGG",
		"GGGGRRRRRGGGGGGGGGGGGGGGG",
		"GGGGRKKKRGGGGGGGGGGGGGGGG",
		"GGGGRKKKRGGGGGGGGGGGGGGGG"
	};

	private LatveriaFlag() {}

	private static BlockState block(char c) {
		return (c == 'K' ? Blocks.BLACK_WOOL : c == 'R' ? Blocks.RED_WOOL : Blocks.GREEN_WOOL).defaultBlockState();
	}

	/** Width in cells of a flag drawn at this scale (1 = full size, 2 = half, ...). */
	static int width(int scale) { return (W + scale - 1) / scale; }

	static int height(int scale) { return (H + scale - 1) / scale; }

	/**
	 * Draws the flag with its bottom-left cell, as the viewer sees it, at (x, y, z); the flag faces {@code facing}, the way
	 * the viewer is, so the emblem reads the right way round from there.
	 */
	static void draw(int x, int y, int z, Direction facing, int scale) {
		int w = width(scale), h = height(scale);
		for (int r = 0; r < h; r++) {
			for (int c = 0; c < w; c++) {
				char ch = CELLS[Math.min(H - 1, r * scale + scale / 2)].charAt(Math.min(W - 1, c * scale + scale / 2));
				int yy = y + (h - 1 - r);
				switch (facing) {
					case SOUTH -> set(x + c, yy, z, block(ch));
					case NORTH -> set(x + (w - 1 - c), yy, z, block(ch));
					case EAST -> set(x, yy, z + (w - 1 - c), block(ch));
					default -> set(x, yy, z + c, block(ch));
				}
			}
		}
	}
}
