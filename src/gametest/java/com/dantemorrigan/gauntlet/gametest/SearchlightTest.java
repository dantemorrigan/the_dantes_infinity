package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.client.doom.DoomVfxClient;
import com.dantemorrigan.gauntlet.doom.Doombot;
import com.dantemorrigan.gauntlet.doom.Latveria;
import com.dantemorrigan.gauntlet.doom.Searchlights;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The castle searchlights sweep the slope, catch a player who walks into a beam, raise the alarm and die with the generator. */
public class SearchlightTest implements FabricClientGameTest {
	private static ServerPlayer player(MinecraftServer s) { return s.getPlayerList().getPlayers().getFirst(); }

	private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

	@Override
	public void runTest(ClientGameTestContext ctx) {
		LegacyBindings.disable(ctx);
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamemode creative @p");
			server.runCommand("execute as @p run gauntlet latveria enter");
			ctx.waitTicks(260);
			// A view of the castle from the road, beams sweeping.
			server.runCommand("execute as @p run gauntlet latveria clear 0");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.getAbilities().flying = true;
				p.onUpdateAbilities();
				p.teleportTo(s.getLevel(Latveria.LEVEL), 12.5, com.dantemorrigan.gauntlet.doom.LatveriaPlan.FLOOR + 6, -160.5, java.util.Set.of(), 170, -12, true);
			});
			ctx.waitTicks(60);
			sp.getClientLevel().waitForChunksRender();
			System.out.println("SPOTS " + server.computeOnServer(s -> {
				ServerLevel l = s.getLevel(Latveria.LEVEL);
				StringBuilder o = new StringBuilder();
				for (int i = 0; i < 3; i++) for (int k = 0; k < 4; k++) {
					long t = l.getGameTime() + k * 150L;
					Vec3 p = Searchlights.spot(l, i, Searchlights.direction(i, t));
					o.append(String.format("L%d@%d(%.0f,%.0f,%.0f d%.0f) ", i, k, p.x, p.y, p.z, p.distanceTo(Searchlights.LAMPS[i])));
				}
				return o.toString();
			}));
			check(ctx.computeOnClient(mc -> DoomVfxClient.lightsOn()), "The searchlights are not burning while the gate is shut");
			ctx.takeScreenshot("searchlights_01_sweep");
			ctx.waitTicks(30);
			ctx.takeScreenshot("searchlights_02_sweep_later");

			// Step into a beam: the alarm sounds and Interceptors launch.
			server.runCommand("gamemode survival @p");
			server.runCommand("effect give @p minecraft:resistance 60 4 true");
			server.runOnServer(s -> {
				ServerLevel l = s.getLevel(Latveria.LEVEL);
				long t = l.getGameTime() + 4 - (l.getGameTime() % 4);
				Vec3 spot = Searchlights.spot(l, 2, Searchlights.direction(2, t));
				player(s).teleportTo(l, spot.x, spot.y, spot.z, java.util.Set.of(), 0, 10, true);
			});
			ctx.waitTicks(10);
			int bots = server.computeOnServer(s -> s.getLevel(Latveria.LEVEL).getEntitiesOfClass(Doombot.class, new AABB(-200, 0, -400, 200, 320, 0),
					b -> b.entityTags().contains(Searchlights.ALARM_TAG)).size());
			check(bots > 0, "Standing in a beam did not raise the alarm");
			check(server.computeOnServer(s -> player(s).hasEffect(net.minecraft.world.effect.MobEffects.GLOWING)), "The caught player does not glow");
			server.runCommand("gamemode creative @p");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.getAbilities().flying = true;
				p.onUpdateAbilities();
				p.teleportTo(s.getLevel(Latveria.LEVEL), 12.5, com.dantemorrigan.gauntlet.doom.LatveriaPlan.FLOOR + 6, -160.5, java.util.Set.of(), 170, -12, true);
			});
			ctx.waitTicks(20);
			ctx.takeScreenshot("searchlights_03_alarm");

			// The generator goes off: so do the lamps.
			server.runCommand("execute as @p run gauntlet latveria generator off");
			ctx.waitTicks(50);
			check(!ctx.computeOnClient(mc -> DoomVfxClient.lightsOn()), "The searchlights still burn with the generator off");
			ctx.takeScreenshot("searchlights_04_dark");

			// Shadow of Doomstadt: an alarm spoils it; an assault reset and done unseen earns it.
			java.util.function.Function<MinecraftServer, Boolean> unseen = s -> {
				var adv = s.getAdvancements().get(com.dantemorrigan.gauntlet.InfinityGauntlet.id("unseen_in_latveria"));
				return adv != null && player(s).getAdvancements().getOrStartProgress(adv).isDone();
			};
			server.runCommand("execute as @p run gauntlet latveria clear 1");
			ctx.waitTicks(30);
			check(!server.computeOnServer(unseen::apply), "The stealth advancement was given after an alarm");
			server.runCommand("execute as @p run gauntlet latveria reset");
			server.runCommand("execute as @p run gauntlet latveria squads");
			ctx.waitTicks(10);
			server.runCommand("execute as @p run gauntlet latveria clear 1");
			ctx.waitTicks(30);
			check(server.computeOnServer(unseen::apply), "Opening the gate unseen did not give Shadow of Doomstadt");
		}
	}
}
