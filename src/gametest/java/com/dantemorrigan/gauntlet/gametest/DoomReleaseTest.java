package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.doom.DoctorDoom;
import com.dantemorrigan.gauntlet.doom.DoomFinalEncounter;
import com.dantemorrigan.gauntlet.doom.LatveriaQuests;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class DoomReleaseTest implements FabricClientGameTest {
	private static final String STOLEN_NAME = "Release test: stolen";

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	private static int returnedCount(ServerPlayer p) {
		int count = 0;
		var inventory = p.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			Component name = stack.get(DataComponents.CUSTOM_NAME);
			if (stack.is(ModItems.GAUNTLET) && name != null && STOLEN_NAME.equals(name.getString())) {
				check(DoomFinalEncounter.packStoneMask(stack) == 0b10101, "The returned gauntlet lost its stones");
				count++;
			}
		}
		return count;
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		TestWorldSave save;
		try (var world = ctx.worldBuilder().create()) {
			world.getClientLevel().waitForChunksRender();
			var server = world.getServer();
			server.runCommand("gamemode creative @p");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				LatveriaQuests.handleDialogAction(p, -1, LatveriaQuests.ACTION_CLAIM_DISRUPTOR);
				check(p.getInventory().countItem(ModItems.GENERATOR_DISRUPTOR) == 0,
						"An unsolicited dialog packet granted the disruptor in the Overworld");

				ItemStack stolen = DoomFinalEncounter.buildGauntletFromMask(0b10101);
				stolen.set(DataComponents.CUSTOM_NAME, Component.literal(STOLEN_NAME));
				DoomFinalEncounter.onGauntletStolen(s, p, stolen);
				DoomFinalEncounter.debugTpArena(p);
			});
			ctx.waitTicks(145);
			server.runOnServer(s -> {
				var at = Vec3.atBottomCenterOf(DoomFinalEncounter.arenaPosition(s)).add(0, 1, 12);
				player(s).teleportTo(at.x, at.y, at.z);
			});
			ctx.waitTicks(235);
			check(!ctx.computeOnClient(mc -> com.dantemorrigan.gauntlet.client.boss.ThanosClient.hasBossBar()),
					"Thanos displayed a health bar during Doom's duel");
			server.runOnServer(s -> check(DoomFinalEncounter.debugForceBreakDome(player(s)), "Doom's dome did not start"));
			ctx.waitTicks(140);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				var actors = s.overworld().getEntitiesOfClass(DoctorDoom.class, p.getBoundingBox().inflate(80), DoctorDoom::isAlive);
				check(actors.size() == 1 && !actors.getFirst().fighting(), "Doom attacked players before the execution scene ended");
			});
			ctx.waitTicks(120);
			server.runOnServer(s -> {
				var center = Vec3.atBottomCenterOf(DoomFinalEncounter.arenaPosition(s)).add(0, 1, 0);
				var actors = s.overworld().getEntitiesOfClass(DoctorDoom.class, player(s).getBoundingBox().inflate(80), DoctorDoom::finalBoss);
				check(actors.size() == 1, "No unique final Doom to test");
				actors.getFirst().teleportTo(center.x, center.y - 6, center.z);
			});
			ctx.waitTicks(20);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				var bosses = s.overworld().getEntitiesOfClass(DoctorDoom.class, p.getBoundingBox().inflate(80), DoctorDoom::finalBoss);
				check(bosses.size() == 1, "No unique final Doom to defeat");
				check(bosses.getFirst().getY() > DoomFinalEncounter.arenaPosition(s).getY() - 2
						&& !bosses.getFirst().isInWall(), "Doom stayed trapped under the arena floor");
				p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.GAUNTLET));
				bosses.getFirst().die(p.damageSources().playerAttack(p));
				check(DoomFinalEncounter.currentStage(s) == DoomFinalEncounter.STAGE_VICTORY_CUTSCENE, "Victory did not start");
				check(returnedCount(p) == 0, "Gauntlet returned before the victory scene");
			});
			save = world.getWorldSave();
		}

		for (int attempt = 0; attempt < 2; attempt++) {
			try (var world = save.open()) {
				world.getClientLevel().waitForChunksRender();
				ctx.waitTicks(30);
				world.getServer().runOnServer(s -> {
					ServerPlayer p = player(s);
					check(DoomFinalEncounter.currentStage(s) == DoomFinalEncounter.STAGE_DONE, "Victory was not settled on restart");
					check(returnedCount(p) == 1, "The stolen stack was not returned exactly once across restarts");
					check(p.getOffhandItem().is(ModItems.GAUNTLET)
							&& DoomFinalEncounter.packStoneMask(p.getOffhandItem()) == 0, "The existing offhand gauntlet was overwritten");
				});
				save = world.getWorldSave();
			}
		}
	}
}
