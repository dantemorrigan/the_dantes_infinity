package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.doom.DoomArmor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Under Doom's jets the body leans into the flight and the limbs hold still: no walking in the air. */
public class JetPoseTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (var sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			var server = sp.getServer();
			server.runCommand("gamemode survival @p");
			server.runCommand("effect give @p minecraft:resistance 300 4 true");
			server.runCommand("tp @p ~ ~20 ~");
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayers().getFirst();
				p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.DOOM_HELMET));
				p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.DOOM_CHESTPLATE));
				p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.DOOM_LEGGINGS));
				p.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.DOOM_BOOTS));
			});
			ctx.waitTicks(30);
			server.runOnServer(s -> DoomArmor.cast(s.getPlayerList().getPlayers().getFirst(), DoomArmor.JET));
			ctx.waitTicks(20);
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			ctx.getInput().lookAt(0, 10);
			ctx.getInput().holdKeyFor(org.lwjgl.glfw.GLFW.GLFW_KEY_W, 30);
			ctx.takeScreenshot("jet_forward_a");
			ctx.getInput().holdKeyFor(org.lwjgl.glfw.GLFW.GLFW_KEY_W, 7);
			ctx.takeScreenshot("jet_forward_b");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			ctx.getInput().holdKeyFor(org.lwjgl.glfw.GLFW.GLFW_KEY_W, 12);
			ctx.takeScreenshot("jet_forward_back_view");
			boolean pose = ctx.computeOnClient(mc -> com.dantemorrigan.gauntlet.client.render.JetLean.jetting(mc.player.getId()));
			System.out.println("JET jetting " + pose);
			if (!pose) throw new AssertionError("The jets did not start");
		}
	}
}
