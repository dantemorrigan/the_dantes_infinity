package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.*;
import com.dantemorrigan.gauntlet.boss.*;
import com.dantemorrigan.gauntlet.cosmos.*;
import com.dantemorrigan.gauntlet.stones.StoneRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import java.util.function.BooleanSupplier;

/** Focused regression for Earth approach after a real warp and the creative testing path. */
public final class EarthReturnCreativeTest implements FabricClientGameTest {
    private static ServerPlayer p(MinecraftServer s) {return s.getPlayerList().getPlayers().getFirst();}
    private static LivingTribunal boss(MinecraftServer s) {var p=p(s);return p.level().getEntitiesOfClass(LivingTribunal.class,p.getBoundingBox().inflate(180),b->b.isAlive()).stream().findFirst().orElse(null);}
    private static void check(boolean b,String m) {if(!b)throw new AssertionError(m);}
    private static void until(ClientGameTestContext ctx,BooleanSupplier condition,String message) {
        for(int i=0;i<160;i++){if(condition.getAsBoolean())return;ctx.waitTicks(4);}throw new AssertionError(message);
    }
    @SuppressWarnings("unchecked")
    @Override public void runTest(ClientGameTestContext ctx) {
        try(var sp=ctx.worldBuilder().create()) {
            sp.getClientLevel().waitForChunksRender();var server=sp.getServer();server.runCommand("gamemode creative @p");
            server.runCommand("gamerule show_advancement_messages false");
            server.runOnServer(s->{
                ItemStack g=new ItemStack(ModItems.GAUNTLET);for(var stone:Stone.values())GauntletState.putStone(g,new ItemStack(stone.item()));
                p(s).setItemInHand(InteractionHand.OFF_HAND,g);CosmosTravel.action(p(s),0);
                p(s).teleport(new TeleportTransition(s.getLevel(CosmosTravel.DIMENSION),new Vec3(30000,256,500),Vec3.ZERO,0,0,TeleportTransition.DO_NOTHING));
                check(CosmosTravel.selectDestination(p(s),CosmosRoutes.EARTH),"Earth route rejected");CosmosTravel.warpSelected(p(s));
            });
            // The first action can share a tick with flight activation, so cast after its short input debounce.
            ctx.waitTicks(8);
            until(ctx,()->server.computeOnServer(s->{if(CosmosTravel.warpCooldown(p(s))==0)CosmosTravel.warpSelected(p(s));return CosmosTravel.warpCooldown(p(s))>0;}),"Earth warp did not start");
            until(ctx,()->server.computeOnServer(s->CosmosRoutes.earthDistance(p(s).getX(),p(s).getY(),p(s).getZ())<300),"Earth warp did not finish");
            server.runOnServer(s->{
                var e=CosmosRoutes.ALL.get(CosmosRoutes.EARTH);
                p(s).teleportTo(e.x()-CosmosRoutes.EARTH_ENTRY_RADIUS-3,e.y(),e.z());
            });ctx.waitTicks(5);
            server.runOnServer(s->{check(CosmosTravel.inCosmos(p(s)),"Atmosphere caught safe orbit");var e=CosmosRoutes.ALL.get(CosmosRoutes.EARTH);p(s).teleportTo(e.x()-CosmosRoutes.EARTH_ENTRY_RADIUS+3,e.y(),e.z());});
            until(ctx,()->server.computeOnServer(s->!CosmosTravel.inCosmos(p(s))),"Earth collision shell blocked re-entry after warp");
            server.runOnServer(s->check(p(s).hasAttached(CosmosTravel.FLIGHT) && p(s).getY()<CosmosRoutes.EXIT_HEIGHT,"Re-entry lost flight or bounced back to space"));
            until(ctx,()->server.computeOnServer(s->GauntletEffects.state(p(s)).phase==GauntletEffects.Phase.AWAKENED),"Gauntlet did not awaken");
            server.runOnServer(s->{
                p(s).teleport(new TeleportTransition(s.getLevel(CosmosTravel.DIMENSION),new Vec3(30000,256,500),Vec3.ZERO,0,-15,TeleportTransition.DO_NOTHING));
                try {var f=ThanosEncounter.class.getDeclaredField("DEFEATED");f.setAccessible(true);s.overworld().setAttached((AttachmentType<Boolean>)f.get(null),true);}catch(Exception e){throw new AssertionError(e);}
                StoneRegistry.markReleased(s,Stone.MIND);
                check(TribunalEncounter.start(p(s)).equals("started"),"Creative challenge rejected");
            });
            until(ctx,()->server.computeOnServer(s->boss(s)!=null && boss(s).phase()==LivingTribunal.BATTLE && boss(s).attack()>0 && boss(s).sceneAge()>LivingTribunal.WINDUP+2),"Creative battle never reached attack");
            server.runOnServer(s->check(p(s).getHealth()==p(s).getMaxHealth() && p(s).isCreative(),"Creative challenger took trial damage"));
            ctx.takeScreenshot("tribunal_stable_creative_body");
            server.runCommand("execute as @p run tribunal verdict");
            until(ctx,()->server.computeOnServer(s->TribunalEncounter.awaitingReturn(p(s))),"Creative verdict command failed");
            until(ctx,()->server.computeOnServer(s->!p(s).isAlive()),"Creative verdict did not finish");
            server.runOnServer(s->p(s).connection.handleClientCommand(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN)));
            until(ctx,()->server.computeOnServer(s->p(s).isAlive() && !TribunalEncounter.awaitingReturn(p(s))),"Creative checkpoint recovery failed");
            server.runOnServer(s->check(p(s).isCreative() && GauntletState.inserted(p(s).getOffhandItem()).size()==6,"Creative mode/gauntlet lost on return"));
            System.out.println("EARTH/CREATIVE: actual Earth warp, atmospheric re-entry, invulnerable creative fight, forced verdict and recovery passed");
        }
    }
}
