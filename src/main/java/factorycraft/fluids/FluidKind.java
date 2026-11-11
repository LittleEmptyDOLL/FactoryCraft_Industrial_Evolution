package factorycraft.fluids;

public enum FluidKind {
  PIPE("fluid_pipe", factorycraft.simulation.fluids.FluidUnits.PIPE_CAPACITY),
  TANK("fluid_tank", factorycraft.simulation.fluids.FluidUnits.TANK_CAPACITY),
  PUMP("electric_pump", factorycraft.simulation.fluids.FluidUnits.PUMP_CAPACITY);
  public final String id;
  public final int capacity;

  FluidKind(String id, int capacity) {
    this.id = id;
    this.capacity = capacity;
  }
}
