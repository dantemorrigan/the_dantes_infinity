package com.dantemorrigan.gauntlet.net;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Mod notices have their own HUD channel instead of competing with vanilla's action bar. */
public final class ModStatus {
    public static void send(ServerPlayer player, Component message) {
        ServerPlayNetworking.send(player, new ModNetwork.Status(message));
    }
    private ModStatus() {}
}
