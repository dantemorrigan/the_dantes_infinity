package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.*;
import com.dantemorrigan.gauntlet.ability.Ability;
import com.dantemorrigan.gauntlet.boss.*;
import com.dantemorrigan.gauntlet.client.lang.ModLang;
import com.dantemorrigan.gauntlet.client.ability.*;
import com.dantemorrigan.gauntlet.cosmos.*;
import com.dantemorrigan.gauntlet.stones.StoneRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.client.gui.components.toasts.*;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.function.BooleanSupplier;

public final class CosmicMenuPersistenceTest implements FabricClientGameTest {
    private static ServerPlayer p(MinecraftServer s){return s.getPlayerList().getPlayers().getFirst();}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void until(ClientGameTestContext ctx,BooleanSupplier condition,String message){for(int i=0;i<200;i++){if(condition.getAsBoolean())return;ctx.waitTicks(4);}throw new AssertionError(message);}
    private static boolean toast(net.minecraft.client.Minecraft mc){return mc.getToastManager().getToast(AdvancementToast.class,Toast.NO_TOKEN)!=null;}
    @SuppressWarnings("unchecked")
    @Override public void runTest(ClientGameTestContext ctx){
        LegacyBindings.disable(ctx);TestWorldSave save;
        try(var sp=ctx.worldBuilder().create()){
            save=sp.getWorldSave();sp.getClientLevel().waitForChunksRender();var server=sp.getServer();server.runCommand("gamemode creative @p");
            server.runOnServer(s->{var g=new ItemStack(ModItems.GAUNTLET);for(var stone:Stone.values())GauntletState.putStone(g,new ItemStack(stone.item()));p(s).setItemInHand(InteractionHand.OFF_HAND,g);});
            until(ctx,()->server.computeOnServer(s->GauntletEffects.state(p(s)).phase==GauntletEffects.Phase.AWAKENED),"First awakening failed");
            ctx.waitTicks(12);ctx.runOnClient(mc->mc.getToastManager().clear());
            server.runOnServer(s->ModAdvancements.award(p(s),"steal_stone"));ctx.waitTicks(6);
            check(ctx.computeOnClient(CosmicMenuPersistenceTest::toast),"New advancement toast was suppressed");
            ctx.runOnClient(mc->{
                mc.getToastManager().clear();var client=mc.getConnection().getAdvancements();var id=InfinityGauntlet.id("steal_stone");var holder=client.get(id);
                var progress=new net.minecraft.advancements.AdvancementProgress();progress.update(holder.value().requirements());holder.value().criteria().keySet().forEach(progress::grantProgress);
                client.update(new ClientboundUpdateAdvancementsPacket(false,List.of(),Set.of(),Map.of(id,progress),true));
                check(!toast(mc),"Completed progress replayed an advancement toast");
            });
            server.runOnServer(s->{var player=p(s);CosmosTravel.action(player,0);player.teleport(new TeleportTransition(s.getLevel(CosmosTravel.DIMENSION),new Vec3(27275,256,0),Vec3.ZERO,90,0,TeleportTransition.DO_NOTHING));});
            ctx.waitTicks(40);ctx.runOnClient(mc->{mc.getToastManager().clear();mc.gui.getChat().clearMessages(true);});ctx.takeScreenshot("bright_sun_corona");
            ctx.runOnClient(mc->{ModLang.set("ru");AbilityInput.choose(Ability.WARP,-1);});ctx.waitTicks(8);
            ctx.getInput().holdKey(GLFW.GLFW_KEY_V);ctx.waitFor(mc->mc.screen instanceof AbilityWheelScreen,100);
            ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);ctx.getInput().pressKey(GLFW.GLFW_KEY_ENTER);ctx.getInput().releaseKey(GLFW.GLFW_KEY_V);ctx.waitTicks(4);ctx.takeScreenshot("unique_planet_icons");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            server.runOnServer(s->{var player=p(s);player.teleport(new TeleportTransition(s.getLevel(CosmosTravel.DIMENSION),new Vec3(30000,256,500),Vec3.ZERO,0,0,TeleportTransition.DO_NOTHING));
                try{var f=ThanosEncounter.class.getDeclaredField("DEFEATED");f.setAccessible(true);s.overworld().setAttached((AttachmentType<Boolean>)f.get(null),true);}catch(Exception e){throw new AssertionError(e);}
                StoneRegistry.markReleased(s,Stone.MIND);com.dantemorrigan.gauntlet.ModAdvancements.award(player,"defeat_loki");com.dantemorrigan.gauntlet.ModAdvancements.award(player,"defeat_thanos");check(TribunalEncounter.start(player).equals("started"),"Trial start failed");});
            until(ctx,()->server.computeOnServer(s->TribunalEncounter.targetFor(p(s)).phase()==LivingTribunal.BATTLE),"Arrival failed");
            LivingTribunal[] boss={server.computeOnServer(s->TribunalEncounter.targetFor(p(s)))};float hp=server.computeOnServer(s->boss[0].getHealth());
            for(int distance:new int[]{800,30000}){
                server.runOnServer(s->{var player=p(s);player.teleport(new TeleportTransition(player.level(),new Vec3(30000+distance,256,500),Vec3.ZERO,0,0,TeleportTransition.DO_NOTHING));});ctx.waitTicks(8);
                server.runOnServer(s->{check(TribunalEncounter.targetFor(p(s))==boss[0],"Flight/warp silently closed encounter");check(boss[0].position().add(0,53,0).distanceTo(p(s).position())<115,"Boss failed to catch distant player");check(boss[0].getHealth()==hp,"Catchup reset boss HP");});
            }
            server.runCommand("execute as @p run tribunal kill");ctx.waitTicks(4);
            server.runOnServer(s->check(!boss[0].isAlive() && boss[0].phase()==LivingTribunal.DEPARTURE && !TribunalEncounter.inTrial(p(s)),"Kill command did not start victory sequence"));
        }
        try(var sp=save.open()){
            sp.getClientLevel().waitForChunksRender();var server=sp.getServer();ctx.waitTicks(12);
            server.runOnServer(s->{check(ModAdvancements.completed(p(s),"awaken_gauntlet"),"Saved advancement progress was lost");check(GauntletEffects.state(p(s)).phase==GauntletEffects.Phase.AWAKENED,"Login replayed awakening ritual");});
            check(ctx.computeOnClient(mc->!toast(mc)),"Rejoining replayed achievement notifications");
            server.runCommand("advancement revoke @p only infinity_gauntlet:steal_stone");ctx.waitTicks(6);
            server.runOnServer(s->ModAdvancements.award(p(s),"steal_stone"));ctx.waitTicks(6);
            check(ctx.computeOnClient(CosmicMenuPersistenceTest::toast),"Legitimate re-earned achievement was suppressed");ctx.runOnClient(mc->mc.getToastManager().clear());
            server.runCommand("execute as @p run tribunal challenge");
            until(ctx,()->server.computeOnServer(s->TribunalEncounter.targetFor(p(s)).phase()==LivingTribunal.BATTLE),"Second trial failed");
            server.runCommand("execute as @p run tribunal verdict");
            until(ctx,()->server.computeOnServer(s->TribunalEncounter.targetFor(p(s)).sceneAge()>=120),"Shattering did not start");ctx.takeScreenshot("six_stone_light_before_death");
            until(ctx,()->server.computeOnServer(s->!p(s).isAlive()),"Player did not die after the flash");
            System.out.println("COSMIC MENU: unique art, sun, distant boss catchup, kill, six-colour verdict, saved awakening and advancement dedup passed");
        }
    }
}
