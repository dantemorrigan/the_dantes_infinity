package com.dantemorrigan.gauntlet.client.space;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Cached vanilla-textured voxel Earth. Every motion is a function of event age, so Time reverses it exactly. */
public final class ShatteredPlanet {
    private static final String[] TEXTURES = {"blue_concrete", "light_blue_concrete", "grass_block_top", "sand", "snow", "stone", "magma", "white_concrete"};
    private static final float[] FACE_LIGHT = {.88f,.7f,1f,.62f,.92f,.72f};
    private static final int[][] NORMALS = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    private static final int[][][] CORNERS = {
        {{1,0,0},{1,1,0},{1,1,1},{1,0,1}},{{0,0,1},{0,1,1},{0,1,0},{0,0,0}},
        {{0,1,1},{1,1,1},{1,1,0},{0,1,0}},{{0,0,0},{1,0,0},{1,0,1},{0,0,1}},
        {{1,0,1},{1,1,1},{0,1,1},{0,0,1}},{{0,0,0},{0,1,0},{1,1,0},{1,0,0}}
    };
    private final PlanetGeometry.Model model;
    private final RenderType[] types = new RenderType[PlanetGeometry.MATERIALS];
    private final List<Shard> shards = new ArrayList<>();
    private final List<Shard> ambient = new ArrayList<>();
    private record Shard(float x,float y,float z,float dx,float dy,float dz,float size,int born,int material,float spin) {}

