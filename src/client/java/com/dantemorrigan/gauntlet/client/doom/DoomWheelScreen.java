package com.dantemorrigan.gauntlet.client.doom;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.doom.DoomArmor;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
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

	public DoomWheelScreen() {
		super(Component.translatable("doom.infinity_gauntlet.wheel"));
	}

	@Override
	public boolean isPauseScreen() { return false; }

	private double[] point(int i) {
		double a = Math.toRadians(-90 + i * 60);
		double r = Math.min(width, height) * 0.27;
		return new double[]{width / 2.0 + Math.cos(a) * r, height / 2.0 + Math.sin(a) * r};
	}

	/** Screen position of a power's button; used by the visual tests. */
	public double[] abilityPoint(int i) { return point(i); }

	private void cast() {
		int choice = hovered;
		Minecraft.getInstance().setScreen(null);
		if (choice >= 0 && ClientPlayNetworking.canSend(ModNetwork.DoomCast.TYPE)) ClientPlayNetworking.send(new ModNetwork.DoomCast(choice));
	}

	@Override
	public void tick() {
		var player = Minecraft.getInstance().player;
		if (!DoomClient.fullSet() || player == null || !player.isAlive()) Minecraft.getInstance().setScreen(null);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (event.key() == KeyMappingHelper.getBoundKeyOf(DoomClient.WHEEL).getValue()) cast();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) { Minecraft.getInstance().setScreen(null); return true; }
		if (event.key() >= GLFW.GLFW_KEY_1 && event.key() <= GLFW.GLFW_KEY_6) { hovered = event.key() - GLFW.GLFW_KEY_1; cast(); }
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) { pick(event.x(), event.y()); cast(); }
		else Minecraft.getInstance().setScreen(null);
		return true;
	}

	private void pick(double mx, double my) {
		hovered = -1;
		double best = Math.min(width, height) * 0.13;
		for (int i = 0; i < 6; i++) {
			double[] p = point(i);
			double d = Math.hypot(mx - p[0], my - p[1]);
			if (d < best) { best = d; hovered = i; }
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		pick(mx, my);
		g.fill(0, 0, width, height, 0x9C06100A);
		int size = (int) Math.max(28, Math.min(width, height) * 0.13);
		for (int i = 0; i < 6; i++) {
			double[] p = point(i);
			boolean ready = DoomClient.cooldown(i) <= 0;
			int x = (int) (p[0] - size / 2.0), y = (int) (p[1] - size / 2.0);
			String frame = i == hovered ? "state_warning" : ready ? "state_active" : "state_cooldown";
			blit(g, "textures/gui/doctor_doom/" + frame + ".png", x - size / 4, y - size / 4, size + size / 2, -1);
			blit(g, "textures/gui/doctor_doom/icons/" + DoomClient.ICONS[i] + ".png", x, y, size, ready ? -1 : ARGB.color(255, 96, 104, 100));
			Component name = Component.translatable("doom.infinity_gauntlet.power." + DoomArmor.NAMES[i]);
			g.centeredText(font, name, (int) p[0], y + size + 5, i == hovered ? 0xFF9CFFB4 : 0xFFE8E0C8);
			if (!ready) g.centeredText(font, Component.literal(String.valueOf((DoomClient.cooldown(i) + 19) / 20)), (int) p[0], (int) p[1] - 4, 0xFFFFE08A);
		}
		blit(g, "textures/gui/doctor_doom/latveria_emblem.png", width / 2 - size / 2, height / 2 - size / 2, size, -1);
		g.centeredText(font, Component.translatable("doom.infinity_gauntlet.wheel_help"), width / 2, height - 24, 0xFFDED7EA);
	}

	private static void blit(GuiGraphicsExtractor g, String path, int x, int y, int size, int color) {
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
