package com.dantemorrigan.gauntlet.client.doom;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ModSounds;
import com.dantemorrigan.gauntlet.client.MusicDuck;
import com.dantemorrigan.gauntlet.client.cutscene.Cutscene;
import com.dantemorrigan.gauntlet.client.fx.ClientPower;
import com.dantemorrigan.gauntlet.doom.DoctorDoom;
import com.dantemorrigan.gauntlet.doom.DoomArmor;
import com.dantemorrigan.gauntlet.doom.DoomFx;
import com.dantemorrigan.gauntlet.doom.Latveria;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

/**
 * Client side of The Fury of Doom: the title card on the first arrival, Latveria's two alternating themes and the
 * boss theme, the letterbox of Doom's entrance, the boss bar, and the armor's wheel and cooldown strip.
 */
public final class DoomClient {
	public static final KeyMapping WHEEL = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.infinity_gauntlet.doom_wheel",
			InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, ClientPower.CATEGORY));
	public static final String[] ICONS = {"gauntlet_bolt", "iron_bastion", "jet_slam", "rift_step", "seven_suns", "latverian_edict"};
	private static final Identifier LOGO = InfinityGauntlet.id("textures/gui/doctor_doom/logo.png");
	private static final String GUI = "textures/gui/doctor_doom/";

	// Armor state from the server.
	private static boolean set;
	private static final int[] cooldown = new int[6], cooldownMax = {1, 1, 1, 1, 1, 1};
	private static int jet, bastion;
	// One-off scenes.
	private static int logoAge = -1, logoTicks, barsAge = -1, barsTicks, flashAge = -1, flashTicks;
	private static float flashStrength;
	// Music.
	private static Track track;
	private static int silence, nextAmbient, clock;
	private static float barTrail = 1;
	private static String lastTheme = "none";

	public static boolean fullSet() { return set; }

	public static int cooldown(int ability) { return cooldown[ability]; }

	public static boolean logoShowing() { return logoAge >= 0; }

	public static boolean bossMusic() { return track != null && track.boss && !track.isStopped(); }

	/** Name of the theme the client last started: "boss", "echoes_of_latveria", "sovereign_sanctum" or "none". */
	public static String lastTheme() { return lastTheme; }

	public static String musicState() {
		return track == null || track.isStopped() ? "silent" : (track.boss ? "boss" : track.name) + (track.out ? " (fading)" : "");
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.DoomFx.TYPE, (fx, ctx) -> {
			switch (fx.kind()) {
				case DoomFx.LOGO -> { logoAge = 0; logoTicks = Math.max(60, fx.ticks()); }
				case DoomFx.CUTSCENE -> { barsAge = 0; barsTicks = Math.max(20, fx.ticks()); }
				case DoomFx.SHAKE -> Cutscene.shake(Math.min(1f, fx.f() * 0.5f), fx.ticks());
				case DoomFx.FLASH -> { flashAge = 0; flashTicks = Math.max(4, fx.ticks()); flashStrength = fx.f(); }
				case DoomFx.MUSIC -> force(Minecraft.getInstance(), fx.id());
				default -> {}
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.DoomSync.TYPE, (sync, ctx) -> {
			String[] parts = sync.state().split(";");
			if (parts.length < 4) return;
			set = parts[0].equals("1");
			jet = Integer.parseInt(parts[1]);
			bastion = Integer.parseInt(parts[2]);
			String[] cds = parts[3].split(",");
			for (int i = 0; i < 6 && i < cds.length; i++) {
				String[] c = cds[i].split("/");
				cooldown[i] = Integer.parseInt(c[0]);
				cooldownMax[i] = Math.max(1, Integer.parseInt(c[1]));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> reset());
		ClientTickEvents.END_CLIENT_TICK.register(DoomClient::tick);
		HudElementRegistry.addLast(InfinityGauntlet.id("doom_hud"), (g, delta) -> hud(g));
	}

	private static void reset() {
		set = false;
		logoAge = barsAge = flashAge = -1;
		if (track != null) track.halt();
		track = null;
		silence = 0;
	}

	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			if (track != null) reset();
			return;
		}
		if (mc.isPaused()) return;
		clock++;
		for (int i = 0; i < 6; i++) if (cooldown[i] > 0) cooldown[i]--;
		if (jet > 0) jet--;
		if (bastion > 0) bastion--;
		if (logoAge >= 0 && ++logoAge > logoTicks) logoAge = -1;
		if (barsAge >= 0 && ++barsAge > barsTicks) barsAge = -1;
		if (flashAge >= 0 && ++flashAge > flashTicks) flashAge = -1;
		while (WHEEL.consumeClick()) {
			if (set && mc.screen == null) mc.setScreen(new DoomWheelScreen());
		}
		music(mc);
	}

	// ------------------------------------------------------------------ music

	private static void music(Minecraft mc) {
		boolean inLatveria = mc.level.dimension().equals(Latveria.LEVEL);
		boolean fight = false;
		if (inLatveria) {
			for (Entity e : mc.level.entitiesForRendering()) {
				if (e instanceof DoctorDoom d && d.isAlive() && d.action() != DoctorDoom.A_DEFEAT && d.distanceToSqr(mc.player) < 80 * 80) { fight = true; break; }
			}
		}
		if (track != null && (track.isStopped() || clock - track.created > 40 && !mc.getSoundManager().isActive(track))) {
			if (!track.boss) silence = 200;
			track = null;
		}
		if (track != null) {
			if (track.pinned) return;
			boolean wrong = !inLatveria || track.boss != fight || !mc.player.isAlive() && track.boss;
			if (wrong) track.fadeOut(track.boss ? 60 : 40);
			return;
		}
		if (!inLatveria) return;
		if (fight) {
			play(mc, new Track(ModSounds.DOOM_BOSS_THEME, "boss", true, 0.5f, true));
			return;
		}
		if (silence > 0) { silence--; return; }
		boolean echoes = nextAmbient++ % 2 == 0;
		play(mc, new Track(echoes ? ModSounds.LATVERIA_ECHOES : ModSounds.LATVERIA_SANCTUM, echoes ? "echoes_of_latveria" : "sovereign_sanctum", false, 0.38f, false));
	}

	/** Debug: a theme chosen by command plays until it ends or another is chosen; 0 stops the music for a minute. */
	private static void force(Minecraft mc, int id) {
		if (track != null) track.halt();
		track = null;
		if (id == 0) { silence = 1200; return; }
		Track t = id == 3 ? new Track(ModSounds.DOOM_BOSS_THEME, "boss", true, 0.5f, true)
				: new Track(id == 1 ? ModSounds.LATVERIA_ECHOES : ModSounds.LATVERIA_SANCTUM, id == 1 ? "echoes_of_latveria" : "sovereign_sanctum", false, 0.38f, false);
		t.pinned = true;
		play(mc, t);
	}

	private static void play(Minecraft mc, Track next) {
		track = next;
		lastTheme = next.name;
		next.created = clock;
		mc.getSoundManager().play(next);
	}

	/** A streamed theme that fades in, keeps vanilla music down while it plays and fades out when asked. */
	private static final class Track extends AbstractTickableSoundInstance {
		final String name;
		final boolean boss;
		final float level;
		float step;
		boolean out, pinned;
		/** Client tick when it was started: the sound engine ticks it only once it really plays. */
		int created;

		Track(SoundEvent sound, String name, boolean boss, float level, boolean loop) {
			super(sound, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
			this.name = name;
			this.boss = boss;
			this.level = level;
			this.step = level / 50f;
			this.looping = loop;
			this.delay = 0;
			this.volume = 0.001f;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
		}

		void fadeOut(int ticks) {
			if (out) return;
			out = true;
			step = Math.max(0.001f, this.volume / ticks);
		}

		void halt() { this.stop(); }

		@Override
		public void tick() {
			if (!out) MusicDuck.pulse();
			if (out) {
				this.volume -= step;
				if (this.volume <= 0.001f) this.stop();
			} else if (this.volume < level) this.volume = Math.min(level, this.volume + step);
		}

		@Override
		public boolean canStartSilent() { return true; }
	}

	// ------------------------------------------------------------------ drawing

	private static void image(GuiGraphicsExtractor g, Identifier texture, float x, float y, float w, float h, int tw, int th, int color) {
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(w / tw, h / th);
		g.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0, 0, tw, th, tw, th, tw, th, color);
		pose.popMatrix();
	}

	private static void hud(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		int w = g.guiWidth(), h = g.guiHeight();
		if (flashAge >= 0) {
			float a = flashStrength * (1 - flashAge / (float) flashTicks) * com.dantemorrigan.gauntlet.GauntletConfig.get().flashIntensity;
			g.fill(0, 0, w, h, ARGB.color((int) (Mth.clamp(a, 0, 1) * 96), 190, 255, 205));
		}
		if (barsAge >= 0) {
			float k = Mth.clamp(Math.min(barsAge / 12f, (barsTicks - barsAge) / 12f), 0, 1);
			int bar = (int) (h * 0.13f * k);
			g.fill(0, 0, w, bar, 0xFF000000);
			g.fill(0, h - bar, w, h, 0xFF000000);
			if (barsAge > 30 && barsTicks - barsAge > 14) {
				int a = (int) (255 * Mth.clamp((barsAge - 30) / 15f, 0, 1));
				if (a > 8) {
					g.centeredText(mc.font, Component.translatable("entity.infinity_gauntlet.doctor_doom").withStyle(net.minecraft.ChatFormatting.BOLD), w / 2, h - bar - 26, ARGB.color(a, 120, 255, 150));
					g.centeredText(mc.font, Component.translatable("boss.infinity_gauntlet.doom.title"), w / 2, h - bar - 14, ARGB.color(a, 232, 214, 150));
				}
			}
		}
		if (logoAge >= 0) {
			// Darken the world, bring the logo up, hold, let both go.
			float dark = Mth.clamp(Math.min(logoAge / 20f, (logoTicks - logoAge) / 30f), 0, 1);
			float show = Mth.clamp(Math.min((logoAge - 18) / 34f, (logoTicks - logoAge - 6) / 30f), 0, 1);
			show = show * show * (3 - 2 * show);
			g.fill(0, 0, w, h, ARGB.color((int) (dark * 210), 2, 8, 5));
			float lw = Math.min(w * 0.78f, h * 0.9f * 2f) * (0.94f + 0.06f * show), lh = lw / 2f;
			if (show > 0.01f) image(g, LOGO, (w - lw) / 2f, (h - lh) / 2f - 6, lw, lh, 1024, 512, ARGB.color((int) (show * 255), 255, 255, 255));
			return;
		}
		if (!set || mc.options.hideGui || mc.screen instanceof DoomWheelScreen) return;
		// The armor's six powers down the right edge: lit when ready, dim with seconds left while cooling.
		int size = 18, x = w - size - 4, y0 = h / 2 - 3 * (size + 3);
		for (int i = 0; i < 6; i++) {
			int y = y0 + i * (size + 3);
			boolean active = i == DoomArmor.JET && jet > 0 || i == DoomArmor.BASTION && bastion > 0;
			boolean ready = cooldown[i] <= 0;
			image(g, InfinityGauntlet.id(GUI + (active ? "state_warning" : ready ? "state_active" : "state_cooldown") + ".png"), x - 3, y - 3, size + 6, size + 6, 64, 64, -1);
			image(g, InfinityGauntlet.id(GUI + "icons/" + ICONS[i] + ".png"), x, y, size, size, 64, 64, ready || active ? -1 : ARGB.color(255, 96, 104, 100));
			if (!ready) g.centeredText(mc.font, Component.literal(String.valueOf((cooldown[i] + 19) / 20)), x + size / 2, y + 5, 0xFFFFE08A);
		}
	}

	/** Doom's boss bar from the pack: track, a fill for the current stage, frame and seal. */
	public static void bossBar(GuiGraphicsExtractor g, int top, float health) {
		float scale = Math.max(0.5f, Math.min(1f, (g.guiWidth() - 12f) / 256f));
		barTrail = health > barTrail ? health : Math.max(health, barTrail - 0.004f);
		String fill = health > 0.66f ? "bossbar_tech" : health > 0.33f ? "bossbar_magic" : "bossbar_edict";
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate((g.guiWidth() - 256 * scale) / 2f, top);
		pose.scale(scale, scale);
		g.blit(RenderPipelines.GUI_TEXTURED, InfinityGauntlet.id(GUI + "bossbar_track.png"), 0, 0, 0, 0, 256, 32, 256, 32, 256, 32, -1);
		int full = 4 + Math.round(248 * Mth.clamp(health, 0, 1)), lag = 4 + Math.round(248 * Mth.clamp(barTrail, 0, 1));
		if (lag > full) g.blit(RenderPipelines.GUI_TEXTURED, InfinityGauntlet.id(GUI + fill + ".png"), full, 0, full, 0, lag - full, 32, lag - full, 32, 256, 32, ARGB.color(120, 255, 240, 200));
		if (full > 4) g.blit(RenderPipelines.GUI_TEXTURED, InfinityGauntlet.id(GUI + fill + ".png"), 0, 0, 0, 0, full, 32, full, 32, 256, 32, -1);
		g.blit(RenderPipelines.GUI_TEXTURED, InfinityGauntlet.id(GUI + "bossbar_frame.png"), 0, 0, 0, 0, 256, 32, 256, 32, 256, 32, -1);
		pose.popMatrix();
		image(g, InfinityGauntlet.id(GUI + "bossbar_seal.png"), (g.guiWidth() - 256 * scale) / 2f - 20 * scale, top - 2 * scale, 36 * scale, 36 * scale, 64, 64, -1);
	}

	private DoomClient() {}
}
