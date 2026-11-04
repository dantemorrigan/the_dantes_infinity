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

	/** Stand the player on the ground at (x,z), looking along yaw/pitch. Returns the feet position. */
	static Vec3 stage(double x, double z, float yaw, float pitch) {
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
		raw("time set 15500");
		gauntlet();
		ticks(60);
		cmd("gauntlet power_shower");
		ticks(120);
		begin("intro");
		Vec3 c = eye(feet);
		Rig.set(Rig.track(c.add(0, -0.5, 12), c.add(0, 3, 4), c.add(0, 40, -40), c.add(0, 70, -40), 220, true));
		ticks(230);
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
		Rig.set(Rig.orbit(c.add(0, -0.3, 0), 1.6, 1.1, 0.2, 0.1, 120, 20, c.add(0, -0.45, 0), 100, true));
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
		Rig.set(Rig.track(c.add(7, 0.5, 8), c.add(-5, 2, 9), c.add(0, 0.5, 0), c.add(0, 0.5, 0), 130, true));
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
		Rig.set(Rig.track(c.add(-6, 0.2, -8), c.add(6, 2.5, -9), c.add(0, 0.5, 0), c.add(0, 0.8, 6), 170, true));
		ticks(10);
		cast("infinity_beam");
		ticks(150);
		ctx.takeScreenshot("trailer_beam");
		release();
		end();
	}
}
