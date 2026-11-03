package com.dantemorrigan.gauntlet.client.ability;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.ability.Ability;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

public final class AbilityFavorites {
    private static final int COUNT = 6;
    private static final int STEP = 22;
    private static final int CELL_WIDTH = 20;
    public static final int WIDTH = COUNT * STEP - 2;
    static final int HEIGHT = 25;
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("infinity_gauntlet_favorites.txt");
    private static final Ability[] SLOTS = load();

    private AbilityFavorites() {}

    static int slotForKey(int key) {
        return key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_6 ? key - GLFW.GLFW_KEY_1 : -1;
    }

    static int slotAt(double x, double y) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || x % STEP >= CELL_WIDTH) return -1;
        return (int) x / STEP;
    }

    static int find(Ability ability) {
        if (ability == null) return -1;
        for (int i = 0; i < COUNT; i++) if (SLOTS[i] == ability) return i;
        return -1;
    }

    static Ability get(int slot) {
        return slot < 0 || slot >= COUNT ? null : SLOTS[slot];
    }

    static void toggle(int slot, Ability ability) {
        Ability[] updated = SLOTS.clone();
        if (updated[slot] == ability) {
            updated[slot] = null;
        } else {
            int previous = find(ability);
            if (previous >= 0) updated[previous] = null;
            updated[slot] = ability;
        }

        try {
            Files.createDirectories(FILE.getParent());
            Path temporary = Files.createTempFile(FILE.getParent(), "gauntlet-favorites-", ".tmp");
            try {
                Files.write(temporary, Arrays.stream(updated).map(a -> a == null ? "-" : a.id).toList());
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
            System.arraycopy(updated, 0, SLOTS, 0, COUNT);
        } catch (IOException exception) {
            InfinityGauntlet.LOGGER.warn("Could not save ability favorites", exception);
        }
    }

    private static Ability[] load() {
        Ability[] slots = new Ability[COUNT];
        if (!Files.isRegularFile(FILE)) return slots;
        try {
            var lines = Files.readAllLines(FILE);
            for (int i = 0; i < Math.min(COUNT, lines.size()); i++) {
                Ability ability = Ability.byId(lines.get(i).strip());
                if (ability != Ability.DESTINATIONS) slots[i] = ability;
            }
        } catch (IOException exception) {
            InfinityGauntlet.LOGGER.warn("Could not read ability favorites", exception);
        }
        return slots;
    }

    public static void render(GuiGraphicsExtractor graphics, int x, int y, int hoveredSlot) {
        var font = Minecraft.getInstance().font;
        for (int i = 0; i < COUNT; i++) {
            int left = x + i * STEP;
            Ability ability = SLOTS[i];
            boolean selected = ability != null && ability == AbilityInput.selected();
            int border = selected || i == hoveredSlot ? 0xFFE8B75F : 0xFF504263;
            graphics.fill(left, y, left + CELL_WIDTH, y + HEIGHT, border);
            graphics.fill(left + 1, y + 1, left + CELL_WIDTH - 1, y + HEIGHT - 1, 0xFF191323);
            if (ability != null) AbilityWheelScreen.icon(graphics, ability, left + 3, y + 1, 14);
            graphics.text(font, Component.literal(Integer.toString(i + 1)), left + 7, y + 15,
                ability == null ? 0xFF9BA7BB : 0xFFF4D68D, false);
        }
    }
}
