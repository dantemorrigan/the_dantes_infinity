package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.cosmos.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.lwjgl.glfw.GLFW;

/** Real key input: reject missing stone, ascend, warp through the catalogue, return, revoke flight. */
public class CosmosTravelTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext ctx) {
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
            for(int i=1;i<6;i++) {
                ctx.getInput().pressKey(GLFW.GLFW_KEY_K);ctx.waitTicks(8);
                ctx.getInput().pressKey(GLFW.GLFW_KEY_U);ctx.waitTicks(70);
                final var d=CosmosRoutes.ALL.get(i);
                if(!ctx.computeOnClient(mc->mc.player.position().distanceToSqr(d.arrivalX(),d.arrivalY(),d.arrivalZ())<400))
                    throw new AssertionError("Warp failed: "+d.id());
                if(d.field()) {
                    sp.getServer().runOnServer(server -> {
                        var space=server.getLevel(CosmosTravel.DIMENSION);
                        int solid=0;
                        for(int x=-100;x<=100;x+=4) for(int y=-40;y<=40;y+=4) for(int z=-100;z<=100;z+=4)
                            if(!space.getBlockState(new net.minecraft.core.BlockPos((int)d.x()+x,(int)d.y()+y,(int)d.z()+z)).isAir()) solid++;
                        if(solid<20) throw new AssertionError("Asteroid field has no physical blocks: "+d.id()+" / "+solid);
                    });
                    sp.getClientLevel().waitForChunksRender();
                }
                ctx.takeScreenshot("cosmos_"+d.id());ctx.waitTicks(100);
            }
            ctx.getInput().pressKey(GLFW.GLFW_KEY_K);ctx.waitTicks(8);
            ctx.getInput().pressKey(GLFW.GLFW_KEY_U);ctx.waitTicks(70);
            ctx.getInput().pressKey(GLFW.GLFW_KEY_J);
            ctx.waitFor(mc->mc.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD),200);
            ctx.waitTicks(10);
            if(ctx.computeOnClient(mc->mc.player.getAbilities().mayfly)) throw new AssertionError("Flight leaked after return");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_J);ctx.waitTicks(10);
            sp.getServer().runCommand("execute as @p run item replace entity @s weapon.offhand with minecraft:air");
            ctx.waitFor(mc->!mc.player.getAbilities().mayfly,100);
        }
    }
}
