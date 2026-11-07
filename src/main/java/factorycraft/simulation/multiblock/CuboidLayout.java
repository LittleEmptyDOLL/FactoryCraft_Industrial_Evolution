package factorycraft.simulation.multiblock;

import java.util.ArrayList;
import java.util.List;

/** Controller is the front-left-bottom cell. A structure extends right, up and backwards. */
public record CuboidLayout(int width,int height,int depth) {
    public record Offset(int x,int y,int z) {}
    public CuboidLayout {
        if(width<1 || height<1 || depth<1 || width>16 || height>16 || depth>16)
            throw new IllegalArgumentException("Assembly dimensions must be 1..16");
    }
    public List<Offset> parts(int forwardX,int forwardZ) {
        if(Math.abs(forwardX)+Math.abs(forwardZ)!=1)throw new IllegalArgumentException("Expected a cardinal horizontal direction");
        var result=new ArrayList<Offset>(width*height*depth-1);
        for(int y=0;y<height;y++)for(int right=0;right<width;right++)for(int back=0;back<depth;back++) {
            if(y==0 && right==0 && back==0)continue;
            result.add(new Offset(-forwardZ*right-forwardX*back,y,forwardX*right-forwardZ*back));
        }
        return List.copyOf(result);
    }
}
