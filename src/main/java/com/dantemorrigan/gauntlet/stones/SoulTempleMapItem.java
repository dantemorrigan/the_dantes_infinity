package com.dantemorrigan.gauntlet.stones;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;

/** An old parchment: unrolled (right-click) it becomes a real map with the Temple of Soul marked. */
public final class SoulTempleMapItem extends Item {
	public SoulTempleMapItem(Properties properties) { super(properties); }

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
		ServerLevel overworld = sp.level().getServer().overworld();
		BlockPos site = SoulTemple.site(sp.level().getServer());
		ItemStack map = MapItem.create(overworld, site.getX(), site.getZ(), (byte) 2, true, true);
		MapItem.renderBiomePreviewMap(overworld, map);
		net.minecraft.world.level.saveddata.maps.MapItemSavedData.addTargetDecoration(map, site, "+", MapDecorationTypes.RED_X);
		map.set(DataComponents.ITEM_NAME, Component.translatable("item.infinity_gauntlet.quest_soul_temple_map").withStyle(ChatFormatting.GOLD));
		sp.getItemInHand(hand).shrink(1);
		if (!sp.getInventory().add(map)) sp.drop(map, false);
		level.playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
		return InteractionResult.SUCCESS;
	}
}
