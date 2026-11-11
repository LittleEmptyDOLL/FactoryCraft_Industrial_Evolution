package factorycraft.blockentity;

import factorycraft.blocks.*;
import factorycraft.energy.*;
import factorycraft.fluids.FluidConnections;
import factorycraft.production.PowerBuffer;
import factorycraft.registry.Content;
import factorycraft.simulation.inserters.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.*;

/** One counted, component-exact persistent hand. Only paid server phases move this hand. */
public final class InserterBlockEntity extends FactoryBlockEntity implements GridConsumer {
  public final ItemStackHandler hand =
      new ItemStackHandler(1) {
        protected void onContentsChanged(int slot) {
          changed();
        }
      };
  private boolean fuelCommit;

  /**
   * Slot 1 owns container remainders or an anomalous actual extraction, never a second ordinary
   * hand.
   */
  public final ItemStackHandler fuel =
      new ItemStackHandler(2) {
        public boolean isItemValid(int slot, ItemStack stack) {
          return slot == 0 && burner() && fuelTime(stack) > 0;
        }

        protected void onContentsChanged(int slot) {
          if (!fuelCommit) changed();
        }
      };

  public factorycraft.circuits.CircuitBinding circuitBinding;
  public final PowerBuffer power = new PowerBuffer(2000, this::markPersistentState);
  private final ArmCycle cycle;
  private final java.util.function.IntPredicate debit = this::spend;
  private long phaseTick, fuelEnergy, researchTick = Long.MIN_VALUE;
  private int stage, gatherUnits, manualLimit, capacityLevel, beltLevel, status;
  private boolean loaded,
      working,
      packed,
      transferring,
      enabled = true,
      forcedDrop,
      dropCredit,
      pendingDrop;
  private ItemFilters filters = ItemFilters.empty();
  private String researchScope = "";
  private Direction cachedFacing;
  private BlockPos sourcePos, targetPos;
  private final IItemHandler exposed =
      new IItemHandler() {
        public int getSlots() {
          return 3;
        }

        public ItemStack getStackInSlot(int slot) {
          Objects.checkIndex(slot, 3);
          return !ready()
              ? ItemStack.EMPTY
              : slot == 0 ? held().copy() : fuel.getStackInSlot(slot - 1).copy();
        }

        public ItemStack insertItem(int slot, ItemStack stack, boolean sim) {
          Objects.checkIndex(slot, 3);
          return ready() && slot == 1 && burner() ? fuel.insertItem(0, stack, sim) : stack;
        }

        public ItemStack extractItem(int slot, int n, boolean sim) {
          Objects.checkIndex(slot, 3);
          return ready() && slot > 0 ? fuel.extractItem(slot - 1, n, sim) : ItemStack.EMPTY;
        }

        public int getSlotLimit(int slot) {
          Objects.checkIndex(slot, 3);
          return slot == 0 ? 16 : 64;
        }

        public boolean isItemValid(int slot, ItemStack stack) {
          return slot == 1 && burner() && fuelTime(stack) > 0;
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

  private void changed() {
    if (!isRemoved()) sync();
    else markPersistentState();
  }

  public FactoryKind kind() {
    return ((FactoryBlock) getBlockState().getBlock()).kind;
  }

  public boolean electric() {
    return kind().electricInserter();
  }

  public boolean burner() {
    return kind() == FactoryKind.BURNER_INSERTER;
  }

  public int reach() {
    return kind().armSpec().reach();
  }

  public int phase() {
    return cycle.remaining();
  }

  public int stage() {
    return stage;
  }

  public boolean enabled() {
    return enabled;
  }

  public boolean blacklist() {
    return filters.blacklist();
  }

  public ResourceLocation filter() {
    return ResourceLocation.tryParse(filters.ids().getFirst());
  }

  public ItemStack filterIcon() {
    return filterIcon(0);
  }

  public ItemStack filterIcon(int slot) {
    var id = ResourceLocation.tryParse(filters.ids().get(slot));
    return id == null
        ? ItemStack.EMPTY
        : BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
  }

  public List<String> filterIds() {
    return filters.ids();
  }

  public int manualLimit() {
    return manualLimit;
  }

  public long fuelEnergy() {
    return fuelEnergy;
  }

  public int status() {
    return status;
  }

  public String scope() {
    return researchScope;
  }

  public void bindResearch(String scope) {
    researchScope = scope;
    researchTick = Long.MIN_VALUE;
    setChanged();
  }

  private void research() {
    if (level == null || level.isClientSide) return;
    long now = level.getGameTime();
    if (researchTick != Long.MIN_VALUE && now >= researchTick && now - researchTick < 20) return;
    researchTick = now;
    capacityLevel = 0;
    beltLevel = 0;
    if (researchScope.isEmpty()
        || !factorycraft.research.ResearchTeams.exists(
            ((ServerLevel) level).getServer(), researchScope)) return;
    var team =
        factorycraft.research.ResearchData.get(((ServerLevel) level).getServer())
            .team(researchScope);
    for (int i = 1; i <= 7; i++)
      if (team.level("factorycraft:inserter_capacity_bonus_" + i) > 0) capacityLevel = i;
    for (int i = 1; i <= 2; i++)
      if (team.level("factorycraft:transport_belt_capacity_" + i) > 0) beltLevel = i;
  }

  public int researchedMaximum() {
    research();
    return InserterCapacity.maximum(kind().armSpec(), capacityLevel, beltLevel);
  }

  public int beltStackHeight() {
    research();
    return Math.clamp(2 + beltLevel, 2, 4);
  }

  private int limit(ItemStack sample, boolean beltTarget) {
    research();
    return InserterCapacity.handLimit(
        kind().armSpec(),
        capacityLevel,
        beltLevel,
        circuit(false) != null && circuit(false).settings().hand()
            ? circuit(false).handLimit
            : manualLimit,
        sample.getMaxStackSize(),
        beltTarget);
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
    return configure(
        enable,
        new ItemFilters(Arrays.asList(id == null ? "" : id.toString(), "", "", "", ""), exclude),
        manualLimit);
  }

  public boolean configure(boolean enable, ItemFilters list, int maximum) {
    if (!ready() || maximum < 0 || maximum > researchedMaximum()) return false;
    filters = list;
    enabled = enable;
    manualLimit = maximum;
    if (kind().armSpec().stack() && !held().isEmpty() && !matches(held())) forcedDrop = true;
    sync();
    return true;
  }

  public boolean setFilter(int slot, ResourceLocation id) {
    if (slot < 0 || slot >= 5) return false;
    var ids = new ArrayList<>(filters.ids());
    ids.set(slot, id == null ? "" : id.toString());
    return configure(enabled, new ItemFilters(ids, blacklist()), manualLimit);
  }

  public PowerBuffer gridBuffer() {
    return power;
  }

  public int receiveGridEnergy(int n, boolean sim) {
    return electric() && ready() ? power.receiveEnergy(n, sim) : 0;
  }

  public IItemHandler inventory() {
    return exposed;
  }

  public ItemStack held() {
    return hand.getStackInSlot(0);
  }

  public double visualPhase(float partial) {
    double n = cycle.remaining();
    if (working && (stage == 1 || stage == 3))
      n -= Math.clamp(level.getGameTime() + partial - phaseTick, 0, 4) * 3;
    return Math.clamp(n / kind().armSpec().swing(), 0, 1);
  }

  public double visualAngle(float partial) {
    return switch (stage) {
      case 1 -> visualPhase(partial) * Math.PI;
      case 2 -> 0;
      case 3 -> (1 - visualPhase(partial)) * Math.PI;
      default -> Math.PI;
    };
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

  public void circuitFiltersChanged() {
    if (kind().armSpec().stack() && !held().isEmpty() && !matches(held())) {
      forcedDrop = true;
      markPersistentState();
    }
  }

  private boolean matches(ItemStack stack) {
    var logic = circuit(false);
    if (logic != null && logic.settings().filters())
      return logic.inputReady
          && logic.filterIds.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    return filters.matches(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
  }

  public static int fuelTime(ItemStack stack) {
    return stack.isEmpty() || stack.getItem() instanceof BucketItem
        ? 0
        : Math.max(0, stack.getBurnTime(RecipeType.SMELTING));
  }

  private boolean ignite() {
    var stack = fuel.getStackInSlot(0);
    int ticks = fuelTime(stack);
    if (ticks == 0) return false;
    var remainder = stack.getCraftingRemainingItem();
    var out = fuel.getStackInSlot(1);
    if (!remainder.isEmpty()
        && (!out.isEmpty()
            && (!ItemStack.isSameItemSameComponents(out, remainder)
                || out.getCount() + remainder.getCount() > out.getMaxStackSize()))) return false;
    fuelCommit = true;
    try {
      fuel.setStackInSlot(0, stack.copyWithCount(stack.getCount() - 1));
      if (!remainder.isEmpty()) {
        if (out.isEmpty()) fuel.setStackInSlot(1, remainder);
        else out.grow(remainder.getCount());
      }
      fuelEnergy += ticks * 30L;
    } finally {
      fuelCommit = false;
    }
    sync();
    return true;
  }

  private boolean spend(int amount) {
    if (!burner()) {
      if (!power.spend(amount)) return false;
      factorycraft.diagnostics.FactoryTelemetry.armWorked(this, amount);
      return true;
    }
    if (fuelEnergy < amount && !ignite()) return false;
    if (fuelEnergy < amount) return false;
    fuelEnergy -= amount;
    markPersistentState();
    return true;
  }

  private void selfFuel(IItemHandler source) {
    if (!burner()
        || fuelEnergy
            >= (kind().armSpec().fe() + kind().armSpec().units() - 1) / kind().armSpec().units()
        || !fuel.getStackInSlot(0).isEmpty()
        || !fuel.getStackInSlot(1).isEmpty()
        || source == null) return;
    for (int i = 0; i < Math.min(source.getSlots(), 4096); i++) {
      var ownerBefore = level.getBlockEntity(sourcePos);
      var stateBefore = level.getBlockState(sourcePos);
      var sample = source.extractItem(i, 1, true);
      if (fuelTime(sample) == 0) continue;
      if (!ready()
          || level.getBlockEntity(sourcePos) != ownerBefore
          || level.getBlockState(sourcePos) != stateBefore) return;
      var fresh = level.getCapability(Capabilities.ItemHandler.BLOCK, sourcePos, cachedFacing);
      if (fresh == null || i >= fresh.getSlots()) return;
      var actual = fresh.extractItem(i, 1, false);
      if (actual.isEmpty()) return;
      fuel.setStackInSlot(fuelTime(actual) > 0 ? 0 : 1, actual);
      return;
    }
  }

  private boolean canPay() {
    if (burner()) return fuelEnergy > 0 || fuelTime(fuel.getStackInSlot(0)) > 0;
    return power.getEnergyStored() > 0;
  }

  private void moving(boolean value) {
    if (value != working) {
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
      if (pendingDrop) {
        pendingDrop = false;
        dropContents();
      }
    }
  }

  private void work() {
    status = 0;
    if (!ready() || !enabled || !factorycraft.circuits.CircuitBinding.allowed(this)) {
      moving(false);
      return;
    }
    positions();
    if (!endpointReady(sourcePos) || !endpointReady(targetPos)) {
      status = 7;
      moving(false);
      return;
    }
    boolean moved = false;
    int budget = 3;
    var facing = cachedFacing;
    for (int transitions = 0; transitions < 8; transitions++) {
      if (!ready()) break;
      if (stage == 0) {
        var target =
            level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, facing.getOpposite());
        var source = level.getCapability(Capabilities.ItemHandler.BLOCK, sourcePos, facing);
        selfFuel(source);
        if (!ready()) break;
        var targetBelt = factorycraft.simulation.BeltSimulation.at(level, targetPos);
        int goal = held().isEmpty() ? Integer.MAX_VALUE : limit(held(), targetBelt != null);
        if (!held().isEmpty()
            && (forcedDrop
                || held().getCount() >= goal
                || !kind().armSpec().bulk()
                || source == null && !kind().armSpec().stack())) {
          startOutbound();
          continue;
        }
        if (target == null || source == null) {
          status = 7;
          break;
        }
        if (budget == 0) break;
        boolean grabbed = collect(source, target, targetBelt);
        if (!ready()) break;
        if (grabbed) {
          budget--;
          moved = true;
          gatherUnits = 0;
        }
        if (held().isEmpty()) {
          status = canPay() ? 2 : 1;
          break;
        }
        goal = limit(held(), targetBelt != null);
        if (held().getCount() >= goal
            || forcedDrop
            || !kind().armSpec().bulk()
            || factorycraft.simulation.BeltSimulation.at(level, sourcePos) == null
                && !kind().armSpec().stack()) {
          startOutbound();
          continue;
        }
        status = 3;
        if (gatherUnits < 30) {
          gatherUnits = Math.min(30, gatherUnits + 3);
          markPersistentState();
        }
        if (!kind().armSpec().stack() && gatherUnits >= 30) {
          startOutbound();
          continue;
        }
        break;
      }
      if (stage == 1 || stage == 3) {
        if (cycle.remaining() == 0) {
          if (stage == 1) {
            stage = 2;
            dropCredit = true;
            sync();
            continue;
          }
          stage = 0;
          forcedDrop = false;
          gatherUnits = 0;
          sync();
          if (budget > 0) continue;
          break;
        }
        if (budget == 0) break;
        if (!cycle.advanceUnit(debit)) {
          status = 1;
          break;
        }
        budget--;
        moved = true;
        status = stage == 1 ? 4 : 6;
        markPersistentState();
        continue;
      }
      if (stage == 2) {
        var target =
            level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, facing.getOpposite());
        if (target == null) {
          status = 7;
          break;
        }
        var belt = factorycraft.simulation.BeltSimulation.at(level, targetPos);
        var ownerBefore = level.getBlockEntity(targetPos);
        var stateBefore = level.getBlockState(targetPos);
        var previous = held().copy();
        if (!previous.isEmpty()
            && deposit(target, belt, previous, true).getCount() >= previous.getCount()) {
          status = 5;
          break;
        }
        if (!ready()
            || !endpointReady(targetPos)
            || level.getBlockEntity(targetPos) != ownerBefore
            || level.getBlockState(targetPos) != stateBefore) break;
        target =
            level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, facing.getOpposite());
        if (target == null) break;
        if (!canPay() || !dropCredit && !cycle.payUnit(debit)) {
          status = 1;
          break;
        }
        var remainder = deposit(target, belt, previous, false);
        int delivered = Math.max(0, previous.getCount() - remainder.getCount());
        if (delivered > 0) {
          dropCredit = false;
          factorycraft.diagnostics.FactoryTelemetry.transferred(this, delivered);
        }
        if (!ItemStack.matches(previous, remainder)) hand.setStackInSlot(0, remainder);
        if (!ready()) break;
        if (!held().isEmpty()) {
          status = 5;
          break;
        }
        stage = 3;
        cycle.begin();
        sync();
        continue;
      }
    }
    if (moved) {
      phaseTick = level.getGameTime();
      if ((phaseTick & 3) == 0) sendUpdate();
    }
    moving(moved && (stage == 1 || stage == 3));
  }

  private void startOutbound() {
    stage = 1;
    if (cycle.remaining() == 0) cycle.begin();
    sync();
  }

  /**
   * Candidate simulation is advisory. Exact actual stacks/counts become owned before another call.
   */
  private boolean collect(IItemHandler source, IItemHandler target, BeltBlockEntity targetBelt) {
    if (!fuel.getStackInSlot(1).isEmpty()) return false;
    var sourceBelt = factorycraft.simulation.BeltSimulation.at(level, sourcePos);
    boolean paid = false, changed = false;
    for (int slot = 0; slot < Math.min(4096, source.getSlots()); slot++) {
      if (sourceBelt != null && !sourceBelt.canSample(sourcePos, slot)) continue;
      var ownerBefore = level.getBlockEntity(sourcePos);
      var stateBefore = level.getBlockState(sourcePos);
      var candidate = source.extractItem(slot, 16, true);
      if (candidate.isEmpty()
          || !matches(candidate)
          || !held().isEmpty() && !ItemStack.isSameItemSameComponents(candidate, held())) continue;
      int goal = limit(candidate, targetBelt != null), room = goal - held().getCount();
      if (room <= 0) break;
      candidate = candidate.copyWithCount(Math.min(room, candidate.getCount()));
      if (held().isEmpty()
          && deposit(target, targetBelt, candidate, true).getCount() >= candidate.getCount())
        continue;
      if (!ready()
          || !endpointReady(sourcePos)
          || level.getBlockEntity(sourcePos) != ownerBefore
          || level.getBlockState(sourcePos) != stateBefore) return changed;
      var fresh = level.getCapability(Capabilities.ItemHandler.BLOCK, sourcePos, cachedFacing);
      if (fresh == null || slot >= fresh.getSlots()) return changed;
      if (!paid) {
        if (!cycle.payUnit(debit)) return false;
        paid = true;
      }
      var actual = fresh.extractItem(slot, candidate.getCount(), false);
      if (actual.isEmpty()) continue;
      if (held().isEmpty()) {
        hand.setStackInSlot(0, actual);
        cycle.begin();
        cycle.restore(cycle.remaining() - 1, cycle.energyRemainder());
      } else if (ItemStack.isSameItemSameComponents(held(), actual)) {
        held().grow(actual.getCount());
        sync();
      } else {
        fuel.setStackInSlot(1, actual);
        forcedDrop = true;
        break;
      }
      changed = true;
      if (kind().armSpec().stack() && !matches(held())) forcedDrop = true;
      if (held().getCount() >= goal || forcedDrop || sourceBelt != null) break;
    }
    return changed;
  }

  private ItemStack deposit(
      IItemHandler target, BeltBlockEntity belt, ItemStack stack, boolean simulate) {
    if (stack.isEmpty()) return stack;
    if (belt == null) return ItemHandlerHelper.insertItem(target, stack.copy(), simulate);
    for (int slot = 0; slot < Math.min(8, target.getSlots()); slot++) {
      var remainder =
          kind().armSpec().stack()
              ? belt.insertStack(targetPos, slot, stack, beltStackHeight(), simulate)
              : target.insertItem(slot, stack.copyWithCount(1), simulate);
      int sent =
          kind().armSpec().stack()
              ? stack.getCount() - remainder.getCount()
              : 1 - remainder.getCount();
      if (sent > 0)
        return stack.getCount() == sent
            ? ItemStack.EMPTY
            : stack.copyWithCount(stack.getCount() - sent);
    }
    return stack;
  }

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

  public void onChunkUnloaded() {
    detach();
    super.onChunkUnloaded();
  }

  public void setRemoved() {
    detach();
    super.setRemoved();
  }

  public void dropContents() {
    if (level == null || level.isClientSide || packed) return;
    if (transferring) {
      pendingDrop = true;
      return;
    }
    var stack = new ItemStack(getBlockState().getBlock());
    saveToItem(stack, level.registryAccess());
    var saved = stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
    if (saved != null) {
      var data = saved.copyTag();
      data.remove("CircuitControl");
      stack.set(
          net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,
          net.minecraft.world.item.component.CustomData.of(data));
    }
    packed = true;
    hand.setStackInSlot(0, ItemStack.EMPTY);
    fuelCommit = true;
    fuel.setStackInSlot(0, ItemStack.EMPTY);
    fuel.setStackInSlot(1, ItemStack.EMPTY);
    fuelCommit = false;
    power.restore(0);
    fuelEnergy = 0;
    net.minecraft.world.Containers.dropItemStack(
        level,
        worldPosition.getX() + .5,
        worldPosition.getY() + .5,
        worldPosition.getZ() + .5,
        stack);
  }

  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registry) {
    super.saveAdditional(tag, registry);
    tag.putInt("Schema", 3);
    tag.put("Hand", hand.serializeNBT(registry));
    tag.put("Fuel", fuel.serializeNBT(registry));
    tag.putInt("Phase", cycle.remaining());
    tag.putInt("EnergyRemainder", cycle.energyRemainder());
    tag.putInt("Stage", stage);
    tag.putLong("PhaseTick", level == null ? 0 : level.getGameTime());
    tag.putLong("FuelEnergy", fuelEnergy);
    tag.putInt("Energy", power.getEnergyStored());
    tag.putBoolean("Enabled", enabled);
    tag.putBoolean("Blacklist", blacklist());
    tag.putInt("HandLimit", manualLimit);
    tag.putInt("GatherUnits", gatherUnits);
    tag.putBoolean("ForcedDrop", forcedDrop);
    tag.putBoolean("DropCredit", dropCredit);
    var ids = new ListTag();
    for (var id : filters.ids()) ids.add(StringTag.valueOf(id));
    tag.put("Filters", ids);
    if (!researchScope.isEmpty()) tag.putString("ResearchScope", researchScope);
    if (circuitBinding != null) tag.put("CircuitControl", circuitBinding.save());
  }

