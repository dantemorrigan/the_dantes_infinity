package com.dantemorrigan.gauntlet.client.fx;

import com.dantemorrigan.gauntlet.client.boss.TribunalRenderer;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.*;
import java.util.*;

/** Free-floating force shells and meteor fragments; no floor plane or vertical fall assumption. */
public final class CosmicClient {
    private static final List<Cue> CUES=new ArrayList<>();
    private static net.minecraft.client.multiplayer.ClientLevel level;
    private static final class Cue {
        final ModNetwork.CosmicFx fx; final ItemStackRenderState rock=new ItemStackRenderState(); int age;
        Cue(ModNetwork.CosmicFx fx) {this.fx=fx;}
    }
    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetwork.CosmicFx.TYPE,(p,c)->{if(CUES.size()<80) CUES.add(new Cue(p));});
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->CUES.clear());
        ClientTickEvents.END_CLIENT_TICK.register(mc->{if(level!=mc.level) {CUES.clear();level=mc.level;} if(!mc.isPaused()) CUES.removeIf(c->++c.age>c.fx.ticks());});
        LevelRenderEvents.COLLECT_SUBMITS.register(ctx->{
            Minecraft mc=Minecraft.getInstance();if(mc.level==null) return;
            float partial=mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);var camera=mc.gameRenderer.getMainCamera().position();
            for(Cue c:CUES) {
                float t=Math.min(1,(c.age+partial)/Math.max(1,c.fx.ticks()));
                var at=c.fx.from().lerp(c.fx.to(),t).subtract(camera);var pose=ctx.poseStack();
                if(c.fx.kind()==0) {
                    int alpha=(int)(220*(1-t));int color=(alpha<<24)|(c.fx.color()&0xffffff);
                    TribunalRenderer.sphere(pose,ctx.submitNodeCollector(),at,c.fx.size()*t,color);
                } else {
                    mc.getItemModelResolver().updateForTopItem(c.rock,new ItemStack(Items.MAGMA_BLOCK),ItemDisplayContext.NONE,mc.level,null,0);
                    pose.pushPose();pose.translate(at.x,at.y,at.z);pose.mulPose(Axis.YP.rotationDegrees(c.age*9));pose.mulPose(Axis.XP.rotationDegrees(c.age*5));
                    pose.scale(c.fx.size(),c.fx.size(),c.fx.size());c.rock.submit(pose,ctx.submitNodeCollector(),LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,0);pose.popPose();
                    TribunalRenderer.sphere(pose,ctx.submitNodeCollector(),at,c.fx.size()*1.2f,c.fx.color());
                }
            }
        });
    }
    private CosmicClient() {}
}
