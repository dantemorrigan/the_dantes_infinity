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
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** An ancient chart: unrolled (right-click) it becomes a filled map marking the single Temple of Time in the world. */
public final class TimeTempleMapItem extends Item {
	public TimeTempleMapItem(Properties properties) { super(properties); }

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
		ServerLevel overworld = sp.level().getServer().overworld();
		BlockPos site = TimeTemple.site(sp.level().getServer());
		ItemStack map = MapItem.create(overworld, site.getX(), site.getZ(), (byte) 2, true, true);
		MapItem.renderBiomePreviewMap(overworld, map);
		MapItemSavedData.addTargetDecoration(map, site, "+", MapDecorationTypes.RED_X);
		map.set(DataComponents.ITEM_NAME, Component.translatable("item.infinity_gauntlet.quest_time_temple_map").withStyle(ChatFormatting.AQUA));
		level.playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.9F);
		return InteractionResult.SUCCESS.heldItemTransformedTo(map);
	}
}
