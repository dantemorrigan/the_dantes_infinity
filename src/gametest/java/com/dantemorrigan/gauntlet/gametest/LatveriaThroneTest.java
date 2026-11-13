package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.doom.Latveria;
import com.dantemorrigan.gauntlet.doom.LatveriaCourt;
import com.dantemorrigan.gauntlet.doom.LatveriaPlan;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The storm strikes the spires; the castle's new lord sits on Doom's throne, only once it is theirs. */
public class LatveriaThroneTest implements FabricClientGameTest {
	private static ServerPlayer player(MinecraftServer s) { return s.getPlayerList().getPlayers().getFirst(); }

	private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

	@Override
	public void runTest(ClientGameTestContext ctx) {
		LegacyBindings.disable(ctx);
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamemode creative @p");
			server.runCommand("execute as @p run gauntlet latveria enter");
			ctx.waitTicks(270);
			server.runCommand("execute as @p run gauntlet latveria tp hall");
			ctx.waitTicks(30);
			check(!server.computeOnServer(s -> LatveriaCourt.sit(player(s))), "A visitor sat on Doom's throne before the castle was taken");

			// The storm on the spires, seen from the yard.
			server.runCommand("execute as @p run gauntlet latveria tp yard");
			server.runCommand("execute in infinity_gauntlet:latveria run tp @p 0.5 " + (LatveriaPlan.FLOOR + 1) + " -212.5 135 -25");
			ctx.waitTicks(40);
			server.runOnServer(s -> LatveriaCourt.strike(s.getLevel(Latveria.LEVEL), new Vec3(45.5, LatveriaPlan.FLOOR + 27, -204.5)));
			ctx.waitTicks(2);
			ctx.runOnClient(mc -> mc.gui.getChat().clearMessages(false));
			ctx.takeScreenshot("latveria_storm_strike");

			// Conquered: the throne is the new lord's.
			server.runCommand("execute as @p run gauntlet latveria conquer");
			ctx.waitTicks(40);
			server.runCommand("execute as @p run gauntlet latveria tp throne");
			ctx.waitTicks(20);
			check(server.computeOnServer(s -> LatveriaCourt.sit(player(s))), "The castle's lord could not sit on the throne");
			ctx.waitTicks(10);
			check(server.computeOnServer(s -> player(s).isPassenger()), "The lord is not seated");
			ctx.runOnClient(mc -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.gui.getChat().clearMessages(false); });
			ctx.waitTicks(10);
			ctx.takeScreenshot("latveria_throne_seated");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			ctx.waitTicks(20);
			sp.getClientLevel().waitForChunksRender();
			System.out.println("THRONE-EYE " + ctx.computeOnClient(mc -> {
				var e = mc.player.getEyePosition();
				return String.format("eye=%.2f,%.2f,%.2f yaw=%.0f pitch=%.0f block=%s feet=%s", e.x, e.y, e.z, mc.player.getYRot(), mc.player.getXRot(),
						mc.level.getBlockState(net.minecraft.core.BlockPos.containing(e)), mc.level.getBlockState(mc.player.blockPosition()));
			}));
			ctx.takeScreenshot("latveria_throne_view");
			server.runOnServer(s -> player(s).stopRiding());
			// The lord builds a Bastion at an anvil: a Mechanical Heart with an arc reactor in the other hand.
			ctx.waitTicks(10);
			String forged = server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				var l = s.getLevel(Latveria.LEVEL);
				net.minecraft.core.BlockPos anvil = new net.minecraft.core.BlockPos(10, LatveriaPlan.FLOOR, -270);
				l.setBlockAndUpdate(anvil, net.minecraft.world.level.block.Blocks.ANVIL.defaultBlockState());
				p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
				p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.dantemorrigan.gauntlet.ModItems.MECHANICAL_HEART));
				p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new net.minecraft.world.item.ItemStack(com.dantemorrigan.gauntlet.ModItems.ARC_REACTOR, 2));
				var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(anvil).add(0, 0.5, 0), net.minecraft.core.Direction.UP, anvil, false);
				p.gameMode.useItemOn(p, l, p.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND, hit);
				var bots = l.getEntitiesOfClass(com.dantemorrigan.gauntlet.doom.Doombot.class, new AABB(anvil).inflate(3), b -> b.allied() && b.variant() == com.dantemorrigan.gauntlet.doom.Doombot.BASTION);
				p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
				return bots.size() + " hp=" + (bots.isEmpty() ? 0 : (int) bots.getFirst().getMaxHealth()) + " reactors=" + p.getOffhandItem().getCount() + " heart=" + p.getMainHandItem().getCount();
			});
			System.out.println("FORGE " + forged);
			check(forged.startsWith("1 hp=204 reactors=1 heart=0"), "The forge did not build a sturdier Bastion from the heart and a reactor: " + forged);
			ctx.waitTicks(30);
			check(server.computeOnServer(s -> s.getLevel(Latveria.LEVEL).getEntitiesOfClass(ArmorStand.class,
					new AABB(-6, LatveriaPlan.FLOOR - 4, -296, 6, LatveriaPlan.FLOOR + 8, -286), a -> a.entityTags().contains(LatveriaCourt.SEAT_TAG)).isEmpty()),
					"The empty seat was left behind");
		}
	}
}
