package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.power.InfinityBeam;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

/** Integration assertions run on the server thread, including the dragon's parent HP budget. */
public class InfinityBeamTest implements FabricClientGameTest {
    private static <T> T onServer(MinecraftServer server, Callable<T> action) {
        FutureTask<T> task = new FutureTask<>(action);
        server.execute(task);
        try { return task.get(10, java.util.concurrent.TimeUnit.SECONDS); }
        catch (Exception e) { throw new AssertionError("Server beam assertion failed", e); }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void command(TestSingleplayerContext sp, String cmd) {
        sp.getServer().runCommand("execute as @p at @p run " + cmd);
    }
    @Override public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getClientLevel().waitForChunksRender();
            MinecraftServer server = ctx.computeOnClient(mc -> mc.getSingleplayerServer());
            command(sp, "gamemode creative");
            command(sp, "tp @p 0 100 0 0 0");
            command(sp, "gauntlet give_power");
            ctx.waitTicks(5);
            onServer(server, () -> {
                var p = server.getPlayerList().getPlayers().getFirst();
                InfinityBeam.request(p, true);
                check(!InfinityBeam.active(p), "Partial gauntlet must not fire");
                return null;
            });
            for (String stone : new String[]{"space", "reality", "soul", "mind", "time"})
                command(sp, "gauntlet give_stone " + stone);
            ctx.waitTicks(90);
            command(sp, "fill -5 98 10 5 107 16 minecraft:stone");
            ctx.waitTicks(5);
            onServer(server, () -> {
                var p = server.getPlayerList().getPlayers().getFirst();
                InfinityBeam.request(p, true);
                check(InfinityBeam.active(p), "Awakened gauntlet should charge");
                return null;
            });
            ctx.waitTicks(15);
            ctx.takeScreenshot("beam_01_charge");
            onServer(server, () -> {
                var p = server.getPlayerList().getPlayers().getFirst();
                check(p.level().getBlockState(new BlockPos(0, 101, 12)).is(Blocks.STONE), "Charge must not destroy blocks");
                InfinityBeam.cancel(p);
                return null;
            });
            ctx.waitTicks(5);
            onServer(server, () -> { InfinityBeam.request(server.getPlayerList().getPlayers().getFirst(), true); return null; });
            ctx.waitTicks(55);
            ctx.takeScreenshot("beam_02_drilling");
            ctx.waitTicks(Math.max(1, GauntletConfig.get().beamChargeTicks + GauntletConfig.get().beamFireTicks - 55 + 10));
            onServer(server, () -> {
                var p = server.getPlayerList().getPlayers().getFirst();
                check(p.level().getBlockState(new BlockPos(0, 101, 12)).isAir(), "Beam must drill stone");
                check(!InfinityBeam.active(p), "Finished beam must release its state");
                return null;
            });
            command(sp, "summon minecraft:ender_dragon 0 100 25 {NoAI:1b}");
            ctx.waitTicks(5);
            float before = onServer(server, () -> server.overworld().getEntitiesOfClass(EnderDragon.class,
                    new AABB(-32, 80, -32, 32, 120, 64)).getFirst().getHealth());
            onServer(server, () -> { InfinityBeam.request(server.getPlayerList().getPlayers().getFirst(), true); return null; });
            ctx.waitTicks(GauntletConfig.get().beamChargeTicks + GauntletConfig.get().beamFireTicks + 5);
            onServer(server, () -> {
                var dragon = server.overworld().getEntitiesOfClass(EnderDragon.class,
                        new AABB(-32, 80, -32, 32, 120, 64)).getFirst();
                check(Math.abs(dragon.getHealth() - (before - dragon.getMaxHealth() * .7f)) < .1f,
                        "Full connected beam must remove exactly 70% max HP once per parent dragon");
                return null;
            });
        }
    }
}
