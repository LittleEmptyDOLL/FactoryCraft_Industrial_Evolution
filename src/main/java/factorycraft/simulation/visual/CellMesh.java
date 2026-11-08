package factorycraft.simulation.visual;

import java.util.*;

/** Partitions only a box's original surfaces, without artificial caps at internal block seams.
 * Coordinates use machine blocks; UVs remain relative to the original complete face. */
public final class CellMesh {
    public record Box(double x0,double y0,double z0,double x1,double y1,double z1,String material) {
        public Box {
            if(!Double.isFinite(x0+y0+z0+x1+y1+z1) || x0<0 || y0<0 || z0<0 || x1<=x0 || y1<=y0 || z1<=z0 || x1>16 || y1>16 || z1>16 || material==null)
                throw new IllegalArgumentException("Invalid machine box");
        }
        public double area() { return 2*((x1-x0)*(y1-y0)+(x1-x0)*(z1-z0)+(y1-y0)*(z1-z0)); }
    }
    /** Direction order follows Minecraft: down, up, north, south, west, east. Local vertices are 0..1. */
    public record Face(int cellX,int cellY,int cellZ,int side,double x0,double y0,double z0,double x1,double y1,double z1,
                       double u0,double v0,double u1,double v1,String material) {
        public double area() { return side<2 ? (x1-x0)*(z1-z0):side<4 ? (x1-x0)*(y1-y0):(z1-z0)*(y1-y0); }
    }
    public static List<Face> split(Box box,int width,int height,int depth) {
        if(width<1 || height<1 || depth<1 || box.x1>width || box.y1>height || box.z1>depth)throw new IllegalArgumentException("Mesh outside machine footprint");
        var result=new ArrayList<Face>();double[] low={box.x0,box.y0,box.z0},high={box.x1,box.y1,box.z1};
        for(int side=0;side<6;side++) {
            int axis=side<2 ? 1:side<4 ? 2:0;double plane=(side%2==0 ? low:high)[axis];
            int normalCell=(int)Math.floor(plane+(side%2==0 ? 1e-8:-1e-8));
            for(int y=0;y<height;y++)for(int z=0;z<depth;z++)for(int x=0;x<width;x++) {
                int[] cell={x,y,z};if(cell[axis]!=normalCell)continue;double[] a=new double[3],b=new double[3];boolean visible=true;
                for(int d=0;d<3;d++)if(d==axis)a[d]=b[d]=plane;else { a[d]=Math.max(low[d],cell[d]);b[d]=Math.min(high[d],cell[d]+1);if(b[d]-a[d]<1e-8)visible=false; }
                if(!visible)continue;
                double ax=(a[0]-low[0])/(high[0]-low[0]),bx=(b[0]-low[0])/(high[0]-low[0]);
                double ay=(a[1]-low[1])/(high[1]-low[1]),by=(b[1]-low[1])/(high[1]-low[1]);
                double az=(a[2]-low[2])/(high[2]-low[2]),bz=(b[2]-low[2])/(high[2]-low[2]);
                double[] uv=switch(side) {case 0->new double[]{ax,1-bz,bx,1-az};case 1->new double[]{ax,az,bx,bz};case 2->new double[]{1-bx,1-by,1-ax,1-ay};case 3->new double[]{ax,1-by,bx,1-ay};case 4->new double[]{az,1-by,bz,1-ay};default->new double[]{1-bz,1-by,1-az,1-ay};};
                result.add(new Face(x,y,z,side,a[0]-x,a[1]-y,a[2]-z,b[0]-x,b[1]-y,b[2]-z,uv[0],uv[1],uv[2],uv[3],box.material));
            }
        }return List.copyOf(result);
    }
    private CellMesh() {}
}
