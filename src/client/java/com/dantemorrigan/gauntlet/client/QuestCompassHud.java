package com.dantemorrigan.gauntlet.client;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.client.ability.AbilityFavoritesScreen;
import com.dantemorrigan.gauntlet.client.ability.AbilityWheelScreen;
import com.dantemorrigan.gauntlet.stormbreaker.StormNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Reads the same world coordinates that vanilla uses to draw the red X on a quest map. */
final class QuestCompassHud {
    private static final int ACCENT = 0xFFE8B75F;
    private static final int STORM_ACCENT = 0xFF65CAFF;
    private static Skyfall skyfall;

    private record Skyfall(ClientLevel level, Vec3 site, long expires) {}
    private static final double ARRIVAL_RADIUS = 12.0;

    private record Target(String nameKey, double x, double z) {
        double distanceFrom(LocalPlayer player) {
            return Math.hypot(x - player.getX(), z - player.getZ());
        }
    }

    static void init() {
        ClientPlayNetworking.registerGlobalReceiver(StormNetwork.Compass.TYPE, (packet, context) -> {
            ClientLevel level = context.client().level;
            skyfall = packet.active() && level != null && level.dimension() == Level.OVERWORLD
                    ? new Skyfall(level, packet.site(), level.getGameTime() + 35) : null;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> skyfall = null);
        HudElementRegistry.addLast(InfinityGauntlet.id("quest_compass"), (graphics, delta) -> render(graphics));
    }

    private static void render(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.level.dimension() != Level.OVERWORLD
                || mc.options.hideGui || mc.screen instanceof AbilityFavoritesScreen
                || mc.screen instanceof AbilityWheelScreen) return;

        Target target = selectTarget(player);
        int y = 25;
        if (target != null && target.distanceFrom(player) > ARRIVAL_RADIUS) {
            renderTarget(graphics, mc, player, target, y, ACCENT);
            y += 24;
        }

        Skyfall current = skyfall;
        if (current == null) return;
        if (current.level != mc.level || mc.level.getGameTime() >= current.expires) {
            skyfall = null;
            return;
        }
        if (player.isSpectator() || player.position().distanceToSqr(current.site)
                > StormNetwork.COMPASS_RANGE * StormNetwork.COMPASS_RANGE) return;
        renderTarget(graphics, mc, player,
                new Target("item.infinity_gauntlet.stormbreaker", current.site.x, current.site.z), y, STORM_ACCENT);
    }

    private static void renderTarget(GuiGraphicsExtractor graphics, Minecraft mc, LocalPlayer player,
            Target target, int y, int accent) {
        double distance = target.distanceFrom(player);
        // Minecraft yaw starts at south (+Z) and increases toward west (-X).
        double bearing = Math.atan2(player.getX() - target.x(), target.z() - player.getZ());
        float relative = (float) (bearing - Math.toRadians(player.getYRot()));
        Component label = Component.translatable("hud.infinity_gauntlet.quest_compass.distance",
                Component.translatable(target.nameKey()), Math.round(distance));
        int width = mc.font.width(label) + 30;
        int x = (graphics.guiWidth() - width) / 2;
        graphics.fill(x, y, x + width, y + 20, 0xD014101F);
        graphics.text(mc.font, label, x + 24, y + 6, 0xFFF4EFFA, true);

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x + 12, y + 10);
        pose.rotate(relative);
        graphics.fill(-1, -5, 1, 6, accent);
        graphics.fill(-2, -4, 2, -2, accent);
        graphics.fill(-4, -2, 4, 0, accent);
        pose.popMatrix();
    }

    private static Target selectTarget(LocalPlayer player) {
        Target held = readTarget(player.getMainHandItem());
        if (held == null) held = readTarget(player.getOffhandItem());
        if (held != null) return held;

        Target nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            Target candidate = readTarget(inventory.getItem(slot));
            if (candidate == null) continue;
            double distance = candidate.distanceFrom(player);
            if (distance > ARRIVAL_RADIUS && distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static Target readTarget(ItemStack stack) {
        if (!stack.is(Items.FILLED_MAP)) return null;
        Component name = stack.get(DataComponents.ITEM_NAME);
        if (name == null || !(name.getContents() instanceof TranslatableContents translated)) return null;

        String targetName = switch (translated.getKey()) {
            case "item.infinity_gauntlet.quest_time_temple_map" -> "hud.infinity_gauntlet.quest_compass.time_temple";
            case "item.infinity_gauntlet.quest_soul_temple_map" -> "hud.infinity_gauntlet.quest_compass.soul_temple";
            case "item.infinity_gauntlet.quest_mind_city_map" -> "hud.infinity_gauntlet.quest_compass.mind_city";
            default -> null;
        };
        if (targetName == null) return null;

        MapDecorations decorations = stack.get(DataComponents.MAP_DECORATIONS);
        if (decorations == null) return null;
        MapDecorations.Entry marker = decorations.decorations().get("+");
        return marker == null ? null : new Target(targetName, marker.x(), marker.z());
    }

    private QuestCompassHud() {}
}
