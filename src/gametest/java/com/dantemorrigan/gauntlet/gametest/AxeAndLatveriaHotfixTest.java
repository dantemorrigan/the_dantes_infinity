package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.doom.DoctorDoom;
import com.dantemorrigan.gauntlet.doom.DoomFinalEncounter;
import com.dantemorrigan.gauntlet.doom.Latveria;
import com.dantemorrigan.gauntlet.doom.LatveriaPlan;
import com.dantemorrigan.gauntlet.power.InfinityBeam;
import com.dantemorrigan.gauntlet.stormbreaker.StormNetwork;
import com.dantemorrigan.gauntlet.stormbreaker.StormbreakerEntity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class AxeAndLatveriaHotfixTest implements FabricClientGameTest {
	private static final AABB WALL = new AABB(-3, 98, 9, 10, 104, 11);
	private static final Vec3 AXE_ORIGIN = new Vec3(.5, 100, .5);

	@Override
	public void runTest(ClientGameTestContext ctx) {
		TestWorldSave save;
		try (var world = ctx.worldBuilder().create()) {
			world.getClientLevel().waitForChunksRender();
			var server = world.getServer();
			server.runCommand("gamemode creative @p");
			server.runCommand("tp @p .5 100 .5 -18 5");
			ctx.waitTicks(12);
			server.runCommand("fill -6 99 -10 9 99 10 minecraft:stone");
			server.runCommand("fill -3 100 10 7 103 10 minecraft:stone");
			server.runCommand("tp @p .5 100 .5 -18 5");
			server.runOnServer(s -> player(s).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.STORMBREAKER)));
			ctx.waitTicks(10);
			action(ctx, StormNetwork.THROW);
			ctx.waitTicks(27);
			assertWallEmbedding(ctx, server);
			save = world.getWorldSave();
		}
		try (var world = save.open()) {
			world.getClientLevel().waitForChunksRender();
			var server = world.getServer();
			ctx.waitTicks(15);
			assertWallEmbedding(ctx, server);
			action(ctx, StormNetwork.RECALL);
			ctx.waitTicks(48);
			check(server.computeOnServer(s -> player(s).getMainHandItem().is(ModItems.STORMBREAKER)), "Could not recall from the wall");
			ctx.getInput().pressKey(GLFW.GLFW_KEY_Q);
			ctx.waitTicks(20);
			check(server.computeOnServer(s -> s.overworld().getEntitiesOfClass(StormbreakerEntity.class,
					new AABB(AXE_ORIGIN, AXE_ORIGIN).inflate(5), a -> a.phase() == StormbreakerEntity.EMBEDDED).size() == 1),
					"Q drop did not keep its original ground pose");
			action(ctx, StormNetwork.RECALL);
			ctx.waitTicks(48);
			server.runCommand("tp @p .5 100 .5 90 50");
			ctx.waitTicks(8);
			action(ctx, StormNetwork.THROW);
			ctx.waitTicks(22);
			check(server.computeOnServer(s -> s.overworld().getEntitiesOfClass(StormbreakerEntity.class,
					new AABB(AXE_ORIGIN, AXE_ORIGIN).inflate(5),
					a -> a.phase() == StormbreakerEntity.EMBEDDED_BLADE && Math.abs(a.getY() - 100.01) < .02
							&& Math.abs(a.getXRot() - 50) < 1).size() == 1),
					"Throwing downward did not plant the blade at its ground contact");

			server.runCommand("execute as @p run gauntlet latveria enter");
			ctx.waitFor(mc -> mc.level != null && mc.level.dimension().equals(Latveria.LEVEL), 200);
			server.runCommand("execute as @p run gauntlet latveria tp hall");
			ctx.waitTicks(15);
			server.runCommand("execute as @p run gauntlet doom stop");
			server.runCommand("execute as @p in infinity_gauntlet:latveria run tp @s 0.5 "
					+ (LatveriaPlan.FLOOR + 2) + " -284.5 180 -5");
			ctx.waitTicks(12);
			server.runCommand("execute as @p run gauntlet doom spawn");
			ctx.waitTicks(5);
			server.runOnServer(s -> {
				var doom = Latveria.boss();
				check(doom != null && doom.action() == DoctorDoom.A_INTRO && !doom.isInWall(),
						"Doom began the throne intro inside a block or without his intro animation");
			});
			ctx.takeScreenshot("doom_intro_seated");
			ctx.waitTicks(104);
			server.runOnServer(s -> {
				var doom = Latveria.boss();
				check(doom != null && doom.fighting() && !doom.isInWall(), "Doom did not leave the seat in a valid fight state");
			});
			testBeam(ctx, server);
		}
	}

	private static void assertWallEmbedding(ClientGameTestContext ctx, TestServerContext server) {
		server.runOnServer(s -> {
			var hits = s.overworld().getEntitiesOfClass(StormbreakerEntity.class, WALL, StormbreakerEntity::embedded);
			check(hits.size() == 1, "Expected one embedded axe on the struck wall");
			StormbreakerEntity axe = hits.getFirst();
			check(axe.phase() == StormbreakerEntity.EMBEDDED_BLADE && Math.abs(axe.getZ() - 9.99) < .02,
					"Thrown axe embedded in the floor instead of at the wall contact");
			check(Math.abs(axe.getYRot() + 18) < 1 && Math.abs(axe.getXRot() - 5) < 1,
					"Wall embedding lost the launch angle");
		});
		ctx.waitFor(mc -> mc.level != null && !mc.level.getEntitiesOfClass(StormbreakerEntity.class, WALL,
				a -> a.phase() == StormbreakerEntity.EMBEDDED_BLADE && a.getXRot() > 3).isEmpty(), 60);
	}

	private static void testBeam(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("execute as @p in infinity_gauntlet:latveria run tp @s 0.5 145 -266.5 0 0");
		ctx.waitTicks(12);
		BlockPos drill = new BlockPos(0, 146, -250);
		Zombie target = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			var level = p.level();
			for (BlockPos floor : BlockPos.betweenClosed(-10, 143, -269, 10, 143, -234)) {
				level.setBlockAndUpdate(floor, Blocks.NETHERRACK.defaultBlockState());
			}
			level.setBlockAndUpdate(drill, Blocks.STONE.defaultBlockState());
			var mob = EntityType.ZOMBIE.create(level, EntitySpawnReason.EVENT);
			check(mob != null, "Could not create the beam target");
			mob.setNoAi(true);
			mob.setNoGravity(true);
			mob.setPos(.5, 145, -257.5);
			check(level.addFreshEntity(mob), "Could not place the beam target");
			p.setItemInHand(InteractionHand.OFF_HAND, DoomFinalEncounter.buildGauntletFromMask(0x3f));
			return mob;
		});
		server.runCommand("execute as @p in infinity_gauntlet:latveria run tp @s 0.5 144 -266.5 0 0");
		ctx.waitTicks(100);
		server.runOnServer(s -> {
			var p = player(s);
			InfinityBeam.request(p, true);
			check(InfinityBeam.active(p), "Infinity Beam did not start");
		});
		ctx.waitTicks(GauntletConfig.get().beamChargeTicks + 10);
		server.runOnServer(s -> {
			var level = player(s).level();
			check(!target.isAlive() && !target.isOnFire(), "Latveria beam either lost its damage or ignited the target");
			check(level.getBlockState(drill).isAir(), "The test beam did not reach its terrain target");
			for (BlockPos pos : BlockPos.betweenClosed(-10, 144, -265, 10, 150, -234)) {
				check(!(level.getBlockState(pos).getBlock() instanceof BaseFireBlock), "The beam ignited Latveria terrain");
			}
			InfinityBeam.cancel(player(s));
		});
	}

	private static void action(ClientGameTestContext ctx, int action) {
		ctx.runOnClient(mc -> ClientPlayNetworking.send(new StormNetwork.Action(action)));
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(boolean ok, String message) {
		if (!ok) throw new AssertionError(message);
	}
}
