package com.dantemorrigan.gauntlet.client.cosmos;

import com.dantemorrigan.gauntlet.InfinityGauntlet;
import com.dantemorrigan.gauntlet.client.space.PlanetGeometry;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import java.util.Random;

/** Static, procedurally painted planet maps (equirectangular) + a smooth unit-sphere mesh with baked sun lighting. */
final class CosmosPlanets {
    static final int W = 1024, H = 512, LAT = 56, LON = 112;
    private static final Identifier[] TEXTURES = new Identifier[4];
    private static final long[] SEEDS = {11, 23, 37, 41};

    static Identifier texture(int planet) {
        if (TEXTURES[planet] == null) {
            NativeImage img = new NativeImage(W, H, false);
            paint(img, planet);
            Identifier id = InfinityGauntlet.id("dynamic/cosmos_planet_" + planet);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> id.toString(), img));
            TEXTURES[planet] = id;
        }
        return TEXTURES[planet];
    }

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
    private static void paint(NativeImage img, int planet) {
        long s = SEEDS[planet];
        double[][] craters = null;
        if (planet == 1) {
            Random r = new Random(s); craters = new double[110][4];
            for (var c : craters) {
                double z = r.nextDouble() * 2 - 1, a = r.nextDouble() * Math.PI * 2, q = Math.sqrt(1 - z * z);
                c[0] = q * Math.cos(a); c[1] = z; c[2] = q * Math.sin(a); c[3] = Math.toRadians(1.5 + 7 * Math.pow(r.nextDouble(), 2.2));
            }
        }
        for (int py = 0; py < H; py++) for (int px = 0; px < W; px++) {
            double lon = (px + .5) / W * 360 - 180, lat = 90 - (py + .5) / H * 180;
            double cl = Math.cos(Math.toRadians(lat));
            double x = cl * Math.sin(Math.toRadians(lon)), y = Math.sin(Math.toRadians(lat)), z = cl * Math.cos(Math.toRadians(lon));
            double[] c = switch (planet) { case 0 -> earth(x, y, z, lon, lat, s); case 1 -> moon(x, y, z, craters, s);
                case 2 -> ember(x, y, z, lat, s); default -> ice(x, y, z, lat, s); };
            img.setPixel(px, py, ARGB.color(255, clamp(c[0]), clamp(c[1]), clamp(c[2])));
        }
    }
    private static int clamp(double v) { return (int) Math.max(0, Math.min(255, v)); }

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
    private static double[] moon(double x, double y, double z, double[][] cr, long s) {
        double g = 95 + 70 * fbm(x * 4, y * 4, z * 4, 5, s) + 14 * (fbm(x * 30, y * 30, z * 30, 3, s + 1) - .5);
        double mare = step(.55, .62, fbm(x * 2.2, y * 2.2, z * 2.2, 4, s + 9));
        g -= 38 * mare;
        for (double[] c : cr) {
            double d = Math.acos(Math.max(-1, Math.min(1, x * c[0] + y * c[1] + z * c[2])));
            double t = d / c[3];
            if (t < 1) g -= 26 * (1 - t) * (1 - t) * 1.2 * (t < .88 ? 1 : 0) + 0;
            if (t >= .82 && t < 1.18) g += 30 * (1 - Math.abs(t - 1) / .18);
        }
        return new double[]{g, g * .98, g * .94};
    }
    private static double[] ember(double x, double y, double z, double lat, long s) {
        double base = fbm(x * 3.5, y * 3.5, z * 3.5, 5, s);
        double band = .5 + .5 * Math.sin(lat * .35 + (fbm(x * 2, y * 2, z * 2, 3, s + 4) - .5) * 8);
        double[] c = mix(new double[]{70, 28, 22}, new double[]{176, 86, 46}, step(.3, .75, base * .6 + band * .4));
        double ridge = 1 - Math.abs(2 * fbm(x * 5 + 4, y * 5, z * 5, 5, s + 8) - 1);
        double lava = step(.935, .975, ridge);
        c = mix(c, new double[]{255, 140, 30}, lava);
        double dust = fbm(x * 18, y * 18, z * 18, 3, s + 2); double m = .85 + .3 * dust;
        return new double[]{c[0] * m, c[1] * m, c[2] * m};
    }
    private static double[] ice(double x, double y, double z, double lat, long s) {
        double base = fbm(x * 3, y * 3, z * 3, 5, s);
        double[] c = mix(new double[]{132, 180, 224}, new double[]{214, 234, 248}, step(.3, .7, base));
        double ridge = 1 - Math.abs(2 * fbm(x * 6, y * 6, z * 6, 4, s + 6) - 1);
        c = mix(c, new double[]{70, 118, 182}, step(.965, .99, ridge) * .6);
        c = mix(c, SNOW, step(62, 78, Math.abs(lat) + (base - .5) * 20));
        double m = .92 + .16 * fbm(x * 25, y * 25, z * 25, 3, s + 3);
        return new double[]{c[0] * m, c[1] * m, c[2] * m};
    }

    // ---------------------------------------------------------------- mesh
    /** Unit-sphere vertex (already rotated by tilt/yaw) for the grid point; out = x,y,z. */
    static void vertex(int planet, int i, int j, int lat, int lon, float[] out) {
        double a = Math.PI * i / lat - Math.PI / 2, b = 2 * Math.PI * j / lon - Math.PI;
        double x = Math.cos(a) * Math.sin(b), y = Math.sin(a), z = Math.cos(a) * Math.cos(b);
        double yaw = Math.toRadians(planet * 67), tilt = Math.toRadians(planet == 0 ? 23.4 : 12);
        double x1 = x * Math.cos(yaw) + z * Math.sin(yaw), z1 = -x * Math.sin(yaw) + z * Math.cos(yaw);
        out[0] = (float) (x1 * Math.cos(tilt) - y * Math.sin(tilt)); out[1] = (float) (x1 * Math.sin(tilt) + y * Math.cos(tilt)); out[2] = (float) z1;
    }
    /** Smooth baked daylight; fixed in world space so the picture is perfectly static. */
    static float light(float nx, float ny, float nz) {
        double d = (-.35 * nx + .65 * ny + .65 * nz) / Math.sqrt(.35 * .35 + .65 * .65 * 2);
        return (float) (.10 + .90 * step(-.18, .38, d));
    }
    private CosmosPlanets() {}
}
