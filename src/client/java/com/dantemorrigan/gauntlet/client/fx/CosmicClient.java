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
        ClientPlayNetworking.registerGlobalReceiver(ModNetwork.CosmicFx.TYPE,(p,c)->{
            if(p.kind()==4) {for(Cue q:CUES) if(q.fx.kind()==3 && q.fx.to().x==p.to().x) q.age=Math.max(q.age,q.fx.ticks()-12);return;}
            if(CUES.size()<80) CUES.add(new Cue(p));
        });
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
                    TribunalRenderer.blast(pose,ctx.submitNodeCollector(),at,c.fx.size()*t,color);
                } else if(c.fx.kind()==2) {
                    var from=c.fx.from().subtract(camera);var end=c.fx.from().lerp(c.fx.to(),Math.min(1,t*5)).subtract(camera);
                    int tint=(((int)(255*(1-t)))<<24)|(c.fx.color()&0xffffff);
                    TribunalRenderer.energyRay(pose,ctx.submitNodeCollector(),from,end,c.fx.size()*.2f,tint);
                } else if(c.fx.kind()==3) {
                    var owner=mc.level.getEntity((int)c.fx.to().x);
                    var center=(owner==null?c.fx.from():owner.getPosition(partial)).subtract(camera);
                    float fade=Math.min(1,(c.age+partial)/10f)*Math.min(1,(c.fx.ticks()-c.age)/12f);
                    pose.pushPose();pose.translate(center.x,center.y,center.z);pose.mulPose(Axis.YP.rotationDegrees(c.age*1.4f));
                    pose.scale(1+(float)Math.sin(c.age*.08)*.2f,1+(float)Math.cos(c.age*.06)*.25f,1);
                    for(int ring=0;ring<4;ring++) TribunalRenderer.sphere(pose,ctx.submitNodeCollector(),net.minecraft.world.phys.Vec3.ZERO,35+ring*22,((int)(fade*110)<<24)|0xe461ff);
                    pose.popPose();
                } else {
                    mc.getItemModelResolver().updateForTopItem(c.rock,new ItemStack(Items.MAGMA_BLOCK),ItemDisplayContext.NONE,mc.level,null,0);
                    pose.pushPose();pose.translate(at.x,at.y,at.z);pose.mulPose(Axis.YP.rotationDegrees(c.age*9));pose.mulPose(Axis.XP.rotationDegrees(c.age*5));
                    pose.scale(c.fx.size(),c.fx.size(),c.fx.size());c.rock.submit(pose,ctx.submitNodeCollector(),LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,0);pose.popPose();
                    TribunalRenderer.sphere(pose,ctx.submitNodeCollector(),at,c.fx.size()*1.2f,c.fx.color());
                }
            }
        });
    }
    public static float distortion() {
        var mc=Minecraft.getInstance();if(mc.player==null) return 0;
        float result=0;
        for(Cue c:CUES) if(c.fx.kind()==3) {
            var owner=mc.level.getEntity((int)c.fx.to().x);var center=owner==null?c.fx.from():owner.position();
            if(center.distanceToSqr(mc.player.position())<c.fx.size()*c.fx.size()) result=Math.max(result,Math.min(1,c.age/12f)*Math.min(1,(c.fx.ticks()-c.age)/12f));
        }
        return result;
    }
    public static org.joml.Vector3f bend(org.joml.Vector3f v,float amount,float time) {
        float len=v.length();if(len<.01 || amount<=0)return v;
        float twist=amount*.23f*(float)Math.sin(v.y/len*5+time*.04);
        v.rotateY(twist);v.y+=len*amount*.12f*(float)Math.sin(v.x/len*5+time*.035);return v.normalize().mul(len);
    }
    private CosmicClient() {}
}
