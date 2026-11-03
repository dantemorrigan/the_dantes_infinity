package com.dantemorrigan.gauntlet.extractor;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * Infinity Extractor. Hold right-click on a gauntlet wearer to run one extraction cycle; three finished cycles pull
 * the chosen stone out of their gauntlet into the extractor. Shift + right-click: take the stone out of the chamber
 * (or, with an empty chamber, choose which stone to pull). Right-click with a stone in the chamber and no target
 * puts it back into your own gauntlet. All rules live in {@link StoneExtraction}.
 */
public class InfinityExtractorItem extends Item {
	private static final int MAX_USE = 72000;

	public InfinityExtractorItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResult.CONSUME;
		ItemStack stack = player.getItemInHand(hand);
		if (player.isShiftKeyDown()) {
			StoneExtraction.sneakUse(sp, stack);
			return InteractionResult.SUCCESS;
		}
		if (StoneExtraction.begin(sp, stack)) {
			player.startUsingItem(hand);
			return InteractionResult.CONSUME;
		}
		return InteractionResult.FAIL;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return MAX_USE;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.NONE;
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksRemaining) {
		if (!level.isClientSide() && entity instanceof ServerPlayer player) StoneExtraction.tick(player, stack);
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
		if (!level.isClientSide() && entity instanceof ServerPlayer player) StoneExtraction.release(player, stack);
		return true;
	}
}
