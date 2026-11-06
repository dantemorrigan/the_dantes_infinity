package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.cosmos.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.lwjgl.glfw.GLFW;

/** Real key input: reject missing stone, ascend, warp through the catalogue, return, revoke flight. */
public class CosmosTravelTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext ctx) {
        LegacyBindings.enable(ctx);
        try(var sp=ctx.worldBuilder().create()) {
            sp.getClientLevel().waitForChunksRender();
            sp.getServer().runCommand("gamemode survival @p"); ctx.waitTicks(5);
            ctx.getInput().pressKey(GLFW.GLFW_KEY_J); ctx.waitTicks(8);
            if(ctx.computeOnClient(mc->mc.player.getAbilities().mayfly)) throw new AssertionError("Flight without stone");
            sp.getServer().runCommand("execute as @p run gauntlet give_power");
            sp.getServer().runCommand("execute as @p run gauntlet give_stone space");
            ctx.waitTicks(10);
            ctx.getInput().pressKey(GLFW.GLFW_KEY_J);
            ctx.waitFor(mc->mc.player.getAbilities().flying,100);
            sp.getServer().runCommand("execute as @p at @p run tp @s ~ 630 ~");
            ctx.waitTicks(5); ctx.getInput().holdKey(o->o.keyJump);
            ctx.waitFor(mc->mc.level.dimension().equals(CosmosTravel.DIMENSION),200);
            ctx.getInput().releaseKey(o->o.keyJump);
            ctx.waitFor(mc->mc.screen==null,400); ctx.waitTicks(20);
            ctx.getInput().lookAt(180,60); ctx.takeScreenshot("cosmos_earth_orbit");
            ctx.getInput().lookAt(0,0);
            ctx.getInput().holdKey(o->o.keyUp); ctx.getInput().holdKey(o->o.keySprint);
            ctx.waitTicks(30);
            if(ctx.computeOnClient(mc->mc.player.getDeltaMovement().length())<1.6) throw new AssertionError("Boost did not accelerate");
            ctx.takeScreenshot("cosmos_boost");
            ctx.getInput().releaseKey(o->o.keyUp); ctx.getInput().releaseKey(o->o.keySprint); ctx.waitTicks(30);
            // Point the crosshair at a body (server-side look) and cast the aimed warp exactly like the wheel's R does.
            java.util.function.Consumer<CosmosRoutes.Destination> warpAimed=t -> {
                sp.getServer().runCommand(String.format(java.util.Locale.ROOT,"execute as @p at @p run tp @s ~ ~ ~ facing %.1f %.1f %.1f",t.x(),t.y(),t.z()));
                ctx.waitTicks(10);
                // Software rendering starves the server: wait for the real cooldown, then cast and wait for arrival.
                for(int i=0;i<60 && sp.getServer().computeOnServer(server -> CosmosTravel.warpCooldown(server.getPlayerList().getPlayers().getFirst())>0);i++) ctx.waitTicks(20);
                sp.getServer().runOnServer(server -> CosmosTravel.action(server.getPlayerList().getPlayers().getFirst(),2));
                ctx.waitFor(mc->mc.player.position().distanceToSqr(t.arrivalX(),t.arrivalY(),t.arrivalZ())<400,1200);
            };
            for(int i=0;i<CosmosRoutes.ALL.size();i++) {
                if(i==CosmosRoutes.EARTH) continue;
                final var d=CosmosRoutes.ALL.get(i);
                final int index=i;
                // A body hidden behind the Sun from here is correctly not aimable: skip it.
                if(ctx.computeOnClient(mc->{var e=mc.player.getEyePosition();var v=new net.minecraft.world.phys.Vec3(d.x()-e.x,d.y()-e.y,d.z()-e.z());
                    return CosmosRoutes.aim(e.x,e.y,e.z,v.x,v.y,v.z,2.5)!=index;})) continue;
                warpAimed.accept(d);
                if(!ctx.computeOnClient(mc->mc.player.position().distanceToSqr(d.arrivalX(),d.arrivalY(),d.arrivalZ())<400))
                    throw new AssertionError("Aimed warp failed: "+d.id());
                ctx.waitTicks(40); ctx.takeScreenshot("cosmos_"+d.id());ctx.waitTicks(80);
            }
            // An explicit route must work even when the crosshair is aimed far away from the chosen body.
            var earth = CosmosRoutes.ALL.get(CosmosRoutes.EARTH);
            sp.getServer().runCommand("execute as @p at @p run tp @s ~ ~ ~ ~ -90");
            for (int i = 0; i < 60 && sp.getServer().computeOnServer(
                    server -> CosmosTravel.warpCooldown(server.getPlayerList().getPlayers().getFirst()) > 0); i++) ctx.waitTicks(20);
            sp.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                if (!CosmosTravel.selectDestination(player, CosmosRoutes.EARTH)) throw new AssertionError("Route selection rejected");
                CosmosTravel.warpSelected(player);
            });
            ctx.waitFor(mc -> mc.player.position().distanceToSqr(earth.arrivalX(), earth.arrivalY(), earth.arrivalZ()) < 400, 1200);
            ctx.getInput().pressKey(GLFW.GLFW_KEY_J);
            ctx.waitFor(mc->mc.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD),200);
            ctx.waitTicks(10);
            // Re-entry intentionally retains manual flight in the current main.
            if(!ctx.computeOnClient(mc->mc.player.getAbilities().flying)) throw new AssertionError("Re-entry lost flight");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_J); ctx.waitTicks(8);
            if(ctx.computeOnClient(mc->mc.player.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING)))
                throw new AssertionError("Flight exit changed falling physics");
            if(ctx.computeOnClient(mc->mc.player.getAbilities().mayfly || mc.player.isNoGravity()))
                throw new AssertionError("Flight state leaked after disabling");
            ctx.waitTicks(20);
            if(ctx.computeOnClient(mc->mc.player.getDeltaMovement().y)>-.5)
                throw new AssertionError("Normal gravity did not resume");
            ctx.waitFor(mc->mc.player.onGround(),300);
            if(!ctx.computeOnClient(mc->mc.player.isAlive())) throw new AssertionError("First landing after flight killed the player");
            // A fresh ordinary jump must finish promptly, rather than float with the old 15-second effect.
            ctx.getInput().pressKey(GLFW.GLFW_KEY_SPACE); ctx.waitTicks(3);
            ctx.waitFor(mc->mc.player.onGround(),30);
            ctx.getInput().pressKey(GLFW.GLFW_KEY_J);ctx.waitTicks(10);
            sp.getServer().runCommand("execute as @p run item replace entity @s weapon.offhand with minecraft:air");
            ctx.waitFor(mc->!mc.player.getAbilities().mayfly,100);
        }
    }
}
