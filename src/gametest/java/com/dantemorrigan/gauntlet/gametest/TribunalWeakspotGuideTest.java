package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.*;
import com.dantemorrigan.gauntlet.boss.*;
import com.dantemorrigan.gauntlet.cosmos.*;
import com.dantemorrigan.gauntlet.power.*;
import com.dantemorrigan.gauntlet.uru.GauntletDamage;
import com.dantemorrigan.gauntlet.guide.GuideBook;
import com.dantemorrigan.gauntlet.client.lang.ModLang;
import com.dantemorrigan.gauntlet.stones.StoneRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import org.lwjgl.glfw.GLFW;
import java.util.function.BooleanSupplier;

public final class TribunalWeakspotGuideTest implements FabricClientGameTest {
    private static ServerPlayer p(MinecraftServer s){return s.getPlayerList().getPlayers().getFirst();}
    private static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    private static void until(ClientGameTestContext ctx,BooleanSupplier condition,String message){for(int i=0;i<200;i++){if(condition.getAsBoolean())return;ctx.waitTicks(4);}throw new AssertionError(message);}
    @SuppressWarnings("unchecked")
    @Override public void runTest(ClientGameTestContext ctx){
        try(var sp=ctx.worldBuilder().create()){
            sp.getClientLevel().waitForChunksRender();var server=sp.getServer();server.runCommand("gamemode creative @p");
            server.runOnServer(s->{var book=GuideBook.create();var content=book.get(DataComponents.WRITTEN_BOOK_CONTENT);book.set(DataComponents.WRITTEN_BOOK_CONTENT,content.withReplacedPages(content.pages().subList(0,19)));p(s).setItemInHand(InteractionHand.MAIN_HAND,book);});
            ctx.runOnClient(mc->ModLang.set("ru"));ctx.waitTicks(5);ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            ctx.waitFor(mc->mc.screen instanceof BookViewScreen,100);
            ctx.runOnClient(mc->{var book=mc.player.getMainHandItem().get(DataComponents.WRITTEN_BOOK_CONTENT);check(book.pages().size()==22,"Existing guide did not refresh");
                String text=book.pages().stream().map(p->p.raw().getString()).reduce("",String::concat).toLowerCase();check(text.contains("локи") && !text.contains("трибун"),"Guide omitted Loki or spoiled final boss");
                ((BookViewScreen)mc.screen).setPage(12);});ctx.waitTicks(3);ctx.takeScreenshot("guide_loki_ru");
            ctx.runOnClient(mc->((BookViewScreen)mc.screen).setPage(13));ctx.waitTicks(2);ctx.takeScreenshot("guide_cosmos_ru");
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            server.runOnServer(s->{var player=p(s);var g=new ItemStack(ModItems.GAUNTLET);for(var stone:Stone.values())GauntletState.putStone(g,new ItemStack(stone.item()));player.setItemInHand(InteractionHand.OFF_HAND,g);
                try{var f=ThanosEncounter.class.getDeclaredField("DEFEATED");f.setAccessible(true);s.overworld().setAttached((AttachmentType<Boolean>)f.get(null),true);}catch(Exception e){throw new AssertionError(e);}
                StoneRegistry.markReleased(s,Stone.MIND);CosmosTravel.action(player,0);player.teleport(new TeleportTransition(s.getLevel(CosmosTravel.DIMENSION),new Vec3(30000,256,500),Vec3.ZERO,0,0,TeleportTransition.DO_NOTHING));});
            until(ctx,()->server.computeOnServer(s->GauntletEffects.state(p(s)).phase==GauntletEffects.Phase.AWAKENED),"Awakening failed");
            ctx.waitTicks(70);server.runOnServer(s->{check(!TribunalEncounter.eligible(p(s)) && !TribunalEncounter.inTrial(p(s)),"Another player's world victories unlocked encounter");ModAdvancements.award(p(s),"defeat_loki");check(!TribunalEncounter.eligible(p(s)),"Loki alone unlocked encounter");ModAdvancements.award(p(s),"defeat_thanos");check(TribunalEncounter.eligible(p(s)),"Creative victor not eligible");});
            until(ctx,()->server.computeOnServer(s->TribunalEncounter.inTrial(p(s))),"Creative automatic arrival failed");
            until(ctx,()->server.computeOnServer(s->TribunalEncounter.targetFor(p(s)).phase()==LivingTribunal.BATTLE),"Intro did not finish");
            float[] bodyDamage={0};
            server.runOnServer(s->{var player=p(s);var b=TribunalEncounter.targetFor(player);
                int count=0;for(var part:TribunalHitbox.parts(b)) {
                    Vec3 at=part.getCenter();check(CombatTargets.inArea(player,new AABB(at,at).inflate(1)).contains(b),"Animated body section was not indexed");
                    check(CombatTargets.near(b,at,1),"Animated body section failed radial hit");
                    player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,at);check(PowerAbility.pick(player,170) instanceof EntityHitResult,"Aimed strike missed part "+count++);
                }
                var outsider=new ServerPlayer(s,player.level(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"Outsider"),net.minecraft.server.level.ClientInformation.createDefault());
                float before=b.getHealth();check(TribunalEncounter.targetFor(outsider)==null && !b.hurtByGauntlet(player.level(),outsider,40,TribunalHitbox.core(b).getCenter()) && b.getHealth()==before,"Uninvited player joined combat");
                Vec3 leg=TribunalHitbox.parts(b).get(2).getCenter();check(GauntletDamage.hurt(player.level(),b,player,player.damageSources().playerAttack(player),40,leg),"Body damage was rejected");bodyDamage[0]=before-b.getHealth();
            });
            ctx.waitTicks(24);
            server.runOnServer(s->{var player=p(s);var b=TribunalEncounter.targetFor(player);float before=b.getHealth();check(GauntletDamage.hurt(player.level(),b,player,player.damageSources().playerAttack(player),40,TribunalHitbox.core(b).getCenter()),"Core damage rejected");check(before-b.getHealth()>bodyDamage[0]*2,"Core did not amplify damage");});
            for(int i=0;i<10;i++) {ctx.waitTicks(8);server.runOnServer(s->{var player=p(s);var b=TribunalEncounter.targetFor(player);float before=b.getHealth();check(GauntletDamage.hurt(player.level(),b,player,player.damageSources().playerAttack(player),48,TribunalHitbox.core(b).getCenter()) && b.getHealth()<before,"Continuous pulse lost damage");});}
            server.runCommand("execute as @p run tribunal kill");
            System.out.println("FINAL MICRO: existing guide update, Loki, no spoilers, personal gates, creative auto-arrival, every body part, core bonus and continuous hits passed");
        }
    }
}
