package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.*;
import com.dantemorrigan.gauntlet.ability.*;
import com.dantemorrigan.gauntlet.client.ability.*;
import com.dantemorrigan.gauntlet.cosmos.CosmosTravel;
import com.dantemorrigan.gauntlet.power.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.world.entity.EntityType;
import org.lwjgl.glfw.GLFW;

/** Exercises real V/R input, destructive-cast cancellation and safe telekinetic release. */
public class AbilityWheelTest implements FabricClientGameTest {
    private static void open(ClientGameTestContext ctx) {
        ctx.getInput().holdKey(GLFW.GLFW_KEY_V);
        ctx.waitFor(mc->mc.screen instanceof AbilityWheelScreen,100);ctx.waitTicks(3);
    }
    private static void close(ClientGameTestContext ctx) {
        ctx.getInput().releaseKey(GLFW.GLFW_KEY_V);ctx.waitFor(mc->mc.screen==null,100);ctx.waitTicks(8);
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
            open(ctx);ctx.getInput().pressKey(GLFW.GLFW_KEY_LEFT);close(ctx);
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
            // Releasing V over Portal keeps its destination menu open.
            open(ctx);ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);ctx.getInput().pressKey(GLFW.GLFW_KEY_LEFT);
            ctx.getInput().releaseKey(GLFW.GLFW_KEY_V);ctx.waitTicks(5);
            if(!ctx.computeOnClient(mc->mc.screen instanceof AbilityWheelScreen))throw new AssertionError("Portal release skipped destination menu");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);ctx.takeScreenshot("wheel_portal_dimensions");ctx.getInput().pressKey(GLFW.GLFW_KEY_ENTER);ctx.waitFor(mc->mc.screen==null,100);ctx.waitTicks(8);
            server.runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();if(SpaceStone.destination(GauntletState.activeGauntlet(p),p.level())!=1)throw new AssertionError("Wheel did not choose End destination");});
            ctx.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT);ctx.getInput().pressKey(GLFW.GLFW_KEY_R);ctx.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT);ctx.waitTicks(8);

            server.runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();if(SpaceStone.destination(GauntletState.activeGauntlet(p),p.level())!=0)throw new AssertionError("Direct portal destination failed");});
            ctx.getInput().pressKey(GLFW.GLFW_KEY_R);ctx.waitTicks(16);ctx.takeScreenshot("wheel_portal_cast");ctx.getInput().pressKey(GLFW.GLFW_KEY_R);ctx.waitTicks(8);
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
