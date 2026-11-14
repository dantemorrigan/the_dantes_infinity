package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.client.ability.AbilityWheelScreen;
import com.dantemorrigan.gauntlet.client.doom.DoomWheelScreen;
import com.dantemorrigan.gauntlet.client.lang.ModLang;
import com.dantemorrigan.gauntlet.doom.Latveria;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;

/**
 * A walk through every screen and message of the add-on in Russian (the longer language), with the Infinity Gauntlet
 * worn alongside Doom's armor: both HUDs together, both wheels, the alarm, Doom's entrance subtitles, the fight HUD,
 * the siphon warning, the decoys and the ascension's titles. Screenshots for review; checks only what can be checked.
 */
public class DoomUxTest implements FabricClientGameTest {
	private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

	private static void clearChat(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> mc.gui.getChat().clearMessages(false));
	}

	private static void shot(ClientGameTestContext ctx, String name) {
		ctx.takeScreenshot("ux_ru_" + name);
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		LegacyBindings.disable(ctx);
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			TestServerContext server = sp.getServer();
			ctx.runOnClient(mc -> ModLang.set("ru"));
			// Every add-on item explains itself in its tooltip, in both languages.
			for (String lang : new String[]{"en", "ru"}) {
				ctx.runOnClient(mc -> ModLang.set(lang));
				String tips = ctx.computeOnClient(mc -> {
					StringBuilder out = new StringBuilder();
					for (var item : new net.minecraft.world.item.Item[]{com.dantemorrigan.gauntlet.ModItems.MECHANICAL_HEART, com.dantemorrigan.gauntlet.ModItems.LATVERIA_KEY,
							com.dantemorrigan.gauntlet.ModItems.DOOM_HELMET, com.dantemorrigan.gauntlet.ModItems.DOOMBOT_CONTROLLER}) {
						var lines = new net.minecraft.world.item.ItemStack(item).getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player,
								net.minecraft.world.item.TooltipFlag.NORMAL);
						if (lines.size() < 2) throw new AssertionError("No tooltip for " + item);
						for (var l : lines) out.append(l.getString()).append(" | ");
						out.append('\n');
					}
					return out.toString();
				});
				System.out.println("TOOLTIPS " + lang + "\n" + tips);
			}
			ctx.runOnClient(mc -> ModLang.set("ru"));
			server.runCommand("gamerule show_advancement_messages false");
			server.runCommand("gamemode creative @p");
			server.runCommand("execute as @p run gauntlet latveria enter");
			ctx.waitTicks(270);

			// Both kits at once: Doom's armor and the whole Infinity Gauntlet. Survival, so every bar shows.
			server.runCommand("execute as @p run gauntlet doom armor");
			server.runCommand("execute as @p run gauntlet full");
			server.runCommand("gamemode survival @p");
			server.runCommand("effect give @p minecraft:resistance 600 4 true");
			server.runCommand("execute as @p run gauntlet latveria tp yard");
			ctx.waitTicks(60);
			ctx.runOnClient(mc -> mc.getToastManager().clear());
			clearChat(ctx);
			shot(ctx, "01_huds_together");

			ctx.runOnClient(mc -> mc.setScreen(new DoomWheelScreen()));
			ctx.waitTicks(6);
			shot(ctx, "02_doom_wheel");
			ctx.runOnClient(mc -> mc.setScreen(new AbilityWheelScreen()));
			ctx.waitTicks(6);
			shot(ctx, "03_gauntlet_wheel");
			ctx.runOnClient(mc -> mc.setScreen(null));
			ctx.waitTicks(4);

			// The alarm, in the open.
			server.runCommand("execute as @p run gauntlet latveria tp road");
			ctx.waitTicks(30);
			clearChat(ctx);
			server.runCommand("execute as @p run gauntlet latveria alarm");
			ctx.waitTicks(10);
			shot(ctx, "04_alarm");

			// Doom's entrance: the letterbox and the Russian subtitles.
			server.runCommand("execute as @p run gauntlet latveria unlock");
			server.runCommand("execute as @p run gauntlet latveria tp hall");
			ctx.waitTicks(30);
			server.runCommand("execute as @p run gauntlet doom spawn");
			clearChat(ctx);
			ctx.waitTicks(70);
			shot(ctx, "05_entrance_subtitle");
			ctx.waitTicks(80);
			server.runCommand("execute as @p run gauntlet doom passive");
			clearChat(ctx);
			ctx.waitTicks(10);
			shot(ctx, "06_fight_hud");

			// Stage two's decoys and his taunt.
			server.runCommand("execute as @p run gauntlet doom action decoy_gambit");
			ctx.waitTicks(40);
			shot(ctx, "07_decoys");

			// The siphon on a gauntlet that is not whole: the warning in the gauntlet's own panel.
			server.runCommand("execute as @p run gauntlet give_power");
			clearChat(ctx);
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().getFirst();
				var d = Latveria.boss();
				p.teleportTo(d.getX(), d.getY(), d.getZ() + 8);
				p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, d.position().add(0, 1.6, 0));
			});
			server.runCommand("execute as @p run gauntlet doom action infinity_siphon");
			ctx.waitTicks(40);
			shot(ctx, "08_siphon_warning");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			ctx.waitTicks(4);
			shot(ctx, "09_siphon_pose");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			ctx.waitTicks(70);
			check(server.computeOnServer(s -> com.dantemorrigan.gauntlet.ability.AbilityController.reason(s.getPlayerList().getPlayers().getFirst(),
					com.dantemorrigan.gauntlet.ability.Ability.STRIKE)).equals("silenced"), "The siphon did not silence the gauntlet");
			clearChat(ctx);
			ctx.runOnClient(mc -> mc.setScreen(new AbilityWheelScreen()));
			ctx.waitTicks(6);
			shot(ctx, "10_wheel_silenced");
			ctx.runOnClient(mc -> mc.setScreen(null));

			// The ascension's Russian titles.
			server.runCommand("execute as @p run gauntlet doom ascend");
			ctx.waitTicks(130);
			shot(ctx, "11_god_title");
			ctx.waitTicks(300);
			shot(ctx, "12_battleworld");
			ctx.waitTicks(80);
			shot(ctx, "13_after");
			ctx.runOnClient(mc -> ModLang.set("auto"));
		}
	}
}
