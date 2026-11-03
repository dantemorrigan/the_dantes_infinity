package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModBlocks;
import com.dantemorrigan.gauntlet.ModItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;

/** Uru ore: worldgen data loads, only diamond/netherite pickaxes get raw uru, it smelts into ingots, the armor is craftable. */
public class UruOreTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			TestServerContext server = sp.getServer();
			sp.getClientLevel().waitForChunksRender();
			server.runOnServer(s -> {
				var level = s.overworld();
				if (s.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE).get(ModBlocks.ORE_URU).isEmpty()) throw new AssertionError("ore_uru placed feature missing");
				var state = ModBlocks.URU_ORE.defaultBlockState();
				if (new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(state)) throw new AssertionError("Iron pickaxe must not mine Uru");
				if (!new ItemStack(Items.DIAMOND_PICKAXE).isCorrectToolForDrops(state) || !new ItemStack(Items.NETHERITE_PICKAXE).isCorrectToolForDrops(state))
					throw new AssertionError("Diamond/netherite pickaxe must mine Uru");
				BlockPos pos = s.getPlayerList().getPlayers().getFirst().blockPosition().offset(2, 0, 0);
				var p = s.getPlayerList().getPlayers().getFirst();
				var drops = Block.getDrops(state, level, pos, null, p, new ItemStack(Items.DIAMOND_PICKAXE));
				if (drops.isEmpty() || !drops.getFirst().is(ModItems.RAW_URU)) throw new AssertionError("Ore drops " + drops);
				var smelt = s.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(new ItemStack(ModItems.RAW_URU)), level);
				if (smelt.isEmpty() || !smelt.get().value().assemble(new SingleRecipeInput(new ItemStack(ModItems.RAW_URU))).is(ModItems.URU_INGOT))
					throw new AssertionError("Raw uru does not smelt into an ingot");
				if (s.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, InfinityGauntlet.id("uru_chestplate"))).isEmpty())
					throw new AssertionError("Uru chestplate recipe missing");
			});
			server.runCommand("time set midnight");
			server.runCommand("gamerule advance_time false");
			server.runCommand("execute as @p at @p run fill ~-3 ~ ~3 ~3 ~3 ~5 minecraft:stone");
			server.runCommand("execute as @p at @p run fill ~-3 ~ ~3 ~-1 ~3 ~3 infinity_gauntlet:uru_ore");
			server.runCommand("execute as @p at @p run fill ~1 ~ ~3 ~3 ~3 ~3 infinity_gauntlet:deepslate_uru_ore");
			server.runCommand("execute as @p at @p run fill ~0 ~ ~3 ~0 ~3 ~3 minecraft:iron_ore");
			server.runCommand("execute as @p run item replace entity @s weapon.mainhand with infinity_gauntlet:uru_ingot");
			ctx.getInput().lookAt(0, -5);
			ctx.waitTicks(20);
			ctx.takeScreenshot("uru_ore_night");
		}
	}
}
