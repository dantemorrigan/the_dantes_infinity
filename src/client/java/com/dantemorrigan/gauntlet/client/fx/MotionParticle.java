package com.dantemorrigan.gauntlet.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;

/**
 * Reality stone sprites that move by themselves: butterflies flutter on arcs around where they were born,
 * petals fall with a sideways sway and spin, glass shards fly out and tumble, aether ribbons drift and rise.
 */
public class MotionParticle extends FxParticle {
	public enum Kind { BUTTERFLY, PETAL, GLASS, AETHER }

	private final Kind kind;
	private double ax, ay, az;           // anchor (butterflies; drifts with the spawn velocity)
	private final float phase, spinSpeed;

	public MotionParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
			SpriteSet sprites, VfxPresets.Spec spec, float size, Kind kind) {
		super(level, x, y, z, sprites, spec, size, null, -1, null, 1f);
		this.kind = kind;
		this.ax = x;
		this.ay = y;
		this.az = z;
		this.phase = this.random.nextFloat() * 6.28f;
		this.spinSpeed = (this.random.nextFloat() - 0.5f) * 0.4f;
		this.xd = vx;
		this.yd = vy;
		this.zd = vz;
		finiteLoop();
	}

	@Override
	protected void motion() {
		float t = this.age + this.phase * 10;
		switch (this.kind) {
			case BUTTERFLY -> {
				// Lazy figure-eights around the anchor, slowly climbing; a spawn velocity carries the anchor away
				// (strike: butterflies scatter upward and dissolve).
				this.ax += this.xd;
				this.ay += this.yd;
				this.az += this.zd;
				double r = 0.6 + 0.4 * Math.sin(t * 0.05);
				double tx = this.ax + Math.cos(t * 0.11 + this.phase) * r;
				double tz = this.az + Math.sin(t * 0.07 + this.phase) * r * 1.3;
				double ty = this.ay + Math.sin(t * 0.23) * 0.25 + this.age * 0.01;
				this.setPos(this.x + (tx - this.x) * 0.25, this.y + (ty - this.y) * 0.25, this.z + (tz - this.z) * 0.25);
				this.oRoll = this.roll;
				this.roll = Mth.sin(t * 0.6f) * 0.3f;
			}
			case PETAL -> {
				this.xd = this.xd * 0.9 + Math.sin(t * 0.21) * 0.012;
				this.zd = this.zd * 0.9 + Math.cos(t * 0.17) * 0.012;
				this.yd = Math.max(-0.06, this.yd * 0.9 - 0.006);
				this.setPos(this.x + this.xd, this.y + this.yd, this.z + this.zd);
				this.oRoll = this.roll;
				this.roll += 0.15f + this.spinSpeed;
			}
			case GLASS -> {
				this.xd *= 0.94;
				this.yd = this.yd * 0.94 - 0.004;
				this.zd *= 0.94;
				this.setPos(this.x + this.xd, this.y + this.yd, this.z + this.zd);
				this.oRoll = this.roll;
				this.roll += this.spinSpeed * 2;
			}
			case AETHER -> {
				this.setPos(this.x + Math.sin(t * 0.05) * 0.02, this.y + 0.015, this.z + Math.cos(t * 0.05) * 0.02);
				this.oRoll = this.roll;
				this.roll += this.spinSpeed * 0.3f;
			}
		}
	}
}
