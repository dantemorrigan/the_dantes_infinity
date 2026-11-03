package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.ability.Ability;
import com.dantemorrigan.gauntlet.client.ability.AbilityInput;
import com.dantemorrigan.gauntlet.client.render.GauntletGestureClient;
import com.dantemorrigan.gauntlet.snap.InfinitySnap;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;

/**
 * The Infinity Snap end to end: snap with six stones, land in the White Void with a burnt-out gauntlet, walk,
 * take the Time stone out, use it and come back to the moment before the snap with the gauntlet intact.
 */
public class SnapTest implements FabricClientGameTest {
	private static ServerPlayer player(TestServerContext server) {
		return server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst());
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		LegacyBindings.disable(ctx);
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			TestServerContext server = sp.getServer();
			sp.getClientLevel().waitForChunksRender();
			server.runCommand("time set noon");
			server.runCommand("gamerule advance_time false");
			server.runCommand("execute as @p at @p run gauntlet give_power");
			for (Stone s : Stone.values()) if (s != Stone.POWER) server.runCommand("execute as @p at @p run gauntlet give_stone " + s.id);
			ctx.waitTicks(10);
			ctx.getInput().lookAt(0, 10);
			var before = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().position());

			// Gesture frames of the snap itself.
			for (float t : new float[]{0.22f, 0.30f}) {
				ctx.runOnClient(mc -> GauntletGestureClient.startAt(mc.player.getId(), 1, t));
				ctx.takeScreenshot("snap_gesture_" + Math.round(t * 100));
			}
			ctx.waitTicks(40);

			ctx.runOnClient(mc -> AbilityInput.choose(Ability.SNAP, -1));
			ctx.waitTicks(8);
			ctx.getInput().pressKey(GLFW.GLFW_KEY_R);
			ctx.waitTicks(InfinitySnap.SNAP_TICK + 4);
			ctx.takeScreenshot("snap_click");
			server.runOnServer(s -> {
				ItemStack g = GauntletState.wornGauntlet(s.getPlayerList().getPlayers().getFirst());
				if (!InfinitySnap.snapped(g)) throw new AssertionError("Gauntlet did not burn out on the snap");
			});
			ctx.waitTicks(18);
			ctx.takeScreenshot("snap_fade");
			ctx.waitFor(mc -> mc.level != null && mc.level.dimension().equals(InfinitySnap.WHITE_VOID), 200);
			ctx.waitTicks(10);
			ctx.takeScreenshot("snap_void_arrive");
			ctx.waitTicks(50);
			ctx.takeScreenshot("snap_void_first_person");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			ctx.waitTicks(5);
			ctx.takeScreenshot("snap_void_third_person");
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

			// Only walking: abilities refuse, walking moves on the invisible floor.
			if (!server.computeOnServer(s -> com.dantemorrigan.gauntlet.ability.AbilityController.reason(s.getPlayerList().getPlayers().getFirst(), Ability.SNAP).equals("broken")))
				throw new AssertionError("Burnt gauntlet still usable");
			var start = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().position());
			ctx.getInput().holdKeyFor(opts -> opts.keyUp, 30);
			var walked = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().position());
			if (walked.distanceTo(start) < 2 || Math.abs(walked.y - 1) > 0.1) throw new AssertionError("Cannot walk in the White Void: " + start + " -> " + walked);

			// The wheel key opens the burnt gauntlet; take the Time stone out like the menu does.
			ctx.getInput().pressKey(GLFW.GLFW_KEY_V);
			ctx.waitTicks(10);
			ctx.takeScreenshot("snap_void_gauntlet_menu");
			if (!ctx.computeOnClient(mc -> mc.screen != null)) throw new AssertionError("Wheel key did not open the burnt gauntlet");
			ctx.runOnClient(mc -> mc.setScreen(null));
			ctx.waitTicks(5);
			server.runOnServer(s -> {
				ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
				ItemStack g = GauntletState.wornGauntlet(p);
				NonNullList<ItemStack> items = NonNullList.withSize(6, ItemStack.EMPTY);
				g.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
				ItemStack time = items.get(Stone.TIME.ordinal());
				if (!time.is(ModItems.TIME_STONE) || !InfinitySnap.snapped(time)) throw new AssertionError("Time stone inside is not burnt");
				items.set(Stone.TIME.ordinal(), ItemStack.EMPTY);
				g.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
				GauntletState.refresh(g);
				p.setItemInHand(InteractionHand.MAIN_HAND, time);
			});
			ctx.waitTicks(5);
			ctx.takeScreenshot("snap_void_time_stone");
			ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			ctx.waitTicks(20);
			ctx.takeScreenshot("snap_rewind");
			ctx.waitFor(mc -> mc.level != null && mc.level.dimension().equals(Level.OVERWORLD), 200);
			ctx.waitTicks(10);
			ctx.takeScreenshot("snap_restored");
			server.runOnServer(s -> {
				ServerPlayer p = s.getPlayerList().getPlayers().getFirst();
				ItemStack g = GauntletState.activeGauntlet(p);
				if (g.isEmpty() || GauntletState.inserted(g).size() != 6) throw new AssertionError("Gauntlet not restored: " + g);
				if (p.hasAttached(InfinitySnap.RECORD)) throw new AssertionError("Snap record left behind");
				for (int i = 0; i < p.getInventory().getContainerSize(); i++)
					if (InfinitySnap.snapped(p.getInventory().getItem(i))) throw new AssertionError("Burnt item left in inventory");
				if (p.position().distanceTo(before) > 1.5) throw new AssertionError("Not back where the snap happened: " + p.position() + " vs " + before);
			});
		}
	}
}
