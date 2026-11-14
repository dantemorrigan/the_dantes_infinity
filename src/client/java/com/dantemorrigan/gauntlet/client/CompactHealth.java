package com.dantemorrigan.gauntlet.client;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Big health pools in two rows of hearts: above {@link #ROWS_HP} the hearts are scaled to the share of health (see
 * GuiHealthMixin), and the real numbers, e.g. 186/200, stand to the left of them.
 */
public final class CompactHealth {
	/** Two rows of hearts. */
	public static final float ROWS_HP = 40;

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
