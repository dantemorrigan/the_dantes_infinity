package com.dantemorrigan.gauntlet.client.doom;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.doom.DoctorDoom;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The ascension of God Emperor Doom (after Secret Wars, 2015): when Doom beats the whole Infinity Gauntlet, the camera
 * leaves the player and circles him as he rises and turns to ivory and gold; another Earth fills the sky in the last
 * Incursion, reality cracks, everything goes white, and Battleworld is named. Purely visual: the server owns the
 * timing, Doom's look (his GOD flag) and what happens afterwards.
 */
public final class GodDoomScene {
	public static final int TURN = DoctorDoom.ASCEND_TURN;
	private static final Identifier EARTH = InfinityGauntlet.id("textures/fx/doom/incursion_earth.png");
	private static final Identifier CRACKS = InfinityGauntlet.id("textures/fx/doom/multiverse_cracks.png");
	private static boolean active;
	private static int age, length, doomId;
	private static float startYaw;


	private GodDoomScene() {}

	public static void start(int doom, int ticks) {
		active = true;
		age = 0;
		length = Math.max(60, ticks);
		doomId = doom;
		Minecraft mc = Minecraft.getInstance();
		startYaw = mc.player == null ? 0 : mc.player.getYRot();
		mc.getToastManager().clear();
	}

	public static void stop() { active = false; }

	/**
	 * A clean frame: every vanilla HUD layer (hotbar, bars, chat, boss bar...) stays silent while the scene plays, so
	 * only the letterbox, the subtitles and the scene's own overlay are on screen.
	 */
	public static void init() {
		for (Identifier id : new Identifier[]{VanillaHudElements.MISC_OVERLAYS, VanillaHudElements.CROSSHAIR, VanillaHudElements.HOTBAR,
				VanillaHudElements.ARMOR_BAR, VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR,
				VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP,
				VanillaHudElements.MOB_EFFECTS, VanillaHudElements.BOSS_BAR, VanillaHudElements.SCOREBOARD, VanillaHudElements.OVERLAY_MESSAGE,
				VanillaHudElements.TITLE_AND_SUBTITLE, VanillaHudElements.CHAT, VanillaHudElements.SUBTITLES}) {
			HudElementRegistry.replaceElement(id, old -> (g, delta) -> { if (!DoomClient.cinematic()) old.extractRenderState(g, delta); });
		}
	}

	public static boolean active() { return active; }

	public static int age() { return age; }

	public static void tick() {
		if (!active) return;
		if (++age > length) { stop(); return; }
		Minecraft.getInstance().getToastManager().clear();
	}

