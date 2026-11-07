package com.dantemorrigan.gauntlet.client.boss;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModSounds;
import com.dantemorrigan.gauntlet.boss.LivingTribunal;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class TribunalClient {
	private static LivingTribunal nearby, departing;
	private static ClientLevel level;
	private static ThanosClient.Music music;
	private static int ticks, musicAt, lastPhase = -1, lastAge;
	private static float shake;
    private static final java.util.Map<Integer,Flying> BOLTS=new java.util.HashMap<>();
    private static final class Flying {
        Vec3 from,to; int age,duration;
        Flying(com.dantemorrigan.gauntlet.net.ModNetwork.TribunalBolt p) {from=p.from();to=p.to();duration=p.ticks();}
    }

	public static void init() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->StoneBurstLight.prepare());
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(com.dantemorrigan.gauntlet.net.ModNetwork.TribunalBolt.TYPE,(p,c)-> {
            if(p.ticks()==0) BOLTS.remove(p.id()); else BOLTS.put(p.id(),new Flying(p));
        });
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            var mc=Minecraft.getInstance(); if(mc.level==null || BOLTS.isEmpty()) return;
            float partial=mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            Vec3 camera=mc.gameRenderer.getMainCamera().position();
            for(Flying b:BOLTS.values()) {
                Vec3 at=b.from.lerp(b.to,Math.min(1,(b.age+partial)/b.duration)).subtract(camera);
                TribunalRenderer.energyBolt(context.poseStack(),context.submitNodeCollector(),at,b.to.subtract(b.from).scale(1.0/b.duration));
            }
        });
		// Keep dialogue and our cinematic layer; normal combat UI returns as soon as the scene ends.
		for (var id : java.util.List.of(
				net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CROSSHAIR,
				net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.HOTBAR,
				net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.ARMOR_BAR,
				net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.HEALTH_BAR,
				net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.FOOD_BAR,
				net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.EXPERIENCE_LEVEL,
				net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.INFO_BAR,
				net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.BOSS_BAR)) {
			HudElementRegistry.replaceElement(id, original -> (g,d) -> { if (!cinematic()) original.extractRenderState(g,d); });
		}
		ClientPlayConnectionEvents.DISCONNECT.register((h,mc) -> reset());
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (level != mc.level) { reset(); level = mc.level; }
			if (mc.isPaused()) return;
			ticks++;
            BOLTS.values().removeIf(b->++b.age>b.duration);
			shake *= .84f;
            java.util.List<LivingTribunal> actors=mc.player==null || mc.level==null?java.util.List.of():mc.level.getEntitiesOfClass(
                LivingTribunal.class,mc.player.getBoundingBox().inflate(340),boss->boss.challenger()==mc.player.getId());
            nearby=actors.stream().filter(boss->boss.isAlive() && boss.phase()!=LivingTribunal.DEPARTURE).findFirst().orElse(null);
            departing=actors.stream().filter(boss->boss.phase()==LivingTribunal.DEPARTURE).findFirst().orElse(null);
            if(departing!=null && departing.deathTime>=LivingTribunal.EXPLOSION_TICK && departing.deathTime<LivingTribunal.EXPLOSION_TICK+3)shake=1;
			if (music != null && (music.isStopped() || ticks - musicAt > 40 && !mc.getSoundManager().isActive(music))) { music.halt(); music = null; }
			if (nearby != null && mc.player.isAlive()) {
				if (music == null || music.fading()) {
					if (music != null) music.halt();
					music = new ThanosClient.Music(ModSounds.TRIBUNAL_MUSIC);
					mc.getSoundManager().play(music);
					musicAt = ticks;
				}
				int phase = nearby.phase(), age = nearby.sceneAge();
				if (phase == LivingTribunal.VERDICT && lastPhase == phase && lastAge < LivingTribunal.SHATTER_TICK && age >= LivingTribunal.SHATTER_TICK) {
					shake = 1;
					Vec3 at = TribunalRenderer.handPosition(nearby, 0);
					var random = mc.level.getRandom();
					for (int i = 0; i < 90; i++) mc.level.addParticle(new net.minecraft.core.particles.DustParticleOptions(
							i % 3 == 0 ? com.dantemorrigan.gauntlet.Stone.values()[(i / 3) % 6].color : 0xffd18b, 2f),
							at.x, at.y, at.z, random.nextGaussian() * .5, random.nextGaussian() * .5, random.nextGaussian() * .5);
				}
				lastPhase = phase; lastAge = age;
			} else { lastPhase = -1; if (music != null) music.fadeOut(45); }
		});
		HudElementRegistry.addLast(InfinityGauntlet.id("tribunal_cinematic"), (g, partial) -> {
            if(departing!=null) {
                float t=(departing.deathTime+partial.getGameTimeDeltaPartialTick(false)-LivingTribunal.EXPLOSION_TICK)/12f;
                if(t>=0 && t<1)g.fill(0,0,g.guiWidth(),g.guiHeight(),ARGB.color((int)(210*(1-t)*(1-t)),255,247,225));
            }
			LivingTribunal boss = nearby;
			if (boss == null) return;
            if(boss.phase()==LivingTribunal.BATTLE) {
                if(boss.attack()>0) g.centeredText(Minecraft.getInstance().font,com.dantemorrigan.gauntlet.text.BossText.action(Component.translatable("tribunal.infinity_gauntlet.attack."+boss.attack())),g.guiWidth()/2,70,0xffffdfa0);
                return;
            }
			int age = boss.sceneAge();
			boolean verdict = boss.phase() == LivingTribunal.VERDICT;
			int w = g.guiWidth(), h = g.guiHeight();
			int bars = Math.round(h * .09f * Mth.clamp(age / 14f, 0, 1));
			g.fill(0, 0, w, bars, 0xf0070410);
			g.fill(0, h - bars, w, h, 0xf0070410);
			if (verdict && age >= LivingTribunal.SHATTER_TICK) {
                StoneBurstLight.draw(g,age+partial.getGameTimeDeltaPartialTick(false)-LivingTribunal.SHATTER_TICK);
			}
			if (verdict) g.centeredText(Minecraft.getInstance().font, Component.literal("♥"), w / 2, h - bars - 16, 0xffff5867);
			if (bars > 10) g.centeredText(Minecraft.getInstance().font,
					com.dantemorrigan.gauntlet.text.BossText.name(Component.translatable(verdict ? "tribunal.infinity_gauntlet.verdict" : "entity.infinity_gauntlet.living_tribunal")),
					w / 2, h - bars + (bars - 8) / 2, 0xffffdfa0);
		});
	}


	public static boolean cinematic() { return nearby != null && nearby.phase() != LivingTribunal.BATTLE; }
	public static boolean inputLocked() { return nearby != null && nearby.phase() == LivingTribunal.VERDICT; }
	public static boolean musicPlaying() { return music != null && !music.isStopped() && !music.fading() && Minecraft.getInstance().getSoundManager().isActive(music); }
	public static Vec3 cameraFocus(float partial) {
		if (!inputLocked()) return null;
		float t = Mth.clamp(nearby.sceneAge() / 65f, 0, 1);
		return nearby.mark().lerp(TribunalRenderer.handPosition(nearby, partial), t * t * (3 - 2 * t));
	}
	public static float shake(float partial, boolean pitch) {
		return shake * Mth.sin((ticks + partial) * (pitch ? 2.3f : 3.1f)) * 1.4f;
	}
	private static void reset() {
		if (music != null) music.halt();
		BOLTS.clear(); music = null; nearby = null; departing=null; level = null; lastPhase = -1; shake = 0;
	}

	private TribunalClient() {}
}
