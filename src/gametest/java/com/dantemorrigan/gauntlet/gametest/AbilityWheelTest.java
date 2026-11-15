package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.*;
import com.dantemorrigan.gauntlet.ability.*;
import com.dantemorrigan.gauntlet.client.ability.*;
import com.dantemorrigan.gauntlet.client.render.GauntletGestureClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import com.dantemorrigan.gauntlet.cosmos.CosmosTravel;
import com.dantemorrigan.gauntlet.power.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.world.entity.EntityType;
import org.lwjgl.glfw.GLFW;

/** Exercises real V/R input, destructive-cast cancellation and safe telekinetic release. */
public class AbilityWheelTest implements FabricClientGameTest {
    private static void open(ClientGameTestContext ctx) {
        ctx.getInput().pressKey(GLFW.GLFW_KEY_V);
        ctx.waitFor(mc->mc.screen instanceof AbilityWheelScreen,100);ctx.waitTicks(3);
    }
    private static void close(ClientGameTestContext ctx) {
        if (ctx.computeOnClient(mc -> mc.screen instanceof AbilityWheelScreen)) ctx.getInput().pressKey(GLFW.GLFW_KEY_V);
        ctx.waitFor(mc->mc.screen==null,100);ctx.waitTicks(8);
    }
    private static void choose(ClientGameTestContext ctx,Ability a,int target) {
        ctx.runOnClient(mc->AbilityInput.choose(a,target));ctx.waitTicks(8);
    }
    @Override public void runTest(ClientGameTestContext ctx) {
        LegacyBindings.disable(ctx);
        try(var sp=ctx.worldBuilder().create()) {
            sp.getClientLevel().waitForChunksRender();
            var server=sp.getServer();
            server.runCommand("gamemode creative @p");
            server.runCommand("execute as @p run gauntlet give_power");
            server.runCommand("execute as @p run gauntlet give_stone space");
            server.runCommand("execute as @p run gauntlet give_stone mind");ctx.waitTicks(12);
            choose(ctx,Ability.SOUL_MASS,-1);
            if(!server.computeOnServer(s->AbilityController.selected(s.getPlayerList().getPlayers().getFirst())==Ability.SOUL_MASS))throw new AssertionError("Server locked ability selection");
            choose(ctx,Ability.STRIKE,-1);
            open(ctx);ctx.takeScreenshot("wheel_partial_original_items");
            if(!ctx.computeOnClient(mc->java.util.Arrays.stream(Ability.values()).allMatch(AbilityInput::selectable))) throw new AssertionError("Wheel still locks ability selection");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);close(ctx);
            if(!ctx.computeOnClient(mc->AbilityInput.selected()==Ability.PLANET)) throw new AssertionError("Wheel keyboard selection failed");
            server.runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();if(AbilityController.chargeTicks(p)>0 || com.dantemorrigan.gauntlet.space.PlanetDestruction.isBusy(p)) throw new AssertionError("Selection activated planet destruction");});
            open(ctx);
            ctx.getInput().pressKey(GLFW.GLFW_KEY_LEFT);
            ctx.getInput().pressKey(GLFW.GLFW_KEY_1);
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            close(ctx);
            // Even an upgrade with the previous duplicate binding must reserve G for favorites.
            ctx.runOnClient(mc -> {
                GauntletGestureClient.KEY.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_G));
                KeyMapping.resetMapping();
            });
            ctx.getInput().pressKey(GLFW.GLFW_KEY_G);
            ctx.waitFor(mc -> AbilityInput.selected() == Ability.STRIKE, 100);
            ctx.waitTicks(2);
            if (!ctx.computeOnClient(mc -> GauntletGestureClient.pose(mc.player.getId()) == null)) {
                throw new AssertionError("G played the gauntlet inspection while switching favorites");
            }
            ctx.runOnClient(mc -> {
                GauntletGestureClient.KEY.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_I));
                KeyMapping.resetMapping();
            });
            ctx.getInput().pressKey(GLFW.GLFW_KEY_I);
            ctx.waitFor(mc -> GauntletGestureClient.pose(mc.player.getId()) != null, 50);
            ctx.waitTicks(5);
            if (!server.computeOnServer(s -> AbilityController.selected(s.getPlayerList().getPlayers().getFirst()) == Ability.STRIKE)) {
                throw new AssertionError("Cycling favorites did not switch the server selection");
            }
            choose(ctx, Ability.PLANET, -1);
            ctx.getInput().holdKey(GLFW.GLFW_KEY_B);
            ctx.waitFor(mc->mc.screen instanceof AbilityFavoritesScreen,100);
            ctx.takeScreenshot("favorites_quick_selection");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_1);
            ctx.getInput().releaseKey(GLFW.GLFW_KEY_B);
            ctx.waitFor(mc->mc.screen==null,100);
            ctx.waitTicks(8);
            if(!server.computeOnServer(s->AbilityController.selected(s.getPlayerList().getPlayers().getFirst())==Ability.STRIKE)) {
                throw new AssertionError("Favorite shortcut did not switch the server selection");
            }
            if(!ctx.computeOnClient(mc->AbilityInput.selected()==Ability.STRIKE)) throw new AssertionError("Power strike not selected");
            server.runCommand("execute as @p run tp @s 0 -60 0 0 0");
            server.runCommand("summon minecraft:cow 0.5 -60 4.5 {NoAI:1b,Tags:[\"wheel_test\"]}");ctx.waitTicks(8);
            ctx.getInput().lookAt(0,8);ctx.getInput().holdKey(GLFW.GLFW_KEY_R);ctx.waitTicks(25);
            if(!server.computeOnServer(s->AbilityController.chargeTicks(s.getPlayerList().getPlayers().getFirst())>10)) throw new AssertionError("R did not charge");
            open(ctx);ctx.getInput().releaseKey(GLFW.GLFW_KEY_R);ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);close(ctx);
            server.runOnServer(s->{
                var cow=s.overworld().getAllEntities().iterator();
                while(cow.hasNext()){var e=cow.next();if(e.getType()==EntityType.COW && e.entityTags().contains("wheel_test") && e instanceof net.minecraft.world.entity.LivingEntity c && c.getHealth()!=c.getMaxHealth())throw new AssertionError("Opening wheel released the attack");}
                if(AbilityController.chargeTicks(s.getPlayerList().getPlayers().getFirst())!=0)throw new AssertionError("Charge survived cancellation");
            });
            ctx.getInput().holdKey(GLFW.GLFW_KEY_R);ctx.waitTicks(22);ctx.getInput().releaseKey(GLFW.GLFW_KEY_R);ctx.waitTicks(8);
            if(server.computeOnServer(s->{for(var e:s.overworld().getAllEntities())if(e.getType()==EntityType.COW && e.entityTags().contains("wheel_test") && e.isAlive())return true;return false;})) throw new AssertionError("R release did not execute the strike");
            server.runCommand("summon minecraft:cow 0.5 -60 4.5 {NoAI:1b,Tags:[\"wheel_test\"]}");ctx.waitTicks(5);
            choose(ctx,Ability.GRAB,-1);ctx.getInput().pressKey(GLFW.GLFW_KEY_R);ctx.waitFor(mc->AbilityInput.holding()==2,100);
            open(ctx);ctx.takeScreenshot("wheel_mind_safe_release");ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);close(ctx);
            choose(ctx,Ability.DROP,-1);ctx.getInput().pressKey(GLFW.GLFW_KEY_R);ctx.waitTicks(12);
            server.runOnServer(s->{
                var p=s.getPlayerList().getPlayers().getFirst();if(MindStone.holding(p))throw new AssertionError("Safe release retained the mob");
                boolean alive=false;for(var e:s.overworld().getAllEntities())if(e.getType()==EntityType.COW && e.entityTags().contains("wheel_test") && e.isAlive()){alive=true;if(e.isNoGravity())throw new AssertionError("Mob gravity not restored");}
                if(!alive)throw new AssertionError("Release tore the mob apart");
            });
            // Tear is an actual wheel selection while the mob remains suspended.
            server.runCommand("kill @e[tag=wheel_test]");
            server.runCommand("summon minecraft:cow 0.5 -60 4.5 {NoAI:1b,Tags:[\"wheel_test\"]}");ctx.waitTicks(8);
            choose(ctx,Ability.GRAB,-1);ctx.getInput().pressKey(GLFW.GLFW_KEY_R);ctx.waitFor(mc->AbilityInput.holding()==2,100);
            open(ctx);ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);ctx.takeScreenshot("wheel_mind_tear_selected");close(ctx);
            if(!ctx.computeOnClient(mc->AbilityInput.selected()==Ability.TEAR))throw new AssertionError("Wheel did not select tear");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_R);ctx.waitTicks(GauntletConfig.get().mindRuptureTicks+12);
            if(server.computeOnServer(s->{for(var e:s.overworld().getAllEntities())if(e.entityTags().contains("wheel_test")&&e.isAlive())return true;return false;}))throw new AssertionError("Selected tear did not kill held mob");
            // The old modifier gesture has a discoverable equivalent on the unified cast key.
            server.runCommand("summon minecraft:cow 0.5 -60 4.5 {NoAI:1b,Tags:[\"wheel_test\"]}");ctx.waitTicks(8);
            choose(ctx,Ability.GRAB,-1);ctx.getInput().pressKey(GLFW.GLFW_KEY_R);ctx.waitFor(mc->AbilityInput.holding()==2,100);
            ctx.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT);ctx.getInput().pressKey(GLFW.GLFW_KEY_R);ctx.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT);ctx.waitTicks(GauntletConfig.get().mindRuptureTicks+12);
            if(server.computeOnServer(s->{for(var e:s.overworld().getAllEntities())if(e.entityTags().contains("wheel_test")&&e.isAlive())return true;return false;}))throw new AssertionError("Shift+R did not tear held mob");
            choose(ctx,Ability.PORTAL,0);
            // Releasing V leaves the wheel open. Enter over Portal opens the destination menu.
            open(ctx);ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);ctx.getInput().pressKey(GLFW.GLFW_KEY_LEFT);
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ENTER);ctx.waitTicks(5);
            if(!ctx.computeOnClient(mc->mc.screen instanceof AbilityWheelScreen))throw new AssertionError("Portal selection skipped destination menu");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);ctx.takeScreenshot("wheel_portal_dimensions");ctx.getInput().pressKey(GLFW.GLFW_KEY_ENTER);ctx.waitFor(mc->mc.screen==null,100);ctx.waitTicks(8);
            server.runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();if(SpaceStone.destination(GauntletState.activeGauntlet(p),p.level())!=1)throw new AssertionError("Wheel did not choose End destination");});
            ctx.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT);ctx.getInput().pressKey(GLFW.GLFW_KEY_R);ctx.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT);ctx.waitTicks(8);

            server.runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();if(SpaceStone.destination(GauntletState.activeGauntlet(p),p.level())!=0)throw new AssertionError("Direct portal destination failed");});
            ctx.getInput().pressKey(GLFW.GLFW_KEY_R);ctx.waitTicks(16);ctx.takeScreenshot("wheel_portal_cast");ctx.getInput().pressKey(GLFW.GLFW_KEY_R);ctx.waitTicks(8);
            // The mystery destination is the only portal selection that opens a scene immediately.
            server.runCommand("execute as @p run tp @s 0.0 -60.0 0.0 0 0");
            choose(ctx,Ability.PORTAL,0);
            open(ctx);
            ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
            ctx.getInput().pressKey(GLFW.GLFW_KEY_LEFT);
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            ctx.waitTicks(5);
            for (int i = 0; i < 4; i++) ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
            ctx.takeScreenshot("latveria_mystery_destination");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            ctx.waitFor(mc -> mc.screen == null && AbilityInput.latveriaActive(), 100);
            ctx.waitTicks(SpaceStone.LATVERIA_ARRIVE + 36);
            ctx.takeScreenshot("latveria_doom_materializes");
            server.runOnServer(s -> {
                var player = s.getPlayerList().getPlayers().getFirst();
                if (!SpaceStone.latveriaActive(player) || SpaceStone.latveriaSealed(player) || Math.abs(player.getZ()) > 0.05) {
                    throw new AssertionError("Doom pushed or sealed Latveria before finishing his entrance");
                }
            });
            ctx.waitTicks(SpaceStone.LATVERIA_PUSH_TICK - SpaceStone.LATVERIA_ARRIVE - 36 + 14);
            ctx.takeScreenshot("latveria_doom_warning");
            ctx.waitTicks(SpaceStone.LATVERIA_DEPART - SpaceStone.LATVERIA_PUSH_TICK + 4);
            ctx.takeScreenshot("latveria_doom_dissolves");
            ctx.waitFor(mc -> AbilityInput.latveriaSealed(), 150);
            server.runOnServer(s -> {
                var player = s.getPlayerList().getPlayers().getFirst();
                if (player.level() != s.overworld() || player.getZ() >= -0.05) {
                    throw new AssertionError("Doom did not push the player back in the same dimension");
                }
                if (!SpaceStone.latveriaSealed(player) || SpaceStone.latveriaActive(player)) {
                    throw new AssertionError("Latveria did not seal after the scene");
                }
                AbilityController.select(player, Ability.PORTAL.id, SpaceStone.LATVERIA_OPTION);
                if (SpaceStone.latveriaActive(player) || SpaceStone.destination(GauntletState.activeGauntlet(player), player.level()) != 0) {
                    throw new AssertionError("The sealed teaser replayed or changed the normal destination");
                }
            });
            open(ctx);
            ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
            ctx.getInput().pressKey(GLFW.GLFW_KEY_LEFT);
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            ctx.waitTicks(5);
            for (int i = 0; i < 4; i++) ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
            ctx.takeScreenshot("latveria_permanently_sealed");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            ctx.waitTicks(8);
            if (!ctx.computeOnClient(mc -> mc.screen instanceof AbilityWheelScreen && AbilityInput.latveriaSealed())) {
                throw new AssertionError("The sealed destination remained selectable");
            }
            ctx.runOnClient(mc -> mc.setScreen(null));
            choose(ctx,Ability.FLIGHT,-1);ctx.getInput().pressKey(GLFW.GLFW_KEY_R);ctx.waitTicks(10);
            choose(ctx,Ability.GRAB,-1);
            if(!server.computeOnServer(s->s.getPlayerList().getPlayers().getFirst().hasAttached(CosmosTravel.FLIGHT)))throw new AssertionError("Changing selection stopped flight");
            choose(ctx,Ability.FLIGHT,-1);ctx.getInput().pressKey(GLFW.GLFW_KEY_R);ctx.waitTicks(10);
            for(String stone:new String[]{"reality","soul","time"})server.runCommand("execute as @p run gauntlet give_stone "+stone);
            ctx.waitTicks(90);open(ctx);ctx.takeScreenshot("wheel_full_original_gauntlet");ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);close(ctx);
            choose(ctx,Ability.BEAM,-1);ctx.getInput().holdKey(GLFW.GLFW_KEY_R);ctx.waitTicks(10);
            if(!server.computeOnServer(s->InfinityBeam.active(s.getPlayerList().getPlayers().getFirst())))throw new AssertionError("Beam did not start through R");
            open(ctx);
            ctx.getInput().releaseKey(GLFW.GLFW_KEY_R);ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);close(ctx);
            if(server.computeOnServer(s->InfinityBeam.active(s.getPlayerList().getPlayers().getFirst())))throw new AssertionError("Opening wheel did not cancel the beam");
            // A personal rewind cooldown must never prevent restoring a destroyed planet.
            server.runOnServer(s->TimeAbility.request(s.getPlayerList().getPlayers().getFirst()));
            ctx.waitTicks(GauntletConfig.get().timeRewindPlaybackTicks+12);
            server.runCommand("gamemode survival @p");
            server.runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();if(!com.dantemorrigan.gauntlet.space.PlanetDestruction.start(p,p.position()))throw new AssertionError("Planet destruction failed to start");});
            ctx.waitFor(mc->mc.level!=null && mc.level.dimension().equals(com.dantemorrigan.gauntlet.space.SpaceManager.DEAD_SPACE),400);ctx.waitTicks(12);
            server.runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();if(!com.dantemorrigan.gauntlet.space.PlanetDestruction.isBusy(p))throw new AssertionError("Missing destroyed planet record");if(!AbilityController.reason(p,Ability.REWIND).isEmpty())throw new AssertionError("Destroyed planet still blocks rewind");});
            open(ctx);ctx.getInput().pressKey(GLFW.GLFW_KEY_PAGE_UP);ctx.getInput().pressKey(GLFW.GLFW_KEY_PAGE_UP);
            ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);ctx.getInput().pressKey(GLFW.GLFW_KEY_LEFT);ctx.takeScreenshot("wheel_destroyed_planet_rewind");close(ctx);
            if(!ctx.computeOnClient(mc->AbilityInput.selected()==Ability.REWIND))throw new AssertionError("Could not select rewind in Dead Space");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_R);ctx.waitTicks(5);
            if(!server.computeOnServer(s->TimeAbility.isRewinding(s.getPlayerList().getPlayers().getFirst())))throw new AssertionError("R failed to restore destroyed planet");
            ctx.waitFor(mc->mc.level!=null && mc.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD),300);ctx.waitTicks(8);
            server.runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();if(p.hasAttached(com.dantemorrigan.gauntlet.space.SpaceManager.RECORD))throw new AssertionError("Planet restoration did not clear space record");});
        }
    }
}
