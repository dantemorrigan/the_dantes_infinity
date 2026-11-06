package com.dantemorrigan.gauntlet.client.boss;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

public enum BossBarArt {
	THANOS("thanos"), THANOS_RAGE("thanos_rage"), LOKI("loki"), FENRIR("fenrir");

	private static final int WIDTH = 320, HEIGHT = 54;
	private static final int FILL_X = 35, FILL_Y = 25, FILL_WIDTH = 250, FILL_HEIGHT = 9;
	public static final int ROW_HEIGHT = 46;

	private final Identifier track, fill, tip, frame, crest;

	BossBarArt(String variant) {
		String path = "textures/gui/boss/" + variant + "/";
		track = InfinityGauntlet.id(path + "track.png");
		fill = InfinityGauntlet.id(path + "fill.png");
		tip = InfinityGauntlet.id(path + "fill_tip.png");
		frame = InfinityGauntlet.id(path + "frame.png");
		crest = InfinityGauntlet.id(path + "crest.png");
	}

	public void draw(GuiGraphicsExtractor g, int top, float health, float damageTrail, int alpha) {
		float scale = Math.min(.72f, (g.guiWidth() - 12f) / WIDTH);
		int full = Math.round(FILL_WIDTH * Mth.clamp(health, 0, 1));
		int lag = Math.round(FILL_WIDTH * Mth.clamp(damageTrail, 0, 1));
		int color = ARGB.color(alpha, 255, 255, 255);
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate((g.guiWidth() - WIDTH * scale) / 2, top);
		pose.scale(scale);
		layer(g, track, color);
		if (lag > full) segment(g, FILL_X + full, lag - full, ARGB.color(alpha / 2, 255, 210, 165));
		if (full > 0) {
			segment(g, FILL_X, full, color);
			if (full < FILL_WIDTH) g.blit(RenderPipelines.GUI_TEXTURED, tip, FILL_X + full - 1, FILL_Y,
					0, 0, 2, FILL_HEIGHT, 2, FILL_HEIGHT, 2, FILL_HEIGHT, color);
		}
		layer(g, frame, color);
		layer(g, crest, color);
		pose.popMatrix();
	}

	private void segment(GuiGraphicsExtractor g, int x, int width, int color) {
		g.blit(RenderPipelines.GUI_TEXTURED, fill, x, FILL_Y, x, FILL_Y,
				width, FILL_HEIGHT, width, FILL_HEIGHT, WIDTH, HEIGHT, color);
	}

	private static void layer(GuiGraphicsExtractor g, Identifier texture, int color) {
		g.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT, WIDTH, HEIGHT, color);
	}
}
