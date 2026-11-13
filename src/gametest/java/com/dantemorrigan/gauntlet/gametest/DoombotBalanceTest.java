package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.doom.Doombot;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;

/** Bastion shield pose and charge, Interceptor dive / land / take-off cycle. */
public class DoombotBalanceTest implements FabricClientGameTest {
	private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

	private static String state(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, int variant) {
		return server.computeOnServer(s -> {
			for (Doombot b : s.overworld().getEntitiesOfClass(Doombot.class, s.getPlayerList().getPlayers().getFirst().getBoundingBox().inflate(40)))
				if (b.variant() == variant) return String.format("act=%d fly=%b y=%.1f hp=%.0f/%.0f", b.action(), b.flag(Doombot.FLAG_FLYING), b.getY(), b.getHealth(), b.getMaxHealth());
			return "none";
		});
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("time set 6000");
			server.runCommand("gamemode creative @p");
			server.runCommand("execute as @p at @s run fill ~-24 ~-1 ~-24 ~24 ~-1 ~24 minecraft:polished_deepslate");
			server.runCommand("execute as @p at @s run fill ~-24 ~ ~-24 ~24 ~14 ~24 minecraft:air");
			server.runCommand("execute as @p at @s run gauntlet doombot spawn bastion true ~-8 ~ ~-14");
			server.runCommand("execute as @p at @s run gauntlet doombot spawn interceptor true ~8 ~ ~-14");
			server.runCommand("execute as @p at @s positioned ~-8 ~ ~-5 run summon minecraft:husk ~ ~ ~ {NoAI:1b,Invulnerable:1b}");
			server.runCommand("execute as @p at @s positioned ~8 ~ ~-5 run summon minecraft:husk ~ ~ ~ {NoAI:1b,Invulnerable:1b}");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 180 6");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			boolean sawLand = false, sawFly = false, sawBastionAttack = false;
			int shots = 0;
			for (int i = 0; i < 120; i++) {
				ctx.waitTicks(4);
				String b = state(server, Doombot.BASTION), f = state(server, Doombot.INTERCEPTOR);
				System.out.println("BAL " + i * 4 + " bastion " + b + " | interceptor " + f);
				if (b.startsWith("act=2")) { sawBastionAttack = true; if (shots < 12) { ctx.takeScreenshot("bastion_" + String.format("%02d", shots++)); } }
				if (f.contains("fly=true")) sawFly = true;
				if (f.contains("fly=false")) sawLand = true;
			}
			ctx.takeScreenshot("balance_end");
			check(sawBastionAttack, "Bastion never attacked");
			check(sawFly && sawLand, "Interceptor did not both fly and land: fly=" + sawFly + " land=" + sawLand);
		}
	}
}
