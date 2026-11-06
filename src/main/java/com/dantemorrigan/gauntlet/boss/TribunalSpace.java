package com.dantemorrigan.gauntlet.boss;

import com.dantemorrigan.gauntlet.cosmos.CosmosRoutes;

/** Collision envelope for the entire giant, including his floating head and outstretched hands. */
public final class TribunalSpace {
    public static final float MODEL_SCALE = 28;
    public static final double HEIGHT = 106, ENVELOPE = 62, ORBIT = 60;
    public record Point(double x, double y, double z) {
        public Point lerp(Point b, double t) { return new Point(x+(b.x-x)*t,y+(b.y-y)*t,z+(b.z-z)*t); }
        public double distance(Point b) { return Math.sqrt((x-b.x)*(x-b.x)+(y-b.y)*(y-b.y)+(z-b.z)*(z-b.z)); }
    }
    public static boolean clear(Point a, Point b, double margin) {
        if (Math.abs(b.x)+margin>CosmosRoutes.LIMIT || Math.abs(b.z)+margin>CosmosRoutes.LIMIT
                || b.y-margin<CosmosRoutes.MIN_Y || b.y+margin>CosmosRoutes.MAX_Y) return false;
        for (var body:CosmosRoutes.ALL) {
            Point center=new Point(body.x(),body.y(),body.z());
            double dx=b.x-a.x,dy=b.y-a.y,dz=b.z-a.z, length=dx*dx+dy*dy+dz*dz;
            double t=length<1e-9?0:Math.max(0,Math.min(1,((center.x-a.x)*dx+(center.y-a.y)*dy+(center.z-a.z)*dz)/length));
            if(a.lerp(b,t).distance(center)<body.radius()+margin) return false;
        }
        return true;
    }
    /** Search around the player instead of pushing a spawn through the nearest planet. Returns a body centre. */
    public static Point orbit(Point player, double angle) {
        for(int i=0;i<32;i++) {
            double a=angle+i*Math.PI/16;
            Point candidate=new Point(player.x+Math.cos(a)*ORBIT,Math.max(CosmosRoutes.MIN_Y+ENVELOPE+4,Math.min(CosmosRoutes.MAX_Y-ENVELOPE-4,player.y+Math.sin(a*.7)*18)),player.z+Math.sin(a)*ORBIT);
            if(clear(candidate,candidate,ENVELOPE)) return candidate;
        }
        return null;
    }
    /** A short step is checked as a swept sphere; stopping is safer than teleporting across an obstacle. */
    public static Point step(Point from, Point to, double speed) {
        double distance=from.distance(to);
        Point next=from.lerp(to,Math.min(1,speed/Math.max(.001,distance)));
        if(clear(from,next,ENVELOPE)) return next;
        for(int i=0;i<12;i++) {
            double a=i*Math.PI/6;
            Point tangent=new Point(from.x+Math.cos(a)*speed,from.y,from.z+Math.sin(a)*speed);
            if(clear(from,tangent,ENVELOPE) && tangent.distance(to)<distance) return tangent;
        }
        return from;
    }
    private TribunalSpace() {}
}
