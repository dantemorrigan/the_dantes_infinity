package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.boss.Thanos;
import com.dantemorrigan.gauntlet.boss.ThanosEncounter;
import com.dantemorrigan.gauntlet.client.boss.ThanosClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * A real dedicated server (no client classes on it) and a client joined over the network: the mod boots, Thanos
 * arrives and fights, the battle theme plays on the remote client, he is beaten, his ship falls and the wreck is built.
 */
public class DedicatedServerTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var server = ctx.worldBuilder().createServer()) {
			try (var connection = server.connect()) {
				var level = connection.getClientLevel();
				level.waitForChunksRender();
				server.runCommand("gamerule advance_time false");
				server.runCommand("gamerule advance_weather false");
				server.runCommand("weather clear");
				server.runCommand("time set 3000");
				server.runCommand("gamemode survival @p");
				ctx.waitTicks(20);
				boolean joined = ctx.computeOnClient(mc -> mc.player != null && mc.level != null);
				System.out.println("DEDICATED joined=" + joined);
				if (!joined) throw new AssertionError("The client did not join the dedicated server");
				int players = server.computeOnServer(s -> s.getPlayerList().getPlayerCount());
				System.out.println("DEDICATED players=" + players);

				// The warning, the ship, the beam, the landing.
				server.runCommand("gauntlet thanos reset");
				// Survive the fight (the point here is the network, not the balance).
				server.runCommand("effect give @p minecraft:resistance 9999 4 true");
				server.runCommand("effect give @p minecraft:regeneration 9999 4 true");
				server.runCommand("execute as @p at @s run gauntlet thanos now");
				ctx.waitTicks(260);
				boolean thanosOnClient = ctx.computeOnClient(mc -> !mc.level.getEntitiesOfClass(Thanos.class, mc.player.getBoundingBox().inflate(80)).isEmpty());
				System.out.println("DEDICATED thanos visible on the client=" + thanosOnClient);
				if (!thanosOnClient) throw new AssertionError("Thanos does not show up on the client of a dedicated server");
				ctx.takeScreenshot("dedicated_thanos_landed");

				// The battle theme reaches the remote client.
				ctx.waitTicks(160);
				boolean music = ctx.computeOnClient(mc -> ThanosClient.musicPlaying());
				System.out.println("DEDICATED music playing=" + music);
				if (!music) throw new AssertionError("No battle theme on the client of a dedicated server");

				// He fights: the player takes damage from him.
				ctx.waitTicks(100);
				ctx.takeScreenshot("dedicated_fight");

				// Beaten: kneel, dust, the ship falls, the wreck is built on the server and stays.
				server.runOnServer(s -> {
					ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
					for (Thanos t : ((ServerLevel) p.level()).getEntitiesOfClass(Thanos.class, p.getBoundingBox().inflate(120), e -> true)) {
						t.invulnerableTime = 0;
						t.setHealth(1);
						t.hurtServer((ServerLevel) t.level(), t.level().damageSources().explosion(null, null), 50);
					}
				});
				for (int i = 0; i < 300 && server.computeOnServer(s -> ThanosEncounter.running()); i++) ctx.waitTicks(10);
				int blocks = server.computeOnServer(s -> ThanosEncounter.wreckBlocks);
				System.out.println("DEDICATED wreck blocks=" + blocks);
				if (blocks < 5000) throw new AssertionError("The wreck was not built on the dedicated server: " + blocks);
				ctx.waitTicks(40);
				ctx.takeScreenshot("dedicated_wreck");
				boolean thanosGone = server.computeOnServer(s -> ThanosEncounter.beaten(s));
				if (!thanosGone) throw new AssertionError("Thanos is not recorded as defeated");
				server.runCommand("gauntlet thanos reset");
			}
		}
	}
}
