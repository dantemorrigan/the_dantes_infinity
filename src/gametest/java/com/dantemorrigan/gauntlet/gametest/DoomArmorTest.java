package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.client.doom.DoomClient;
import com.dantemorrigan.gauntlet.client.doom.DoomVfxClient;
import com.dantemorrigan.gauntlet.doom.DoomArmor;
import com.dantemorrigan.gauntlet.doom.DoomVfx;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.lwjgl.glfw.GLFW;

/**
 * Doom's armor on the player next to Doctor Doom himself (front, side, back), first person, and each of the six
 * powers with its effect: bolt, shell, jets (flown by the client with real key presses), rift, suns, Edict.
 */
public class DoomArmorTest implements FabricClientGameTest {
	private static ServerPlayer player(MinecraftServer s) { return s.getPlayerList().getPlayers().getFirst(); }

	private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

	@Override
	public void runTest(ClientGameTestContext ctx) {
		LegacyBindings.disable(ctx);
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("weather clear");
			server.runCommand("time set 6000");
			server.runCommand("gamemode creative @p");
			server.runCommand("execute as @p at @s run fill ~-8 ~-1 ~-8 ~8 ~-1 ~8 minecraft:polished_deepslate");
			server.runCommand("execute as @p at @s run fill ~-8 ~ ~-8 ~8 ~6 ~8 minecraft:air");
			server.runCommand("execute as @p run gauntlet doom armor");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 180 0");
			// The real Doom beside the player, for comparison (he stands idle outside his throne room).
			server.runCommand("execute as @p at @s run summon infinity_gauntlet:doctor_doom ~1.6 ~ ~ 180 0 {NoAI:1b}");
			ctx.waitTicks(30);
			check(ctx.computeOnClient(mc -> DoomClient.fullSet()), "The client does not see the full armor");

			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			ctx.waitTicks(10);
			ctx.takeScreenshot("doom_armor_01_front_with_doom");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 90 0");
			ctx.waitTicks(10);
			ctx.takeScreenshot("doom_armor_02_side");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 0");
			ctx.waitTicks(10);
			ctx.takeScreenshot("doom_armor_03_back_cloak");
			server.runCommand("kill @e[type=infinity_gauntlet:doctor_doom]");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 180 10");
			ctx.waitTicks(10);
			ctx.takeScreenshot("doom_armor_04_first_person");
			// With the Infinity Gauntlet on the off hand: Doom's forearm and fingers give way to it.
			server.runCommand("execute as @p run gauntlet full");
			ctx.waitTicks(80);
			ctx.runOnClient(mc -> mc.gui.getChat().clearMessages(false));
			ctx.waitTicks(2);
			ctx.takeScreenshot("doom_armor_04b_gauntlet_first_person");
			ctx.runOnClient(mc -> mc.options.hideGui = true);
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 150 20");
			ctx.waitTicks(10);
			ctx.takeScreenshot("doom_armor_04c_gauntlet_front");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 60 0");
			ctx.waitTicks(10);
			ctx.takeScreenshot("doom_armor_04d_gauntlet_side");
			server.runCommand("item replace entity @p weapon.offhand with minecraft:air");
			ctx.runOnClient(mc -> mc.options.hideGui = false);
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 180 10");
			ctx.waitTicks(10);

			// Targets.
			server.runCommand("execute as @p at @s run summon minecraft:zombie ~ ~ ~-7 {NoAI:1b,PersistenceRequired:1b}");
			server.runCommand("execute as @p at @s run summon minecraft:zombie ~3 ~ ~-6 {NoAI:1b,PersistenceRequired:1b}");
			server.runCommand("execute as @p at @s run summon minecraft:zombie ~-3 ~ ~-6 {NoAI:1b,PersistenceRequired:1b}");
			server.runCommand("effect give @e[type=minecraft:zombie] minecraft:fire_resistance infinite 0 true");
			server.runCommand("gamemode survival @p");
			server.runCommand("effect give @p minecraft:resistance 9999 4 true");
			ctx.waitTicks(10);

			// The wheel only selects. Nothing is cast until the cast key is pressed.
			ctx.getInput().holdKey(GLFW.GLFW_KEY_M);
			ctx.waitFor(mc -> mc.screen instanceof com.dantemorrigan.gauntlet.client.doom.DoomWheelScreen, 100);
			ctx.takeScreenshot("doom_armor_05_wheel");
			ctx.getInput().pressKey(GLFW.GLFW_KEY_2);
			ctx.getInput().releaseKey(GLFW.GLFW_KEY_M);
			ctx.waitTicks(10);
			check(ctx.computeOnClient(mc -> DoomClient.selected()) == DoomArmor.BASTION, "The wheel did not select the Iron Bastion");
			check(ctx.computeOnClient(mc -> DoomVfxClient.count(DoomVfx.SHIELD)) == 0, "The wheel cast the power on release");
			ctx.runOnClient(mc -> DoomClient.select(DoomArmor.BOLT));
			ctx.waitTicks(10);
			check(ctx.computeOnClient(mc -> DoomVfxClient.count(DoomVfx.BOLT) + DoomVfxClient.count(DoomVfx.IMPACT)) == 0, "A bolt flew without the cast key");
			ctx.getInput().pressKey(GLFW.GLFW_KEY_N);
			ctx.waitTicks(2);
			check(ctx.computeOnClient(mc -> DoomVfxClient.count(DoomVfx.BOLT)) > 0 || ctx.computeOnClient(mc -> DoomVfxClient.count(DoomVfx.IMPACT)) > 0,
					"The bolt did not fly on the client");
			ctx.takeScreenshot("doom_armor_06_bolt");
			for (int i = 0; i < 3; i++) { ctx.getInput().pressKey(GLFW.GLFW_KEY_N); ctx.waitTicks(11); }
			ctx.waitTicks(2);
			ctx.takeScreenshot("doom_armor_07_bolt_impacts");

			// Iron Bastion.
			ctx.runOnClient(mc -> { DoomClient.select(DoomArmor.BASTION); DoomClient.cast(); });
			ctx.waitTicks(4);
			check(ctx.computeOnClient(mc -> DoomVfxClient.count(DoomVfx.SHIELD)) == 1, "The Bastion shell is not shown");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			ctx.waitTicks(6);
			ctx.takeScreenshot("doom_armor_08_bastion");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

			// Seven Suns: they orbit and wait. Each press of the cast key sends one at the crosshair; holding keeps them coming.
			float healthBefore = server.computeOnServer(s -> (float) s.getLevel(net.minecraft.world.level.Level.OVERWORLD)
					.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class, player(s).getBoundingBox().inflate(30)).stream().mapToDouble(z -> z.getHealth()).sum());
			ctx.runOnClient(mc -> { DoomClient.select(DoomArmor.SUNS); DoomClient.cast(); });
			ctx.waitTicks(8);
			check(ctx.computeOnClient(mc -> DoomVfxClient.count(DoomVfx.ORBIT)) == 1, "The suns do not orbit");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			ctx.waitTicks(40);
			ctx.takeScreenshot("doom_armor_09_suns_orbit");
			check(ctx.computeOnClient(mc -> DoomVfxClient.count(DoomVfx.ORBIT)) == 1 && server.computeOnServer(s -> s.getLevel(net.minecraft.world.level.Level.OVERWORLD)
					.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class, player(s).getBoundingBox().inflate(30), z -> true).stream().mapToDouble(z -> z.getHealth()).sum() >= healthBefore),
					"The suns left the orbit on their own");
			ctx.getInput().pressKey(GLFW.GLFW_KEY_N);
			ctx.waitTicks(3);
			ctx.takeScreenshot("doom_armor_10_suns_strike");
			ctx.getInput().holdKey(GLFW.GLFW_KEY_N);
			ctx.waitTicks(40);
			ctx.getInput().releaseKey(GLFW.GLFW_KEY_N);
			ctx.takeScreenshot("doom_armor_11_suns_volley");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

			// Edict: the aiming circle, then the seal and the column.
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 180 25");
			ctx.runOnClient(mc -> DoomClient.select(DoomArmor.EDICT));
			ctx.waitTicks(10);
			ctx.takeScreenshot("doom_armor_12a_edict_aim");
			ctx.getInput().pressKey(GLFW.GLFW_KEY_N);
			ctx.waitTicks(4);
			ctx.takeScreenshot("doom_armor_12_edict_seal");
			ctx.waitTicks(DoomArmor.EDICT_DELAY - 6);
			ctx.takeScreenshot("doom_armor_13_edict_column");
			check(ctx.computeOnClient(mc -> DoomVfxClient.count(DoomVfx.COLUMN)) == 1, "The Edict's column did not rise");

			// Rift Step.
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 180 0");
			double z0 = server.computeOnServer(s -> player(s).getZ());
			ctx.runOnClient(mc -> { DoomClient.select(DoomArmor.RIFT); DoomClient.cast(); });
			ctx.waitTicks(3);
			check(server.computeOnServer(s -> player(s).getZ()) < z0 - 4, "Rift Step did not move the wearer");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			ctx.takeScreenshot("doom_armor_14_rift");

			// Jets: the client flies with real keys.
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 180 -20");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			double y0 = server.computeOnServer(s -> player(s).getY());
			ctx.runOnClient(mc -> { DoomClient.select(DoomArmor.JET); DoomClient.cast(); });
			ctx.waitTicks(3);
			check(ctx.computeOnClient(mc -> DoomClient.jetActive()), "The jets did not light");
			ctx.getInput().holdKey(GLFW.GLFW_KEY_SPACE);
			ctx.getInput().holdKey(GLFW.GLFW_KEY_W);
			ctx.waitTicks(25);
			ctx.takeScreenshot("doom_armor_15_jets");
			ctx.getInput().releaseKey(GLFW.GLFW_KEY_SPACE);
			ctx.getInput().releaseKey(GLFW.GLFW_KEY_W);
			check(server.computeOnServer(s -> player(s).getY()) > y0 + 4, "The jets did not lift the wearer");
			check(!server.computeOnServer(s -> player(s).getAbilities().mayfly), "The jets must not touch the flight ability");
			ctx.waitTicks(20);
			check(server.computeOnServer(s -> player(s).getY()) > y0 + 3, "With no key held the wearer should hover");
			ctx.takeScreenshot("doom_armor_16_hover");
			ctx.runOnClient(mc -> { DoomClient.select(DoomArmor.JET); DoomClient.cast(); });
			ctx.waitTicks(60);
			check(server.computeOnServer(s -> player(s).isAlive() && player(s).getHealth() > 18), "Landing after the jets must not hurt");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		}
	}
}
