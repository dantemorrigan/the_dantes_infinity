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
            open(ctx);ctx.takeScreenshot("wheel_partial_original_items");
            if(!ctx.computeOnClient(mc->!AbilityInput.selectable(Ability.SOUL_MASS))) throw new AssertionError("Missing Soul stone was not locked");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);close(ctx);
            if(!ctx.computeOnClient(mc->AbilityInput.selected()==Ability.PLANET)) throw new AssertionError("Wheel keyboard selection failed");
            server.runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();if(AbilityController.chargeTicks(p)>0 || com.dantemorrigan.gauntlet.space.PlanetDestruction.isBusy(p)) throw new AssertionError("Selection activated planet destruction");});
            open(ctx);ctx.getInput().pressKey(GLFW.GLFW_KEY_LEFT);close(ctx);
            if(!ctx.computeOnClient(mc->AbilityInput.selected()==Ability.STRIKE)) throw new AssertionError("Power strike not selected");
            server.runCommand("execute as @p run tp @s 0 -60 0 0 0");
            server.runCommand("summon minecraft:cow 0.5 -60 4.5 {NoAI:1b,Tags:[\"wheel_test\"]}");ctx.waitTicks(8);
            ctx.getInput().lookAt(0,0);ctx.getInput().holdKey(GLFW.GLFW_KEY_R);ctx.waitTicks(25);
            if(!server.computeOnServer(s->AbilityController.chargeTicks(s.getPlayerList().getPlayers().getFirst())>10)) throw new AssertionError("R did not charge");
            open(ctx);ctx.getInput().releaseKey(GLFW.GLFW_KEY_R);ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);close(ctx);
            server.runOnServer(s->{
                var cow=s.overworld().getAllEntities().iterator();
                while(cow.hasNext()){var e=cow.next();if(e.getType()==EntityType.COW && e instanceof net.minecraft.world.entity.LivingEntity c && c.getHealth()!=c.getMaxHealth())throw new AssertionError("Opening wheel released the attack");}
                if(AbilityController.chargeTicks(s.getPlayerList().getPlayers().getFirst())!=0)throw new AssertionError("Charge survived cancellation");
            });
            ctx.getInput().holdKey(GLFW.GLFW_KEY_R);ctx.waitTicks(22);ctx.getInput().releaseKey(GLFW.GLFW_KEY_R);ctx.waitTicks(8);
            if(server.computeOnServer(s->{for(var e:s.overworld().getAllEntities())if(e.getType()==EntityType.COW && e.isAlive())return true;return false;})) throw new AssertionError("R release did not execute the strike");
            server.runCommand("summon minecraft:cow 0.5 -60 4.5 {NoAI:1b,Tags:[\"wheel_test\"]}");ctx.waitTicks(5);
            choose(ctx,Ability.GRAB,-1);ctx.getInput().pressKey(GLFW.GLFW_KEY_R);ctx.waitFor(mc->AbilityInput.holding()==2,100);
            open(ctx);ctx.takeScreenshot("wheel_mind_safe_release");ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);close(ctx);
            choose(ctx,Ability.DROP,-1);ctx.getInput().pressKey(GLFW.GLFW_KEY_R);ctx.waitTicks(12);
            server.runOnServer(s->{
                var p=s.getPlayerList().getPlayers().getFirst();if(MindStone.holding(p))throw new AssertionError("Safe release retained the mob");
                boolean alive=false;for(var e:s.overworld().getAllEntities())if(e.getType()==EntityType.COW && e.isAlive()){alive=true;if(e.isNoGravity())throw new AssertionError("Mob gravity not restored");}
                if(!alive)throw new AssertionError("Release tore the mob apart");
            });
            choose(ctx,Ability.PORTAL,0);
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
        }
    }
}
