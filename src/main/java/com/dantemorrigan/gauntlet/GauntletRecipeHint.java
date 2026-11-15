package com.dantemorrigan.gauntlet;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The first time a player holds an Uru ingot (the moment the Infinity Gauntlet's recipe unlocks in the recipe book), the
 * recipe is written out in chat, once, with where to find it again. Also used by {@code /gauntlet recipe}.
 */
public final class GauntletRecipeHint {
	private static final AttachmentType<Boolean> SHOWN = AttachmentRegistry.<Boolean>builder()
			.persistent(Codec.BOOL).copyOnDeath().buildAndRegister(InfinityGauntlet.id("gauntlet_recipe_hint"));

	private GauntletRecipeHint() {}

	public static void init() {
		// Anyone can read the recipe again, no operator rights needed.
		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(
				net.minecraft.commands.Commands.literal("gauntlet_recipe").executes(ctx -> {
					show(ctx.getSource().getPlayerOrException());
					return 1;
				})));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 40 != 0) return;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (Boolean.TRUE.equals(p.getAttached(SHOWN))) continue;
				if (!p.getInventory().contains(st -> st.is(ModItems.URU_INGOT))) continue;
				p.setAttached(SHOWN, true);
				show(p);
			}
		});
	}

	/** Writes the recipe in the player's chat. */
	public static void show(ServerPlayer p) {
		p.sendSystemMessage(Component.translatable("msg.infinity_gauntlet.recipe.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		p.sendSystemMessage(Component.literal("   U  G  D").withStyle(ChatFormatting.YELLOW));
		p.sendSystemMessage(Component.literal("   G  R  G").withStyle(ChatFormatting.YELLOW));
		p.sendSystemMessage(Component.literal("   D  G  U").withStyle(ChatFormatting.YELLOW));
		p.sendSystemMessage(Component.translatable("msg.infinity_gauntlet.recipe.legend").withStyle(ChatFormatting.GRAY));
		p.sendSystemMessage(Component.translatable("msg.infinity_gauntlet.recipe.where").withStyle(ChatFormatting.GRAY));
	}
}
