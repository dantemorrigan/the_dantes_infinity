package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.net.ModStatus;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Ancient forging schematic for the Infinity Gauntlet, found in chests and sold by villagers/traders.
 * Shows the 3x3 crafting recipe in its tooltip and unlocks the Gauntlet and Uru recipes in the recipe book on right-click.
 */
public final class GauntletBlueprintItem extends Item {
	private static final List<ResourceKey<Recipe<?>>> RECIPES = List.of(
			key("gauntlet"),
			key("uru_helmet"),
			key("uru_chestplate"),
			key("uru_leggings"),
			key("uru_boots"),
			key("uru_ingot_from_smelting_raw_uru"),
			key("uru_ingot_from_blasting_raw_uru"),
			key("uru_ingot_from_smelting_uru_ore"),
			key("uru_ingot_from_blasting_uru_ore"),
			key("uru_ingot_from_smelting_deepslate_uru_ore"),
			key("uru_ingot_from_blasting_deepslate_uru_ore"));

	private static ResourceKey<Recipe<?>> key(String path) {
		return ResourceKey.create(Registries.RECIPE, InfinityGauntlet.id(path));
	}

	public GauntletBlueprintItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp) {
			sp.awardRecipesByKey(RECIPES);
			ModStatus.send(sp, Component.translatable("msg.infinity_gauntlet.blueprint.learned"));
			level.playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.9F);
			level.playSound(null, sp.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 0.8F, 1.1F);
		}
		return InteractionResult.SUCCESS;
	}
}
