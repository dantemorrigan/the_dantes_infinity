package com.dantemorrigan.gauntlet.client.doom;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.doom.DoomArmor;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.dantemorrigan.gauntlet.client.ability.AbilityScroll;
import com.dantemorrigan.gauntlet.client.ui.Smooth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ScrollWheelHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.lwjgl.glfw.GLFW;

/** The wheel of the Doom armor: hold the key, point at a power, let go (or click) to cast it. */
public final class DoomWheelScreen extends Screen {
	private int hovered = -1;
	/** Per-power hover progress (0..1) and the opening fade; both ease by real time, not by tick. */
	private final float[] glow = new float[6];
	private float open;
	private long lastFrame;
	private final ScrollWheelHandler wheel = new ScrollWheelHandler();
	private double mouseX = -1000, mouseY = -1000, anchorX, anchorY;
	/** The wheel or a key chose the power: a little mouse jitter must not take it away. */
	private boolean scrolled;

	public DoomWheelScreen() {
		super(Component.translatable("doom.infinity_gauntlet.wheel"));
	}

	@Override
	public boolean isPauseScreen() { return false; }

	/** Lines of help at the bottom; the wheel sits centred in the space above them. */
	private int helpLines() {
		return font.split(Component.translatable("doom.infinity_gauntlet.wheel_help"), Math.max(120, width - 40)).size();
	}

	private double centerY() {
		return (height - 18 - helpLines() * 10) / 2.0;
	}

	private double radius() {
		return Math.min(width * 0.5, centerY() * 2) * 0.32;
	}

	private int iconSize() {
		return (int) Math.max(24, Math.min(width, height) * 0.11);
	}

	private double[] point(int i) {
		double a = Math.toRadians(-90 + i * 60);
		double r = radius();
		return new double[]{width / 2.0 + Math.cos(a) * r, centerY() + Math.sin(a) * r};
	}

	/** Screen position of a power's button; used by the visual tests. */
	public double[] abilityPoint(int i) { return point(i); }

	/** Letting go of the key (or a click) only picks the power under the pointer; the cast key fires it. */
	private void select() {
		int choice = hovered;
		Minecraft.getInstance().setScreen(null);
		if (choice >= 0) DoomClient.select(choice);
	}

