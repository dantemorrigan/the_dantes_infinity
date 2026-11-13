package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.CoopRules;
import com.dantemorrigan.gauntlet.ability.Ability;
import com.dantemorrigan.gauntlet.ability.AbilityController;
import com.dantemorrigan.gauntlet.doom.DoctorDoom;
import com.dantemorrigan.gauntlet.doom.Doombot;
import com.dantemorrigan.gauntlet.doom.Latveria;
import com.dantemorrigan.gauntlet.doom.LatveriaPlan;
import com.dantemorrigan.gauntlet.power.MindStone;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The gauntlet against The Fury of Doom: Latveria's masonry survives every way of breaking it, each stone's powers and a
 * few combinations hurt Doom and his robots without deleting them, holding them or ending the fight out of turn, and
 * leaving mid-fight through the Space Stone's portal resets the duel cleanly.
 */
public class DoomGauntletCompatTest implements FabricClientGameTest {
	private static final List<String> REPORT = new ArrayList<>();

	private static ServerPlayer player(MinecraftServer s) { return s.getPlayerList().getPlayers().getFirst(); }

	private static ServerLevel latveria(MinecraftServer s) { return s.getLevel(Latveria.LEVEL); }

	private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

	/** Every block of the closed throne door and a strip of the hall floor and wall: what must never change. */
	private static List<BlockPos> masonry() {
		List<BlockPos> out = new ArrayList<>();
		int[] b = LatveriaPlan.THRONE_DOOR;
		for (BlockPos p : BlockPos.betweenClosed(b[0], b[1], b[2], b[3], b[4], b[5])) out.add(p.immutable());
		for (int x = -6; x <= 6; x++) out.add(new BlockPos(x, LatveriaPlan.FLOOR - 1, -270));
		return out;
	}

	private static String snapshot(ServerLevel level, List<BlockPos> blocks) {
		StringBuilder s = new StringBuilder();
		for (BlockPos p : blocks) s.append(level.getBlockState(p).getBlock()).append(';');
		return s.toString();
	}

