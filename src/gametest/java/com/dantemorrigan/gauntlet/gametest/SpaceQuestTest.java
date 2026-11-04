package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.stones.SpaceStoneQuest;
import com.dantemorrigan.gauntlet.stones.StoneRegistry;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/** End rift → frozen asteroid (cold without iron armor) → Tesseract on the tower → home → lava → Space Stone. */
public class SpaceQuestTest implements FabricClientGameTest {
	private static ServerPlayer player(net.minecraft.server.MinecraftServer s) { return s.getPlayerList().getPlayers().getFirst(); }

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamemode creative @p");
			server.runCommand("gauntlet stones reset");
			// The rift in the End.
			server.runCommand("execute in minecraft:the_end run tp @p 0.5 90 10.5");
			ctx.waitTicks(60);
			BlockPos rift = server.computeOnServer(s -> SpaceStoneQuest.buildEndRift(s.getLevel(Level.END)));
			server.runCommand("execute in minecraft:the_end run tp @p " + (rift.getX() + 0.5) + " " + (rift.getY() + 1) + " " + (rift.getZ() - 12.5) + " 0 -5");
			server.runCommand("effect give @p slow_falling 30 1 true");
			ctx.waitTicks(80);
			ctx.takeScreenshot("space_end_rift");
			// Walk in (survival: the cold must bite).
			server.runCommand("gamemode survival @p");
			server.runCommand("execute in minecraft:the_end run tp @p " + (rift.getX() + 0.5) + " " + (rift.getY() + 0.5) + " " + (rift.getZ() + 0.5));
			ctx.waitFor(mc -> mc.level != null && mc.level.dimension().equals(SpaceStoneQuest.FROST), 200);
			ctx.waitTicks(10);
			ctx.takeScreenshot("space_asteroid_arrival");
			float hp = server.computeOnServer(s -> player(s).getHealth());
			ctx.waitTicks(25);
			if (server.computeOnServer(s -> player(s).getHealth()) >= hp) throw new AssertionError("The cold does not hurt without armor");
			ctx.takeScreenshot("space_freezing");
			for (String part : new String[]{"head iron_helmet", "chest iron_chestplate", "legs iron_leggings", "feet iron_boots"}) {
				String[] a = part.split(" ");
				server.runCommand("item replace entity @p armor." + a[0] + " with minecraft:" + a[1]);
			}
			server.runCommand("effect give @p instant_health 1 5 true");
			ctx.waitTicks(20);
			float healed = server.computeOnServer(s -> player(s).getHealth());
			ctx.waitTicks(40);
			if (server.computeOnServer(s -> player(s).getHealth()) < healed) throw new AssertionError("Iron armor does not protect from the cold");

			// The tower: from outside, inside and on top.
			BlockPos cube = server.computeOnServer(s -> SpaceStoneQuest.tesseractPos(s.getLevel(SpaceStoneQuest.FROST)));
			server.runCommand("effect give @p slow_falling 60 1 true");
			server.runCommand("execute in infinity_gauntlet:frost_asteroid run tp @p " + (cube.getX() + 0.5) + " " + (cube.getY() - 20) + " " + (cube.getZ() - 40.5) + " 0 -12");
			ctx.waitTicks(10);
			sp.getClientLevel().waitForChunksRender();
			ctx.takeScreenshot("space_tower_outside");
			server.runCommand("execute in infinity_gauntlet:frost_asteroid run tp @p " + (cube.getX() + 0.5) + " " + (cube.getY() - 30) + " " + (cube.getZ() - 3.5) + " 0 -40");
			ctx.waitTicks(10);
			sp.getClientLevel().waitForChunksRender();
			ctx.takeScreenshot("space_tower_inside");
			server.runCommand("execute in infinity_gauntlet:frost_asteroid run tp @p " + (cube.getX() + 0.5) + " " + (cube.getY() - 1) + " " + (cube.getZ() - 3.5) + " 0 20");
			ctx.waitTicks(10);
			sp.getClientLevel().waitForChunksRender();
			ctx.takeScreenshot("space_tesseract_pedestal");
			server.runOnServer(s -> SpaceStoneQuest.takeTesseract(player(s), cube));
			ctx.waitTicks(15);
			ctx.takeScreenshot("space_alert");
			if (!server.computeOnServer(s -> player(s).getInventory().contains(st -> st.is(ModItems.QUEST_SPACE_TESSERACT)))) throw new AssertionError("No Tesseract");
			if (!server.computeOnServer(s -> StoneRegistry.found(s, Stone.SPACE))) throw new AssertionError("Space not found");

			// Home.
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.QUEST_SPACE_TESSERACT));
				SpaceStoneQuest.useTesseract(p, InteractionHand.MAIN_HAND);
			});
			ctx.waitFor(mc -> mc.level != null && mc.level.dimension().equals(Level.OVERWORLD), 200);
			sp.getClientLevel().waitForChunksRender();
			ctx.waitTicks(10);
			if (!server.computeOnServer(s -> player(s).level().dimension().equals(Level.OVERWORLD))) throw new AssertionError("Tesseract did not send home: " + server.computeOnServer(s -> player(s).level().dimension() + " " + player(s).position() + " alive=" + player(s).isAlive()));

			// Lava.
			BlockPos pool = server.computeOnServer(s -> player(s).blockPosition().offset(0, -1, 6));
			server.runCommand("fill " + (pool.getX() - 2) + " " + (pool.getY() - 1) + " " + (pool.getZ() - 2) + " " + (pool.getX() + 2) + " " + pool.getY() + " " + (pool.getZ() + 2) + " minecraft:lava");
			server.runCommand("tp @p " + (pool.getX() + 0.5) + " " + (pool.getY() + 3) + " " + (pool.getZ() - 5.5) + " 0 30");
			ctx.waitTicks(10);
			sp.getClientLevel().waitForChunksRender();
			server.runOnServer(s -> {
				ItemEntity cubeItem = new ItemEntity(s.overworld(), pool.getX() + 0.5, pool.getY() + 1.5, pool.getZ() + 0.5, new ItemStack(ModItems.QUEST_SPACE_TESSERACT));
				s.overworld().addFreshEntity(cubeItem);
				player(s).getInventory().clearOrCountMatchingItems(st -> st.is(ModItems.QUEST_SPACE_TESSERACT), -1, player(s).inventoryMenu.getCraftSlots());
			});
			ctx.waitTicks(6);
			ctx.takeScreenshot("space_lava_burst");
			ctx.waitTicks(40);
			server.runCommand("tp @p " + (pool.getX() + 0.5) + " " + (pool.getY() + 4) + " " + (pool.getZ() - 6.5) + " 0 25");
			ctx.waitTicks(5);
			sp.getClientLevel().waitForChunksRender();
			ctx.takeScreenshot("space_stone_released");
			if (!server.computeOnServer(s -> s.overworld().getBlockState(pool).is(Blocks.OBSIDIAN))) throw new AssertionError("Lava did not turn to obsidian");
			if (!server.computeOnServer(s -> StoneRegistry.released(s, Stone.SPACE))) throw new AssertionError("Space Stone not released");
			if (server.computeOnServer(s -> s.overworld().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(pool).inflate(4),
					e -> e.getItem().is(ModItems.SPACE_STONE)).isEmpty())) throw new AssertionError("No glowing Space Stone: " + server.computeOnServer(st -> st.overworld().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(pool).inflate(30)).toString()) + " pool=" + pool + " obs=" + server.computeOnServer(st -> st.overworld().getBlockState(pool)));
		}
	}
}
