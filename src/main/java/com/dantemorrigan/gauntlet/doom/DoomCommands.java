package com.dantemorrigan.gauntlet.doom;

import com.dantemorrigan.gauntlet.ModAdvancements;
import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.power.SpaceStone;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.Arrays;
import java.util.List;

/**
 * Admin tools for The Fury of Doom, under {@code /gauntlet}: {@code latveria ...} for travel and the assault,
 * {@code doom ...} for the boss and the armor, {@code doombot ...} for the robots. {@code /gauntlet latveria help}
 * prints the whole list.
 */
public final class DoomCommands {
	private static final AABB WORLD = new AABB(-400, 0, -500, 400, 320, 400);
	private static final List<String> HELP = List.of(
			"§a/gauntlet latveria help§r — this list",
			"§a/gauntlet latveria prepare§r — the honest way in: teaser seen, Loki beaten, a Mind Stone in hand",
			"§a/gauntlet latveria enter | leave | unlock§r — travel / give access without travelling",
			"§a/gauntlet latveria gate | lab | throne§r — quick jumps to the gate, the service door, the throne room",
			"§a/gauntlet latveria tp <place>§r — arrival, square, road, gate, yard, barracks, lab, hall, throne, bedrooms, library, roof...",
			"§a/gauntlet latveria skip <road|gate|yard|lab|assault|boss|all>§r — clear a part of the assault",
			"§a/gauntlet latveria squads§r — bring up every squad now · §aclear <0-3|all>§r — destroy squads",
			"§a/gauntlet latveria generator <on|off>§r · §agates§r — open the portcullis and the throne room",
			"§a/gauntlet latveria alarm§r — the searchlights catch you now (horn, red beams, Interceptors)",
			"§a/gauntlet latveria conquer [player]§r — win at once: spoils, castle, Doombots",
			"§a/gauntlet latveria teaser <replay|skip>§r — forget or mark the Space Stone preview",
			"§a/gauntlet latveria logo§r — the title card now · §alogo reset§r — again on the next arrival",
			"§a/gauntlet latveria music <echoes|sanctum|boss|stop>§r",
			"§a/gauntlet latveria give§r — every item · §aforget§r — wipe your access, title card, spoils, ownership",
			"§a/gauntlet latveria status | reset§r — the state of the assault / play it again",
			"§a/gauntlet doom spawn | stop | kill | heal | passive§r",
			"§a/gauntlet doom stage <1-3>§r · §aaction <clip>§r — e.g. latverian_edict, seven_suns, jet_slam",
			"§a/gauntlet doom armor [player]§r · §acooldowns§r — every armor power ready",
			"§a/gauntlet doom ascend§r — God Emperor Doom: the ascension scene now",
			"§a/gauntlet doombot spawn <variant> [allied] [x y z]§r · §akill§r · §aallies§r — every Doombot near obeys you");

