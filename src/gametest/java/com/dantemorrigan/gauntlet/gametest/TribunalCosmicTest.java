package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.*;
import com.dantemorrigan.gauntlet.ability.*;
import com.dantemorrigan.gauntlet.boss.*;
import com.dantemorrigan.gauntlet.cosmos.*;
import com.dantemorrigan.gauntlet.power.*;
import com.dantemorrigan.gauntlet.client.ability.*;
import com.dantemorrigan.gauntlet.client.lang.ModLang;
import com.dantemorrigan.gauntlet.stones.StoneRegistry;
import net.fabricmc.fabric.api.client.gametest.v1.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.portal.TeleportTransition;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Automatic arrival, all seven attacks, moving collision envelope, cosmic player casts and readable route cards. */
public final class TribunalCosmicTest implements FabricClientGameTest {
    private static ServerPlayer player(MinecraftServer s) {return s.getPlayerList().getPlayers().getFirst();}
    private static LivingTribunal boss(MinecraftServer s) {return player(s).level().getEntitiesOfClass(LivingTribunal.class,player(s).getBoundingBox().inflate(400),e->e.isAlive()).stream().findFirst().orElse(null);}
    private static void check(boolean v,String m) {if(!v)throw new AssertionError(m);}
    @SuppressWarnings("unchecked") private static void unlock(MinecraftServer s) {
        try {var f=ThanosEncounter.class.getDeclaredField("DEFEATED");f.setAccessible(true);s.overworld().setAttached((AttachmentType<Boolean>)f.get(null),true);}
        catch(Exception e) {throw new AssertionError(e);}
        StoneRegistry.markReleased(s,Stone.MIND);
    }
    @Override public void runTest(ClientGameTestContext ctx) {
        LegacyBindings.disable(ctx);
        try(var sp=ctx.worldBuilder().create()) {
            sp.getClientLevel().waitForChunksRender();var server=sp.getServer();
            server.runCommand("gamemode survival @p");server.runCommand("gamerule show_advancement_messages false");
            server.runOnServer(s->{
                var p=player(s);var g=new ItemStack(ModItems.GAUNTLET);for(var st:Stone.values())GauntletState.putStone(g,new ItemStack(st.item()));
                p.setItemInHand(InteractionHand.OFF_HAND,g);
            });
            for(int i=0;i<100 && !server.computeOnServer(s->GauntletEffects.state(player(s)).phase==GauntletEffects.Phase.AWAKENED);i++)ctx.waitTicks(5);
            server.runOnServer(s->{var p=player(s);CosmosTravel.action(p,0);p.teleport(new TeleportTransition(s.getLevel(CosmosTravel.DIMENSION),new Vec3(30000,256,500),Vec3.ZERO,0,0,TeleportTransition.DO_NOTHING));});
            ctx.waitTicks(10);
            // The actual warp submenu, including all localized labels, must fit and select independently of casting.
            for(String language:new String[]{"ru","en"}) {
                ctx.runOnClient(mc->{ModLang.set(language);AbilityInput.choose(Ability.WARP,-1);});ctx.waitTicks(8);
                ctx.getInput().holdKey(GLFW.GLFW_KEY_V);ctx.waitFor(mc->mc.screen instanceof AbilityWheelScreen,100);
                ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);ctx.getInput().pressKey(GLFW.GLFW_KEY_ENTER);ctx.getInput().releaseKey(GLFW.GLFW_KEY_V);ctx.waitTicks(4);
                ctx.runOnClient(mc->{
                    check(mc.screen instanceof AbilityWheelScreen,"Destination selection closed");var w=(AbilityWheelScreen)mc.screen;
                    for(int i=0;i<CosmosRoutes.ALL.size();i++) {int[] a=w.destinationBounds(i);check(a[0]>=0 && a[1]>=28 && a[0]+a[2]<=w.width && a[1]+a[3]<w.height-58,"Route card clipped");
                        for(int j=0;j<i;j++){int[] b=w.destinationBounds(j);check(a[0]>=b[0]+b[2] || b[0]>=a[0]+a[2] || a[1]>=b[1]+b[3] || b[1]>=a[1]+a[3],"Route cards overlap");}}
                });
                ctx.runOnClient(mc->{mc.getToastManager().clear();mc.gui.getChat().clearMessages(true);});
                ctx.takeScreenshot("cosmic_routes_"+language);if(language.equals("en")) {
                    for(int key=0;key<6;key++)ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
                    ctx.getInput().pressKey(GLFW.GLFW_KEY_ENTER);ctx.waitTicks(8);
                    server.runOnServer(s->check(CosmosTravel.route(player(s))==5,"Route card selection did not reach the server"));
                } else {ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);}
            }
            ctx.runOnClient(mc->ModLang.set("auto"));
            // No ground is needed by blink, the meteor stream or Reality/Soul casts.
            server.runOnServer(s->{
                var p=player(s);Vec3 before=p.position();SpaceStone.action(p,2);check(p.position().distanceTo(before)>20,"Space blink needed ground");
                for(var a:new Ability[]{Ability.REALITY_STRIKE,Ability.SOUL,Ability.SOUL_MASS,Ability.INVERSION,Ability.METEOR_STREAM}) {
                    check(AbilityController.reason(p,a).isEmpty(),"Cosmic ability blocked: "+a+" / "+AbilityController.reason(p,a));
                    CosmicGauntlet.cast(p,a,45);check(CosmicGauntlet.cooldown(p,a)>0,"Cosmic cooldown missing: "+a);
                }
                CosmicGauntlet.stop(p);
                check(!TribunalEncounter.inTrial(p),"Trial started without progression");
                unlock(s);
            });
            // Register test protection only for this world; combat remains fully simulated, including projectiles.
            MinecraftServer protectedServer=server.computeOnServer(s->s);
            net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((e,d,a)->!(e instanceof ServerPlayer p && p.level().getServer()==protectedServer));
            Set<Integer> seen=new HashSet<>();Vec3[] first={null};boolean[] moved={false};
            for(int i=0;i<650;i++) {
                int type=server.computeOnServer(s->{var b=boss(s);if(b==null)return 0;
                    Vec3 c=b.position().add(0,TribunalSpace.HEIGHT/2,0);var q=new TribunalSpace.Point(c.x,c.y,c.z);
                    check(TribunalSpace.clear(q,q,TribunalSpace.ENVELOPE),"Boss intersects planet");
                    if(first[0]==null)first[0]=b.position();else if(first[0].distanceTo(b.position())>12)moved[0]=true;
                    return b.attack();});
                if(type>0 && seen.add(type)) {ctx.waitTicks(12);ctx.takeScreenshot("tribunal_attack_"+type);}
                if(seen.size()==7 && moved[0])break;ctx.waitTicks(5);
            }
            check(seen.size()==7,"Attack variety missing: "+seen);check(moved[0],"Tribunal stayed stationary");
            server.runOnServer(s->{
                var p=player(s);var b=boss(s);check(TribunalEncounter.inTrial(p),"Automatic trial disappeared");
                p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,b.getBoundingBox().getCenter());
                float before=b.getHealth();CosmicGauntlet.cast(p,Ability.REALITY_STRIKE,0);
                check(b.getHealth()<before,"Cosmic Reality strike did not physically reach the giant");
            });
            System.out.println("COSMIC TRIBUNAL: automatic arrival, seven attacks, moving envelope, cosmic gauntlet and RU/EN route cards passed");
        }
    }
}
