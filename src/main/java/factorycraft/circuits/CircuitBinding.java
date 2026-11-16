package factorycraft.circuits;

import factorycraft.blockentity.FactoryBlockEntity;

/** Common direct-circuit enable check. There are no separate controller/reader devices. */
public final class CircuitBinding {
  public static boolean allowed(FactoryBlockEntity device) {
    return CircuitSimulation.permitted(device);
  }

  private CircuitBinding() {}
}
