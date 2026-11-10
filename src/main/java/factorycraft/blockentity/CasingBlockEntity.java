package factorycraft.blockentity;

import factorycraft.blocks.FactoryBlock;
import factorycraft.fluids.FluidConnections;
import factorycraft.multiblock.*;
import factorycraft.registry.Content;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Stateless resource forwarding: only anchor/identity persist, never an inventory or fluid copy.
 */
public final class CasingBlockEntity extends FactoryBlockEntity {
  private BlockPos anchor;
  private UUID identity;
  private boolean loaded, mirrored;

  public CasingBlockEntity(BlockPos pos, BlockState state) {
    super(Content.CASING.get(), pos, state);
  }

  @Override
  public net.neoforged.neoforge.client.model.data.ModelData getModelData() {
    if (anchor == null) return net.neoforged.neoforge.client.model.data.ModelData.EMPTY;
    var profile = getBlockState().getValue(FactoryBlock.PROFILE);
    var facing = getBlockState().getValue(FactoryBlock.FACING);
    var delta = worldPosition.subtract(anchor);
    var right = facing.getClockWise();
    var local = profile.local(anchor, facing, worldPosition);
    return net.neoforged.neoforge.client.model.data.ModelData.builder()
        .with(
            MachineModelData.CELL,
            new MachineModelData.Cell(profile, local.getX(), local.getY(), local.getZ(), mirrored))
        .build();
  }

  public boolean loaded() {
    return loaded && !isRemoved();
  }

  public boolean virtualPart() {
    return true;
  }

  public boolean boundTo(BlockPos pos, UUID id) {
    return anchor != null && anchor.equals(pos) && identity != null && identity.equals(id);
  }

  public AssemblyOwner genericForRemoval() {
    if (level == null || anchor == null || !level.hasChunkAt(anchor)) return null;
    var owner = AssemblyAccess.owner(level.getBlockEntity(anchor));
    return owner != null && owner.assembly().owns(identity) ? owner : null;
  }

  public AssemblyOwner genericOwner() {
    var owner = genericForRemoval();
    return loaded() && FluidConnections.canonical(this) && owner != null && owner.assembly().ready()
        ? owner
        : null;
  }

  public MachineBlockEntity controllerForRemoval() {
    var owner = genericForRemoval();
    return owner instanceof MachineBlockEntity m ? m : null;
  }

  public MachineBlockEntity controllerForInteraction() {
    return loaded() ? controllerForRemoval() : null;
  }

  public MachineBlockEntity owner() {
    var owner = genericOwner();
    return owner instanceof MachineBlockEntity m ? m : null;
  }

