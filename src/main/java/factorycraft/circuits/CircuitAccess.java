package factorycraft.circuits;

import factorycraft.blockentity.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapability;

/**
 * Explicit opt-in capability. Add-ons may use CircuitContainers.attach without changing their BE
 * tags.
 */
public final class CircuitAccess {
  public static final BlockCapability<CircuitAttachment, Void> CAPABILITY =
      BlockCapability.createVoid(
          ResourceLocation.fromNamespaceAndPath("factorycraft", "circuit_contact"),
          CircuitAttachment.class);

  public static boolean supported(BlockEntity be) {
    return be instanceof factorycraft.circuits.CircuitNodeBlockEntity
        || be instanceof InserterBlockEntity
        || be instanceof BeltBlockEntity
        || be instanceof MachineBlockEntity
        || be instanceof FluidBlockEntity
        || be instanceof ThermalBlockEntity
        || be instanceof LabBlockEntity
        || be instanceof PoleBlockEntity;
  }

  public static CircuitAttachment own(BlockEntity be, boolean create) {
    if (be instanceof CasingBlockEntity part)
      be = part.genericForRemoval() == null ? null : part.genericForRemoval().factoryEntity();
    else if (be instanceof TransportPartBlockEntity part) be = part.owner();
    return supported(be) ? ((FactoryBlockEntity) be).circuit(create) : null;
  }

  public static CircuitAttachment at(Level level, BlockPos physical) {
    var be =
        level instanceof net.minecraft.server.level.ServerLevel
            ? factorycraft.fluids.FluidConnections.at(level, physical)
            : level.hasChunkAt(physical) ? level.getBlockEntity(physical) : null;
    if (be == null) return null;
    var own = own(be, true);
    return own != null
        ? own
        : level.getCapability(CAPABILITY, physical, be.getBlockState(), be, null);
  }

  private CircuitAccess() {}
}