    public ShatteredPlanet(long seed) {
        model=PlanetGeometry.build(seed);
        for(int m=0;m<types.length;m++) types[m]=RenderTypes.entityCutoutNoCull(Identifier.parse("minecraft:textures/block/"+TEXTURES[m]+".png"));
        Random r=new Random(seed ^ 0x5B71A9L);
        // Hundreds of tiny fragments at planetary distances, separate from the near-player debris.
        for(int i=0;i<650;i++) {
            var p=model.parents().get(i%model.parents().size());
            Vec3 direction=new Vec3(r.nextGaussian(),r.nextGaussian(),r.nextGaussian()).normalize();
            float speed=.035f+r.nextFloat()*.14f;
            Vec3 base=new Vec3(p.x(),p.y(),p.z()).add(outward(p).scale(separation(splitTick(p.id()))));
            shards.add(new Shard((float)base.x,(float)base.y,(float)base.z,
                    (float)direction.x*speed,(float)direction.y*speed,(float)direction.z*speed,
                    .035f+r.nextFloat()*.15f,splitTick(p.id())+r.nextInt(24),i%3==0?6:5,r.nextFloat()*6.28f));
        }
        for(int i=0;i<280;i++) {
            Vec3 v=new Vec3(r.nextGaussian(),r.nextGaussian(),r.nextGaussian()).normalize().scale(15+r.nextDouble()*265);
            ambient.add(new Shard((float)v.x,(float)v.y,(float)v.z,(r.nextFloat()-.5f)*.02f,(r.nextFloat()-.5f)*.02f,
                    (r.nextFloat()-.5f)*.02f,.14f+r.nextFloat()*.65f,140+r.nextInt(140),i%9==0?6:5,r.nextFloat()*6.28f));
        }
    }
    private static int splitTick(int id) { return 210 + Math.floorMod(id*37,76); }
    private static Vec3 outward(PlanetGeometry.Piece p) {
        Vec3 v=new Vec3(p.x(),p.y(),p.z());
        return v.lengthSqr()<.1 ? new Vec3(.3,.7,-.5).normalize() : v.normalize();
    }
    private static float separation(float age) {
        float t=Math.max(0,Math.min(1,(age-110)/85));
        return t*t*(3-2*t)*12 + Math.min(20,Math.max(0,age-195)*.014f);
    }
    public void render(PoseStack pose,SubmitNodeCollector collector,Vec3 camera,Vec3 center,float age,float scale) {
        if(age<80) return;
        pose.pushPose();
        pose.translate(center.x-camera.x,center.y-camera.y,center.z-camera.z);
        pose.scale(scale,scale,scale);
        pose.mulPose(Axis.ZP.rotationDegrees(23.4f));
        pose.mulPose(Axis.YP.rotationDegrees(Math.min(1500,age-80)*.008f));
        for(var parent:model.parents()) {
            Vec3 direction=outward(parent);
            float separation=separation(age),spin=Math.max(0,age-130)*.025f;
            pose.pushPose();
            pose.translate(parent.x()+direction.x*separation,parent.y()+direction.y*separation,parent.z()+direction.z*separation);
            pose.mulPose(Axis.of(new org.joml.Vector3f((float)direction.z,.5f,(float)direction.x).normalize()).rotationDegrees(spin));
            float split=age-splitTick(parent.id());
            if(split<0) mesh(pose,collector,parent);
            else {
                float travel=Math.min(55,split*.085f);
                for(var piece:model.fragments()) if(piece.parent()==parent.id()) {
                    Vec3 out=new Vec3(piece.x()-parent.x(),piece.y()-parent.y(),piece.z()-parent.z()).normalize();
                    pose.pushPose();
                    pose.translate(piece.x()-parent.x()+out.x*travel,piece.y()-parent.y()+out.y*travel,piece.z()-parent.z()+out.z*travel);
                    pose.mulPose(Axis.YP.rotationDegrees(split*(.1f+piece.id()%5*.04f)));
                    mesh(pose,collector,piece);
                    pose.popPose();
                }
                if(split<30) burst(pose,collector,split,30,6+parent.id()%4,"detonation_flash");
            }
            pose.popPose();
        }
        if(age>=130 && age<170) burst(pose,collector,age-130,40,30,"detonation_flash");
        if(age>=135 && age<205) burst(pose,collector,age-135,70,20+(age-135)*.65f,"power_shockwave");
        dust(pose,collector,age,shards,null,Float.MAX_VALUE);
        pose.popPose();
    }
    public void renderAmbient(PoseStack pose,SubmitNodeCollector collector,Vec3 camera,Vec3 center,float age,float distance) {
        pose.pushPose();
        pose.translate(center.x-camera.x,center.y-camera.y,center.z-camera.z);
        dust(pose,collector,age,ambient,camera.subtract(center),distance);
        pose.popPose();
    }
    private void mesh(PoseStack pose,SubmitNodeCollector collector,PlanetGeometry.Piece piece) {
        for(int m=0;m<types.length;m++) {
            var faces=piece.materials().get(m); if(faces.isEmpty()) continue;
            final int material=m;
            collector.submitCustomGeometry(pose,types[m],(p,out)->{
                for(var f:faces) {
                    double len=Math.sqrt((f.x()+.5)*(f.x()+.5)+(f.y()+.5)*(f.y()+.5)+(f.z()+.5)*(f.z()+.5));
                    double sunlight=Math.max(0,(-.35*f.x()+.65*f.y()+.65*f.z())/Math.max(1,len));
                    float light=material==6?1f:(float)(.48+.52*sunlight);
                    light*=FACE_LIGHT[f.side()];
                    int green=material==2?0x86AF55:0xFFFFFF;
                    int color=0xFF000000 | ((int)(((green>>16)&255)*light)<<16) | ((int)(((green>>8)&255)*light)<<8) | (int)((green&255)*light);
                    int[][] corners=CORNERS[f.side()];int[] normal=NORMALS[f.side()];
                    for(int i=0;i<4;i++) vertex(out,p,f.x()+corners[i][0]-piece.x(),f.y()+corners[i][1]-piece.y(),f.z()+corners[i][2]-piece.z(),
                            i==0||i==3?0:1,i<2?1:0,color,normal[0],normal[1],normal[2]);
                }
            });
        }
    }
    private static void burst(PoseStack pose,SubmitNodeCollector collector,float age,int duration,float size,String texture) {
        int frame=Math.min(3,(int)(age*4/duration));
        int color=((int)(255*Math.min(1,(duration-age)/8f))<<24)|0xE5A5FF;
        var type=RenderTypes.entityTranslucentEmissive(InfinityGauntlet.id("textures/particle/"+texture+"_"+frame+".png"));
        collector.submitCustomGeometry(pose,type,(p,out)->{
            // Three intersecting, double-sided planes remain visible at oblique angles.
            for(int axis=0;axis<3;axis++) for(int winding=0;winding<2;winding++) for(int j=0;j<4;j++) {
                int i=winding==0?j:3-j;
                float a=(i==0||i==3?-1:1)*size,b=(i<2?-1:1)*size;
                vertex(out,p,axis==0?0:a,axis==1?0:b,axis==2?0:axis==0?a:b,
                        i==0||i==3?0:1,i<2?1:0,color,0,1,0);
            }
        });
    }
    private void dust(PoseStack pose,SubmitNodeCollector collector,float age,List<Shard> cloud,Vec3 viewer,float distance) {
        for(int material:new int[]{5,6}) collector.submitCustomGeometry(pose,types[material],(p,out)->{
            for(Shard s:cloud) {
                if(s.material!=material || age<s.born) continue;
                float t=Math.min(900,age-s.born),angle=s.spin+t*.014f;
                float x=s.x+s.dx*t,y=s.y+s.dy*t,z=s.z+s.dz*t;
                if(viewer!=null && viewer.distanceToSqr(x,y,z)>distance*distance) continue;
                float cos=(float)Math.cos(angle),sin=(float)Math.sin(angle),size=s.size;
                for(int side=0;side<6;side++) for(int i=0;i<4;i++) {
                    int[] v=CORNERS[side][i];float vx=(v[0]-.5f)*size,vy=(v[1]-.5f)*size,vz=(v[2]-.5f)*size;
                    vertex(out,p,x+vx*cos-vz*sin,y+vy,z+vx*sin+vz*cos,i==0||i==3?0:1,i<2?1:0,0xFFCEB7DC,0,1,0);
                }
            }
        });
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose pose,float x,float y,float z,float u,float v,int color,float nx,float ny,float nz) {
        out.addVertex(pose,x,y,z).setColor(color).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose,nx,ny,nz);
    }
}
