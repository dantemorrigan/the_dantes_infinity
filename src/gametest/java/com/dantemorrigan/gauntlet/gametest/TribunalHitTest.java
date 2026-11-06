package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.*;
import com.dantemorrigan.gauntlet.boss.*;
import com.dantemorrigan.gauntlet.cosmos.*;
import com.dantemorrigan.gauntlet.power.*;
import com.dantemorrigan.gauntlet.ability.Ability;
import com.dantemorrigan.gauntlet.client.fx.CosmicClient;
import com.dantemorrigan.gauntlet.stones.StoneRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.portal.TeleportTransition;
import java.util.function.BooleanSupplier;

/** Giant-section targeting, overlapping gauntlet hits, one-shot inversion and a defeat without changing boss health. */
public final class TribunalHitTest implements FabricClientGameTest {
    private static ServerPlayer p(MinecraftServer s){return s.getPlayerList().getPlayers().getFirst();}
    private static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    private static void until(ClientGameTestContext ctx,BooleanSupplier condition,String message){for(int i=0;i<180;i++){if(condition.getAsBoolean())return;ctx.waitTicks(4);}throw new AssertionError(message);}
    @SuppressWarnings("unchecked")
    @Override public void runTest(ClientGameTestContext ctx){
        try(var sp=ctx.worldBuilder().create()){
            sp.getClientLevel().waitForChunksRender();var server=sp.getServer();server.runCommand("gamemode creative @p");server.runCommand("gamerule show_advancement_messages false");
            server.runOnServer(s->{var p=p(s);var g=new ItemStack(ModItems.GAUNTLET);for(var stone:Stone.values())GauntletState.putStone(g,new ItemStack(stone.item()));p.setItemInHand(InteractionHand.OFF_HAND,g);CosmosTravel.action(p,0);});
            until(ctx,()->server.computeOnServer(s->GauntletEffects.state(p(s)).phase==GauntletEffects.Phase.AWAKENED),"Awakening failed");
            server.runOnServer(s->{
                var p=p(s);p.teleport(new TeleportTransition(s.getLevel(CosmosTravel.DIMENSION),new Vec3(30000,256,500),Vec3.ZERO,0,-10,TeleportTransition.DO_NOTHING));
                try{var f=ThanosEncounter.class.getDeclaredField("DEFEATED");f.setAccessible(true);s.overworld().setAttached((AttachmentType<Boolean>)f.get(null),true);}catch(Exception e){throw new AssertionError(e);}
                StoneRegistry.markReleased(s,Stone.MIND);check(TribunalEncounter.start(p).equals("started"),"Creative trial failed");
            });
            until(ctx,()->server.computeOnServer(s->TribunalEncounter.targetFor(p(s)).sceneAge()>78),"No emergence");ctx.takeScreenshot("tribunal_emerging_through_horizon");
            until(ctx,()->server.computeOnServer(s->TribunalEncounter.targetFor(p(s)).phase()==LivingTribunal.BATTLE),"Arrival stuck");
            float[] inversionHealth={0};
            server.runOnServer(s->{
                var p=p(s);var b=TribunalEncounter.targetFor(p);var g=p.getOffhandItem();
                for(double height:new double[]{8,53,97}){
                    Vec3 point=b.position().add(0,height,0);p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,point);
                    check(PowerAbility.pick(p,170) instanceof EntityHitResult,"Giant ray missed height "+height);
                }
                p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,b.getBoundingBox().getCenter());
                g.set(ModComponents.POWER_MODE,PowerAbility.MODE_STRIKE);float before=b.getHealth();PowerAbility.release(p,g,25);
                check(b.getHealth()<before-20,"Power strike did not damage giant chest");
                before=b.getHealth();CosmicGauntlet.cast(p,Ability.REALITY_STRIKE,0);check(b.getHealth()<before-20,"Reality strike rejected a consecutive ability hit");
                before=b.getHealth();CosmicGauntlet.cast(p,Ability.INVERSION,0);check(b.getHealth()<before-20,"Inversion did not hit surrounding volume");inversionHealth[0]=b.getHealth();
            });
            ctx.waitTicks(32);ctx.takeScreenshot("cosmic_space_inversion");
            check(ctx.computeOnClient(mc->CosmicClient.distortion()>.1),"Space distortion never became visible");
            server.runOnServer(s->{var p=p(s);var b=TribunalEncounter.targetFor(p);check(b.getHealth()==inversionHealth[0],"Inversion damaged more than once per cast");CosmicGauntlet.stop(p);
                p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,b.getBoundingBox().getCenter());TimeFlow.start(p,p.getOffhandItem());});
            until(ctx,()->server.computeOnServer(s->TribunalEncounter.targetFor(p(s)).getHealth()<inversionHealth[0]),"Time Flow missed giant volume");
            server.runOnServer(s->{
                var p=p(s);var b=TribunalEncounter.targetFor(p);TimeFlow.cancel(p);
                // Exercise real ability damage routing repeatedly; never set the target's health.
                for(int i=0;i<40 && b.isAlive();i++) com.dantemorrigan.gauntlet.uru.GauntletDamage.hurt(p.level(),b,p,p.damageSources().playerAttack(p),90);
                check(!b.isAlive() && !TribunalEncounter.inTrial(p),"Full-gauntlet damage could not defeat the Tribunal");
            });
            System.out.println("TRIBUNAL HITS: feet/chest/head, Power, Reality, single-hit inversion, Time Flow and full-health defeat passed");
        }
    }
}
