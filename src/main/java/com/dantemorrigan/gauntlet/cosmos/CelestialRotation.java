package com.dantemorrigan.gauntlet.cosmos;

/** Sidereal periods and obliquities; one terrestrial day is scaled to one 20-minute game day. */
public final class CelestialRotation {
    public record Spin(double days,float tilt,float phase) {}
    public static Spin spin(String id) {
        return switch(id) {
            case "sun" -> new Spin(25.38,7.25f,0);
            case "mercury" -> new Spin(58.646,.034f,67);
            case "venus" -> new Spin(243.025,177.36f,134);
            case "earth" -> new Spin(.99726968,23.44f,201);
            case "moon" -> new Spin(27.321661,6.68f,268);
            case "mars" -> new Spin(1.025957,25.19f,335);
            case "jupiter" -> new Spin(.41354,3.13f,42);
            case "saturn" -> new Spin(.444,26.73f,109);
            case "uranus" -> new Spin(.71833,97.77f,176);
            case "neptune" -> new Spin(.67125,28.32f,243);
            default -> throw new IllegalArgumentException("Unknown celestial body: "+id);
        };
    }
    public static float yaw(String id,double ticks) {
        Spin s=spin(id);return (float)((s.phase()+ticks%(s.days()*24000)*360/(s.days()*24000))%360);
    }
    private CelestialRotation() {}
}
