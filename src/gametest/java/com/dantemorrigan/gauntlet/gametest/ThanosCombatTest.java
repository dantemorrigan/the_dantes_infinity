package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.boss.Thanos;
import com.dantemorrigan.gauntlet.uru.GauntletDamage;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The gauntlet is what he fears most; swords hurt him less; he walks out of water. */
public class ThanosCombatTest implements FabricClientGameTest {
	private static ServerPlayer player(MinecraftServer s) { return s.getPlayerList().getPlayers().getFirst(); }

	private static Thanos thanos(MinecraftServer s) {
		ServerPlayer p = player(s);
		var list = ((ServerLevel) p.level()).getEntitiesOfClass(Thanos.class, p.getBoundingBox().inflate(80), e -> true);
		return list.isEmpty() ? null : list.getFirst();
	}

	private static float lost(MinecraftServer s, java.util.function.Consumer<Thanos> blow) {
		Thanos t = thanos(s);
		t.setHealth(t.getMaxHealth());
		t.invulnerableTime = 0;
		float before = t.getHealth();
		blow.accept(t);
		float lost = before - t.getHealth();
		t.setHealth(t.getMaxHealth());
		t.invulnerableTime = 0;
		return lost;
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamemode survival @p");
			server.runCommand("effect give @p minecraft:resistance 9999 4 true");
			server.runCommand("gauntlet thanos reset");
			server.runCommand("execute as @p at @s run gauntlet thanos spawn");
			ctx.waitTicks(40);

			// ---- Damage: swords < a loose stone < the gauntlet < the full gauntlet.
			float[] r = server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = (ServerLevel) p.level();
				var src = level.damageSources().playerAttack(p);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_SWORD));
				float netherite = lost(s, t -> t.hurtServer(level, src, 8f));
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
				float diamond = lost(s, t -> t.hurtServer(level, src, 7f));
				p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.REALITY_STONE));
				float loose = lost(s, t -> t.hurtServer(level, src, 8f));
				// A worn gauntlet with one stone, then with all six.
				ItemStack g = new ItemStack(ModItems.GAUNTLET);
				GauntletState.putStone(g, new ItemStack(ModItems.POWER_STONE));
				p.setItemInHand(InteractionHand.OFF_HAND, g);
				float gauntlet = lost(s, t -> GauntletDamage.hurt(level, t, p, src, 18f));
				for (var stone : new Item[]{ModItems.SPACE_STONE, ModItems.REALITY_STONE, ModItems.MIND_STONE, ModItems.SOUL_STONE, ModItems.TIME_STONE}) GauntletState.putStone(g, new ItemStack(stone));
				float full = lost(s, t -> GauntletDamage.hurt(level, t, p, src, 18f));
				// The beam: ten slices in the same tick all land (no hurt-immunity gaps).
				float beam = lost(s, t -> { for (int i = 0; i < 10; i++) t.hurtByBeam(level, p, 5f); });
				return new float[]{netherite, diamond, loose, gauntlet, full, beam};
			});
			System.out.println("THANOS-DAMAGE netherite=" + r[0] + " diamond=" + r[1] + " looseStone=" + r[2] + " gauntlet=" + r[3] + " fullGauntlet=" + r[4] + " beam10slices=" + r[5]);
			if (!(r[3] > r[0] * 3)) throw new AssertionError("The gauntlet (" + r[3] + ") should hurt far more than a netherite sword (" + r[0] + ")");
			if (!(r[3] > r[2])) throw new AssertionError("The gauntlet should beat a loose stone: " + r[3] + " vs " + r[2]);
			if (!(r[2] > r[0] * 0.9f || r[2] >= r[1])) throw new AssertionError("A loose stone should not be weaker than a sword: " + r[2]);
			if (!(r[4] > r[3])) throw new AssertionError("The full gauntlet should beat a partial one: " + r[4] + " vs " + r[3]);
			if (Math.abs(r[5] - 50f) > 1f) throw new AssertionError("Ten beam slices of 5 should all land (50), got " + r[5]);
			if (!(r[1] < r[0])) throw new AssertionError("A diamond sword should hurt less than a netherite one");

			server.runOnServer(s -> {
				Thanos boss = thanos(s);
				ServerPlayer p = player(s);
				boss.hurtByBeam(p.level(), p, boss.getMaxHealth());
				float phaseHealth = boss.getHealth();
				if (phaseHealth > boss.getMaxHealth() * .5f) throw new AssertionError("Beam did not reach the rage transition");
				boss.hurtByBeam(p.level(), p, boss.getMaxHealth());
				if (!boss.isAlive() || boss.getHealth() != phaseHealth) throw new AssertionError("Beam bypassed the rage transition");
			});

			// ---- Water: he is dropped into a pond and must walk out to the player.
			server.runCommand("gauntlet thanos stop");
			ctx.waitTicks(100);
			server.runCommand("gauntlet thanos reset");
			// Creative: he chases but cannot kill the player (the point here is the pond).
			server.runCommand("gamemode creative @p");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				var level = (ServerLevel) p.level();
				p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
				p.teleportTo(0.5, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, 0), 0.5);
			});
			server.runCommand("fill 6 -63 -8 22 -61 8 minecraft:water");
			server.runCommand("execute as @p at @s run gauntlet thanos spawn");
			ctx.waitTicks(10);
			server.runOnServer(s -> {
				Thanos t = thanos(s);
				t.teleportTo(14.5, -61, 0.5);
				t.setDeltaMovement(0, 0, 0);
			});
			for (int i = 0; i < 13; i++) {
				ctx.waitTicks(20);
				if (i % 4 == 0) System.out.println("THANOS-WATER t=" + (i * 20) + " " + server.computeOnServer(s -> {
					Thanos t = thanos(s);
					return t == null ? "NONE running=" + com.dantemorrigan.gauntlet.boss.ThanosEncounter.running()
							: "dist=" + (int) t.distanceTo(player(s)) + " y=" + (int) t.getY() + " inWater=" + t.isInWater() + " hp=" + (int) t.getHealth() + " action=" + t.action();
				}));
			}
			double[] result = server.computeOnServer(s -> {
				Thanos t = thanos(s);
				return new double[]{t.distanceTo(player(s)), t.isInWater() ? 1 : 0};
			});
			System.out.println("THANOS-WATER distanceToPlayer=" + result[0] + " stillInWater=" + result[1]);
			if (result[1] > 0 || result[0] > 12) throw new AssertionError("Thanos did not get out of the water: distance " + result[0] + ", in water " + result[1]);
			server.runCommand("gauntlet thanos reset");
		}
	}
}
