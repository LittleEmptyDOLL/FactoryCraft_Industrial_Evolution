package factorycraft.circuits;

import factorycraft.blockentity.*;
import factorycraft.fluids.FluidConnections;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** An explicit persisted attachment keeps a device fail-closed when its controller is unloaded. */
public record CircuitBinding(BlockPos controller, UUID identity) {
  public CircuitBinding {
    controller = controller.immutable();
  }

  public boolean allows(FactoryBlockEntity device) {
    var be = FluidConnections.at(device.getLevel(), controller);
    return be instanceof CircuitNodeBlockEntity node
        && node.assembly().owns(identity)
        && node.active
        && node.available()
        && node.gate
        && CircuitSimulation.controls(node, device)
        && CircuitSimulation.inputReady(node);
  }

  public CompoundTag save() {
    var t = new CompoundTag();
    t.putLong("Controller", controller.asLong());
    t.putUUID("Identity", identity);
    return t;
  }

  public static CircuitBinding load(CompoundTag t) {
    if (t.isEmpty()) return null;
    try {
      return new CircuitBinding(BlockPos.of(t.getLong("Controller")), t.getUUID("Identity"));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public static CircuitBinding get(FactoryBlockEntity b) {
    return b instanceof InserterBlockEntity i
        ? i.circuitBinding
        : b instanceof FluidBlockEntity f
            ? f.circuitBinding
            : b instanceof BeltBlockEntity belt ? belt.circuitBinding : null;
  }

  public static void set(FactoryBlockEntity b, CircuitBinding binding) {
    var old = get(b);
    if (b instanceof InserterBlockEntity i) i.circuitBinding = binding;
    else if (b instanceof FluidBlockEntity f) f.circuitBinding = binding;
    else if (b instanceof BeltBlockEntity belt && belt.kind().splitter()) {
      belt.circuitBinding = binding;
      belt.refreshCircuitControl();
      CircuitSimulation.watchSplitter(belt, old);
      belt.sync();
    } else return;
    b.setChanged();
    if (b instanceof FluidBlockEntity f) factorycraft.fluids.FluidSimulation.activatePump(f);
  }

  public static boolean allowed(FactoryBlockEntity b) {
    var binding = get(b);
    if (binding == null) return true;
    var node = FluidConnections.at(b.getLevel(), binding.controller());
    var server = (net.minecraft.server.level.ServerLevel) b.getLevel();
    var chunk =
        server
            .getChunkSource()
            .getChunkNow(binding.controller().getX() >> 4, binding.controller().getZ() >> 4);
    boolean destroyed =
        chunk != null
            && !(chunk.getBlockState(binding.controller()).getBlock()
                    instanceof factorycraft.blocks.FactoryBlock block
                && block.kind == factorycraft.blocks.FactoryKind.CIRCUIT_CONTROLLER);
    boolean replaced =
        node instanceof CircuitNodeBlockEntity n
            && n.loaded()
            && n.assembly().identity() != null
            && !n.assembly().identity().equals(binding.identity());
    if (destroyed || replaced) {
      set(b, null);
      return true;
    }
    return binding.allows(b);
  }
}
