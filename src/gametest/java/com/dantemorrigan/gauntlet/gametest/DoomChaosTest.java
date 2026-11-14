package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.ability.Ability;
import com.dantemorrigan.gauntlet.ability.AbilityController;
import com.dantemorrigan.gauntlet.doom.DoomArmor;
import com.dantemorrigan.gauntlet.doom.Doombot;
import com.dantemorrigan.gauntlet.doom.Latveria;
import com.dantemorrigan.gauntlet.doom.LatveriaPlan;
import com.dantemorrigan.gauntlet.power.MindStone;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import java.util.Random;

/**
 * Chaos in the throne room: forty rounds of random Infinity Gauntlet powers and Doom's-armor powers, fired in any
 * order and at any moment, against a fighting Doom, his Royal Guards and a few extra Doombots. Afterwards the castle
 * stands, Doom is either alive and whole or properly dead, nothing holds him, every Doombot is accounted for, and the
 * player is free to move.
 */
public class DoomChaosTest implements FabricClientGameTest {
	private static ServerPlayer player(MinecraftServer s) { return s.getPlayerList().getPlayers().getFirst(); }

	private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

	private static String castle(MinecraftServer s) {
		var l = s.getLevel(Latveria.LEVEL);
		StringBuilder out = new StringBuilder();
		for (BlockPos p : BlockPos.betweenClosed(-21, LatveriaPlan.FLOOR - 1, -294, 21, LatveriaPlan.FLOOR - 1, -254)) {
			if ((p.getX() + p.getZ()) % 5 == 0) out.append(l.getBlockState(p).getBlock().hashCode() % 97).append(',');
		}
		for (BlockPos p : BlockPos.betweenClosed(-21, LatveriaPlan.FLOOR, -295, 21, LatveriaPlan.FLOOR + 12, -295)) out.append(l.getBlockState(p).isAir() ? 'a' : 's');
		return out.toString();
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		LegacyBindings.disable(ctx);
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamemode creative @p");
			server.runCommand("execute as @p run gauntlet latveria enter");
			ctx.waitTicks(40);
			server.runCommand("execute as @p run gauntlet latveria unlock");
			server.runCommand("execute as @p run gauntlet latveria tp hall");
			ctx.waitTicks(30);
			server.runCommand("execute as @p run gauntlet doom spawn");
			ctx.waitTicks(140);
			for (String v : new String[]{"soldier", "bastion", "interceptor", "arcanist"}) {
				server.runCommand("execute as @p at @s run gauntlet doombot spawn " + v + " false ~4 ~ ~-6");
			}
			server.runCommand("execute as @p run gauntlet full");
			server.runCommand("execute as @p run gauntlet doom armor");
			ctx.waitTicks(20);
			String before = server.computeOnServer(DoomChaosTest::castle);

			Ability[] powers = {Ability.STRIKE, Ability.REALITY_STRIKE, Ability.INVERSION, Ability.TIME_FLOW, Ability.REWIND, Ability.GRAB,
					Ability.TEAR, Ability.DROP, Ability.SOUL, Ability.SOUL_MASS, Ability.BLINK, Ability.BEAM, Ability.PLANET};
			Random rnd = new Random(1234);
			for (int round = 0; round < 40; round++) {
				final int pick = rnd.nextInt(powers.length + 6);
				final double yaw = rnd.nextDouble() * 360;
				server.runOnServer(s -> {
					ServerPlayer p = player(s);
					var d = Latveria.boss();
					if (d != null && d.isAlive()) p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, d.position().add(0, 1.5, 0));
					else p.setYRot((float) yaw);
					if (pick < powers.length) {
						AbilityController.select(p, powers[pick].id, -1);
						AbilityController.action(p, 0);
					} else {
						DoomArmor.resetCooldowns(p);
						DoomArmor.cast(p, pick - powers.length);
					}
				});
				ctx.waitTicks(4 + rnd.nextInt(18));
				server.runOnServer(s -> AbilityController.action(player(s), 1));
				if (round % 8 == 7) server.runCommand("execute as @p run gauntlet doom heal");
			}
			ctx.waitTicks(80);
			server.runOnServer(s -> { AbilityController.cancel(player(s)); MindStone.drop(player(s)); });
			ctx.waitTicks(20);

			String broken = server.computeOnServer(s -> {
				var d = Latveria.boss();
				if (d != null && d.isRemoved()) return "Doom was removed without dying";
				if (d != null && MindStone.holdsEntity(d)) return "something still holds Doom";
				if (!castle(s).equals(before)) return "the throne room changed";
				ServerPlayer p = player(s);
				if (!p.isAlive()) return "the player died in creative";
				for (Doombot b : s.getLevel(Latveria.LEVEL).getEntitiesOfClass(Doombot.class, new AABB(-60, 0, -340, 60, 320, -200)))
					if (b.isRemoved() && b.getRemovalReason() == net.minecraft.world.entity.Entity.RemovalReason.DISCARDED && !b.decoy()) return "a Doombot was discarded";
				return "";
			});
			System.out.println("CHAOS doom=" + server.computeOnServer(s -> Latveria.boss() == null ? "none" : (int) Latveria.boss().getHealth() + "hp " + Latveria.boss().action())
					+ " bots=" + server.computeOnServer(s -> s.getLevel(Latveria.LEVEL).getEntitiesOfClass(Doombot.class, new AABB(-60, 0, -340, 60, 320, -200)).size()));
			check(broken.isEmpty(), "After the chaos: " + broken);
			// The player can still move and cast: no stuck cutscene or input lock.
			check(!ctx.computeOnClient(mc -> com.dantemorrigan.gauntlet.client.doom.DoomClient.cinematic()), "A cinematic is still on after the chaos");
			ctx.takeScreenshot("chaos_after");
		}
	}
}
