package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.ModComponents;
import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.boss.BossFlight;
import com.dantemorrigan.gauntlet.boss.ThanosEncounter;
import com.dantemorrigan.gauntlet.cosmos.CosmosTravel;
import com.dantemorrigan.gauntlet.doom.DoctorDoom;
import com.dantemorrigan.gauntlet.doom.DoomArmor;
import com.dantemorrigan.gauntlet.doom.DoomFinalEncounter;
import com.dantemorrigan.gauntlet.doom.Latveria;
import com.dantemorrigan.gauntlet.doom.LatveriaQuests;
import com.dantemorrigan.gauntlet.power.SpaceStone;
import com.dantemorrigan.gauntlet.stormbreaker.StormNetwork;
import com.dantemorrigan.gauntlet.stormbreaker.Stormbreaker;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.portal.TeleportTransition;
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
				ItemStack burned = new ItemStack(ModItems.GAUNTLET);
				burned.set(ModComponents.SNAPPED, true);
				p.setItemInHand(InteractionHand.OFF_HAND, burned);
				p.getInventory().add(stolen);
				var latveria = s.getLevel(Latveria.LEVEL);
				DoctorDoom actor = ModEntities.DOCTOR_DOOM.create(latveria, EntitySpawnReason.EVENT);
				check(actor != null && actor.stealGauntletAndFlee(latveria, p), "Doom could not steal the working gauntlet");
				check(Boolean.TRUE.equals(p.getOffhandItem().get(ModComponents.SNAPPED)) && returnedCount(p) == 0,
						"Doom stole the burned gauntlet instead of the working one in the inventory");
				check(!ThanosEncounter.start(p, false) && ThanosEncounter.spawnNow(p) == null,
						"A second Thanos encounter could start during Doom's storyline");
				DoomFinalEncounter.debugTpArena(p);
			});
			ctx.waitFor(mc -> theftTitleShown(mc.gui), 55);
			ctx.waitTicks(145);
			server.runOnServer(s -> {
				check(Latveria.gateOpen(s) && player(s).getInventory().countItem(ModItems.LATVERIA_KEY) == 1,
						"Leaving Latveria during the theft prevented the gates or key from unlocking");
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
				if (attempt == 1) cosmicEquipment(ctx, world.getServer());
				save = world.getWorldSave();
			}
		}
	}

	private static boolean theftTitleShown(net.minecraft.client.gui.Gui gui) {
		try {
			var field = net.minecraft.client.gui.Gui.class.getDeclaredField("title");
			field.setAccessible(true);
			return field.get(gui) instanceof Component title
					&& title.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
					&& text.getKey().equals("hud.infinity_gauntlet.doom.theft_title");
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError("Could not read the vanilla title for the theft alert", exception);
		}
	}

	private static void cosmicEquipment(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("difficulty normal");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.setItemInHand(InteractionHand.OFF_HAND, DoomFinalEncounter.buildGauntletFromMask(1 << Stone.SPACE.ordinal()));
			CosmosTravel.action(p, 0);
			p.teleport(new TeleportTransition(s.getLevel(CosmosTravel.DIMENSION), new Vec3(30000, 256, 500),
					Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
			p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.DOOM_HELMET));
			p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.DOOM_CHESTPLATE));
			p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.DOOM_LEGGINGS));
			p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.DOOM_BOOTS));
			ItemStack axe = new ItemStack(ModItems.STORMBREAKER);
			Stormbreaker.finishCraft(p, axe);
			p.setItemInHand(InteractionHand.MAIN_HAND, axe);
		});
		ctx.waitTicks(25);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			check(BossFlight.airborne(p), "The player was not in flight in the cosmos");
			p.setDeltaMovement(new Vec3(1, 0, 0));
			BossFlight.interrupt(p, 1);
			check(p.getDeltaMovement().length() < .3 && p.getAbilities().flying && p.hasAttached(CosmosTravel.FLIGHT),
					"An aerial hit disabled space flight instead of slowing the player");
			SpaceStone.action(p, 2);
			check(p.getZ() > 520, "The Space Stone blink required a floor");
			double previous = p.getZ();
			DoomArmor.cast(p, DoomArmor.RIFT);
			check(p.getZ() > previous + 15, "Doom's Rift Step required a floor");
			DoomArmor.cast(p, DoomArmor.JET);
			check(p.getDeltaMovement().length() > .5 && p.getAbilities().flying && !DoomArmor.jetsActive(p),
					"Doom's space jet did not provide a burst alongside gauntlet flight");
			p.setDeltaMovement(Vec3.ZERO);
		});
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> ClientPlayNetworking.send(new StormNetwork.Action(StormNetwork.THROW)));
		ctx.waitTicks(14);
		server.runOnServer(s -> check(player(s).getInventory().countItem(ModItems.STORMBREAKER) == 0,
				"The axe did not leave the inventory when thrown in space"));
		ctx.waitTicks(55);
		LivingEntity target = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			check(p.getInventory().countItem(ModItems.STORMBREAKER) == 1, "The axe failed to return exactly once in space");
			var dummy = EntityType.ZOMBIE.create(p.level(), EntitySpawnReason.EVENT);
			Vec3 at = p.getEyePosition().add(p.getLookAngle().scale(14)).add(0, -.8, 0);
			dummy.setPos(at.x, at.y, at.z);
			dummy.setNoAi(true);
			dummy.setNoGravity(true);
			check(p.level().addFreshEntity(dummy), "Could not spawn an Edict target");
			DoomArmor.cast(p, DoomArmor.EDICT);
			return dummy;
		});
		ctx.waitTicks(7);
		// The test harness forces an extra render without giving Sodium a terrain extraction pass.
		if (System.getenv("INFINITY_COMPAT_MODS") == null) ctx.takeScreenshot("cosmic_doom_edict");
		ctx.waitTicks(20);
		server.runOnServer(s -> check(target.getHealth() < target.getMaxHealth(),
				"Doom's Latverian Edict missed a target suspended in empty space"));
	}
}
