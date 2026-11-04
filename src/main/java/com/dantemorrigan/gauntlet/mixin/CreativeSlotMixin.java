package com.dantemorrigan.gauntlet.mixin;

import com.dantemorrigan.gauntlet.stones.StoneKeeper;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.server.level.ServerPlayer;

/** A stone deleted from the creative inventory no longer exists: the world takes it back. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CreativeSlotMixin {
	@Shadow public ServerPlayer player;

	@Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"))
	private void infinityGauntlet$stoneDeleted(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
		if (!player.hasInfiniteMaterials()) return;
		int slot = packet.slotNum();
		if (slot < 0 || slot >= player.inventoryMenu.slots.size()) return;
		StoneKeeper.creativeReplaced(player, player.inventoryMenu.getSlot(slot).getItem(), packet.itemStack());
	}
}
