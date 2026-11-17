package factorycraft.nuclear;

import factorycraft.simulation.nuclear.HeatStore;
import java.util.List;
import net.minecraft.core.*;

/** Physical contact data does not own heat; its controller store does. */
public interface HeatOwner {
  record Port(BlockPos position, Direction side) {}

  NuclearOwner heatController();

  HeatStore heat();

  List<Port> heatPorts();

  long maximumTransfer();
}
