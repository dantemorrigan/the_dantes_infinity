package com.dantemorrigan.gauntlet.doom;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static com.dantemorrigan.gauntlet.doom.LatveriaKit.*;

/**
 * The great statue of Doctor Doom on the pedestal in the middle of the square: sixty blocks of him, standing with his
 * arms crossed on the chest, in a hood and a cloak, his iron mask and his burning eyes toward the city gate. Built in
 * boxes in the statue's own terms: x is the world's x (east), dz runs from the statue's back (negative) to its face (positive,
 * south), y is counted from the pedestal's top.
 */
final class LatveriaStatue {
	static final int CX = 0, CZ = 30;

	private static final BlockState ARMOR = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), PLATE = Blocks.SMOOTH_STONE.defaultBlockState(),
			IRON = Blocks.IRON_BLOCK.defaultBlockState(), CLOTH = Blocks.GREEN_WOOL.defaultBlockState(), CLOAK = Blocks.GREEN_CONCRETE.defaultBlockState(),
			DEEP = Blocks.GREEN_TERRACOTTA.defaultBlockState(), DARK = Blocks.POLISHED_BLACKSTONE.defaultBlockState(), GOLD = Blocks.GOLD_BLOCK.defaultBlockState(),
			EYE = Blocks.VERDANT_FROGLIGHT.defaultBlockState(), SLIT = Blocks.BLACK_CONCRETE.defaultBlockState();

	private LatveriaStatue() {}

	/** The pedestal's top surface (the first row of the statue stands on it). */
	static final int TOP = G + 1 + 7;

	private static void box(int x0, int y0, int dz0, int x1, int y1, int dz1, BlockState s) {
		fill(CX + x0, TOP + y0, CZ + dz0, CX + x1, TOP + y1, CZ + dz1, s);
	}

	private static void block(int x, int y, int dz, BlockState s) { set(CX + x, TOP + y, CZ + dz, s); }

	static void build() {
		pedestal();
		boots();
		legs();
		tunic();
		torso();
		arms();
		head();
		cloak();
	}

	// ------------------------------------------------------------------ the pedestal: three tiers, steps on the south

	private static void pedestal() {
		int b = G + 1;
		BlockState base = Blocks.POLISHED_DEEPSLATE.defaultBlockState(), brick = Blocks.DEEPSLATE_BRICKS.defaultBlockState(), trim = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		fill(CX - 9, b, CZ - 9, CX + 9, b + 1, CZ + 9, base);
		fill(CX - 9, b + 2, CZ - 9, CX + 9, b + 2, CZ + 9, trim);
		fill(CX - 8, b + 3, CZ - 8, CX + 8, b + 3, CZ + 8, brick);
		fill(CX - 7, b + 3, CZ - 7, CX + 7, b + 6, CZ + 7, brick);
		fill(CX - 8, b + 3, CZ - 8, CX + 8, b + 3, CZ + 8, trim);
		fill(CX - 7, b + 6, CZ - 7, CX + 7, b + 6, CZ + 7, trim);
		// Gold studs along the plinth and green banners (cloth plates) on the dado of the faces.
		for (int i = -6; i <= 6; i += 3) {
			set(CX + i, b + 4, CZ + 8, GOLD);
			set(CX + i, b + 4, CZ - 8, GOLD);
			set(CX + 8, b + 4, CZ + i, GOLD);
			set(CX - 8, b + 4, CZ + i, GOLD);
		}
		for (int s : new int[]{-1, 1}) {
			fill(CX + s * 4 - 1, b + 3, CZ + 8, CX + s * 4 + 1, b + 6, CZ + 8, Blocks.GREEN_WOOL);
		}
		// Steps up the south face: from the moat's bridge to the statue's feet.
		for (int i = 0; i < 3; i++) {
			fill(CX - 2, b + i, CZ + 9 + (2 - i), CX + 2, b + i, CZ + 9 + (2 - i), stairs(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.NORTH, false));
			if (i > 0) fill(CX - 2, b, CZ + 9 + (2 - i), CX + 2, b + i - 1, CZ + 9 + (2 - i), base);
		}
		// Braziers at the pedestal's front corners.
		for (int s : new int[]{-1, 1}) for (int t : new int[]{-1, 1}) {
			fill(CX + s * 9, b + 3, CZ + t * 9, CX + s * 9, b + 4, CZ + t * 9, Blocks.POLISHED_BLACKSTONE_WALL);
			set(CX + s * 9, b + 5, CZ + t * 9, Blocks.LANTERN);
		}
		// The ground of the pedestal is the statue's floor: a smooth top over the last tier.
		fill(CX - 7, b + 7 - 1, CZ - 7, CX + 7, b + 7 - 1, CZ + 7, Blocks.POLISHED_DEEPSLATE);
	}

	// ------------------------------------------------------------------ the figure

	private static void boots() {
		for (int s : new int[]{-1, 1}) {
			int x0 = s < 0 ? -5 : 2, x1 = s < 0 ? -2 : 5;
			box(x0, 0, -3, x1, 3, 3, DARK);              // the boot
			box(x0, 0, 4, x1, 1, 5, DARK);                // the toe, a block longer than the heel
			box(x0, 3, 3, x1, 3, 3, ARMOR);               // the cuff
		}
	}

	private static void legs() {
		for (int s : new int[]{-1, 1}) {
			int x0 = s < 0 ? -5 : 2, x1 = s < 0 ? -2 : 5;
			box(x0, 4, -2, x1, 11, 2, ARMOR);             // the greaves
			box(x0, 8, 3, x1, 10, 3, PLATE);              // the knee plate
			box(x0, 12, -2, x1, 17, 2, ARMOR);            // the thigh
		}
		// The crotch bridge: the legs are one piece with the tunic above.
		box(-1, 14, -2, 1, 17, 2, CLOTH);
	}

	private static void tunic() {
		box(-6, 16, -3, 6, 22, 3, CLOTH);                 // the green tunic over the hips, to the knees at the front
		box(-6, 16, 3, 6, 21, 4, CLOTH);                  // the front flap
		box(-1, 16, 4, 1, 21, 4, DEEP);                   // its seam
		box(-6, 22, -3, 6, 23, 3, DARK);                  // the belt
		box(-1, 22, 4, 1, 23, 4, GOLD);                   // the buckle
		box(-1, 22, 4, 1, 23, 4, GOLD);
	}

	private static void torso() {
		box(-5, 24, -3, 5, 30, 3, CLOTH);                 // the waist and ribs under the plate
		box(-7, 31, -3, 7, 37, 3, ARMOR);                 // the cuirass
		box(-6, 31, 4, 6, 36, 4, ARMOR);                  // its front, a block proud
		box(-4, 32, 4, 4, 35, 4, PLATE);                  // the breastplate
		box(-1, 33, 5, 1, 35, 5, GOLD);                   // Doom's roundel on the chest
		block(0, 34, 5, CLOAK);
		box(-5, 24, 4, 5, 30, 4, ARMOR);                  // the abdomen plates
		for (int y = 25; y <= 29; y += 2) box(-5, y, 4, 5, y, 4, PLATE);
		// The gorget and the collar rising to the mask.
		box(-3, 38, -3, 3, 39, 3, ARMOR);
		box(-4, 38, -3, 4, 38, 4, DARK);
	}

	private static void arms() {
		for (int s : new int[]{-1, 1}) {
			int xa = s < 0 ? -10 : 8, xb = s < 0 ? -8 : 10;
			// The pauldron with its rivets and spikes, and the upper arm down to the elbow.
			box(xa - (s < 0 ? 1 : 0), 36, -3, xb + (s < 0 ? 0 : 1), 38, 3, ARMOR);
			box(xa - (s < 0 ? 1 : 0), 39, -2, xb + (s < 0 ? 0 : 1), 39, 2, PLATE);
			block(s < 0 ? -11 : 11, 40, 0, IRON);
			block(s < 0 ? -11 : 11, 41, 0, IRON);
			box(xa, 28, -2, xb, 35, 2, ARMOR);
			// The elbow comes forward and the forearm crosses the chest.
			box(xa, 28, 3, xb, 33, 6, ARMOR);
		}
		// The crossing forearms: the left one low across to the right shoulder, the right one high across to the left.
		box(-9, 28, 3, 3, 30, 6, ARMOR);                   // lower forearm, from the left elbow
		box(-3, 31, 3, 9, 33, 6, ARMOR);                   // upper forearm, from the right elbow
		box(-9, 28, 6, 3, 30, 6, PLATE);                   // the plates along them
		box(-3, 31, 6, 9, 33, 6, PLATE);
		// The gauntlets: fists at the ends, on the shoulders' side.
		box(3, 28, 4, 5, 30, 7, DARK);
		box(-5, 31, 4, -3, 33, 7, DARK);
	}

	private static void head() {
		// The iron mask: a flat, cold plate, smooth and blank but for its slits; no expression but menace.
		BlockState mask = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), shade = Blocks.GRAY_CONCRETE.defaultBlockState();
		box(-3, 40, -3, 3, 49, 3, mask);                  // the helm
		box(-3, 40, 4, 3, 49, 4, mask);                   // the face plate
		box(-3, 40, 4, -3, 46, 4, shade);                 // the cheek plates, darker at the edges
		box(3, 40, 4, 3, 46, 4, shade);
		// The brow: a heavy ridge standing out over the eyes and sloping down to the middle, a frown cut in steel.
		box(-3, 47, 5, -2, 47, 5, mask);
		box(2, 47, 5, 3, 47, 5, mask);
		box(-1, 46, 5, 1, 46, 5, mask);
		// The eyes: narrow slits in shadow, burning green deep inside.
		box(-3, 45, 4, -1, 45, 4, SLIT);
		box(1, 45, 4, 3, 45, 4, SLIT);
		box(-2, 45, 4, -1, 45, 4, EYE);
		box(1, 45, 4, 2, 45, 4, EYE);
		// The bridge of the nose, standing out from between the eyes and down the plate.
		box(0, 43, 5, 0, 45, 5, mask);
		// The mouth: one straight dark slit, no lips, no smile.
		box(-2, 42, 4, 2, 42, 4, SLIT);
		// Rivets at the cheeks.
		for (int x : new int[]{-3, 3}) block(x, 43, 5, shade);
		// The hood: drapes at the sides, a cowl above, a point at the top.
		box(-5, 38, -4, -4, 52, 4, CLOTH);
		box(4, 38, -4, 5, 52, 4, CLOTH);
		box(-5, 50, -4, 5, 51, 4, CLOTH);
		box(-4, 52, -4, 4, 52, 4, CLOTH);
		box(-3, 53, -4, 3, 53, 3, CLOTH);
		box(-2, 54, -3, 2, 54, 3, CLOTH);
		box(-1, 55, -3, 1, 55, 2, CLOTH);
		box(0, 56, -3, 0, 57, 1, CLOTH);
		box(-3, 38, -4, 3, 49, -4, CLOTH);                 // the back of the hood, down onto the cloak
		box(-4, 50, 5, 4, 50, 5, DEEP);                    // the hood's front edge, over the brow
		box(-4, 51, 5, 4, 51, 5, DEEP);
		box(-5, 38, 4, 5, 39, 5, CLOTH);                   // the cowl's collar in front
		box(-5, 40, 5, -4, 51, 6, CLOTH);                  // the hood comes forward round the face, so the mask sits in its shadow
		box(4, 40, 5, 5, 51, 6, CLOTH);
		box(-4, 50, 6, 4, 51, 6, DEEP);
	}

	private static void cloak() {
		// The great cloak down the back to the pedestal: four blocks thick, a little wider at the shoulders, folded in columns,
		// and never reaching farther back or out than the pedestal's top beneath it.
		for (int y = 0; y <= 38; y++) {
			int flare = 7 + (y * 3) / 38;
			for (int x = -flare; x <= flare; x++) {
				int fold = Math.floorMod(x, 4) < 2 ? 0 : 1;
				BlockState mat = (Math.abs(x) + y / 6) % 3 == 0 ? DEEP : (Math.floorMod(x, 4) < 2 ? CLOAK : CLOTH);
				box(x, y, -7 + fold, x, y, -3, mat);
			}
		}
	}
}