	private static Entity doom() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? null : mc.level.getEntity(doomId);
	}

	/** The scene's camera: {position, point looked at}, or null when the player's own view is kept. */
	public static Vec3[] shot(float partial) {
		if (!active || age < 24) return null;
		Entity d = doom();
		if (d == null) return null;
		float t = age + partial;
		Vec3 base = d.getPosition(partial);
		Vec3 head = base.add(0, 2.2, 0);
		double radius, height, angle;
		if (t < 170) {
			// Close, circling in front of the throne while he rises and turns.
			float k = (t - 24) / 146f;
			radius = 7.5;
			height = 2.4 + k * 1.0;
			angle = Math.sin(k * Math.PI * 0.9 - 0.9) * 1.1;
		} else if (t < 300) {
			// Pulling back and up: the whole hall, and the god above it.
			float k = Mth.clamp((t - 170) / 130f, 0, 1);
			k = k * k * (3 - 2 * k);
			radius = 7.5 + k * 6;
			height = 3.4 + k * 3.7;
			angle = 0.8 - k * 1.4;
		} else {
			// A low hero shot from in front, looking up as the sky breaks.
			float k = Mth.clamp((t - 300) / 60f, 0, 1);
			radius = 13.5 - k * 5;
			height = 7.1 - k * 7.4;
			angle = -0.6 + k * 0.6;
			head = head.add(0, 1.0 * k, 0);
		}
		Vec3 pos = base.add(Math.sin(angle) * radius, height, Math.cos(angle) * radius);
		// Stay inside the throne room.
		pos = new Vec3(Mth.clamp(pos.x, -19.5, 20.5), Mth.clamp(pos.y, base.y - 1.2, base.y + 12), Mth.clamp(pos.z, -292.5, -255));
		return new Vec3[]{pos, head};
	}

	/** How strongly Doom shines: nothing before the turn, a burst at it, a steady radiance after. */
	public static float radiance(float partial) {
		if (!active) return 0;
		float t = age + partial;
		if (t < TURN - 20) return 0;
		if (t < TURN) return (t - (TURN - 20)) / 20f * 0.6f;
		return Math.min(1, 0.6f + (t - TURN) / 40f * 0.4f);
	}

	public static Entity radiantDoom() { return active ? doom() : null; }

	/** The letterbox's title once he has turned. */
	public static boolean crowned() { return active && age >= TURN; }

	/** Under the letterbox: the other Earth, the red sky, the cracks. */
	public static void hudUnder(GuiGraphicsExtractor g) {
		if (!active) return;
		Minecraft mc = Minecraft.getInstance();
		int w = g.guiWidth(), h = g.guiHeight();
		float t = age + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		// The last Incursion: another Earth comes down out of the sky, its rim burning.
		if (t > 170 && t < 400) {
			float k = Mth.clamp((t - 170) / 210f, 0, 1);
			float size = h * (0.55f + 1.45f * k * k);
			float cy = -h * 0.3f + (h * 0.62f) * k;
			float alpha = Mth.clamp((t - 170) / 40f, 0, 1);
			image(g, EARTH, w / 2f - size / 2f, cy - size / 2f, size, size, 384, 384, ARGB.color((int) (alpha * 255), 255, 255, 255));
			if (t > 230) g.fill(0, 0, w, h, ARGB.color((int) (Mth.clamp((t - 230) / 150f, 0, 1) * 70), 255, 60, 30));
		}
		// Reality breaks.
		if (t > 260 && t < 400) {
			float k = Mth.clamp((t - 260) / 110f, 0, 1);
			float s = 1 + 0.25f * k;
			float cw = w * s, ch = h * s;
			image(g, CRACKS, (w - cw) / 2f, (h - ch) / 2f, cw, ch, 640, 360, ARGB.color((int) (k * 255), 255, 255, 255));
		}
	}

	/** Over everything: the flash of the turn, then the white and the naming of Battleworld. */
	public static void hud(GuiGraphicsExtractor g) {
		if (!active) return;
		Minecraft mc = Minecraft.getInstance();
		int w = g.guiWidth(), h = g.guiHeight();
		float t = age + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		if (t > TURN - 6 && t < TURN + 26) {
			float k = t < TURN ? (t - (TURN - 6)) / 6f : 1 - (t - TURN) / 26f;
			g.fill(0, 0, w, h, ARGB.color((int) (Mth.clamp(k, 0, 1) * 235), 255, 250, 235));
		}
		// White.
		if (t > 360) {
			float k = t < 400 ? (t - 360) / 40f : t > length - 20 ? Math.max(0, (length - t) / 20f) : 1;
			g.fill(0, 0, w, h, ARGB.color((int) (Mth.clamp(k, 0, 1) * 255), 252, 250, 244));
			if (t > 405 && t < length - 12) {
				float a = Mth.clamp(Math.min((t - 405) / 15f, (length - 12 - t) / 10f), 0, 1);
				flat(g, mc, Component.translatable("msg.infinity_gauntlet.latveria.god.ended"), w / 2, h / 2 - 34, ARGB.color((int) (a * 255), 70, 66, 60));
				if (t > 425) {
					float b = Mth.clamp(Math.min((t - 425) / 15f, (length - 12 - t) / 10f), 0, 1);
					var pose = g.pose();
					pose.pushMatrix();
					pose.translate(w / 2f, h / 2f - 8);
					pose.scale(3f, 3f);
					flat(g, mc, Component.translatable("msg.infinity_gauntlet.latveria.god.battleworld").withStyle(ChatFormatting.BOLD), 0, 0,
							ARGB.color((int) (b * 255), 196, 150, 50));
					pose.popMatrix();
					flat(g, mc, Component.translatable("msg.infinity_gauntlet.latveria.god.no_god"), w / 2, h / 2 + 26, ARGB.color((int) (b * 255), 120, 100, 70));
				}
			}
		}
	}

	/** Centred text without a shadow: on the white, shadows only smear the letters. */
	private static void flat(GuiGraphicsExtractor g, Minecraft mc, Component text, int x, int y, int color) {
		g.text(mc.font, text, x - mc.font.width(text) / 2, y, color, false);
	}

	private static void image(GuiGraphicsExtractor g, Identifier texture, float x, float y, float w, float h, int tw, int th, int color) {
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(w / tw, h / th);
		g.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0, 0, tw, th, tw, th, tw, th, color);
		pose.popMatrix();
	}
}
