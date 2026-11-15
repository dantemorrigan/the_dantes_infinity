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
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Client side of The Fury of Doom: the title card on the first arrival, Latveria's two alternating themes and the
 * boss theme, the letterbox of Doom's entrance, the boss bar, and the armor's wheel and cooldown strip.
 */
public final class DoomClient {
	public static final KeyMapping WHEEL = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.infinity_gauntlet.doom_wheel",
			InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, ClientPower.CATEGORY));
	/** Casts the selected power again without opening the wheel. */
	public static final KeyMapping CAST = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.infinity_gauntlet.doom_cast",
			InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, ClientPower.CATEGORY));
	public static final String[] ICONS = {"gauntlet_bolt", "iron_bastion", "jet_slam", "rift_step", "seven_suns", "latverian_edict"};
	private static final Identifier LOGO = InfinityGauntlet.id("textures/gui/doctor_doom/logo.png");
	private static final String GUI = "textures/gui/doctor_doom/";

	// Armor state from the server.
	private static boolean set, jet;
	private static final int[] cooldown = new int[6], cooldownMax = {1, 1, 1, 1, 1, 1};
	private static int fuel = DoomArmor.FUEL, bastion, suns, selected, holdTicks;
	// One-off scenes.
	private static boolean welcomeDue;
	private static int logoAge = -1, logoTicks, barsAge = -1, barsTicks, flashAge = -1, flashTicks;
	/** Doom's last spoken line and its age in ticks: during the entrance it is shown as a subtitle inside the letterbox. */
	private static Component subtitle;

	/** A letterboxed scene is playing (Doom's entrance, the ascension, or the final gauntlet storyline): the rest of the HUD steps aside. */
	public static boolean cinematic() { return barsAge >= 0 || GodDoomScene.active() || DoomFinalClient.inCutscene(); }
	private static int subtitleAge;
	private static float flashStrength;
	// Music.
	private static Track track;
	private static int silence, nextAmbient, clock;
	private static float barTrail = 1;
	private static String lastTheme = "none";

	public static boolean fullSet() { return set; }

	public static int cooldown(int ability) { return cooldown[ability]; }

	public static int selected() { return selected; }

	public static boolean jetActive() { return jet; }

	/** Picks a power. Casting is a separate press of the cast key, like with the Infinity Gauntlet. */
	public static void select(int ability) {
		selected = Mth.clamp(ability, 0, 5);
	}

	/** Casts the selected power. Seven Suns cast again while they orbit looses them at the crosshair. */
	public static void cast() {
		if (!set || !ClientPlayNetworking.canSend(ModNetwork.DoomCast.TYPE)) return;
		ClientPlayNetworking.send(new ModNetwork.DoomCast(selected));
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && selected != DoomArmor.JET && (cooldown[selected] <= 0 || selected == DoomArmor.SUNS && suns > 0)) mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
	}

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
				case DoomFx.LOGO -> { logoAge = 0; logoTicks = Math.max(60, fx.ticks()); if (fx.id() == 1) welcomeDue = true; }
				case DoomFx.CUTSCENE -> { barsAge = 0; barsTicks = Math.max(20, fx.ticks()); }
				case DoomFx.SHAKE -> Cutscene.shake(Math.min(1f, fx.f() * 0.5f), fx.ticks());
				case DoomFx.FLASH -> { flashAge = 0; flashTicks = Math.max(4, fx.ticks()); flashStrength = fx.f(); }
				case DoomFx.MUSIC -> force(Minecraft.getInstance(), fx.id());
				case DoomFx.LIGHTS -> DoomVfxClient.lights(fx.f() > 0.5f);
				case DoomFx.GOD -> GodDoomScene.start(fx.id(), fx.ticks());
				case DoomFx.STEALTH -> {
					stealthState = fx.id(); stealthMeter = fx.f(); stealthLeft = fx.ticks(); stealthAt = System.currentTimeMillis();
					// The hot zone the city's lamps circle (none when the point is zero); the server's copy, in one game, agrees.
					var mc = Minecraft.getInstance();
					if (mc.level != null) {
						boolean on = fx.at().lengthSqr() > 0;
						com.dantemorrigan.gauntlet.doom.Searchlights.focus(on, fx.at().x, fx.at().z, mc.level.getGameTime());
					}
				}
				case DoomFx.ALARM -> { DoomVfxClient.alarm(fx.id(), fx.ticks()); flashAge = 0; flashTicks = 8; flashStrength = 0.25f; }
				default -> {}
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.DoomSync.TYPE, (sync, ctx) -> {
			String[] parts = sync.state().split(";");
			if (parts.length < 6) return;
			set = parts[0].equals("1");
			jet = parts[1].equals("1");
			fuel = Integer.parseInt(parts[2]);
			bastion = Integer.parseInt(parts[3]);
			suns = Integer.parseInt(parts[4]);
			String[] cds = parts[5].split(",");
			for (int i = 0; i < 6 && i < cds.length; i++) {
				String[] c = cds[i].split("/");
				cooldown[i] = Integer.parseInt(c[0]);
				cooldownMax[i] = Math.max(1, Integer.parseInt(c[1]));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> reset());
		ClientTickEvents.END_CLIENT_TICK.register(DoomClient::tick);
		net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay && message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
					&& tc.getKey().startsWith("msg.infinity_gauntlet.latveria.doom.")) {
				subtitle = message;
				subtitleAge = 0;
			}
		});
		HudElementRegistry.addFirst(InfinityGauntlet.id("latveria_filter"), (g, delta) -> filter(g));
		HudElementRegistry.addLast(InfinityGauntlet.id("doom_hud"), (g, delta) -> hud(g));
		HudElementRegistry.addLast(InfinityGauntlet.id("latveria_stealth"), (g, delta) -> stealth(g));
		ClientPlayNetworking.registerGlobalReceiver(ModNetwork.LatveriaDialogOpen.TYPE, (pkt, ctx) -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.screen instanceof LatveriaDialogScreen existing && existing.matches(pkt.entityId(), pkt.role())) {
				existing.updateState(pkt.flags());
			} else {
				mc.setScreen(new LatveriaDialogScreen(pkt.entityId(), pkt.role(), pkt.flags()));
			}
		});
		GodDoomScene.init();
		DoomFinalClient.init();
		DoomTooltips.init();
	}

	private static void reset() {
		set = false;
		logoAge = barsAge = flashAge = -1;
				subtitle = null;
				GodDoomScene.stop();
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
		if (bastion > 0) bastion--;
		if (jet && fuel > 0) fuel--;
		if (logoAge >= 0 && ++logoAge > logoTicks) logoAge = -1;
		// The first arrival: after the title card, the welcome to Latveria.
		if (welcomeDue && logoAge < 0 && mc.screen == null && mc.player != null && !Boolean.getBoolean("infinity_gauntlet.noWelcome")) {
			welcomeDue = false;
			mc.setScreen(new LatveriaWelcomeScreen());
		}
		if (barsAge >= 0 && ++barsAge > barsTicks) barsAge = -1;
		if (subtitle != null && ++subtitleAge > 90) subtitle = null;
		GodDoomScene.tick();
		if (flashAge >= 0 && ++flashAge > flashTicks) flashAge = -1;
		while (WHEEL.consumeClick()) {
			if (set && mc.screen == null) mc.setScreen(new DoomWheelScreen());
		}
		boolean pressed = false;
		while (CAST.consumeClick()) if (mc.screen == null) { cast(); pressed = true; holdTicks = 0; }
		// Holding the cast key keeps a bolt or a sun coming.
		if (!pressed && CAST.isDown() && mc.screen == null && (selected == DoomArmor.BOLT || selected == DoomArmor.SUNS && suns > 0)) {
			if (++holdTicks >= 8) { cast(); holdTicks = 5; }
		} else if (!CAST.isDown()) holdTicks = 0;
		if (!set) jet = false;
		fly(mc);
		edictPreview(mc);
		music(mc);
	}

	// ------------------------------------------------------------------ jets and aiming

	/**
	 * Jet Flight runs on the client, so it answers like walking does: W thrusts along the view (sprint is faster),
	 * S brakes, A and D slide, Space climbs, Shift sinks; with no key held the wearer hovers. The server only keeps
	 * the fuel and the fall damage.
	 */
	private static void fly(Minecraft mc) {
		var p = mc.player;
		if (!jet || p == null || !p.isAlive() || p.getAbilities().flying || p.isPassenger()) return;
		var keys = p.input.keyPresses;
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
		Vec3 side = new Vec3(-flat.z, 0, flat.x);
		double speed = keys.sprint() ? 1.35 : 0.85;
		Vec3 want = Vec3.ZERO;
		if (keys.forward()) want = want.add(look.scale(speed));
		if (keys.backward()) want = want.subtract(flat.scale(speed * 0.45));
		if (keys.right()) want = want.add(side.scale(speed * 0.55));
		if (keys.left()) want = want.subtract(side.scale(speed * 0.55));
		if (keys.jump()) want = want.add(0, 0.55, 0);
		if (keys.shift()) want = want.add(0, -0.55, 0);
		Vec3 next = p.getDeltaMovement().lerp(want, 0.2);
		// Air drag and gravity act on this before the next move; give them back so the jets hold the line.
		p.setDeltaMovement(next.x / 0.91, next.y / 0.98 + 0.08, next.z / 0.91);
		p.fallDistance = 0;
		if (fuel <= 0 && ClientPlayNetworking.canSend(ModNetwork.DoomJet.TYPE)) ClientPlayNetworking.send(new ModNetwork.DoomJet(false));
	}

	/** With the Edict or the Rift Step selected, the ground shows where it would land: aim first, then cast. */
	private static void edictPreview(Minecraft mc) {
		boolean edict = selected == DoomArmor.EDICT, rift = selected == DoomArmor.RIFT;
		if (!set || !(edict || rift) || mc.player == null || mc.screen != null || cooldown[selected] > 0) {
			DoomVfxClient.preview(null, 0, 0);
			return;
		}
		double reach = edict ? DoomArmor.EDICT_RANGE : DoomArmor.RIFT_RANGE;
		var hit = mc.player.pick(reach, 1f, false);
		Vec3 at = hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? hit.getLocation()
				: mc.player.getEyePosition().add(mc.player.getLookAngle().scale(reach));
		if (rift) at = at.subtract(mc.player.getLookAngle().scale(hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? 0.8 : 0));
		var down = mc.level.clip(new net.minecraft.world.level.ClipContext(at.add(0, 0.5, 0), at.add(0, -24, 0),
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
		DoomVfxClient.preview(down.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? null : down.getLocation(),
				edict ? (float) DoomArmor.EDICT_RADIUS : 0.9f, edict ? 1 : 2);
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

	/**
	 * The dimension's screen filter: a cold teal grade, dark vignette corners that breathe a little, and a green
	 * flush when lightning strikes. Under the HUD, so the hotbar and the boss bar stay clean.
	 */
	private static void filter(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || !LatveriaSky.active()) return;
		int w = g.guiWidth(), h = g.guiHeight();
		float time = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float flash = LatveriaSky.flash(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
		g.fill(0, 0, w, h, ARGB.color(26, 12, 64, 52));
		float breathe = 0.92f + 0.08f * Mth.sin(time * 0.02f);
		image(g, InfinityGauntlet.id(GUI + "vignette.png"), 0, 0, w, h, 256, 256, ARGB.color((int) (255 * breathe * (1 - flash * 0.5f)), 255, 255, 255));
		if (flash > 0.05f) g.fill(0, 0, w, h, ARGB.color((int) (flash * 46), 170, 255, 205));
	}

	private static void hud(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		int w = g.guiWidth(), h = g.guiHeight();
		if (flashAge >= 0) {
			float a = flashStrength * (1 - flashAge / (float) flashTicks) * com.dantemorrigan.gauntlet.GauntletConfig.get().flashIntensity;
			g.fill(0, 0, w, h, ARGB.color((int) (Mth.clamp(a, 0, 1) * 96), 190, 255, 205));
		}
		GodDoomScene.hudUnder(g);
		if (barsAge >= 0) {
			float k = Mth.clamp(Math.min(barsAge / 12f, (barsTicks - barsAge) / 12f), 0, 1);
			int bar = (int) (h * 0.13f * k);
			g.fill(0, 0, w, bar, 0xFF000000);
			g.fill(0, h - bar, w, h, 0xFF000000);
			// Name and title in the top bar; Doom's words as subtitles in the bottom one, where the bars would hide the chat.
			if (barsAge > 30 && barsTicks - barsAge > 14 && bar >= 22) {
				int a = (int) (255 * Mth.clamp((barsAge - 30) / 15f, 0, 1));
				if (a > 8) {
					boolean god = GodDoomScene.crowned();
					g.centeredText(mc.font, Component.translatable(god ? "boss.infinity_gauntlet.doom.god_name" : "entity.infinity_gauntlet.doctor_doom").withStyle(net.minecraft.ChatFormatting.BOLD),
							w / 2, bar / 2 - 10, god ? ARGB.color(a, 255, 236, 180) : ARGB.color(a, 120, 255, 150));
					g.centeredText(mc.font, Component.translatable(god ? "boss.infinity_gauntlet.doom.god_title" : "boss.infinity_gauntlet.doom.title"), w / 2, bar / 2 + 1, ARGB.color(a, 232, 214, 150));
				}
			}
			if (subtitle != null && bar >= 12) {
				int a = (int) (255 * Mth.clamp(Math.min(subtitleAge / 6f, (90 - subtitleAge) / 10f), 0, 1));
				var lines = mc.font.split(subtitle, (int) (w * 0.8f));
				int n = Math.min(lines.size(), Math.max(1, (bar - 4) / 10));
				int y = h - bar / 2 - n * 10 / 2 + 1;
				for (int i = 0; i < n; i++) g.centeredText(mc.font, lines.get(i), w / 2, y + i * 10, ARGB.color(Math.max(a, 9), 255, 255, 255));
			}
		}
		GodDoomScene.hud(g);
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
		if (!set || mc.options.hideGui || mc.screen instanceof DoomWheelScreen || cinematic()) return;
		// The armor's six powers down the right edge: the selected one framed in gold, lit when ready, dim with
		// seconds left while cooling; the jets show their fuel, the suns how many still orbit.
		int size = 18, x = w - size - 6, y0 = h / 2 - 3 * (size + 4);
		for (int i = 0; i < 6; i++) {
			int y = y0 + i * (size + 4);
			boolean active = i == DoomArmor.JET && jet || i == DoomArmor.BASTION && bastion > 0 || i == DoomArmor.SUNS && suns > 0;
			boolean ready = cooldown[i] <= 0 || active;
			if (i == selected) g.fill(x - 5, y - 5, x + size + 5, y + size + 5, 0x66FFD36B);
			image(g, InfinityGauntlet.id(GUI + (active ? "state_warning" : ready ? "state_active" : "state_cooldown") + ".png"), x - 3, y - 3, size + 6, size + 6, 64, 64, -1);
			image(g, InfinityGauntlet.id(GUI + "icons/" + ICONS[i] + ".png"), x, y, size, size, 64, 64, ready ? -1 : ARGB.color(255, 96, 104, 100));
			if (!ready) g.centeredText(mc.font, Component.literal(String.valueOf((cooldown[i] + 19) / 20)), x + size / 2, y + 5, 0xFFFFE08A);
			if (i == DoomArmor.JET) {
				int bar = Math.round(size * fuel / (float) DoomArmor.FUEL);
				g.fill(x, y + size + 1, x + size, y + size + 3, 0xAA101010);
				g.fill(x, y + size + 1, x + bar, y + size + 3, jet ? 0xFFFFB347 : 0xFF6CE89A);
			}
			if (i == DoomArmor.SUNS && suns > 0) g.text(mc.font, Component.literal(String.valueOf(suns)), x - 8, y + 5, 0xFFFFE9A8, true);
		}
		Component name = Component.translatable("doom.infinity_gauntlet.power." + DoomArmor.NAMES[selected]);
		Component hint = Component.translatable("doom.infinity_gauntlet.cast_hint", CAST.getTranslatedKeyMessage());
		g.text(mc.font, name, w - 6 - mc.font.width(name), y0 + 6 * (size + 4) + 2, 0xFFFFE08A, true);
		g.text(mc.font, hint, w - 6 - mc.font.width(hint), y0 + 6 * (size + 4) + 13, 0xFFCFE8D8, true);
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

	// ------------------------------------------------------------------ the hunt: detection eye and where the robots are

	private static int stealthState, stealthLeft;
	private static float stealthMeter, shownMeter;
	private static long stealthAt;

	/**
	 * The detection eye at the top of the screen: shut while nobody suspects you, opening yellow as the meter fills, red and
	 * wide when you are found; under it what the hunt is doing (and how long the search has left). Around the crosshair,
	 * a marker toward every robot that senses you, so you know where to break line of sight.
	 */
	private static void stealth(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || !LatveriaSky.active() || cinematic()) return;
		boolean fresh = System.currentTimeMillis() - stealthAt < 1500;
		if (!fresh) { stealthMeter = 0; stealthState = 0; }
		shownMeter += (stealthMeter - shownMeter) * 0.2f;
		boolean lamps = DoomVfxClient.lightsOn();
		if (shownMeter < 0.01f && stealthState == 0 && !lamps) return;
		int cx = g.guiWidth() / 2, top = 6 + (mc.gui.getBossOverlay() != null && hasBossBar(mc) ? 50 : 0);
		boolean found = stealthState == com.dantemorrigan.gauntlet.doom.LatveriaHunt.CHASING || stealthState == com.dantemorrigan.gauntlet.doom.LatveriaHunt.SIEGE;
		int color = found ? 0xFFE8322A : shownMeter > 0.5f ? 0xFFFFB02E : shownMeter > 0.02f ? 0xFFF3E37A : 0xFFB8C8C0;
		float open = found ? 1 : Mth.clamp(shownMeter, 0, 1);
		// The eye: an outline, a lid that opens with the meter, and the pupil.
		int w = 15, h = Math.max(1, Math.round(6 * open));
		g.fill(cx - w - 2, top + 6, cx + w + 2, top + 7, 0xAA000000);
		for (int x = -w; x <= w; x++) {
			int y = Math.round((float) Math.sqrt(Math.max(0, 1 - (x * x) / (float) (w * w))) * h);
			g.fill(cx + x, top + 6 - y, cx + x + 1, top + 7 + y, ARGB.color(open > 0 ? 200 : 120, (color >> 16) & 255, (color >> 8) & 255, color & 255));
		}
		if (open > 0.15f) g.fill(cx - 2, top + 6 - Math.min(3, h), cx + 3, top + 7 + Math.min(3, h), 0xFF101010);
		// The meter as a bar under the eye.
		g.fill(cx - 20, top + 15, cx + 20, top + 17, 0x88000000);
		g.fill(cx - 20, top + 15, cx - 20 + Math.round(40 * Mth.clamp(shownMeter, 0, 1)), top + 17, color);
		String key = switch (stealthState) {
			case com.dantemorrigan.gauntlet.doom.LatveriaHunt.CHASING -> "hud.infinity_gauntlet.stealth.found";
			case com.dantemorrigan.gauntlet.doom.LatveriaHunt.SEARCHING -> "hud.infinity_gauntlet.stealth.search";
			case com.dantemorrigan.gauntlet.doom.LatveriaHunt.INBOUND -> "hud.infinity_gauntlet.stealth.inbound";
			case com.dantemorrigan.gauntlet.doom.LatveriaHunt.SIEGE -> "hud.infinity_gauntlet.stealth.siege";
			case com.dantemorrigan.gauntlet.doom.LatveriaHunt.HOTZONE -> "hud.infinity_gauntlet.stealth.hot";
			default -> shownMeter > 0.02f ? "hud.infinity_gauntlet.stealth.suspect" : "hud.infinity_gauntlet.stealth.hidden";
		};
		boolean timed = stealthState == com.dantemorrigan.gauntlet.doom.LatveriaHunt.SEARCHING || stealthState == com.dantemorrigan.gauntlet.doom.LatveriaHunt.SIEGE
				|| stealthState == com.dantemorrigan.gauntlet.doom.LatveriaHunt.HOTZONE;
		if (stealthState == com.dantemorrigan.gauntlet.doom.LatveriaHunt.HOTZONE) color = 0xFFFF8A2E;
		Component text = timed
				? Component.translatable(key, stealthLeft / 60 + ":" + String.format("%02d", stealthLeft % 60)) : Component.translatable(key);
		g.centeredText(mc.font, text, cx, top + 20, color);
		// Markers toward the robots that sense you.
		float yaw = mc.player.getYRot();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof com.dantemorrigan.gauntlet.doom.Doombot b) || b.awareness() == 0 || b.distanceTo(mc.player) > 64) continue;
			double dx = b.getX() - mc.player.getX(), dz = b.getZ() - mc.player.getZ();
			float rel = (float) Math.toRadians(Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)) - yaw));
			int mx = g.guiWidth() / 2 + Math.round(Mth.sin(rel) * 34), my = g.guiHeight() / 2 - Math.round(Mth.cos(rel) * 34);
			int c = b.awareness() == 2 ? 0xFFE8322A : 0xFFFFC23A;
			g.fill(mx - 2, my - 2, mx + 3, my + 3, 0xAA000000);
			g.fill(mx - 1, my - 1, mx + 2, my + 2, c);
		}
	}

	private static boolean hasBossBar(Minecraft mc) {
		for (Entity e : mc.level.entitiesForRendering()) if (e instanceof DoctorDoom d && d.isAlive() && d.distanceToSqr(mc.player) < 80 * 80) return true;
		return false;
	}

	private DoomClient() {}
}
