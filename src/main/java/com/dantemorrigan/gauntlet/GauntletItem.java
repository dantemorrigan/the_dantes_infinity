package com.dantemorrigan.gauntlet;

import com.dantemorrigan.gauntlet.menu.GauntletMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class GauntletItem extends Item {
	public GauntletItem(Properties properties) {
		super(properties);
	}

	/** Right-click with the gauntlet in the MAIN hand opens the stone menu. Off-hand is reserved for powers. */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			ItemStack gauntlet = player.getItemInHand(hand);
			int slot = player.getInventory().getSelectedSlot();
			player.openMenu(new SimpleMenuProvider(
					(id, inventory, p) -> new GauntletMenu(id, inventory, gauntlet, slot),
					Component.translatable("container.infinity_gauntlet.gauntlet")));
		}
		return InteractionResult.SUCCESS;
	}
}
