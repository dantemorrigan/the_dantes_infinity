package com.dantemorrigan.gauntlet.client.cosmos;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.cosmos.CosmosRoutes;
import com.dantemorrigan.gauntlet.client.celestial.CelestialSky;
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
        float distortion=com.dantemorrigan.gauntlet.client.fx.CosmicClient.distortion();
        float clock=Minecraft.getInstance().level==null?0:Minecraft.getInstance().level.getGameTime();
        int cells=distortion>.001f?8:1;
        for(int f=0;f<6;f++) {
            final int faceIndex=f;
            CelestialSky.draw(pose,InfinityGauntlet.id("textures/environment/cosmos/faces/"+FACES[f]+".png"),out->{
                for(int x=0;x<cells;x++) for(int y=0;y<cells;y++) {
                    float u0=(float)x/cells,u1=(float)(x+1)/cells,v0=(float)y/cells,v1=(float)(y+1)/cells;
                    float[][] cell={{u0,v0},{u1,v0},{u1,v1},{u0,v1}};
                    for(int winding=0;winding<2;winding++)for(int j=0;j<4;j++) {
                        float[] t=cell[winding==0?j:3-j];
                        Vector3f vertex=com.dantemorrigan.gauntlet.client.fx.CosmicClient.bend(face(faceIndex,t[0]*2-1,t[1]*2-1).mul(100),distortion,clock);
                        out.addVertex(vertex.x,vertex.y,vertex.z).setUv(inset+t[0]*(1-2*inset),inset+t[1]*(1-2*inset)).setColor(color);
                    }
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
    /** Atmosphere colour + strength per body; the Sun gets a stronger corona. */
    private static final Map<String,float[]> GLOW=Map.of("earth",new float[]{.35f,.6f,1f,.34f},"venus",new float[]{1f,.85f,.5f,.5f},
        "mars",new float[]{1f,.6f,.4f,.12f},"jupiter",new float[]{.9f,.8f,.65f,.10f},"saturn",new float[]{.9f,.85f,.6f,.10f},
        "uranus",new float[]{.5f,.85f,.9f,.3f},"neptune",new float[]{.3f,.5f,1f,.3f},"sun",new float[]{1f,.9f,.62f,1f});
    private static final Vec3 OVERWORLD_SUN=new Vec3(-.35,.65,.65).normalize();
    private static boolean warmed;
    private record Body(String id,Vec3 delta,double radius,double distance,double angular,int index) {}
    private static void render(LevelRenderContext ctx) {
        var mc=Minecraft.getInstance();
        boolean orbit=CosmosClient.inCosmos(); float atmosphere=CosmosClient.atmosphere();
        if(mc.player==null || (!orbit && atmosphere<.65)) return;
        if(!warmed) { warmed=true; CosmosPlanets.warmup(); }
        Vec3 cam=mc.gameRenderer.getMainCamera().position(), look=mc.player.getViewVector(1);
        float far=mc.options.getEffectiveRenderDistance()*64f;
        List<Body> bodies=new ArrayList<>();
        if(orbit) for(int i=0;i<CosmosRoutes.ALL.size();i++) {
            var d=CosmosRoutes.ALL.get(i); Vec3 delta=new Vec3(d.x(),d.y(),d.z()).subtract(cam); double dist=delta.length();
            if(dist<=d.radius()+1) continue;
            double angular=Math.asin(Math.min(1,d.radius()/dist));
            if(Math.acos(Math.max(-1,Math.min(1,delta.dot(look)/dist)))-angular>1.3) continue; // outside the view cone
            bodies.add(new Body(d.id(),delta,d.radius(),dist,angular,i));
        } else {
            Vec3 delta=new Vec3(0,-1080-cam.y,0); bodies.add(new Body("earth",delta,1560,delta.length(),Math.asin(Math.min(1,1560/delta.length())),-1));
        }
        // Far to near. A body hidden behind a nearer one (its centre inside the nearer disk) is skipped, because the
        // compressed depth below preserves order but not exact occlusion.
        bodies.sort(Comparator.comparingDouble((Body x)->-x.distance()));
        List<Body> visible=new ArrayList<>();
        for(Body j:bodies) {
            boolean hidden=false;
            for(Body i:bodies) if(i!=j && i.distance()<j.distance() && Math.acos(Math.max(-1,Math.min(1,i.delta().dot(j.delta())/(i.distance()*j.distance()))))<i.angular()) {hidden=true;break;}
            if(!hidden) visible.add(j);
        }
        Vec3 sun=orbit?new Vec3(CosmosRoutes.ALL.get(0).x(),CosmosRoutes.ALL.get(0).y(),CosmosRoutes.ALL.get(0).z()):null;
        for(Body b:visible) {
            Identifier texture=CosmosPlanets.texture(b.id()); if(texture==null) continue;
            boolean isSun=b.id().equals("sun");
            // Distance used for depth only: angular size and direction stay true, order is preserved.
            double shown=isSun?far*.68:orbit?far*(.2+.2*Math.min(1,b.distance()/130000)):far*.3;
            double k=shown/b.distance(); float scale=(float)(b.radius()*k);
            int alpha=orbit?255:(int)(255*Math.min(1,(atmosphere-.65)/.35));
            float tilt=com.dantemorrigan.gauntlet.cosmos.CelestialRotation.spin(b.id()).tilt();
            float yaw=com.dantemorrigan.gauntlet.cosmos.CelestialRotation.yaw(b.id(),mc.level.getGameTime()+mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
            Vec3 toSun=orbit?(isSun?Vec3.ZERO:sun.subtract(new Vec3(b.delta().x+cam.x,b.delta().y+cam.y,b.delta().z+cam.z)).normalize()):OVERWORLD_SUN;
            var q=new org.joml.Quaternionf().rotationZ((float)Math.toRadians(tilt)).rotateY((float)Math.toRadians(yaw)).conjugate();
            Vector3f light=q.transform(new Vector3f((float)toSun.x,(float)toSun.y,(float)toSun.z));
            Vector3f camLocal=q.transform(new Vector3f((float)(-b.delta().x/b.radius()),(float)(-b.delta().y/b.radius()),(float)(-b.delta().z/b.radius())));
            double ratio=b.radius()/b.distance(); int level=ratio>.3?2:ratio>.07?1:0;
            var mesh=CosmosPlanets.mesh(level);
            var pose=ctx.poseStack(); pose.pushPose();
            pose.translate(b.delta().x*k,b.delta().y*k,b.delta().z*k);
            pose.scale(scale,scale,scale);
            pose.mulPose(Axis.ZP.rotationDegrees(tilt)); pose.mulPose(Axis.YP.rotationDegrees(yaw));
            // Stellar surface uses an unlit opaque pipeline: entity lighting must not shade the Sun like a planet.
            ctx.submitNodeCollector().submitCustomGeometry(pose,isSun?RenderTypes.beaconBeam(texture,false):RenderTypes.entityTranslucentEmissive(texture),(p,out)->surface(p,out,mesh,light,alpha,isSun));
            float[] glow=GLOW.get(b.id());
            if(glow!=null && ratio>.0015) {
                var white=isSun?RenderTypes.beaconBeam(InfinityGauntlet.id("textures/tribunal/white.png"),true):RenderTypes.entityTranslucentEmissive(Identifier.withDefaultNamespace("textures/block/white_concrete.png"));
                var gm=CosmosPlanets.mesh(Math.min(level,1));
                ctx.submitNodeCollector().submitCustomGeometry(pose,white,(p,out)->glow(p,out,gm,light,alpha,camLocal,glow,isSun));
            }
            if(b.id().equals("saturn")) {
                Identifier ring=CosmosPlanets.texture("saturn_ring");
                if(ring!=null) ctx.submitNodeCollector().submitCustomGeometry(pose,RenderTypes.entityTranslucentEmissive(ring),(p,out)->ring(p,out,alpha));
            }
            pose.popPose();
        }
    }
    private static void vertex(PoseStack.Pose p,VertexConsumer out,float x,float y,float z,float u,float v,int argb,float nx,float ny,float nz) {
        out.addVertex(p,x,y,z).setColor(argb).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p,nx,ny,nz);
    }
    private static float shade(float nx,float ny,float nz,Vector3f l) {
        double d=nx*l.x+ny*l.y+nz*l.z, t=Math.max(0,Math.min(1,(d+.12)/.47));
        return (float)(.08+.92*t*t*(3-2*t));
    }
    /** Front faces only: quads are counter-clockwise seen from outside. */
    private static void surface(PoseStack.Pose p,VertexConsumer out,CosmosPlanets.Mesh m,Vector3f light,int alpha,boolean sun) {
        int cols=m.lon()+1; int[] color=new int[(m.lat()+1)*cols];
        for(int n=0;n<color.length;n++) {
            int g=sun?255:(int)(255*shade(m.pos()[n*3],m.pos()[n*3+1],m.pos()[n*3+2],light));
            color[n]=alpha<<24|g<<16|g<<8|Math.min(255,g+(int)((255-g)*.06f));
        }
        for(int i=0;i<m.lat();i++) for(int j=0;j<m.lon();j++) {
            int a=i*cols+j, b=a+1, c=a+cols+1, d=a+cols;
            for(int n:new int[]{a,b,c,d}) vertex(p,out,m.pos()[n*3],m.pos()[n*3+1],m.pos()[n*3+2],m.uv()[n*2],m.uv()[n*2+1],color[n],m.pos()[n*3],m.pos()[n*3+1],m.pos()[n*3+2]);
        }
    }
    /** Thin translucent shells: soft atmospheric rim that fades toward the middle of the disc. */
    private static void glow(PoseStack.Pose p,VertexConsumer out,CosmosPlanets.Mesh m,Vector3f light,int alpha,Vector3f cam,float[] glow,boolean sun) {
        float[] sizes=sun?new float[]{1.008f,1.025f,1.065f,1.14f,1.3f,1.55f}:new float[]{1.012f,1.03f,1.06f};
        float[] strength=sun?new float[]{.65f,.45f,.28f,.16f,.08f,.025f}:new float[]{1f,.6f,.3f};
        int cols=m.lon()+1; int cr=(int)(glow[0]*255),cg=(int)(glow[1]*255),cb=(int)(glow[2]*255);
        for(int s=0;s<sizes.length;s++) {
            int[] color=new int[(m.lat()+1)*cols];
            for(int n=0;n<color.length;n++) {
                float nx=m.pos()[n*3],ny=m.pos()[n*3+1],nz=m.pos()[n*3+2];
                float vx=cam.x-nx*sizes[s],vy=cam.y-ny*sizes[s],vz=cam.z-nz*sizes[s], vl=(float)Math.sqrt(vx*vx+vy*vy+vz*vz);
                float facing=Math.max(0,(nx*vx+ny*vy+nz*vz)/Math.max(1e-4f,vl));
                float rim=sun?.08f+.92f*(float)Math.pow(1-facing,1.2):(float)Math.pow(1-facing,2.2), day=sun?1:.25f+.75f*shade(nx,ny,nz,light);
                color[n]=((int)(alpha*glow[3]*strength[s]*rim*day))<<24|cr<<16|cg<<8|cb;
            }
            for(int i=0;i<m.lat();i++) for(int j=0;j<m.lon();j++) {
                int a=i*cols+j, b=a+1, c=a+cols+1, d=a+cols;
                for(int n:new int[]{a,b,c,d}) vertex(p,out,m.pos()[n*3]*sizes[s],m.pos()[n*3+1]*sizes[s],m.pos()[n*3+2]*sizes[s],.5f,.5f,color[n],m.pos()[n*3],m.pos()[n*3+1],m.pos()[n*3+2]);
            }
        }
    }
    /** Saturn's flat ring (unit = planet radii), visible from both sides. */
    private static void ring(PoseStack.Pose p,VertexConsumer out,int alpha) {
        final int seg=96; final float r0=1.24f,r1=2.33f; int col=alpha<<24|0xE6E6E6;
        for(int i=0;i<seg;i++) {
            double a0=2*Math.PI*i/seg,a1=2*Math.PI*(i+1)/seg;
            float[][] q={{(float)Math.cos(a0)*r0,(float)Math.sin(a0)*r0,0},{(float)Math.cos(a0)*r1,(float)Math.sin(a0)*r1,1},
                {(float)Math.cos(a1)*r1,(float)Math.sin(a1)*r1,1},{(float)Math.cos(a1)*r0,(float)Math.sin(a1)*r0,0}};
            for(int w=0;w<2;w++) for(int k=0;k<4;k++) {
                float[] v=q[w==0?k:3-k];
                vertex(p,out,v[0],0,v[1],v[2],.5f,col,0,w==0?1:-1,0);
            }
        }
    }
    private CosmosRenderer() {}
}