  public ItemStack controllerItem() {
    var owner = genericForRemoval();
    return owner == null
        ? new ItemStack(
            net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    "factorycraft", getBlockState().getValue(FactoryBlock.PROFILE).id)))
        : new ItemStack(owner.factoryEntity().getBlockState().getBlock().asItem());
  }

  public boolean availableFor(BlockPos pos, UUID oldId) {
    if (anchor == null || boundTo(pos, oldId)) return true;
    repairOrphan();
    return anchor == null;
  }

  public void bind(BlockPos pos, UUID id, boolean generated) {
    anchor = pos.immutable();
    identity = id;
  }

  public void refreshAppearance() {
    if (level != null && !level.isClientSide) {
      var owner = genericForRemoval();
      var state = getBlockState().setValue(FactoryBlock.ACTIVE, true);
      if (owner != null) mirrored = owner.assembly().mirrored();
      if (owner != null)
        state =
            state
                .setValue(FactoryBlock.PROFILE, owner.assembly().profile())
                .setValue(
                    FactoryBlock.FACING,
                    owner.factoryEntity().getBlockState().getValue(FactoryBlock.FACING));
      level.invalidateCapabilities(worldPosition);
      level.setBlock(worldPosition, state, 3);
      sync();
    }
  }

  public void unbind() {
    anchor = null;
    identity = null;
  }

  private void repairOrphan() {
    if (anchor == null || level == null || level.isClientSide || !level.hasChunkAt(anchor)) return;
    if (genericForRemoval() == null) level.removeBlock(worldPosition, false);
  }

  private void suspendOwner() {
    var owner = genericForRemoval();
    if (owner != null) owner.assembly().invalidate();
  }

  public void onLoad() {
    super.onLoad();
    loaded = true;
    repairOrphan();
    suspendOwner();
  }

  public void onChunkUnloaded() {
    loaded = false;
    suspendOwner();
    super.onChunkUnloaded();
  }

  public void setRemoved() {
    loaded = false;
    suspendOwner();
    super.setRemoved();
  }

  public void dropContents() {
    var owner = genericForRemoval();
    if (owner != null) owner.assembly().removeWhole(true);
  }

  private IItemHandler items(Direction side) {
    var owner = genericOwner();
    return owner == null
        ? null
        : owner instanceof BeltBlockEntity belt
            ? belt.inventoryAt(worldPosition)
            : AssemblyAccess.items(owner, side);
  }

  private final IItemHandler itemPort =
      new IItemHandler() {
        public int getSlots() {
          var h = items(null);
          return h == null ? 0 : h.getSlots();
        }

        public ItemStack getStackInSlot(int i) {
          var h = items(null);
          return h == null ? ItemStack.EMPTY : h.getStackInSlot(i).copy();
        }

        public ItemStack insertItem(int i, ItemStack s, boolean sim) {
          var h = items(null);
          return h == null ? s : h.insertItem(i, s, sim);
        }

        public ItemStack extractItem(int i, int n, boolean sim) {
          var h = items(null);
          return h == null ? ItemStack.EMPTY : h.extractItem(i, n, sim);
        }

        public int getSlotLimit(int i) {
          var h = items(null);
          return h == null ? 0 : h.getSlotLimit(i);
        }

        public boolean isItemValid(int i, ItemStack s) {
          var h = items(null);
          return h != null && h.isItemValid(i, s);
        }
      };

  public IItemHandler inventory() {
    return itemPort;
  }

  public final IEnergyStorage energy =
      new IEnergyStorage() {
        private IEnergyStorage port() {
          var owner = genericOwner();
          return owner == null ? null : AssemblyAccess.energy(owner);
        }

        public int receiveEnergy(int n, boolean sim) {
          var p = port();
          return p == null ? 0 : p.receiveEnergy(n, sim);
        }

        public int extractEnergy(int n, boolean sim) {
          var p = port();
          return p == null ? 0 : p.extractEnergy(n, sim);
        }

        public int getEnergyStored() {
          var p = port();
          return p == null ? 0 : p.getEnergyStored();
        }

        public int getMaxEnergyStored() {
          var p = port();
          return p == null ? 0 : p.getMaxEnergyStored();
        }

        public boolean canReceive() {
          var p = port();
          return p != null && p.canReceive();
        }

        public boolean canExtract() {
          var p = port();
          return p != null && p.canExtract();
        }
      };

  public BlockPos controllerPosition() {
    return anchor;
  }

  public boolean fluidPort() {
    if (anchor == null) return false;
    var p = getBlockState().getValue(FactoryBlock.PROFILE);
    var facing = getBlockState().getValue(FactoryBlock.FACING);
    return MachinePorts.definitions(p, mirrored).stream()
        .anyMatch(port -> port.position(p, anchor, facing).equals(worldPosition));
  }

  public IFluidHandler fluid(Direction side) {
    var owner = genericOwner();
    return owner == null ? null : AssemblyAccess.fluid(owner, worldPosition, side);
  }

  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
    super.saveAdditional(tag, r);
    tag.putInt("Schema", 4);
    tag.putBoolean("MirroredPorts", mirrored);
    if (anchor != null) tag.putLong("Controller", anchor.asLong());
    if (identity != null) tag.putUUID("AssemblyIdentity", identity);
  }

  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
    super.loadAdditional(tag, r);
    mirrored = tag.getBoolean("MirroredPorts");
    anchor =
        tag.getInt("Schema") == 4 && tag.contains("Controller")
            ? BlockPos.of(tag.getLong("Controller"))
            : null;
    identity = tag.hasUUID("AssemblyIdentity") ? tag.getUUID("AssemblyIdentity") : null;
    if (level != null && level.isClientSide) {
      if (genericForRemoval() instanceof PoleBlockEntity pole) pole.invalidateVisual();
      requestModelDataUpdate();
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
  }
}
