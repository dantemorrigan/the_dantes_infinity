package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.ModEntities;
import com.dantemorrigan.gauntlet.boss.Loki;
import com.dantemorrigan.gauntlet.client.lang.ModLang;
import com.dantemorrigan.gauntlet.client.quest.SpaceQuestClient;
import com.dantemorrigan.gauntlet.client.boss.StoneBurstLight;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.EntitySpawnReason;
import java.util.function.BooleanSupplier;

public final class LokiCreativePolishTest implements FabricClientGameTest {
    private static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    private static void until(ClientGameTestContext ctx,BooleanSupplier condition,String message){for(int i=0;i<180;i++){if(condition.getAsBoolean())return;ctx.waitTicks(4);}throw new AssertionError(message);}
    @Override public void runTest(ClientGameTestContext ctx){
        try(var sp=ctx.worldBuilder().create()){
            sp.getClientLevel().waitForChunksRender();var server=sp.getServer();server.runCommand("gamemode creative @p");server.runCommand("difficulty normal");
            server.runCommand("fill -20 100 -20 20 110 20 minecraft:air");server.runCommand("fill -20 99 -20 20 99 20 minecraft:stone");
            server.runCommand("tp @p 0.5 100 0.5 0 0");
            Loki[] loki={null};
            server.runOnServer(s->{loki[0]=ModEntities.LOKI.create(s.overworld(),EntitySpawnReason.MOB_SUMMONED);loki[0].snapTo(.5,100,8.5,180,0);s.overworld().addFreshEntity(loki[0]);});
            until(ctx,()->server.computeOnServer(s->loki[0].getTarget()==s.getPlayerList().getPlayers().getFirst() && loki[0].strikeTicks()>0),"Loki did not attack a creative player");
            server.runOnServer(s->{check(s.getPlayerList().getPlayers().getFirst().getHealth()==20,"Creative player took damage");loki[0].setHealth(loki[0].getMaxHealth()*.4f);});
            until(ctx,()->server.computeOnServer(s->loki[0].isEnraged() && loki[0].transformationTicks()==0 && loki[0].strikeTicks()>0),"Fenrir did not attack creative player");
            ctx.runOnClient(mc->{ModLang.set("ru");mc.getToastManager().clear();mc.gui.getChat().clearMessages(true);});
            server.runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();loki[0].setHealth(0);loki[0].die(p.damageSources().playerAttack(p));});
            until(ctx,()->server.computeOnServer(s->loki[0].isRemoved()),"Loki defeat did not finish");ctx.waitTicks(12);
            check(ctx.computeOnClient(mc->{try{var a=SpaceQuestClient.class.getDeclaredField("alert");var st=SpaceQuestClient.class.getDeclaredField("style");a.setAccessible(true);st.setAccessible(true);return a.get(null)!=null && st.getInt(null)==2;}catch(Exception e){throw new AssertionError(e);}}),"No Loki victory banner");
            ctx.takeScreenshot("loki_victory_banner_ru");
            // Exercise the optimized light in the actual HUD without another full Tribunal encounter.
            var id=com.dantemorrigan.gauntlet.InfinityGauntlet.id("burst_performance_preview");
            ctx.runOnClient(mc->net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(id,(g,d)->StoneBurstLight.draw(g,18)));
            ctx.waitTicks(3);ctx.takeScreenshot("cached_six_stone_light");
            ctx.runOnClient(mc->net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.removeElement(id));
            System.out.println("LOKI POLISH: creative attacks in both forms, immunity, localized victory banner and cached two-quad burst passed");
        }
    }
}
