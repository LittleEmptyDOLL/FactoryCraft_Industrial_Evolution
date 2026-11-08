package factorycraft.simulation.benchmark;

import factorycraft.simulation.multiblock.CuboidLayout;
import java.util.*;

/** Deterministic, Minecraft-independent footprint. Only the isolated diagnostic server builds it. */
public final class BenchmarkPlan {
    public enum Profile {
        SMOKE(1,5,40,120), FULL(100,250,200,600);
        public final int loops,cells,warmup,ticks;
        Profile(int loops,int cells,int warmup,int ticks) { this.loops=loops;this.cells=cells;this.warmup=warmup;this.ticks=ticks; }
        public static Profile parse(String value) { return valueOf(value.toUpperCase(Locale.ROOT)); }
    }
    public enum Heading {
        NORTH(0,-1),EAST(1,0),SOUTH(0,1),WEST(-1,0);
        public final int x,z;
        Heading(int x,int z) { this.x=x;this.z=z; }
    }
    public enum Role { BELT,FURNACE,ASSEMBLER,INDUSTRIAL_FURNACE,CASING,GENERATOR,POLE,INSERTER,SOURCE }
    public record Cell(int x,int y,int z,Role role,Heading heading) {}
    private final List<Cell> blocks;
    public BenchmarkPlan(Profile profile) {
        var cells=new ArrayList<Cell>();
        for(int ring=0;ring<profile.loops;ring++) {
            int x=2+(ring%5)*28,z=2+(ring/5%2)*28,y=20+(ring/10)*3;
            for(int i=0;i<26;i++)cells.add(new Cell(x+i,y,z,Role.BELT,i==25 ? Heading.SOUTH:Heading.EAST));
            for(int i=1;i<26;i++)cells.add(new Cell(x+25,y,z+i,Role.BELT,i==25 ? Heading.WEST:Heading.SOUTH));
            for(int i=24;i>=0;i--)cells.add(new Cell(x+i,y,z+25,Role.BELT,i==0 ? Heading.NORTH:Heading.WEST));
            for(int i=24;i>=1;i--)cells.add(new Cell(x,y,z+i,Role.BELT,Heading.NORTH));
        }
        for(int i=0;i<profile.cells;i++) {
            int x=(i%25)*16,z=(i/25)*12;
            add(cells,x,0,z+1,Role.SOURCE,Heading.NORTH);
            add(cells,x+1,0,z+1,Role.INSERTER,Heading.EAST);
            machine(cells,x+2,z,Role.FURNACE);
            add(cells,x+5,0,z+1,Role.INSERTER,Heading.EAST);
            machine(cells,x+6,z,Role.ASSEMBLER);
            machine(cells,x+2,z+6,Role.FURNACE);
            machine(cells,x+6,z+6,Role.INDUSTRIAL_FURNACE);
            machine(cells,x+10,z,Role.GENERATOR);
            add(cells,x+5,0,z+4,Role.POLE,Heading.NORTH);
            add(cells,x+11,0,z+4,Role.POLE,Heading.NORTH);
            if(i+1<profile.cells && i%25<24)add(cells,x+17,0,z+4,Role.POLE,Heading.NORTH);
            if(i%25==0 && i+25<profile.cells)add(cells,x+5,0,z+10,Role.POLE,Heading.NORTH);
        }
        blocks=List.copyOf(cells);
    }
    private static void machine(List<Cell> cells,int x,int z,Role role) {
        add(cells,x,0,z,role,Heading.NORTH);
        for(var part:new CuboidLayout(3,3,3).parts(0,-1))add(cells,x+part.x(),part.y(),z+part.z(),Role.CASING,Heading.NORTH);
    }
    public static int poleCount(Profile p) { return p.cells*2+(p.cells-1); }
    private static void add(List<Cell> cells,int x,int y,int z,Role role,Heading heading) { cells.add(new Cell(x,y,z,role,heading)); }
    public List<Cell> blocks() { return blocks; }
}
