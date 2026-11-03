package com.dantemorrigan.gauntlet.client;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
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
    private static final int[] COLORS = {0xFFA23BFF, 0xFF168CFF, 0xFFEF293E, 0xFFFFE157, 0xFF32E268, 0xFFFF981F};
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
            show(Component.literal(packet.text()));
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
        if (mc.player == null || mc.options.hideGui || (notice == null && LINES.isEmpty())) return;
        int maxWidth = Math.max(60, Math.min(270, g.guiWidth() - 40));
        List<FormattedCharSequence> rows = new ArrayList<>();
        for (Component line : LINES.values()) rows.addAll(mc.font.split(bold(line), maxWidth));
        if (notice != null) rows.addAll(mc.font.split(bold(notice), maxWidth));
        // Bound the panel on small windows; long messages wrap inside its background.
        int limit = Math.max(1, (g.guiHeight() - 125) / 12);
        if (rows.size() > limit) rows = new ArrayList<>(rows.subList(0, limit));
        int width = 120;
        for (var row : rows) width = Math.max(width, mc.font.width(row));
        width = Math.min(width, maxWidth);
        int x = 12, y = 52, height = 23 + rows.size() * 12;
        g.fill(x, y, x + width + 18, y + height, 0xD014101F);
        g.fill(x, y, x + 2, y + height, 0xFFE8B75F);
        for (int i = 0; i < 6; i++) g.fill(x + 9 + i * 7, y + 8, x + 13 + i * 7, y + 11, COLORS[i]);
        g.fill(x + 9, y + 17, x + width + 9, y + 18, 0x504F4263);
        for (int i = 0; i < rows.size(); i++) g.text(mc.font, rows.get(i), x + 9, y + 23 + i * 12, 0xFFF4EFFA, true);
    }
    private StatusHud() {}
}
