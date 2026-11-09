package factorycraft.blockentity;

import factorycraft.blocks.*;
import factorycraft.energy.*;
import factorycraft.fluids.FluidConnections;
import factorycraft.production.PowerBuffer;
import factorycraft.registry.Content;
import factorycraft.simulation.inserters.ArmCycle;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.*;

/** One persistent hand for all arms. Rendering never extracts, delivers or bills work. */
public final class InserterBlockEntity extends FactoryBlockEntity implements GridConsumer {
  public final ItemStackHandler hand =
      new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
          sync();
        }
      };
  public factorycraft.circuits.CircuitBinding circuitBinding;
  public final PowerBuffer power = new PowerBuffer(2000, this::markPersistentState);
  private final ArmCycle cycle;
  private long phaseTick;
  private boolean loaded, working, packed, transferring, enabled = true, blacklist;
  private ResourceLocation filter;
  private Direction cachedFacing;
  private BlockPos sourcePos, targetPos;
  private final IItemHandler exposed =
      new IItemHandler() {
        public int getSlots() {
          return 1;
        }

        public ItemStack getStackInSlot(int s) {
          return loaded && !packed ? held().copy() : ItemStack.EMPTY;
        }

        public ItemStack insertItem(int s, ItemStack stack, boolean sim) {
          return stack;
        }

        public ItemStack extractItem(int s, int n, boolean sim) {
          return ItemStack.EMPTY;
        }

        public int getSlotLimit(int s) {
          return 1;
        }

        public boolean isItemValid(int s, ItemStack stack) {
          return false;
        }
      };
  public final IEnergyStorage energyPort =
      new IEnergyStorage() {
        public int receiveEnergy(int n, boolean sim) {
          return receiveGridEnergy(n, sim);
        }

        public int extractEnergy(int n, boolean sim) {
          return 0;
        }

        public int getEnergyStored() {
          return power.getEnergyStored();
        }

        public int getMaxEnergyStored() {
          return power.getMaxEnergyStored();
        }

        public boolean canExtract() {
          return false;
        }

        public boolean canReceive() {
          return electric();
        }
      };

  public InserterBlockEntity(BlockPos p, BlockState s) {
    super(Content.INSERTER.get(), p, s);
    cycle = new ArmCycle(((FactoryBlock) s.getBlock()).kind.armSpec());
  }

  public FactoryKind kind() {
    return ((FactoryBlock) getBlockState().getBlock()).kind;
  }

  public boolean electric() {
    return kind().electricInserter();
  }

  public int reach() {
    return kind().armSpec().reach();
  }

  public int phase() {
    return cycle.remaining();
  }

  public boolean enabled() {
    return enabled;
  }

  public boolean blacklist() {
    return blacklist;
  }

  public ResourceLocation filter() {
    return filter;
  }

  public ItemStack filterIcon() {
    return filter == null
        ? ItemStack.EMPTY
        : BuiltInRegistries.ITEM.getOptional(filter).map(ItemStack::new).orElse(ItemStack.EMPTY);
  }

  public boolean ready() {
    return loaded
        && !packed
        && FluidConnections.canonical(this)
        && level instanceof ServerLevel server
        && server.shouldTickBlocksAt(
            net.minecraft.world.level.ChunkPos.asLong(
                worldPosition.getX() >> 4, worldPosition.getZ() >> 4))
        && server.tickRateManager().runsNormally();
  }

  public boolean configure(boolean enable, boolean exclude, ResourceLocation id) {
    if (!electric() || !ready() || kind() != FactoryKind.FILTER_INSERTER && (exclude || id != null))
      return false;
    enabled = enable;
    blacklist = exclude;
    filter = id;
    sync();
    return true;
  }

  @Override
  public PowerBuffer gridBuffer() {
    return power;
  }

  @Override
  public int receiveGridEnergy(int n, boolean sim) {
    return electric() && ready() ? power.receiveEnergy(n, sim) : 0;
  }

  @Override
  public IItemHandler inventory() {
    return exposed;
  }

  public ItemStack held() {
    return hand.getStackInSlot(0);
  }

  public double visualPhase(float partial) {
    return Math.clamp(
        (cycle.remaining()
                - (electric() && !working
                    ? 0
                    : Math.max(0, level.getGameTime() + partial - phaseTick)))
            / kind().armSpec().swing(),
        0,
        1);
  }

  public BlockPos sourcePosition() {
    positions();
    return sourcePos;
  }

  public BlockPos targetPosition() {
    positions();
    return targetPos;
  }

  private void positions() {
    var f = getBlockState().getValue(FactoryBlock.FACING);
    if (f != cachedFacing) {
      cachedFacing = f;
      sourcePos = worldPosition.relative(f.getOpposite(), reach());
      targetPos = worldPosition.relative(f, reach());
    }
  }

  private boolean endpointReady(BlockPos p) {
    return level instanceof ServerLevel server
        && server.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4) != null
        && server.shouldTickBlocksAt(
            net.minecraft.world.level.ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4));
  }

  private boolean matches(ItemStack stack) {
    return filter == null
        || blacklist != filter.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
  }

  private void moving(boolean value) {
    if (electric() && value != working) {
      working = value;
      sendUpdate();
    }
  }

  public void tick() {
    if (transferring) return;
    transferring = true;
    try {
      work();
    } finally {
      transferring = false;
    }
  }

  private void work() {
    if (!ready()
        || electric() && (!enabled || !factorycraft.circuits.CircuitBinding.allowed(this))) {
      moving(false);
      return;
    }
    positions();
    if (!endpointReady(sourcePos) || !endpointReady(targetPos)) {
      moving(false);
      return;
    }
    if (cycle.remaining() > 0) {
      if (cycle.advance(power)) {
        markPersistentState();
        moving(true);
        if (held().isEmpty() && cycle.remaining() == 0) sync();
      } else moving(false);
      return;
    }
    var facing = getBlockState().getValue(FactoryBlock.FACING);
    var target =
        level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, facing.getOpposite());
    if (target == null) {
      moving(false);
      return;
    }
    if (held().isEmpty()) {
      var source = level.getCapability(Capabilities.ItemHandler.BLOCK, sourcePos, facing);
      if (source == null) {
        moving(false);
        return;
      }
      var belt = factorycraft.simulation.BeltSimulation.at(level, sourcePos);
      for (int slot = 0; slot < source.getSlots(); slot++) {
        if (belt != null && !belt.canSample(sourcePos, slot)) continue;
        var candidate = source.extractItem(slot, 1, true);
        if (candidate.isEmpty()
            || !matches(candidate)
            || !ItemHandlerHelper.insertItem(target, candidate, true).isEmpty()) continue;
        if (!cycle.pay(power)) {
          moving(false);
          return;
        }
        var extracted = source.extractItem(slot, 1, false);
        if (extracted.isEmpty()) {
          moving(false);
          return;
        }
        hand.setStackInSlot(0, extracted);
        cycle.begin();
        moving(true);
        sync();
        return;
      }
      moving(false);
    } else {
      if (!ItemHandlerHelper.insertItem(target, held().copy(), true).isEmpty()) {
        moving(false);
        return;
      }
      if (!cycle.pay(power)) {
        moving(false);
        return;
      }
      var remainder = ItemHandlerHelper.insertItem(target, held().copy(), false);
      if (remainder.getCount() != held().getCount()) {
        factorycraft.diagnostics.FactoryTelemetry.transferred(
            this, held().getCount() - remainder.getCount());
        hand.setStackInSlot(0, remainder);
        if (remainder.isEmpty()) cycle.begin();
        moving(true);
        sync();
      } else moving(false);
    }
  }

  @Override
  public void onLoad() {
    super.onLoad();
    loaded = true;
    if (!level.isClientSide && electric()) PowerGrid.add(this);
  }

  private void detach() {
    loaded = false;
    working = false;
    if (level != null && !level.isClientSide && electric()) PowerGrid.remove(this);
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
  public void dropContents() {
    if (level == null || level.isClientSide || packed) return;
    if (electric()) {
      var stack = new ItemStack(getBlockState().getBlock());
      saveToItem(stack, level.registryAccess());
      var portable = stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
      if (portable != null) {
        var data = portable.copyTag();
        data.remove("CircuitControl");
        stack.set(
            net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,
            net.minecraft.world.item.component.CustomData.of(data));
      }
      packed = true;
      hand.setStackInSlot(0, ItemStack.EMPTY);
      power.restore(0);
      net.minecraft.world.Containers.dropItemStack(
          level,
          worldPosition.getX() + .5,
          worldPosition.getY() + .5,
          worldPosition.getZ() + .5,
          stack);
    } else {
      var stack = hand.extractItem(0, Integer.MAX_VALUE, false);
      if (!stack.isEmpty())
        net.minecraft.world.Containers.dropItemStack(
            level,
            worldPosition.getX() + .5,
            worldPosition.getY() + .5,
            worldPosition.getZ() + .5,
            stack);
    }
  }

  @Override
  protected void saveAdditional(CompoundTag t, HolderLookup.Provider r) {
    super.saveAdditional(t, r);
    if (circuitBinding != null) t.put("CircuitControl", circuitBinding.save());
    t.putInt("Schema", 1);
    t.put("Hand", hand.serializeNBT(r));
    t.putInt("Phase", cycle.remaining());
    t.putLong("PhaseTick", level == null ? 0 : level.getGameTime());
    if (electric()) {
      var a = new CompoundTag();
      a.putInt("Schema", 1);
      a.putInt("Energy", power.getEnergyStored());
      a.putBoolean("Enabled", enabled);
      a.putBoolean("Blacklist", blacklist);
      if (filter != null) a.putString("Filter", filter.toString());
      t.put("Actuator", a);
    }
  }

  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider r) {
    var t = super.getUpdateTag(r);
    if (electric()) t.putBoolean("ArmMoving", working);
    return t;
  }

  @Override
  protected void loadAdditional(CompoundTag t, HolderLookup.Provider r) {
    super.loadAdditional(t, r);
    circuitBinding = factorycraft.circuits.CircuitBinding.load(t.getCompound("CircuitControl"));
    hand.deserializeNBT(r, t.getCompound("Hand"));
    cycle.restore(t.getInt("Phase"));
    phaseTick = t.getLong("PhaseTick");
    if (electric()) {
      var a = t.getCompound("Actuator");
      power.restore(a.getInt("Energy"));
      enabled = !a.contains("Enabled") || a.getBoolean("Enabled");
      blacklist = a.getBoolean("Blacklist");
      filter = a.contains("Filter") ? ResourceLocation.tryParse(a.getString("Filter")) : null;
      working = t.getBoolean("ArmMoving");
    }
  }
}
