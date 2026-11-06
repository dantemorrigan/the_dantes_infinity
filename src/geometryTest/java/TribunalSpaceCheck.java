import com.dantemorrigan.gauntlet.boss.TribunalSpace;
import com.dantemorrigan.gauntlet.cosmos.CosmosRoutes;

public class TribunalSpaceCheck {
    public static void main(String[] args) {
        for(var body:CosmosRoutes.ALL) {
            var player=new TribunalSpace.Point(body.x()+body.radius()+28,body.y(),body.z());
            for(int i=0;i<64;i++) {
                var next=TribunalSpace.orbit(player,i*Math.PI/32);
                if(next==null || !TribunalSpace.clear(next,next,TribunalSpace.ENVELOPE)) throw new AssertionError("Unsafe orbit/spawn by "+body.id());
            }
            var from=new TribunalSpace.Point(body.x()-body.radius()-100,body.y(),body.z());
            var to=new TribunalSpace.Point(body.x()+body.radius()+100,body.y(),body.z());
            if(TribunalSpace.clear(from,to,TribunalSpace.ENVELOPE)) throw new AssertionError("Swept path tunneled through "+body.id());
            var step=TribunalSpace.step(from,to,1.35);
            if(!TribunalSpace.clear(from,step,TribunalSpace.ENVELOPE)) throw new AssertionError("Movement penetrated "+body.id());
        }
        for(double y:new double[]{CosmosRoutes.MIN_Y+1,CosmosRoutes.MAX_Y-1}) {
            var spawn=TribunalSpace.orbit(new TribunalSpace.Point(30000,y,500),0);
            if(spawn==null || !TribunalSpace.clear(spawn,spawn,TribunalSpace.ENVELOPE)) throw new AssertionError("Dimension border spawn failed");
        }
        System.out.println("TRIBUNAL SPACE: spawn/orbit and swept collision passed for all planets, Sun and Moon");
    }
}
