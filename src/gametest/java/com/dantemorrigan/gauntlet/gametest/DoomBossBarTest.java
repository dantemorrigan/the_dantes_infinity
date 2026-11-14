package com.dantemorrigan.gauntlet.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Doctor Doom's boss bar in its states: full health, the shield up, casting, nearly dead, and defeated. */
public class DoomBossBarTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamemode creative @p");
			server.runCommand("execute as @p run gauntlet latveria enter");
			ctx.waitTicks(260);
			server.runCommand("execute as @p run gauntlet latveria unlock");
			server.runCommand("execute as @p run gauntlet latveria tp hall");
			ctx.waitTicks(30);
			server.runCommand("execute as @p run gauntlet doom spawn");
			ctx.waitTicks(200);
			server.runCommand("execute as @p run gauntlet doom passive");
			ctx.waitTicks(20);
			ctx.takeScreenshot("bar_sovereign");
			server.runCommand("execute as @p run gauntlet doom action iron_bastion");
			ctx.waitTicks(40);
			ctx.takeScreenshot("bar_warded");
			server.runCommand("execute as @p run gauntlet doom action latverian_edict");
			ctx.waitTicks(30);
			ctx.takeScreenshot("bar_overdrive");
			server.runOnServer(s -> { var d = com.dantemorrigan.gauntlet.doom.Latveria.boss(); if (d != null) d.setHealth(d.getMaxHealth() * 0.15f); });
			ctx.waitTicks(60);
			ctx.takeScreenshot("bar_critical");
			server.runCommand("execute as @p run gauntlet doom kill");
			ctx.waitTicks(30);
			ctx.takeScreenshot("bar_defeated");
		}
	}
}
