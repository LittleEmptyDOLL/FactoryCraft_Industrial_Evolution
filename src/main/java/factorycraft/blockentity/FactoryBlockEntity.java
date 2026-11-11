package factorycraft.blockentity;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

public abstract class FactoryBlockEntity extends BlockEntity {
  private factorycraft.circuits.CircuitAttachment directCircuit;

  /** Nonowning pressure quotas are shared by all capability lookups at one physical boundary. */
  public final java.util.Map<
          String, factorycraft.simulation.fluids.FluidCell<factorycraft.fluids.FluidKey>>
      fluidPortViews = new java.util.HashMap<>();

  public final factorycraft.circuits.CircuitAttachment circuit(boolean create) {
    if (!factorycraft.circuits.CircuitAccess.supported(this)) return null;
    if (directCircuit == null && create)
      directCircuit = new factorycraft.circuits.CircuitAttachment(this);
    return directCircuit;
  }

  @Override
  public void onLoad() {
    super.onLoad();
    if (level != null && !level.isClientSide && directCircuit != null) {
      directCircuit.suspended = false;
      factorycraft.circuits.CircuitSimulation.add(directCircuit);
    }
  }

  @Override
  public void onChunkUnloaded() {
    if (directCircuit != null && level != null && !level.isClientSide) {
      directCircuit.suspended = true;
      factorycraft.circuits.CircuitSimulation.remove(directCircuit);
    }
    super.onChunkUnloaded();
  }

  @Override
  public void setRemoved() {
    if (directCircuit != null && level != null && !level.isClientSide) {
      var server = (net.minecraft.server.level.ServerLevel) level;
      var chunk =
          server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
      if (chunk != null
          && chunk.getBlockState(worldPosition).getBlock() != getBlockState().getBlock())
        factorycraft.circuits.CircuitSimulation.destroyed(directCircuit);
      factorycraft.circuits.CircuitSimulation.remove(directCircuit);
    }
    super.setRemoved();
  }

  @Override
  public void setBlockState(BlockState state) {
    var previous = getBlockState();
    super.setBlockState(state);
    var facing = factorycraft.blocks.FactoryBlock.FACING;
    if (directCircuit != null
        && level != null
        && !level.isClientSide
        && previous.hasProperty(facing)
        && state.hasProperty(facing)
        && previous.getValue(facing) != state.getValue(facing)) {
      factorycraft.circuits.CircuitSimulation.changed(directCircuit);
      factorycraft.circuits.CircuitNetwork.changed(directCircuit);
    }
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registry) {
    super.saveAdditional(tag, registry);
    if (directCircuit != null && directCircuit.configured)
      tag.put("DirectCircuit", directCircuit.save());
  }

  @Override
  public void saveToItem(ItemStack stack, HolderLookup.Provider registry) {
    super.saveToItem(stack, registry);
    var data = stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
    if (data != null) {
      var tag = data.copyTag();
      factorycraft.circuits.CircuitAttachment.portable(tag);
      stack.set(
          net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,
          net.minecraft.world.item.component.CustomData.of(tag));
    }
  }

  protected FactoryBlockEntity(BlockEntityType<?> t, BlockPos p, BlockState s) {
    super(t, p, s);
  }

  /** Persist circuit-only deltas without synchronous inventory neighbor notifications. */
  public final void markCircuitStatePersistent() {
    if (level instanceof net.minecraft.server.level.ServerLevel server) {
      var chunk =
          server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
      if (chunk != null) chunk.setUnsaved(true);
    }
  }

  public void sync() {
    setChanged();
    sendUpdate();
  }

  /** Internal motion/progress/FE changes persist without inventory comparator notifications. */
  protected final void markPersistentState() {
    if (level == null) return;
    if (getBlockState().hasAnalogOutputSignal()) setChanged();
    else level.blockEntityChanged(worldPosition);
  }

  protected final void sendUpdate() {
    factorycraft.diagnostics.FactoryTelemetry.synced(this);
    if (level != null && !level.isClientSide)
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
  }

  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider r) {
    return saveWithoutMetadata(r);
  }

  @Override
  public ClientboundBlockEntityDataPacket getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }

  @Override
  public net.neoforged.neoforge.client.model.data.ModelData getModelData() {
    var owner = factorycraft.multiblock.AssemblyAccess.owner(this);
    if (owner == null) return net.neoforged.neoforge.client.model.data.ModelData.EMPTY;
    var profile = owner.assembly().profile();
    var at = profile.controllerLocal();
    return net.neoforged.neoforge.client.model.data.ModelData.builder()
        .with(
            factorycraft.multiblock.MachineModelData.CELL,
            new factorycraft.multiblock.MachineModelData.Cell(
                profile, at.getX(), at.getY(), at.getZ(), owner.assembly().mirrored()))
        .build();
  }

  private boolean mirroredModelState;

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    if (tag.contains("DirectCircuit") && factorycraft.circuits.CircuitAccess.supported(this))
      circuit(true).load(tag.getCompound("DirectCircuit"));
    boolean mirror = tag.getCompound("Assembly").getBoolean("MirroredPorts");
    if (level != null && level.isClientSide && mirror != mirroredModelState) {
      mirroredModelState = mirror;
      requestModelDataUpdate();
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
  }

  public abstract IItemHandler inventory();

  public void dropContents() {
    if (level == null || level.isClientSide) return;
    var items = inventory();
    for (int i = 0; i < items.getSlots(); i++) {
      ItemStack stack = items.extractItem(i, Integer.MAX_VALUE, false);
      if (!stack.isEmpty())
        Containers.dropItemStack(
            level,
            worldPosition.getX() + .5,
            worldPosition.getY() + .5,
            worldPosition.getZ() + .5,
            stack);
    }
  }
}
