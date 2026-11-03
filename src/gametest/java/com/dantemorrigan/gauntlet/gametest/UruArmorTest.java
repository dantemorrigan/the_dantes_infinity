package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.entity.GauntletDoll;
import com.dantemorrigan.gauntlet.uru.GauntletDamage;
import com.dantemorrigan.gauntlet.uru.UruArmor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.EquipmentSlot;

/** Armor of Uru: looks (doll and player, front/back) and the full-gauntlet damage rules. */
public class UruArmorTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		LegacyBindings.disable(ctx);
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			TestServerContext server = sp.getServer();
			sp.getClientLevel().waitForChunksRender();
			server.runCommand("time set noon");
			server.runCommand("gamerule advance_time false");
			// Looks first (no stones yet: a full gauntlet awakening fills the air with particles).
			server.runCommand("execute as @p at @p run gauntlet uru");
			ctx.getInput().lookAt(0, 12);
			server.runCommand("execute as @p at @p run gauntlet doll uru");
			ctx.waitTicks(5);
			server.runCommand("data merge entity @e[type=infinity_gauntlet:gauntlet_doll,limit=1] {NoAI:1b,Rotation:[180f,0f]}");
			ctx.runOnClient(mc -> mc.options.hideGui = true);
			ctx.waitTicks(10);
			ctx.takeScreenshot("uru_01_doll_front");
			server.runCommand("data merge entity @e[type=infinity_gauntlet:gauntlet_doll,limit=1] {Rotation:[0f,0f]}");
			ctx.waitTicks(6);
			ctx.takeScreenshot("uru_02_doll_back");
			server.runCommand("data merge entity @e[type=infinity_gauntlet:gauntlet_doll,limit=1] {Rotation:[180f,0f]}");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			ctx.getInput().lookAt(180, 12);
			ctx.waitTicks(8);
			ctx.takeScreenshot("uru_03_player_back");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			ctx.waitTicks(8);
			ctx.takeScreenshot("uru_04_player_front");
			ctx.runOnClient(mc -> {mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = false;});
			ctx.getInput().lookAt(0, 12);

			server.runCommand("execute as @p at @p run gauntlet give_power");
			for (Stone s : Stone.values()) if (s != Stone.POWER) server.runCommand("execute as @p at @p run gauntlet give_stone " + s.id);
			ctx.waitTicks(5);
			// 32 incoming from a full gauntlet: Uru lets 25% through (8 HP), wears every piece, keeps the target alive.
			float[] result = server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().getFirst();
				GauntletDoll d = s.overworld().getEntities(ModEntities.GAUNTLET_DOLL, x -> true).getFirst();
				if (!UruArmor.fullSet(d)) throw new AssertionError("Doll is not in a full Uru set");
				d.setHealth(d.getMaxHealth());
				float before = d.getHealth();
				boolean hit = GauntletDamage.hurt(s.overworld(), d, p, p.damageSources().playerAttack(p), 32f / 1.5f);
				return new float[]{hit ? 1 : 0, before - d.getHealth(), d.getItemBySlot(EquipmentSlot.CHEST).getDamageValue()};
			});
			if (result[0] != 1 || Math.abs(result[1] - 8f) > 0.01f || result[2] < 6) throw new AssertionError("Uru hit: hit=" + result[0] + " taken=" + result[1] + " wear=" + result[2]);
			ctx.waitTicks(3);
			ctx.takeScreenshot("uru_05_guard_flash");
			// An instant-kill effect becomes a finite hit: 40 * 25% = 10.
			float lethal = server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().getFirst();
				GauntletDoll d = s.overworld().getEntities(ModEntities.GAUNTLET_DOLL, x -> true).getFirst();
				d.invulnerableTime = 0;
				d.setHealth(d.getMaxHealth());
				GauntletDamage.hurt(s.overworld(), d, p, p.damageSources().playerAttack(p), Float.MAX_VALUE);
				return d.isAlive() ? d.getMaxHealth() - d.getHealth() : -1;
			});
			if (Math.abs(lethal - 10f) > 0.01f) throw new AssertionError("Lethal effect vs Uru took " + lethal);
			// Three pieces only (and a mob target): back to the ordinary path, plain armor reduces a normal hit.
			float partial = server.computeOnServer(s -> {
				var p = s.getPlayerList().getPlayers().getFirst();
				GauntletDoll d = s.overworld().getEntities(ModEntities.GAUNTLET_DOLL, x -> true).getFirst();
				d.setItemSlot(EquipmentSlot.FEET, net.minecraft.world.item.ItemStack.EMPTY);
				d.invulnerableTime = 0;
				d.setHealth(d.getMaxHealth());
				GauntletDamage.hurt(s.overworld(), d, p, p.damageSources().playerAttack(p), 10f);
				return d.getMaxHealth() - d.getHealth();
			});
			if (partial <= 0.01f || partial >= 10f) throw new AssertionError("Partial set should take a normal, armor-reduced hit: took " + partial);

		}
	}
}
