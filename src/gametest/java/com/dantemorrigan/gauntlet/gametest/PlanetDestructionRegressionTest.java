package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.celestial.CelestialApocalypse;
import com.dantemorrigan.gauntlet.client.celestial.CelestialClient;
import com.dantemorrigan.gauntlet.space.PlanetDestruction;
import com.dantemorrigan.gauntlet.space.SpaceManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.phys.Vec3;

/** Rain + red sky -> planet destruction -> clean space -> Time rewind. */
public class PlanetDestructionRegressionTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext ctx) {
        LegacyBindings.enable(ctx);
        try(TestSingleplayerContext sp=ctx.worldBuilder().create()) {
            sp.getClientLevel().waitForChunksRender();
            sp.getServer().runCommand("execute as @p run gamemode creative");
            sp.getServer().runCommand("execute as @p run gauntlet give_power");
            sp.getServer().runCommand("execute as @p run gauntlet give_time");
            sp.getServer().runCommand("execute as @p run gauntlet celestial sun");
            ctx.waitFor(mc->CelestialClient.phase()==2,500);
            ctx.takeScreenshot("planet_regression_01_red_rain");
            // Raw admin dimension travel keeps the event alive, exercising the dimension guard itself.
            sp.getServer().runCommand("execute as @a in infinity_gauntlet:dead_space run tp @s 0 128 0");
            ctx.waitFor(mc->mc.level!=null&&mc.level.dimension().equals(SpaceManager.DEAD_SPACE),200);
            ctx.runOnClient(mc->{
                if(CelestialClient.phase()!=2 || CelestialClient.redFactor(mc.player.position(),0)!=0)
                    throw new AssertionError("An active overworld event tinted a different dimension");
            });
            sp.getServer().runCommand("execute as @a in minecraft:overworld run tp @s 0 128 0");
            ctx.waitFor(mc->mc.level!=null&&!mc.level.dimension().equals(SpaceManager.DEAD_SPACE),200);
            sp.getServer().runOnServer(server->{
                var p=server.getPlayerList().getPlayers().getFirst();
                if(!PlanetDestruction.start(p,p.position())) throw new AssertionError("Planet destruction failed during rain");
                if(server.overworld().getAttached(CelestialApocalypse.EVENT)!=null)
                    throw new AssertionError("Destroyed planet retained the persistent meteor event");
            });
            ctx.waitFor(mc->mc.level!=null&&mc.level.dimension().equals(SpaceManager.DEAD_SPACE),200);
            ctx.waitTicks(15);
            ctx.runOnClient(mc->{
                if(CelestialClient.redFactor(mc.player.position(),0)!=0 || CelestialClient.phase()!=0)
                    throw new AssertionError("Red sky or meteor state leaked into Dead Space");
            });
            ctx.getInput().lookAt(180,-5);
            ctx.takeScreenshot("planet_regression_02_large_intact_planet");
            ctx.waitTicks(100);
            ctx.takeScreenshot("planet_regression_03_primary_fracture");
            ctx.waitTicks(120);
            ctx.takeScreenshot("planet_regression_04_secondary_explosions");
            for(int yaw:new int[]{0,90,180,270}) {
                ctx.getInput().lookAt(yaw,-10);
                ctx.waitTicks(2);
                ctx.takeScreenshot("planet_regression_05_debris_"+yaw);
            }
            ctx.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_R);
            ctx.waitTicks(25);
            ctx.takeScreenshot("planet_regression_06_reassembling");
            ctx.waitFor(mc->mc.level!=null&&!mc.level.dimension().equals(SpaceManager.DEAD_SPACE),200);
            sp.getServer().runOnServer(server->{
                if(server.overworld().getAttached(CelestialApocalypse.EVENT)!=null)
                    throw new AssertionError("Returning from space resurrected the old meteor event");
            });
        }
    }
}
