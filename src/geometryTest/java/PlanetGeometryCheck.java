import com.dantemorrigan.gauntlet.client.space.PlanetGeometry;
import java.util.HashMap;
import java.util.Map;

/** Standalone checks, no game runtime required: java -cp build/classes/java/client this-file.java. */
class PlanetGeometryCheck {
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static int outsideFaces(java.util.List<PlanetGeometry.Piece> pieces) {
        Map<String,Integer> interfaces=new HashMap<>();
        for(var piece:pieces) for(var material:piece.materials()) for(var f:material) {
            int x=f.x()+(f.side()==0?1:0),y=f.y()+(f.side()==2?1:0),z=f.z()+(f.side()==4?1:0);
            interfaces.merge(x+","+y+","+z+":"+f.side()/2,1,Integer::sum);
        }
        check(interfaces.values().stream().allMatch(n->n==1||n==2),"Overlapping/corrupt fracture faces");
        return (int)interfaces.values().stream().filter(n->n==1).count();
    }
    public static void main(String[] args) {
        for(long seed:new long[]{0,7919,1234,-97}) {
            var m=PlanetGeometry.build(seed);
            check(m.equals(PlanetGeometry.build(seed)),"Reconnection must rebuild identical geometry");
            check(m.parents().size()==14,"Expected 14 irregular primary fragments");
            check(m.fragments().size()>45,"Secondary fracture needs many smaller pieces");
            int volume=m.parents().stream().mapToInt(PlanetGeometry.Piece::volume).sum();
            check(volume>33000&&volume<34000,"Planet volume is not a solid sphere");
            check(volume==m.fragments().stream().mapToInt(PlanetGeometry.Piece::volume).sum(),"Secondary fracture loses matter");
            for(var p:m.parents()) check(p.volume()==m.fragments().stream().filter(c->c.parent()==p.id()).mapToInt(PlanetGeometry.Piece::volume).sum(),"Child pieces do not reassemble into their parent");
            int min=m.parents().stream().mapToInt(PlanetGeometry.Piece::volume).min().orElseThrow();
            int max=m.parents().stream().mapToInt(PlanetGeometry.Piece::volume).max().orElseThrow();
            check(max>min*2,"Pieces must not be equal quarters/octants");
            check(outsideFaces(m.parents())==outsideFaces(m.fragments()),"Secondary split changes the outer surface");
            long faces=m.fragments().stream().flatMap(p->p.materials().stream()).mapToLong(java.util.List::size).sum();
            check(faces<65000,"Geometry exceeds the bounded face budget");
            for(int material:new int[]{0,2,4,6,7}) {
                final int mat=material;
                check(m.parents().stream().anyMatch(p->!p.materials().get(mat).isEmpty()),"Missing ocean, land, polar cap, core, or clouds");
            }
            System.out.println("seed="+seed+", volume="+volume+", primary="+m.parents().size()+", secondary="+m.fragments().size()+", faces="+faces);
        }
        check(!PlanetGeometry.build(1).equals(PlanetGeometry.build(2)),"Different events need different fracture patterns");
        System.out.println("Planet geometry checks passed");
    }
}
