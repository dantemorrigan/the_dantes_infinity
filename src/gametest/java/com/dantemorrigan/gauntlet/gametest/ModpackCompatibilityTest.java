package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.client.screen.ModSettingsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

public final class ModpackCompatibilityTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		FabricLoader loader = FabricLoader.getInstance();
		String expected = System.getenv("INFINITY_COMPAT_MODS");
		if (expected == null || expected.isBlank()) throw new AssertionError("Specify the expected Fabric modpack in INFINITY_COMPAT_MODS");
		for (String id : expected.split("\\s+")) {
			check(loader.isModLoaded(id), "The compatibility run did not load " + id);
		}

		try (var world = ctx.worldBuilder().create()) {
			world.getClientLevel().waitForChunksRender();
			world.getServer().runOnServer(server -> {
				for (String recipe : new String[]{"gauntlet", "stormbreaker", "uru_ingot_from_smelting_raw_uru"}) {
					check(server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, InfinityGauntlet.id(recipe))).isPresent(),
							"Missing standard recipe " + recipe);
				}
				check(new ItemStack(ModItems.URU_INGOT).is(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ingots"))),
						"Uru ingots did not resolve through the shared ingot tag");
				var player = server.getPlayerList().getPlayers().getFirst();
				player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(480);
				player.setHealth(420);
			});
			ctx.waitTicks(5);
			ctx.runOnClient(mc -> mc.setScreen(readModMenuSettings(loader, mc.screen)));
			ctx.waitTicks(5);
			ctx.takeScreenshot("modpack_settings");
			ctx.runOnClient(mc -> mc.screen.onClose());
		}
	}

	private static Screen readModMenuSettings(FabricLoader loader, Screen parent) {
		try {
			// Use the installed Mod Menu API at runtime. The test source set has no dependency on it.
			Class<?> api = Class.forName("com.terraformersmc.modmenu.api.ModMenuApi");
			Class<?> factory = Class.forName("com.terraformersmc.modmenu.api.ConfigScreenFactory");
			Object entrypoint = loader.getEntrypointContainers("modmenu", api).stream()
					.filter(entry -> entry.getProvider().getMetadata().getId().equals(InfinityGauntlet.MOD_ID))
					.findFirst().orElseThrow().getEntrypoint();
			Object config = api.getMethod("getModConfigScreenFactory").invoke(entrypoint);
			Object screen = factory.getMethod("create", Screen.class).invoke(config, parent);
			check(screen instanceof ModSettingsScreen, "Mod Menu did not open the Infinity configuration");
			return (Screen) screen;
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError("Mod Menu could not open the Infinity configuration", exception);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