	@Override
	public void tick() {
		var player = Minecraft.getInstance().player;
		if (!DoomClient.fullSet() || player == null || !player.isAlive()) Minecraft.getInstance().setScreen(null);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (event.key() == KeyMappingHelper.getBoundKeyOf(DoomClient.WHEEL).getValue()) select();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) { Minecraft.getInstance().setScreen(null); return true; }
		if (event.key() >= GLFW.GLFW_KEY_1 && event.key() <= GLFW.GLFW_KEY_6) { hovered = event.key() - GLFW.GLFW_KEY_1; select(); }
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) { pointer(event.x(), event.y()); select(); }
		else Minecraft.getInstance().setScreen(null);
		return true;
	}

	/** The wheel walks around the ring (down = next); the choice shows at once and the cast stays a separate press. */
	@Override
	public boolean mouseScrolled(double mx, double my, double dx, double dy) {
		var steps = wheel.onMouseScroll(dx, dy);
		int delta = steps.y != 0 ? steps.y : -steps.x;
		if (delta == 0) return true;
		int direction = Math.max(-3, Math.min(3, -delta));
		hovered = Math.floorMod((hovered >= 0 ? hovered : DoomClient.selected()) + direction, 6);
		scrolled = true;
		anchorX = mouseX;
		anchorY = mouseY;
		AbilityScroll.tick(direction);
		return true;
	}

	private void pointer(double mx, double my) {
		if (mx == mouseX && my == mouseY) return;
		if (scrolled && Math.hypot(mx - anchorX, my - anchorY) < 10) return;
		mouseX = mx;
		mouseY = my;
		scrolled = false;
		pick(mx, my);
	}

	private void pick(double mx, double my) {
		hovered = -1;
		double best = iconSize();
		for (int i = 0; i < 6; i++) {
			double[] p = point(i);
			double d = Math.hypot(mx - p[0], my - p[1]);
			if (d < best) { best = d; hovered = i; }
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		pointer(mx, my);
		long now = System.nanoTime();
		float dt = Smooth.dt(lastFrame, now);
		lastFrame = now;
		open = Smooth.approach(open, 1f, 16f, dt);
		for (int i = 0; i < 6; i++) glow[i] = Smooth.approach(glow[i], i == hovered ? 1f : 0f, 22f, dt);
		g.fill(0, 0, width, height, ARGB.color((int) (0x9C * open), 0x06, 0x10, 0x0A));
		int base = iconSize();
		for (int i = 0; i < 6; i++) {
			double[] p = point(i);
			boolean ready = DoomClient.cooldown(i) <= 0;
			// The hovered power swells and settles with a soft pop; the whole ring eases in as it opens.
			float lift = 1f + 0.16f * Smooth.outBack(glow[i]) * (glow[i] > 0 ? 1f : 0f);
			float size = base * lift * (0.82f + 0.18f * Smooth.smoothstep(open));
			float x = (float) (p[0] - size / 2.0), y = (float) (p[1] - size / 2.0);
			int fade = (int) (255 * Smooth.smoothstep(open));
			String frame = i == hovered ? "state_warning" : ready ? "state_active" : "state_cooldown";
			if (i == DoomClient.selected()) g.fill((int) (x - size / 3), (int) (y - size / 3), (int) (x + size + size / 3), (int) (y + size + size / 3), 0x44FFD36B);
			blit(g, "textures/gui/doctor_doom/" + frame + ".png", x - size / 4, y - size / 4, size + size / 2, ARGB.color(fade, 255, 255, 255));
			blit(g, "textures/gui/doctor_doom/icons/" + DoomClient.ICONS[i] + ".png", x, y, size, ready ? ARGB.color(fade, 255, 255, 255) : ARGB.color(fade, 96, 104, 100));
			Component name = Component.translatable("doom.infinity_gauntlet.power." + DoomArmor.NAMES[i]);
			// Names go outward from the wheel: above the top power, below the bottom one, beside the others.
			int color = ARGB.color(fade, (int) (0xE8 + (0x9C - 0xE8) * glow[i]), (int) (0xE0 + (0xFF - 0xE0) * glow[i]), (int) (0xC8 + (0xB4 - 0xC8) * glow[i]));
			int pad = (int) (size / 4) + 4;
			switch (i) {
				case 0 -> g.centeredText(font, name, (int) p[0], (int) (y - pad - 9), color);
				case 3 -> g.centeredText(font, name, (int) p[0], (int) (y + size + pad), color);
				case 1, 2 -> g.text(font, name, (int) (x + size + pad), (int) p[1] - 4, color, true);
				default -> g.text(font, name, (int) (x - pad - font.width(name)), (int) p[1] - 4, color, true);
			}
			if (!ready) g.centeredText(font, Component.literal(String.valueOf((DoomClient.cooldown(i) + 19) / 20)), (int) p[0], (int) p[1] - 4, 0xFFFFE08A);
		}
		blit(g, "textures/gui/doctor_doom/latveria_emblem.png", width / 2f - base / 2f, (float) centerY() - base / 2f, base, ARGB.color((int) (255 * open), 255, 255, 255));
		var help = font.split(Component.translatable("doom.infinity_gauntlet.wheel_help"), Math.max(120, width - 40));
		int y = height - 14 - help.size() * 10;
		for (var line : help) {
			g.text(font, line, width / 2 - font.width(line) / 2, y, 0xFFDED7EA, true);
			y += 10;
		}
	}

	private static void blit(GuiGraphicsExtractor g, String path, float x, float y, float size, int color) {
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(size / 64f, size / 64f);
		int tex = path.endsWith("latveria_emblem.png") ? 96 : 64;
		pose.scale(64f / tex, 64f / tex);
		g.blit(RenderPipelines.GUI_TEXTURED, InfinityGauntlet.id(path), 0, 0, 0, 0, tex, tex, tex, tex, tex, tex, color);
		pose.popMatrix();
	}
}
