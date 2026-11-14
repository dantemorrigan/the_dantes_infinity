package com.dantemorrigan.gauntlet.client;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Huge health pools stay honest: the awakened gauntlet's 100 hearts are all drawn, row upon row (vanilla packs the rows
 * closer as they pile up). Only a pool beyond {@link #ROWS_HP} - far past anything the add-on grants - is scaled to the
 * share of health, and then the real numbers, e.g. 186/800, stand to the left of the hearts.
 */
public final class CompactHealth {
	/** Beyond this many health points the hearts are scaled; the full gauntlet (200) is shown in full. */
	public static final float ROWS_HP = 400;

	private CompactHealth() {}

	public static float scale(Player player) {
		float max = player.getMaxHealth();
		return max > ROWS_HP ? ROWS_HP / max : 1f;
	}

	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.HEALTH_BAR, InfinityGauntlet.id("compact_health"), (g, delta) -> {
			Minecraft mc = Minecraft.getInstance();
			Player p = mc.player;
			if (p == null || mc.options.hideGui || com.dantemorrigan.gauntlet.client.doom.DoomClient.cinematic() || p.isCreative() || p.isSpectator() || scale(p) >= 1f) return;
			String text = Mth.ceil(p.getHealth() + p.getAbsorptionAmount()) + "/" + Mth.ceil(p.getMaxHealth());
			int x = g.guiWidth() / 2 - 91 - mc.font.width(text) - 4, y = g.guiHeight() - 39;
			g.text(mc.font, text, x, y, 0xFFFF6B6B, true);
		});
	}
}
