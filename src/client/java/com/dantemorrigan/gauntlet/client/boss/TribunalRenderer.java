package com.dantemorrigan.gauntlet.client.boss;

import com.dantemorrigan.gauntlet.GauntletState;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModItems;
import com.dantemorrigan.gauntlet.Stone;
import com.dantemorrigan.gauntlet.boss.LivingTribunal;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public final class TribunalRenderer extends EntityRenderer<LivingTribunal, TribunalRenderer.State> {
	private static final Identifier GOLD = InfinityGauntlet.id("textures/entity/tribunal/tribunal.png");
	private static final Identifier EMISSIVE = InfinityGauntlet.id("textures/entity/tribunal/tribunal_emissive.png");
	private static final Identifier HOLE = InfinityGauntlet.id("textures/tribunal/singularity.png");
	private static final Identifier HALO = InfinityGauntlet.id("textures/tribunal/halo.png");
	private static final Identifier EDICT = InfinityGauntlet.id("textures/tribunal/edict.png");
	private static final Identifier GEOMETRY = InfinityGauntlet.id("tribunal/tribunal.geo.json");
	private static final float SCALE = com.dantemorrigan.gauntlet.boss.TribunalSpace.MODEL_SCALE;
	private static GeoModel model;
    private static final java.util.Set<GeoModel.Cube> GLOW = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private static void loadModel() {
        if(model!=null) return;
        model=GeoModel.load(GEOMETRY);
        GLOW.add(model.bone("core").cubes().get(1));GLOW.add(model.bone("core").cubes().get(2));
        GLOW.add(model.bone("head").cubes().get(4));GLOW.add(model.bone("head").cubes().get(9));
    }

	public TribunalRenderer(EntityRendererProvider.Context context) { super(context); }

	public static final class State extends EntityRenderState {
		int phase, attack;
		float yaw, ticks, death;
		Vec3 mark = Vec3.ZERO, base = Vec3.ZERO;
		final ItemStackRenderState gauntlet = new ItemStackRenderState();
	}

	@Override protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(LivingTribunal e) { return e.getBoundingBox().inflate(150); }

	@Override public State createRenderState() { return new State(); }

	@Override
	public void extractRenderState(LivingTribunal boss, State state, float partial) {
		super.extractRenderState(boss, state, partial);
		state.phase = boss.phase();
		state.attack = boss.attack();
		state.yaw = Mth.rotLerp(partial,boss.yRotO,boss.getYRot());
		state.ticks = boss.sceneAge() + partial;
		state.death = boss.deathTime + partial;
		state.mark = boss.mark();
		state.base = boss.getPosition(partial);
		if (state.phase == LivingTribunal.VERDICT && state.ticks < LivingTribunal.SHATTER_TICK) {
			ItemStack stack = new ItemStack(ModItems.GAUNTLET);
			for (Stone stone : Stone.values()) GauntletState.putStone(stack, new ItemStack(stone.item()));
			if(state.ticks>=85) stack.set(com.dantemorrigan.gauntlet.ModComponents.SNAPPED,true);
			stack.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, InfinityGauntlet.id("gauntlet_worn"));
			Minecraft.getInstance().getItemModelResolver().updateForTopItem(state.gauntlet, stack,
					ItemDisplayContext.NONE, boss.level(), null, 0);
		}
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		loadModel();
		float reveal = state.phase == LivingTribunal.ARRIVAL ? smooth((state.ticks - 36) / 100) : 1;
		float fade = state.phase == LivingTribunal.DEPARTURE ? 1 - smooth((state.death-48) / 37) : 1;
		float rise = 0;
        Vec3 forward=Vec3.directionFromRotation(0,state.yaw);
        Vec3 emerge=state.phase==LivingTribunal.ARRIVAL?forward.scale(-112*(1-reveal)):Vec3.ZERO;
        Vec3 plane=state.base.subtract(Minecraft.getInstance().gameRenderer.getMainCamera().position()).add(forward.scale(-36));
        Vec3 normal=forward.reverse();
        GeoModel.Clip clip=state.phase==LivingTribunal.ARRIVAL?new GeoModel.Clip((float)normal.x,0,(float)normal.z,(float)normal.dot(plane)):null;
		if (state.phase == LivingTribunal.ARRIVAL) {
			float radius = 122 * smooth(state.ticks / 32) * (1 - smooth((state.ticks - 146) / 34));
			Vec3 behind = Vec3.directionFromRotation(0, state.yaw).scale(-36).add(0, 53, 0);
			portal(pose,collector,HOLE,behind,radius,state.yaw,0,0xffffffff);
			portal(pose,collector,HALO,behind,radius*1.15f,state.yaw,state.ticks*.6f,0xe6ffffff);
            portal(pose,collector,HALO,behind,radius*1.3f,state.yaw,-state.ticks*.4f,0x887e74ed);
		}
		Map<String, GeoModel.BonePose> bones = bones(state, reveal);
        if(state.attack>0) {
            for(boolean left:new boolean[]{false,true}) {
                Vec3 palm=com.dantemorrigan.gauntlet.boss.TribunalPose.palm(state.yaw,state.ageInTicks,state.phase,state.attack,state.ticks,left);
                float charge=12+smooth(state.ticks/34)*10;
                billboard(pose,collector,HALO,palm,charge,state.ticks*1.5f,0xffaadeff);
                billboard(pose,collector,EDICT,palm,charge*.8f,-state.ticks*2,0xfff6db9b);
                sphere(pose,collector,palm,charge*.55f,0x99d3ecff);
                if(left && (state.attack==LivingTribunal.GRAVITY || state.attack==LivingTribunal.PRISON || state.attack==LivingTribunal.WAVE))
                    beam(pose,collector,palm,state.mark.subtract(state.base),.35f,0x99d9aaff);
            }
        }

		if (fade > 0) {
			pose.pushPose();
            pose.translate(emerge.x,emerge.y,emerge.z);
			modelTransform(pose, state.yaw, rise);
			int tint = ARGB.color((int) (fade * 255), 255, 255, 255);
			collector.submitCustomGeometry(pose, fade < .99f ? RenderTypes.entityTranslucent(GOLD) : RenderTypes.entityCutout(GOLD),
					(p, out) -> drawBody(p,out,bones,(bone,cube)->GLOW.contains(cube)?0:tint,clip,state));
			collector.order(1).submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(EMISSIVE),
					(p, out) -> drawBody(p,out,bones,(bone,cube)->GLOW.contains(cube)?tint:0,clip,state));
			pose.popPose();
		}
        if(state.phase==LivingTribunal.DEPARTURE) {
            Vec3 center=new Vec3(0,53,0);
            float collapse=smooth(state.death/55);
            billboard(pose,collector,HALO,center,8+30*collapse,state.death*3,ARGB.color((int)(221*(1-smooth((state.death-55)/45))),233,229,255));
            if(state.death>=LivingTribunal.EXPLOSION_TICK) {
                float t=(state.death-LivingTribunal.EXPLOSION_TICK)/45;
                int color=ARGB.color((int)(255*(1-t)),255,243,210);
                blast(pose,collector,center,20+160*t,color);
                sphere(pose,collector,center,8+130*t,color);
            }
        }
		if (state.attack > 0) {
			Vec3 at = state.mark.subtract(state.base);
			float radius = state.attack == LivingTribunal.NOVA ? 30 : state.attack == LivingTribunal.GRAVITY ? 18 : state.attack == LivingTribunal.PRISON ? 24 : state.attack == LivingTribunal.LANCE ? 5 : 10;
			float opacity = 1 - smooth((state.ticks - (state.attack==LivingTribunal.PRISON?90:state.attack==LivingTribunal.WAVE?70:LivingTribunal.WINDUP)) / 12);
            if(state.attack==LivingTribunal.WAVE) radius=Math.max(1,(state.ticks-34)*1.6f);
            if(state.attack==LivingTribunal.VOLLEY) opacity=0;
			int color = ARGB.color((int) (opacity * 235), 255, 216, 151);
			if(state.attack==LivingTribunal.WAVE || state.attack==LivingTribunal.PRISON) sphere(pose,collector,at,radius,color);
            else billboard(pose, collector, EDICT, at, radius * (state.ticks < LivingTribunal.WINDUP ? 1 : 1.3f),
					state.ticks * 2, color);
			// The edict focuses into a cosmic ray; the marked radius is the server's actual hit radius.
			if(state.attack!=LivingTribunal.WAVE && state.attack!=LivingTribunal.PRISON) beam(pose, collector, com.dantemorrigan.gauntlet.boss.TribunalPose.palm(state.yaw,state.ageInTicks,state.phase,state.attack,state.ticks,false), at, state.ticks < LivingTribunal.WINDUP ? .12f : 1.6f,
					ARGB.color((int) (opacity * (state.ticks < LivingTribunal.WINDUP ? 85 : 255)), 230, 238, 255));
			if (state.ticks >= LivingTribunal.WINDUP) {
				billboard(pose, collector, HALO, at, radius * 1.65f, state.ticks * 5, color);
			}
		}
		if (state.phase == LivingTribunal.VERDICT) {
			PoseStack handPose = new PoseStack();
			modelTransform(handPose, state.yaw, 0);
			Vec3 hand = new Vec3(model.pivotOf("right_hand", bones, handPose)).add(palmOffset(state.yaw));
			float progress = smooth((state.ticks - 16) / 70);
			Vec3 glove = state.mark.subtract(state.base).lerp(hand, progress).add(0, 5 * Mth.sin(Mth.PI * progress), 0);
			if (state.ticks < LivingTribunal.SHATTER_TICK) {
				pose.pushPose();
				pose.translate(glove.x, glove.y, glove.z);
				pose.mulPose(Axis.YP.rotationDegrees(state.ticks < 85 ? state.ticks * 5 : 65));
				float scale = 1.2f + progress * 4.8f;
				pose.scale(scale, scale, scale);
				state.gauntlet.submit(pose, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
				pose.popPose();
				billboard(pose, collector, HALO, glove, 1.1f + progress * 2.9f, -state.ticks * 5, 0xadffffff);
			} else {
				float shatter = smooth((state.ticks - LivingTribunal.SHATTER_TICK) / 24);
				billboard(pose, collector, EDICT, hand, 2 + 17 * shatter, state.ticks * 5,
						ARGB.color((int) (255 * (1 - shatter)), 255, 246, 206));
			}
		}
		super.submit(state, pose, collector, camera);
	}

    private static void drawBody(PoseStack.Pose p,VertexConsumer out,Map<String,GeoModel.BonePose> bones,GeoModel.CubeTint tint,GeoModel.Clip clip,State state) {
        if(state.phase==LivingTribunal.DEPARTURE) model.renderFragments(ThanosRenderer.stack(p),out,bones,LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,tint,state.death);
        else model.renderClipped(ThanosRenderer.stack(p),out,bones,LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,tint,clip);
    }

	private static Map<String, GeoModel.BonePose> bones(State state, float reveal) {
		Map<String, GeoModel.BonePose> bones = new HashMap<>();
		float reach = state.phase == LivingTribunal.VERDICT ? smooth(state.ticks / 28)
				: state.attack > 0 ? Mth.sin(Mth.PI * Mth.clamp(state.ticks / 48, 0, 1)) : 0;
		float open = state.phase == LivingTribunal.ARRIVAL ? Mth.sin(reveal*Mth.PI) : 0;
		bone(bones, "right_arm", -70 * reach - 9 * open, -14 * reach, -10 - 8 * open);
		bone(bones, "left_arm", -28 * reach - 9 * open, 0, 10 + 8 * open);
		bone(bones, "right_forearm", -27 * reach, 0, 0);
		bone(bones, "left_forearm", -16 * reach, 0, 0);
		bone(bones, "right_hand", 19 * smooth((state.ticks - 87) / 18) * reach, 0, 0);
		bone(bones, "head", state.phase == LivingTribunal.DEPARTURE ? 32 * smooth(state.death / 52) : state.phase==LivingTribunal.ARRIVAL?22*(1-reveal):8 * reach,
				6 * Mth.sin(state.ageInTicks * .048f), 0);
		bone(bones, "body", 0, 0, 2 * Mth.sin(state.ageInTicks * .039f));
        if(state.phase==LivingTribunal.ARRIVAL) {
            bone(bones,"right_leg",-5*Mth.sin(reveal*Mth.PI),0,0);
            bone(bones,"left_leg",3*Mth.sin(reveal*Mth.PI),0,0);
        }

		return bones;
	}

	public static Vec3 handPosition(LivingTribunal boss, float partial) {
		loadModel();
		State state = new State();
		state.phase = boss.phase(); state.attack = boss.attack(); state.ticks = boss.sceneAge() + partial;
		state.yaw = Mth.rotLerp(partial,boss.yRotO,boss.getYRot()); state.ageInTicks = boss.tickCount + partial;
		PoseStack pose = new PoseStack(); modelTransform(pose, state.yaw, 0);
		return boss.getPosition(partial).add(new Vec3(model.pivotOf("right_hand", bones(state, 1), pose))).add(palmOffset(state.yaw));
	}

	private static Vec3 palmOffset(float yaw) { return Vec3.directionFromRotation(0, yaw).scale(12).add(0, 10, 0); }

	private static void modelTransform(PoseStack pose, float yaw, float rise) {
		pose.translate(0, rise, 0);
		pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
		pose.scale(SCALE, SCALE, SCALE);
	}

    private static void portal(PoseStack pose,SubmitNodeCollector collector,Identifier texture,Vec3 at,float radius,float yaw,float roll,int color) {
        if(radius<.01f)return;
        pose.pushPose();pose.translate(at.x,at.y,at.z);pose.mulPose(Axis.YP.rotationDegrees(-yaw));pose.mulPose(Axis.ZP.rotationDegrees(roll));
        collector.order(-1).submitCustomGeometry(pose,texture.equals(HOLE)?RenderTypes.entityTranslucent(texture):RenderTypes.entityTranslucentEmissive(texture),(p,out)-> {
            vertex(out,p,-radius,-radius,0,1,color);vertex(out,p,radius,-radius,1,1,color);
            vertex(out,p,radius,radius,1,0,color);vertex(out,p,-radius,radius,0,0,color);
        });pose.popPose();
    }

	private static void billboard(PoseStack pose, SubmitNodeCollector collector, Identifier texture, Vec3 at,
			float radius, float roll, int color) {
		if (radius < .01f || (color >>> 24) == 0) return;
		pose.pushPose();
		pose.translate(at.x, at.y, at.z);
		pose.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
		pose.mulPose(Axis.ZP.rotationDegrees(roll));
		collector.order(-1).submitCustomGeometry(pose, texture.equals(HOLE) ? RenderTypes.entityTranslucent(texture) : RenderTypes.entityTranslucentEmissive(texture), (p, out) -> {
			vertex(out, p, -radius, -radius, 0, 1, color);
			vertex(out, p, radius, -radius, 1, 1, color);
			vertex(out, p, radius, radius, 1, 0, color);
			vertex(out, p, -radius, radius, 0, 0, color);
		});
		pose.popPose();
	}

	private static void beam(PoseStack pose, SubmitNodeCollector collector, Vec3 from, Vec3 to, float width, int color) {
		Vec3 d = to.subtract(from);
		if (d.lengthSqr() < .01) return;
		float length = (float) d.length();
		pose.pushPose(); pose.translate(from.x, from.y, from.z);
		pose.mulPose(new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(0,0,1), d.normalize().toVector3f()));
		collector.order(1).submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(InfinityGauntlet.id("textures/tribunal/white.png")), (p,out) -> {
			for (int side = 0; side < 2; side++) {
				float x = side == 0 ? width : 0, y = side == 0 ? 0 : width;
				out.addVertex(p,-x,-y,0).setColor(color).setUv(0,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p,0,1,0);
				out.addVertex(p,x,y,0).setColor(color).setUv(1,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p,0,1,0);
				out.addVertex(p,x,y,length).setColor(color).setUv(1,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p,0,1,0);
				out.addVertex(p,-x,-y,length).setColor(color).setUv(0,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p,0,1,0);
			}
		});
		pose.popPose();
	}

    /** Three great circles share one submit; the shell represents a true spherical gameplay radius. */
    public static void sphere(PoseStack pose,SubmitNodeCollector collector,Vec3 at,float radius,int color) {
        if(radius<=0 || color>>>24==0) return;
        pose.pushPose();pose.translate(at.x,at.y,at.z);
        collector.submitCustomGeometry(pose,RenderTypes.entityTranslucentEmissive(InfinityGauntlet.id("textures/tribunal/white.png")),(p,out)-> {
            for(int axis=0;axis<3;axis++) for(int i=0;i<48;i++) {
                double a=i*Math.PI/24,b=(i+1)*Math.PI/24;float w=Math.max(.06f,radius*.006f);
                for(int corner=0;corner<4;corner++) {
                    double angle=corner<2?a:b;float r=radius+((corner==0||corner==3)?-w:w);
                    float x=(float)Math.cos(angle)*r,y=(float)Math.sin(angle)*r;
                    out.addVertex(p,axis==0?0:x,axis==1?0:axis==0?x:y,axis==2?0:axis==0?y:axis==1?y:0)
                        .setColor(color).setUv(.5f,.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p,0,1,0);
                }
            }
        });pose.popPose();
    }

    public static void energyRay(PoseStack pose,SubmitNodeCollector collector,Vec3 from,Vec3 to,float width,int color) {beam(pose,collector,from,to,width,color);}
    public static void blast(PoseStack pose,SubmitNodeCollector collector,Vec3 at,float radius,int color) {
        billboard(pose,collector,EDICT,at,radius,0,color);
        billboard(pose,collector,HALO,at,radius*.6f,30,color);
    }
    public static void energyBolt(PoseStack pose, SubmitNodeCollector collector, Vec3 at, Vec3 velocity) {
        billboard(pose,collector,HALO,at,2.2f,0,0xffb5ecff);
        billboard(pose,collector,EDICT,at,1.1f,0,0xffffffff);
        beam(pose,collector,at.subtract(velocity.scale(3)),at,.5f,0xff8fbdff);
    }

	private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float u, float v, int color) {
		out.addVertex(pose, x, y, 0).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 0, 1);
	}

	private static void bone(Map<String, GeoModel.BonePose> bones, String name, float x, float y, float z) {
		var pose = new GeoModel.BonePose();
		pose.rx = x; pose.ry = y; pose.rz = z;
		bones.put(name, pose);
	}

	private static float smooth(float t) {
		float v = Mth.clamp(t, 0, 1);
		return v * v * (3 - 2 * v);
	}
}
