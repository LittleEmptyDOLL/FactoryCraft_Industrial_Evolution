package factorycraft.nuclear;

import factorycraft.blockentity.FactoryBlockEntity;
import factorycraft.blocks.*;
import factorycraft.energy.*;
import factorycraft.fluids.*;
import factorycraft.multiblock.*;
import factorycraft.registry.Content;
import factorycraft.simulation.fluids.FluidCell;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.*;

/**
 * Shared physical ownership and presentation only; production remains in separate device classes.
 */
public abstract class NuclearOwner extends FactoryBlockEntity
    implements AssemblyOwner, MenuProvider {
  private final IndustrialAssembly assembly = new IndustrialAssembly(this);
  private boolean loaded, syncPending;
  private final IItemHandler empty = new ItemStackHandler(0);

  protected NuclearOwner(BlockPos p, BlockState s) {
    super(Content.NUCLEAR.get(), p, s);
  }

  public FactoryKind kind() {
    return ((FactoryBlock) getBlockState().getBlock()).kind;
  }

  public IndustrialAssembly assembly() {
    return assembly;
  }

  public boolean loaded() {
    return loaded && !isRemoved();
  }

  public boolean ready() {
    return loaded() && FluidConnections.canonical(this) && assembly.ready();
  }

  /** Loaded inventory access is distinct from permission to advance world simulation. */
  public boolean simulating() {
    return ready()
        && level instanceof net.minecraft.server.level.ServerLevel server
        && server.tickRateManager().runsNormally()
        && server.shouldTickBlocksAt(
            new net.minecraft.world.level.ChunkPos(worldPosition).toLong());
  }

  public IItemHandler inventory() {
    return empty;
  }

  public IItemHandler automation() {
    return inventory();
  }

  public FluidCell<FluidKey> cell(int channel) {
    return null;
  }

  public boolean accepts(int channel, FluidKey key) {
    return false;
  }

  public IEnergyStorage energyPort() {
    return null;
  }

  protected final void active(boolean active) {
    if (getBlockState().getValue(FactoryBlock.ACTIVE) != active)
      level.setBlock(worldPosition, getBlockState().setValue(FactoryBlock.ACTIVE, active), 3);
  }

  public abstract void tick();

  /**
   * Quanta/capacity, milli-C, fuel ticks, neighbors, status, water and steam; split on the wire.
   */
  public int[] metrics() {
    return new int[8];
  }

  public void changed() {
    syncPending = true;
    markPersistentState();
    if (level != null && !level.isClientSide) FluidSimulation.machineChanged(this);
  }

  protected final void syncPeriodic() {
    if (syncPending && level.getGameTime() % 20 == 0) {
      syncPending = false;
      sync();
    }
  }

  protected final void pullFluids() {
    for (var port : assembly.ports()) {
      if (port.port().input() < 0) continue;
      var to = cell(port.port().input());
      var from = FluidConnections.output(level, port.adjacent(), port.side().getOpposite());
      if (from != null
          && to != null
          && from.amount() > 0
          && accepts(port.port().input(), from.key())) from.transferTo(to, 6000, false);
    }
  }

  protected final void pushFluids() {
    for (var port : assembly.ports()) {
      if (port.port().output() < 0) continue;
      var from = cell(port.port().output());
      if (from == null || from.amount() == 0) continue;
      var to =
          FluidConnections.input(level, port.adjacent(), port.side().getOpposite(), from.key());
      if (to != null)
        from.transferTo(to, MachineFluidAccess.transferLimit(port.port(), from, to, 6000), false);
    }
  }

  public Component getDisplayName() {
    return Component.translatable("block.factorycraft." + kind().id);
  }

  public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
    return new NuclearMenu(id, inv, this);
  }

  public void onLoad() {
    super.onLoad();
    loaded = true;
    assembly.invalidate();
    if (!level.isClientSide) {
      if (this instanceof GridSupply || this instanceof GridConsumer) PowerGrid.add(this);
      if (this instanceof HeatOwner) HeatSimulation.changed(this);
    }
  }

  private void detach() {
    loaded = false;
    assembly.invalidate();
    if (level != null && !level.isClientSide) {
      PowerGrid.remove(this);
      HeatSimulation.remove(this);
    }
  }

  public void onChunkUnloaded() {
    detach();
    super.onChunkUnloaded();
  }

  public void setRemoved() {
    detach();
    super.setRemoved();
  }

  public void dropContents() {
    assembly.removeWhole(true);
  }

  protected void saveAdditional(CompoundTag n, HolderLookup.Provider r) {
    super.saveAdditional(n, r);
    assembly.save(n);
  }

  protected void loadAdditional(CompoundTag n, HolderLookup.Provider r) {
    super.loadAdditional(n, r);
    assembly.load(n);
  }
}
