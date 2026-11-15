package com.dantemorrigan.gauntlet.client.ability;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ability.Ability;
import com.dantemorrigan.gauntlet.client.doom.DoomClient;
import com.dantemorrigan.gauntlet.client.fx.ClientPower;
import com.dantemorrigan.gauntlet.client.ui.Smooth;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ScrollWheelHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The mouse wheel as a quick ability switch. With the gauntlet worn (or the Doom armor on), hold the scroll key
 * (Left Alt by default, rebindable; config {@code abilityScrollMode}: "modifier", "always" or "off") and turn the wheel:
 * the choice changes on the same frame, a strip of neighbouring abilities glides under the crosshair, and a soft tick
 * answers every notch. Nothing waits for the server and the hotbar stays put.
 */
public final class AbilityScroll {
	public static final KeyMapping MODIFIER = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.infinity_gauntlet.ability_scroll",
		InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, ClientPower.CATEGORY));

	private static final ScrollWheelHandler WHEEL = new ScrollWheelHandler();
	private static final int VISIBLE_HALF = 2;
	private static final float SHOW_SECONDS = 1.6f;

	// Strip state. `target` counts steps without wrapping, so the strip always glides the short way round.
	private static int target;
	private static double pos;
	private static float alpha;
	private static long shownAt, lastFrame, lastTick;
	private static int streak;

	public static void init() {
		HudElementRegistry.addLast(InfinityGauntlet.id("ability_scroll"), (g, delta) -> render(g));
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> {alpha = 0;streak = 0;});
	}

	/** Called by the mouse mixin before the hotbar sees the wheel. True swallows the event. */
	public static boolean onMouseScroll(double x, double y) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.screen != null || mc.getOverlay() != null || !wanted()) return false;
		boolean gauntlet = AbilityInput.scrollAllowed() && AbilityInput.scrollable().size() > 1;
		boolean doom = !gauntlet && mc.player.isAlive() && DoomClient.fullSet() && !DoomClient.cinematic();
		if (!gauntlet && !doom) return false;
		// Same wheel options as the hotbar: discrete scrolling and the sensitivity slider apply.
		boolean discrete = mc.options.discreteMouseScroll().get();
		double sensitivity = mc.options.mouseWheelSensitivity().get();
		var steps = WHEEL.onMouseScroll((discrete ? Math.signum(x) : x) * sensitivity, (discrete ? Math.signum(y) : y) * sensitivity);
		int delta = steps.y != 0 ? steps.y : -steps.x;
		if (delta == 0) return true; // a fraction of a notch (touchpad): keep collecting it
		int direction = Math.max(-3, Math.min(3, -delta)); // wheel down = next, like the hotbar
		if (gauntlet) step(direction); else DoomClient.scroll(direction);
		return true;
	}

	private static boolean wanted() {
		String mode = GauntletConfig.get().abilityScrollMode;
		if ("off".equalsIgnoreCase(mode)) return false;
		if ("always".equalsIgnoreCase(mode)) return true;
		return !MODIFIER.isUnbound() && MODIFIER.isDown();
	}

	private static void step(int direction) {
		List<Ability> list = AbilityInput.scrollable();
		int n = list.size();
		Ability from = AbilityInput.selected();
		Ability to = AbilityInput.scroll(direction);
		if (to == null) return;
		int index = list.indexOf(to);
		long now = System.nanoTime();
		boolean live = alpha > 0.05f && Math.floorMod(target, n) == list.indexOf(from);
		if (live) target += direction; else {target = index;pos = index;}
		shownAt = now;
		tick(direction);
	}

	/** One soft tick per notch; a quick run of notches climbs (or falls) in pitch like a ratchet. */
	public static void tick(int direction) {
		long now = System.nanoTime();
		streak = now - lastTick < 350_000_000L ? Math.min(streak + 1, 10) : 0;
		lastTick = now;
		float pitch = direction > 0 ? 1.15f + 0.045f * streak : 1.55f - 0.045f * streak;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
	}

	private static void render(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		long now = System.nanoTime();
		float dt = Smooth.dt(lastFrame, now);
		lastFrame = now;
		boolean show = (now - shownAt) / 1e9f < SHOW_SECONDS && mc.screen == null && !mc.options.hideGui && AbilityInput.equipped();
		alpha = Smooth.approach(alpha, show ? 1f : 0f, show ? 26f : 7f, dt);
		if (alpha <= 0.01f) return;
		List<Ability> list = AbilityInput.scrollable();
		int n = list.size();
		if (n < 2) return;
		pos = Smooth.approach(pos, target, 22.0, dt);

		int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2 + 40;
		int spacing = 26, half = VISIBLE_HALF;
		int panelHalf = half * spacing + 30;
		int fade = (int) (alpha * 255);
		g.fill(cx - panelHalf, cy - 22, cx + panelHalf, cy + 36, ARGB.color((int) (alpha * 150), 12, 10, 22));
		g.fill(cx - panelHalf, cy - 22, cx - panelHalf + 2, cy + 36, ARGB.color(fade, 232, 183, 95));

		int base = (int) Math.floor(pos);
		for (int k = base - half; k <= base + half + 1; k++) {
			float d = (float) (k - pos), ad = Math.abs(d);
			float vis = Smooth.clamp01(half + 1f - ad);
			if (vis <= 0f) continue;
			Ability a = list.get(Math.floorMod(k, n));
			float focus = 1f - Smooth.smoothstep(Math.min(1f, ad));
			float size = 16f + 14f * focus;
			float x = cx + Math.signum(d) * (ad * spacing + 5f * Math.min(1f, ad)) - size / 2f;
			float y = cy - size / 2f - 2f * focus;
			boolean usable = AbilityInput.reason(a).isEmpty();
			int shade = usable ? 255 : 120;
			int color = ARGB.color((int) (fade * vis * (0.55f + 0.45f * focus)), shade, shade, shade);
			icon(g, a, x, y, size, color);
			if (focus > 0.85f) frame(g, (int) x - 3, (int) y - 3, (int) size + 6, ARGB.color((int) (fade * (focus - 0.85f) / 0.15f), 244, 214, 141));
		}
		Ability current = list.get(Math.floorMod(target, n));
		Component title = AbilityInput.title(current), hint = AbilityInput.hint(current);
		var font = mc.font;
		g.centeredText(font, title, cx, cy + 18, ARGB.color(fade, 244, 214, 141));
		if (alpha > 0.4f) {
			g.pose().pushMatrix();
			g.pose().translate(cx, cy + 28);
			g.pose().scale(0.75f, 0.75f);
			g.centeredText(font, hint, 0, 0, ARGB.color(fade, 200, 196, 214));
			g.pose().popMatrix();
		}
	}

	private static void frame(GuiGraphicsExtractor g, int x, int y, int s, int color) {
		g.fill(x, y, x + s, y + 1, color);
		g.fill(x, y + s - 1, x + s, y + s, color);
		g.fill(x, y, x + 1, y + s, color);
		g.fill(x + s - 1, y, x + s, y + s, color);
	}

	private static void icon(GuiGraphicsExtractor g, Ability a, float x, float y, float size, int color) {
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(size / 64f, size / 64f);
		g.blit(RenderPipelines.GUI_TEXTURED, InfinityGauntlet.id("textures/gui/ability_wheel/icons_64/" + AbilityWheelScreen.iconName(a) + ".png"),
			0, 0, 0f, 0f, 64, 64, 64, 64, 64, 64, color);
		pose.popMatrix();
	}

	private AbilityScroll() {}
}
