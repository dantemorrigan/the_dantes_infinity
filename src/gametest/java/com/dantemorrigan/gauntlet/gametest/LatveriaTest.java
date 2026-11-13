package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.client.doom.DoomClient;
import com.dantemorrigan.gauntlet.doom.DoctorDoom;
import com.dantemorrigan.gauntlet.doom.DoomArmor;
import com.dantemorrigan.gauntlet.doom.Doombot;
import com.dantemorrigan.gauntlet.doom.Latveria;
import com.dantemorrigan.gauntlet.doom.LatveriaPlan;
import com.dantemorrigan.gauntlet.ModItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * The Fury of Doom from the first arrival to the reward: the title card and the valley, the road and the gate squad,
 * the laboratory route, Doom's three stages with the Edict, the spoils, the Doombots changing sides and the armor's
 * wheel. Also checks that dying resets the fight and that winning twice does not pay twice.
 */
public class LatveriaTest implements FabricClientGameTest {
	private static final AABB WORLD = new AABB(-400, 0, -500, 400, 320, 400);

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static ServerLevel latveria(MinecraftServer s) {
		return s.getLevel(Latveria.LEVEL);
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}

	private static void look(TestServerContext server, ClientGameTestContext ctx, double x, double y, double z, float yaw, float pitch, int wait) {
		server.runCommand("execute in infinity_gauntlet:latveria run tp @p " + x + " " + y + " " + z + " " + yaw + " " + pitch);
		ctx.waitTicks(wait);
	}

	/** Puts the player a few blocks in front of Doom, looking at him. */
	private static void faceDoom(TestServerContext server, ClientGameTestContext ctx, double distance) {
		server.runOnServer(s -> {
			DoctorDoom d = Latveria.boss();
			ServerPlayer p = player(s);
			var to = new net.minecraft.world.phys.Vec3(0, 0, 1).scale(distance);
			double z = Math.min(d.getZ() + to.z, -255.5);
			p.teleportTo(d.getX(), LatveriaPlan.FLOOR, z);
			p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, d.position().add(0, 1.5, 0));
		});
		ctx.waitTicks(6);
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		LegacyBindings.disable(ctx);
		net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave save;
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			TestServerContext server = sp.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("weather clear");
			server.runCommand("time set 6000");
			server.runCommand("gamemode creative @p");

			// ---- The way in is closed until the seal is read.
			check(!server.computeOnServer(s -> Latveria.hasAccess(player(s))), "A new player must not have access to Latveria");
			server.runCommand("execute as @p run gauntlet latveria enter");
			ctx.waitTicks(30);
			check(server.computeOnServer(s -> player(s).level().dimension() == Latveria.LEVEL), "The player did not arrive in Latveria");
			ctx.takeScreenshot("latveria_01_logo_fade_in");
			ctx.waitTicks(60);
			check(ctx.computeOnClient(mc -> DoomClient.logoShowing()), "The title card is not shown on the first arrival");
			ctx.takeScreenshot("latveria_02_logo");
			ctx.waitTicks(150);
			check(!ctx.computeOnClient(mc -> DoomClient.logoShowing()), "The title card did not go away");
			// Headless runs have no sound device, so this checks which theme the client started, not what is heard.
			String music = ctx.computeOnClient(mc -> DoomClient.lastTheme());
			check(music.startsWith("echoes_of_latveria") || music.startsWith("sovereign_sanctum"), "Latveria's ambient theme is not playing: " + music);
			sp.getClientLevel().waitForChunksRender();
			ctx.takeScreenshot("latveria_03_arrival");

