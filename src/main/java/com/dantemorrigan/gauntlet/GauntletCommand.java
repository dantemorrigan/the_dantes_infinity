package com.dantemorrigan.gauntlet;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import com.dantemorrigan.gauntlet.boss.Loki;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.stones.MindQuest;
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
						.then(Commands.literal("stormbreaker_fall").executes(ctx -> {
							ServerPlayer p = ctx.getSource().getPlayerOrException();
							if (com.dantemorrigan.gauntlet.stormbreaker.Stormbreaker.debugSkyfall(p)) return 1;
							ctx.getSource().sendFailure(Component.translatable("command.infinity_gauntlet.stormbreaker_fall_failed"));
							return 0;
						}))
						.then(Commands.literal("full").executes(ctx -> fullGauntlet(ctx.getSource())))
						.then(Commands.literal("give_power").executes(ctx -> givePower(ctx.getSource())))
						.then(Commands.literal("give_time").executes(ctx -> giveStone(ctx.getSource(), Stone.TIME)))
						.then(Commands.literal("give_stone").then(Commands.argument("stone", StringArgumentType.word())
								.suggests((ctx, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(Stone.values()).map(st -> st.id).toList(), b))
								.executes(ctx -> {
									String id = StringArgumentType.getString(ctx, "stone");
									for (Stone st : Stone.values()) if (st.id.equals(id)) return giveStone(ctx.getSource(), st);
									ctx.getSource().sendFailure(Component.translatable("command.infinity_gauntlet.unknown_stone", id));
									return 0;
								})))
						.then(Commands.literal("doll").executes(ctx -> doll(ctx.getSource(), false))
								.then(Commands.literal("uru").executes(ctx -> doll(ctx.getSource(), true))))
						.then(Commands.literal("uru").executes(ctx -> uru(ctx.getSource())))
						.then(Commands.literal("meteor").executes(ctx -> meteor(ctx.getSource())))
						.then(Commands.literal("state").executes(ctx -> state(ctx.getSource())))
						.then(Commands.literal("tp")
								.then(Commands.argument("trial", StringArgumentType.word())
										.suggests((ctx, b) -> SharedSuggestionProvider.suggest(TP_TRIALS, b))
										.executes(ctx -> tpTrial(ctx.getSource(), StringArgumentType.getString(ctx, "trial")))))
						.then(Commands.literal("soul_quest")
								.then(Commands.literal("tp")
										.executes(ctx -> tpTrial(ctx.getSource(), "soul"))
										.then(Commands.literal("summit").executes(ctx -> tpTrial(ctx.getSource(), "soul_summit"))))
								.then(Commands.literal("temple_here").executes(ctx -> {
									var p = ctx.getSource().getPlayerOrException();
									var level = p.level().getServer().overworld();
									com.dantemorrigan.gauntlet.stones.SoulTemple.relocate(level.getServer(), p.blockPosition().offset(0, -1, 0));
									com.dantemorrigan.gauntlet.stones.SoulTemple.forceBuild(level);
									return 1;
								}))
								.then(Commands.literal("map").executes(ctx -> {
									var p = ctx.getSource().getPlayerOrException();
									var map = new net.minecraft.world.item.ItemStack(com.dantemorrigan.gauntlet.ModItems.QUEST_SOUL_TEMPLE_MAP);
									if (!p.getInventory().add(map)) p.drop(map, false);
									return 1;
								}))
								.then(Commands.literal("locate").executes(ctx -> {
									var site = com.dantemorrigan.gauntlet.stones.SoulTemple.site(ctx.getSource().getServer());
									ctx.getSource().sendSuccess(() -> Component.translatable("command.infinity_gauntlet.located", Component.translatable("hud.infinity_gauntlet.quest_compass.soul_temple"), site.toShortString()), false);
									return 1;
								}))
								.then(Commands.literal("ritual").executes(ctx -> com.dantemorrigan.gauntlet.stones.SoulQuest.sacrifice(ctx.getSource().getPlayerOrException(), null) ? 1 : 0)))
						.then(Commands.literal("time_quest")
								.then(Commands.literal("tp")
										.executes(ctx -> tpTrial(ctx.getSource(), "time"))
										.then(Commands.literal("clock").executes(ctx -> tpTrial(ctx.getSource(), "time_clock"))))
								.then(Commands.literal("temple_here").executes(ctx -> {
									var p = ctx.getSource().getPlayerOrException();
									var level = p.level().getServer().overworld();
									com.dantemorrigan.gauntlet.stones.TimeTemple.relocate(level.getServer(), p.blockPosition().offset(0, -1, 0));
									com.dantemorrigan.gauntlet.stones.TimeTemple.forceBuild(level);
									return 1;
								}))
								.then(Commands.literal("map").executes(ctx -> {
									var p = ctx.getSource().getPlayerOrException();
									var map = new net.minecraft.world.item.ItemStack(com.dantemorrigan.gauntlet.ModItems.QUEST_TIME_TEMPLE_MAP);
									if (!p.getInventory().add(map)) p.drop(map, false);
									return 1;
								}))
								.then(Commands.literal("locate").executes(ctx -> {
									var site = com.dantemorrigan.gauntlet.stones.TimeTemple.site(ctx.getSource().getServer());
									ctx.getSource().sendSuccess(() -> Component.translatable("command.infinity_gauntlet.located", Component.translatable("hud.infinity_gauntlet.quest_compass.time_temple"), site.toShortString()), false);
									return 1;
								}))
								.then(Commands.literal("gears").executes(ctx -> {
									var p = ctx.getSource().getPlayerOrException();
									for (var item : new net.minecraft.world.item.Item[]{
											com.dantemorrigan.gauntlet.ModItems.QUEST_TIME_GEAR_OVERWORLD,
											com.dantemorrigan.gauntlet.ModItems.QUEST_TIME_GEAR_NETHER,
											com.dantemorrigan.gauntlet.ModItems.QUEST_TIME_GEAR_END}) {
										var stack = new net.minecraft.world.item.ItemStack(item);
										if (!p.getInventory().add(stack)) p.drop(stack, false);
									}
									return 1;
								})))
						.then(Commands.literal("thanos")
								// start: the full scene with the warning; now: the scene without the warning; spawn: a fighting Thanos here.
								.then(Commands.literal("start").executes(ctx -> thanosCommand(ctx.getSource(), 0)))
								.then(Commands.literal("now").executes(ctx -> thanosCommand(ctx.getSource(), 1)))
								.then(Commands.literal("spawn").executes(ctx -> thanosCommand(ctx.getSource(), 2)))
								.then(Commands.literal("stop").executes(ctx -> { com.dantemorrigan.gauntlet.boss.ThanosEncounter.stop(); return 1; }))
								.then(Commands.literal("reset").executes(ctx -> {
									com.dantemorrigan.gauntlet.boss.ThanosEncounter.reset(ctx.getSource().getServer());
									ctx.getSource().sendSuccess(() -> Component.translatable("command.infinity_gauntlet.thanos_reset"), true);
									return 1;
								})))
						.then(Commands.literal("loki")
								.then(Commands.literal("spawn").executes(ctx -> spawnLoki(ctx.getSource())))
								.then(Commands.literal("reset").executes(ctx -> {
									var source = ctx.getSource();
									MindQuest.resetLoki(source.getServer());
									source.sendSuccess(() -> Component.translatable("command.infinity_gauntlet.loki.reset"), true);
									return 1;
								})))
						.then(Commands.literal("mind_quest")
								.then(Commands.literal("tp").executes(ctx -> tpTrial(ctx.getSource(), "mind")))
								.then(Commands.literal("city_here").executes(ctx -> {
									var p = ctx.getSource().getPlayerOrException();
									var level = p.level().getServer().overworld();
									com.dantemorrigan.gauntlet.stones.MindCity.relocate(level.getServer(), p.blockPosition().offset(0, -1, 0));
									com.dantemorrigan.gauntlet.stones.MindCity.forceBuild(level);
									return 1;
								}))
								.then(Commands.literal("map").executes(ctx -> {
									var p = ctx.getSource().getPlayerOrException();
									var map = new net.minecraft.world.item.ItemStack(com.dantemorrigan.gauntlet.ModItems.QUEST_MIND_CITY_MAP);
									if (!p.getInventory().add(map)) p.drop(map, false);
									return 1;
								}))
								.then(Commands.literal("locate").executes(ctx -> {
									var site = com.dantemorrigan.gauntlet.stones.MindCity.site(ctx.getSource().getServer());
									ctx.getSource().sendSuccess(() -> Component.translatable("command.infinity_gauntlet.located", Component.translatable("hud.infinity_gauntlet.quest_compass.mind_city"), site.toShortString()), false);
									return 1;
								}))
								.then(Commands.literal("summon")
										.executes(ctx -> summonLoki(ctx.getSource().getPlayerOrException())))
								.then(Commands.literal("complete")
										.executes(ctx -> summonLoki(ctx.getSource().getPlayerOrException()))))
						.then(Commands.literal("reality_fog").executes(ctx -> com.dantemorrigan.gauntlet.stones.RealityFog.start(ctx.getSource().getPlayerOrException()) ? 1 : 0))
						.then(Commands.literal("space_quest")
								.then(Commands.literal("tp")
										.executes(ctx -> tpTrial(ctx.getSource(), "space"))
										.then(Commands.literal("rift").executes(ctx -> tpTrial(ctx.getSource(), "space_rift"))))
								.then(Commands.literal("rift").executes(ctx -> {
									var end = ctx.getSource().getServer().getLevel(net.minecraft.world.level.Level.END);
									if (end == null) return 0;
									var pos = com.dantemorrigan.gauntlet.stones.SpaceStoneQuest.buildEndRift(end);
									ctx.getSource().sendSuccess(() -> Component.translatable("command.infinity_gauntlet.located", Component.translatable("command.infinity_gauntlet.place.end_rift"), pos.toShortString()), false);
									return 1;
								}))
								.then(Commands.literal("asteroid").executes(ctx -> {
									com.dantemorrigan.gauntlet.stones.SpaceStoneQuest.goToAsteroid(ctx.getSource().getPlayerOrException());
									return 1;
								})))
						.then(Commands.literal("power_shower").executes(ctx -> {
							ServerPlayer p = ctx.getSource().getPlayerOrException();
							if (!com.dantemorrigan.gauntlet.stones.PowerStoneFall.start(p.level().getServer().overworld(), p)) {
								ctx.getSource().sendFailure(Component.translatable("command.infinity_gauntlet.shower_failed"));
								return 0;
							}
							return 1;
						}))
						.then(Commands.literal("stones").executes(ctx -> {
							var server = ctx.getSource().getServer();
							for (Stone st : Stone.values()) {
								String state = com.dantemorrigan.gauntlet.stones.StoneRegistry.released(server, st) ? "released"
										: com.dantemorrigan.gauntlet.stones.StoneRegistry.found(server, st) ? "found" : "-";
								ctx.getSource().sendSuccess(() -> Component.literal(st.id + ": " + state), false);
							}
							return 1;
						}).then(Commands.literal("reset").executes(ctx -> {
							com.dantemorrigan.gauntlet.stones.StoneRegistry.reset(ctx.getSource().getServer());
							ctx.getSource().sendSuccess(() -> Component.translatable("command.infinity_gauntlet.stones_reset"), true);
							return 1;
						})))
						.then(Commands.literal("reload_config").executes(ctx -> {
							GauntletConfig.reload();
							ctx.getSource().sendSuccess(() -> Component.translatable("command.infinity_gauntlet.reload"), false);
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
									ctx.getSource().sendSuccess(() -> Component.translatable("command.infinity_gauntlet.celestial_restored"), true);
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

	static final List<String> TP_TRIALS = List.of("time", "time_clock", "mind", "soul", "soul_summit", "space", "space_rift");

	private static int spawnLoki(CommandSourceStack source) {
		if (Loki.spawnStandalone(source.getLevel(), source.getPosition(), source.getRotation().y) == null) {
			source.sendFailure(Component.translatable("command.infinity_gauntlet.loki.spawn_failed"));
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("command.infinity_gauntlet.loki.spawn"), true);
		return 1;
	}

	private static int summonLoki(ServerPlayer player) {
		if (com.dantemorrigan.gauntlet.stones.MindQuest.summonLoki(player)) return 1;
		player.sendSystemMessage(Component.translatable("msg.infinity_gauntlet.mind_quest.summon_unavailable"));
		return 0;
	}

	/** Teleports the player directly to the start of a stone trial (building its temple/city/tower if not yet built). */
	private static int tpTrial(CommandSourceStack source, String trial) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		var server = source.getServer();
		var overworld = server.overworld();
		switch (trial.toLowerCase(Locale.ROOT)) {
			case "time" -> {
				BlockPos site = com.dantemorrigan.gauntlet.stones.TimeTemple.site(server);
				if (!com.dantemorrigan.gauntlet.stones.TimeTemple.built(server)) {
					com.dantemorrigan.gauntlet.stones.TimeTemple.forceBuild(overworld);
				}
				Vec3 stand = com.dantemorrigan.gauntlet.stones.TimeTemple.courtyardStand(site);
				player.teleport(new net.minecraft.world.level.portal.TeleportTransition(overworld, stand, Vec3.ZERO, 180f, 0f, net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
				source.sendSuccess(() -> Component.translatable("command.infinity_gauntlet.teleported", Component.translatable("hud.infinity_gauntlet.quest_compass.time_temple"), site.toShortString()), false);
				return 1;
			}
			case "time_clock" -> {
				BlockPos site = com.dantemorrigan.gauntlet.stones.TimeTemple.site(server);
				if (!com.dantemorrigan.gauntlet.stones.TimeTemple.built(server)) {
					com.dantemorrigan.gauntlet.stones.TimeTemple.forceBuild(overworld);
				}
				Vec3 stand = com.dantemorrigan.gauntlet.stones.TimeTemple.sanctumStand(site);
				player.teleport(new net.minecraft.world.level.portal.TeleportTransition(overworld, stand, Vec3.ZERO, 180f, 0f, net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
				source.sendSuccess(() -> Component.translatable("command.infinity_gauntlet.teleported", Component.translatable("command.infinity_gauntlet.place.time_clock"), site.toShortString()), false);
				return 1;
			}
			case "mind" -> {
				BlockPos site = com.dantemorrigan.gauntlet.stones.MindCity.site(server);
				if (!com.dantemorrigan.gauntlet.stones.MindCity.built(server)) {
					com.dantemorrigan.gauntlet.stones.MindCity.forceBuild(overworld);
				}
				Vec3 stand = com.dantemorrigan.gauntlet.stones.MindCity.entranceStand(site);
				player.teleport(new net.minecraft.world.level.portal.TeleportTransition(overworld, stand, Vec3.ZERO, 180f, 0f, net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
				source.sendSuccess(() -> Component.translatable("command.infinity_gauntlet.teleported", Component.translatable("hud.infinity_gauntlet.quest_compass.mind_city"), site.toShortString()), false);
				return 1;
			}
			case "soul" -> {
				BlockPos site = com.dantemorrigan.gauntlet.stones.SoulTemple.site(server);
				if (!com.dantemorrigan.gauntlet.stones.SoulTemple.built(server)) {
					com.dantemorrigan.gauntlet.stones.SoulTemple.forceBuild(overworld);
				}
				Vec3 stand = com.dantemorrigan.gauntlet.stones.SoulTemple.stairwayStart(site);
				player.teleport(new net.minecraft.world.level.portal.TeleportTransition(overworld, stand, Vec3.ZERO, 0f, 0f, net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
				source.sendSuccess(() -> Component.translatable("command.infinity_gauntlet.teleported", Component.translatable("command.infinity_gauntlet.place.soul_stairway"), site.toShortString()), false);
				return 1;
			}
			case "soul_summit" -> {
				BlockPos site = com.dantemorrigan.gauntlet.stones.SoulTemple.site(server);
				if (!com.dantemorrigan.gauntlet.stones.SoulTemple.built(server)) {
					com.dantemorrigan.gauntlet.stones.SoulTemple.forceBuild(overworld);
				}
				Vec3 stand = com.dantemorrigan.gauntlet.stones.SoulTemple.summitStand(site);
				player.teleport(new net.minecraft.world.level.portal.TeleportTransition(overworld, stand, Vec3.ZERO, 90f, 0f, net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
				source.sendSuccess(() -> Component.translatable("command.infinity_gauntlet.teleported", Component.translatable("command.infinity_gauntlet.place.soul_summit"), site.toShortString()), false);
				return 1;
			}
			case "space" -> {
				com.dantemorrigan.gauntlet.stones.SpaceStoneQuest.goToAsteroid(player);
				source.sendSuccess(() -> Component.translatable("command.infinity_gauntlet.teleported_nocoords", Component.translatable("command.infinity_gauntlet.place.ice_tower")), false);
				return 1;
			}
			case "space_rift", "rift" -> {
				var end = server.getLevel(net.minecraft.world.level.Level.END);
				if (end == null) return 0;
				BlockPos rift = com.dantemorrigan.gauntlet.stones.SpaceStoneQuest.buildEndRift(end);
				Vec3 stand = new Vec3(rift.getX() + 0.5, rift.getY() - 1.0, rift.getZ() - 4.5);
				player.teleport(new net.minecraft.world.level.portal.TeleportTransition(end, stand, Vec3.ZERO, 0f, 0f, net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
				source.sendSuccess(() -> Component.translatable("command.infinity_gauntlet.teleported", Component.translatable("command.infinity_gauntlet.place.end_rift"), rift.toShortString()), false);
				return 1;
			}
			default -> {
				source.sendFailure(Component.translatable("command.infinity_gauntlet.unknown_trial", trial, String.join(", ", TP_TRIALS)));
				return 0;
			}
		}
	}

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
		com.dantemorrigan.gauntlet.stones.StoneRegistry.markReleased(source.getServer(), Stone.POWER);
		source.sendSuccess(() -> Component.translatable("command.infinity_gauntlet.give_power"), false);
		return 1;
	}

	/** Insert a stone into the worn gauntlet. */
	/** Debug: a gauntlet with all six stones in the off hand (the old off-hand item goes to the inventory). */
	private static int fullGauntlet(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ItemStack glove = new ItemStack(ModItems.GAUNTLET);
		for (Stone stone : Stone.values()) {
			// Debug copy: deliberately NOT registered, so it cannot close the quests for survival players on the same world.
			GauntletState.putStone(glove, new ItemStack(stone.item()));
		}
		ItemStack displaced = player.getOffhandItem().copy();
		player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, glove);
		if (!displaced.isEmpty() && !player.getInventory().add(displaced)) player.drop(displaced, false);
		com.dantemorrigan.gauntlet.boss.TribunalEncounter.debugMode(player, true); // debug gauntlet: the Tribunal comes by itself in space
		source.sendSuccess(() -> Component.translatable("command.infinity_gauntlet.full"), false);
		return 1;
	}

	private static int giveStone(CommandSourceStack source, Stone stone) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ItemStack gauntlet = GauntletState.activeGauntlet(player);
		if (gauntlet.isEmpty()) {
			source.sendFailure(Component.translatable("command.infinity_gauntlet.need_give_power"));
			return 0;
		}
		net.minecraft.core.NonNullList<ItemStack> items = net.minecraft.core.NonNullList.withSize(Stone.values().length, ItemStack.EMPTY);
		gauntlet.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
		items.set(stone.ordinal(), new ItemStack(stone.item()));
		gauntlet.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
		GauntletState.refresh(gauntlet);
		com.dantemorrigan.gauntlet.stones.StoneRegistry.markReleased(source.getServer(), stone); // the world now has this stone
		source.sendSuccess(() -> Component.translatable("command.infinity_gauntlet.stone_inserted", stone.id), false);
		return 1;
	}

	/** Spawns a Steve doll in a full gauntlet a few blocks in front of the player (target for the Infinity Extractor). */
	/** Puts a full set of Uru on the player (old armor goes to the inventory). */
	private static int uru(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		net.minecraft.world.entity.EquipmentSlot[] slots = {net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
				net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};
		net.minecraft.world.item.Item[] items = {ModItems.URU_HELMET, ModItems.URU_CHESTPLATE, ModItems.URU_LEGGINGS, ModItems.URU_BOOTS};
		for (int i = 0; i < 4; i++) {
			ItemStack old = player.getItemBySlot(slots[i]);
			if (!old.isEmpty() && !player.getInventory().add(old.copy())) player.drop(old.copy(), false);
			player.setItemSlot(slots[i], new ItemStack(items[i]));
		}
		source.sendSuccess(() -> Component.translatable("command.infinity_gauntlet.uru_armor"), false);
		return 1;
	}

	private static int doll(CommandSourceStack source, boolean uru) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		net.minecraft.server.level.ServerLevel level = player.level();
		net.minecraft.world.phys.Vec3 look = player.getLookAngle();
		net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(player.getX() + look.x * 4, player.getY(), player.getZ() + look.z * 4);
		var spawned = ModEntities.GAUNTLET_DOLL.spawn(level, pos, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		if (spawned != null && uru) {
			spawned.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(ModItems.URU_HELMET));
			spawned.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(ModItems.URU_CHESTPLATE));
			spawned.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(ModItems.URU_LEGGINGS));
			spawned.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(ModItems.URU_BOOTS));
			for (var slot : new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
					net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) spawned.setDropChance(slot, 0f);
		}
		if (spawned == null) {
			source.sendFailure(Component.translatable("command.infinity_gauntlet.doll_failed"));
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("command.infinity_gauntlet.doll_summoned"), false);
		return 1;
	}

	/** Starts a Meteor Stream on the ground ~40 blocks ahead without the aiming steps (the server rules still apply). */
	private static int meteor(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		net.minecraft.world.phys.Vec3 look = player.getLookAngle();
		net.minecraft.core.BlockPos column = net.minecraft.core.BlockPos.containing(player.getX() + look.x * 40, player.getY(), player.getZ() + look.z * 40);
		net.minecraft.server.level.ServerLevel level = player.level();
		if (!level.hasChunkAt(column)) {
			source.sendFailure(Component.translatable("command.infinity_gauntlet.chunk_not_loaded"));
			return 0;
		}
		net.minecraft.world.phys.Vec3 center = net.minecraft.world.phys.Vec3.atBottomCenterOf(
				level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column));
		String body = com.dantemorrigan.gauntlet.celestial.CelestialApocalypse.bodyDirection(level, "sun").y > 0.1 ? "sun" : "moon";
		if (!com.dantemorrigan.gauntlet.meteor.MeteorStream.startAt(player, body, center)) {
			source.sendFailure(Component.translatable("command.infinity_gauntlet.stream_failed"));
			return 0;
		}
		return 1;
	}

	private static int state(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		PlanetDestruction.Phase phase = PlanetDestruction.phaseOf(player);
		ItemStack g = GauntletState.activeGauntlet(player);
		String mode = g.isEmpty() ? "-" : PowerAbility.isUltimate(g) ? "ultimate" : "strike";
		source.sendSuccess(() -> Component.translatable("command.infinity_gauntlet.state", String.valueOf(phase), mode, String.valueOf(player.hasAttached(SpaceManager.RECORD))), false);
		return 1;
	}

	private static int fx(CommandSourceStack source, String effect) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!FX.contains(effect)) {
			source.sendFailure(Component.translatable("command.infinity_gauntlet.effects", String.join(", ", FX)));
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
			source.sendFailure(Component.translatable("command.infinity_gauntlet.event_running"));
			return 0;
		}
		return 1;
	}

	/** Jump straight into Dead Space (as if the planet broke a few seconds ago). */
	private static int spaceEnter(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (SpaceManager.inSpace(player)) {
			source.sendFailure(Component.translatable("command.infinity_gauntlet.already_in_space"));
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
		source.sendSuccess(() -> Component.translatable(had ? "command.infinity_gauntlet.returned" : "command.infinity_gauntlet.not_in_space"), true);
		return 1;
	}

	private static int use(CommandSourceStack source, String arg) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ItemStack gauntlet = GauntletState.activeGauntlet(player);
		if (gauntlet.isEmpty()) {
			source.sendFailure(Component.translatable("command.infinity_gauntlet.hold_gauntlet"));
			return 0;
		}
		Set<Stone> stones = EnumSet.noneOf(Stone.class);
		for (String word : arg.toLowerCase(Locale.ROOT).split("[\\s,]+")) {
			if (word.isEmpty() || word.equals("none")) continue;
			if (word.equals("all")) { stones.addAll(EnumSet.allOf(Stone.class)); continue; }
			Stone match = null;
			for (Stone s : Stone.values()) if (s.id.equals(word)) match = s;
			if (match == null) {
				source.sendFailure(Component.translatable("command.infinity_gauntlet.unknown_stone", word));
				return 0;
			}
			stones.add(match);
		}
		GauntletState.setInUse(gauntlet, stones);
		source.sendSuccess(() -> Component.translatable("command.infinity_gauntlet.in_use", String.valueOf(GauntletState.inUse(gauntlet))), false);
		return 1;
	}

	/** /gauntlet thanos start | now | spawn: refused while he is here or after he was beaten (until reset). */
	private static int thanosCommand(net.minecraft.commands.CommandSourceStack source, int mode) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = source.getPlayerOrException();
		if (com.dantemorrigan.gauntlet.boss.ThanosEncounter.beaten(source.getServer())) {
			source.sendFailure(Component.translatable("command.infinity_gauntlet.thanos_beaten"));
			return 0;
		}
		if (com.dantemorrigan.gauntlet.boss.ThanosEncounter.running()) {
			source.sendFailure(Component.translatable("command.infinity_gauntlet.thanos_running"));
			return 0;
		}
		if (com.dantemorrigan.gauntlet.stones.MindArena.excludesThanos(p.level(), p.position())) {
			source.sendFailure(Component.translatable("command.infinity_gauntlet.thanos_city_protected"));
			return 0;
		}
		boolean ok = mode == 2 ? com.dantemorrigan.gauntlet.boss.ThanosEncounter.spawnNow(p) != null
				: com.dantemorrigan.gauntlet.boss.ThanosEncounter.start(p, mode == 1);
		return ok ? 1 : 0;
	}

	private GauntletCommand() {}
}
