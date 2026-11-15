package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.client.doom.LatveriaWelcomeScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** The first arrival in Latveria: the title card, then the welcome, in English and in Russian; and a house's door. */
public class LatveriaWelcomeTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		System.setProperty("infinity_gauntlet.noWelcome", "false");
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamemode survival @p");
			server.runCommand("execute as @p run gauntlet latveria enter");
			ctx.waitTicks(320);
			boolean shown = ctx.computeOnClient(mc -> mc.screen instanceof LatveriaWelcomeScreen);
			System.out.println("WELCOME shown " + shown);
			ctx.takeScreenshot("welcome_en");
			if (!shown) throw new AssertionError("The welcome to Latveria did not open after the title card");
			ctx.runOnClient(mc -> { com.dantemorrigan.gauntlet.client.lang.ModLang.set("ru"); mc.setScreen(new LatveriaWelcomeScreen()); });
			ctx.waitTicks(10);
			ctx.takeScreenshot("welcome_ru");
			ctx.runOnClient(mc -> { com.dantemorrigan.gauntlet.client.lang.ModLang.set("auto"); mc.setScreen(null); });
			// A house's front with its door, seen from the street.
			server.runCommand("gamemode spectator @p");
			server.runCommand("execute in infinity_gauntlet:latveria run tp @p 0.5 75 60.5");
			ctx.waitTicks(60);
			String tp = server.computeOnServer(s -> {
				var l = s.getLevel(com.dantemorrigan.gauntlet.doom.Latveria.LEVEL);
				int doors = 0; String best = null;
				for (int y = 69; y <= 71; y++) for (int x = -40; x <= 40; x++) for (int z = 20; z <= 100; z++) {
					var p = new net.minecraft.core.BlockPos(x, y, z);
					var st = l.getBlockState(p);
					if (!(st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock)) continue;
					doors++;
					var in = st.getValue(net.minecraft.world.level.block.DoorBlock.FACING);
					boolean open = true;
					for (int k = 1; k <= 5; k++) open &= l.getBlockState(p.relative(in.getOpposite(), k)).isAir() && l.getBlockState(p.relative(in.getOpposite(), k).above()).isAir();
					if (best == null && open) {
						var side = in.getClockWise();
						double ex = x + 0.5 - in.getStepX() * 5 + side.getStepX() * 2.5, ez = z + 0.5 - in.getStepZ() * 5 + side.getStepZ() * 2.5;
						best = ex + " " + (y + 1.2) + " " + ez + " facing " + (x + 0.5) + " " + (y + 0.8) + " " + (z + 0.5);
					}
				}
				System.out.println("DOORS around the square " + doors);
				return best;
			});
			if (tp == null) throw new AssertionError("No house doors near the square");
			server.runCommand("execute in infinity_gauntlet:latveria run tp @p " + tp);
			ctx.waitTicks(40);
			ctx.takeScreenshot("doors_street");
			// The city lamps glide: no spot moves more than half a block a tick, and none ever leaves the city.
			String glide = server.computeOnServer(s -> {
				var l = s.getLevel(com.dantemorrigan.gauntlet.doom.Latveria.LEVEL);
				var SL = com.dantemorrigan.gauntlet.doom.Searchlights.class;
				double worst = 0; int out = 0;
				for (int i = com.dantemorrigan.gauntlet.doom.Searchlights.SLOPE_LAMPS; i < com.dantemorrigan.gauntlet.doom.Searchlights.LAMPS.length; i++) {
					net.minecraft.world.phys.Vec3 prev = null;
					for (int t = 0; t < 6000; t += 1) {
						var sp2 = com.dantemorrigan.gauntlet.doom.Searchlights.spot(l, i, com.dantemorrigan.gauntlet.doom.Searchlights.direction(i, t));
						if (sp2.x < -96 || sp2.x > 96 || sp2.z < -72 || sp2.z > 150) out++;
						if (prev != null) worst = Math.max(worst, Math.hypot(sp2.x - prev.x, sp2.z - prev.z));
						prev = sp2;
					}
				}
				return worst + " " + out;
			});
			System.out.println("SEARCHLIGHT glide worst step/out " + glide);
			String[] g = glide.split(" ");
			if (Double.parseDouble(g[0]) > 0.6 || Integer.parseInt(g[1]) > 0) throw new AssertionError("City searchlights jump or leave the city: " + glide);
			server.runCommand("time set midnight");
			String aim = server.computeOnServer(s -> {
				var l = s.getLevel(com.dantemorrigan.gauntlet.doom.Latveria.LEVEL);
				var c = com.dantemorrigan.gauntlet.doom.Searchlights.cityTarget(5, l.getGameTime() + 60);
				return (c.x + 12) + " 88 " + (c.z + 12) + " facing " + c.x + " 69 " + c.z;
			});
			server.runCommand("execute in infinity_gauntlet:latveria run tp @p " + aim);
			ctx.waitTicks(60);
			ctx.takeScreenshot("searchlights_a");
			ctx.waitTicks(40);
			ctx.takeScreenshot("searchlights_b");
		}
	}
}
