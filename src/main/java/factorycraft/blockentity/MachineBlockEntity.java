package factorycraft.blockentity;

import factorycraft.blocks.*;
import factorycraft.config.FactoryConfig;
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
        factorycraft.energy.GridConsumer,
        factorycraft.energy.GridGenerator {
  @Override
  public boolean generatesElectricity() {
    return kind() == FactoryKind.GENERATOR;
  }

  @Override
  public int receiveGridEnergy(int n, boolean simulate) {
    return loaded()
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
  private boolean loaded;
  private String researchScope = "";

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
      new ItemStackHandler(9) {
        @Override
        protected void onContentsChanged(int s) {
          if (petroleumTransaction) petroleumInventoryDirty = true;
          else setChanged();
          if (petroleum != null) petroleum.changed();
        }

        @Override
        public boolean isItemValid(int s, ItemStack stack) {
          if (s >= 4 && s < 8) return false;
          if (s == 8) return kind() == FactoryKind.GENERATOR && burnTime(stack) > 0;
          return kind().processing();
        }
      };
  public final PowerBuffer power = new PowerBuffer(16000, this::markPersistentState);
  private final IItemHandler automation =
      new IItemHandler() {
        public int getSlots() {
          return 9;
        }

        public ItemStack getStackInSlot(int s) {
          return automationReady() ? items.getStackInSlot(s).copy() : ItemStack.EMPTY;
        }

        public ItemStack insertItem(int s, ItemStack stack, boolean sim) {
          return automationReady() ? items.insertItem(s, stack, sim) : stack;
        }

        public ItemStack extractItem(int s, int n, boolean sim) {
          return automationReady() && s >= 4 && s < 8
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
          return automationReady() ? power.receiveEnergy(n, sim) : 0;
        }

        public int extractEnergy(int n, boolean sim) {
          return 0;
        }

        public int getEnergyStored() {
          return automationReady() ? power.getEnergyStored() : 0;
        }

        public int getMaxEnergyStored() {
          return power.getMaxEnergyStored();
        }

        public boolean canExtract() {
          return false;
        }

        public boolean canReceive() {
          return automationReady();
        }
      };
  private final IndustrialAssembly assembly;

  public IndustrialAssembly assembly() {
    return assembly;
  }

  private int progress, duration = 1, fuelTicks;
  private String selected = "", activeRecipe = "";
  private List<RecipeHolder<FactoryRecipe>> recipeCache = List.of();
  private long recipeCacheTick = Long.MIN_VALUE;
  private BlockPos oreTarget;
  public final ContainerData data =
      new ContainerData() {
        public int get(int i) {
          return switch (i) {
            case 0 -> progress;
            case 1 -> duration;
            case 2 -> power.getEnergyStored();
            case 3 -> fuelTicks;
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
    return fuelTicks;
  }

  public List<RecipeHolder<FactoryRecipe>> recipes() {
    if (level == null) return List.of();
    long now = level.getGameTime();
    if (recipeCacheTick == Long.MIN_VALUE || now - recipeCacheTick >= 20) {
      recipeCache =
          level.getRecipeManager().getAllRecipesFor(Content.PROCESSING.get()).stream()
              .filter(h -> h.value().machine().equals(kind().recipeGroup()))
              .sorted(Comparator.comparing(h -> h.id().toString()))
              .toList();
      recipeCacheTick = now;
    }
    return recipeCache;
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
    if (kind() != FactoryKind.ASSEMBLER && kind() != FactoryKind.ADVANCED_ASSEMBLER) return false;
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
    if (kind() == FactoryKind.GENERATOR) {
      generate();
      return;
    }
    boolean worked = kind() == FactoryKind.DRILL ? mine() : produce();
    if (worked && level.getGameTime() % 20 == 0) sync();
    setActive(worked);
  }

  private void setActive(boolean active) {
    if (getBlockState().getValue(FactoryBlock.ACTIVE) != active)
      level.setBlock(worldPosition, getBlockState().setValue(FactoryBlock.ACTIVE, active), 3);
  }

  private void generate() {
    boolean hasDemand = PowerGrid.hasDemand(this);
    if (fuelTicks <= 0 && hasDemand) {
      var fuel = items.getStackInSlot(8);
      int burn = burnTime(fuel);
      if (burn > 0) {
        ItemStack remainder = fuel.getCraftingRemainingItem();
        // Never replace a stacked container fuel with its remainder.
        if (!remainder.isEmpty() && fuel.getCount() > 1) {
          setActive(false);
          return;
        }
        items.extractItem(8, 1, false);
        if (!remainder.isEmpty()) items.setStackInSlot(8, remainder);
        fuelTicks = burn;
        markPersistentState();
      }
    }
    boolean active = fuelTicks > 0 && hasDemand;
    if (active) {
      int supplied = PowerGrid.distribute(this, FactoryConfig.GENERATOR_FE.get());
      active = supplied > 0;
      if (active) {
        fuelTicks--;
        markPersistentState();
      }
    }
    setActive(active);
  }

  private boolean produce() {
    var input = new FactoryInput(items);
    RecipeHolder<FactoryRecipe> chosen = null;
    for (var r : recipes()) {
      if (!selected.isEmpty() && !selected.equals(r.id().toString())) continue;
      if (!activeRecipe.isEmpty() && progress > 0 && !activeRecipe.equals(r.id().toString()))
        continue;
      if (recipeAllowed(r.id()) && r.value().matches(input, level)) {
        chosen = r;
        break;
      }
    }
    if (chosen == null) return false;
    if (modules.enabled()) return produceWithModules(chosen, input);
    var recipe = chosen.value();
    if (!output(recipe.result(), true).isEmpty()) return false;
    int step = kind() == FactoryKind.INDUSTRIAL_FURNACE ? 2 : 1;
    int advancing = Math.min(step, recipe.ticks() - Math.min(progress, recipe.ticks() - 1));
    if (!power.spend(recipe.energy() * advancing)) return false;
    activeRecipe = chosen.id().toString();
    duration = recipe.ticks();
    progress += advancing;
    markPersistentState();
    factorycraft.diagnostics.FactoryTelemetry.worked(this, recipe.energy() * advancing);
    if (progress >= duration) {
      int[] plan = recipe.consumptionPlan(input);
      // Single server-thread commit, after every output slot and input count has been checked.
      if (plan != null && output(recipe.result(), true).isEmpty()) {
        for (int s = 0; s < plan.length; s++) if (plan[s] > 0) items.extractItem(s, plan[s], false);
        output(recipe.result().copy(), false);
        factorycraft.diagnostics.FactoryTelemetry.produced(this);
        progress = 0;
        activeRecipe = "";
        sync();
      }
    }
    return true;
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
            kind() == FactoryKind.INDUSTRIAL_FURNACE ? 2 : 1,
            recipe.ticks() - Math.min(current, recipe.ticks() - 1),
            modules.identityChanged(key) ? 0 : modules.fraction());
    if (!power.spend(step.energy())) return false;
    progress = current + step.work();
    duration = recipe.ticks();
    activeRecipe = chosen.id().toString();
    modules.advance(key, step.remainder(), step.work(), duration, rate);
    if (progress >= duration) {
      var plan = recipe.consumptionPlan(input);
      if (plan == null) throw new IllegalStateException("Reserved module ingredients changed");
      beginPetroleumInventoryTransaction();
      try {
        for (int s = 0; s < plan.length; s++) if (plan[s] > 0) items.extractItem(s, plan[s], false);
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
    return remainder;
  }

  private ItemStack oreResult(BlockPos pos) {
    var be = factorycraft.fluids.FluidConnections.at(level, pos);
    return be instanceof ResourceDepositBlockEntity deposit
            && deposit.available()
            && deposit.remaining() > 0
        ? deposit.resourceBlock().kind.result()
        : ItemStack.EMPTY;
  }

  private boolean mine() {
    duration = FactoryConfig.DRILL_TICKS.get();
    if (oreTarget == null || oreResult(oreTarget).isEmpty()) {
      oreTarget = null;
      if (level.getGameTime() % 20 != 0) return false;
      var profile = factorycraft.multiblock.MachineProfile.DRILL;
      var center = profile.center(worldPosition, getBlockState().getValue(FactoryBlock.FACING));
      search:
      for (int y = 1; y <= 8; y++)
        for (int x = -2; x <= 2; x++)
          for (int z = -2; z <= 2; z++) {
            var p = center.offset(x, -y, z);
            if (!oreResult(p).isEmpty()) {
              oreTarget = p.immutable();
              break search;
            }
          }
    }
    if (oreTarget == null) return false;
    var result = oreResult(oreTarget);
    if (result.isEmpty() || !output(result, true).isEmpty() || !power.spend(10)) return false;
    progress++;
    markPersistentState();
    if (progress >= duration) {
      var be = factorycraft.fluids.FluidConnections.at(level, oreTarget);
      if (be instanceof ResourceDepositBlockEntity deposit
          && deposit.available()
          && deposit.remaining() > 0
          && output(result, true).isEmpty()) {
        beginPetroleumInventoryTransaction();
        try {
          output(result, false);
          deposit.extractAccepted();
          progress = 0;
        } finally {
          endPetroleumInventoryTransaction();
        }
        sync();
      }
    }
    return true;
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
      PowerGrid.add(this);
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
    if (!researchScope.isEmpty()) t.putString("ResearchScope", researchScope);
    t.putInt("Schema", 1);
    t.put("Items", items.serializeNBT(r));
    t.putInt("Energy", power.getEnergyStored());
    t.putInt("Progress", progress);
    t.putInt("Duration", duration);
    t.putInt("Fuel", fuelTicks);
    t.putString("Selected", selected);
    t.putString("ActiveRecipe", activeRecipe);
    if (assembly != null) assembly.save(t);
    if (petroleum != null) petroleum.save(t, r);
  }

  @Override
  protected void loadAdditional(CompoundTag t, HolderLookup.Provider r) {
    super.loadAdditional(t, r);
    modules.load(t, r);
    researchScope = t.getString("ResearchScope");
    items.deserializeNBT(r, t.getCompound("Items"));
    power.restore(t.getInt("Energy"));
    progress = Math.max(0, t.getInt("Progress"));
    duration = Math.max(1, t.getInt("Duration"));
    fuelTicks = Math.max(0, t.getInt("Fuel"));
    selected = t.getString("Selected");
    activeRecipe = t.getString("ActiveRecipe");
    recipeCacheTick = Long.MIN_VALUE;
    if (assembly != null) assembly.load(t);
    if (petroleum != null) petroleum.load(t, r);
  }
}
