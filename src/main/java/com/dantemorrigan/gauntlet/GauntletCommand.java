package com.dantemorrigan.gauntlet;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.power.PowerAbility;
import com.dantemorrigan.gauntlet.space.FragmentCapture;
import com.dantemorrigan.gauntlet.space.PlanetDestruction;
import com.dantemorrigan.gauntlet.space.SpaceManager;
import com.dantemorrigan.gauntlet.space.SpaceRecord;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
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
										.executes(ctx -> use(ctx.getSource(), StringArgumentType.getString(ctx, "stones")))))
						.then(Commands.literal("give_power").executes(ctx -> givePower(ctx.getSource())))
						.then(Commands.literal("give_time").executes(ctx -> giveStone(ctx.getSource(), Stone.TIME)))
						.then(Commands.literal("give_stone").then(Commands.argument("stone", StringArgumentType.word())
								.suggests((ctx, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(Stone.values()).map(st -> st.id).toList(), b))
								.executes(ctx -> {
									String id = StringArgumentType.getString(ctx, "stone");
									for (Stone st : Stone.values()) if (st.id.equals(id)) return giveStone(ctx.getSource(), st);
									ctx.getSource().sendFailure(Component.literal("Неизвестный камень: " + id));
									return 0;
								})))
						.then(Commands.literal("state").executes(ctx -> state(ctx.getSource())))
						.then(Commands.literal("reload_config").executes(ctx -> {
							GauntletConfig.reload();
							ctx.getSource().sendSuccess(() -> Component.literal("config/infinity_gauntlet.json перечитан"), false);
							return 1;
						}))
						.then(Commands.literal("fx")
								.then(Commands.argument("effect", StringArgumentType.word())
										.suggests((ctx, b) -> SharedSuggestionProvider.suggest(FX, b))
										.executes(ctx -> fx(ctx.getSource(), StringArgumentType.getString(ctx, "effect")))))
						.then(Commands.literal("cutscene")
								.executes(ctx -> cutscene(ctx.getSource()))
								.then(Commands.literal("stop").executes(ctx -> {
									PlanetDestruction.cancelFor(ctx.getSource().getPlayerOrException());
									return 1;
								})))
						.then(Commands.literal("celestial")
								.then(Commands.literal("sun").executes(ctx -> celestial(ctx.getSource(), "sun")))
								.then(Commands.literal("moon").executes(ctx -> celestial(ctx.getSource(), "moon")))
								.then(Commands.literal("stop").executes(ctx -> {
									com.dantemorrigan.gauntlet.celestial.CelestialApocalypse.stop(ctx.getSource().getServer());
									return 1;
								}))
								.then(Commands.literal("restore").executes(ctx -> {
									com.dantemorrigan.gauntlet.celestial.CelestialApocalypse.restoreBodies(ctx.getSource().getServer());
									ctx.getSource().sendSuccess(() -> Component.literal("Солнце и луна восстановлены"), true);
									return 1;
								})))
						.then(Commands.literal("space")
								.then(Commands.literal("enter").executes(ctx -> spaceEnter(ctx.getSource())))
								.then(Commands.literal("return")
										.executes(ctx -> spaceReturn(ctx.getSource(), ctx.getSource().getPlayerOrException()))
										.then(Commands.argument("player", EntityArgument.player())
												.executes(ctx -> spaceReturn(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))))));
	}

	static final List<String> FX = List.of("power_charge", "power_impact", "power_shockwave", "power_sparks_smoke", "power_strike",
			"ground_fracture", "planet_rift", "detonation_flash", "void_dissolve", "camera_shake", "white_flash", "lift_debris", "meteor");

	/** Gauntlet with only the Power stone, put into the off hand (worn). */
	private static int givePower(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ItemStack gauntlet = new ItemStack(ModItems.GAUNTLET);
		List<ItemStack> stones = new ArrayList<>();
		for (Stone s : Stone.values()) stones.add(s == Stone.POWER ? new ItemStack(ModItems.POWER_STONE) : ItemStack.EMPTY);
		gauntlet.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(stones));
		GauntletState.refresh(gauntlet);
		ItemStack old = player.getOffhandItem();
		player.setItemInHand(InteractionHand.OFF_HAND, gauntlet);
		if (!old.isEmpty() && !player.getInventory().add(old)) player.drop(old, false);
		source.sendSuccess(() -> Component.literal("Перчатка с Камнем Силы надета. ПКМ (держать) — заряд, отпустить — удар. Смена режима — клавиша V."), false);
		return 1;
	}

	/** Insert a stone into the worn gauntlet. */
	private static int giveStone(CommandSourceStack source, Stone stone) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ItemStack gauntlet = GauntletState.activeGauntlet(player);
		if (gauntlet.isEmpty()) {
			source.sendFailure(Component.literal("Сначала /gauntlet give_power"));
			return 0;
		}
		net.minecraft.core.NonNullList<ItemStack> items = net.minecraft.core.NonNullList.withSize(Stone.values().length, ItemStack.EMPTY);
		gauntlet.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
		items.set(stone.ordinal(), new ItemStack(stone.item()));
		gauntlet.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
		GauntletState.refresh(gauntlet);
		source.sendSuccess(() -> Component.literal("Камень вставлен: " + stone.id), false);
		return 1;
	}

	private static int state(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		PlanetDestruction.Phase phase = PlanetDestruction.phaseOf(player);
		ItemStack g = GauntletState.activeGauntlet(player);
		String mode = g.isEmpty() ? "-" : PowerAbility.isUltimate(g) ? "ultimate" : "strike";
		source.sendSuccess(() -> Component.literal("Состояние: " + phase + ", режим: " + mode + ", космос: " + player.hasAttached(SpaceManager.RECORD)), false);
		return 1;
	}

	private static int fx(CommandSourceStack source, String effect) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!FX.contains(effect)) {
			source.sendFailure(Component.literal("Эффекты: " + String.join(", ", FX)));
			return 0;
		}
		Vec3 pos = PowerAbility.pick(player, 6.0).getLocation();
		ServerPlayNetworking.send(player, new ModNetwork.EffectDemo(effect, pos));
		return 1;
	}

	/** Run the full Planet Destruction cutscene at the ground under the player (skips charge/mode checks). */
	private static int cutscene(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		BlockPos ground = player.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition());
		return PlanetDestruction.start(player, Vec3.atBottomCenterOf(ground)) ? 1 : 0;
	}

	/** Break the sun/moon without aiming (debug). */
	private static int celestial(CommandSourceStack source, String body) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!com.dantemorrigan.gauntlet.celestial.CelestialApocalypse.start(player, body)) {
			source.sendFailure(Component.literal("Событие уже идёт: /gauntlet celestial stop"));
			return 0;
		}
		return 1;
	}

	/** Jump straight into Dead Space (as if the planet broke a few seconds ago). */
	private static int spaceEnter(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (SpaceManager.inSpace(player)) {
			source.sendFailure(Component.literal("Уже в космосе"));
			return 0;
		}
		long now = player.level().getServer().overworld().getGameTime();
		SpaceRecord record = new SpaceRecord(player.level().dimension(), player.position(), now - 80, (int) now,
				FragmentCapture.capture(player.level(), player.blockPosition()));
		return SpaceManager.sendToSpace(player, record, 80) ? 1 : 0;
	}

	private static int spaceReturn(CommandSourceStack source, ServerPlayer player) {
		PlanetDestruction.cancelFor(player);
		boolean had = SpaceManager.returnFromSpace(player);
		source.sendSuccess(() -> Component.literal(had ? "Возврат выполнен, состояние сброшено" : "Игрок не был в космосе; перенесён на точку возрождения"), true);
		return 1;
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
