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
            if(Math.abs(d.arrivalY())>=900) throw new AssertionError("Arrival outside vertical safety bounds");
        }
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
        System.out.println("Cosmos navigation and atmosphere checks passed");
    }
}
