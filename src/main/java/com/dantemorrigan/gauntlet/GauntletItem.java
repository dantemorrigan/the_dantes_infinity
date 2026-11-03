package com.dantemorrigan.gauntlet;

import com.dantemorrigan.gauntlet.menu.GauntletMenu;
import com.dantemorrigan.gauntlet.power.PowerAbility;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

import java.util.EnumSet;

public class GauntletItem extends Item {
	private static final int MAX_USE = 72000;

	public GauntletItem(Properties properties) {
		super(properties);
	}

	/**
	 * Main hand: right-click opens the stone menu.
	 * Off hand (worn): hold right-click to charge the Power stone, release to strike.
	 */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack gauntlet = player.getItemInHand(hand);
		if (hand == InteractionHand.MAIN_HAND) {
			if (!level.isClientSide()) {
				int slot = player.getInventory().getSelectedSlot();
				player.openMenu(new SimpleMenuProvider(
						(id, inventory, p) -> new GauntletMenu(id, inventory, gauntlet, slot),
						Component.translatable("container.infinity_gauntlet.gauntlet")));
			}
			return InteractionResult.SUCCESS;
		}
		if (!GauntletState.inserted(gauntlet).contains(Stone.POWER) || player.getCooldowns().isOnCooldown(gauntlet)) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer sp && com.dantemorrigan.gauntlet.power.InfinityBeam.active(sp)) return InteractionResult.FAIL;
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
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
		if (level.isClientSide()) return;
		int used = MAX_USE - ticksRemaining;
		if (used == 1) GauntletState.setInUse(stack, EnumSet.of(Stone.POWER)); // strong glow while charging
		if (entity instanceof ServerPlayer player && PowerAbility.mode(stack) == PowerAbility.MODE_CELESTIAL
				&& com.dantemorrigan.gauntlet.celestial.CelestialApocalypse.aimTick(player, stack, used)) {
			player.stopUsingItem();
		}
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
		if (entity instanceof ServerPlayer player) {
			PowerAbility.release(player, stack, MAX_USE - remainingTime);
		}
		return true;
	}

	/** Charge time of an entity currently using a gauntlet (client + server). */
	public static int chargeTicks(LivingEntity entity) {
		return entity.isUsingItem() && entity.getUseItem().getItem() instanceof GauntletItem ? MAX_USE - entity.getUseItemRemainingTicks() : 0;
	}
}
