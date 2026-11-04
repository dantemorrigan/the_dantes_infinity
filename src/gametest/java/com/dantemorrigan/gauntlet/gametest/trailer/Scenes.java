package com.dantemorrigan.gauntlet.gametest.trailer;

import com.dantemorrigan.gauntlet.ability.AbilityController;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import static com.dantemorrigan.gauntlet.gametest.trailer.Shoot.*;

final class Scenes {
	private Scenes() {}

	static Vec3 playerPos() {
		return server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().position());
	}

	static int ground(double x, double z) {
		return server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z)));
	}

	/** Nearest dry, flat, solid ground to (x,z). */
	static double[] land(double x0, double z0) {
		return server.computeOnServer(s -> {
			var lvl = s.overworld();
			for (int r = 0; r < 400; r += 6) {
				for (int k = 0; k < Math.max(1, r / 3); k++) {
					double a = k * Math.PI * 2 / Math.max(1, r / 3);
					int x = (int) (x0 + Math.cos(a) * r), z = (int) (z0 + Math.sin(a) * r);
					lvl.getChunk(x >> 4, z >> 4);
					int g = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
					var below = lvl.getBlockState(new net.minecraft.core.BlockPos(x, g - 1, z));
					if (!below.getFluidState().isEmpty() || below.is(net.minecraft.world.level.block.Blocks.ICE) || below.is(net.minecraft.world.level.block.Blocks.PACKED_ICE)
							|| below.is(net.minecraft.world.level.block.Blocks.BLUE_ICE) || below.isAir()) continue;
					boolean flat = true;
					for (int dx = -6; dx <= 6 && flat; dx += 6) for (int dz = -6; dz <= 6; dz += 6) {
						lvl.getChunk((x + dx) >> 4, (z + dz) >> 4);
						int h = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
						if (Math.abs(h - g) > 2 || !lvl.getBlockState(new net.minecraft.core.BlockPos(x + dx, h - 1, z + dz)).getFluidState().isEmpty()) { flat = false; break; }
					}
					if (flat) return new double[]{x + .5, z + .5};
				}
			}
			return new double[]{x0, z0};
		});
	}

	/** Stand the player on the ground at (x,z), looking along yaw/pitch. Returns the feet position. */
	static Vec3 stage(double x, double z, float yaw, float pitch) {
		double[] l = land(x, z);
		x = l[0]; z = l[1];
		raw("forceload add " + (int) x + " " + (int) z);
		raw("tp @p " + x + " " + (ground(x, z) + 60) + " " + z);
		ticks(30);
		int g = ground(x, z);
		raw("tp @p " + x + " " + g + " " + z + " " + yaw + " " + pitch);
		ticks(10);
		return new Vec3(x, g, z);
	}

	static void aim(float yaw, float pitch) {
		cmd("tp @s ~ ~ ~ " + yaw + " " + pitch);
	}

	static void gauntlet() {
		cmd("gauntlet give_power");
		for (String s : new String[]{"space", "reality", "soul", "mind", "time"}) cmd("gauntlet give_stone " + s);
	}

	static void cast(String ability) {
		server.runOnServer(s -> { var p = s.getPlayerList().getPlayers().getFirst(); AbilityController.select(p, ability, -1); });
		ticks(4);
		server.runOnServer(s -> AbilityController.action(s.getPlayerList().getPlayers().getFirst(), 0));
	}

	static void release() {
		server.runOnServer(s -> AbilityController.action(s.getPlayerList().getPlayers().getFirst(), 1));
	}

	static void begin(String name) { Rig.record(out + "/" + name + ".mkv", W, H); }

	static void end() { Rig.off(); Rig.stop(); }

	static Vec3 eye(Vec3 feet) { return feet.add(0, 1.6, 0); }

	// ---------------------------------------------------------------------------------------------------------------
	static void scout() {
		raw("time set 13000");
		Vec3 p = playerPos();
		System.out.println("SCOUT spawn " + p);
	}

	/** Night, meteors streaking across the sky. */
	static void intro() {
		Vec3 feet = stage(0, -380, 0, -5);
		raw("time set 15000");
		com.dantemorrigan.gauntlet.GauntletConfig.get().powerShowerSeconds = 24;
		com.dantemorrigan.gauntlet.GauntletConfig.get().powerShowerMeteorsPerSecond = 4f;
		cmd("gauntlet stones reset");
		cmd("gauntlet power_shower");
		ticks(20);
		Vec3 c = eye(feet);
		var ctr = com.dantemorrigan.gauntlet.stones.PowerStoneFall.showerCenter();
		Vec3 target = ctr == null ? c.add(0, 5, 60) : new Vec3(ctr.getX(), ctr.getY() + 12, ctr.getZ());
		System.out.println("INTRO shower " + ctr);
		begin("intro");
		Rig.set(Rig.track(c.add(0, 0.5, 0), c.add(0, 4, 0), target.add(0, 0, 0), target.add(0, 18, 0), 480, true));
		ticks(500);
		ctx.takeScreenshot("trailer_intro");
		end();
	}

	/** Close-ups of the gauntlet while the stones light up. */
	static void gauntletShot() {
		Vec3 feet = stage(60, -380, 20, 0);
		raw("time set 18000");
		cmd("gauntlet give_power");
		ticks(40);
		begin("gauntlet");
		Vec3 c = eye(feet);
		Rig.set(Rig.orbit(c.add(0, -0.3, 0), 2.4, 1.4, 0.3, 0.1, 200, 130, c.add(0, -0.35, 0), 100, true));
		ticks(20);
		for (String s : new String[]{"space", "reality", "soul", "mind", "time"}) { cmd("gauntlet give_stone " + s); ticks(14); }
		ticks(60);
		ctx.takeScreenshot("trailer_gauntlet");
		end();
	}

	static void power() {
		Vec3 feet = stage(-60, -390, 0, 8);
		raw("time set 13500");
		gauntlet();
		ticks(100);
		begin("power");
		Vec3 c = eye(feet);
		Rig.set(Rig.track(c.add(-4, 0.2, 7), c.add(4, 1.5, 7), c.add(0, 0.2, 0), c.add(0, 0.2, 0), 130, true));
		ticks(10);
		cast("power_strike");
		ticks(45);
		release();
		ticks(80);
		ctx.takeScreenshot("trailer_power");
		end();
	}

	static void beam() {
		Vec3 feet = stage(90, -380, 180, -8);
		raw("time set 13500");
		gauntlet();
		ticks(100);
		begin("beam");
		Vec3 c = eye(feet);
		Rig.set(Rig.track(c.add(-5, 0.2, -6), c.add(5, 1.6, -6), c.add(0, 0.2, 0), c.add(0, 0.6, 8), 170, true));
		ticks(10);
		cast("infinity_beam");
		ticks(150);
		ctx.takeScreenshot("trailer_beam");
		release();
		end();
	}

	// ---------------------------------------------------------------------------------------------------------------
	static volatile Vec3 focus = Vec3.ZERO;

	static com.dantemorrigan.gauntlet.boss.Thanos thanos(net.minecraft.server.MinecraftServer s) {
		for (var lvl : s.getAllLevels()) {
			var l = lvl.getEntitiesOfClass(com.dantemorrigan.gauntlet.boss.Thanos.class, new net.minecraft.world.phys.AABB(-3e4, -64, -3e4, 3e4, 400, 3e4));
			if (!l.isEmpty()) return l.getFirst();
		}
		return null;
	}

	static Vec3 thanosPos() {
		return server.computeOnServer(s -> { var t = thanos(s); return t == null ? null : t.position(); });
	}

	static void planet() {
		Vec3 feet = stage(0, -380, 0, 0);
		raw("time set 6000");
		com.dantemorrigan.gauntlet.GauntletConfig.get().planetDestructionAllPlayers = true;
		cmd("gauntlet give_power");
		ticks(20);
		server.runOnServer(s -> com.dantemorrigan.gauntlet.space.PlanetDestruction.start(s.getPlayerList().getPlayers().getFirst(), s.getPlayerList().getPlayers().getFirst().position()));
		ctx.waitFor(mc -> mc.level != null && mc.level.dimension().equals(com.dantemorrigan.gauntlet.space.SpaceManager.DEAD_SPACE), 400);
		ticks(10);
		Vec3 p = playerPos();
		System.out.println("PLANET player in space at " + p);
		begin("planet");
		Vec3 c = eye(p);
		// The planet sits toward yaw 180 (north, -z).
		Vec3 far = c.add(0, -2, -120);
		Rig.set(Rig.track(c.add(6, 1, 9), c.add(-7, 3, 6), far, far.add(0, 5, 0), 380, true));
		ticks(370);
		ctx.takeScreenshot("trailer_planet");
		end();
	}

	static void snap() {
		Vec3 feet = stage(-120, -380, 0, 0);
		raw("time set 14000");
		gauntlet();
		ticks(100);
		begin("snap");
		Vec3 c = eye(feet);
		Rig.set(Rig.track(c.add(0, -0.3, -4), c.add(0.4, 0.3, -1.8), c.add(0, -0.2, 0), c.add(0.3, -0.4, 0), 70, true));
		ticks(5);
		cast("infinity_snap");
		ticks(160);
		ctx.takeScreenshot("trailer_snap");
		end();
	}

	/** Thanos: arrival, the fight, the fall and the crash. */
	static void thanosScenes() {
		Vec3 feet = stage(40, -380, 0, -4);
		raw("time set 5000");
		cmd("gauntlet thanos reset");
		ticks(20);
		cmd("gauntlet thanos now");
		Vec3 c = eye(feet);
		// ship hovers ~70 ahead (z+) and ~78 up; Thanos lands in between
		Vec3 ship = c.add(0, 78, 70);
		begin("thanos_arrive");
		Rig.set(Rig.track(c.add(4, -0.3, -9), c.add(-3, 6, -4), c.add(0, 40, 70), ship.add(0, -6, 0), 330, true));
		ticks(335);
		Rig.set(Rig.track(c.add(-3, 6, -4), c.add(-3, 1, 6), ship.add(0, -6, 0), c.add(0, 1, 40), 100, true));
		ticks(100);
		Vec3 t = thanosPos();
		System.out.println("THANOS landed at " + t);
		if (t != null) {
			focus = t.add(0, 3, 0);
			Rig.set(Rig.make(u -> new Vec3(focus.x + Math.sin(u * .02) * 14, focus.y - 1, focus.z - 13 + Math.cos(u * .02) * 4), u -> focus));
			ticks(130);
		}
		ctx.takeScreenshot("trailer_thanos_land");
		end();

		// the fight
		cmd("gamemode survival");
		cmd("effect give @s resistance 90 4 true");
		begin("thanos_fight");
		for (int i = 0; i < 12; i++) {
			Vec3 tp = thanosPos();
			if (tp != null) focus = tp.add(0, 2.5, 0);
			final int k = i;
			Rig.set(Rig.make(u -> new Vec3(focus.x + 11 * Math.sin((k * 20 + u) * .03), focus.y - .5, focus.z + 11 * Math.cos((k * 20 + u) * .03)), u -> focus));
			ticks(20);
		}
		ctx.takeScreenshot("trailer_thanos_fight");
		end();

		// the fall
		cmd("gamemode creative");
		server.runOnServer(s -> {
			var th = thanos(s);
			if (th == null) return;
			th.invulnerableTime = 0;
			th.setHealth(1);
			th.hurtServer((net.minecraft.server.level.ServerLevel) th.level(), th.level().damageSources().explosion(null, null), 50);
		});
		Vec3 tp = thanosPos();
		if (tp != null) focus = tp.add(0, 1.5, 0);
		begin("thanos_death");
		Rig.set(Rig.make(u -> new Vec3(focus.x + 5, focus.y, focus.z - 6 + u * .01), u -> focus));
		ticks(250);
		ctx.takeScreenshot("trailer_thanos_dust");
		end();

		// the ship crashes
		begin("thanos_crash");
		Vec3 pc = playerPos();
		Rig.set(Rig.track(pc.add(0, 3, -4), pc.add(-6, 8, -2), pc.add(0, 30, 65), pc.add(0, 4, 60), 360, true));
		ticks(380);
		ctx.takeScreenshot("trailer_crash");
		end();
	}
}