	public static LiteralArgumentBuilder<CommandSourceStack> latveria() {
		return Commands.literal("latveria")
				.then(Commands.literal("help").executes(ctx -> { HELP.forEach(l -> ctx.getSource().sendSuccess(() -> Component.literal(l), false)); return 1; }))
				.then(Commands.literal("prepare").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					SpaceStone.setLatveriaSeen(p, true);
					ModAdvancements.award(p, "defeat_loki");
					if (!p.getInventory().contains(new ItemStack(ModItems.MIND_STONE))) give(p, new ItemStack(ModItems.MIND_STONE));
					return ok(ctx, "the seal will be read within a second; then use the Latveria option of the Space Stone portal");
				}))
				.then(Commands.literal("enter").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					Latveria.grantAccess(p);
					return Latveria.enter(p, false) ? ok(ctx, "entered") : fail(ctx, "the Latveria dimension is missing");
				}))
				.then(Commands.literal("leave").executes(ctx -> { Latveria.leave(ctx.getSource().getPlayerOrException()); return ok(ctx, "left"); }))
				.then(Commands.literal("unlock").executes(ctx -> { Latveria.grantAccess(ctx.getSource().getPlayerOrException()); return ok(ctx, "access granted"); }))
				.then(Commands.literal("gate").executes(ctx -> shortcut(ctx, "gate")))
				.then(Commands.literal("lab").executes(ctx -> shortcut(ctx, "service_door")))
				.then(Commands.literal("throne").executes(ctx -> shortcut(ctx, "hall")))
				.then(Commands.literal("tp").then(Commands.argument("place", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(Latveria.PLACES.keySet(), b))
						.executes(ctx -> {
							ServerPlayer p = ctx.getSource().getPlayerOrException();
							Latveria.grantAccess(p);
							String place = StringArgumentType.getString(ctx, "place");
							return Latveria.tp(p, place) ? ok(ctx, "at " + place) : fail(ctx, "unknown place: " + place);
						})))
				.then(Commands.literal("skip").then(Commands.argument("part", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("road", "gate", "yard", "lab", "assault", "boss", "all"), b))
						.executes(ctx -> skip(ctx, StringArgumentType.getString(ctx, "part")))))
				.then(Commands.literal("squads").executes(ctx -> ok(ctx, "squads brought up: " + Latveria.spawnSquads(level(ctx)))))
				.then(Commands.literal("clear")
						.then(Commands.literal("all").executes(ctx -> {
							int n = 0;
							for (int s = 0; s < 4; s++) n += Latveria.clearSquad(ctx.getSource().getServer(), s);
							return ok(ctx, "every squad cleared, Doombots destroyed: " + n);
						}))
						.then(Commands.argument("squad", IntegerArgumentType.integer(0, 3)).executes(ctx ->
								ok(ctx, "squad cleared, Doombots destroyed: " + Latveria.clearSquad(ctx.getSource().getServer(), IntegerArgumentType.getInteger(ctx, "squad"))))))
				.then(Commands.literal("generator")
						.then(Commands.literal("off").executes(ctx -> { Latveria.setGenerator(level(ctx), true); return ok(ctx, "generator off"); }))
						.then(Commands.literal("on").executes(ctx -> { Latveria.setGenerator(level(ctx), false); return ok(ctx, "generator on"); })))
				.then(Commands.literal("alarm").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					if (p.level() != level(ctx)) return fail(ctx, "go to Latveria first");
					Searchlights.alarm(level(ctx), p);
					return ok(ctx, "alarm raised" + (Searchlights.active(ctx.getSource().getServer()) ? "" : " (the lamps are out: gate open or generator off)"));
				}))
				.then(Commands.literal("gates").executes(ctx -> { Latveria.openGates(level(ctx)); return ok(ctx, "portcullis and throne room open"); }))
				.then(Commands.literal("conquer")
						.executes(ctx -> conquer(ctx, ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
								.executes(ctx -> conquer(ctx, net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player")))))
				.then(Commands.literal("teaser")
						.then(Commands.literal("replay").executes(ctx -> { SpaceStone.setLatveriaSeen(ctx.getSource().getPlayerOrException(), false); return ok(ctx, "the preview can be seen again"); }))
						.then(Commands.literal("skip").executes(ctx -> { SpaceStone.setLatveriaSeen(ctx.getSource().getPlayerOrException(), true); return ok(ctx, "the preview is marked as seen"); })))
				.then(Commands.literal("logo")
						.executes(ctx -> { Latveria.showLogo(ctx.getSource().getPlayerOrException()); return ok(ctx, "title card"); })
						.then(Commands.literal("reset").executes(ctx -> { Latveria.forgetLogo(ctx.getSource().getPlayerOrException()); return ok(ctx, "the title card will play on the next arrival"); })))
				.then(Commands.literal("music").then(Commands.argument("theme", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("echoes", "sanctum", "boss", "stop"), b))
						.executes(ctx -> {
							int id = List.of("stop", "echoes", "sanctum", "boss").indexOf(StringArgumentType.getString(ctx, "theme"));
							if (id < 0) return fail(ctx, "themes: echoes, sanctum, boss, stop");
							ServerPlayer p = ctx.getSource().getPlayerOrException();
							DoomFx.send(p, DoomFx.MUSIC, id, p.position(), 0, 0);
							return ok(ctx, "music: " + StringArgumentType.getString(ctx, "theme"));
						})))
				.then(Commands.literal("give").executes(ctx -> { Latveria.giveAll(ctx.getSource().getPlayerOrException()); return ok(ctx, "every item of the add-on"); }))
				.then(Commands.literal("forget").executes(ctx -> { Latveria.forget(ctx.getSource().getPlayerOrException()); return ok(ctx, "Latveria forgot you"); }))
				.then(Commands.literal("status").executes(ctx -> ok(ctx, Latveria.status(ctx.getSource().getServer()))))
				.then(Commands.literal("reset").executes(ctx -> { Latveria.reset(ctx.getSource().getServer()); return ok(ctx, "the assault is reset"); }));
	}

	public static LiteralArgumentBuilder<CommandSourceStack> doom() {
		return Commands.literal("doom")
				.then(Commands.literal("spawn").executes(ctx -> {
					ServerLevel level = level(ctx);
					level.getChunk(net.minecraft.core.BlockPos.containing(LatveriaPlan.THRONE));
					DoctorDoom d = Latveria.summon(level);
					return d != null ? ok(ctx, "Doctor Doom rises") : fail(ctx, "could not summon him");
				}))
				.then(Commands.literal("stop").executes(ctx -> { Latveria.hold(level(ctx)); return ok(ctx, "the fight is called off; he stays away until the hall is empty"); }))
				.then(Commands.literal("kill").executes(ctx -> boss(ctx, d -> d.hurtServer((ServerLevel) d.level(), d.level().damageSources().genericKill(), Float.MAX_VALUE), "defeated")))
				.then(Commands.literal("heal").executes(ctx -> boss(ctx, DoctorDoom::heal, "healed")))
				.then(Commands.literal("passive").executes(ctx -> {
					DoctorDoom d = Latveria.boss();
					if (d == null) return fail(ctx, "Doctor Doom is not in his throne room");
					return ok(ctx, d.togglePassive() ? "passive: he only stands and takes blows" : "he fights again");
				}))
				.then(Commands.literal("phase2").executes(ctx -> boss(ctx, d -> d.stage(2), "stage 2")))
				.then(Commands.literal("phase3").executes(ctx -> boss(ctx, d -> d.stage(3), "stage 3")))
				.then(Commands.literal("stage").then(Commands.argument("n", IntegerArgumentType.integer(1, 3)).executes(ctx -> {
					int n = IntegerArgumentType.getInteger(ctx, "n");
					return boss(ctx, d -> d.stage(n), "stage " + n);
				})))
				.then(Commands.literal("action").then(Commands.argument("clip", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(DoctorDoom.CLIPS).filter(c -> !c.equals("idle") && !c.equals("intro") && !c.equals("defeat")), b))
						.executes(ctx -> {
							DoctorDoom d = Latveria.boss();
							String clip = StringArgumentType.getString(ctx, "clip");
							if (d == null) return fail(ctx, "Doctor Doom is not in his throne room");
							return d.force(clip) ? ok(ctx, "action " + clip) : fail(ctx, "unknown action: " + clip);
						})))
				.then(Commands.literal("ascend").executes(ctx -> {
					DoctorDoom d = Latveria.boss();
					if (d == null) return fail(ctx, "Doctor Doom is not in his throne room (try /gauntlet doom spawn)");
					d.ascend(level(ctx), ctx.getSource().getPlayer());
					return ok(ctx, "God Emperor Doom rises");
				}))
				.then(Commands.literal("steal").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					DoctorDoom d = Latveria.boss();
					if (d == null) {
						Latveria.tp(p, "hall");
						d = Latveria.summon(level(ctx));
					}
					if (d == null) return fail(ctx, "Doctor Doom could not be summoned");
					d.stealGauntletAndFlee(level(ctx), p);
					return ok(ctx, "Doom steals the Infinity Gauntlet (<=5 stones) and flees to the Overworld");
				}))
				.then(Commands.literal("arena").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					DoomFinalEncounter.debugTpArena(p);
					return ok(ctx, "teleported to Doom's sandy-fiery battlefield in Overworld");
				}))
				.then(Commands.literal("break_dome").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					return DoomFinalEncounter.debugForceBreakDome(p)
							? ok(ctx, "shattered the energy dome")
							: fail(ctx, "dome duel is not currently active");
				}))
				.then(Commands.literal("reset_final").executes(ctx -> {
					DoomFinalEncounter.debugReset(ctx.getSource().getServer());
					return ok(ctx, "reset Doom vs Thanos final encounter");
				}))
				.then(Commands.literal("armor")
						.executes(ctx -> armor(ctx, ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
								.executes(ctx -> armor(ctx, net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player")))))
				.then(Commands.literal("cooldowns").executes(ctx -> { DoomArmor.resetCooldowns(ctx.getSource().getPlayerOrException()); return ok(ctx, "every power is ready"); }));
	}

	public static LiteralArgumentBuilder<CommandSourceStack> doombot() {
		return Commands.literal("doombot")
				.then(Commands.literal("spawn").then(Commands.argument("variant", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(Doombot.NAMES, b))
						.executes(ctx -> spawnBot(ctx, false, null))
						.then(Commands.argument("allied", BoolArgumentType.bool()).executes(ctx -> spawnBot(ctx, BoolArgumentType.getBool(ctx, "allied"), null))
								.then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.Vec3Argument.vec3())
										.executes(ctx -> spawnBot(ctx, BoolArgumentType.getBool(ctx, "allied"),
												net.minecraft.commands.arguments.coordinates.Vec3Argument.getVec3(ctx, "pos")))))))
				.then(Commands.literal("kill").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					int n = 0;
					for (Doombot b : p.level().getEntitiesOfClass(Doombot.class, p.getBoundingBox().inflate(96), b -> b.isAlive() && !b.allied())) {
						b.hurtServer(p.level(), p.level().damageSources().genericKill(), Float.MAX_VALUE);
						n++;
					}
					return ok(ctx, "hostile Doombots destroyed: " + n);
				}))
				.then(Commands.literal("allies").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					List<Doombot> bots = p.level().getEntitiesOfClass(Doombot.class, p.getBoundingBox().inflate(48), Doombot::isAlive);
					bots.forEach(b -> b.obey(p));
					return ok(ctx, "Doombots now obeying you: " + bots.size());
				}));
	}

	// ------------------------------------------------------------------ helpers

	/** gate, lab and throne: quick jumps to the gate, the service door and the throne room's doorway. */
	private static int shortcut(CommandContext<CommandSourceStack> ctx, String place) throws CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		Latveria.grantAccess(p);
		return Latveria.tp(p, place) ? ok(ctx, "at " + place) : fail(ctx, "the Latveria dimension is missing");
	}

	private static int skip(CommandContext<CommandSourceStack> ctx, String part) throws CommandSyntaxException {
		ServerLevel level = level(ctx);
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		var server = ctx.getSource().getServer();
		Latveria.grantAccess(p);
		switch (part) {
			case "road" -> Latveria.clearSquad(server, Latveria.SQUAD_ROAD);
			case "gate" -> { Latveria.clearSquad(server, Latveria.SQUAD_GATE); Latveria.openGates(level); }
			case "yard" -> { Latveria.clearSquad(server, Latveria.SQUAD_YARD); Latveria.openGates(level); }
			case "lab" -> { Latveria.clearSquad(server, Latveria.SQUAD_LAB); Latveria.setGenerator(level, true); }
			case "assault" -> { for (int s = 0; s < 4; s++) Latveria.clearSquad(server, s); Latveria.openGates(level); }
			case "boss" -> { for (int s = 0; s < 4; s++) Latveria.clearSquad(server, s); Latveria.openGates(level); Latveria.tp(p, "hall"); }
			case "all" -> { for (int s = 0; s < 4; s++) Latveria.clearSquad(server, s); Latveria.openGates(level); Latveria.setGenerator(level, true); Latveria.tp(p, "hall"); }
			default -> { return fail(ctx, "parts: road, gate, yard, lab, assault, boss, all"); }
		}
		return ok(ctx, "skipped: " + part);
	}

	private static int conquer(CommandContext<CommandSourceStack> ctx, ServerPlayer p) {
		ServerLevel level = level(ctx);
		if (Latveria.boss() != null) Latveria.callOff(level);
		Latveria.conquer(level, List.of(p.getUUID()));
		return ok(ctx, "Castle Doom belongs to " + p.getName().getString());
	}

	private static int armor(CommandContext<CommandSourceStack> ctx, ServerPlayer p) {
		p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.DOOM_HELMET));
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.DOOM_CHESTPLATE));
		p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.DOOM_LEGGINGS));
		p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.DOOM_BOOTS));
		return ok(ctx, "Doom's armor on " + p.getName().getString());
	}

	private static int spawnBot(CommandContext<CommandSourceStack> ctx, boolean allied, net.minecraft.world.phys.Vec3 where) throws CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		int v = Arrays.asList(Doombot.NAMES).indexOf(StringArgumentType.getString(ctx, "variant"));
		if (v < 0) return fail(ctx, "variants: " + String.join(", ", Doombot.NAMES));
		Doombot bot = ModEntities.DOOMBOT.create(p.level(), EntitySpawnReason.COMMAND);
		if (bot == null) return 0;
		bot.setup(v, -1);
		// In front of the player, or at the given position.
		var at = where == null ? p.position().add(p.getLookAngle().multiply(3, 0, 3)) : where;
		bot.snapTo(at.x, at.y + (v == Doombot.INTERCEPTOR ? 3 : 0), at.z, p.getYRot() + 180, 0);
		p.level().addFreshEntity(bot);
		if (allied) bot.obey(p);
		return ok(ctx, (allied ? "allied " : "hostile ") + Doombot.NAMES[v]);
	}

	private static int boss(CommandContext<CommandSourceStack> ctx, java.util.function.Consumer<DoctorDoom> what, String done) {
		DoctorDoom d = Latveria.boss();
		if (d == null) return fail(ctx, "Doctor Doom is not in his throne room (try /gauntlet doom spawn)");
		what.accept(d);
		return ok(ctx, done);
	}

	private static ServerLevel level(CommandContext<CommandSourceStack> ctx) {
		return ctx.getSource().getServer().getLevel(Latveria.LEVEL);
	}

	private static void give(ServerPlayer p, ItemStack stack) {
		if (!p.getInventory().add(stack)) p.drop(stack, false);
	}

	private static int ok(CommandContext<CommandSourceStack> ctx, String text) {
		ctx.getSource().sendSuccess(() -> Component.literal("§2[Latveria]§r " + text), true);
		return 1;
	}

	private static int fail(CommandContext<CommandSourceStack> ctx, String text) {
		ctx.getSource().sendFailure(Component.literal(text));
		return 0;
	}

	private DoomCommands() {}
}
