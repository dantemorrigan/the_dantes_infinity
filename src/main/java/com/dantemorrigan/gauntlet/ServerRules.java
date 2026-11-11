package com.dantemorrigan.gauntlet;

import com.dantemorrigan.gauntlet.net.ModNetwork;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The server's values of the rules the client needs for its own visuals and local block changes. The client must
 * read them here, never from its own config file: on a dedicated server the two files differ. The server sends
 * them on join and after {@code /gauntlet reload_config}; off a server they fall back to the local config.
 */
public final class ServerRules {
	public static volatile boolean abilityBlockChanges;
	public static volatile int realityRadius;
	public static volatile int realityMaxBlocks;
	public static volatile int redSkyRadius;
	public static volatile int redSkyFalloff;
	public static volatile float celestialAimSeconds;
	public static volatile double soulHealthPerSoul;
	public static volatile double soulDamagePerSoul;

	static {
		resetToLocal();
	}

	public static void init() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer()));
	}

	private static ModNetwork.RulesSync payload() {
		GauntletConfig c = GauntletConfig.get();
		return new ModNetwork.RulesSync(c.abilityBlockChanges, c.realityRadius, c.realityMaxBlocks, c.redSkyRadius, c.redSkyFalloff,
				c.celestialAimSeconds, c.soulHealthPerSoul, c.soulDamagePerSoul);
	}

	private static void send(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, ModNetwork.RulesSync.TYPE)) ServerPlayNetworking.send(player, payload());
	}

	/** After the config was reloaded on the server. */
	public static void broadcast(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) send(player);
	}

	/** Client: the server's rules arrived. */
	public static void apply(ModNetwork.RulesSync r) {
		abilityBlockChanges = r.abilityBlockChanges();
		realityRadius = r.realityRadius();
		realityMaxBlocks = r.realityMaxBlocks();
		redSkyRadius = r.redSkyRadius();
		redSkyFalloff = r.redSkyFalloff();
		celestialAimSeconds = r.celestialAimSeconds();
		soulHealthPerSoul = r.soulHealthPerSoul();
		soulDamagePerSoul = r.soulDamagePerSoul();
	}

	/** Client: left the server, so the local file is all there is. */
	public static void resetToLocal() {
		apply(payload());
	}

	private ServerRules() {}
}
