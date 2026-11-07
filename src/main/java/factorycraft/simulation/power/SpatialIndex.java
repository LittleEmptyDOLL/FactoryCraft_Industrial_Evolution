package factorycraft.simulation.power;

import java.util.*;

/** A rebuild-time index. A cursor spends at most one bucket probe or candidate per step. */
final class SpatialIndex<N> {
    private record Cell(int x,int y,int z) {}
    private final int size;
    private final Map<Cell,ArrayList<N>> buckets=new HashMap<>();
    SpatialIndex(int size) { this.size=size; }
    void add(PowerPoint p,N value) {
        buckets.computeIfAbsent(cell(p),k->new ArrayList<>()).add(value);
    }
    private Cell cell(PowerPoint p) { return new Cell(Math.floorDiv(p.x(),size),Math.floorDiv(p.y(),size),Math.floorDiv(p.z(),size)); }
    Cursor query(PowerPoint p) { return new Cursor(cell(p)); }
    final class Cursor {
        private final Cell origin;
        private int cell,entry;
        private List<N> bucket;
        Cursor(Cell origin) { this.origin=origin; }
        boolean done() { return cell==27 && (bucket==null || entry==bucket.size()); }
        N step() {
            if(bucket!=null && entry<bucket.size())return bucket.get(entry++);
            if(cell==27)return null;
            int c=cell++;
            bucket=buckets.get(new Cell(origin.x+c%3-1,origin.y+c/3%3-1,origin.z+c/9-1));
            entry=0;return null;
        }
    }
}