  public CompoundTag getUpdateTag(HolderLookup.Provider registry) {
    var tag = super.getUpdateTag(registry);
    tag.putBoolean("ArmMoving", working);
    return tag;
  }

  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registry) {
    super.loadAdditional(tag, registry);
    hand.deserializeNBT(registry, tag.getCompound("Hand"));
    fuel.deserializeNBT(registry, tag.getCompound("Fuel"));
    stage = Math.clamp(tag.getInt("Stage"), 0, 3);
    cycle.restore(tag.getInt("Phase"), tag.getInt("EnergyRemainder"));
    phaseTick = tag.getLong("PhaseTick");
    fuelEnergy = Math.max(0, tag.getLong("FuelEnergy"));
    power.restore(tag.getInt("Energy"));
    enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
    manualLimit = Math.clamp(tag.getInt("HandLimit"), 0, 16);
    gatherUnits = Math.clamp(tag.getInt("GatherUnits"), 0, 30);
    forcedDrop = tag.getBoolean("ForcedDrop");
    dropCredit = tag.getBoolean("DropCredit");
    var list = tag.getList("Filters", Tag.TAG_STRING);
    var ids = new ArrayList<String>();
    for (int i = 0; i < 5; i++) ids.add(i < list.size() ? list.getString(i) : "");
    filters = new ItemFilters(ids, tag.getBoolean("Blacklist"));
    researchScope = tag.getString("ResearchScope");
    researchTick = Long.MIN_VALUE;
    working = tag.getBoolean("ArmMoving");
    circuitBinding = factorycraft.circuits.CircuitBinding.load(tag.getCompound("CircuitControl"));
  }
}
