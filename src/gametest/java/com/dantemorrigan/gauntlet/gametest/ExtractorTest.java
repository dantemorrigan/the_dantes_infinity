package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.entity.GauntletDoll;
import com.dantemorrigan.gauntlet.extractor.StoneExtraction;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** Infinity Extractor against a Steve doll: three cycles pull a stone out of its gauntlet into the extractor. */
public class ExtractorTest implements FabricClientGameTest {
	private static GauntletDoll doll(TestServerContext server) {
		return server.computeOnServer(s -> s.overworld().getEntities(ModEntities.GAUNTLET_DOLL, d -> true).getFirst());
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		LegacyBindings.disable(ctx);
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			TestServerContext server = sp.getServer();
			sp.getClientLevel().waitForChunksRender();
			server.runCommand("time set noon");
			server.runCommand("gamerule advance_time false");
			ctx.getInput().lookAt(0, 8);
			server.runCommand("execute as @p at @p run gauntlet doll");
			server.runCommand("execute as @p run give @s infinity_gauntlet:infinity_extractor");
			ctx.waitTicks(10);
			server.runCommand("data merge entity @e[type=infinity_gauntlet:gauntlet_doll,limit=1] {NoAI:1b}");
			ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().getFirst();
				if (!p.getInventory().getItem(0).is(com.dantemorrigan.gauntlet.ModItems.INFINITY_EXTRACTOR)) {
					ItemStack e = p.getInventory().getItem(p.getInventory().findSlotMatchingItem(new ItemStack(com.dantemorrigan.gauntlet.ModItems.INFINITY_EXTRACTOR)));
					p.getInventory().setItem(0, e.copy());
				}
				var d = s.overworld().getEntities(ModEntities.GAUNTLET_DOLL, x -> true).getFirst();
				if (GauntletState.inserted(GauntletState.activeGauntlet(d)).size() != 6) throw new AssertionError("Doll has no full gauntlet");
			});
			ctx.waitTicks(5);
			ctx.takeScreenshot("extractor_00_aim");
			for (int cycle = 1; cycle <= 3; cycle++) {
				ctx.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
				ctx.waitTicks(30);
				ctx.takeScreenshot("extractor_0" + cycle + "a_channel");
				ctx.waitTicks(StoneExtraction.CYCLE_TICKS - 30 + 6);
				ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
				ctx.waitTicks(8);
				ctx.takeScreenshot("extractor_0" + cycle + "b_done");
				ctx.waitTicks(5);
			}
			server.runOnServer(s -> {
				var d = s.overworld().getEntities(ModEntities.GAUNTLET_DOLL, x -> true).getFirst();
				if (GauntletState.inserted(GauntletState.activeGauntlet(d)).contains(Stone.POWER)) throw new AssertionError("Stone was not pulled out of the doll");
				var p = s.getPlayerList().getPlayers().getFirst();
				ItemStack ex = p.getMainHandItem();
				if (!ex.is(com.dantemorrigan.gauntlet.ModItems.INFINITY_EXTRACTOR) || StoneExtraction.chamber(ex).isEmpty()) throw new AssertionError("Chamber is empty");
			});
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			ctx.waitTicks(5);
			ctx.takeScreenshot("extractor_04_third_person");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			// Put the stone back into the doll's owner... here: into the player's own gauntlet via the use-without-target path.
			server.runCommand("execute as @p at @p run gauntlet give_power");
			ctx.getInput().lookAt(0, -60);
			ctx.waitTicks(5);
			ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			ctx.waitTicks(10);
			ctx.takeScreenshot("extractor_05_returned");
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().getFirst();
				if (!StoneExtraction.chamber(p.getMainHandItem()).isEmpty() && !GauntletState.inserted(GauntletState.activeGauntlet(p)).contains(Stone.POWER))
					throw new AssertionError("Stone was neither returned nor kept");
			});
		}
	}
}
