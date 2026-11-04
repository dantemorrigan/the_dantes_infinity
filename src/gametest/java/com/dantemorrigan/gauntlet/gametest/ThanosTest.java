package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.boss.Thanos;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Thanos: the Overworld arrival (ship, red sky, beam, landing, line), the fight, damage rules, the defeat (kneel,
 * dust, empty gauntlet), then the Nether rift and the End (the ship's shot at the dragon).
 */
public class ThanosTest implements FabricClientGameTest {
	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static Thanos thanos(MinecraftServer s) {
		ServerPlayer p = player(s);
		List<Thanos> list = ((ServerLevel) p.level()).getEntitiesOfClass(Thanos.class, p.getBoundingBox().inflate(80), e -> true);
		return list.isEmpty() ? null : list.getFirst();
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("weather clear");
			server.runCommand("time set 3000");
			server.runCommand("gamemode creative @p");
			server.runCommand("gauntlet thanos reset");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 -12");
			ctx.waitTicks(10);

			// ---- Overworld: ship, red sky, beam, landing.
			server.runCommand("execute as @p at @p run gauntlet thanos now");
			ctx.waitTicks(70);
			ctx.takeScreenshot("thanos_ship_approach");
			ctx.waitTicks(55);
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ ~ -30");
			ctx.waitTicks(5);
			ctx.takeScreenshot("thanos_ship_hover_red_sky");
			ctx.waitTicks(25);
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ ~ -12");
			ctx.waitTicks(5);
			ctx.takeScreenshot("thanos_beam");
			ctx.waitTicks(40);
			if (server.computeOnServer(ThanosTest::thanos) == null) throw new AssertionError("Thanos did not arrive");
			ctx.takeScreenshot("thanos_landed");
			// Step closer to see him.
			server.runOnServer(s -> {
				Thanos t = thanos(s);
				ServerPlayer p = player(s);
				var d = p.position().subtract(t.position()).normalize();
				p.teleportTo(t.getX() + d.x * 5.5, t.getY(), t.getZ() + d.z * 5.5);
				p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, t.position().add(0, 1.6, 0));
			});
			ctx.waitTicks(30);
			ctx.takeScreenshot("thanos_intro_line");
			ctx.waitTicks(80);
			if (!server.computeOnServer(s -> thanos(s).fighting())) throw new AssertionError("Thanos did not start the fight");
			if (!server.computeOnServer(s -> thanos(s).hasGauntlet())) throw new AssertionError("Thanos should carry his gauntlet when the player has none");
			ctx.takeScreenshot("thanos_fight_bossbar");
			server.runCommand("gamemode survival @p");
			server.runCommand("effect give @p resistance 60 4 true");
			ctx.waitTicks(60);
			ctx.takeScreenshot("thanos_fight_action");
			server.runCommand("gamemode creative @p");

			// ---- Damage rules: a netherite sword cuts far deeper than an iron one.
			float iron = hitWith(server, Items.IRON_SWORD);
			float netherite = hitWith(server, Items.NETHERITE_SWORD);
			if (!(netherite > iron * 3)) throw new AssertionError("Netherite " + netherite + " should hurt much more than iron " + iron);

			// ---- Defeat: kneel, dust, the empty gauntlet.
			server.runOnServer(s -> {
				Thanos t = thanos(s);
				t.invulnerableTime = 0;
				t.setHealth(1);
				t.hurtServer((ServerLevel) t.level(), t.level().damageSources().explosion(null, null), 50);
			});
			ctx.waitTicks(30);
			ctx.takeScreenshot("thanos_kneel");
			ctx.waitTicks(Thanos.DUST_START - 30 + 30);
			ctx.takeScreenshot("thanos_dust");
			ctx.waitTicks(Thanos.DEATH_TICKS - Thanos.DUST_START);
			boolean dropped = server.computeOnServer(s -> !((ServerLevel) player(s).level()).getEntitiesOfClass(ItemEntity.class,
					player(s).getBoundingBox().inflate(40), e -> e.getItem().is(ModItems.GAUNTLET)).isEmpty());
			if (!dropped) throw new AssertionError("Thanos did not leave his gauntlet");
			ctx.takeScreenshot("thanos_gauntlet_drop");

			// ---- His ship falls and burns.
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 -22");
			ctx.waitTicks(30);
			ctx.takeScreenshot("thanos_ship_falling_1");
			ctx.waitTicks(35);
			ctx.takeScreenshot("thanos_ship_falling_2");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 -8");
			ctx.waitTicks(32);
			ctx.takeScreenshot("thanos_ship_almost_down");
			ctx.waitTicks(16);
			ctx.takeScreenshot("thanos_ship_impact");
			ctx.waitTicks(45);
			ctx.takeScreenshot("thanos_ship_wreck");
			int ironDrops = server.computeOnServer(s -> ((ServerLevel) player(s).level()).getEntitiesOfClass(ItemEntity.class,
					player(s).getBoundingBox().inflate(220), e -> e.getItem().is(Items.IRON_INGOT)).size());
			int netheriteDrops = server.computeOnServer(s -> ((ServerLevel) player(s).level()).getEntitiesOfClass(ItemEntity.class,
					player(s).getBoundingBox().inflate(220), e -> e.getItem().is(Items.NETHERITE_SCRAP) || e.getItem().is(Items.NETHERITE_INGOT)).size());
			System.out.println("SHIP-CRASH iron=" + ironDrops + " netherite=" + netheriteDrops);
			if (ironDrops < 20 || netheriteDrops < 5) throw new AssertionError("The ship should spill iron and netherite: iron " + ironDrops + ", netherite " + netheriteDrops);
			server.runCommand("execute as @p at @s run tp @s ~ ~30 ~20 0 25");
			ctx.waitTicks(40);
			sp.getClientLevel().waitForChunksRender();
			ctx.takeScreenshot("thanos_ship_crater");

			// ---- Nether: the rift.
			server.runCommand("gauntlet thanos reset");
			server.runCommand("execute in minecraft:the_nether run tp @p 0 121 0 0 10");
			ctx.waitTicks(20);
			server.runCommand("execute in minecraft:the_nether run fill -14 120 -14 14 120 14 minecraft:netherrack");
			server.runCommand("execute in minecraft:the_nether run fill -14 121 -14 14 127 14 minecraft:air");
			server.runCommand("execute in minecraft:the_nether run tp @p 0 121 0 0 10");
			ctx.waitTicks(40);
			sp.getClientLevel().waitForChunksRender();
			server.runCommand("execute as @p at @p run gauntlet thanos now");
			ctx.waitTicks(30);
			ctx.takeScreenshot("thanos_nether_rift");
			ctx.waitTicks(30);
			ctx.takeScreenshot("thanos_nether_step_out");
			server.runCommand("gauntlet thanos stop");
			ctx.waitTicks(80);

			// ---- End: the ship strikes the dragon down.
			server.runCommand("gauntlet thanos reset");
			server.runCommand("execute in minecraft:the_end run tp @p 40 70 40 135 -10");
			ctx.waitTicks(80);
			sp.getClientLevel().waitForChunksRender();
			server.runCommand("execute as @p at @p run gauntlet thanos now");
			ctx.waitTicks(120);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				var d = ((ServerLevel) p.level()).getDragons();
				if (!d.isEmpty()) p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, d.getFirst().position());
			});
			ctx.waitTicks(20);
			ctx.takeScreenshot("thanos_end_charge");
			ctx.waitTicks(19);
			ctx.takeScreenshot("thanos_end_ray");
			ctx.waitTicks(30);
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ ~ 20");
			ctx.waitTicks(40);
			ctx.takeScreenshot("thanos_end_beam");
			server.runCommand("gauntlet thanos stop");
			ctx.waitTicks(40);
		}
	}

	/** Health Thanos loses to one fresh hit from the player holding this weapon. */
	private static float hitWith(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, net.minecraft.world.item.Item weapon) {
		return server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Thanos t = thanos(s);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(weapon));
			t.setHealth(t.getMaxHealth());
			t.invulnerableTime = 0;
			float before = t.getHealth();
			t.hurtServer((ServerLevel) t.level(), t.level().damageSources().playerAttack(p), 8f);
			float lost = before - t.getHealth();
			t.setHealth(t.getMaxHealth());
			t.invulnerableTime = 0;
			return lost;
		});
	}
}
