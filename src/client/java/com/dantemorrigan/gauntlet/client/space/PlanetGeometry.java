package com.dantemorrigan.gauntlet.client.space;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Deterministic two-level Voronoi fracture. Pure geometry: no Minecraft state or render-thread calls. */
public final class PlanetGeometry {
    public static final int RADIUS = 20, PARENTS = 14, CHILDREN = 4, MATERIALS = 8;
    private static final int N = RADIUS * 2;
    private static final int[][] NORMALS = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    public record Face(int x, int y, int z, int side) {}
    public record Piece(int id, int parent, float x, float y, float z, int volume, List<List<Face>> materials) {}
    public record Model(List<Piece> parents, List<Piece> fragments) {}

    public static Model build(long seed) {
        Random random = new Random(seed);
        double[][] roots = new double[PARENTS][3], children = new double[PARENTS * CHILDREN][3];
        // One core and an irregular, unequal distribution of crust/mantle volumes.
        for (int i = 1; i < PARENTS; i++) {
            double y = 1 - 2.0 * (i - .5) / (PARENTS - 1);
            double angle = i * 2.3999632297 + .15 * random.nextDouble();
            double r = RADIUS * (.48 + .22 * random.nextDouble());
            roots[i] = new double[]{Math.cos(angle) * Math.sqrt(1-y*y) * r, y*r, Math.sin(angle) * Math.sqrt(1-y*y) * r};
        }
        for (int i = 0; i < children.length; i++) {
            int p = i / CHILDREN;
            children[i] = new double[]{roots[p][0] + random.nextGaussian()*4.5,
                    roots[p][1] + random.nextGaussian()*4.5, roots[p][2] + random.nextGaussian()*4.5};
        }
        int[] parent = new int[N*N*N], child = new int[N*N*N], material = new int[N*N*N];
        Arrays.fill(parent, -1); Arrays.fill(child, -1);
        for (int x = -RADIUS; x < RADIUS; x++) for (int y = -RADIUS; y < RADIUS; y++) for (int z = -RADIUS; z < RADIUS; z++) {
            double px=x+.5, py=y+.5, pz=z+.5, r=Math.sqrt(px*px+py*py+pz*pz);
            if (r > RADIUS) continue;
            int at = index(x,y,z), p = nearest(roots, px,py,pz,0,PARENTS);
            parent[at] = p;
            child[at] = nearest(children,px,py,pz,p*CHILDREN,(p+1)*CHILDREN);
            material[at] = material(px,py,pz,r);
        }
        return new Model(mesh(parent, material, PARENTS, false), mesh(child, material, PARENTS*CHILDREN, true));
    }
    private static int index(int x,int y,int z) {
        if (x < -RADIUS || x >= RADIUS || y < -RADIUS || y >= RADIUS || z < -RADIUS || z >= RADIUS) return -1;
        return ((x+RADIUS)*N+y+RADIUS)*N+z+RADIUS;
    }
    private static int nearest(double[][] points,double x,double y,double z,int from,int to) {
        int best = from; double distance = Double.MAX_VALUE;
        for (int i=from;i<to;i++) {
            double dx=x-points[i][0],dy=y-points[i][1],dz=z-points[i][2],d=dx*dx+dy*dy+dz*dz;
            if(d<distance) {distance=d;best=i;}
        }
        return best;
    }
    private static List<Piece> mesh(int[] owners,int[] materials,int count,boolean children) {
        List<List<List<Face>>> faces = new ArrayList<>();
        double[][] sums = new double[count][3]; int[] volumes = new int[count];
        for(int i=0;i<count;i++) {
            List<List<Face>> byMaterial=new ArrayList<>();
            for(int m=0;m<MATERIALS;m++) byMaterial.add(new ArrayList<>());
            faces.add(byMaterial);
        }
        for(int x=-RADIUS;x<RADIUS;x++) for(int y=-RADIUS;y<RADIUS;y++) for(int z=-RADIUS;z<RADIUS;z++) {
            int at=index(x,y,z),owner=owners[at]; if(owner<0) continue;
            volumes[owner]++; sums[owner][0]+=x+.5;sums[owner][1]+=y+.5;sums[owner][2]+=z+.5;
            for(int side=0;side<6;side++) {
                int[] n=NORMALS[side]; int next=index(x+n[0],y+n[1],z+n[2]);
                if(next>=0 && owners[next]==owner) continue; // Cull every internal face of this fragment.
                faces.get(owner).get(materials[at]).add(new Face(x,y,z,side));
            }
        }
        List<Piece> pieces=new ArrayList<>();
        for(int i=0;i<count;i++) if(volumes[i]>0) pieces.add(new Piece(i,children?i/CHILDREN:i,
                (float)(sums[i][0]/volumes[i]),(float)(sums[i][1]/volumes[i]),(float)(sums[i][2]/volumes[i]),volumes[i],faces.get(i)));
        return pieces;
    }
    private static int material(double x,double y,double z,double radius) {
        if(radius < RADIUS*.48) return 6; // Hot core, exposed only after the first fractures.
        if(radius < RADIUS-1.4) return 5;
        double lat=Math.toDegrees(Math.asin(y/radius)),lon=Math.toDegrees(Math.atan2(x,z));
        if(Math.abs(lat)>72) return 4;
        double clouds=Math.sin(x*.44+Math.sin(z*.23)*2)+Math.sin(z*.33+y*.41)+Math.cos(y*.59-x*.18);
        if(clouds>1.65 && radius>RADIUS-.8) return 7;
        if(land(lon,lat)) return (lat>8 && lat<32 && lon>-18 && lon<65) || (lat<-18 && lon>112) ? 3 : 2;
        return land(lon+4,lat)||land(lon-4,lat)||land(lon,lat+4)||land(lon,lat-4) ? 1 : 0;
    }
    // Coarse coastlines inspired by NASA Blue Marble; blocks and textures remain vanilla Minecraft.
    private static final double[][][] CONTINENTS = {
        {{-17,35},{7,37},{35,31},{51,11},{40,-15},{20,-35},{10,-25},{-5,0},{-17,15}},
        {{-10,36},{0,45},{-10,58},{30,72},{100,75},{170,62},{165,50},{140,35},{130,20},{110,0},{95,5},{75,8},{50,30},{35,35}},
        {{-168,68},{-145,70},{-125,55},{-105,72},{-60,65},{-52,48},{-80,25},{-97,15},{-115,29},{-130,52},{-168,55}},
        {{-81,12},{-64,10},{-35,-7},{-45,-25},{-68,-56},{-76,-33},{-81,-5}},
        {{112,-12},{133,-11},{153,-24},{146,-39},{116,-34}},
        {{-58,60},{-22,70},{-25,83},{-60,82}},{{44,-13},{50,-16},{47,-26},{43,-23}}
    };
    private static boolean land(double lon,double lat) {
        for(double[][] polygon:CONTINENTS) {
            boolean inside=false;
            for(int i=0,j=polygon.length-1;i<polygon.length;j=i++) {
                double[] a=polygon[i],b=polygon[j];
                if((a[1]>lat)!=(b[1]>lat) && lon<(b[0]-a[0])*(lat-a[1])/(b[1]-a[1])+a[0]) inside=!inside;
            }
            if(inside) return true;
        }
        return false;
    }
    private PlanetGeometry() {}
}
