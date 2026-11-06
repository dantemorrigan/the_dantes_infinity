package com.dantemorrigan.gauntlet.client.quest;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.client.celestial.CelestialSky;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.dantemorrigan.gauntlet.stones.SpaceStoneQuest;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

/** Space Stone quest on the client: the asteroid's extragalactic sky, the frost vignette and the framed quest notice. */
public final class SpaceQuestClient {
	private static final Identifier STARS = InfinityGauntlet.id("textures/environment/quest_space_extragalactic_stars.png");
	private static final Identifier FROST = InfinityGauntlet.id("textures/gui/quest_space_frost.png");
	private static final Identifier FRAME = InfinityGauntlet.id("textures/gui/quest_space_alert.png");
	private static final Identifier SOUL_FRAME = InfinityGauntlet.id("textures/gui/quest_soul_alert.png");
	private static int style;
	private static final int ALERT_TICKS = 120;

	private static Component alert;
	private static int alertAge;
	private static float frost;

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.QuestAlert.TYPE, (packet, ctx) -> { alert = packet.text(); style = packet.style(); alertAge = 0; });
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (alert != null && ++alertAge > ALERT_TICKS) alert = null;
			float target = 0;
			if (onAsteroid() && mc.player != null) target = warm(mc.player) ? 0.35f : 1f;
			frost += (target - frost) * 0.08f;
		});
		HudElementRegistry.addLast(InfinityGauntlet.id("space_quest"), (g, delta) -> render(g));
	}

	public static boolean onAsteroid() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null && mc.level.dimension().equals(SpaceStoneQuest.FROST);
	}

	private static boolean warm(Player p) {
		for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
			if (!p.getItemBySlot(s).is(SpaceStoneQuest.COLD_ARMOR)) return false;
		return true;
	}

	private static void render(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui) return;
		int w = g.guiWidth(), h = g.guiHeight();
		if (frost > 0.01f) {
			int a = Mth.clamp((int) (frost * 255), 0, 255);
			g.blit(RenderPipelines.GUI_TEXTURED, FROST, 0, 0, 0, 0, w, h, 128, 128, 128, 128, ARGB.color(a, 255, 255, 255));
		}
		if (alert != null) {
			float in = Math.min(1f, alertAge / 8f), out = Math.min(1f, (ALERT_TICKS - alertAge) / 15f);
			int a = Mth.clamp((int) (Math.min(in, out) * 255), 4, 255);
			// Three-slice frame: the icon cap keeps its shape, the text never touches the icon or the right edge.
			int cap = 48, end = 16, pad = 12, fh = 48;
			int textW = mc.font.width(alert);
			int fw = Math.min(w - 16, Math.max(256, cap + pad + textW + pad + end));
			int x = (w - fw) / 2, y = h / 5, color = ARGB.color(a, 255, 255, 255);
            if(style==2){lokiAlert(g,x,y,fw,fh,a,leftTextCenter(x,cap,pad,fw,end));return;}
			g.blit(RenderPipelines.GUI_TEXTURED, style == 1 ? SOUL_FRAME : FRAME, x, y, 0, 0, cap, fh, 24, 24, 128, 24, color);
			g.blit(RenderPipelines.GUI_TEXTURED, style == 1 ? SOUL_FRAME : FRAME, x + cap, y, 24, 0, fw - cap - end, fh, 96, 24, 128, 24, color);
			g.blit(RenderPipelines.GUI_TEXTURED, style == 1 ? SOUL_FRAME : FRAME, x + fw - end, y, 120, 0, end, fh, 8, 24, 128, 24, color);
			int left = x + cap + pad, right = x + fw - end - pad;
			g.centeredText(mc.font, alert, (left + right) / 2, y + (fh - mc.font.lineHeight) / 2 + 1, style == 1 ? ARGB.color(a, 255, 214, 170) : ARGB.color(a, 210, 236, 255));
		}
	}

    private static int leftTextCenter(int x,int cap,int pad,int width,int end){return (x+cap+pad+x+width-end-pad)/2;}
    private static void lokiAlert(GuiGraphicsExtractor g,int x,int y,int w,int h,int alpha,int textCenter) {
        int tint=ARGB.color(alpha,183,255,208),gold=ARGB.color(alpha,239,203,104),jade=ARGB.color(alpha,68,219,138);
        g.blit(RenderPipelines.GUI_TEXTURED,SOUL_FRAME,x,y,0,0,48,h,24,24,128,24,tint);
        g.blit(RenderPipelines.GUI_TEXTURED,SOUL_FRAME,x+48,y,24,0,w-64,h,96,24,128,24,tint);
        g.blit(RenderPipelines.GUI_TEXTURED,SOUL_FRAME,x+w-16,y,120,0,16,h,8,24,128,24,tint);
        // Golden horned helm over an emerald seal; the original three-slice notice layout is retained.
        int cx=x+24,cy=y+24;
        g.fill(cx-12,cy-15,cx+13,cy+15,ARGB.color(alpha,7,12,16));
        g.fill(cx-7,cy-4,cx+8,cy+8,gold);g.fill(cx-5,cy+2,cx+6,cy+11,jade);
        for(int side:new int[]{-1,1}) {
            int hx=cx+side*8;
            g.fill(hx-2,cy-10,hx+2,cy+1,gold);
            g.fill(hx+side*2-1,cy-15,hx+side*2+2,cy-8,gold);
        }
        g.fill(cx-5,cy+3,cx-2,cy+5,ARGB.color(alpha,230,255,235));
        g.fill(cx+3,cy+3,cx+6,cy+5,ARGB.color(alpha,230,255,235));
        g.fill(cx-2,cy+8,cx+3,cy+9,gold);
        g.centeredText(Minecraft.getInstance().font,alert,textCenter,y+(h-Minecraft.getInstance().font.lineHeight)/2+1,ARGB.color(alpha,202,255,219));
    }

	/** Six faces of the star field, drawn inside the sky pass (dimension skybox "end"). */
	public static void drawSky() {
		float r = 100f;
		Vector3f[][] faces = {
				{v(-1, -1, -1), v(1, -1, -1), v(1, 1, -1), v(-1, 1, -1)},
				{v(1, -1, 1), v(-1, -1, 1), v(-1, 1, 1), v(1, 1, 1)},
				{v(-1, -1, 1), v(-1, -1, -1), v(-1, 1, -1), v(-1, 1, 1)},
				{v(1, -1, -1), v(1, -1, 1), v(1, 1, 1), v(1, 1, -1)},
				{v(-1, 1, -1), v(1, 1, -1), v(1, 1, 1), v(-1, 1, 1)},
				{v(-1, -1, 1), v(1, -1, 1), v(1, -1, -1), v(-1, -1, -1)}};
		PoseStack pose = new PoseStack();
		for (int f = 0; f < 6; f++) {
			Vector3f[] c = faces[f];
			float u0 = (f % 2) * 0.5f, u1 = u0 + 0.5f;
			float[][] uv = {{u0, 1}, {u1, 1}, {u1, 0}, {u0, 0}};
			CelestialSky.draw(pose, STARS, buf -> {
				for (int i = 0; i < 4; i++) buf.addVertex(c[i].x * r, c[i].y * r, c[i].z * r).setUv(uv[i][0], uv[i][1]).setColor(0xFFFFFFFF);
				for (int i = 3; i >= 0; i--) buf.addVertex(c[i].x * r, c[i].y * r, c[i].z * r).setUv(uv[i][0], uv[i][1]).setColor(0xFFFFFFFF);
			});
		}
	}

	private static Vector3f v(float x, float y, float z) { return new Vector3f(x, y, z); }

	private SpaceQuestClient() {}
}
