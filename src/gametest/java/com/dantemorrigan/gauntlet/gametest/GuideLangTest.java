package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.client.lang.ModLang;
import com.dantemorrigan.gauntlet.client.screen.ModSettingsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/** Language switcher + the first-join guide book, in both languages. */
public class GuideLangTest implements FabricClientGameTest {
	private static void book(ClientGameTestContext ctx, String name, int pages) {
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		ctx.waitFor(mc -> mc.screen instanceof BookViewScreen, 100);
		ctx.waitTicks(4);
		ctx.takeScreenshot(name + "_cover");
		for (int page : new int[]{pages, 3, 8}) {
			ctx.runOnClient(mc -> ((BookViewScreen) mc.screen).setPage(page));
			ctx.waitTicks(2);
			ctx.takeScreenshot(name + "_p" + (page + 1));
		}
		ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		ctx.waitFor(mc -> mc.screen == null, 100);
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			ctx.waitTicks(10);
			if (!ctx.computeOnClient(mc -> java.util.stream.IntStream.range(0, mc.player.getInventory().getContainerSize()).anyMatch(i -> mc.player.getInventory().getItem(i).is(Items.WRITTEN_BOOK))))
				throw new AssertionError("Guide book was not given on first join");

			// Settings screen in every language
			for (String code : new String[]{"en", "ru", "auto"}) {
				ctx.runOnClient(mc -> ModLang.set(code));
				ctx.runOnClient(mc -> mc.setScreen(new ModSettingsScreen(null)));
				ctx.waitTicks(6);
				ctx.takeScreenshot("settings_" + code);
				ctx.runOnClient(mc -> mc.setScreen(null));
			}
			// The override must really change mod strings
			ctx.runOnClient(mc -> ModLang.set("ru"));
			String ru = ctx.computeOnClient(mc -> Component.translatable("item.infinity_gauntlet.power_stone").getString());
			ctx.runOnClient(mc -> ModLang.set("en"));
			String en = ctx.computeOnClient(mc -> Component.translatable("item.infinity_gauntlet.power_stone").getString());
			if (ru.equals(en) || !ru.matches(".*[а-яА-Я].*")) throw new AssertionError("Language override failed: " + en + " / " + ru);

			ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
			ctx.runOnClient(mc -> ModLang.set("en"));
			book(ctx, "guide_en", 1);
			ctx.runOnClient(mc -> ModLang.set("ru"));
			book(ctx, "guide_ru", 1);
			ctx.runOnClient(mc -> ModLang.set("auto"));
		}
	}
}
