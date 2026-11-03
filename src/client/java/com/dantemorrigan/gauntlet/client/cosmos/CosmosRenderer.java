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
    private static final String[][] PALETTES={
        {"blue_concrete","light_blue_concrete","grass_block_top","sand","snow","stone","magma","white_concrete"},
        {"stone","andesite","tuff","light_gray_concrete","calcite","stone","basalt_side","diorite"},
        {"red_sandstone","terracotta","red_terracotta","orange_terracotta","basalt_side","blackstone","magma","orange_terracotta"},
        {"blue_ice","packed_ice","light_blue_concrete","calcite","snow","ice","blue_ice","white_concrete"}};
    private static final int[][] N={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    private static final int[][][] C={{{1,0,0},{1,1,0},{1,1,1},{1,0,1}},{{0,0,1},{0,1,1},{0,1,0},{0,0,0}},
        {{0,1,1},{1,1,1},{1,1,0},{0,1,0}},{{0,0,0},{1,0,0},{1,0,1},{0,0,1}},
        {{1,0,1},{1,1,1},{0,1,1},{0,0,1}},{{0,0,0},{0,1,0},{1,1,0},{1,0,0}}};
    private static List<List<PlanetGeometry.Face>> surface;
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
    private static void build() {
        surface=new ArrayList<>(); for(int m=0;m<8;m++) surface.add(new ArrayList<>());
        // Retain only the exterior of the intact planet: no fracture interiors or moving shards.
        for(var p:PlanetGeometry.build(1234).parents()) for(int m=0;m<8;m++) for(var f:p.materials().get(m)) {
            int[] n=N[f.side()]; double x=f.x()+.5+n[0],y=f.y()+.5+n[1],z=f.z()+.5+n[2];
            if(x*x+y*y+z*z>PlanetGeometry.RADIUS*PlanetGeometry.RADIUS) surface.get(m).add(f);
        }
    }
    private static void render(LevelRenderContext ctx) {
        var mc=Minecraft.getInstance();
        boolean orbit=CosmosClient.inCosmos(); float atmosphere=CosmosClient.atmosphere();
        if(mc.player==null || (!orbit && atmosphere<.65)) return;
        if(surface==null) build();
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
            Vec3 center=orbit?new Vec3(d.x(),d.y(),d.z()):new Vec3(cam.x,-300,cam.z);
            double radius=orbit?d.radius():780;
            Vec3 delta=center.subtract(cam);
            double fit=Math.min(1,far*.68/(delta.length()+radius));
            float scale=(float)(radius/PlanetGeometry.RADIUS*fit);
            int alpha=orbit?255:(int)(255*Math.min(1,(atmosphere-.65)/.35));
            var pose=ctx.poseStack(); pose.pushPose();
            pose.translate(delta.x*fit,delta.y*fit,delta.z*fit);
            pose.scale(scale,scale,scale);
            pose.mulPose(Axis.ZP.rotationDegrees(index==0?23.4f:12));
            pose.mulPose(Axis.YP.rotationDegrees(index*67));
            for(int m=0;m<8;m++) {
                final int material=m, planet=index;
                var faces=surface.get(m);
                var type=RenderTypes.entityTranslucentEmissive(Identifier.withDefaultNamespace("textures/block/"+PALETTES[index][m]+".png"));
                ctx.submitNodeCollector().submitCustomGeometry(pose,type,(p,out)->{
                    for(var f:faces) {
                        double len=Math.sqrt(f.x()*f.x()+f.y()*f.y()+f.z()*f.z());
                        float light=(float)(.20+.80*Math.max(0,(-.35*f.x()+.65*f.y()+.65*f.z())/Math.max(1,len)));
                        int tint=planet==0 && material==2?0x86AF55:0xFFFFFF;
                        int color=alpha<<24 | (int)(((tint>>16)&255)*light)<<16 | (int)(((tint>>8)&255)*light)<<8 | (int)((tint&255)*light);
                        int[] normal=N[f.side()];
                        for(int v=0;v<4;v++) {
                            int[] c=C[f.side()][v];
                            out.addVertex(p,f.x()+c[0],f.y()+c[1],f.z()+c[2]).setColor(color).setUv(v==0||v==3?0:1,v<2?1:0)
                                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p,normal[0],normal[1],normal[2]);
                        }
                    }
                });
            }
            pose.popPose();
        }
    }
    private CosmosRenderer() {}
}
