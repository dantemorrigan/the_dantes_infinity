package com.dantemorrigan.gauntlet.client.cosmos;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.cosmos.CosmosRoutes;
import com.dantemorrigan.gauntlet.client.celestial.CelestialSky;
import com.dantemorrigan.gauntlet.client.space.PlanetGeometry;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.*;

/** Sky-pass cube map + parallax nebulae; cached exterior-only voxel planets in the world pass. */
public final class CosmosRenderer {
    private static final String[] FACES={"positive_x","negative_x","positive_y","negative_y","positive_z","negative_z"};
    private static final String[] NEBULAE={"cyan_arch","violet_crescent","amber_nursery","ice_ribbon"};
    private static final double[] YAW={35,155,255,320}, PITCH={12,-18,24,-35}, PARALLAX={.012,.006,.009,.018};
    private static final float[] WIDTH={55,62,48,65}, ALPHA={.5f,.45f,.4f,.35f};
    public static void init() { LevelRenderEvents.COLLECT_SUBMITS.register(CosmosRenderer::render); }
    private static Vector3f face(int f,float u,float v) {
        return switch(f) {
            case 0 -> new Vector3f(1,-v,-u); case 1 -> new Vector3f(-1,-v,u);
            case 2 -> new Vector3f(u,1,v); case 3 -> new Vector3f(u,-1,-v);
            case 4 -> new Vector3f(u,-v,1); default -> new Vector3f(-u,-v,-1);
        };
    }
    /** Called in the existing sky pass, never a see-through world overlay. */
    public static void sky(float opacity) {
        if(opacity<.001) return;
        PoseStack pose=new PoseStack();
        int color=((int)(255*opacity)<<24)|0xFFFFFF;
        float inset=.5f/512;
        float[][] uv={{inset,inset},{1-inset,inset},{1-inset,1-inset},{inset,1-inset}};
        for(int f=0;f<6;f++) {
            Vector3f[] c={face(f,-1,-1),face(f,1,-1),face(f,1,1),face(f,-1,1)};
            CelestialSky.draw(pose,InfinityGauntlet.id("textures/environment/cosmos/faces/"+FACES[f]+".png"),out->{
                for(int winding=0;winding<2;winding++) for(int j=0;j<4;j++) {
                    int i=winding==0?j:3-j;
                    out.addVertex(c[i].x*100,c[i].y*100,c[i].z*100).setUv(uv[i][0],uv[i][1]).setColor(color);
                }
            });
        }
        var mc=Minecraft.getInstance();
        Vec3 camera=mc.gameRenderer.getMainCamera().position();
        boolean orbit=CosmosClient.inCosmos();
        for(int i=0;i<4;i++) {
            double y=Math.toRadians(YAW[i]),p=Math.toRadians(PITCH[i]);
            Vec3 normal=new Vec3(Math.cos(y)*Math.cos(p),Math.sin(p),Math.sin(y)*Math.cos(p));
            // Bounded directional parallax; different layer distances prevent the wallpaper look.
            if(orbit) normal=normal.subtract(camera.scale(PARALLAX[i]/120)).normalize();
            Vec3 right=normal.cross(new Vec3(0,1,0)).normalize(), up=right.cross(normal).normalize();
            double size=Math.tan(Math.toRadians(WIDTH[i]/2))*90;
            Vec3 center=normal.scale(90), dx=right.scale(size), dy=up.scale(size);
            Vec3[] corners={center.subtract(dx).add(dy),center.add(dx).add(dy),center.add(dx).subtract(dy),center.subtract(dx).subtract(dy)};
            int tint=((int)(255*opacity*ALPHA[i])<<24)|0xFFFFFF;
            String quality=mc.options.getEffectiveRenderDistance()<=8?"nebulae_256/":"nebulae/";
            Identifier tex=InfinityGauntlet.id("textures/environment/cosmos/"+quality+"nebula_"+NEBULAE[i]+".png");
            CelestialSky.draw(pose,tex,out->{
                for(int winding=0;winding<2;winding++) for(int j=0;j<4;j++) {
                    int k=winding==0?j:3-j; var v=corners[k];
                    out.addVertex((float)v.x,(float)v.y,(float)v.z).setUv(k==0||k==3?0:1,k<2?0:1).setColor(tint);
                }
            });
        }
    }
    private static final float[][] ATMOS={{.35f,.60f,1f},{0,0,0},{1f,.45f,.25f},{.65f,.85f,1f}};
    private static void render(LevelRenderContext ctx) {
        var mc=Minecraft.getInstance();
        boolean orbit=CosmosClient.inCosmos(); float atmosphere=CosmosClient.atmosphere();
        if(mc.player==null || (!orbit && atmosphere<.65)) return;
        Vec3 cam=mc.gameRenderer.getMainCamera().position();
        float far=mc.options.getEffectiveRenderDistance()*64f;
        // Far to near submission preserves the visual order of distant worlds.
        List<Integer> order=new ArrayList<>(List.of(0,1,2,3));
        order.sort(Comparator.comparingDouble((Integer i)->{
            var d=CosmosRoutes.ALL.get(i); return -cam.distanceToSqr(d.x(),d.y(),d.z());
        }));
        for(int index:order) {
            if(!orbit && index!=0) continue;
            var d=CosmosRoutes.ALL.get(index);
            Vec3 center=orbit?new Vec3(d.x(),d.y(),d.z()):new Vec3(cam.x,-1080,cam.z);
            double radius=orbit?d.radius():1560;
            Vec3 delta=center.subtract(cam);
            double fit=Math.min(1,far*.68/(delta.length()+radius));
            float scale=(float)(radius*fit);
            int alpha=orbit?255:(int)(255*Math.min(1,(atmosphere-.65)/.35));
            // Camera in the planet's unit-sphere space, for the rim glow.
            final float cx=(float)(-delta.x*fit/scale), cy=(float)(-delta.y*fit/scale), cz=(float)(-delta.z*fit/scale);
            var pose=ctx.poseStack(); pose.pushPose();
            pose.translate(delta.x*fit,delta.y*fit,delta.z*fit);
            pose.scale(scale,scale,scale);
            final int planet=index, a=alpha;
            var type=RenderTypes.entityTranslucentEmissive(CosmosPlanets.texture(index));
            ctx.submitNodeCollector().submitCustomGeometry(pose,type,(p,out)->surface(p,out,planet,a));
            if(ATMOS[index][0]>0) {
                var white=RenderTypes.entityTranslucentEmissive(Identifier.withDefaultNamespace("textures/block/white_concrete.png"));
                ctx.submitNodeCollector().submitCustomGeometry(pose,white,(p,out)->glow(p,out,planet,a,cx,cy,cz));
            }
            pose.popPose();
        }
    }
    private static void surface(PoseStack.Pose p,VertexConsumer out,int planet,int alpha) {
        final int la=CosmosPlanets.LAT, lo=CosmosPlanets.LON; float[] v=new float[3];
        for(int i=0;i<la;i++) for(int j=0;j<lo;j++) {
            int[][] q={{i,j},{i,j+1},{i+1,j+1},{i+1,j}};
            for(int w=0;w<2;w++) for(int k=0;k<4;k++) {
                int[] c=q[w==0?k:3-k];
                CosmosPlanets.vertex(planet,c[0],c[1],la,lo,v);
                int g=(int)(255*CosmosPlanets.light(v[0],v[1],v[2]));
                int r=g, b=Math.min(255,g+(int)((255-g)*.06f));
                float u=c[1]/(float)lo, t=1f-c[0]/(float)la;
                out.addVertex(p,v[0],v[1],v[2]).setColor(alpha<<24|r<<16|g<<8|b).setUv(u,t)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p,v[0],v[1],v[2]);
            }
        }
    }
    /** Thin translucent shells: soft atmospheric rim that fades toward the planet's centre disc. */
    private static void glow(PoseStack.Pose p,VertexConsumer out,int planet,int alpha,float cx,float cy,float cz) {
        final int la=40, lo=80; float[] v=new float[3]; float[] col=ATMOS[planet];
        float[] sizes={1.012f,1.03f,1.06f}, strength={.34f,.20f,.10f};
        for(int s=0;s<sizes.length;s++) {
            for(int i=0;i<la;i++) for(int j=0;j<lo;j++) {
                int[][] q={{i,j},{i,j+1},{i+1,j+1},{i+1,j}};
                for(int w=0;w<2;w++) for(int k=0;k<4;k++) {
                    int[] c=q[w==0?k:3-k];
                    CosmosPlanets.vertex(planet,c[0],c[1],la,lo,v);
                    float x=v[0]*sizes[s],y=v[1]*sizes[s],z=v[2]*sizes[s];
                    float vx=cx-x,vy=cy-y,vz=cz-z, vl=(float)Math.sqrt(vx*vx+vy*vy+vz*vz);
                    float facing=Math.max(0,(v[0]*vx+v[1]*vy+v[2]*vz)/Math.max(1e-4f,vl));
                    float rim=(float)Math.pow(1-facing,2.2);
                    float day=.25f+.75f*CosmosPlanets.light(v[0],v[1],v[2]);
                    int a=(int)(alpha*strength[s]*rim*day);
                    out.addVertex(p,x,y,z).setColor(a<<24|(int)(col[0]*255)<<16|(int)(col[1]*255)<<8|(int)(col[2]*255)).setUv(.5f,.5f)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p,v[0],v[1],v[2]);
                }
            }
        }
    }
    private CosmosRenderer() {}
}
