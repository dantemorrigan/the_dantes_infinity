package com.dantemorrigan.gauntlet;

import com.dantemorrigan.gauntlet.snap.InfinitySnap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** The Time stone on its own: in the White Void, right-click rewinds the universe to before the Infinity Snap. */
public class TimeStoneItem extends Item {
	public TimeStoneItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!InfinitySnap.inVoid(player)) return InteractionResult.PASS;
		if (player instanceof ServerPlayer sp) return InfinitySnap.rewind(sp) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
		return InteractionResult.SUCCESS;
	}
}
