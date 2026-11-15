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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;

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
				checkUruTools(server.overworld());
				var player = server.getPlayerList().getPlayers().getFirst();
				player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(480);
				player.setHealth(420);
			});
			ctx.waitTicks(5);
			ctx.runOnClient(mc -> mc.setScreen(readModMenuSettings(loader, mc.screen)));
			ctx.waitTicks(5);
			check(ctx.computeOnClient(mc -> mc.screen instanceof ModSettingsScreen), "Infinity settings did not stay open");
			ctx.runOnClient(mc -> mc.screen.onClose());
		}
	}

	private static void checkUruTools(ServerLevel level) {
		ItemStack sword = craft(level, ModItems.URU_SWORD, " U ", " U ", " S ");
		ItemStack pickaxe = craft(level, ModItems.URU_PICKAXE, "UUU", " S ", " S ");
		ItemStack hoe = craft(level, ModItems.URU_HOE, "UU ", " S ", " S ");
		ItemStack shovel = craft(level, ModItems.URU_SHOVEL, " U ", " S ", " S ");

		double damageRatio = attackDamage(sword) / attackDamage(new ItemStack(Items.NETHERITE_SWORD));
		check(damageRatio >= 1.10 && damageRatio <= 1.15, "Uru sword damage was outside the target range");
		check(sword.is(ItemTags.SWORDS) && pickaxe.is(ItemTags.PICKAXES)
				&& hoe.is(ItemTags.HOES) && shovel.is(ItemTags.SHOVELS), "Missing standard classification for the Uru tools");
		check(pickaxe.isCorrectToolForDrops(Blocks.ANCIENT_DEBRIS.defaultBlockState()), "Uru could not mine ancient debris");
		check(pickaxe.getDestroySpeed(Blocks.STONE.defaultBlockState())
				> new ItemStack(Items.NETHERITE_PICKAXE).getDestroySpeed(Blocks.STONE.defaultBlockState()), "Uru pickaxe was not faster than netherite");
		for (ItemStack tool : new ItemStack[]{sword, pickaxe, hoe, shovel}) {
			check(tool.getMaxDamage() > new ItemStack(Items.NETHERITE_SWORD).getMaxDamage(), "Uru tool durability was too low");
			check(tool.isValidRepairItem(new ItemStack(ModItems.URU_INGOT)), "Uru ingots could not repair the tool");
		}
	}

	private static ItemStack craft(ServerLevel level, Item output, String... pattern) {
		var stacks = new ArrayList<ItemStack>(9);
		for (String row : pattern) {
			for (int column = 0; column < row.length(); column++) {
				stacks.add(switch (row.charAt(column)) {
					case 'U' -> new ItemStack(ModItems.URU_INGOT);
					case 'S' -> new ItemStack(Items.STICK);
					default -> ItemStack.EMPTY;
				});
			}
		}
		CraftingInput input = CraftingInput.of(3, 3, stacks);
		ItemStack crafted = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
				.orElseThrow(() -> new AssertionError("No crafting recipe for " + output)).value().assemble(input);
		check(crafted.is(output), "Wrong result for " + output);
		return crafted;
	}

	private static double attackDamage(ItemStack sword) {
		double[] damage = {1}; // Player base attack damage.
		sword.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			if (attribute == Attributes.ATTACK_DAMAGE) damage[0] += modifier.amount();
		});
		return damage[0];
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
