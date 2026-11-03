package com.dantemorrigan.gauntlet;

import com.dantemorrigan.gauntlet.snap.InfinitySnap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Rewinds the player outside the White Void, and undoes the Snap inside it. */
public class TimeStoneItem extends Item {
	public TimeStoneItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp) {
			if (InfinitySnap.inVoid(player)) return InfinitySnap.rewind(sp) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
			if (!com.dantemorrigan.gauntlet.power.TimeAbility.rewindLoose(sp)) return InteractionResult.FAIL;
			player.getCooldowns().addCooldown(player.getItemInHand(hand), 100);
		}
		return InteractionResult.SUCCESS;
	}
}
