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

public final class TribunalClient {
	private static LivingTribunal nearby;
	private static ClientLevel level;
	private static ThanosClient.Music music;

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (level != mc.level) {
				if (music != null) music.halt();
				music = null;
				level = mc.level;
			}
			nearby = mc.player == null || mc.level == null ? null
					: mc.level.getEntitiesOfClass(LivingTribunal.class, mc.player.getBoundingBox().inflate(105),
							boss -> boss.isAlive() && boss.phase() != LivingTribunal.DEPARTURE).stream().findFirst().orElse(null);
			if (music != null && music.isStopped()) music = null;
			if (nearby != null && mc.player.isAlive()) {
				if (music == null) {
					music = new ThanosClient.Music(ModSounds.TRIBUNAL_MUSIC);
					mc.getSoundManager().play(music);
				}
				mc.getMusicManager().stopPlaying();
			} else if (music != null) music.fadeOut(45);
		});
		HudElementRegistry.addLast(InfinityGauntlet.id("tribunal_cinematic"), (g, partial) -> {
			LivingTribunal boss = nearby;
			if (boss == null || boss.phase() == LivingTribunal.BATTLE) return;
			int age = boss.sceneAge();
			boolean verdict = boss.phase() == LivingTribunal.VERDICT;
			int w = g.guiWidth(), h = g.guiHeight();
			int bars = Math.round(h * .09f * Mth.clamp(age / 14f, 0, 1));
			g.fill(0, 0, w, bars, 0xf0070410);
			g.fill(0, h - bars, w, h, 0xf0070410);
			if (verdict && age >= LivingTribunal.SHATTER_TICK) {
				float fade = Mth.clamp((age - LivingTribunal.SHATTER_TICK) / 26f, 0, 1);
				g.fill(0, bars, w, h - bars, ARGB.color((int) (235 * fade * fade), 6, 4, 13));
			}
			if (bars > 10) g.centeredText(Minecraft.getInstance().font,
					Component.translatable(verdict ? "tribunal.infinity_gauntlet.verdict" : "entity.infinity_gauntlet.living_tribunal"),
					w / 2, h - bars + (bars - 8) / 2, 0xffffdfa0);
		});
	}

	private TribunalClient() {}
}
