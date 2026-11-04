package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.stones.SoulQuest;
import com.dantemorrigan.gauntlet.stones.SoulTemple;
import com.dantemorrigan.gauntlet.stones.StoneRegistry;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Smoke: temple built, a tamed wolf thrown into the chasm, the soul world, the rising stone, the way back. */
public class SoulQuestTest implements FabricClientGameTest {
	private static net.minecraft.server.level.ServerPlayer player(net.minecraft.server.MinecraftServer s) { return s.getPlayerList().getPlayers().getFirst(); }

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("time set 6000");
			server.runCommand("gamemode creative @p");
			server.runCommand("gauntlet stones reset");
			server.runCommand("execute as @p run gauntlet soul_quest temple_here");
			if (!server.computeOnServer(SoulTemple::built)) throw new AssertionError("Temple not built");
			var site = server.computeOnServer(SoulTemple::site);
			int top = SoulTemple.summitY(site);
			server.runCommand("tp @p " + (site.getX() + 0.5) + " " + (top - 25) + " " + (site.getZ() - 62.5) + " 0 -5");
			ctx.waitTicks(20);
			sp.getClientLevel().waitForChunksRender();
			ctx.takeScreenshot("soul_mountain");
			Vec3 stand = SoulTemple.summitStand(site);
			server.runCommand("tp @p " + stand.x + " " + stand.y + " " + stand.z);
			ctx.waitTicks(10);
			sp.getClientLevel().waitForChunksRender();
			// A tamed wolf falls into the chasm.
			server.runOnServer(s -> {
				var wolf = EntityType.WOLF.create(s.overworld(), EntitySpawnReason.COMMAND);
				wolf.tame(player(s));
				wolf.setOrderedToSit(true);
				var c = SoulTemple.chasm(site).getCenter();
				wolf.setPos(c.x, top - 6, c.z);
				s.overworld().addFreshEntity(wolf);
				player(s).lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, new Vec3(c.x, top + 40, c.z));
			});
			ctx.waitTicks(70);
			ctx.takeScreenshot("soul_ritual");
			if (!server.computeOnServer(s -> StoneRegistry.found(s, Stone.SOUL))) throw new AssertionError("The sacrifice was not noticed");
			ctx.waitFor(mc -> mc.level != null && mc.level.dimension().equals(SoulQuest.SOUL_WORLD), 400);
			ctx.waitTicks(150);
			ctx.takeScreenshot("soul_world");
			if (!server.computeOnServer(s -> StoneRegistry.released(s, Stone.SOUL))) throw new AssertionError("The stone did not rise");
			server.runCommand("execute in infinity_gauntlet:soul_world run tp @p 0.5 3 7.5");
			ctx.waitTicks(30);
			ctx.takeScreenshot("soul_alert");
			if (!server.computeOnServer(s -> player(s).getInventory().contains(st -> st.is(ModItems.SOUL_STONE)))) throw new AssertionError("Stone not picked up");
			server.runOnServer(s -> SoulQuest.returnHome(player(s)));
			ctx.waitFor(mc -> mc.level != null && mc.level.dimension().equals(Level.OVERWORLD), 200);
		}
	}
}