			// ---- The valley, the city, the road and the castle.
			check(server.computeOnServer(s -> !latveria(s).getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class, WORLD).isEmpty()), "The city has no townsfolk");
			server.runCommand("gamemode spectator @p");
			look(server, ctx, 0.5, 118, 190.5, 180, 28, 80);
			sp.getClientLevel().waitForChunksRender();
			ctx.takeScreenshot("latveria_04_valley_from_above");
			look(server, ctx, 0.5, 72, 48.5, 180, 4, 40);
			sp.getClientLevel().waitForChunksRender();
			ctx.takeScreenshot("latveria_05_city_square");
			look(server, ctx, 0.5, 100, -120.5, 180, 12, 60);
			sp.getClientLevel().waitForChunksRender();
			ctx.takeScreenshot("latveria_06_road_and_castle");
			look(server, ctx, 70.5, 160, -180.5, 135, 38, 80);
			sp.getClientLevel().waitForChunksRender();
			ctx.takeScreenshot("latveria_07_castle_from_above");
			look(server, ctx, 0.5, LatveriaPlan.FLOOR + 6, -256.5, 180, 8, 60);
			sp.getClientLevel().waitForChunksRender();
			ctx.takeScreenshot("latveria_08_throne_room");
			look(server, ctx, -34.5, LatveriaPlan.FLOOR + 11.5, -268.5, 110, 20, 40);
			ctx.takeScreenshot("latveria_09_bedroom");
			look(server, ctx, 28.5, LatveriaPlan.FLOOR + 3, -262.5, 200, 15, 40);
			ctx.takeScreenshot("latveria_10_laboratory");
			check(server.computeOnServer(s -> latveria(s).getBlockState(new BlockPos(0, LatveriaPlan.FLOOR + 2, -205)).is(Blocks.IRON_BARS)), "The portcullis is missing");
			check(server.computeOnServer(s -> latveria(s).getBlockState(new BlockPos(-41, LatveriaPlan.FLOOR, -243)).getBlock() instanceof net.minecraft.world.level.block.BedBlock),
					"The barracks have no beds");
			check(server.computeOnServer(s -> Latveria.boss() == null), "A spectator must not wake Doom");

			// ---- The direct assault: the gate squad holds the portcullis.
			server.runCommand("gamemode survival @p");
			server.runCommand("effect give @p minecraft:resistance 9999 3 true");
			server.runCommand("effect give @p minecraft:regeneration 9999 3 true");
			server.runCommand("execute as @p run gauntlet latveria gate");
			ctx.waitTicks(60);
			int gate = server.computeOnServer(s -> latveria(s).getEntitiesOfClass(Doombot.class, WORLD, b -> b.squad() == Latveria.SQUAD_GATE).size());
			check(gate == 5, "The gate squad should be five Doombots, found " + gate);
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 180 0");
			ctx.waitTicks(50);
			ctx.takeScreenshot("latveria_11_gate_squad");
			ctx.waitTicks(60);
			ctx.takeScreenshot("latveria_12_gate_squad_fighting");
			server.runCommand("gauntlet latveria clear 1");
			ctx.waitTicks(70);
			check(server.computeOnServer(s -> latveria(s).getBlockState(new BlockPos(0, LatveriaPlan.FLOOR + 2, -205)).isAir()), "The portcullis did not open after the gate squad fell");
			ctx.takeScreenshot("latveria_13_gate_open");

			// ---- The yard: interceptors overhead; clearing it opens the throne room.
			look(server, ctx, 0.5, LatveriaPlan.FLOOR, -212.5, 180, -10, 80);
			int yard = server.computeOnServer(s -> latveria(s).getEntitiesOfClass(Doombot.class, WORLD, b -> b.squad() == Latveria.SQUAD_YARD).size());
			check(yard == 7, "The yard squad should be seven Doombots, found " + yard);
			ctx.takeScreenshot("latveria_14_yard_squad");
			ctx.waitTicks(60);
			ctx.takeScreenshot("latveria_15_yard_fight");
			check(server.computeOnServer(s -> latveria(s).getBlockState(new BlockPos(0, LatveriaPlan.FLOOR + 2, -253)).is(Blocks.GREEN_STAINED_GLASS)), "The throne room must be sealed while the yard stands");

			// ---- The other way: the service door, the laboratory, the generator.
			server.runCommand("execute as @p run gauntlet latveria lab");
			ctx.waitTicks(40);
			ctx.takeScreenshot("latveria_16_service_door");
			look(server, ctx, 36.5, LatveriaPlan.FLOOR, -280.5, 30, 0, 60);
			ctx.takeScreenshot("latveria_17_generator");
			check(server.computeOnServer(s -> Latveria.shutGenerator(player(s))), "The generator did not switch off");
			ctx.waitTicks(30);
			check(server.computeOnServer(s -> Latveria.generatorOff(s) && latveria(s).getBlockState(new BlockPos(22, LatveriaPlan.FLOOR + 1, -261)).isAir()),
					"Switching the generator off must open the side door to the throne room");
			ctx.takeScreenshot("latveria_18_generator_off");

			// ---- The throne room: Doom's entrance, the boss bar, the first stage.
			server.runCommand("execute as @p run gauntlet latveria throne");
			ctx.waitTicks(40);
			check(server.computeOnServer(s -> Latveria.boss() != null && Latveria.boss().action() == DoctorDoom.A_INTRO), "Doom did not rise when a challenger entered");
			ctx.takeScreenshot("latveria_19_doom_entrance");
			ctx.waitTicks(45);
			ctx.takeScreenshot("latveria_20_doom_entrance_line");
			for (int i = 0; i < 40 && !server.computeOnServer(s -> Latveria.boss().fighting()); i++) ctx.waitTicks(5);
			check(server.computeOnServer(s -> Latveria.boss().fighting()), "The fight did not begin after the entrance");
			check(ctx.computeOnClient(mc -> DoomClient.lastTheme()).equals("boss"), "The boss theme was not started");
			check(server.computeOnServer(s -> latveria(s).getEntitiesOfClass(Doombot.class, LatveriaPlan.ARENA_BOX, b -> b.variant() == Doombot.ROYAL_GUARD).size()) == 2,
					"Two Royal Guards should stand by the throne");
			faceDoom(server, ctx, 6);
			ctx.takeScreenshot("latveria_21_fight_stage_1");
			ctx.waitTicks(50);
			faceDoom(server, ctx, 7);
			ctx.takeScreenshot("latveria_22_fight_stage_1_attack");
			float before = server.computeOnServer(s -> Latveria.boss().getHealth());
			server.runOnServer(s -> Latveria.boss().hurtServer(latveria(s), latveria(s).damageSources().playerAttack(player(s)), 20));
			float lost = before - server.computeOnServer(s -> Latveria.boss().getHealth());
			check(lost > 0 && lost < 45, "A blow must hurt Doom within the cap, lost " + lost);

			// ---- Stage two: rifts and sorcery.
			server.runCommand("gauntlet doom phase2");
			for (int i = 0; i < 60 && server.computeOnServer(s -> Latveria.boss().phase()) < 2; i++) ctx.waitTicks(5);
			check(server.computeOnServer(s -> Latveria.boss().phase()) == 2, "Doom did not enter the second stage");
			ctx.waitTicks(20);
			ctx.takeScreenshot("latveria_23_stage_2_shift");
			ctx.waitTicks(90);
			faceDoom(server, ctx, 7);
			ctx.waitTicks(20);
			ctx.takeScreenshot("latveria_24_stage_2_sorcery");

			// ---- Stage three: the Edict, a broken seal node and a safe sector.
			server.runOnServer(s -> latveria(s).getEntitiesOfClass(Doombot.class, LatveriaPlan.ARENA_BOX.inflate(6)).forEach(b -> b.discard()));
			server.runCommand("gauntlet doom phase3");
			for (int i = 0; i < 240 && server.computeOnServer(s -> Latveria.boss().action()) != DoctorDoom.A_EDICT; i++) ctx.waitTicks(5);
			check(server.computeOnServer(s -> Latveria.boss().action()) == DoctorDoom.A_EDICT, "Doom did not cast the Edict in the third stage");
			ctx.waitTicks(22);
			faceDoom(server, ctx, 12);
			ctx.takeScreenshot("latveria_25_edict_warning");
			check(server.computeOnServer(s -> latveria(s).getBlockState(LatveriaPlan.SEAL_NODES[0]).is(Blocks.SEA_LANTERN)), "The Edict's seal nodes did not appear");
			server.runCommand("effect clear @p");
			ctx.waitTicks(2);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.setHealth(p.getMaxHealth());
				BlockPos node = LatveriaPlan.SEAL_NODES[DoctorDoom.sectorOf(p.position())];
				if (!Latveria.boss().breakNode(latveria(s), node)) throw new AssertionError("Striking a seal node did not break it");
			});
			for (int i = 0; i < 40 && server.computeOnServer(s -> Latveria.boss().action()) == DoctorDoom.A_EDICT
					&& !server.computeOnServer(s -> Latveria.boss().flag(DoctorDoom.FLAG_EXPOSED)); i++) ctx.waitTicks(3);
			ctx.takeScreenshot("latveria_26_edict_blast");
			check(server.computeOnServer(s -> player(s).isAlive() && player(s).getHealth() >= 19f), "The safe sector did not protect from the Edict");
			check(server.computeOnServer(s -> Latveria.boss().flag(DoctorDoom.FLAG_EXPOSED)), "Doom must be exposed after the Edict");
			check(server.computeOnServer(s -> latveria(s).getBlockState(LatveriaPlan.SEAL_NODES[1]).isAir()), "Seal nodes must be gone after the Edict");
			server.runCommand("effect give @p minecraft:resistance 9999 4 true");

			// ---- A hard blow interrupts the next Edict; the Time Stone and the Space Stone open their windows.
			for (int i = 0; i < 400 && !(server.computeOnServer(s -> Latveria.boss().action()) == DoctorDoom.A_EDICT
					&& server.computeOnServer(s -> Latveria.boss().flag(DoctorDoom.FLAG_CASTING))); i++) ctx.waitTicks(5);
			check(server.computeOnServer(s -> Latveria.boss().action()) == DoctorDoom.A_EDICT, "Doom did not cast a second Edict");
			ctx.waitTicks(15);
			server.runOnServer(s -> {
				Latveria.boss().invulnerableTime = 0;
				// One blow of Stormbreaker breaks the cast, however small.
				var axe = latveria(s).registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
						.getOrThrow(com.dantemorrigan.gauntlet.stormbreaker.Stormbreaker.DAMAGE);
				Latveria.boss().hurtServer(latveria(s), new net.minecraft.world.damagesource.DamageSource(axe, player(s)), 6);
			});
			ctx.waitTicks(3);
			check(server.computeOnServer(s -> Latveria.boss().action()) == DoctorDoom.A_STAGGER, "Stormbreaker did not interrupt the Edict");
			ctx.takeScreenshot("latveria_27_edict_interrupted");
			ctx.waitTicks(80);
			server.runOnServer(s -> Latveria.boss().timeLock(latveria(s)));
			check(server.computeOnServer(s -> Latveria.boss().action() == DoctorDoom.A_STAGGER && Latveria.boss().flag(DoctorDoom.FLAG_EXPOSED)),
					"The Time Stone did not open a window on Doom");
			check(server.computeOnServer(s -> {
				com.dantemorrigan.gauntlet.doom.DoomCombat.dodge(player(s));
				return !com.dantemorrigan.gauntlet.doom.DoomCombat.hurt(latveria(s), player(s), Latveria.boss(), 5);
			}), "A Space Stone blink did not let the player slip through an attack");

			// ---- Death calls the fight off; coming back starts it afresh.
			server.runCommand("effect clear @p");
			server.runCommand("kill @p");
			ctx.waitTicks(20);
			ctx.runOnClient(mc -> mc.player.respawn());
			ctx.waitTicks(140);
			check(server.computeOnServer(s -> Latveria.boss() == null), "Doom must leave when every challenger is dead");
			check(server.computeOnServer(s -> !Latveria.conquered(s)), "Dying must not conquer the castle");
			server.runCommand("effect give @p minecraft:resistance 9999 4 true");
			server.runCommand("execute as @p run gauntlet latveria throne");
			ctx.waitTicks(60);
			check(server.computeOnServer(s -> Latveria.boss() != null && Latveria.boss().getHealth() == Latveria.boss().getMaxHealth() && Latveria.boss().phase() == 1),
					"The fight did not restart from the beginning");
			for (int i = 0; i < 40 && !server.computeOnServer(s -> Latveria.boss().fighting()); i++) ctx.waitTicks(5);

			// ---- Victory: the spoils, the castle, the Doombots.
			server.runCommand("gauntlet doom kill");
			ctx.waitTicks(40);
			ctx.takeScreenshot("latveria_28_doom_defeated");
			ctx.waitTicks(90);
			check(server.computeOnServer(s -> Latveria.conquered(s) && Latveria.isOwner(player(s))), "Beating Doom must give the castle to the victor");
			check(server.computeOnServer(s -> player(s).getInventory().countItem(ModItems.DOOM_HELMET)) == 1, "The victor did not get Doom's armor");
			check(server.computeOnServer(s -> player(s).getInventory().countItem(ModItems.LATVERIA_KEY)) == 1, "The victor did not get the Latveria Key");
			check(server.computeOnServer(s -> latveria(s).getEntitiesOfClass(Doombot.class, WORLD, b -> b.isAlive() && !b.allied()).isEmpty()),
					"Surviving Doombots must change sides");
			check(server.computeOnServer(s -> !latveria(s).getEntitiesOfClass(Doombot.class, WORLD, Doombot::allied).isEmpty()), "The victor has no Doombots");
			ctx.takeScreenshot("latveria_29_castle_taken");
			// Winning again (after a debug reset) must not pay again.
			server.runCommand("gauntlet latveria reset");
			ctx.waitTicks(40);
			for (int i = 0; i < 60 && !server.computeOnServer(s -> Latveria.boss() != null && Latveria.boss().fighting()); i++) ctx.waitTicks(5);
			server.runCommand("gauntlet doom kill");
			ctx.waitTicks(130);
			check(server.computeOnServer(s -> Latveria.conquered(s)), "The second victory was not recorded");
			check(server.computeOnServer(s -> player(s).getInventory().countItem(ModItems.DOOM_HELMET)) == 1, "The reward was paid twice");

			// ---- The armor: the wheel, the strip of cooldowns and the six powers.
			server.runCommand("effect clear @p");
			server.runCommand("execute as @p run gauntlet doom armor");
			server.runCommand("execute in infinity_gauntlet:latveria run tp @p 0.5 " + LatveriaPlan.FLOOR + " -214.5 180 0");
			ctx.waitTicks(30);
			check(ctx.computeOnClient(mc -> DoomClient.fullSet()), "The client does not know the full Doom armor is worn");
			ctx.getInput().holdKey(org.lwjgl.glfw.GLFW.GLFW_KEY_M);
			ctx.waitFor(mc -> mc.screen instanceof com.dantemorrigan.gauntlet.client.doom.DoomWheelScreen, 100);
			ctx.waitTicks(10);
			ctx.takeScreenshot("latveria_30_armor_wheel");
			ctx.getInput().releaseKey(org.lwjgl.glfw.GLFW.GLFW_KEY_M);
			ctx.waitFor(mc -> mc.screen == null, 100);
			// The Infinity Gauntlet keeps its own wheel on its own key.
			server.runCommand("item replace entity @p weapon.offhand with infinity_gauntlet:gauntlet");
			ctx.waitTicks(20);
			ctx.getInput().holdKey(org.lwjgl.glfw.GLFW.GLFW_KEY_V);
			ctx.waitFor(mc -> mc.screen instanceof com.dantemorrigan.gauntlet.client.ability.AbilityWheelScreen, 100);
			ctx.getInput().releaseKey(org.lwjgl.glfw.GLFW.GLFW_KEY_V);
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> mc.setScreen(null));
			ctx.waitTicks(5);
			ctx.takeScreenshot("latveria_31_armor_and_gauntlet_hud");
			double z0 = server.computeOnServer(s -> player(s).getZ());
			server.runOnServer(s -> DoomArmor.cast(player(s), DoomArmor.RIFT));
			ctx.waitTicks(5);
			check(server.computeOnServer(s -> player(s).getZ()) < z0 - 8, "Rift Step did not carry the wearer forward");
			float hp = server.computeOnServer(s -> player(s).getHealth());
			server.runOnServer(s -> {
				DoomArmor.cast(player(s), DoomArmor.BASTION);
				player(s).hurtServer(latveria(s), latveria(s).damageSources().magic(), 10);
			});
			float taken = hp - server.computeOnServer(s -> player(s).getHealth());
			check(taken > 0 && taken <= 3f, "Iron Bastion should stop four fifths of a blow, took " + taken);
			server.runOnServer(s -> { DoomArmor.cast(player(s), DoomArmor.BOLT); DoomArmor.cast(player(s), DoomArmor.SUNS); DoomArmor.cast(player(s), DoomArmor.EDICT); });
			ctx.waitTicks(50);
			ctx.takeScreenshot("latveria_32_armor_powers");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 180 -70");
			double y0 = server.computeOnServer(s -> player(s).getY());
			server.runOnServer(s -> DoomArmor.cast(player(s), DoomArmor.JET));
			ctx.waitTicks(30);
			check(server.computeOnServer(s -> player(s).getY()) > y0 + 6, "Jet Flight did not lift the wearer");
			check(!server.computeOnServer(s -> player(s).getAbilities().mayfly), "Jet Flight must not touch the player's flight ability");
			ctx.takeScreenshot("latveria_33_jet_flight");
			server.runOnServer(s -> DoomArmor.cast(player(s), DoomArmor.JET));
			server.runCommand("execute in infinity_gauntlet:latveria run tp @p 0.5 " + LatveriaPlan.FLOOR + " -258.5 180 0");
			server.runCommand("execute in infinity_gauntlet:latveria run setblock 5 " + LatveriaPlan.FLOOR + " -270 minecraft:diamond_block");
			ctx.waitTicks(40);
			save = sp.getWorldSave();
		}

		// ---- After a restart: the castle is still taken, what was built stands, the key leads home and back.
		try (var sp = save.open()) {
			sp.getClientLevel().waitForChunksRender();
			TestServerContext server = sp.getServer();
			check(server.computeOnServer(s -> Latveria.conquered(s) && Latveria.isOwner(player(s))), "The castle was lost over a restart");
			check(server.computeOnServer(s -> latveria(s).getBlockState(new BlockPos(5, LatveriaPlan.FLOOR, -270)).is(Blocks.DIAMOND_BLOCK)), "A block built in the castle did not survive a restart");
			check(server.computeOnServer(s -> latveria(s).getBlockState(new BlockPos(0, LatveriaPlan.FLOOR + 2, -205)).isAir()), "The portcullis closed again after a restart");
			ctx.waitTicks(40);
			check(server.computeOnServer(s -> Latveria.boss() == null), "Doom must not return to a castle that was taken");
			check(server.computeOnServer(s -> !latveria(s).getEntitiesOfClass(Doombot.class, WORLD, Doombot::allied).isEmpty()), "The victor's Doombots did not survive a restart");
			check(!ctx.computeOnClient(mc -> DoomClient.logoShowing()), "The title card must be shown only once");
			server.runOnServer(s -> Latveria.leave(player(s)));
			ctx.waitTicks(40);
			check(server.computeOnServer(s -> player(s).level().dimension() != Latveria.LEVEL), "The way out of Latveria did not work");
			// Without the armor, from another dimension, the owner still has the way in.
			server.runOnServer(s -> {
				for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) player(s).setItemSlot(slot, ItemStack.EMPTY);
				if (!Latveria.enter(player(s), true)) throw new AssertionError("The owner could not return to Latveria");
			});
			ctx.waitTicks(60);
			check(server.computeOnServer(s -> player(s).level().dimension() == Latveria.LEVEL && player(s).position().distanceTo(LatveriaPlan.CASTLE_HALL) < 4),
					"The owner should arrive in the castle hall");
			sp.getClientLevel().waitForChunksRender();
			ctx.takeScreenshot("latveria_34_home_after_restart");
			ctx.waitTicks(20);
		}
	}
}
