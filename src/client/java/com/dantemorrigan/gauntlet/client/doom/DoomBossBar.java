package com.dantemorrigan.gauntlet.client.doom;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.doom.DoctorDoom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Doctor Doom's boss bar from the Bossbar Pack (art/doctor_doom_bossbar): a 512 by 88 canvas shown at half size with a
 * recessed fill slot, a trail of recent damage, an animated effect layer for each of five states, a flash on every
 * hit, his mask and, while his shield is up, the crest of Latveria. The state follows the fight: sovereign (emerald
 * sparks), warded (the shield's runes), overdrive (amber lightning while he casts), critical (cracks under a fifth of
 * his health) and defeated (ashes, played once).
 */
public final class DoomBossBar {
	public static final int HEIGHT = 44;
	private static final String DIR = "textures/gui/doctor_doom/bossbar_pack/";
	private static final int W = 512, H = 88, SLOT_X = 63, SLOT_Y = 47, SLOT_W = 386, SLOT_H = 13;
	private static final String[] STATES = {"sovereign", "warded", "overdrive", "critical", "defeated"};
	private static final int SOVEREIGN = 0, WARDED = 1, OVERDRIVE = 2, CRITICAL = 3, DEFEATED = 4;

	private static float trail = 1, lastHealth = 1;
	private static long hitAt = -1, defeatedAt = -1;

	private DoomBossBar() {}

	private static Identifier tex(String name) { return InfinityGauntlet.id(DIR + name + ".png"); }

	private static DoctorDoom doom() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return null;
		for (Entity e : mc.level.entitiesForRendering()) if (e instanceof DoctorDoom d && d.isAlive() || e instanceof DoctorDoom) return (DoctorDoom) e;
		return null;
	}

	private static int stateOf(DoctorDoom d, float health) {
		if (d == null) return health <= 0 ? DEFEATED : SOVEREIGN;
		if (d.action() == DoctorDoom.A_DEFEAT || health <= 0 || d.isDeadOrDying()) return DEFEATED;
		if (d.flag(DoctorDoom.FLAG_SHIELD)) return WARDED;
		if (health < 0.2f) return CRITICAL;
		if (d.flag(DoctorDoom.FLAG_CASTING)) return OVERDRIVE;
		return SOVEREIGN;
	}

	private static void layer(GuiGraphicsExtractor g, Identifier id, int w, int h, int x, int y, int color) {
		g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0, 0, w, h, w, h, w, h, color);
	}

	public static void draw(GuiGraphicsExtractor g, int top, float health, Component name) {
		Minecraft mc = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		health = Mth.clamp(health, 0, 1);
		trail = health > trail ? health : Math.max(health, trail - 0.004f);
		if (health < lastHealth - 0.002f) hitAt = now;
		lastHealth = health;
		int state = stateOf(doom(), health);
		if (state == DEFEATED) { if (defeatedAt < 0) defeatedAt = now; } else defeatedAt = -1;
		// Half size, snapped so the pixels stay square on small windows.
		float scale = Math.max(0.25f, Math.min(0.5f, (float) Math.floor((g.guiWidth() - 12f) / W * 8) / 8f));
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate((g.guiWidth() - W * scale) / 2f, top);
		pose.scale(scale, scale);
		int white = -1;
		int full = Math.round(SLOT_W * health), lag = Math.round(SLOT_W * trail);
		String name2 = STATES[state];
		// The frame carries the dark track in its slot, so the trail and the fill go over it.
		layer(g, tex(state == DEFEATED ? "frame_defeated" : "frame"), W, H, 0, 0, white);
		if (lag > full) g.blit(RenderPipelines.GUI_TEXTURED, tex("damage_trail"), SLOT_X + full, SLOT_Y, full, 0, lag - full, SLOT_H, lag - full, SLOT_H, SLOT_W, SLOT_H, white);
		if (full > 0) g.blit(RenderPipelines.GUI_TEXTURED, tex("fill_" + name2), SLOT_X, SLOT_Y, 0, 0, full, SLOT_H, full, SLOT_H, SLOT_W, SLOT_H, white);
		// The state's effect layer: 90 ms a frame, looping; the defeat plays once and holds its last frame.
		int frame = state == DEFEATED ? (int) Math.min(7, (now - defeatedAt) / 90) : (int) (now / 90 % 8);
		layer(g, tex("effects/" + name2 + "_0" + frame), W, H, 0, 0, white);
		// The hit flash, 45 ms a frame, cut off at the current health.
		if (hitAt >= 0 && state != DEFEATED) {
			int f = (int) ((now - hitAt) / 45);
			if (f < 5) {
				int clip = Math.min(W, SLOT_X + full);
				if (clip > 0) g.blit(RenderPipelines.GUI_TEXTURED, tex("effects/hit_0" + f), 0, 0, 0, 0, clip, H, clip, H, W, H, white);
			} else hitAt = -1;
		}
		layer(g, tex(state == DEFEATED ? "doom_mask_defeated" : "doom_mask"), 39, 37, 29, 12, white);
		if (state == WARDED) layer(g, tex("latveria_crest"), 25, 25, 453, 15, white);
		pose.popMatrix();
		g.centeredText(mc.font, name, g.guiWidth() / 2, top + Math.round(21 * scale) - 4, ARGB.color(255, 207, 255, 220));
	}
}
