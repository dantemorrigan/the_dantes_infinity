package com.dantemorrigan.gauntlet.stones;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** The Tesseract: right-click folds space and sends its holder home to Earth. Thrown into lava it breaks open. */
public final class TesseractItem extends Item {
	public TesseractItem(Properties properties) { super(properties); }

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp) SpaceStoneQuest.useTesseract(sp, hand);
		return InteractionResult.SUCCESS;
	}
}
