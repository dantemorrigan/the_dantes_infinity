package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.stones.RealityFog;
import com.dantemorrigan.gauntlet.stones.StoneRegistry;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;

/** Smoke: the red mist starts, the stone darts around, an arrow knocks it down next to the player. */
public class RealityFogTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gauntlet stones reset");
			server.runCommand("execute as @p run gauntlet reality_fog");
			ctx.waitTicks(80);
			ctx.takeScreenshot("reality_fog");
			if (!server.computeOnServer(s -> RealityFog.active())) throw new AssertionError("Mist did not start");
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().getFirst();
				var wisp = RealityFog.wisp();
				Arrow arrow = new Arrow(EntityType.ARROW, s.overworld());
				arrow.setOwner(p);
				wisp.hurtServer(s.overworld(), s.overworld().damageSources().arrow(arrow, p), 2);
			});
			ctx.waitTicks(30);
			ctx.takeScreenshot("reality_fog_dispel");
			if (!server.computeOnServer(s -> StoneRegistry.released(s, Stone.REALITY))) throw new AssertionError("Stone not released");
			if (server.computeOnServer(s -> s.overworld().getEntitiesOfClass(ItemEntity.class, s.getPlayerList().getPlayers().getFirst().getBoundingBox().inflate(6),
					e -> e.getItem().is(ModItems.REALITY_STONE)).isEmpty())) throw new AssertionError("No stone near the player");
		}
	}
}
