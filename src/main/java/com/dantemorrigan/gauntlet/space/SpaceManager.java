package com.dantemorrigan.gauntlet.space;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.net.ModNetwork;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Dead Space: an empty dimension the participants are moved to after the planet is destroyed.
 * The original dimension is untouched; the {@link SpaceRecord} attachment marks a player as "post-destruction",
 * so respawn/relog bring them back here until the debug return command clears it.
 */
public final class SpaceManager {
	public static final ResourceKey<Level> DEAD_SPACE = ResourceKey.create(Registries.DIMENSION, InfinityGauntlet.id("dead_space"));
	/** Scene center; the planet and debris are placed relative to it on the client. */
	public static final Vec3 CENTER = new Vec3(0.5, 128.0, 0.5);

	public static final AttachmentType<SpaceRecord> RECORD = AttachmentRegistry.<SpaceRecord>builder()
			.persistent(SpaceRecord.CODEC)
			.copyOnDeath()
			.buildAndRegister(InfinityGauntlet.id("space_record"));

	private static final AttachmentType<List<String>> REVERSED = AttachmentRegistry.<List<String>>builder()
			.persistent(Codec.STRING.listOf()).buildAndRegister(InfinityGauntlet.id("reversed_planets"));

	private static String eventKey(SpaceRecord record) {
		return record.returnDimension().toString() + "/" + record.eventStartTime() + "/" + record.seed();
	}

	public static boolean sameEvent(SpaceRecord first, SpaceRecord second) {
		return eventKey(first).equals(eventKey(second));
	}

	public static void markReversed(MinecraftServer server, SpaceRecord record) {
		List<String> old = server.overworld().getAttached(REVERSED);
		String key = eventKey(record);
		if (old != null && old.contains(key)) return;
		List<String> events = old == null ? new ArrayList<>() : new ArrayList<>(old);
		events.add(key);
		server.overworld().setAttached(REVERSED, events);
	}

	private static boolean reversed(MinecraftServer server, SpaceRecord record) {
		List<String> events = server.overworld().getAttached(REVERSED);
		return events != null && events.contains(eventKey(record));
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SpaceManager::tick);
		// No fall / void damage in Dead Space.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && inSpace(player)
					&& (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && player.getY() < 16)) {
				return false;
			}
			return true;
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (newPlayer.hasAttached(RECORD)) sendToSpace(newPlayer, newPlayer.getAttached(RECORD), 9999);
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			SpaceRecord record = player.getAttached(RECORD);
			if (record != null) {
				if (reversed(server, record)) {
					returnFromSpace(player);
					return;
				}
				if (record.returnDimension().equals(Level.OVERWORLD))
					com.dantemorrigan.gauntlet.celestial.CelestialApocalypse.stop(server);
				if (!inSpace(player)) sendToSpace(player, record, ageOf(server, record));
				else ServerPlayNetworking.send(player, new ModNetwork.SpaceState(record, ageOf(server, record)));
			} else if (inSpace(player)) {
				// In Dead Space without a record (e.g. teleported by an admin): still keep them safe.
				player.setNoGravity(true);
				player.addTag(NO_GRAVITY_TAG);
			}
		});
	}

	public static boolean inSpace(ServerPlayer player) {
		return player.level().dimension().equals(DEAD_SPACE);
	}

	public static int ageOf(MinecraftServer server, SpaceRecord record) {
		long age = server.overworld().getGameTime() - record.eventStartTime();
		return (int) Math.max(0, Math.min(Integer.MAX_VALUE, age));
	}

	/** Move the player into Dead Space and tell the client to build the scene. */
	public static boolean sendToSpace(ServerPlayer player, SpaceRecord record, int eventAge) {
		ServerLevel space = player.level().getServer().getLevel(DEAD_SPACE);
		if (space == null) {
			InfinityGauntlet.LOGGER.error("Dead Space dimension is missing (datapack not loaded?)");
			return false;
		}
		player.setAttached(RECORD, record);
		if (player.level().dimension().equals(Level.OVERWORLD))
			com.dantemorrigan.gauntlet.celestial.CelestialApocalypse.stop(player.level().getServer());
		player.fallDistance = 0;
		player.setNoGravity(true);
		player.addTag(NO_GRAVITY_TAG);
		Vec3 offset = new Vec3((player.getRandom().nextDouble() - 0.5) * 6, 0, (player.getRandom().nextDouble() - 0.5) * 6);
		player.teleport(new TeleportTransition(space, CENTER.add(offset), Vec3.ZERO, 180f, 0f, TeleportTransition.DO_NOTHING));
		ServerPlayNetworking.send(player, new ModNetwork.SpaceState(record, eventAge));
		return true;
	}

	/** Debug return: restore the player to normal play in their original dimension. */
	public static boolean returnFromSpace(ServerPlayer player) {
		SpaceRecord record = player.removeAttached(RECORD);
		MinecraftServer server = player.level().getServer();
		ServerLevel target = record != null ? server.getLevel(record.returnDimension()) : null;
		Vec3 pos = record != null ? record.returnPos() : null;
		if (target == null) {
			target = server.overworld();
			pos = Vec3.atBottomCenterOf(target.getRespawnData().pos());
		}
		player.setNoGravity(false);
		player.removeTag(NO_GRAVITY_TAG);
		player.fallDistance = 0;
		player.setDeltaMovement(Vec3.ZERO);
		player.teleport(new TeleportTransition(target, pos, Vec3.ZERO, player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
		ServerPlayNetworking.send(player, new ModNetwork.SpaceLeave());
		return record != null;
	}

	private static void tick(MinecraftServer server) {
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		for (ServerPlayer player : players) {
			boolean space = inSpace(player);
			if (space) {
				if (!player.isNoGravity()) player.setNoGravity(true);
				player.addTag(NO_GRAVITY_TAG);
				player.fallDistance = 0;
				// Soft safety net: never let anyone drift into the void.
				if (player.getY() < 8 || player.getY() > 250 || player.position().horizontalDistance() > 600) {
					player.teleport(new TeleportTransition(player.level(), CENTER, Vec3.ZERO, player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
				}
			} else {
				SpaceRecord record = player.getAttached(RECORD);
				if (record != null && player.isAlive() && player.tickCount > 20) {
					// The old world is "destroyed" for this player: any other exit brings them back.
					sendToSpace(player, record, ageOf(server, record));
				} else if (record == null && player.entityTags().contains(NO_GRAVITY_TAG)) {
					player.setNoGravity(false);
					player.removeTag(NO_GRAVITY_TAG);
				}
			}
		}
	}

	static final String NO_GRAVITY_TAG = "infinity_gauntlet_space";

	private SpaceManager() {}
}
