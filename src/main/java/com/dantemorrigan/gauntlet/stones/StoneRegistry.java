package com.dantemorrigan.gauntlet.stones;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.Stone;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * World-wide record of the six stones (stored on the overworld). Every stone exists once per world:
 * <ul>
 *   <li><b>found</b> — its source was claimed (for Power: the Orb was taken out of the meteorite), so the source
 *   stops producing it (no more Power meteor showers);</li>
 *   <li><b>released</b> — the loose stone now exists as an item (a second Orb opens empty).</li>
 * </ul>
 * Operator tools ({@code /gauntlet give_stone}) mark both, so they never create a second copy behind the world's back.
 */
public final class StoneRegistry {
	private static final AttachmentType<List<String>> FOUND = AttachmentRegistry.<List<String>>builder()
			.persistent(Codec.STRING.listOf()).buildAndRegister(InfinityGauntlet.id("stones_found"));
	private static final AttachmentType<List<String>> RELEASED = AttachmentRegistry.<List<String>>builder()
			.persistent(Codec.STRING.listOf()).buildAndRegister(InfinityGauntlet.id("stones_released"));
	/** Where the purple meteorite with the Orb lies while nobody has taken it. */
	static final AttachmentType<BlockPos> POWER_SITE = AttachmentRegistry.<BlockPos>builder()
			.persistent(BlockPos.CODEC).buildAndRegister(InfinityGauntlet.id("power_meteorite_site"));
	/** Game time of the next Power meteor shower attempt. */
	static final AttachmentType<Long> NEXT_SHOWER = AttachmentRegistry.<Long>builder()
			.persistent(Codec.LONG).buildAndRegister(InfinityGauntlet.id("power_next_shower"));

	public static boolean found(MinecraftServer server, Stone stone) { return has(server.overworld(), FOUND, stone); }
	public static boolean released(MinecraftServer server, Stone stone) { return has(server.overworld(), RELEASED, stone); }

	public static void markFound(MinecraftServer server, Stone stone) { add(server.overworld(), FOUND, stone); }

	/** Marks the stone as existing; returns false if it already did (the caller must not create another one). */
	public static boolean markReleased(MinecraftServer server, Stone stone) {
		markFound(server, stone);
		return add(server.overworld(), RELEASED, stone);
	}

	/** Last place the Power meteorite's Orb lay (kept after the Orb is taken so it can be restored). */
	static final AttachmentType<BlockPos> POWER_ORB_SITE = AttachmentRegistry.<BlockPos>builder()
			.persistent(BlockPos.CODEC).buildAndRegister(InfinityGauntlet.id("power_orb_site"));

	/** The stone was destroyed (e.g. deleted in creative): the world forgets it and its source starts over. */
	public static void forget(MinecraftServer server, Stone stone) {
		ServerLevel level = server.overworld();
		remove(level, FOUND, stone);
		remove(level, RELEASED, stone);
		StoneSources.restore(server, stone);
	}

	private static void remove(ServerLevel level, AttachmentType<List<String>> type, Stone stone) {
		List<String> list = level.getAttached(type);
		if (list == null || !list.contains(stone.id)) return;
		List<String> next = new ArrayList<>(list);
		next.remove(stone.id);
		level.setAttached(type, next);
	}

	/** Debug: forget everything (stones already in inventories stay where they are). */
	public static void reset(MinecraftServer server) {
		ServerLevel level = server.overworld();
		level.removeAttached(FOUND);
		level.removeAttached(RELEASED);
		level.removeAttached(POWER_SITE);
		level.removeAttached(NEXT_SHOWER);
		TimeQuest.resetClock(server);
		MindQuest.reset(server);
	}

	private static boolean has(ServerLevel level, AttachmentType<List<String>> type, Stone stone) {
		List<String> list = level.getAttached(type);
		return list != null && list.contains(stone.id);
	}

	private static boolean add(ServerLevel level, AttachmentType<List<String>> type, Stone stone) {
		List<String> list = level.getAttached(type);
		if (list != null && list.contains(stone.id)) return false;
		List<String> next = list == null ? new ArrayList<>() : new ArrayList<>(list);
		next.add(stone.id);
		level.setAttached(type, next);
		return true;
	}

	private StoneRegistry() {}
}
