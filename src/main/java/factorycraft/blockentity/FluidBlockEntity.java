package factorycraft.blockentity;

import factorycraft.blocks.FluidDeviceBlock;
import factorycraft.energy.*;
import factorycraft.fluids.*;
import factorycraft.production.PowerBuffer;
import factorycraft.registry.Content;
import factorycraft.simulation.fluids.FluidCell;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.*;

/**
 * One canonical, persisted fluid cell. Cached capability objects recheck lifecycle and orientation.
 */
public final class FluidBlockEntity extends FactoryBlockEntity
    implements factorycraft.multiblock.AssemblyOwner, MenuProvider, GridConsumer {
  private static final IItemHandler EMPTY = new ItemStackHandler(0);
  private final factorycraft.multiblock.IndustrialAssembly assembly;

  public factorycraft.multiblock.IndustrialAssembly assembly() {
    return assembly;
  }

  public factorycraft.circuits.CircuitBinding circuitBinding;
  private boolean loaded, packed;
  private java.util.UUID fluidOwner = java.util.UUID.randomUUID();

  public java.util.UUID fluidOwner() {
    return fluidOwner;
  }

  private final FluidKind kind;
  private final FluidCell<FluidKey> cell;
  private net.minecraft.world.level.chunk.LevelChunk ownedChunk;
  public final PowerBuffer power = new PowerBuffer(16000, this::energyChanged);
  private final IFluidHandler[] ports = new IFluidHandler[7];
  public final net.neoforged.neoforge.energy.IEnergyStorage energyPort =
      new net.neoforged.neoforge.energy.IEnergyStorage() {
        public int receiveEnergy(int n, boolean simulate) {
          return accessible() && kind == FluidKind.PUMP ? power.receiveEnergy(n, simulate) : 0;
        }

        public int extractEnergy(int n, boolean simulate) {
          return 0;
        }

        public int getEnergyStored() {
          return readable() ? power.getEnergyStored() : 0;
        }

        public int getMaxEnergyStored() {
          return 16000;
        }

        public boolean canExtract() {
          return false;
        }

        public boolean canReceive() {
          return accessible() && kind == FluidKind.PUMP;
        }
      };

  public FluidBlockEntity(BlockPos pos, BlockState state) {
    super(Content.FLUID.get(), pos, state);
    kind = FluidBlocks.kind(state);
    cell = new FluidCell<>(kind.capacity, this::fluidChanged);
    assembly = kind == FluidKind.PIPE ? null : new factorycraft.multiblock.IndustrialAssembly(this);
    for (int i = 0; i < ports.length; i++)
      ports[i] = new Port(i == 6 ? null : Direction.from3DDataValue(i));
  }

  public FluidKind kind() {
    return kind;
  }

  public FluidCell<FluidKey> cell() {
    return FluidSimulation.owned(this);
  }

  public FluidCell<FluidKey> localCell() {
    return cell;
  }

  public FluidCell<FluidKey> hydraulic(BlockPos pos, Direction side) {
    return FluidSimulation.hydraulic(this, pos, side);
  }

  public boolean loaded() {
    return loaded && !isRemoved();
  }

  public boolean simulationAvailable() {
    if (packed || !loaded() || !(level instanceof net.minecraft.server.level.ServerLevel server))
      return false;
    var chunk =
        server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
    if (chunk == null || chunk.getBlockEntities().get(worldPosition) != this) return false;
    if (ownedChunk == null) ownedChunk = chunk;
    return ownedChunk == chunk && (assembly == null || assembly.ready());
  }

  private boolean accessible() {
    return simulationAvailable();
  }

  private boolean readable() {
    if (level == null || isRemoved()) return false;
    return level.isClientSide
        ? level.hasChunkAt(worldPosition) && level.getBlockEntity(worldPosition) == this
        : simulationAvailable();
  }

  public Direction facing() {
    return kind == FluidKind.PIPE
        ? Direction.NORTH
        : getBlockState().getValue(FluidDeviceBlock.FACING);
  }

  private java.util.List<factorycraft.multiblock.MachinePorts.Port> mappedPorts() {
    return factorycraft.multiblock.MachinePorts.forController(this);
  }

  public BlockPos inputNozzle() {
    if (kind == FluidKind.PIPE) return worldPosition;
    for (var p : assembly.ports()) if (p.port().input() >= 0) return p.position();
    throw new IllegalStateException("No input port");
  }

  public BlockPos outputNozzle() {
    if (kind == FluidKind.PIPE) return worldPosition;
    for (var p : assembly.ports()) if (p.port().output() >= 0) return p.position();
    throw new IllegalStateException("No output port");
  }

  public record Connection(BlockPos pos, Direction side) {}

  public java.util.List<Connection> connections() {
    if (kind == FluidKind.PIPE)
      return java.util.Arrays.stream(Direction.values())
          .map(d -> new Connection(worldPosition, d))
          .toList();
    return mappedPorts().stream()
        .map(
            p ->
                new Connection(
                    p.position(assembly.profile(), worldPosition, facing()), p.side(facing())))
        .toList();
  }

  public IFluidHandler structurePort(BlockPos pos, Direction side) {
    return kind == FluidKind.PIPE
        ? (pos.equals(worldPosition) ? ports[side == null ? 6 : side.get3DDataValue()] : null)
        : factorycraft.multiblock.MachineFluidAccess.port(this, pos, side);
  }

  public IFluidHandler port(Direction side) {
    return structurePort(worldPosition, side);
  }

  public FluidStack stored() {
    var c = cell();
    return c.amount() == 0 ? FluidStack.EMPTY : c.key().stack(c.amount());
  }

  @Override
  public IItemHandler inventory() {
    return EMPTY;
  }

  @Override
  public PowerBuffer gridBuffer() {
    return power;
  }

  @Override
  public int receiveGridEnergy(int offered, boolean simulate) {
    return energyPort.receiveEnergy(offered, simulate);
  }

  private void fluidChanged() {
    if (level == null || level.isClientSide || !loaded()) return;
    if (ownedChunk != null) ownedChunk.setUnsaved(true);
    markPersistentState();
    FluidSimulation.changed(this);
  }

  private void energyChanged() {
    if (level == null || level.isClientSide || !loaded()) return;
    if (ownedChunk != null) ownedChunk.setUnsaved(true);
    markPersistentState();
    FluidSimulation.activatePump(this);
  }

  public void flushUpdate() {
    sendUpdate();
  }

  private final class Port implements IFluidHandler {
    final Direction side;
    final java.util.UUID identity = fluidOwner;

    Port(Direction side) {
      this.side = side;
    }

    boolean input() {
      return identity.equals(fluidOwner)
          && accessible()
          && (kind != FluidKind.PUMP || side == null || side == facing().getOpposite());
    }

    boolean output() {
      return identity.equals(fluidOwner)
          && accessible()
          && (kind != FluidKind.PUMP || side == null || side == facing());
    }

    public int getTanks() {
      return identity.equals(fluidOwner) && accessible() ? 1 : 0;
    }

    public FluidStack getFluidInTank(int tank) {
      java.util.Objects.checkIndex(tank, 1);
      if (!identity.equals(fluidOwner) || !accessible()) return FluidStack.EMPTY;
      var c = hydraulic(worldPosition, side);
      return c == null || c.amount() == 0 ? FluidStack.EMPTY : c.key().stack(c.amount());
    }

    public int getTankCapacity(int tank) {
      java.util.Objects.checkIndex(tank, 1);
      if (!identity.equals(fluidOwner) || !accessible()) return 0;
      var c = hydraulic(worldPosition, side);
      return c == null ? 0 : Math.max(c.capacity(), c.amount());
    }

    public boolean isFluidValid(int tank, FluidStack stack) {
      java.util.Objects.checkIndex(tank, 1);
      var c = hydraulic(worldPosition, side);
      return input() && !stack.isEmpty() && c != null && c.accepts(new FluidKey(stack));
    }

    public int fill(FluidStack stack, FluidAction action) {
      var c = hydraulic(worldPosition, side);
      return !input() || stack.isEmpty() || c == null
          ? 0
          : c.fill(new FluidKey(stack), stack.getAmount(), action.simulate());
    }

    public FluidStack drain(FluidStack stack, FluidAction action) {
      var c = hydraulic(worldPosition, side);
      if (!output()
          || stack.isEmpty()
          || c == null
          || c.key() == null
          || !c.key().equals(new FluidKey(stack))) return FluidStack.EMPTY;
      return drain(stack.getAmount(), action);
    }

    public FluidStack drain(int amount, FluidAction action) {
      var c = hydraulic(worldPosition, side);
      if (!output() || amount <= 0 || c == null || c.amount() == 0) return FluidStack.EMPTY;
      var fluid = c.key();
      int extracted = c.drain(amount, action.simulate());
      return fluid.stack(extracted);
    }
  }

  @Override
  public void onLoad() {
    super.onLoad();
    if (level.isClientSide || loaded) return;
    loaded = true;
    if (assembly != null) assembly.invalidate();
    simulationAvailable();
    FluidSimulation.register(this);
    if (kind == FluidKind.PUMP) PowerGrid.add(this);
  }

  private void detach() {
    if (!loaded || level == null || level.isClientSide) return;
    loaded = false;
    ownedChunk = null;
    if (assembly != null) assembly.invalidate();
    FluidSimulation.unregister(this);
    if (kind == FluidKind.PUMP) PowerGrid.remove(this);
  }

  @Override
  public void onChunkUnloaded() {
    detach();
    super.onChunkUnloaded();
  }

  @Override
  public void setRemoved() {
    detach();
    super.setRemoved();
  }

  @Override
  public void setBlockState(BlockState state) {
    boolean rotated =
        kind == FluidKind.PUMP
            && getBlockState().getValue(FluidDeviceBlock.FACING)
                != state.getValue(FluidDeviceBlock.FACING);
    super.setBlockState(state);
    if (rotated && loaded()) {
      level.invalidateCapabilities(worldPosition);
      FluidSimulation.topologyChanged(this);
    }
  }

  public final ContainerData data =
      new ContainerData() {
        public int get(int index) {
          var v = FluidSimulation.view(FluidBlockEntity.this);
          int id = v.key() == null ? 0 : BuiltInRegistries.FLUID.getId(v.key().stack(1).getFluid());
          return switch (index) {
            case 0 -> (int) v.amount() & 65535;
            case 1 -> (int) (v.amount() >>> 16) & 65535;
            case 2 -> (int) v.capacity() & 65535;
            case 3 -> (int) (v.capacity() >>> 16) & 65535;
            case 4 -> power.getEnergyStored();
            case 5 -> id & 65535;
            case 6 -> id >>> 16;
            case 7 -> v.cells() & 65535;
            case 8 -> v.cells() >>> 16;
            case 9 -> v.status();
            case 10 -> v.conflicts();
            case 11 -> Math.min(65535, v.x());
            case 12 -> Math.min(65535, v.z());
            case 13 -> Math.min(65535, v.y());
            case 14 -> (int) v.id() & 65535;
            case 15 -> (int) (v.id() >>> 16) & 65535;
            case 16 -> (int) (v.amount() >>> 32) & 65535;
            case 17 -> (int) (v.amount() >>> 48) & 65535;
            case 18 -> (int) (v.capacity() >>> 32) & 65535;
            case 19 -> (int) (v.capacity() >>> 48) & 65535;
            default -> 0;
          };
        }

        public void set(int index, int value) {}

        public int getCount() {
          return 20;
        }
      };

  @Override
  public void dropContents() {
    if (assembly != null) assembly.removeWhole(true);
  }

  @Override
  public Component getDisplayName() {
    return Component.translatable("block.factorycraft." + kind.id);
  }

  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
    return new FluidMenu(id, inventory, FluidBlockEntity.this, data);
  }

  @Override
  public void saveToItem(ItemStack stack, HolderLookup.Provider registries) {
    if (level != null && !level.isClientSide && !packed) {
      FluidSimulation.unregister(this);
      packed = true;
      PowerGrid.remove(this);
    }
    super.saveToItem(stack, registries);
    var saved = stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
    if (saved != null) {
      var tag = saved.copyTag();
      tag.remove("FluidOwner");
      stack.set(
          net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,
          net.minecraft.world.item.component.CustomData.of(tag));
    }
    if (level != null && !level.isClientSide) {
      cell.restore(null, 0);
      power.restore(0);
      markCircuitStatePersistent();
    }
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    if (circuitBinding != null) tag.put("CircuitControl", circuitBinding.save());
    if (assembly != null) assembly.save(tag);
    tag.putInt("Schema", 2);
    tag.putUUID("FluidOwner", fluidOwner);
    tag.put("StoredFluid", stored().saveOptional(registries));
    tag.putInt("Energy", power.getEnergyStored());
  }

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    boolean reconnect = FluidSimulation.registered(this);
    if (reconnect) FluidSimulation.unregister(this);
    packed = false;
    fluidOwner =
        tag.hasUUID("FluidOwner") ? tag.getUUID("FluidOwner") : java.util.UUID.randomUUID();
    for (int i = 0; i < ports.length; i++)
      ports[i] = new Port(i == 6 ? null : Direction.from3DDataValue(i));
    super.loadAdditional(tag, registries);
    circuitBinding = factorycraft.circuits.CircuitBinding.load(tag.getCompound("CircuitControl"));
    if (assembly != null) assembly.load(tag);
    var fluid = FluidStack.parseOptional(registries, tag.getCompound("StoredFluid"));
    cell.restore(fluid.isEmpty() ? null : new FluidKey(fluid), fluid.getAmount());
    power.restore(tag.getInt("Energy"));
    if (reconnect) FluidSimulation.register(this);
  }
}
