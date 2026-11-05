package com.dantemorrigan.gauntlet.client;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.client.ability.AbilityFavorites;
import com.dantemorrigan.gauntlet.client.ability.AbilityFavoritesScreen;
import com.dantemorrigan.gauntlet.client.ability.AbilityInput;
import com.dantemorrigan.gauntlet.client.ability.AbilityWheelScreen;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compact, wrapping top-left panel, independent of health/armor rows and vanilla notifications. */
public final class StatusHud {
    private static final Map<String, Component> LINES = new LinkedHashMap<>();
    private static Component notice;
    private static int remaining;

    public static void show(Component text) { notice = text; remaining = 100; }

    public static void persistent(String key, Component text) {
        if (text == null) LINES.remove(key); else LINES.put(key, text);
    }
    private static Component bold(Component text) {
        // Legacy ability strings still contain section colors; retain bold after every color/reset.
        String value = text.getString().replace("§5", "§d").replace("§9", "§b").replace("§2", "§a")
                .replaceAll("(?i)§([0-9a-fr])", "§$1§l");
        return Component.literal(value).withStyle(ChatFormatting.BOLD);
    }
    private static void clear() { LINES.clear(); notice = null; remaining = 0; }
    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetwork.Status.TYPE, (packet, ctx) -> {
            show(packet.text());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> clear());
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.level == null) clear();
            else if (remaining > 0 && --remaining == 0) notice = null;
        });
        HudElementRegistry.addLast(InfinityGauntlet.id("status_panel"), (g, delta) -> render(g));
    }
    private static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen instanceof AbilityFavoritesScreen
                || mc.screen instanceof AbilityWheelScreen) return;
        boolean equipped = AbilityInput.equipped() && com.dantemorrigan.gauntlet.GauntletConfig.get().showStatusHud;
        if (!equipped && notice == null && LINES.isEmpty()) return;

        int maxWidth = Math.max(44, Math.min(166, g.guiWidth() - 36));
        int favoritesHeight = equipped ? 39 : 0;
        int lineHeight = 10;
        List<FormattedCharSequence> rows = new ArrayList<>();
        if (equipped) {
            var selected = AbilityInput.selected();
            rows.addAll(mc.font.split(bold(AbilityInput.title(selected)), maxWidth - 22));
            rows.addAll(mc.font.split(AbilityInput.hint(selected), maxWidth - 22));
        }
        int cardRows = rows.size();
        for (Component line : LINES.values()) rows.addAll(mc.font.split(bold(line), maxWidth));
        if (notice != null) rows.addAll(mc.font.split(bold(notice), maxWidth));

        int limit = Math.max(2, (g.guiHeight() - 112 - favoritesHeight) / lineHeight);
        if (rows.size() > limit) rows = rows.subList(0, limit);
        int width = equipped ? Math.min(maxWidth, AbilityFavorites.WIDTH) : 0;
        for (int row = 0; row < rows.size(); row++) {
            width = Math.max(width, mc.font.width(rows.get(row)) + (row < cardRows ? 22 : 0));
        }
        width = Math.min(width, maxWidth);

        int x = 12, y = 52, padding = 6;
        int height = padding * 2 + rows.size() * lineHeight + favoritesHeight;
        g.fill(x, y, x + width + padding * 2, y + height, 0xD014101F);
        g.fill(x, y, x + 2, y + height, 0xFFE8B75F);
        if (equipped) AbilityWheelScreen.icon(g, AbilityInput.selected(), x + padding, y + padding, 16);
        for (int row = 0; row < rows.size(); row++) {
            g.text(mc.font, rows.get(row), x + padding + (row < cardRows ? 22 : 0),
                y + padding + row * lineHeight, 0xFFF4EFFA, true);
        }
        if (equipped) {
            int barY = y + padding + rows.size() * lineHeight + 3;
            Component shortcut = Component.translatable("ability.infinity_gauntlet.favorites_shortcut",
                AbilityInput.FAVORITES.getTranslatedKeyMessage());
            float scale = Math.min(1f, (float) width / Math.max(AbilityFavorites.WIDTH, mc.font.width(shortcut)));
            g.pose().pushMatrix();
            g.pose().translate(x + padding, barY);
            g.pose().scale(scale, scale);
            g.text(mc.font, shortcut, 0, 0, 0xFFF4D68D, true);
            AbilityFavorites.render(g, 0, 11, -1);
            g.pose().popMatrix();
        }
    }
    private StatusHud() {}
}
