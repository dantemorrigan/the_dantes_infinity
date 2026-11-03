package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.client.cosmos.CosmosClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;

/** Real movement, actual blocks and server impacts: trees, wall, ground and indestructible bedrock. */
public class SupersonicFlightTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext ctx) {
        LegacyBindings.enable(ctx);
        try(var sp=ctx.worldBuilder().create()) {
            sp.getClientLevel().waitForChunksRender();
            sp.getServer().runCommand("gamemode survival @p");
            sp.getServer().runCommand("execute as @p run gauntlet give_power");
            sp.getServer().runCommand("execute as @p run gauntlet give_stone space");
            sp.getServer().runCommand("fill -3 87 8 3 96 14 minecraft:oak_leaves[persistent=true]");
            sp.getServer().runCommand("fill -1 86 10 1 97 12 minecraft:oak_log");
            sp.getServer().runCommand("fill -12 75 50 12 105 52 minecraft:stone");
            sp.getServer().runCommand("setblock 0 90 51 minecraft:bedrock");
            sp.getServer().runCommand("execute as @p run tp @s 0 90 -65 0 0");
            ctx.waitTicks(2);ctx.getInput().pressKey(GLFW.GLFW_KEY_J);
            ctx.waitFor(mc->mc.player.getAbilities().flying,100);
            ctx.getInput().lookAt(0,0);ctx.getInput().holdKey(o->o.keyUp);
            ctx.waitTicks(5);
            if(ctx.computeOnClient(mc->CosmosClient.sonicBooms())!=0) throw new AssertionError("Slow flight triggered a sonic boom");
            ctx.getInput().holdKey(o->o.keySprint);
            ctx.waitFor(mc->CosmosClient.sonicBooms()>0,150);
            ctx.takeScreenshot("sonic_barrier");
            ctx.getInput().holdKey(o->o.keyUp);ctx.getInput().holdKey(o->o.keySprint);
            // Wait for the real condition (software rendering makes tick counts unreliable); a blocked player never gets there.
            try { ctx.waitFor(mc->mc.player.getZ()>24,300); } catch(AssertionError ignored) {}
            ctx.takeScreenshot("sonic_tree_passage");
            if(!ctx.computeOnClient(mc->mc.player.getZ()>24))
                throw new AssertionError("Tree passage blocked: "+ctx.computeOnClient(mc->mc.player.position()+" / "+mc.player.getDeltaMovement()+" impacts="+CosmosClient.impactBursts()));
            sp.getServer().runOnServer(server->{
                if(!server.overworld().getBlockState(new BlockPos(0,90,11)).isAir()) throw new AssertionError("Flight did not break the tree");
            });
            ctx.waitFor(mc->CosmosClient.impactBursts()>0,150);
            ctx.getInput().releaseKey(o->o.keyUp);ctx.getInput().releaseKey(o->o.keySprint);
            ctx.waitTicks(30);ctx.takeScreenshot("sonic_wall_crater");
            sp.getServer().runOnServer(server->{
                var level=server.overworld();
                if(!level.getBlockState(new BlockPos(0,90,50)).isAir()) throw new AssertionError("Wall crater missing");
                if(!level.getBlockState(new BlockPos(0,90,51)).is(Blocks.BEDROCK)) throw new AssertionError("Impact destroyed bedrock");
                int fire=0;
                for(BlockPos pos:BlockPos.betweenClosed(-7,83,43,7,97,57)) if(level.getBlockState(pos).is(Blocks.FIRE)) fire++;
                if(fire==0) throw new AssertionError("Wall impact did not ignite terrain");
            });
            sp.getServer().runCommand("fill 20 65 -20 40 70 0 minecraft:stone");
            sp.getServer().runCommand("execute as @p run tp @s 30 150 -10 0 90");
            ctx.waitTicks(45);ctx.getInput().lookAt(0,90);
            ctx.getInput().holdKey(o->o.keyUp);ctx.getInput().holdKey(o->o.keySprint);
            ctx.waitFor(mc->CosmosClient.impactBursts()>1,200);
            ctx.getInput().releaseKey(o->o.keyUp);ctx.getInput().releaseKey(o->o.keySprint);
            ctx.waitTicks(30);ctx.takeScreenshot("sonic_ground_crater");
            sp.getServer().runOnServer(server->{
                if(!server.overworld().getBlockState(new BlockPos(30,70,-10)).isAir()) throw new AssertionError("Ground crater missing");
            });
            ctx.getInput().pressKey(GLFW.GLFW_KEY_J);ctx.waitTicks(8);
            if(ctx.computeOnClient(mc->mc.player.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING)))
                throw new AssertionError("Flight restored the slow-falling regression");
        }
    }
}
