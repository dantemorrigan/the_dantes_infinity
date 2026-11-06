package com.dantemorrigan.gauntlet.boss;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import net.minecraft.world.phys.Vec3;

/** Shared animated palm geometry: server projectile starts and client spell anchors use the same rig. */
public final class TribunalPose {
    public static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    public static float reach(int phase,int attack,float time) {
        if(phase==LivingTribunal.VERDICT)return smooth(time/28);
        return attack>0?(float)Math.sin(Math.PI*Math.max(0,Math.min(1,time/48))):0;
    }
    private static void joint(Matrix4f m,float x,float y,float z,float rx,float ry,float rz){
        float d=(float)(Math.PI/180);m.translate(x,y,z).rotateZ(-rz*d).rotateY(-ry*d).rotateX(-rx*d).translate(-x,-y,-z);
    }
    public static Vec3 palm(float yaw,float age,int phase,int attack,float time,boolean left) {
        float r=reach(phase,attack,time), sign=left?1:-1;
        Matrix4f m=new Matrix4f().rotateY((float)Math.toRadians(180-yaw)).scale(TribunalSpace.MODEL_SCALE/16f);
        joint(m,0,37,0,0,0,2*(float)Math.sin(age*.039));
        joint(m,sign*14,43,0,left?-28*r:-70*r,left?0:-14*r,left?10:-10);
        joint(m,sign*14,35,0,left?-16*r:-27*r,0,0);
        joint(m,sign*14,27,0,left?0:19*smooth((time-87)/18)*r,0,0);
        return new Vec3(m.transformPosition(new Vector3f(sign*14,24.7f,-1)));
    }
    private TribunalPose(){}
}
