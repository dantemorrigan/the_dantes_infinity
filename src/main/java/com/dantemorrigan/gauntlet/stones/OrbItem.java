package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.net.ModStatus;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The sealed Orb. Right-click opens it: the Power Stone goes into the backpack (never into the hand). */
public final class OrbItem extends Item {
	public OrbItem(Properties properties) { super(properties); }

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp) open(sp, hand);
		return InteractionResult.SUCCESS;
	}

	public static void open(ServerPlayer player, InteractionHand hand) {
		ServerLevel level = (ServerLevel) player.level();
		player.setItemInHand(hand, new ItemStack(ModItems.POWER_ORB_OPEN));
		level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.2F, 0.6F);
		if (!StoneRegistry.markReleased(level.getServer(), Stone.POWER)) {
			ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.orb.empty"));
			return;
		}
		level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5F, 0.5F);
		level.sendParticles(new DustParticleOptions(Stone.POWER.color, 2.0F), player.getX(), player.getY() + 1.2, player.getZ(),
				60, 0.6, 0.6, 0.6, 0.15);
		giveIntoBackpack(player, new ItemStack(ModItems.POWER_STONE));
		ModStatus.send(player, Component.translatable("msg.infinity_gauntlet.orb.opened"));
		player.sendSystemMessage(Component.translatable("msg.infinity_gauntlet.orb.warning"));
	}

	/** Puts the stack into the main inventory first (slots 9..35), then the hotbar away from the selected slot, else drops it. */
	public static void giveIntoBackpack(ServerPlayer player, ItemStack stack) {
		Inventory inv = player.getInventory();
		for (int i = Inventory.SELECTION_SIZE; i < Inventory.INVENTORY_SIZE; i++) {
			if (inv.getItem(i).isEmpty()) { inv.setItem(i, stack); return; }
		}
		for (int i = 0; i < Inventory.SELECTION_SIZE; i++) {
			if (i != inv.getSelectedSlot() && inv.getItem(i).isEmpty()) { inv.setItem(i, stack); return; }
		}
		player.drop(stack, false);
	}
}
