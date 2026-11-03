package com.dantemorrigan.gauntlet.client.ability;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Hold the favorites key and choose a slot without navigating the stone wheel. */
public final class AbilityFavoritesScreen extends Screen {
    public AbilityFavoritesScreen() {
        super(Component.translatable("key.infinity_gauntlet.favorites"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        var player = Minecraft.getInstance().player;
        if (player == null || !player.isAlive() || !AbilityInput.equipped()) onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) onClose();
        else choose(AbilityFavorites.slotForKey(event.key()));
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == KeyMappingHelper.getBoundKeyOf(AbilityInput.FAVORITES).getValue()) onClose();
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) choose(AbilityFavorites.slotAt(event.x() - left(), event.y() - top()));
        return true;
    }

    private void choose(int slot) {
        var ability = AbilityFavorites.get(slot);
        if (ability == null) return;
        AbilityInput.choose(ability, -1);
        onClose();
    }

    private int left() {
        return (width - AbilityFavorites.WIDTH) / 2;
    }

    private int top() {
        return height / 2 + 15;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int x = left();
        int y = top();
        int hovered = AbilityFavorites.slotAt(mouseX - x, mouseY - y);
        var ability = AbilityFavorites.get(hovered);
        var font = Minecraft.getInstance().font;
        Component caption = ability == null ? title : AbilityInput.title(ability);
        Component hint = Component.translatable(hovered >= 0 && ability == null
            ? "ability.infinity_gauntlet.favorites_empty" : "ability.infinity_gauntlet.favorites_choose");
        int halfWidth = Math.max(AbilityFavorites.WIDTH / 2, Math.max(font.width(caption), font.width(hint)) / 2);
        graphics.fill(width / 2 - halfWidth - 5, y - 15, width / 2 + halfWidth + 6, y + 42, 0xE014101F);
        graphics.text(font, caption, (width - font.width(caption)) / 2, y - 11, 0xFFF4D68D, true);
        AbilityFavorites.render(graphics, x, y, hovered);
        graphics.text(font, hint, (width - font.width(hint)) / 2, y + 29, 0xFFDEE8F9, true);
    }
}
