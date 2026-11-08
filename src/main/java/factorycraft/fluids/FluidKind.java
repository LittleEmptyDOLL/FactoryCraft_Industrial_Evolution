package factorycraft.fluids;

public enum FluidKind {
    PIPE("fluid_pipe",1000),TANK("fluid_tank",16000),PUMP("electric_pump",1000);
    public final String id;
    public final int capacity;
    FluidKind(String id,int capacity) { this.id=id;this.capacity=capacity; }
}
