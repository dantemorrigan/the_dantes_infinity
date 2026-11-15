package com.dantemorrigan.gauntlet.client.doom;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * The welcome to Latveria, shown once after the title card of the first arrival: what this land is, what watches it,
 * what hunts there and how one might get into the castle, without giving the assault away. Scrolls when the window
 * is small.
 */
public final class LatveriaWelcomeScreen extends Screen {
	private static final String KEY = "screen.infinity_gauntlet.latveria_welcome.";
	private static final int SECTIONS = 5;
	private static final Identifier EMBLEM = InfinityGauntlet.id("textures/gui/doctor_doom/latveria_emblem.png");
	private final List<Line> lines = new ArrayList<>();
	private int panelW, contentH;
	private double scroll;

	private record Line(FormattedCharSequence text, int color, int gapBefore) {}

	public LatveriaWelcomeScreen() {
		super(Component.translatable(KEY + "title"));
	}

	@Override
	protected void init() {
		panelW = Math.min(360, width - 24);
		int textW = panelW - 28;
		lines.clear();
		for (FormattedCharSequence s : font.split(Component.translatable(KEY + "intro"), textW)) lines.add(new Line(s, 0xFFFFE08A, 0));
		for (int i = 1; i <= SECTIONS; i++) {
			boolean first = true;
			for (FormattedCharSequence s : font.split(Component.translatable(KEY + "s" + i + ".title").withStyle(st -> st.withBold(true)), textW)) {
				lines.add(new Line(s, 0xFF9CFFB4, first ? 9 : 0));
				first = false;
			}
			for (FormattedCharSequence s : font.split(Component.translatable(KEY + "s" + i + ".text"), textW)) lines.add(new Line(s, 0xFFDDE8E2, 0));
		}
		contentH = 0;
		for (Line l : lines) contentH += 10 + l.gapBefore;
		addRenderableWidget(Button.builder(Component.translatable(KEY + "ok"), b -> onClose())
				.bounds(width / 2 - 70, height - 28, 140, 20).build());
	}

	private int top() { return 46; }

	private int viewH() { return height - 28 - 8 - top(); }

	@Override
	public boolean mouseScrolled(double mx, double my, double dx, double dy) {
		scroll = Mth.clamp(scroll - dy * 14, 0, Math.max(0, contentH - viewH()));
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		super.extractRenderState(g, mx, my, delta);
		int x0 = width / 2 - panelW / 2, x1 = x0 + panelW;
		// The panel: black glass framed in Latverian green, the emblem above the title.
		g.fill(x0 - 2, 6, x1 + 2, height - 34, 0xFF2E6B3F);
		g.fill(x0, 8, x1, height - 36, 0xEE0B110E);
		g.blit(RenderPipelines.GUI_TEXTURED, EMBLEM, width / 2 - 10, 10, 0, 0, 20, 20, 64, 64, 64, 64, -1);
		g.centeredText(font, title.copy().withStyle(s -> s.withBold(true)), width / 2, 33, 0xFFFFFFFF);
		g.enableScissor(x0, top(), x1, top() + viewH());
		int y = top() - (int) scroll;
		for (Line l : lines) {
			y += l.gapBefore;
			g.text(font, l.text, x0 + 14, y, l.color, false);
			y += 10;
		}
		g.disableScissor();
		if (contentH > viewH()) {
			int bar = Math.max(12, viewH() * viewH() / contentH);
			int at = top() + (int) ((viewH() - bar) * scroll / Math.max(1, contentH - viewH()));
			g.fill(x1 - 4, at, x1 - 2, at + bar, 0xFF5FA874);
		}
	}
}
