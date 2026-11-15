package com.dantemorrigan.gauntlet.client.screen;

import com.dantemorrigan.gauntlet.GauntletConfig;
import com.dantemorrigan.gauntlet.client.lang.ModLang;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Mod settings: card-style language switcher (Auto / English / Русский) and client presentation controls. */
public final class ModSettingsScreen extends Screen {
	private static final int ACCENT = 0xFFBB72FF;
	private static final String[] NATIVE = {null, "English", "Русский"};
	private static final String[] BADGE = {"AUTO", "EN", "RU"};
	private static final int[] BADGE_COLOR = {0xFF8A8FA8, 0xFF51B8FF, 0xFFFF6678};

	private final Screen parent;
	private int selected;
	private int hovered = -1;
	/** Per-card hover animation, 0..1. */
	private final float[] glow = new float[3];

	public ModSettingsScreen(Screen parent) {
		super(Component.translatable("settings.infinity_gauntlet.title"));
		this.parent = parent;
		String cur = ModLang.current();
		for (int i = 0; i < ModLang.CODES.length; i++) if (ModLang.CODES[i].equals(cur)) selected = i;
	}

	private int cardW() { return Math.max(70, Math.min(116, (width - 60) / 3)); }
	private int cardH() { return 76; }
	private int cardX(int i) { return width / 2 - (cardW() * 3 + 16) / 2 + i * (cardW() + 8); }
	private int cardY() { return Math.max(42, height / 2 - 86); }
	private int clientRowsY() { return cardY() + cardH() + 34; }

