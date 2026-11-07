package com.dantemorrigan.gauntlet.client.boss;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

public enum BossBarArt {
	THANOS("thanos"), THANOS_RAGE("thanos_rage"), LOKI("loki"), FENRIR("fenrir"), TRIBUNAL("tribunal");

	private static final int WIDTH = 320, HEIGHT = 54;
	private static final int FILL_X = 35, FILL_Y = 25, FILL_WIDTH = 250, FILL_HEIGHT = 9;
	public static final int ROW_HEIGHT = 54;

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
		if(this==TRIBUNAL){drawTribunal(g,top,health,alpha);return;}
		float scale = snap(.72f, g.guiWidth());
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

    /** Pixel art blurs at arbitrary ratios: snap the bar scale to 1/8 steps that still fit the screen. */
    private static float snap(float cap, int guiWidth) {
        float fit = Math.min(cap, (guiWidth - 12f) / WIDTH);
        float rounded = Math.round(fit * 8) / 8f;
        return rounded * WIDTH > guiWidth - 12f ? Math.max(.125f, (float) Math.floor(fit * 8) / 8f) : Math.max(.125f, rounded);
    }

    private static float tribunalTrail=1;
    private static long trailAt;
    private static void drawTribunal(GuiGraphicsExtractor g,int top,float hp,int alpha) {
        hp=Mth.clamp(hp,0,1);
        long now=System.nanoTime()/1_000_000;
        float dt=Math.min(100,Math.max(0,now-trailAt))/1000f;trailAt=now;
        tribunalTrail=hp>tribunalTrail?hp:Math.max(hp,tribunalTrail-dt*.08f);
        float scale=snap(.9f,g.guiWidth());
        var pose=g.pose();pose.pushMatrix();pose.translate((g.guiWidth()-WIDTH*scale)/2,top);pose.scale(scale);
        int gold=ARGB.color(alpha,224,175,76),light=ARGB.color(alpha,255,235,171);
        // Recessed astral track, stepped golden filigree and delayed amber damage trail.
        g.fill(23,23,297,40,ARGB.color(alpha,34,22,55));g.fill(27,25,293,38,gold);
        g.fill(29,27,291,36,ARGB.color(alpha,9,8,24));
        int end=30+Math.round(260*hp),trail=30+Math.round(260*tribunalTrail);
        if(trail>end)g.fill(end,28,trail,35,ARGB.color(alpha,178,111,57));
        for(int row=0;row<7;row++)g.fill(30,28+row,end,29+row,ARGB.color(alpha,105+row*12,62+row*12,163+row*10));
        if(end>30)g.fill(end-1,27,end+1,36,light);
        for(int i=1;i<12;i++){int x=30+i*260/12;g.fill(x,28,x+1,35,ARGB.color(alpha/2,241,211,143));}
        for(int side:new int[]{0,1}) {
            int x=side==0?18:302;
            diamond(g,x,31,10,gold);diamond(g,x,31,6,ARGB.color(alpha,67,41,110));diamond(g,x,31,2,light);
            g.fill(side==0?35:239,20,side==0?81:285,21,gold);
            g.fill(side==0?45:245,42,side==0?75:275,43,gold);
        }
        // Three golden faces, cowled in violet, above a luminous balance seal.
        for(int face=0;face<3;face++) {
            int x=160+(face-1)*15,y=face==1?3:8;
            diamond(g,x,y+8,face==1?12:9,ARGB.color(alpha,72,46,103));
            g.fill(x-4,y+3,x+5,y+14,gold);g.fill(x-3,y+2,x+4,y+3,light);
            g.fill(x-3,y+6,x-1,y+7,0xffe9faff);g.fill(x+2,y+6,x+4,y+7,0xffe9faff);
            g.fill(x,y+7,x+1,y+10,light);g.fill(x-2,y+11,x+3,y+12,0xff6d481f);
        }
        diamond(g,160,26,6,light);diamond(g,160,26,3,0xffc6efff);
        for(int i=0;i<17;i++) {
            int x=34+(i*47)%252,y=29+i%5;
            if(x<end)g.fill(x,y,x+1,y+1,ARGB.color(alpha,226,225,255));
        }
        pose.popMatrix();
    }
    private static void diamond(GuiGraphicsExtractor g,int x,int y,int radius,int color) {
        for(int row=-radius;row<=radius;row++) {int half=radius-Math.abs(row);g.fill(x-half,y+row,x+half+1,y+row+1,color);}
    }

	private void segment(GuiGraphicsExtractor g, int x, int width, int color) {
		g.blit(RenderPipelines.GUI_TEXTURED, fill, x, FILL_Y, x, FILL_Y,
				width, FILL_HEIGHT, width, FILL_HEIGHT, WIDTH, HEIGHT, color);
	}

	private static void layer(GuiGraphicsExtractor g, Identifier texture, int color) {
		g.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT, WIDTH, HEIGHT, color);
	}
}
