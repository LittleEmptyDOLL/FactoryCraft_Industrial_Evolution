package factorycraft.blockentity;

import factorycraft.belts.*;
import factorycraft.fluids.FluidConnections;
import factorycraft.registry.Content;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.items.IItemHandler;

/** Physical reservation only: no lane, item, node or resource copy. */
public final class TransportPartBlockEntity extends FactoryBlockEntity {
  private BlockPos anchor;
  private UUID identity;
  private BeltModelData.Cell cell;
  private boolean loaded;

  public TransportPartBlockEntity(BlockPos p, BlockState s) {
    super(Content.TRANSPORT_PART.get(), p, s);
  }

  public void bind(BeltBlockEntity owner) {
    anchor = owner.getBlockPos();
    identity = owner.transportIdentity();
    cell = owner.modelCell(worldPosition);
    sync();
  }

  public BeltBlockEntity ownerForRemoval() {
    var be = anchor == null ? null : FluidConnections.at(level, anchor);
    return be instanceof BeltBlockEntity belt
            && !belt.isRemoved()
            && identity != null
            && identity.equals(belt.transportIdentity())
        ? belt
        : null;
  }

  public BeltBlockEntity owner() {
    var belt = ownerForRemoval();
    return loaded && belt != null && belt.loaded() ? belt : null;
  }

  public boolean boundTo(BlockPos pos, UUID id) {
    return pos.equals(anchor) && id != null && id.equals(identity);
  }

  public boolean belongs(BlockPos pos, UUID id) {
    return loaded && boundTo(pos, id);
  }

  public ModelData getModelData() {
    return cell == null
        ? ModelData.EMPTY
        : ModelData.builder().with(BeltModelData.CELL, cell).build();
  }

  public BeltModelData.Cell cell() {
    return cell;
  }

  public IItemHandler inventory() {
    return new IItemHandler() {
      private IItemHandler port() {
        var b = owner();
        return b == null ? null : b.inventoryAt(worldPosition);
      }

      public int getSlots() {
        var p = port();
        return p == null ? 0 : p.getSlots();
      }

      public ItemStack getStackInSlot(int s) {
        var p = port();
        return p == null ? ItemStack.EMPTY : p.getStackInSlot(s);
      }

      public ItemStack insertItem(int s, ItemStack v, boolean sim) {
        var p = port();
        return p == null ? v : p.insertItem(s, v, sim);
      }

      public ItemStack extractItem(int s, int n, boolean sim) {
        var p = port();
        return p == null ? ItemStack.EMPTY : p.extractItem(s, n, sim);
      }

      public int getSlotLimit(int s) {
        return 1;
      }

      public boolean isItemValid(int s, ItemStack v) {
        var p = port();
        return p != null && p.isItemValid(s, v);
      }
    };
  }

  private int orphanChecks;

  public void tick() {
    if (level == null
        || level.isClientSide
        || ++orphanChecks % 40 != 0
        || anchor == null
        || !level.hasChunkAt(anchor)) return;
    var be = FluidConnections.at(level, anchor);
    // A loaded controller can still be waiting for its own onLoad callback. Never treat that as
    // demolition.
    if (be instanceof BeltBlockEntity belt
        && identity != null
        && identity.equals(belt.transportIdentity())) return;
    boolean restoring = level.restoringBlockSnapshots;
    level.restoringBlockSnapshots = true;
    try {
      level.removeBlock(worldPosition, false);
    } finally {
      level.restoringBlockSnapshots = restoring;
    }
  }

  public void dropContents() {
    var b = ownerForRemoval();
    if (b != null) b.removeTransport(true);
  }

  public void onLoad() {
    super.onLoad();
    loaded = true;
    var controller = ownerForRemoval();
    if (controller != null) bind(controller);
    notifyOwner();
  }

  public void onChunkUnloaded() {
    loaded = false;
    notifyOwner();
    super.onChunkUnloaded();
  }

  public void setRemoved() {
    loaded = false;
    notifyOwner();
    super.setRemoved();
  }

  private void notifyOwner() {
    var b = anchor == null ? null : FluidConnections.at(level, anchor);
    if (b instanceof BeltBlockEntity belt) {
      factorycraft.simulation.BeltSimulation.topologyChanged(belt);
      if (belt.circuit(false) != null)
        factorycraft.circuits.CircuitSimulation.changed(belt.circuit(false));
    }
  }

  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
    super.saveAdditional(tag, r);
    if (anchor != null) tag.putLong("Controller", anchor.asLong());
    if (identity != null) tag.putUUID("Identity", identity);
    if (cell != null) TransportPlan.saveCell(tag, cell);
  }

  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
    super.loadAdditional(tag, r);
    anchor = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
    identity = tag.hasUUID("Identity") ? tag.getUUID("Identity") : null;
    cell = TransportPlan.loadCell(tag);
    if (level != null && level.isClientSide) {
      requestModelDataUpdate();
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
  }
}
