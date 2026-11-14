package com.dantemorrigan.gauntlet.doom;

import com.dantemorrigan.gauntlet.ModAdvancements;
import com.dantemorrigan.gauntlet.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Latveria's court and its weather. The storm over the valley strikes the castle's spires now and then (light and
 * thunder only). Once the castle is taken, its new lord may sit on Doom's throne: the court salutes, and the Doombots
 * in the hall with it.
 */
public final class LatveriaCourt {
	/** The spires the storm strikes: the points of the castle's towers, the central needle (the tallest) most often. */
	private static Vec3[] spires;

	private static Vec3[] spires() {
		if (spires == null) {
			LatveriaPlan.OPS.size(); // the plan is built on first use, and the spires with it
			java.util.List<Vec3> all = new java.util.ArrayList<>();
			for (int[] t : LatveriaCastle.SPIRE_TIPS) {
				all.add(new Vec3(t[0] + 0.5, t[1], t[2] + 0.5));
				if (t[1] > LatveriaPlan.FLOOR + 150) { all.add(new Vec3(t[0] + 0.5, t[1], t[2] + 0.5)); all.add(new Vec3(t[0] + 0.5, t[1], t[2] + 0.5)); }
			}
			spires = all.toArray(new Vec3[0]);
		}
		return spires;
	}
	/** Where the monarch sits: on the gold of the throne, facing the hall. */
	public static final Vec3 SEAT = new Vec3(0.5, LatveriaPlan.FLOOR + 3.05, -290.9);
	public static final String SEAT_TAG = "latveria_throne_seat";
	private static long nextStrike;

	private LatveriaCourt() {}

	static void tick(ServerLevel level) {
		long now = level.getGameTime();
		if (nextStrike == 0) nextStrike = now + 200;
		if (now >= nextStrike) {
			nextStrike = now + 240 + level.getRandom().nextInt(520);
			strike(level, spires()[level.getRandom().nextInt(spires().length)]);
		}
		if (now % 10 == 0) {
			for (ArmorStand seat : level.getEntitiesOfClass(ArmorStand.class, new AABB(BlockPos.containing(SEAT)).inflate(4), s -> s.entityTags().contains(SEAT_TAG))) {
				if (seat.getPassengers().isEmpty()) seat.discard();
			}
		}
	}

	/** A bolt of the storm on a spire: the flash and the thunder of a real one, nothing set alight, nobody hurt. */
	public static void strike(ServerLevel level, Vec3 at) {
		if (!level.isLoaded(BlockPos.containing(at))) return;
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
		if (bolt == null) return;
		bolt.setVisualOnly(true);
		bolt.snapTo(at.x, at.y, at.z);
		level.addFreshEntity(bolt);
	}

	/** A bolt on a spire chosen at random. */
	public static void strikeRandom(ServerLevel level) {
		strike(level, spires()[level.getRandom().nextInt(spires().length)]);
	}

	/** A click on the throne's gold or cloth. */
	public static boolean isThrone(BlockPos p) {
		return p.getX() >= -2 && p.getX() <= 2 && p.getZ() >= -292 && p.getZ() <= -290
				&& p.getY() >= LatveriaPlan.FLOOR + 1 && p.getY() <= LatveriaPlan.FLOOR + 7;
	}

	/** The castle's lord takes the throne. False if someone else may not, or it is already taken. */
	public static boolean sit(ServerPlayer player) {
		ServerLevel level = player.level();
		if (!Latveria.conquered(level.getServer()) || !Latveria.isOwner(player) || player.isPassenger()) return false;
		boolean taken = !level.getEntitiesOfClass(ArmorStand.class, new AABB(BlockPos.containing(SEAT)).inflate(2),
				s -> s.entityTags().contains(SEAT_TAG) && !s.getPassengers().isEmpty()).isEmpty();
		if (taken) return false;
		ArmorStand seat = new ArmorStand(level, SEAT.x, SEAT.y - 1.4, SEAT.z);
		seat.setInvisible(true);
		seat.setNoGravity(true);
		seat.setInvulnerable(true);
		seat.setSilent(true);
		seat.addTag(SEAT_TAG);
		seat.setYRot(0);
		level.addFreshEntity(seat);
		if (!player.startRiding(seat, true, false)) { seat.discard(); return false; }
		player.setYRot(0);
		player.setYHeadRot(0);
		// The court salutes the new monarch.
		// The Doombots form an honour guard down both sides of the dais, facing the aisle, and salute.
		int i = 0;
		for (Doombot bot : level.getEntitiesOfClass(Doombot.class, LatveriaPlan.ARENA_BOX.inflate(24), b -> b.isAlive() && b.allied())) {
			int side = i % 2 == 0 ? -1 : 1, rank = i / 2;
			double x = side * (5.5 + (rank % 2) * 3), z = -284.5 + (rank / 2) * 5;
			bot.teleportTo(x + 0.5, LatveriaPlan.FLOOR, z);
			float yaw = side < 0 ? -90 : 90;
			bot.setYRot(yaw);
			bot.setYBodyRot(yaw);
			bot.setYHeadRot(yaw);
			bot.setGuarding(true);
			bot.salute();
			i++;
		}
		for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(48))) {
			level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1f);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 0.8f);
		for (ServerPlayer p : level.players()) DoomFx.tell(p, "throne.sit", player.getDisplayName());
		ModAdvancements.award(player, "throne_of_latveria");
		return true;
	}

	/** Columns of the ranks either side of the aisle, and the first and last rows (z), spaced evenly. */
	private static final double[] COLUMNS = {4.5, 7.5, 10.5, 13.5};
	private static final double FRONT_Z = -284.5, ROW_GAP = 3.0;

	/**
	 * After the castle falls the Doombots fall in: neat ranks in the throne room, either side of the red-carpet aisle,
	 * facing the throne, the Royal Guard in front and the Bastions behind them, then everyone else. They stand there
	 * and wait; the controller still sends any of them after its master.
	 */
	public static void parade(ServerLevel level, java.util.Collection<Doombot> bots) {
		java.util.List<Doombot> order = new java.util.ArrayList<>(bots);
		int[] rank = {Doombot.ROYAL_GUARD, Doombot.BASTION, Doombot.ARCANIST, Doombot.SOLDIER, Doombot.ENGINEER, Doombot.INTERCEPTOR};
		order.sort(java.util.Comparator.comparingInt((Doombot b) -> {
			for (int i = 0; i < rank.length; i++) if (rank[i] == b.variant()) return i;
			return rank.length;
		}).thenComparing(Doombot::getUUID));
		java.util.List<double[]> slots = new java.util.ArrayList<>();
		for (int row = 0; row < 9; row++) {
			for (double c : COLUMNS) {
				slots.add(new double[]{-c, FRONT_Z + row * ROW_GAP, row * 10 + c});
				slots.add(new double[]{c, FRONT_Z + row * ROW_GAP, row * 10 + c});
			}
		}
		// Front row first, and each row from the aisle outward.
		slots.sort(java.util.Comparator.comparingDouble((double[] a) -> a[2]));
		for (int i = 0; i < order.size() && i < slots.size(); i++) {
			double[] slot = slots.get(i);
			Doombot bot = order.get(i);
			DoomFx.bill(level, ModParticles.DOOM_PHASE_SHIFT, bot.position().add(0, 1, 0), 1.2);
			bot.park(new Vec3(slot[0] + 0.5, LatveriaPlan.FLOOR, slot[1]), 180f);
		}
	}

	public static void clear() { nextStrike = 0; }
}
