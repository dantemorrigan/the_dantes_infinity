package com.dantemorrigan.gauntlet.gametest;

import com.dantemorrigan.gauntlet.*;
import com.dantemorrigan.gauntlet.boss.*;
import com.dantemorrigan.gauntlet.cosmos.CosmosTravel;
import com.dantemorrigan.gauntlet.client.boss.TribunalClient;
import com.dantemorrigan.gauntlet.stones.StoneRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import java.util.function.BooleanSupplier;

/** Real client regression: progression gates, art, real music, awakened/Uru damage, death, retry and persisted escrow. */
public final class TribunalTest implements FabricClientGameTest {
    private static ServerPlayer player(MinecraftServer s) { return s.getPlayerList().getPlayers().getFirst(); }
    private static LivingTribunal boss(MinecraftServer s) {
        var p = player(s);
        return p.level().getEntitiesOfClass(LivingTribunal.class,p.getBoundingBox().inflate(140),e -> !e.isRemoved()).stream().findFirst().orElse(null);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void until(ClientGameTestContext ctx, BooleanSupplier condition, String message) {
        for (int i=0;i<200;i++) { if(condition.getAsBoolean()) return; ctx.waitTicks(5); }
        throw new AssertionError(message);
    }
    @SuppressWarnings("unchecked")
    private static void thanosDefeated(MinecraftServer server) {
        try {
            var field = ThanosEncounter.class.getDeclaredField("DEFEATED"); field.setAccessible(true);
            server.overworld().setAttached((AttachmentType<Boolean>)field.get(null),true);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void assertRestored(MinecraftServer s, Vec3 origin) {
        var p = player(s);
        check(p.isAlive(),"Challenger stayed dead");
        check(CosmosTravel.inCosmos(p) && p.position().distanceToSqr(origin)<4,"Wrong checkpoint position/dimension");
        check(GauntletState.inserted(GauntletState.activeGauntlet(p)).size()==6,"Full gauntlet was not restored");
        check(p.getHealth()>150 && p.getAbilities().flying && p.isNoGravity(),"Health/flight not restored: health="+p.getHealth()+" max="+p.getMaxHealth()+" flying="+p.getAbilities().flying+" gravity="+p.isNoGravity());
        check(!TribunalEncounter.awaitingReturn(p) && !TribunalEncounter.inTrial(p),"Trial/checkpoint leaked");
        check(p.getMainHandItem().is(Items.DIAMOND) && p.getMainHandItem().getCount()==7,"Inventory was lost");
        check(p.experienceLevel==12,"Experience lost on trial death");
        int gauntlets=0,stones=0;
        for(int i=0;i<p.getInventory().getContainerSize();i++) {
            var stack=p.getInventory().getItem(i);
            if(stack.is(ModItems.GAUNTLET)) gauntlets+=stack.getCount();
            stones+=com.dantemorrigan.gauntlet.stones.StoneKeeper.stonesIn(stack).size();
        }
        check(gauntlets==1 && stones==6,"Escrow duplicated gauntlet/stones: "+gauntlets+" / "+stones);
        check(p.level().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(140),e->true).isEmpty(),"Trial dropped loot");
        check(boss(s)==null,"Boss survived scene cleanup");
    }
    @Override public void runTest(ClientGameTestContext ctx) {
        var sp=ctx.worldBuilder().create();
        try {
            sp.getClientLevel().waitForChunksRender();
            var server=sp.getServer();
            server.runCommand("gamemode survival @p");
            server.runCommand("difficulty normal");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamerule keep_inventory false");
            server.runOnServer(s->{
                var p=player(s);
                check(TribunalEncounter.start(p).equals("space_only"),"Trial opened outside cosmos");
                var glove=new ItemStack(ModItems.GAUNTLET);
                for(var stone:Stone.values()) GauntletState.putStone(glove,new ItemStack(stone.item()));
                p.setItemInHand(InteractionHand.OFF_HAND,glove);
                p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND,7));
                p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(ModItems.URU_HELMET));
                p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(ModItems.URU_CHESTPLATE));
                p.setItemSlot(EquipmentSlot.LEGS,new ItemStack(ModItems.URU_LEGGINGS));
                p.setItemSlot(EquipmentSlot.FEET,new ItemStack(ModItems.URU_BOOTS));
                p.setExperienceLevels(12);
            });
            until(ctx,()->server.computeOnServer(s->GauntletEffects.state(player(s)).phase==GauntletEffects.Phase.AWAKENED),"Gauntlet did not awaken");
            Vec3 origin=new Vec3(30000,256,500);
            server.runOnServer(s->{
                var p=player(s);
                p.setExperienceLevels(12); p.setExperiencePoints(0);
                CosmosTravel.action(p,0);
                p.teleport(new TeleportTransition(s.getLevel(CosmosTravel.DIMENSION),origin,Vec3.ZERO,0,-12,TeleportTransition.DO_NOTHING));
                check(TribunalEncounter.start(p).equals("locked"),"Trial opened without defeating the bosses");
                StoneRegistry.markReleased(s,Stone.MIND);
                check(TribunalEncounter.start(p).equals("locked"),"Trial opened without Thanos");
                thanosDefeated(s);
                var glove=p.getOffhandItem();p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                check(TribunalEncounter.start(p).equals("locked"),"Trial opened without full gauntlet");
                p.setItemInHand(InteractionHand.OFF_HAND,glove);
                check(TribunalEncounter.start(p).equals("started"),"Unlocked trial refused");
                check(TribunalEncounter.start(p).equals("busy"),"Two trials started");
            });
            ctx.runOnClient(mc->{ mc.gui.getChat().clearMessages(true); mc.getToastManager().clear(); });
            until(ctx,()->server.computeOnServer(s->boss(s)!=null && boss(s).sceneAge()>24),"No arrival");
            ctx.takeScreenshot("tribunal_01_black_hole");
            until(ctx,()->server.computeOnServer(s->boss(s)!=null && boss(s).sceneAge()>82),"No emergence");
            ctx.takeScreenshot("tribunal_02_emergence");
            until(ctx,()->server.computeOnServer(s->boss(s)!=null && boss(s).phase()==LivingTribunal.BATTLE),"Battle did not begin");
            until(ctx,()->ctx.computeOnClient(mc->TribunalClient.musicPlaying()),"Tribunal soundtrack did not play");
            ctx.takeScreenshot("tribunal_03_battle");
            server.runOnServer(s->{com.dantemorrigan.gauntlet.snap.InfinitySnap.cast(player(s));check(!com.dantemorrigan.gauntlet.snap.InfinitySnap.casting(player(s)),"Snap escaped the trial");});
            until(ctx,()->server.computeOnServer(s->player(s).getHealth()<150),"Tribunal could not hurt awakened Uru wearer");
            ctx.takeScreenshot("tribunal_04_cosmic_ray");
            until(ctx,()->server.computeOnServer(s->TribunalEncounter.awaitingReturn(player(s))),"One-heart verdict never started");
            server.runOnServer(s->{check(player(s).getOffhandItem().isEmpty(),"Gauntlet not confiscated");check(player(s).getHealth()==2,"Verdict did not start at one heart");});
            ctx.waitTicks(20);ctx.takeScreenshot("tribunal_05_confiscation");
            until(ctx,()->server.computeOnServer(s->boss(s)!=null && boss(s).sceneAge()>85),"Gauntlet never reached hand");
            ctx.takeScreenshot("tribunal_06_gauntlet_in_hand");
            until(ctx,()->server.computeOnServer(s->boss(s)!=null && boss(s).sceneAge()>LivingTribunal.SHATTER_TICK+3),"No shatter");
            ctx.takeScreenshot("tribunal_07_shatter");
            until(ctx,()->server.computeOnServer(s->player(s).isDeadOrDying()),"Verdict did not kill player");
            ctx.takeScreenshot("tribunal_08_death");
            server.runOnServer(s->player(s).connection.handleClientCommand(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(
                net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN)));
            until(ctx,()->server.computeOnServer(s->player(s).isAlive() && !TribunalEncounter.awaitingReturn(player(s))),"Respawn never restored checkpoint");
            server.runOnServer(s->assertRestored(s,origin));
            ctx.waitTicks(20);ctx.takeScreenshot("tribunal_09_return");
            // Quit during the next confiscation: the persisted checkpoint must return the item exactly once on login.
            server.runOnServer(s->check(TribunalEncounter.start(player(s)).equals("started"),"Retry was unavailable"));
            until(ctx,()->server.computeOnServer(s->boss(s)!=null && boss(s).phase()==LivingTribunal.BATTLE),"Retry arrival stuck");
            server.runOnServer(s->player(s).setHealth(2));
            until(ctx,()->server.computeOnServer(s->TribunalEncounter.awaitingReturn(player(s))),"Retry verdict not started");
            var save=sp.getWorldSave(); sp.close(); sp=null;
            try(var again=save.open()) {
                var reopened=again.getServer();
                until(ctx,()->reopened.computeOnServer(s->player(s).isAlive() && !TribunalEncounter.awaitingReturn(player(s))),"Login escrow was not recovered");
                reopened.runOnServer(s->assertRestored(s,origin));
                ctx.waitTicks(60);
                check(!ctx.computeOnClient(mc->TribunalClient.musicPlaying() || TribunalClient.inputLocked()),"Cinematic/music leaked after reconnect");
                ctx.takeScreenshot("tribunal_10_recovered_after_reconnect");
                // A very rare victory still runs the defeat voice, departure animation and cleanup.
                reopened.runOnServer(s->check(TribunalEncounter.start(player(s)).equals("started"),"Trial unavailable after reconnect"));
                until(ctx,()->reopened.computeOnServer(s->boss(s)!=null && boss(s).phase()==LivingTribunal.BATTLE),"Victory run never entered battle");
                reopened.runOnServer(s->{
                    var b=boss(s); b.setHealth(1); b.invulnerableTime=0;
                    check(b.hurtServer(player(s).level(),player(s).damageSources().playerAttack(player(s)),80),"Legal challenger could not defeat boss");
                    check(!TribunalEncounter.inTrial(player(s)),"Victory did not close trial");
                });
                ctx.waitTicks(15);ctx.takeScreenshot("tribunal_11_rare_victory");
                until(ctx,()->reopened.computeOnServer(s->boss(s)==null),"Defeated Tribunal remained");
                ctx.waitTicks(60);
                check(!ctx.computeOnClient(mc->TribunalClient.musicPlaying() || TribunalClient.inputLocked()),"Music leaked after victory");
            }
            System.out.println("TRIBUNAL: gates, actual music, Uru/awakened damage, confiscation, death, checkpoint, retry and persisted recovery passed");
        } finally { if(sp!=null) sp.close(); }
    }
}
