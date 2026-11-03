package com.dantemorrigan.gauntlet.cosmos;

import java.util.List;

/**
 * Shared deterministic catalogue of the Solar System.
 * Body radii keep their REAL ratios (Earth = 100 blocks, Sun = 10 900, Jupiter = 1 120, Moon = 27);
 * orbital distances are compressed so the whole system fits in the dimension.
 */
public final class CosmosRoutes {
    /** id, centre, radius. {@code field} is kept for compatibility and is always false. */
    public record Destination(String id, double x, double y, double z, double radius, boolean field) {
        private double stand() { return Math.max(radius * 2.5, 160); }
        /** Unit vector (xz plane) from this body towards the Sun; the arrival point is on the lit side. */
        private double[] toSun() {
            double l = Math.hypot(x, z);
            return l < 1 ? new double[]{1, 0} : new double[]{-x / l, -z / l};
        }
        public double arrivalX() { return x + (id.equals("sun") ? stand() : toSun()[0] * stand()); }
        public double arrivalY() { return y + radius * .05; }
        public double arrivalZ() { return z + (id.equals("sun") ? 0 : toSun()[1] * stand()); }
        /** Vanilla yaw looking at the body from the arrival point. */
        public float arrivalYaw() {
            double dx = x - arrivalX(), dz = z - arrivalZ();
            return (float) Math.toDegrees(Math.atan2(-dx, dz));
        }
    }
    public static final double Y = 256, R_EARTH = 100;
    private static Destination body(String id, double orbit, double degrees, double radiusInEarths) {
        double a = Math.toRadians(degrees);
        return new Destination(id, orbit * Math.cos(a), Y, orbit * Math.sin(a), R_EARTH * radiusInEarths, false);
    }
    private static final Destination EARTH_BODY = body("earth", 32000, 0, 1);
    public static final List<Destination> ALL = List.of(
        new Destination("sun", 0, Y, 0, R_EARTH * 109.1, false),
        body("mercury", 18000, 40, .383),
        body("venus", 24000, 115, .949),
        EARTH_BODY,
        new Destination("moon", EARTH_BODY.x() + 1500, Y + 60, EARTH_BODY.z() + 1100, R_EARTH * .2727, false),
        body("mars", 40000, 205, .532),
        body("jupiter", 54000, 280, 11.21),
        body("saturn", 70000, 335, 9.45),
        body("uranus", 86000, 150, 4.01),
        body("neptune", 100000, 245, 3.88));
    public static final int EARTH = 3, SUN = 0;
    public static final double RETURN_RANGE = 3000, LIMIT = 125000;
    public static final double EXIT_HEIGHT = 640;
    public static final int WARP_TICKS = 50;
    public static final double CRUISE_SPEED = .85, BOOST_SPEED = 3;
    public static double earthDistance(double x, double y, double z) {
        var e = ALL.get(EARTH); return Math.sqrt((x - e.x()) * (x - e.x()) + (y - e.y()) * (y - e.y()) + (z - e.z()) * (z - e.z()));
    }
    /**
     * Index of the body under the crosshair, or -1. A body counts if the look ray passes through its disk or within
     * {@code assistDegrees} of its edge; the nearest such body wins, so a planet in front of the Sun is picked first.
     */
    public static int aim(double ex, double ey, double ez, double lx, double ly, double lz, double assistDegrees) {
        double ll = Math.sqrt(lx * lx + ly * ly + lz * lz); if (ll < 1e-6) return -1;
        lx /= ll; ly /= ll; lz /= ll;
        int best = -1; double bestDistance = Double.MAX_VALUE, assist = Math.toRadians(assistDegrees);
        for (int i = 0; i < ALL.size(); i++) {
            var d = ALL.get(i);
            double dx = d.x() - ex, dy = d.y() - ey, dz = d.z() - ez, dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < 1) continue;
            double cos = Math.max(-1, Math.min(1, (dx * lx + dy * ly + dz * lz) / dist));
            double angle = Math.acos(cos), radius = dist <= d.radius() ? Math.PI : Math.asin(d.radius() / dist);
            if (angle <= radius + assist && dist < bestDistance) { best = i; bestDistance = dist; }
        }
        return best;
    }
    public static double atmosphere(double y) {
        double t = Math.max(0, Math.min(1, (y - 256) / (EXIT_HEIGHT - 256)));
        return t * t * (3 - 2 * t);
    }
    public static int next(int index) { return Math.floorMod(index + 1, ALL.size()); }
    private CosmosRoutes() {}
}
