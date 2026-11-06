package com.dantemorrigan.gauntlet.client.cosmos;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.client.space.PlanetGeometry;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import java.util.*;
import java.util.concurrent.*;

/**
 * Static procedurally painted maps for the whole Solar System. Painting runs on two background threads (never on the
 * render thread), the finished pixels are uploaded once; meshes are cached unit spheres in three levels of detail.
 */
final class CosmosPlanets {
    static final int W = 1024, H = 512;
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> { var t = new Thread(r, "cosmos-planet-paint"); t.setDaemon(true); return t; });
    private static final Map<String, CompletableFuture<int[]>> PENDING = new HashMap<>();
    private static final Map<String, Identifier> READY = new HashMap<>();

    /** Texture of a body, or null while it is still being painted in the background. */
    static Identifier texture(String id) {
        Identifier ready = READY.get(id);
        if (ready != null) return ready;
        var future = PENDING.computeIfAbsent(id, k -> CompletableFuture.supplyAsync(() -> paint(k), POOL));
        if (!future.isDone()) return null;
        PENDING.remove(id);
        try {
            int[] px = future.get(); boolean ring = id.equals("saturn_ring");
            int w = ring ? 256 : W, h = ring ? 2 : H;
            NativeImage img = new NativeImage(w, h, false);
            for (int i = 0; i < px.length; i++) img.setPixel(i % w, i / w, px[i]);
            Identifier tid = InfinityGauntlet.id("dynamic/cosmos_" + id);
            Minecraft.getInstance().getTextureManager().register(tid, new DynamicTexture(() -> tid.toString(), img));
            READY.put(id, tid); return tid;
        } catch (Exception e) { InfinityGauntlet.LOGGER.warn("Planet texture {} failed", id, e); READY.put(id, Identifier.withDefaultNamespace("textures/block/stone.png")); return READY.get(id); }
    }
    /** Starts painting every body in the background, nearest-first order is irrelevant. */
    static void warmup() { for (String id : IDS) texture(id); }
    private static final String[] IDS = {"sun", "earth", "moon", "jupiter", "saturn", "mars", "venus", "mercury", "uranus", "neptune", "saturn_ring"};
    static final int[] TILT = {7, 0, 3, 23, 7, 25, 3, 27, 82, 28};

    // ---------------------------------------------------------------- noise
    private static double hash(int x, int y, int z, long seed) {
        long h = x * 374761393L + y * 668265263L + z * 2147483647L + seed * 1274126177L;
        h = (h ^ (h >>> 13)) * 1274126177L; h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }
    private static double smooth(double t) { return t * t * (3 - 2 * t); }
    private static double value(double x, double y, double z, long s) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        double fx = smooth(x - xi), fy = smooth(y - yi), fz = smooth(z - zi);
        double c00 = lerp(hash(xi, yi, zi, s), hash(xi + 1, yi, zi, s), fx);
        double c10 = lerp(hash(xi, yi + 1, zi, s), hash(xi + 1, yi + 1, zi, s), fx);
        double c01 = lerp(hash(xi, yi, zi + 1, s), hash(xi + 1, yi, zi + 1, s), fx);
        double c11 = lerp(hash(xi, yi + 1, zi + 1, s), hash(xi + 1, yi + 1, zi + 1, s), fx);
        return lerp(lerp(c00, c10, fy), lerp(c01, c11, fy), fz);
    }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static double fbm(double x, double y, double z, int oct, long s) {
        double sum = 0, amp = .5, norm = 0;
        for (int i = 0; i < oct; i++) { sum += amp * value(x, y, z, s + i * 17); norm += amp; amp *= .5; x *= 2.03; y *= 2.03; z *= 2.03; }
        return sum / norm;
    }
    private static double step(double a, double b, double v) { return smooth(Math.max(0, Math.min(1, (v - a) / (b - a)))); }
    private static double[] mix(double[] a, double[] b, double t) {
        return new double[]{lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)};
    }
    private static final double[] DEEP = {8, 30, 85}, SHALLOW = {30, 120, 165}, FOREST = {38, 92, 40}, GRASS = {88, 130, 55},
        SAND = {196, 170, 112}, TUNDRA = {128, 124, 108}, SNOW = {240, 245, 250};

    // ---------------------------------------------------------------- painting
    private static int[] paint(String id) {
        if (id.equals("saturn_ring")) return ring();
        long s = id.hashCode();
        double[][] craters = null;
        if (id.equals("moon") || id.equals("mercury")) {
            Random r = new Random(s); craters = new double[id.equals("moon") ? 110 : 150][4];
            for (var c : craters) {
                double z = r.nextDouble() * 2 - 1, a = r.nextDouble() * Math.PI * 2, q = Math.sqrt(1 - z * z);
                c[0] = q * Math.cos(a); c[1] = z; c[2] = q * Math.sin(a); c[3] = Math.toRadians(1.2 + 7 * Math.pow(r.nextDouble(), 2.2));
            }
        }
        int[] out = new int[W * H];
        for (int py = 0; py < H; py++) for (int px = 0; px < W; px++) {
            double lon = (px + .5) / W * 360 - 180, lat = 90 - (py + .5) / H * 180;
            double cl = Math.cos(Math.toRadians(lat));
            double x = cl * Math.sin(Math.toRadians(lon)), y = Math.sin(Math.toRadians(lat)), z = cl * Math.cos(Math.toRadians(lon));
            double[] c = switch (id) {
                case "sun" -> sun(x, y, z, s); case "earth" -> earth(x, y, z, lon, lat, s);
                case "moon" -> moon(x, y, z, craters, s, 1); case "mercury" -> moon(x, y, z, craters, s, 0);
                case "venus" -> venus(x, y, z, lat, s); case "mars" -> mars(x, y, z, lat, s);
                case "jupiter" -> jupiter(x, y, z, lon, lat, s); case "saturn" -> saturn(x, y, z, lat, s);
                case "uranus" -> giant(x, y, z, lat, s, new double[]{138, 208, 220}, new double[]{170, 226, 232}, .5);
                default -> giant(x, y, z, lat, s, new double[]{36, 70, 190}, new double[]{74, 124, 236}, 1.4);
            };
            out[py * W + px] = ARGB.color(255, clamp(c[0]), clamp(c[1]), clamp(c[2]));
        }
        return out;
    }
    private static int clamp(double v) { return (int) Math.max(0, Math.min(255, v)); }
    private static double[] scale(double[] c, double m) { return new double[]{c[0] * m, c[1] * m, c[2] * m}; }

    private static double[] sun(double x, double y, double z, long s) {
        double g = fbm(x * 9, y * 9, z * 9, 5, s), fine = fbm(x * 40, y * 40, z * 40, 3, s + 1);
        double[] c = mix(new double[]{255, 220, 135}, new double[]{255, 253, 227}, step(.3, .75, g * .7 + fine * .3));
        double spot = step(.72, .8, fbm(x * 3.5 + 7, y * 3.5, z * 3.5, 4, s + 5)) * step(.45, .0, Math.abs(y)) * .0;
        return scale(c, 1 - spot * .5);
    }
    private static double[] venus(double x, double y, double z, double lat, long s) {
        double sw = fbm(x * 3 + Math.sin(lat * .05) * 2, y * 5, z * 3, 5, s);
        double[] c = mix(new double[]{214, 176, 108}, new double[]{246, 226, 172}, step(.3, .7, sw));
        return scale(c, .92 + .16 * fbm(x * 14, y * 14, z * 14, 3, s + 2));
    }
    private static double[] mars(double x, double y, double z, double lat, long s) {
        double base = fbm(x * 4, y * 4, z * 4, 5, s);
        double[] c = mix(new double[]{172, 88, 52}, new double[]{214, 140, 88}, step(.3, .7, base));
        c = mix(c, new double[]{92, 52, 40}, step(.55, .68, fbm(x * 2.4 + 5, y * 2.4, z * 2.4, 4, s + 4)) * .75);
        c = mix(c, new double[]{246, 244, 240}, step(78, 86, Math.abs(lat) + (base - .5) * 8));
        return scale(c, .9 + .2 * fbm(x * 20, y * 20, z * 20, 3, s + 3));
    }
    private static double[] jupiter(double x, double y, double z, double lon, double lat, long s) {
        double warp = (fbm(x * 4, y * 9, z * 4, 4, s) - .5) * .45;
        double b = Math.sin((Math.toRadians(lat) + warp) * 11) * .5 + .5, b2 = Math.sin((Math.toRadians(lat) + warp) * 27 + 1) * .5 + .5;
        double[] c = mix(new double[]{236, 218, 190}, new double[]{176, 112, 72}, step(.35, .75, b * .7 + b2 * .3));
        c = mix(c, new double[]{205, 150, 100}, step(.55, .9, b2) * .4);
        double dl = ((lon - 40 + 540) % 360) - 180, spot = 1 - Math.min(1, Math.sqrt(dl * dl / 100 + (lat + 22) * (lat + 22) / 25));
        c = mix(c, new double[]{184, 76, 54}, step(.15, .6, spot));
        return scale(c, .92 + .16 * fbm(x * 12, y * 30, z * 12, 3, s + 2));
    }
    private static double[] saturn(double x, double y, double z, double lat, long s) {
        double warp = (fbm(x * 4, y * 8, z * 4, 3, s) - .5) * .25;
        double b = Math.sin((Math.toRadians(lat) + warp) * 18) * .5 + .5;
        double[] c = mix(new double[]{204, 176, 124}, new double[]{234, 214, 164}, b);
        return scale(c, .94 + .1 * fbm(x * 10, y * 26, z * 10, 3, s + 1));
    }
    private static double[] giant(double x, double y, double z, double lat, long s, double[] a, double[] b, double bands) {
        double t = Math.sin(Math.toRadians(lat) * 9 * bands + (fbm(x * 3, y * 6, z * 3, 3, s) - .5) * 1.5) * .5 + .5;
        double[] c = mix(a, b, t);
        double spot = step(.72, .8, fbm(x * 2.5 + 3, y * 3.5, z * 2.5, 4, s + 8)) * (bands > 1 ? .5 : 0);
        return scale(mix(c, new double[]{20, 40, 120}, spot), .95 + .08 * fbm(x * 14, y * 20, z * 14, 3, s + 2));
    }
    private static int[] ring() {
        int[] out = new int[256 * 2];
        for (int x = 0; x < 256; x++) {
            double t = x / 255.0, a;
            if (t < .12) a = .22; else if (t < .45) a = .82; else if (t < .5) a = .04; else if (t < .78) a = .62;
            else if (t < .8) a = .08; else if (t < .9) a = .55; else a = .12;
            a *= .75 + .25 * value(t * 90, 0, 0, 3);
            double m = .8 + .2 * value(t * 40, 1, 0, 5);
            int px = ARGB.color(clamp(a * 255), clamp(222 * m), clamp(200 * m), clamp(160 * m));
            out[x] = px; out[256 + x] = px;
        }
        return out;
    }
    private static double[] earth(double x, double y, double z, double lon, double lat, long s) {
        double wl = (fbm(x * 3, y * 3, z * 3, 4, s) - .5) * 16, wb = (fbm(x * 3 + 9, y * 3, z * 3, 4, s + 5) - .5) * 12;
        boolean land = PlanetGeometry.land(lon + wl, lat + wb);
        int near = 0;
        double[][] o = {{5, 0}, {-5, 0}, {0, 5}, {0, -5}, {9, 9}, {-9, 9}, {9, -9}, {-9, -9}};
        for (double[] d : o) if (PlanetGeometry.land(lon + wl + d[0], lat + wb + d[1])) near++;
        double detail = fbm(x * 22, y * 22, z * 22, 4, s + 3);
        double[] col;
        double al = Math.abs(lat);
        if (land) {
            double dry = fbm(x * 4, y * 4, z * 4, 4, s + 7);
            col = mix(FOREST, GRASS, step(.35, .65, fbm(x * 7, y * 7, z * 7, 4, s + 2)));
            if (al < 38) col = mix(col, SAND, step(.52, .66, dry) * (al < 34 ? 1 : .4));
            col = mix(col, TUNDRA, step(48, 62, al));
            col = mix(col, SNOW, step(66, 76, al + (detail - .5) * 14));
            double m = .72 + .5 * detail; col = new double[]{col[0] * m, col[1] * m, col[2] * m};
        } else {
            double shallow = (8 - near) / 8.0; shallow = near == 0 ? 0 : shallow * 0.0 + (near / 8.0);
            col = mix(DEEP, SHALLOW, Math.min(1, shallow * 1.3 + (detail - .5) * .15));
            col = mix(col, SNOW, step(70, 80, al + (detail - .5) * 10));
        }
        double cloud = step(.56, .80, fbm(x * 3.2 + 20, y * 3.8, z * 3.2, 5, s + 31)) * .8;
        return mix(col, new double[]{250, 252, 255}, cloud);
    }
    private static double[] moon(double x, double y, double z, double[][] cr, long s, int maria) {
        double g = 95 + 70 * fbm(x * 4, y * 4, z * 4, 5, s) + 14 * (fbm(x * 30, y * 30, z * 30, 3, s + 1) - .5);
        if (maria == 1) g -= 38 * step(.55, .62, fbm(x * 2.2, y * 2.2, z * 2.2, 4, s + 9));
        for (double[] c : cr) {
            double d = Math.acos(Math.max(-1, Math.min(1, x * c[0] + y * c[1] + z * c[2])));
            double t = d / c[3];
            if (t < .88) g -= 30 * (1 - t) * (1 - t);
            if (t >= .82 && t < 1.18) g += 30 * (1 - Math.abs(t - 1) / .18);
        }
        return maria == 1 ? new double[]{g, g * .98, g * .94} : new double[]{g * 1.0, g * .92, g * .84};
    }
    // ---------------------------------------------------------------- meshes (cached unit spheres)
    record Mesh(int lat, int lon, float[] pos, float[] uv) {}
    private static final Mesh[] MESHES = new Mesh[3];
    /** 0 = coarse (distant), 1 = medium, 2 = fine (fills the screen). */
    static Mesh mesh(int level) {
        if (MESHES[level] == null) {
            int lat = new int[]{20, 40, 64}[level], lon = lat * 2;
            float[] pos = new float[(lat + 1) * (lon + 1) * 3], uv = new float[(lat + 1) * (lon + 1) * 2];
            for (int i = 0, n = 0; i <= lat; i++) for (int j = 0; j <= lon; j++, n++) {
                double a = Math.PI * i / lat - Math.PI / 2, b = 2 * Math.PI * j / lon - Math.PI;
                pos[n * 3] = (float) (Math.cos(a) * Math.sin(b)); pos[n * 3 + 1] = (float) Math.sin(a); pos[n * 3 + 2] = (float) (Math.cos(a) * Math.cos(b));
                uv[n * 2] = j / (float) lon; uv[n * 2 + 1] = 1f - i / (float) lat;
            }
            MESHES[level] = new Mesh(lat, lon, pos, uv);
        }
        return MESHES[level];
    }
    static double stepSmooth(double a, double b, double v) { return step(a, b, v); }
    private CosmosPlanets() {}
}