	private static void faceDoom(TestServerContext server, ClientGameTestContext ctx, double distance) {
		server.runOnServer(s -> {
			DoctorDoom d = Latveria.boss();
			ServerPlayer p = player(s);
			p.teleportTo(latveria(s), d.getX(), LatveriaPlan.FLOOR, Math.min(d.getZ() + distance, -255.5), java.util.Set.of(), 180, 0, true);
			p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, d.position().add(0, 1.6, 0));
		});
		ctx.waitTicks(4);
	}

	/** Selects and casts one ability; held ones are let go after {@code hold} ticks. */
	private static String cast(TestServerContext server, ClientGameTestContext ctx, Ability a, int hold) {
		String why = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			AbilityController.select(p, a.id, -1);
			String r = AbilityController.reason(p, a);
			AbilityController.action(p, 0);
			return r;
		});
		ctx.waitTicks(Math.max(2, hold));
		server.runOnServer(s -> AbilityController.action(player(s), 1));
		return why;
	}

	/** The duel's invariants after any power: Doom is the same living boss, nobody holds him, the castle stands. */
	private static void invariants(TestServerContext server, String after, String masonry) {
		String broken = server.computeOnServer(s -> {
			DoctorDoom d = Latveria.boss();
			if (d == null) return "Doom vanished from Latveria";
			if (d.isRemoved()) return "Doom was removed (" + d.getRemovalReason() + ")";
			if (!d.isAlive()) return "Doom died to a single power";
			if (MindStone.holdsEntity(d)) return "the Mind Stone holds Doom";
			if (d.level() != latveria(s)) return "Doom left Latveria";
			if (!snapshot(latveria(s), masonry()).equals(masonry)) return "the castle changed";
			return "";
		});
		check(broken.isEmpty(), "After " + after + ": " + broken);
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		LegacyBindings.disable(ctx);
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			TestServerContext server = sp.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("time set 6000");
			server.runCommand("gamemode creative @p");
			server.runCommand("execute as @p run gauntlet latveria enter");
			ctx.waitTicks(40);
			server.runCommand("execute as @p run gauntlet latveria tp hall");
			ctx.waitTicks(30);

			// ---- 1. Nothing breaks Latveria: the plain block calls, a break with drops, a blast, the gauntlet's own check.
			String masonry = server.computeOnServer(s -> snapshot(latveria(s), masonry()));
			check(masonry.contains("green_stained_glass"), "The throne door is not closed in a fresh world: " + masonry);
			String guard = server.computeOnServer(s -> {
				ServerLevel l = latveria(s);
				BlockPos glass = new BlockPos(LatveriaPlan.THRONE_DOOR[0], LatveriaPlan.THRONE_DOOR[1], LatveriaPlan.THRONE_DOOR[2]);
				BlockPos wall = new BlockPos(0, LatveriaPlan.FLOOR - 1, -270);
				if (l.setBlock(glass, Blocks.AIR.defaultBlockState(), 3)) return "setBlock removed the throne glass";
				if (l.destroyBlock(wall, true)) return "destroyBlock broke the hall floor";
				int drops = l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(wall).inflate(4)).size();
				if (drops > 0) return "a refused break still dropped " + drops + " items";
				l.explode(null, glass.getX() + 0.5, glass.getY() + 1, glass.getZ() + 0.5, 6f, Level.ExplosionInteraction.TNT);
				if (CoopRules.canEdit(player(s), l, wall)) return "CoopRules lets the gauntlet edit the castle";
				BlockPos open = new BlockPos(0, LatveriaPlan.FLOOR + 2, -262);
				if (!l.getBlockState(open).isAir() || !l.setBlock(open, Blocks.TORCH.defaultBlockState(), 3) || !l.setBlock(open, Blocks.AIR.defaultBlockState(), 3))
					return "placing into open air (and taking it back) should work";
				return "";
			});
			check(guard.isEmpty(), guard);
			ctx.waitTicks(4);
			check(server.computeOnServer(s -> snapshot(latveria(s), masonry())).equals(masonry), "A TNT-class blast changed the castle");
			check(server.computeOnServer(s -> latveria(s).getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
					new AABB(-12, LatveriaPlan.FLOOR - 4, -262, 12, LatveriaPlan.FLOOR + 12, -244)).isEmpty()), "The blast dropped castle blocks");

			// ---- 1b. A survival visitor: their own block comes back out, the castle's does not.
			String visitor = server.computeOnServer(s -> {
				ServerLevel l = latveria(s);
				ServerPlayer p = player(s);
				p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
				BlockPos mine = new BlockPos(3, LatveriaPlan.FLOOR, -262);
				BlockPos wall = new BlockPos(0, LatveriaPlan.FLOOR - 1, -262);
				p.teleportTo(l, 3.5, LatveriaPlan.FLOOR, -259.5, java.util.Set.of(), 180, 30, true);
				l.setBlock(mine, Blocks.OAK_PLANKS.defaultBlockState(), 3);
				boolean ownBroken = p.gameMode.destroyBlock(mine) && l.getBlockState(mine).isAir();
				boolean wallKept = !p.gameMode.destroyBlock(wall) && !l.getBlockState(wall).isAir();
				p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
				return ownBroken + "/" + wallKept;
			});
			check(visitor.equals("true/true"), "Survival visitor: own block broken / castle kept = " + visitor);

			// ---- 2. Doom in his hall with the whole gauntlet against him.
			server.runCommand("execute as @p run gauntlet latveria unlock throne");
			server.runCommand("execute as @p run gauntlet doom spawn");
			ctx.waitTicks(140);
			server.runCommand("execute as @p run gauntlet doom passive");
			server.runCommand("execute as @p run gauntlet full");
			ctx.waitTicks(10);
			masonry = server.computeOnServer(s -> snapshot(latveria(s), masonry()));
			check(server.computeOnServer(s -> Latveria.boss() != null && Latveria.boss().isAlive()), "Doom was not summoned");

			Object[][] powers = {
					{Ability.STRIKE, 30}, {Ability.PLANET, 40}, {Ability.REALITY_STRIKE, 40}, {Ability.INVERSION, 60},
					{Ability.TIME_FLOW, 70}, {Ability.REWIND, 10}, {Ability.GRAB, 20}, {Ability.TEAR, 10}, {Ability.DROP, 10},
					{Ability.SOUL, 80}, {Ability.SOUL_MASS, 40}, {Ability.BLINK, 6}, {Ability.BEAM, 80}, {Ability.METEOR_STREAM, 40},
			};
			for (Object[] pw : powers) {
				Ability a = (Ability) pw[0];
				server.runCommand("execute as @p run gauntlet doom heal");
				ctx.waitTicks(3);
				faceDoom(server, ctx, 7);
				float before = server.computeOnServer(s -> Latveria.boss().getHealth());
				String why = cast(server, ctx, a, (int) pw[1]);
				ctx.waitTicks(30);
				invariants(server, a.id, masonry);
				float after = server.computeOnServer(s -> Latveria.boss().getHealth());
				REPORT.add(String.format("%s: %.0f -> %.0f%s", a.id, before, after, why.isEmpty() ? "" : " (" + why + ")"));
				server.runOnServer(s -> AbilityController.cancel(player(s)));
				server.runOnServer(s -> MindStone.drop(player(s)));
			}

			// ---- 3. Combinations, back to back without healing: what one power leaves behind must not break the next.
			Ability[][] combos = {
					{Ability.GRAB, Ability.REALITY_STRIKE}, {Ability.GRAB, Ability.BEAM}, {Ability.TIME_FLOW, Ability.BEAM},
					{Ability.SOUL, Ability.REALITY_STRIKE}, {Ability.INVERSION, Ability.STRIKE}, {Ability.REWIND, Ability.TEAR},
			};
			for (Ability[] combo : combos) {
				server.runCommand("execute as @p run gauntlet doom heal");
				server.runCommand("execute as @p run gauntlet doom cooldowns");
				faceDoom(server, ctx, 7);
				StringBuilder name = new StringBuilder();
				for (Ability a : combo) {
					cast(server, ctx, a, a == Ability.BEAM || a == Ability.TIME_FLOW ? 50 : 14);
					name.append(a.id).append('+');
				}
				ctx.waitTicks(30);
				invariants(server, name.toString(), masonry);
				server.runOnServer(s -> { AbilityController.cancel(player(s)); MindStone.drop(player(s)); });
			}

			// ---- 4. Doombots: a fallen robot always counts for its squad, whatever power took it.
			String bots = server.computeOnServer(s -> {
				ServerLevel l = latveria(s);
				StringBuilder out = new StringBuilder();
				for (int v = 0; v < 6; v++) {
					Doombot b = com.dantemorrigan.gauntlet.ModEntities.DOOMBOT.create(l, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
					b.setup(v, -1);
					b.snapTo(4.5 + v * 2, LatveriaPlan.FLOOR, -262.5, 0, 0);
					l.addFreshEntity(b);
				}
				return out.toString();
			});
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.teleportTo(latveria(s), 9.5, LatveriaPlan.FLOOR, -256.5, java.util.Set.of(), 180, 0, true);
				p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, new Vec3(9.5, LatveriaPlan.FLOOR + 1, -262.5));
			});
			ctx.waitTicks(4);
			server.runCommand("execute as @p run gauntlet doom cooldowns");
			cast(server, ctx, Ability.REALITY_STRIKE, 30);
			ctx.waitTicks(40);
			String discarded = server.computeOnServer(s -> {
				for (Doombot b : latveria(s).getEntitiesOfClass(Doombot.class, new AABB(-20, LatveriaPlan.FLOOR - 4, -280, 30, LatveriaPlan.FLOOR + 20, -240)))
					if (b.isRemoved() && b.getRemovalReason() == Entity.RemovalReason.DISCARDED) return "a Doombot was discarded by Reality";
				return "";
			});
			check(discarded.isEmpty(), discarded + bots);
			REPORT.add("doombots after reality strike: " + server.computeOnServer(s -> {
				StringBuilder o = new StringBuilder();
				for (Doombot b : latveria(s).getEntitiesOfClass(Doombot.class, new AABB(-20, LatveriaPlan.FLOOR - 4, -280, 30, LatveriaPlan.FLOOR + 20, -240)))
					o.append(Doombot.NAMES[b.variant()]).append('=').append((int) b.getHealth()).append(' ');
				return o.toString();
			}));

			// ---- 4b. The Infinity Siphon: held, it silences the gauntlet; a blink or hard blows break it and open him up.
			// Five stones short of the whole: an ordinary siphon (the whole gauntlet makes him ascend, see the end).
			server.runCommand("execute as @p run gauntlet give_power");
			server.runCommand("gamemode survival @p");
			server.runCommand("effect give @p minecraft:resistance 120 4 true");
			server.runCommand("effect give @p minecraft:regeneration 120 4 true");
			server.runCommand("execute as @p run gauntlet doom heal");
			faceDoom(server, ctx, 8);
			server.runCommand("execute as @p run gauntlet doom action infinity_siphon");
			ctx.waitTicks(40);
			ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
			ctx.waitTicks(4);
			ctx.takeScreenshot("doom_siphon_tether");
			ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
			ctx.waitTicks(70);
			check(server.computeOnServer(s -> com.dantemorrigan.gauntlet.doom.GauntletSilence.silenced(player(s))), "A held siphon did not silence the gauntlet");
			check(server.computeOnServer(s -> AbilityController.reason(player(s), Ability.STRIKE)).equals("silenced"), "A silenced gauntlet still casts");
			server.runOnServer(s -> com.dantemorrigan.gauntlet.doom.GauntletSilence.lift(player(s)));

			server.runCommand("execute as @p run gauntlet doom action infinity_siphon");
			ctx.waitTicks(30);
			server.runOnServer(s -> com.dantemorrigan.gauntlet.doom.DoomCombat.dodge(player(s)));
			ctx.waitTicks(4);
			check(server.computeOnServer(s -> Latveria.boss().action() == DoctorDoom.A_STAGGER && Latveria.boss().flag(DoctorDoom.FLAG_EXPOSED)),
					"A blink did not break the siphon and leave Doom open");
			check(!server.computeOnServer(s -> com.dantemorrigan.gauntlet.doom.GauntletSilence.silenced(player(s))), "A broken siphon still silenced the gauntlet");

			ctx.waitTicks(40);
			server.runCommand("execute as @p run gauntlet doom action infinity_siphon");
			ctx.waitTicks(24);
			server.runOnServer(s -> {
				DoctorDoom d = Latveria.boss();
				for (int i = 0; i < 4; i++) { d.invulnerableTime = 0; d.hurtServer(latveria(s), latveria(s).damageSources().playerAttack(player(s)), 20); }
			});
			ctx.waitTicks(3);
			check(server.computeOnServer(s -> Latveria.boss().action() == DoctorDoom.A_STAGGER), "Hard blows did not break the siphon");
			server.runCommand("gamemode creative @p");

			// ---- 5. Leaving mid-fight: the duel is called off, Doom's guards go, and the fight can be had again.
			faceDoom(server, ctx, 7);
			server.runCommand("execute as @p run gauntlet latveria leave");
			ctx.waitTicks(40);
			String left = server.computeOnServer(s -> player(s).level().dimension().identifier() + " boss=" + (Latveria.boss() == null ? "none" : "still there"));
			REPORT.add("left mid-fight: " + left);
			check(left.endsWith("boss=none"), "Doom's fight was not called off when the challenger left: " + left);
			server.runCommand("execute as @p run gauntlet latveria tp hall");
			ctx.waitTicks(40);
			server.runCommand("execute as @p run gauntlet latveria tp throne");
			ctx.waitTicks(60);
			check(server.computeOnServer(s -> Latveria.boss() != null && Latveria.boss().isAlive()), "Doom did not rise again for a new challenger");
			// ---- 6. A siphon that drains the whole gauntlet: God Emperor Doom.
			server.runCommand("execute as @p run gauntlet full");
			server.runCommand("gamemode survival @p");
			server.runCommand("effect give @p minecraft:resistance 60 4 true");
			faceDoom(server, ctx, 8);
			server.runCommand("execute as @p run gauntlet doom action infinity_siphon");
			ctx.waitTicks(DoctorDoom.SIPHON_DONE + 6);
			check(server.computeOnServer(s -> Latveria.boss() != null && Latveria.boss().ascending()), "Draining all six stones did not make Doom ascend");
			server.runCommand("execute as @p run gauntlet doom stop");
			server.runCommand("gamemode creative @p");
			ctx.waitTicks(10);

			for (String line : REPORT) System.out.println("DOOM-COMPAT " + line);
		}
	}
}
