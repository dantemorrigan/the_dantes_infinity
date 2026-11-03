package com.dantemorrigan.gauntlet.client.cosmos;
import com.dantemorrigan.gauntlet.client.fx.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import org.joml.Vector3f;
/** Short, bounded life; velocity-aligned streaks also leave a trail behind the fist. */
public final class CosmosParticle extends FxParticle {
    public CosmosParticle(ClientLevel level,double x,double y,double z,double vx,double vy,double vz,SpriteSet sprites,String name) {
        super(level,x,y,z,sprites,VfxPresets.get(name),name.endsWith("streak")?18f:2f,null,-1,null,.9f);
        xd=vx; yd=vy; zd=vz; finiteLoop();
        if(vx*vx+vy*vy+vz*vz>.001 && name.endsWith("streak")) alignTo(new Vector3f((float)vx,(float)vy,(float)vz).normalize());
    }
    @Override protected void motion() { setPos(x+xd,y+yd,z+zd); }
}
