package com.dantemorrigan.gauntlet.cosmos;

import java.util.List;

/** Shared, deterministic navigation catalogue. Distances are world blocks, radii are visual exclusion zones. */
public final class CosmosRoutes {
    public record Destination(String id, double x, double y, double z, double radius, boolean field) {
        public double arrivalX() { return id.equals("earth") ? 0 : x; }
        public double arrivalY() { return id.equals("earth") ? 128 : field ? y : y + radius * .4; }
        public double arrivalZ() { return id.equals("earth") ? 0 : field ? z : z + radius * 2.5; }
    }
    public static final double EARTH_CENTER_Y = -1000;
    public static final List<Destination> ALL = List.of(
        new Destination("earth", 0, -1000, 0, 875, false),
        new Destination("moon", 1800, 320, -600, 225, false),
        new Destination("ember", -2200, 320, -1600, 500, false),
        new Destination("ice", 800, 320, 2600, 375, false),
        new Destination("belt", -1000, 256, 900, 90, true),
        new Destination("ice_belt", 2400, 256, 1800, 90, true));
    public static final double EXIT_HEIGHT = 640;
    public static final int WARP_TICKS = 50;
    public static final double CRUISE_SPEED = .85, BOOST_SPEED = 3;
    public static double atmosphere(double y) {
        double t = Math.max(0, Math.min(1, (y - 256) / (EXIT_HEIGHT - 256)));
        return t * t * (3 - 2 * t);
    }
    public static int next(int index) { return Math.floorMod(index + 1, ALL.size()); }
    private CosmosRoutes() {}
}
