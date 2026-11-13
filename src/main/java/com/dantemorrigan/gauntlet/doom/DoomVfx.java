package com.dantemorrigan.gauntlet.doom;

import com.dantemorrigan.gauntlet.net.ModNetwork;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Doom's world effects, sent once and played by the client: bolts that fly and steer on their own, impacts,
 * rift tears, the Iron Bastion shell and the jets that follow their wearer, orbiting suns, the Edict's circle and
 * column, ground waves and a charge glow in the hand.
 */
public final class DoomVfx {
	public static final int BOLT = 1, BOLT_END = 2, IMPACT = 3, RIFT = 4, SHIELD = 5, JET = 6, ORBIT = 7, EDICT = 8, COLUMN = 9,
			SLAM = 10, CHARGE = 11, TRAIL = 12, SHIELD_HIT = 13, PREVIEW_OFF = 14;
	/** Colour styles: the gauntlet's gold, sorcery green, the suns' white-gold, the Edict's emerald, the Engineer's teal. */
	public static final int GOLD = 0, GREEN = 1, SUN = 2, EDICT_GREEN = 3, TEAL = 4;
	private static final double RANGE = 128;
	private static int nextId = 1;

	public static int nextId() {
		return nextId++;
	}

	private static void send(ServerLevel level, Vec3 near, ModNetwork.DoomVfx fx) {
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(near) <= RANGE * RANGE && ServerPlayNetworking.canSend(p, ModNetwork.DoomVfx.TYPE)) ServerPlayNetworking.send(p, fx);
		}
	}

	public static void bolt(ServerLevel level, int id, Entity owner, Entity target, Vec3 from, Vec3 velocity, float size, float turn, int life, int style) {
		send(level, from, new ModNetwork.DoomVfx(BOLT, id, owner == null ? -1 : owner.getId(), target == null ? -1 : target.getId(), from, velocity, size, turn, life, style));
	}

	public static void boltEnd(ServerLevel level, int id, Vec3 at, float size, int style) {
		send(level, at, new ModNetwork.DoomVfx(BOLT_END, id, -1, -1, at, Vec3.ZERO, size, 0, 0, style));
	}

	public static void at(ServerLevel level, int kind, Vec3 at, float size, int ticks, int style) {
		send(level, at, new ModNetwork.DoomVfx(kind, 0, -1, -1, at, Vec3.ZERO, size, 0, ticks, style));
	}

	public static void line(ServerLevel level, int kind, Vec3 from, Vec3 to, float size, int ticks, int style) {
		send(level, from, new ModNetwork.DoomVfx(kind, 0, -1, -1, from, to, size, 0, ticks, style));
	}

	public static void on(ServerLevel level, int kind, Entity entity, float size, int ticks, int style) {
		send(level, entity.position(), new ModNetwork.DoomVfx(kind, 0, entity.getId(), -1, entity.position(), Vec3.ZERO, size, 0, ticks, style));
	}

	private DoomVfx() {}
}
