package com.dantemorrigan.gauntlet;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Debug: /gauntlet use <power space ...|none> — preview the strong "in use" glow before abilities exist. */
public final class GauntletCommand {
	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(
				Commands.literal("gauntlet")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("use")
								.then(Commands.argument("stones", StringArgumentType.greedyString())
										.executes(ctx -> use(ctx.getSource(), StringArgumentType.getString(ctx, "stones")))))));
	}

	private static int use(CommandSourceStack source, String arg) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ItemStack gauntlet = GauntletState.activeGauntlet(player);
		if (gauntlet.isEmpty()) {
			source.sendFailure(Component.literal("Возьми перчатку в левую руку (F)"));
			return 0;
		}
		Set<Stone> stones = EnumSet.noneOf(Stone.class);
		for (String word : arg.toLowerCase(Locale.ROOT).split("[\\s,]+")) {
			if (word.isEmpty() || word.equals("none")) continue;
			if (word.equals("all")) { stones.addAll(EnumSet.allOf(Stone.class)); continue; }
			Stone match = null;
			for (Stone s : Stone.values()) if (s.id.equals(word)) match = s;
			if (match == null) {
				source.sendFailure(Component.literal("Неизвестный камень: " + word));
				return 0;
			}
			stones.add(match);
		}
		GauntletState.setInUse(gauntlet, stones);
		source.sendSuccess(() -> Component.literal("Используются: " + GauntletState.inUse(gauntlet)), false);
		return 1;
	}

	private GauntletCommand() {}
}
