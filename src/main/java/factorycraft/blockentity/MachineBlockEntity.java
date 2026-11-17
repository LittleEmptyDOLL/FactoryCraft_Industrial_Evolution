package factorycraft.blockentity;

import factorycraft.blocks.*;
import factorycraft.energy.PowerGrid;
import factorycraft.multiblock.IndustrialAssembly;
import factorycraft.production.*;
import factorycraft.recipes.*;
import factorycraft.registry.Content;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.*;

public final class MachineBlockEntity extends FactoryBlockEntity
    implements factorycraft.multiblock.AssemblyOwner,
        MenuProvider,
        factorycraft.energy.GridConsumer {
  @Override
  public int receiveGridEnergy(int n, boolean simulate) {
    return !kind().burner()
            && loaded()
            && factorycraft.multiblock.AssemblyAccess.ready(this)
            && factorycraft.fluids.FluidConnections.canonical(this)
        ? power.receiveEnergy(n, simulate)
        : 0;
  }

  @Override
  public PowerBuffer gridBuffer() {
    return power;
  }

  public final factorycraft.modules.MachineModules modules =
      new factorycraft.modules.MachineModules(this);
  public final factorycraft.simulation.fluids.FluidCell<factorycraft.fluids.FluidKey> miningAcid =
      kind() == FactoryKind.DRILL
          ? new factorycraft.simulation.fluids.FluidCell<>(2000, this::markPetroleumState)
          : null;
  public final factorycraft.nuclear.ConstructionWork construction =
      new factorycraft.nuclear.ConstructionWork(this);

  public ItemStack nuclearOutput(ItemStack stack, boolean simulate) {
    return output(stack, simulate);
  }

  private boolean loaded;
  public final factorycraft.nuclear.UraniumWork uraniumWork =
      kind() == FactoryKind.CENTRIFUGE ? new factorycraft.nuclear.UraniumWork(this) : null;

  public void nuclearProgress(int value, int total) {
    progress = value;
    duration = total;
  }

  private String researchScope = "";

  public String researchScope() {
    return researchScope;
  }

  public void bindResearch(String scope) {
    researchScope = scope;
    setChanged();
  }

  public boolean recipeAllowed(net.minecraft.resources.ResourceLocation id) {
    return researchScope.isEmpty()
        || factorycraft.research.ResearchCatalog.get(level).allowed(researchScope, id, level);
  }

  private boolean petroleumTransaction, petroleumInventoryDirty;
  private net.minecraft.world.level.chunk.LevelChunk petroleumChunk;
  private final factorycraft.petroleum.PetroleumProduction petroleum;

  public factorycraft.petroleum.PetroleumProduction petroleum() {
    return petroleum;
  }

  public boolean petroleumAvailable() {
    if (!loaded() || !factorycraft.fluids.FluidConnections.canonical(this)) return false;
    if (petroleumChunk == null)
      petroleumChunk =
          ((net.minecraft.server.level.ServerLevel) level)
              .getChunkSource()
              .getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
    return petroleumChunk != null;
  }

  public void markPetroleumState() {
    if (level == null || level.isClientSide || !loaded()) return;
    if (petroleumChunk != null) petroleumChunk.setUnsaved(true);
    markPersistentState();
  }

  public void syncPetroleum() {
    sendUpdate();
  }

  public void beginPetroleumInventoryTransaction() {
    petroleumTransaction = true;
  }

  public void endPetroleumInventoryTransaction() {
    petroleumTransaction = false;
    if (petroleumInventoryDirty) {
      petroleumInventoryDirty = false;
      setChanged();
    }
  }

  public boolean loaded() {
    return loaded && !isRemoved();
  }

  private boolean automationReady() {
    return loaded()
        && level != null
        && !level.isClientSide
        && factorycraft.multiblock.AssemblyAccess.ready(this)
        && (petroleum == null || petroleum.ready());
  }

  public final ItemStackHandler items =
      new ItemStackHandler(kind().assembler() ? 21 : 9) {
        @Override
        protected void onContentsChanged(int s) {
          if (petroleumTransaction) petroleumInventoryDirty = true;
          else setChanged();
          if (petroleum != null) petroleum.changed();
        }

        @Override
        public boolean isItemValid(int s, ItemStack stack) {
          if (s >= 4 && s < 8) return false;
          if (kind() == FactoryKind.CENTRIFUGE)
            return s < 4 && factorycraft.nuclear.NuclearItems.is(stack, "uranium_ore");
          if (s == 8) return kind().burner() && burnTime(stack) > 0;
          return kind() == FactoryKind.CENTRIFUGE
              ? factorycraft.nuclear.NuclearItems.is(stack, "uranium_ore")
              : kind().processing();
        }
      };
  public final PowerBuffer power = new PowerBuffer(16000, this::markPersistentState);
  private final IItemHandler automation =
      new IItemHandler() {
        public int getSlots() {
          return items.getSlots();
        }

        public ItemStack getStackInSlot(int s) {
          return automationReady() ? items.getStackInSlot(s).copy() : ItemStack.EMPTY;
        }

        public ItemStack insertItem(int s, ItemStack stack, boolean sim) {
          return automationReady() ? items.insertItem(s, stack, sim) : stack;
        }

        public ItemStack extractItem(int s, int n, boolean sim) {
          return automationReady()
                  && (s >= 4 && s < 8
                      || s == 8 && kind().burner() && burnTime(items.getStackInSlot(8)) == 0)
              ? items.extractItem(s, n, sim)
              : ItemStack.EMPTY;
        }

        public int getSlotLimit(int s) {
          return items.getSlotLimit(s);
        }

        public boolean isItemValid(int s, ItemStack stack) {
          return automationReady() && items.isItemValid(s, stack);
        }
      };
  public final net.neoforged.neoforge.energy.IEnergyStorage energyPort =
      new net.neoforged.neoforge.energy.IEnergyStorage() {
        public int receiveEnergy(int n, boolean sim) {
          return !kind().burner() && automationReady() ? power.receiveEnergy(n, sim) : 0;
        }

        public int extractEnergy(int n, boolean sim) {
          return 0;
        }

        public int getEnergyStored() {
          return !kind().burner() && automationReady() ? power.getEnergyStored() : 0;
        }

        public int getMaxEnergyStored() {
          return kind().burnerFurnace() ? 0 : power.getMaxEnergyStored();
        }

        public boolean canExtract() {
          return false;
        }

        public boolean canReceive() {
          return !kind().burner() && automationReady();
        }
      };
  private final IndustrialAssembly assembly;

  public IndustrialAssembly assembly() {
    return assembly;
  }

  private int progress, duration = 1, fuelTicks;
  private int furnaceEnergyRemainder, furnaceBudget;
  private java.util.UUID oreIdentity;
  private long miningFuel;
  private int miningPhase, miningCredit, miningProductivityRemainder;
  private boolean furnacePaid;
  private String selected = "", activeRecipe = "";
  private List<RecipeHolder<FactoryRecipe>> recipeCache = List.of();
  private long recipeCacheTick = Long.MIN_VALUE;
  private BlockPos oreTarget;
  private long nextOreSearchTick = Long.MIN_VALUE;
  public final ContainerData data =
      new ContainerData() {
        public int get(int i) {
          return switch (i) {
            case 0 -> progress;
            case 1 -> duration;
            case 2 -> power.getEnergyStored();
            case 3 -> fuelTicks();
            case 4 -> selectedIndex();
            case 5 -> previewIndex();
            case 6 -> assembly == null ? IndustrialAssembly.READY : assembly.status();
            case 7 -> lockedPreview() ? 1 : 0;
            default -> 0;
          };
        }

        public void set(int i, int v) {
          switch (i) {
            case 0 -> progress = v;
            case 1 -> duration = v;
            case 2 -> power.restore(v);
            case 3 -> fuelTicks = v;
            default -> {}
          }
        }

        public int getCount() {
          return 8;
        }
      };

  public MachineBlockEntity(BlockPos p, BlockState s) {
    super(Content.MACHINE.get(), p, s);
    assembly = ((FactoryBlock) s.getBlock()).kind.large() ? new IndustrialAssembly(this) : null;
    petroleum = kind().petroleum() ? new factorycraft.petroleum.PetroleumProduction(this) : null;
  }

  public FactoryKind kind() {
    return ((FactoryBlock) getBlockState().getBlock()).kind;
  }

  @Override
  public IItemHandler inventory() {
    return items;
  }

  public IItemHandler automation(Direction side) {
    return automation;
  }

  public int fuelTicks() {
    return kind() == FactoryKind.BURNER_DRILL
        ? (int) Math.min(Integer.MAX_VALUE, miningFuel / 3)
        : fuelTicks;
  }

  private final Map<ResourceLocation, RecipeHolder<SmeltingRecipe>> smelting = new HashMap<>();
  private final Map<ResourceLocation, String> definitions = new HashMap<>();
  private long recipeEpoch = -1;
  private List<RecipeHolder<FactoryRecipe>> sourceProcessing = List.of();
  private List<RecipeHolder<SmeltingRecipe>> sourceSmelting = List.of();
  private String activeDefinition = "", resolvedBase = "", resolvedDefinition = "";
  private ItemStack resolvedResult = ItemStack.EMPTY;

  public String selectedRecipeId() {
    return petroleum == null ? selected : petroleum.selectedRecipeId();
  }

  public boolean selectRecipe(ResourceLocation id) {
    if (level == null
        || level.isClientSide
        || !loaded()
        || !factorycraft.multiblock.AssemblyAccess.ready(this)
        || !kind().processing()) return false;
    if (construction.ownsMaterials() && (id == null || !id.toString().equals(selectedRecipeId())))
      return false;
    if (petroleum != null) return petroleum.selectRecipe(id);
    if (id != null && (!recipeAllowed(id) || recipes().stream().noneMatch(r -> r.id().equals(id))))
      return false;
    String next = id == null ? "" : id.toString();
    if (next.equals(selected)) return true;
    selected = next;
    progress = 0;
    activeRecipe = "";
    activeDefinition = "";
    sync();
    return true;
  }

  public List<RecipeHolder<FactoryRecipe>> recipes() {
    if (level == null) return List.of();
    long now = level.getGameTime();
    if (recipeCacheTick == Long.MIN_VALUE
        || now - recipeCacheTick >= 20
        || recipeEpoch != RecipeSelectionNetwork.generation) {
      var processingSource = level.getRecipeManager().getAllRecipesFor(Content.PROCESSING.get());
      var smeltingSource =
          SmeltingInterop.furnace(kind())
              ? level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING)
              : List.<RecipeHolder<SmeltingRecipe>>of();
      if (recipeCacheTick != Long.MIN_VALUE
          && recipeEpoch == RecipeSelectionNetwork.generation
          && sourceProcessing.equals(processingSource)
          && sourceSmelting.equals(smeltingSource)) {
        recipeCacheTick = now;
        return recipeCache;
      }
      sourceProcessing = processingSource;
      sourceSmelting = smeltingSource;
      var result = new ArrayList<RecipeHolder<FactoryRecipe>>();
      smelting.clear();
      definitions.clear();
      processingSource.stream()
          .filter(
              h ->
                  h.value().machine().equals(kind().recipeGroup())
                      || kind().assembler() && h.value().machine().equals(FactoryKind.ASSEMBLER.id))
          .sorted(Comparator.comparing(h -> h.id().toString()))
          .forEach(result::add);
      if (SmeltingInterop.furnace(kind()))
        for (var h :
            smeltingSource.stream().sorted(Comparator.comparing(h -> h.id().toString())).toList()) {
          if (result.stream().anyMatch(r -> r.id().equals(h.id()))) continue;
          var projected = SmeltingInterop.project(h, level);
          if (projected.result().isEmpty()) continue;
          smelting.put(h.id(), h);
          result.add(new RecipeHolder<>(h.id(), projected));
        }
      var ops =
          net.minecraft.resources.RegistryOps.create(
              com.mojang.serialization.JsonOps.INSTANCE, level.registryAccess());
      for (var r : result)
        definitions.put(
            r.id(),
            FactoryRecipe.CODEC.codec().encodeStart(ops, r.value()).getOrThrow().toString());
      recipeCache = List.copyOf(result);
      recipeCacheTick = now;
      recipeEpoch = RecipeSelectionNetwork.generation;
    }
    return recipeCache;
  }

  private int[] consumption(RecipeHolder<FactoryRecipe> r, FactoryInput input) {
    var nativeRecipe = smelting.get(r.id());
    if (nativeRecipe == null) return r.value().consumptionPlan(input);
    int slot = SmeltingInterop.slot(nativeRecipe, input, level);
    if (slot < 0) return null;
    int[] plan = new int[input.size()];
    plan[slot] = 1;
    return plan;
  }

  private RecipeHolder<FactoryRecipe> resolve(RecipeHolder<FactoryRecipe> r, FactoryInput input) {
    var n = smelting.get(r.id());
    if (n == null) return r;
    int slot = SmeltingInterop.slot(n, input, level);
    if (slot < 0) return null;
    var v = r.value();
    var out = SmeltingInterop.result(n, input.getItem(slot), level);
    return out.isEmpty()
        ? null
        : new RecipeHolder<>(
            r.id(), new FactoryRecipe(v.machine(), v.inputs(), out, v.ticks(), v.energy()));
  }

  public boolean nativeSmelting(ResourceLocation id) {
    recipes();
    return smelting.containsKey(id);
  }

  public int selectedIndex() {
    var rs = recipes();
    for (int i = 0; i < rs.size(); i++)
      if (rs.get(i).id().toString().equals(selected)) return i + 1;
    return 0;
  }

  public int previewIndex() {
    var rs = recipes();
    for (int i = 0; i < rs.size(); i++)
      if ((!activeRecipe.isEmpty() && activeRecipe.equals(rs.get(i).id().toString()))
          || (activeRecipe.isEmpty() && rs.get(i).value().matches(new FactoryInput(items), level)))
        return i + 1;
    return 0;
  }

  private boolean lockedPreview() {
    int i = selectedIndex();
    if (i == 0) i = previewIndex();
    return i > 0 && !recipeAllowed(recipes().get(i - 1).id());
  }

  public void cycleRecipe() {
    var rs = recipes();
    int next = (selectedIndex() + 1) % (rs.size() + 1);
    selected = next == 0 ? "" : rs.get(next - 1).id().toString();
    progress = 0;
    activeRecipe = "";
    sync();
  }

  private static int burnTime(ItemStack stack) {
    return stack.getBurnTime(RecipeType.SMELTING);
  }

  public RecipeHolder<FactoryRecipe> circuitRecipe() {
    var rs = recipes();
    for (var r : rs)
      if (r.id().toString().equals(selected)
          || progress > 0 && r.id().toString().equals(activeRecipe)) return r;
    int index = previewIndex();
    return index > 0 ? rs.get(index - 1) : null;
  }

  public boolean circuitSelectRecipe(ResourceLocation id) {
    if (!loaded() || !recipeAllowed(id)) return false;
    if (petroleum != null) return petroleum.circuitSelectRecipe(id);
    if (!kind().assembler()) return false;
    for (var r : recipes())
      if (r.id().equals(id)) {
        if (progress > 0 && !activeRecipe.equals(id.toString())) return false;
        if (!selected.equals(id.toString())) {
          selected = id.toString();
          sync();
        }
        return true;
      }
    return false;
  }

  public void tick() {
    if (!loaded()) return;
    if (construction.handles()) {
      boolean worked =
          factorycraft.multiblock.AssemblyAccess.ready(this)
              && factorycraft.fluids.FluidConnections.canonical(this)
              && factorycraft.circuits.CircuitBinding.allowed(this)
              && construction.tick();
      setActive(worked);
      return;
    }
    if (petroleum != null) {
      boolean worked = petroleum.tick();
      if (petroleumAvailable()) setActive(worked);
      petroleum.tickSync();
      return;
    }
    if (assembly != null && !assembly.ready()) {
      setActive(false);
      return;
    }
    if (!factorycraft.circuits.CircuitBinding.allowed(this)) {
      setActive(false);
      return;
    }
    if (kind().drill()) pushDrillOutput();
    if (miningAcid != null)
      for (var port : assembly.ports()) {
        var from =
            factorycraft.fluids.FluidConnections.output(
                level, port.adjacent(), port.side().getOpposite());
        if (from != null
            && from.amount() > 0
            && factorycraft.multiblock.MachineFluidAccess.accepts(this, 0, from.key()))
          from.transferTo(miningAcid, 6000, false);
      }
    boolean worked;
    if (kind().furnace() || kind().assembler()) {
      furnaceBudget =
          factorycraft.simulation.production.PaidWork.budget(
              kind().workQuarters(), modules.effects().speed());
      furnacePaid = false;
      worked = false;
      // At most ten one-tick recipes fit the maximum speed; bound even malformed callbacks.
      for (int attempts = 0; attempts < 16 && furnaceBudget > 0; attempts++) {
        if (!produce()) break;
        worked = true;
      }
    } else worked = uraniumWork != null ? uraniumWork.tick() : kind().drill() ? mine() : produce();
    if (worked && level.getGameTime() % 20 == 0) sync();
    setActive(worked);
  }

  private void setActive(boolean active) {
    if (getBlockState().getValue(FactoryBlock.ACTIVE) != active)
      level.setBlock(worldPosition, getBlockState().setValue(FactoryBlock.ACTIVE, active), 3);
  }

  private boolean produce() {
    var input = new FactoryInput(items);
    RecipeHolder<FactoryRecipe> chosen = null;
    for (var r : recipes()) {
      if (selected.isEmpty()
          && (r.value().categories().contains("military")
              || r.value().categories().contains("nuclear")
              || r.value().categories().contains("nuclear_construction"))) continue;
      if (!selected.isEmpty() && !selected.equals(r.id().toString())) continue;
      if (!activeRecipe.isEmpty() && progress > 0 && !activeRecipe.equals(r.id().toString()))
        continue;
      if (recipeAllowed(r.id()) && consumption(r, input) != null) {
        chosen = resolve(r, input);
        if (chosen != null) break;
      }
    }
    if (chosen == null) return false;
    String definition = definitions.get(chosen.id());
    if (smelting.containsKey(chosen.id())) {
      var result = chosen.value().result();
      if (!definition.equals(resolvedBase)
          || result.getCount() != resolvedResult.getCount()
          || !ItemStack.isSameItemSameComponents(result, resolvedResult)) {
        resolvedBase = definition;
        resolvedResult = result.copy();
        resolvedDefinition = definition + "|" + result.save(level.registryAccess());
      }
      definition = resolvedDefinition;
    }
    if (!activeDefinition.equals(definition)) {
      progress = 0;
      activeRecipe = "";
      activeDefinition = definition;
    }
    if (kind().furnace() || kind().assembler()) return produceFurnace(chosen, input);
    if (modules.enabled()) return produceWithModules(chosen, input);
    var recipe = chosen.value();
    if (!output(recipe.result(), true).isEmpty()) return false;
    int step = 1;
    int advancing = Math.min(step, recipe.ticks() - Math.min(progress, recipe.ticks() - 1));
    if (!power.spend(recipe.energy() * advancing)) return false;
    activeRecipe = chosen.id().toString();
    duration = recipe.ticks();
    progress += advancing;
    markPersistentState();
    factorycraft.diagnostics.FactoryTelemetry.worked(this, recipe.energy() * advancing);
    if (progress >= duration) {
      int[] plan = consumption(chosen, input);
      // Single server-thread commit, after every output slot and input count has been checked.
      if (plan != null && output(recipe.result(), true).isEmpty()) {
        beginPetroleumInventoryTransaction();
        try {
          for (int s = 0; s < plan.length; s++)
            if (plan[s] > 0) items.extractItem(FactoryInput.realSlot(s), plan[s], false);
          output(recipe.result(), false);
          progress = 0;
          activeRecipe = "";
        } finally {
          endPetroleumInventoryTransaction();
        }
        factorycraft.diagnostics.FactoryTelemetry.produced(this);
        sync();
      }
    }
    return true;
  }

  private boolean produceFurnace(RecipeHolder<FactoryRecipe> chosen, FactoryInput input) {
    var recipe = chosen.value();
    var key = modules.identity(chosen);
    var effects = modules.effects();
    int phase = modules.identityChanged(key) ? 0 : modules.fraction();
    int current = modules.identityChanged(key) ? 0 : progress;
    int rate =
        kind().supportsModules() && modules.productive(recipe.result())
            ? effects.productivity()
            : 0;
    int batches = modules.batches(key, rate, recipe.ticks() - current, recipe.ticks());
    var result = recipe.result().copyWithCount(recipe.result().getCount() * batches);
    var plan = consumption(chosen, input);
    if (plan == null || !output(result, true).isEmpty()) return false;
    beginPetroleumInventoryTransaction();
    try {
      if (!furnacePaid) {
        if (kind().burnerFurnace()) {
          if (fuelTicks <= 0) {
            var fuel = items.getStackInSlot(8);
            int burn = burnTime(fuel);
            if (burn <= 0) return false;
            var remainder = fuel.getCraftingRemainingItem();
            if (!remainder.isEmpty() && fuel.getCount() != 1) return false;
            items.extractItem(8, 1, false);
            if (!remainder.isEmpty()) items.setStackInSlot(8, remainder);
            fuelTicks = burn;
          }
          fuelTicks--;
          factorycraft.diagnostics.FactoryTelemetry.worked(this, 0);
        } else {
          var charge =
              factorycraft.simulation.production.PaidWork.charge(
                  kind().activeFE(), effects.energy(), furnaceEnergyRemainder);
          if (!power.spend(charge.energy())) return false;
          furnaceEnergyRemainder = charge.remainder();
          factorycraft.diagnostics.FactoryTelemetry.worked(this, charge.energy());
        }
        furnacePaid = true;
      }
      int used = Math.min(furnaceBudget, (recipe.ticks() - current) * 40000 - phase);
      furnaceBudget -= used;
      int total = phase + used, work = total / 40000;
      progress = current + work;
      duration = recipe.ticks();
      activeRecipe = chosen.id().toString();
      modules.advance(key, total % 40000, work, duration, rate);
      markPersistentState();
      if (progress >= duration) {
        for (int slot = 0; slot < plan.length; slot++)
          if (plan[slot] > 0) items.extractItem(FactoryInput.realSlot(slot), plan[slot], false);
        output(result, false);
        modules.finish();
        progress = 0;
        activeRecipe = "";
        factorycraft.diagnostics.FactoryTelemetry.produced(this);
        sync();
      }
      return true;
    } finally {
      endPetroleumInventoryTransaction();
    }
  }

  private boolean produceWithModules(RecipeHolder<FactoryRecipe> chosen, FactoryInput input) {
    var recipe = chosen.value();
    String key = modules.identity(chosen);
    var effects = modules.effects();
    int rate = modules.productive(recipe.result()) ? effects.productivity() : 0;
    int batches =
        modules.batches(
            key,
            rate,
            recipe.ticks()
                - (modules.identityChanged(key) ? 0 : Math.min(progress, recipe.ticks() - 1)),
            recipe.ticks());
    var result = recipe.result().copyWithCount(recipe.result().getCount() * batches);
    if (!output(result, true).isEmpty()) return false;
    int current = modules.identityChanged(key) ? 0 : progress;
    var step =
        effects.plan(
            recipe.energy(),
            1,
            recipe.ticks() - Math.min(current, recipe.ticks() - 1),
            modules.identityChanged(key) ? 0 : modules.fraction());
    if (!power.spend(step.energy())) return false;
    progress = current + step.work();
    duration = recipe.ticks();
    activeRecipe = chosen.id().toString();
    modules.advance(key, step.remainder(), step.work(), duration, rate);
    if (progress >= duration) {
      var plan = consumption(chosen, input);
      if (plan == null) throw new IllegalStateException("Reserved module ingredients changed");
      beginPetroleumInventoryTransaction();
      try {
        for (int s = 0; s < plan.length; s++)
          if (plan[s] > 0) items.extractItem(FactoryInput.realSlot(s), plan[s], false);
        output(result, false);
        modules.finish();
        progress = 0;
        activeRecipe = "";
      } finally {
        endPetroleumInventoryTransaction();
      }
      sync();
    }
    return true;
  }

  public ItemStack output(ItemStack stack, boolean simulate) {
    ItemStack remainder = stack.copy();
    for (int s = 4; s < 8 && !remainder.isEmpty(); s++) {
      var existing = items.getStackInSlot(s);
      if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, remainder)) continue;
      int max = Math.min(items.getSlotLimit(s), remainder.getMaxStackSize());
      int insert = Math.min(remainder.getCount(), max - existing.getCount());
      if (insert <= 0) continue;
      if (!simulate) {
        ItemStack merged = remainder.copyWithCount(insert + existing.getCount());
        items.setStackInSlot(s, merged);
      }
      remainder.shrink(insert);
    }
    if (!simulate && level instanceof net.minecraft.server.level.ServerLevel server)
      factorycraft.research.ResearchTriggers.crafted(
          server, researchScope, stack, stack.getCount() - remainder.getCount());
    return remainder;
  }

  /** One physical front chute. Even 2-wide drill uses local x=1 (front-right). */
  public BlockPos drillOutputPosition() {
    var profile = assembly.profile();
    var facing = profile.facing(getBlockState());
    return profile.offset(worldPosition, facing, profile.layout.width() / 2, 0, 0).relative(facing);
  }

  private boolean pushingDrill;

  private void pushDrillOutput() {
    if (pushingDrill || !automationReady() || !factorycraft.fluids.FluidConnections.canonical(this))
      return;
    boolean pending = false;
    for (int slot = 4; slot < 8; slot++)
      if (!items.getStackInSlot(slot).isEmpty()) {
        pending = true;
        break;
      }
    if (!pending) return;
    var targetPos = drillOutputPosition();
    var server = (net.minecraft.server.level.ServerLevel) level;
    var targetChunk =
        server.getChunkSource().getChunkNow(targetPos.getX() >> 4, targetPos.getZ() >> 4);
    if (targetChunk == null
        || !server.shouldTickBlocksAt(new net.minecraft.world.level.ChunkPos(targetPos).toLong()))
      return;
    var facing = getBlockState().getValue(FactoryBlock.FACING);
    var target =
        level.getCapability(
            net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
            targetPos,
            facing.getOpposite());
    if (target == null) return; // Safe bounded fallback: owned slots 4–7, no loose item flood.
    var belt = factorycraft.simulation.BeltSimulation.at(level, targetPos);
    pushingDrill = true;
    beginPetroleumInventoryTransaction();
    try {
      for (int slot = 4; slot < 8; slot++) {
        var stack = items.getStackInSlot(slot);
        if (stack.isEmpty()) continue;
        // Remove before invoking external observers: a recursive callback cannot extract it twice.
        var offered = items.extractItem(slot, belt == null ? stack.getCount() : 1, false);
        ItemStack remainder;
        if (belt == null) remainder = ItemHandlerHelper.insertItem(target, offered.copy(), false);
        else {
          int lane =
              factorycraft.simulation.production.DrillChute.beltLane(
                  facing.getStepX(),
                  facing.getStepZ(),
                  belt.facing().getStepX(),
                  belt.facing().getStepZ());
          remainder = target.insertItem(lane * 4, offered.copy(), false);
        }
        if (!remainder.isEmpty()) {
          if (!isRemoved()) {
            var left = output(remainder, false);
            if (!left.isEmpty())
              net.minecraft.world.Containers.dropItemStack(
                  level,
                  worldPosition.getX() + .5,
                  worldPosition.getY() + .5,
                  worldPosition.getZ() + .5,
                  left);
          } else
            net.minecraft.world.Containers.dropItemStack(
                level,
                worldPosition.getX() + .5,
                worldPosition.getY() + .5,
                worldPosition.getZ() + .5,
                remainder);
        }
        break; // One pending stack / one ordinary physical belt carrier per server tick.
      }
    } finally {
      endPetroleumInventoryTransaction();
      pushingDrill = false;
    }
  }

  private ItemStack oreResult(BlockPos pos) {
    var be = factorycraft.fluids.FluidConnections.at(level, pos);
    return be instanceof ResourceDepositBlockEntity deposit
            && deposit.available()
            && deposit.remaining() > 0
            && (kind() != FactoryKind.BURNER_DRILL
                || deposit.resourceBlock().kind != factorycraft.resources.ResourceKind.URANIUM)
        ? deposit.resourceBlock().kind.result()
        : ItemStack.EMPTY;
  }

  private boolean mine() {
    duration =
        20; // Basic resources have mining time 1 s; speed is prototype work, not a rounded cycle.
    if (oreTarget != null) {
      if (!level.hasChunkAt(oreTarget)) return false;
      var tile = factorycraft.fluids.FluidConnections.at(level, oreTarget);
      if (tile instanceof ResourceDepositBlockEntity d && !d.available()) return false;
      if (!(tile instanceof ResourceDepositBlockEntity d) || !d.identity().equals(oreIdentity))
        oreTarget = null;
    }
    if (oreTarget == null && level.getGameTime() < nextOreSearchTick) return false;
    if (oreTarget == null || oreResult(oreTarget).isEmpty()) {
      oreTarget = null;
      progress = 0;
      miningPhase = 0;
      var profile = assembly.profile();
      var center = profile.center(worldPosition, profile.facing(getBlockState()));
      search:
      for (int y = 1; y <= 8; y++)
        for (int x = kind() == FactoryKind.BURNER_DRILL ? -1 : -2;
            x <= (kind() == FactoryKind.BURNER_DRILL ? 0 : 2);
            x++)
          for (int z = kind() == FactoryKind.BURNER_DRILL ? -1 : -2;
              z <= (kind() == FactoryKind.BURNER_DRILL ? 0 : 2);
              z++) {
            var p =
                kind() == FactoryKind.BURNER_DRILL
                    ? profile.offset(
                        worldPosition, profile.facing(getBlockState()), x + 1, -y, z + 1)
                    : center.offset(x, -y, z);
            if (!oreResult(p).isEmpty()) {
              oreTarget = p.immutable();
              oreIdentity =
                  ((ResourceDepositBlockEntity) factorycraft.fluids.FluidConnections.at(level, p))
                      .identity();
              break search;
            }
          }
      if (oreTarget == null) {
        nextOreSearchTick = level.getGameTime() + 20;
        progress = 0;
        miningPhase = 0;
        return false;
      }
    }
    var deposit =
        (ResourceDepositBlockEntity) factorycraft.fluids.FluidConnections.at(level, oreTarget);
    var result = oreResult(oreTarget);
    boolean uranium = deposit.resourceBlock().kind == factorycraft.resources.ResourceKind.URANIUM;
    duration = uranium ? 40 : 20;
    if (uranium
        && (!recipeAllowed(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    "factorycraft", "uranium_ore"))
            || miningAcid == null
            || miningAcid.amount() < 100
            || !factorycraft.multiblock.MachineFluidAccess.accepts(this, 0, miningAcid.key())))
      return false;
    var effects = modules.effects();
    int work = kind().workQuarters() * effects.speed(),
        next = progress + (miningPhase + work) / 40000;
    var earned =
        factorycraft.simulation.modules.ModuleEffects.accrue(
            effects.productivity(), next - progress, duration, miningProductivityRemainder);
    int bonus = (miningCredit + earned.credit()) / 10000;
    var batch = result.copyWithCount(1 + bonus);
    if (result.isEmpty() || !output(batch, true).isEmpty()) return false;
    beginPetroleumInventoryTransaction();
    try {
      if (kind() == FactoryKind.BURNER_DRILL) {
        while (miningFuel < 5) {
          var fuel = items.getStackInSlot(8);
          int burn = burnTime(fuel);
          if (burn <= 0) return false;
          var remainder = fuel.getCraftingRemainingItem();
          if (!remainder.isEmpty() && fuel.getCount() != 1) return false;
          items.extractItem(8, 1, false);
          if (!remainder.isEmpty()) items.setStackInSlot(8, remainder);
          miningFuel += 3L * burn;
        }
        miningFuel -= 5;
      } else {
        var charge =
            factorycraft.simulation.production.PaidWork.charge(
                kind().activeFE(), effects.energy(), furnaceEnergyRemainder);
        if (!power.spend(charge.energy())) return false;
        furnaceEnergyRemainder = charge.remainder();
      }
      int total = miningPhase + work;
      progress = next;
      miningPhase = total % 40000;
      miningCredit += earned.credit();
      miningProductivityRemainder = earned.remainder();
      markPersistentState();
      if (progress >= duration) {
        // The current canonical tile is debited once; productivity is an explicit earned bonus.
        Runnable commit =
            () -> {
              output(batch, false);
              progress -= duration;
              miningCredit %= 10000;
              if (!deposit.extractAccepted())
                throw new IllegalStateException("Reserved ore disappeared");
            };
        if (uranium) {
          factorycraft.simulation.fluids.FluidCell.commitBatch(
              java.util.List.of(
                  new factorycraft.simulation.fluids.FluidCell.Mutation<>(
                      miningAcid, miningAcid.key(), miningAcid.amount() - 100)),
              commit,
              false);
          factorycraft.research.ResearchTriggers.record(
              (net.minecraft.server.level.ServerLevel) level,
              researchScope,
              "mine:factorycraft:uranium_ore",
              1);
        } else commit.run();
        sync();
      }
      return true;
    } finally {
      endPetroleumInventoryTransaction();
    }
  }

  @Override
  public void setBlockState(BlockState state) {
    boolean rotated =
        getBlockState().getValue(FactoryBlock.FACING) != state.getValue(FactoryBlock.FACING);
    if (assembly != null
        && assembly.formed()
        && getBlockState().getValue(FactoryBlock.FACING) != state.getValue(FactoryBlock.FACING))
      assembly.dismantle();
    super.setBlockState(state);
    if (petroleum != null && rotated) {
      petroleum.rotated();
      if (level != null && !level.isClientSide) level.invalidateCapabilities(worldPosition);
    }
  }

  @Override
  public void dropContents() {
    if (assembly != null) assembly.dismantle();
    if (assembly == null) super.dropContents();
  }

  @Override
  public void onLoad() {
    super.onLoad();
    loaded = true;
    if (assembly != null) assembly.invalidate();
    if (!level.isClientSide) {
      if (petroleum != null) petroleumAvailable();
      if (!kind().burner()) PowerGrid.add(this);
      factorycraft.modules.BeaconIndex.add(this);
    }
  }

  @Override
  public void setRemoved() {
    loaded = false;
    petroleumChunk = null;
    if (assembly != null) assembly.invalidate();
    if (level != null && !level.isClientSide) {
      PowerGrid.remove(this);
      factorycraft.modules.BeaconIndex.remove(this);
    }
    super.setRemoved();
  }

  @Override
  public void onChunkUnloaded() {
    loaded = false;
    petroleumChunk = null;
    if (assembly != null) assembly.invalidate();
    if (level != null && !level.isClientSide) {
      PowerGrid.remove(this);
      factorycraft.modules.BeaconIndex.remove(this);
    }
    super.onChunkUnloaded();
  }

  @Override
  public Component getDisplayName() {
    return Component.translatable("block.factorycraft." + kind().id);
  }

  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
    return petroleum == null
        ? new FactoryMenu(id, inv, this, data)
        : new factorycraft.petroleum.PetroleumMenu(id, inv, this, petroleum.data);
  }

  @Override
  protected void saveAdditional(CompoundTag t, HolderLookup.Provider r) {
    super.saveAdditional(t, r);
    modules.save(t, r);
    construction.save(t, r);
    if (miningAcid != null && miningAcid.amount() > 0)
      t.put("MiningAcid", miningAcid.key().stack(miningAcid.amount()).save(r));
    if (uraniumWork != null) uraniumWork.save(t, r);
    if (!researchScope.isEmpty()) t.putString("ResearchScope", researchScope);
    t.putInt("Schema", 2);
    t.putString("ActiveDefinition", activeDefinition);
    t.put("Items", items.serializeNBT(r));
    t.putInt("Energy", power.getEnergyStored());
    t.putInt("Progress", progress);
    t.putInt("Duration", duration);
    t.putInt("Fuel", fuelTicks);
    if (oreTarget != null && oreIdentity != null) {
      t.putLong("OreTarget", oreTarget.subtract(worldPosition).asLong());
      t.putUUID("OreIdentity", oreIdentity);
    }
    t.putLong("MiningFuel", miningFuel);
    t.putInt("MiningPhase", miningPhase);
    t.putInt("MiningCredit", miningCredit);
    t.putInt("MiningProductivityRemainder", miningProductivityRemainder);
    if (kind().furnace() || kind().assembler() || kind().drill())
      t.putInt("FurnaceEnergyRemainder", furnaceEnergyRemainder);
    t.putString("Selected", selected);
    t.putString("ActiveRecipe", activeRecipe);
    if (assembly != null) assembly.save(t);
    if (petroleum != null) petroleum.save(t, r);
  }

  @Override
  protected void loadAdditional(CompoundTag t, HolderLookup.Provider r) {
    super.loadAdditional(t, r);
    modules.load(t, r);
    construction.load(t, r);
    if (miningAcid != null) {
      var acid =
          net.neoforged.neoforge.fluids.FluidStack.parseOptional(r, t.getCompound("MiningAcid"));
      if (!acid.isEmpty()
          && (acid.getAmount() > 2000
              || !factorycraft.multiblock.MachineFluidAccess.accepts(
                  this, 0, new factorycraft.fluids.FluidKey(acid))))
        throw new IllegalArgumentException("Drill acid");
      miningAcid.restore(
          acid.isEmpty() ? null : new factorycraft.fluids.FluidKey(acid), acid.getAmount());
    }

    if (uraniumWork != null) uraniumWork.load(t, r);
    researchScope = t.getString("ResearchScope");
    items.deserializeNBT(r, t.getCompound("Items"));
    power.restore(t.getInt("Energy"));
    progress = Math.max(0, t.getInt("Progress"));
    duration = Math.max(1, t.getInt("Duration"));
    fuelTicks = Math.max(0, t.getInt("Fuel"));
    oreTarget =
        t.contains("OreTarget") ? worldPosition.offset(BlockPos.of(t.getLong("OreTarget"))) : null;
    oreIdentity = t.hasUUID("OreIdentity") ? t.getUUID("OreIdentity") : null;
    if (oreTarget != null
        && (Math.abs(oreTarget.getX() - worldPosition.getX()) > 5
            || Math.abs(oreTarget.getZ() - worldPosition.getZ()) > 5
            || oreTarget.getY() >= worldPosition.getY()
            || oreTarget.getY() < worldPosition.getY() - 8
            || oreIdentity == null)) throw new IllegalArgumentException("Invalid mining target");
    miningFuel = t.getLong("MiningFuel");
    miningPhase = t.getInt("MiningPhase");
    miningCredit = t.getInt("MiningCredit");
    miningProductivityRemainder = t.getInt("MiningProductivityRemainder");
    if (miningFuel < 0
        || miningPhase < 0
        || miningPhase >= 40000
        || miningCredit < 0
        || miningCredit >= 20000
        || miningProductivityRemainder < 0
        || miningProductivityRemainder >= 20)
      throw new IllegalArgumentException("Invalid mining ledger");
    furnaceEnergyRemainder = t.getInt("FurnaceEnergyRemainder");
    if (furnaceEnergyRemainder < 0 || furnaceEnergyRemainder >= 10000)
      throw new IllegalArgumentException("Invalid furnace energy remainder");
    selected = t.getString("Selected");
    activeDefinition = t.getString("ActiveDefinition");
    activeRecipe = t.getString("ActiveRecipe");
    recipeCacheTick = Long.MIN_VALUE;
    if (assembly != null) assembly.load(t);
    if (petroleum != null) petroleum.load(t, r);
  }
}
