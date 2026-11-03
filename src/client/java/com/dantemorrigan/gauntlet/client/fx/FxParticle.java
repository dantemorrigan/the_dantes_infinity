package com.dantemorrigan.gauntlet.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.Function;

/**
 * Sprite-sheet particle from the VFX packs: 4 frames chosen by AGE (never random), optional loop, fade-out in the
 * final frame, optional flat orientation along a surface normal, translucent layer (no black squares) and full
 * brightness for energy. The size never grows in code: ring expansion is already drawn in the frames.
 */
public class FxParticle extends SingleQuadParticle {
	private final SpriteSet sprites;
	private final VfxPresets.Spec spec;
	private final Quaternionf flatRotation;
	private final int ownerId;
	private final Function<Entity, Vec3> follow;
	private final float baseAlpha;

	public FxParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, VfxPresets.Spec spec,
			float sizeMul, Vector3f normal, int ownerId, Function<Entity, Vec3> follow, float alpha) {
		super(level, x, y, z, sprites.get(0, Math.max(1, spec.frames() - 1)));
		this.sprites = sprites;
		this.spec = spec;
		this.ownerId = ownerId;
		this.follow = follow;
		this.baseAlpha = alpha;
		this.quadSize = spec.width() * 0.5f * sizeMul; // quad spans ±quadSize
		this.lifetime = spec.loop() ? Integer.MAX_VALUE : spec.lifetime();
		this.gravity = 0;
		this.hasPhysics = false;
		this.xd = this.yd = this.zd = 0;
		this.alpha = alpha;
		if (normal != null) {
			// Quads are built in the XY plane facing +Z; rotate +Z onto the surface normal, random spin around it.
			this.flatRotation = new Quaternionf().rotationTo(0, 0, 1, normal.x, normal.y, normal.z)
					.rotateZ(this.random.nextFloat() * (float) Math.PI * 2);
		} else {
			this.flatRotation = null;
		}
		this.setSprite(sprites.get(0, Math.max(1, spec.frames() - 1)));
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		if (this.ownerId >= 0) {
			Entity owner = this.level.getEntity(this.ownerId);
			Vec3 pos = owner != null ? this.follow.apply(owner) : null;
			if (pos == null) {
				this.remove(); // charge released / owner gone: the aura disappears immediately
				return;
			}
			this.setPos(pos.x, pos.y, pos.z);
		}
		if (!this.spec.loop() && this.age++ >= this.lifetime) {
			this.remove();
			return;
		}
		if (this.spec.loop()) this.age++;
		int last = this.spec.frames() - 1;
		int frame = this.spec.loop()
				? (this.age / this.spec.ticksPerFrame()) % this.spec.frames()
				: Math.min(last, this.age / this.spec.ticksPerFrame());
		this.setSprite(this.sprites.get(frame, last));
		if (!this.spec.loop() && frame == last) {
			float t = (this.age - last * this.spec.ticksPerFrame()) / (float) Math.max(1, this.lifetime - last * this.spec.ticksPerFrame());
			this.alpha = this.baseAlpha * Math.max(0f, 1f - t);
		}
	}

	@Override
	public FacingCameraMode getFacingCameraMode() {
		if (this.flatRotation != null) {
			Quaternionf rot = this.flatRotation;
			return (target, camera, partial) -> target.set(rot);
		}
		return super.getFacingCameraMode();
	}

	@Override
	protected Layer getLayer() {
		return Layer.TRANSLUCENT;
	}

	@Override
	public int getLightCoords(float partialTick) {
		return this.spec.fullBright() ? LightCoordsUtil.FULL_BRIGHT : super.getLightCoords(partialTick);
	}
}