	@Override
	protected void init() {
		GauntletConfig cfg = GauntletConfig.get();
		int bw = Math.min(156, (width - 36) / 2);
		int leftX = width / 2 - bw - 4;
		int rightX = width / 2 + 4;
		int row1Y = clientRowsY();
		int row2Y = row1Y + 24;

		addRenderableWidget(Button.builder(hudLabel(cfg), b -> {
			cfg.showStatusHud = !cfg.showStatusHud;
			GauntletConfig.save();
			b.setMessage(hudLabel(cfg));
		}).bounds(width / 2 - bw / 2, row1Y, bw, 20).build());

		addRenderableWidget(Button.builder(shakeLabel(cfg), b -> {
			cfg.cameraShakeScale = nextStep(cfg.cameraShakeScale);
			GauntletConfig.save();
			b.setMessage(shakeLabel(cfg));
		}).bounds(leftX, row2Y, bw, 20).build());

		addRenderableWidget(Button.builder(flashLabel(cfg), b -> {
			cfg.flashIntensity = nextStep(cfg.flashIntensity);
			GauntletConfig.save();
			b.setMessage(flashLabel(cfg));
		}).bounds(rightX, row2Y, bw, 20).build());

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(width / 2 - 75, Math.min(height - 24, row2Y + 28), 150, 20).build());
	}

	private static float nextStep(float cur) {
		if (cur > 0.75f) return 0.5f;
		if (cur > 0.1f) return 0.0f;
		return 1.0f;
	}

	private static Component stepText(float val) {
		if (val <= 0.05f) return Component.translatable("settings.infinity_gauntlet.value.off");
		return Component.literal(Math.round(val * 100f) + "%");
	}

	private static Component hudLabel(GauntletConfig cfg) {
		Component state = Component.translatable(cfg.showStatusHud
				? "settings.infinity_gauntlet.value.on"
				: "settings.infinity_gauntlet.value.off");
		return Component.translatable("settings.infinity_gauntlet.hud", state);
	}

	private static Component shakeLabel(GauntletConfig cfg) {
		return Component.translatable("settings.infinity_gauntlet.shake", stepText(cfg.cameraShakeScale));
	}

	private static Component flashLabel(GauntletConfig cfg) {
		return Component.translatable("settings.infinity_gauntlet.flash", stepText(cfg.flashIntensity));
	}

	@Override public void onClose() { minecraft.setScreen(parent); }

	private void choose(int i) {
		selected = i;
		ModLang.set(ModLang.CODES[i]);
		// Rebuild widgets so every translated label picks the new language up at once.
		rebuildWidgets();
	}

	private int cardAt(double mx, double my) {
		for (int i = 0; i < 3; i++) {
			if (mx >= cardX(i) && mx < cardX(i) + cardW() && my >= cardY() && my < cardY() + cardH()) return i;
		}
		return -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			int i = cardAt(event.x(), event.y());
			if (i >= 0) { choose(i); return true; }
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == GLFW.GLFW_KEY_LEFT) { choose(Math.floorMod(selected - 1, 3)); return true; }
		if (key == GLFW.GLFW_KEY_RIGHT) { choose((selected + 1) % 3); return true; }
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		super.extractRenderState(g, mx, my, delta); // background + buttons
		hovered = cardAt(mx, my);
		int cy = cardY();
		g.centeredText(font, title.copy().withStyle(s -> s.withBold(true)), width / 2, Math.max(8, cy - 34), 0xFFFFFFFF);
		g.centeredText(font, Component.translatable("settings.infinity_gauntlet.language"), width / 2, cy - 22, 0xFFB9B2D6);
		fitted(g, Component.translatable("settings.infinity_gauntlet.language.hint"), cy - 11, 0xFFA29DBA);

		for (int i = 0; i < 3; i++) {
			glow[i] += ((hovered == i ? 1f : 0f) - glow[i]) * Math.min(1f, delta * 0.35f + 0.12f);
			drawCard(g, i);
		}

		String cur = ModLang.CODES[selected];
		Component note = switch (cur) {
			case "en" -> Component.translatable("settings.infinity_gauntlet.note.en");
			case "ru" -> Component.translatable("settings.infinity_gauntlet.note.ru");
			default -> Component.translatable("settings.infinity_gauntlet.note.auto");
		};
		fitted(g, note, cy + cardH() + 5, 0xFFD9D4F0);
		g.centeredText(font, Component.translatable("settings.infinity_gauntlet.client_section"), width / 2, clientRowsY() - 12, 0xFFB9B2D6);
	}

	/** Centered text that shrinks to fit the screen instead of running off it. */
	private void fitted(GuiGraphicsExtractor g, Component text, int y, int color) {
		int tw = Math.max(1, font.width(text));
		float s = Math.max(.6f, Math.min(1f, (width - 24f) / tw));
		g.pose().pushMatrix();
		g.pose().translate(width / 2f, y);
		g.pose().scale(s, s);
		g.centeredText(font, text, 0, 0, color);
		g.pose().popMatrix();
	}

	private void drawCard(GuiGraphicsExtractor g, int i) {
		int x = cardX(i), y = cardY(), w = cardW(), h = cardH();
		boolean sel = i == selected;
		float gl = glow[i];
		int border = sel ? ACCENT : blend(0xFF3A3650, 0xFF8F86B8, gl);
		int top = sel ? 0xF02A2145 : blend(0xE0181626, 0xE0262240, gl);
		int bottom = sel ? 0xF0171230 : 0xE00F0E1A;
		g.fillGradient(x, y, x + w, y + h, top, bottom);
		g.outline(x, y, w, h, border);
		if (sel) g.outline(x - 1, y - 1, w + 2, h + 2, (ACCENT & 0x00FFFFFF) | 0x66000000);

		// Badge
		int bw = Math.max(34, font.width(BADGE[i]) + 14), bx = x + (w - bw) / 2, by = y + 8;
		g.fill(bx, by, bx + bw, by + 18, (BADGE_COLOR[i] & 0x00FFFFFF) | 0x38000000);
		g.outline(bx, by, bw, 18, BADGE_COLOR[i]);
		g.centeredText(font, BADGE[i], x + w / 2, by + 5, BADGE_COLOR[i]);

		// Name in its own language, never translated
		Component name = NATIVE[i] == null ? Component.translatable("settings.infinity_gauntlet.language.auto") : Component.literal(NATIVE[i]);
		g.centeredText(font, name.copy().withStyle(s -> s.withBold(true)), x + w / 2, y + 34, sel ? 0xFFFFFFFF : 0xFFC9C4E0);
		Component sub = i == 0 ? Component.translatable("settings.infinity_gauntlet.language.auto.sub")
				: Component.literal(i == 1 ? "United States" : "Россия");
		g.centeredText(font, sub, x + w / 2, y + 46, 0xFF8F8AA8);

		if (sel) {
			g.fill(x + 10, y + h - 16, x + w - 10, y + h - 5, 0x40FFFFFF & ((ACCENT & 0x00FFFFFF) | 0xFF000000));
			g.centeredText(font, "✔", x + w / 2, y + h - 14, 0xFFFFFFFF);
		}
	}

	private static int blend(int a, int b, float t) {
		int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255, aa = a >>> 24;
		int br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255, ba = b >>> 24;
		return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
	}
}
