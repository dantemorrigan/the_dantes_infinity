package com.dantemorrigan.gauntlet.boss;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Server hit volumes follow the same animated rig as the enormous visible body. */
public final class TribunalHitbox {
    private static AABB box(LivingTribunal b,Matrix4f m,float x0,float y0,float z0,float x1,float y1,float z1) {
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        for(int i=0;i<8;i++) {
            Vector3f p=m.transformPosition(new Vector3f((i&1)==0?x0:x1,(i&2)==0?y0:y1,(i&4)==0?z0:z1));
            minX=Math.min(minX,p.x);minY=Math.min(minY,p.y);minZ=Math.min(minZ,p.z);
            maxX=Math.max(maxX,p.x);maxY=Math.max(maxY,p.y);maxZ=Math.max(maxZ,p.z);
        }
        return new AABB(minX,minY,minZ,maxX,maxY,maxZ).move(b.position()).inflate(.8);
    }
    public static AABB core(LivingTribunal b) {
        return box(b,TribunalPose.body(b.getYRot(),b.tickCount),-4,37,-7,4,45,-5);
    }
    public static List<AABB> parts(LivingTribunal b) {
        var parts=new ArrayList<AABB>();var body=TribunalPose.body(b.getYRot(),b.tickCount);
        parts.add(core(b));parts.add(box(b,body,-9,28,-5.6f,9,46,4.5f));
        parts.add(box(b,body,-6.5f,-.5f,-4.3f,-.9f,29,3));parts.add(box(b,body,.9f,-.5f,-4.3f,6.5f,29,3));
        float r=TribunalPose.reach(b.phase(),b.attack(),b.sceneAge());
        var head=new Matrix4f(body);TribunalPose.joint(head,0,54,0,8*r,6*(float)Math.sin(b.tickCount*.048),0);
        parts.add(box(b,head,-8,47,-5.5f,8,61,5.5f));
        for(boolean left:new boolean[]{false,true}) {
            float x=left?14:-14;var arm=new Matrix4f(body);
            TribunalPose.joint(arm,x,43,0,left?-28*r:-70*r,left?0:-14*r,left?10:-10);
            parts.add(box(b,arm,x-4,35,-4,x+4,46,4));
            TribunalPose.joint(arm,x,35,0,left?-16*r:-27*r,0,0);
            parts.add(box(b,arm,x-3.5f,27,-3.5f,x+3.5f,36.5f,3.5f));
            TribunalPose.joint(arm,x,27,0,left?0:19*TribunalPose.smooth((b.sceneAge()-87)/18f)*r,0,0);
            parts.add(box(b,arm,x-3.8f,20,-3.5f,x+3.8f,28,3.5f));
        }
        return parts;
    }
    public static AABB bounds(LivingTribunal b) {
        var boxes=parts(b);AABB out=boxes.getFirst();for(int i=1;i<boxes.size();i++)out=out.minmax(boxes.get(i));return out;
    }
    public static Optional<Vec3> clip(LivingTribunal b,Vec3 from,Vec3 to,double radius) {
        Vec3 closest=null;double best=Double.POSITIVE_INFINITY;
        for(var part:parts(b)) {
            var hit=part.inflate(radius);Vec3 at=hit.contains(from)?from:hit.clip(from,to).orElse(null);
            if(at!=null && from.distanceToSqr(at)<best){closest=at;best=from.distanceToSqr(at);}
        }
        return Optional.ofNullable(closest);
    }
    private TribunalHitbox() {}
}
