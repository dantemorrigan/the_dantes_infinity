package com.dantemorrigan.gauntlet.gametest.trailer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.io.IOException;
import java.util.function.Function;

/** Trailer camera (a client-side marker entity moved every tick, so frames interpolate smoothly) + screen recorder. */
final class Rig {
	/** Camera pose at tick t: x, y, z, yaw, pitch. */
	interface Path { double[] at(double t); }

	static volatile Path path;
	static volatile double t;
	private static Marker cam;
	private static boolean installed;
	private static boolean fresh;
	private static RemotePlayer body;
	static volatile boolean showBody = true;

	/** The local player is never drawn when the camera is elsewhere, so a client-side twin stands in for it. */
	private static void syncBody(Minecraft mc) {
		var pl = mc.player;
		if (pl == null) return;
		if (!showBody) { if (body != null) { body.discard(); body = null; } return; }
		if (body == null || body.level() != mc.level || body.isRemoved()) {
			body = new RemotePlayer(mc.level, pl.getGameProfile());
			body.setPos(pl.getX(), pl.getY(), pl.getZ());
			mc.level.addEntity(body);
			body.setOldPosAndRot();
		}
		body.setOldPosAndRot();
		body.setPos(pl.getX(), pl.getY(), pl.getZ());
		body.setYRot(pl.getYRot());
		body.setXRot(pl.getXRot());
		body.yBodyRot = pl.yBodyRot;
		body.yHeadRot = pl.yHeadRot;
		body.setPose(pl.getPose());
		body.setItemSlot(EquipmentSlot.MAINHAND, pl.getMainHandItem().copy());
		body.setItemSlot(EquipmentSlot.OFFHAND, pl.getOffhandItem().copy());
		for (EquipmentSlot es : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
			body.setItemSlot(es, pl.getItemBySlot(es).copy());
		body.walkAnimation.update(pl.walkAnimation.speed(), 1f, 1f);
	}

	static void install() {
		if (installed) return;
		installed = true;
		ClientTickEvents.START_CLIENT_TICK.register(Rig::tick);
	}

	static void set(Path p) { t = 0; fresh = true; path = p; }

	private static volatile boolean release;

	static void off() { path = null; release = true; }

	private static void tick(Minecraft mc) {
		Path p = path;
		if (release && p == null) {
			release = false;
			if (mc.player != null) mc.setCameraEntity(mc.player);
		}
		if (p == null || mc.level == null) {
			if (body != null) { body.discard(); body = null; }
			return;
		}
		syncBody(mc);
		if (cam == null || cam.level() != mc.level || cam.isRemoved()) {
			cam = new Marker(EntityType.MARKER, mc.level);
			double[] a = p.at(0);
			cam.setPos(a[0], a[1], a[2]);
			mc.level.addEntity(cam);
			fresh = true;
		}
		if (mc.getCameraEntity() != cam) mc.setCameraEntity(cam);
		double[] a = p.at(t);
		t += 1;
		cam.setOldPosAndRot();
		cam.setPos(a[0], a[1], a[2]);
		cam.setYRot((float) a[3]);
		cam.setXRot((float) a[4]);
		if (fresh) { fresh = false; cam.setOldPosAndRot(); }
	}

	// ---- path builders --------------------------------------------------------------------------------------------
	static double ease(double u) { u = Math.max(0, Math.min(1, u)); return u * u * (3 - 2 * u); }
	static double lerp(double a, double b, double u) { return a + (b - a) * u; }

	static double[] look(Vec3 from, Vec3 to) {
		double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
		double h = Math.sqrt(dx * dx + dz * dz);
		return new double[]{Math.toDegrees(Math.atan2(-dx, dz)), -Math.toDegrees(Math.atan2(dy, h))};
	}

	static Path make(Function<Double, Vec3> pos, Function<Double, Vec3> target) {
		return u -> {
			Vec3 p = pos.apply(u), q = target.apply(u);
			double[] yp = look(p, q);
			// a breath of handheld motion
			double s = u * .05;
			return new double[]{p.x + Math.sin(s * 1.7) * .04, p.y + Math.sin(s * 2.3) * .04, p.z + Math.cos(s * 1.3) * .04,
					yp[0] + Math.sin(s * 1.1) * .18, yp[1] + Math.sin(s * 1.9) * .12};
		};
	}

	/** Straight move with eased progress, looking from one target to another. */
	static Path track(Vec3 a, Vec3 b, Vec3 la, Vec3 lb, int dur, boolean eased) {
		return make(u -> a.lerp(b, prog(u, dur, eased)), u -> la.lerp(lb, prog(u, dur, eased)));
	}

	static Path hold(Vec3 a, Vec3 look) { return make(u -> a, u -> look); }

	private static double prog(double u, int dur, boolean eased) {
		double k = Math.max(0, Math.min(1, u / dur));
		return eased ? ease(k) : k;
	}

	/** Orbit around (cx,cz) at radius r (degrees a0 -> a1), height from h0 -> h1, looking at target (follows a lerp). */
	static Path orbit(Vec3 c, double r0, double r1, double h0, double h1, double a0, double a1, Vec3 look, int dur, boolean eased) {
		return make(u -> {
			double k = prog(u, dur, eased), a = Math.toRadians(lerp(a0, a1, k)), r = lerp(r0, r1, k);
			return new Vec3(c.x + Math.sin(a) * r, c.y + lerp(h0, h1, k), c.z + Math.cos(a) * r);
		}, u -> look);
	}

	// ---- recorder -------------------------------------------------------------------------------------------------
	private static Process ff;
	private static long ffStart;

	static void record(String file, int w, int h) {
		try {
			new File(file).getParentFile().mkdirs();
			ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-y", "-loglevel", "error", "-f", "x11grab", "-framerate", "30",
					"-video_size", w + "x" + h, "-draw_mouse", "0", "-i", System.getenv().getOrDefault("DISPLAY", ":99") + ".0",
					"-c:v", "libx264", "-preset", "ultrafast", "-crf", "14", "-pix_fmt", "yuv420p", file);
			pb.redirectErrorStream(true);
			pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
			ffStart = System.currentTimeMillis();
			ff = pb.start();
			Thread.sleep(1200);
			System.out.println("CLIP " + file + " start=" + (System.currentTimeMillis() - ffStart));
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	static void stop() {
		if (ff == null) return;
		try {
			ff.getOutputStream().write("q\n".getBytes());
			ff.getOutputStream().flush();
			ff.waitFor();
		} catch (Exception e) {
			ff.destroy();
		}
		ff = null;
	}
}
