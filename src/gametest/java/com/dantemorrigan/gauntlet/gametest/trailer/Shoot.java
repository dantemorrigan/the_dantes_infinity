package com.dantemorrigan.gauntlet.gametest.trailer;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/** The trailer shoot. Scenes are picked with TRAILER_SCENES=scout,intro,... ; clips land in TRAILER_OUT. */
public class Shoot implements FabricClientGameTest {
	static final int W = 1920, H = 1080;
	static String out;
	static ClientGameTestContext ctx;
	static TestServerContext server;

	static void cmd(String c) { server.runCommand("execute as @p at @p run " + c); }
	static void raw(String c) { server.runCommand(c); }
	static void ticks(int n) { ctx.waitTicks(n); }

	@Override
	public void runTest(ClientGameTestContext c) {
		ctx = c;
		out = System.getenv().getOrDefault("TRAILER_OUT", "/tmp/trailer");
		Set<String> which = Set.of(System.getenv().getOrDefault("TRAILER_SCENES", "scout").split(","));
		Rig.install();
		try (TestSingleplayerContext sp = ctx.worldBuilder().setUseConsistentSettings(false)
				.adjustSettings(s -> s.setSeed(System.getenv().getOrDefault("TRAILER_SEED", "dante"))).create()) {
			server = sp.getServer();
			sp.getClientLevel().waitForChunksRender();
			ctx.runOnClient(mc -> {
				mc.getWindow().setWindowed(W, H);
				mc.options.hideGui = true;
				mc.options.renderDistance().set(14);
				mc.options.fov().set(70);
			});
			raw("gamerule advance_time false");
			raw("gamerule advance_weather false");
			raw("gamerule spawn_mobs false");
			raw("weather clear");
			cmd("gamemode creative");
			raw("tick rate 5");
			ticks(10);
			if (which.contains("scout")) Scenes.scout();
			if (which.contains("intro")) Scenes.intro();
			if (which.contains("gauntlet")) Scenes.gauntletShot();
			if (which.contains("power")) Scenes.power();
			if (which.contains("beam")) Scenes.beam();
			if (which.contains("abilities")) Scenes.abilities();
			if (which.contains("thanos")) Scenes.thanosScenes();
			if (which.contains("snap")) Scenes.snap();
			if (which.contains("planet")) Scenes.planet();
			Rig.off();
		}
	}
}
