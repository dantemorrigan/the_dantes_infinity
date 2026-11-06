import com.dantemorrigan.gauntlet.cosmos.CosmosRoutes;
import java.util.HashSet;

/** Pure checks for navigation, transition continuity, safe planet arrivals and a finite speed budget. */
public class CosmosRoutesCheck {
    public static void main(String[] args) {
        var names=new HashSet<String>();
        if(CosmosRoutes.ALL.size()!=10) throw new AssertionError("Expected the Sun and nine bodies");
        for(var d:CosmosRoutes.ALL) {
            if(!names.add(d.id())) throw new AssertionError("Duplicate destination");
            double dx=d.arrivalX()-d.x(),dy=d.arrivalY()-d.y(),dz=d.arrivalZ()-d.z();
            if(!d.field() && Math.sqrt(dx*dx+dy*dy+dz*dz)<=d.radius()+28) throw new AssertionError("Unsafe arrival");
            if(d.arrivalY()<=CosmosRoutes.MIN_Y || d.arrivalY()>=CosmosRoutes.MAX_Y) throw new AssertionError("Arrival outside vertical safety bounds");
            if (!d.id().equals("sun") && (d.y() - d.radius() - CosmosRoutes.BODY_CLEARANCE <= CosmosRoutes.MIN_Y
                    || d.y() + d.radius() + CosmosRoutes.BODY_CLEARANCE >= CosmosRoutes.MAX_Y))
                throw new AssertionError("Body cannot be flown around: " + d.id());
        }
        var earth=CosmosRoutes.ALL.get(CosmosRoutes.EARTH);
        if(CosmosRoutes.entersEarth(earth.arrivalX(),earth.arrivalY(),earth.arrivalZ())) throw new AssertionError("Warp arrival must stay in orbit");
        if(CosmosRoutes.EARTH_ENTRY_RADIUS<=earth.radius()+CosmosRoutes.BODY_CLEARANCE) throw new AssertionError("Re-entry blocked by planet collision shell");
        if(!CosmosRoutes.entersEarth(earth.x()+CosmosRoutes.EARTH_ENTRY_RADIUS-1,earth.y(),earth.z())
            || CosmosRoutes.entersEarth(earth.x()+CosmosRoutes.EARTH_ENTRY_RADIUS+1,earth.y(),earth.z())) throw new AssertionError("Atmosphere entry boundary");
        double previous=0;
        for(int y=0;y<1200;y++) {
            double f=CosmosRoutes.atmosphere(y);
            if(f<previous || f<0 || f>1) throw new AssertionError("Discontinuous atmosphere");
            previous=f;
        }
        if(CosmosRoutes.atmosphere(256)!=0 || CosmosRoutes.atmosphere(640)!=1) throw new AssertionError("Transition endpoints");
        int target=0; for(int i=0;i<CosmosRoutes.ALL.size();i++) target=CosmosRoutes.next(target);
        if(target!=0) throw new AssertionError("Navigation wrap");
        if(CosmosRoutes.BOOST_SPEED<=CosmosRoutes.CRUISE_SPEED || CosmosRoutes.BOOST_SPEED>3) throw new AssertionError("Movement budget");
        if (CosmosRoutes.SPACE_BOOST_SPEED <= CosmosRoutes.BOOST_SPEED || CosmosRoutes.SPACE_BOOST_SPEED >= 8)
            throw new AssertionError("Space boost budget");
        double max = CosmosRoutes.MAX_Y;
        if (CosmosRoutes.limitAxis(max, 7, CosmosRoutes.MIN_Y, max) != 0
                || CosmosRoutes.limitAxis(max, -7, CosmosRoutes.MIN_Y, max) != -7
                || CosmosRoutes.limitAxis(max - 1, 7, CosmosRoutes.MIN_Y, max) != .12)
            throw new AssertionError("Flight limit must brake only in the outward direction");
        System.out.println("Cosmos navigation and atmosphere checks passed");
    }
}
