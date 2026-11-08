package factorycraft.simulation;

import factorycraft.simulation.visual.CellMesh;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

class CellMeshTest {
    @Test void largeSurfaceAreaIsConservedWithoutInternalCaps() {
        var b=new CellMesh.Box(0,0,0,5,4,5,"shell");var faces=CellMesh.split(b,5,4,5);
        assertEquals(b.area(),faces.stream().mapToDouble(CellMesh.Face::area).sum(),1e-9);assertEquals(130,faces.size());
        for(var f:faces) { assertTrue(f.cellX()>=0 && f.cellX()<5 && f.cellY()>=0 && f.cellY()<4 && f.cellZ()>=0 && f.cellZ()<5);assertTrue(f.x0()>=0 && f.y0()>=0 && f.z0()>=0 && f.x1()<=1 && f.y1()<=1 && f.z1()<=1); }
        assertTrue(faces.stream().noneMatch(f->f.cellX()==2 && f.cellY()==1 && f.cellZ()==2));
    }
    @Test void arbitraryFractionalBoxesKeepAreaAndUvAtSeams() {
        var rng=new Random(73);
        for(int i=0;i<300;i++) {
            double x=rng.nextDouble()*2,y=rng.nextDouble(),z=rng.nextDouble()*2;
            var box=new CellMesh.Box(x,y,z,x+.2+rng.nextDouble()*2,y+.2+rng.nextDouble()*2,z+.2+rng.nextDouble()*2,"steel");
            var faces=CellMesh.split(box,5,4,5);assertEquals(box.area(),faces.stream().mapToDouble(CellMesh.Face::area).sum(),1e-8);
            for(var f:faces) { assertTrue(f.u0()>=0 && f.v0()>=0 && f.u1()<=1 && f.v1()<=1);assertTrue(f.u1()>f.u0() && f.v1()>f.v0()); }
        }
        var tops=CellMesh.split(new CellMesh.Box(0,.1,0,3,.5,1,"shell"),3,1,1).stream().filter(f->f.side()==1).sorted(Comparator.comparingInt(CellMesh.Face::cellX)).toList();
        assertEquals(3,tops.size());assertEquals(tops.get(0).u1(),tops.get(1).u0());assertEquals(tops.get(1).u1(),tops.get(2).u0());
    }
    @Test void invalidOrOutOfFootprintMeshesAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new CellMesh.Box(0,0,0,0,1,1,"shell"));assertThrows(IllegalArgumentException.class,()->new CellMesh.Box(-1,0,0,1,1,1,"shell"));
        assertThrows(IllegalArgumentException.class,()->CellMesh.split(new CellMesh.Box(0,0,0,4,1,1,"shell"),3,1,1));
    }
}
