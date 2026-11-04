package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.space.PlanetDestruction;
import com.dantemorrigan.gauntlet.space.SpaceManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Planet Destruction started in the Nether: the whole event runs, the player goes to Dead Space and comes back to the Nether. */
public class PlanetNetherTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamemode survival @p");
			server.runCommand("execute in minecraft:the_nether run tp @p 0 120 0");
			ctx.waitTicks(30);
			server.runCommand("execute in minecraft:the_nether run fill -8 119 -8 8 119 8 minecraft:netherrack");
			server.runCommand("execute in minecraft:the_nether run tp @p 0 120 0");
			ctx.waitTicks(20);
			GauntletConfig cfg = GauntletConfig.get();
			System.out.println("PLANET-ALL allPlayers=" + cfg.planetDestructionAllPlayers);
			boolean started = server.computeOnServer(s -> {
				ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
				return PlanetDestruction.start(p, new Vec3(0.5, 120, 0.5));
			});
			if (!started) throw new AssertionError("Planet Destruction did not start in the Nether");
			ctx.waitTicks(cfg.transferTick + 40);
			boolean inSpace = server.computeOnServer(s -> SpaceManager.inSpace(s.getPlayerList().getPlayers().getFirst()));
			System.out.println("PLANET-ALL inSpace=" + inSpace);
			if (!inSpace) throw new AssertionError("The player was not taken to Dead Space");
			server.runOnServer(s -> SpaceManager.returnFromSpace(s.getPlayerList().getPlayers().getFirst()));
			ctx.waitTicks(40);
			boolean home = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().level().dimension().equals(Level.NETHER));
			System.out.println("PLANET-ALL back in the Nether=" + home);
			if (!home) throw new AssertionError("The player did not return to the Nether");
		}
	}
}
