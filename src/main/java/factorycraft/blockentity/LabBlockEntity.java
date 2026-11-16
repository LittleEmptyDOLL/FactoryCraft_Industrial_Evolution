package factorycraft.blockentity;

import factorycraft.blocks.FactoryBlock;
import factorycraft.energy.*;
import factorycraft.fluids.FluidConnections;
import factorycraft.multiblock.*;
import factorycraft.production.PowerBuffer;
import factorycraft.registry.Content;
import factorycraft.research.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.*;

/** Packs remain owned until one shared unit commits. Another lab cannot overrun the last unit. */
public final class LabBlockEntity extends FactoryBlockEntity
    implements AssemblyOwner, GridConsumer, MenuProvider {
  private final IndustrialAssembly assembly = new IndustrialAssembly(this);
  private boolean loaded, transaction, inventoryDirty;
  private final factorycraft.simulation.research.LabWork work =
      new factorycraft.simulation.research.LabWork();
  private int energyRemainder;
  private java.util.List<BeaconBlockEntity> beacons = java.util.List.of();
  public boolean beaconDirty = true;
  public final ItemStackHandler modules =
      new ItemStackHandler(2) {
        public int getSlotLimit(int slot) {
          return 1;
        }

        public boolean isItemValid(int slot, ItemStack stack) {
          return stack.getItem() instanceof factorycraft.modules.ModuleItem;
        }

        protected void onContentsChanged(int slot) {
          sync();
        }
      };

  public factorycraft.simulation.modules.ModuleEffects effects() {
    int speed = 0, energy = 0, productivity = 0, bs = 0, be = 0, count = 0;
    for (int i = 0; i < 2; i++)
      if (modules.getStackInSlot(i).getItem() instanceof factorycraft.modules.ModuleItem item) {
        speed += item.profile.speed();
        energy += item.profile.energy();
        productivity += item.profile.productivity();
      }
    if (beaconDirty) {
      beacons = factorycraft.modules.BeaconIndex.nearby(this);
      beaconDirty = false;
    }
    for (var beacon : beacons)
      if (beacon.broadcasting()) {
        bs += beacon.speedBonus();
        be += beacon.energyBonus();
        count++;
      }
    return factorycraft.simulation.modules.ModuleEffects.combine(
        speed, energy, productivity, bs, be, count);
  }

  public long productivityCredit() {
    return work.bonus();
  }

  public int researchSpeed() {
    return level instanceof ServerLevel server
        ? factorycraft.simulation.research.LabWork.researchSpeed(
            ResearchData.get(server.getServer()).team(scope))
        : 10000;
  }

  private static String workKey(
      ResearchCatalog.Entry entry, factorycraft.simulation.research.ResearchLedger ledger) {
    // Repeat levels change the number of units, not the pack/time cost of each unit. One signature
    // preserves paid overshoot when a level finishes mid-tick or another lab completes that level.
    return entry.logic().signature()
        + (entry.recipe().repeatable()
            ? "|repeatable"
            : "|level=" + ledger.level(entry.id().toString()));
  }

  public String workState() {
    if (!ready()) return "unavailable";
    if (!factorycraft.circuits.CircuitBinding.allowed(this)) return "disabled";
    if (!(level instanceof ServerLevel server) || !ResearchTeams.exists(server.getServer(), scope))
      return "team";
    var ledger = ResearchData.get(server.getServer()).team(scope);
    var id = ResourceLocation.tryParse(ledger.selected());
    var entry = id == null ? null : ResearchCatalog.get(level).entry(id);
    if (entry == null || !ledger.available(entry.logic())) return "idle";
    if (!work.canTrack(workKey(entry, ledger))) return "definition_limit";
    if (plan(entry.recipe()) == null) return "science";
    var charge =
        factorycraft.simulation.production.PaidWork.charge(8, effects().energy(), energyRemainder);
    return power.getEnergyStored() < charge.energy() ? "power" : "ready";
  }

  private String scope = "", working = "";
  public final PowerBuffer power = new PowerBuffer(16000, this::markPersistentState);
  public final ItemStackHandler items =
      new ItemStackHandler(SciencePacks.COUNT) {
        protected void onContentsChanged(int slot) {
          if (transaction) inventoryDirty = true;
          else sync();
        }

        public boolean isItemValid(int slot, ItemStack stack) {
          return SciencePacks.accepts(slot, stack);
        }
      };

  public LabBlockEntity(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
    super(Content.LAB.get(), pos, state);
  }

  public boolean loaded() {
    return loaded && !isRemoved();
  }

  public IndustrialAssembly assembly() {
    return assembly;
  }

  public PowerBuffer gridBuffer() {
    return power;
  }

  public String scope() {
    return scope;
  }

  public int progress() {
    return (int) (work.partial(working).work() / factorycraft.simulation.research.LabWork.UNIT);
  }

  public void bind(String team) {
    if (!scope.isEmpty()) throw new IllegalStateException("Lab is already bound");
    scope = team;
    sync();
  }

  public boolean ready() {
    return loaded && !isRemoved() && FluidConnections.canonical(this) && assembly.ready();
  }

  public int receiveGridEnergy(int n, boolean sim) {
    return ready() ? power.receiveEnergy(n, sim) : 0;
  }

  public final net.neoforged.neoforge.energy.IEnergyStorage energy =
      new net.neoforged.neoforge.energy.IEnergyStorage() {
        public int receiveEnergy(int n, boolean sim) {
          return receiveGridEnergy(n, sim);
        }

        public int extractEnergy(int n, boolean sim) {
          return 0;
        }

        public int getEnergyStored() {
          return ready() ? power.getEnergyStored() : 0;
        }

        public int getMaxEnergyStored() {
          return 16000;
        }

        public boolean canExtract() {
          return false;
        }

        public boolean canReceive() {
          return ready();
        }
      };
  private final IItemHandler port =
      new IItemHandler() {
        public int getSlots() {
          return SciencePacks.COUNT;
        }

        public ItemStack getStackInSlot(int s) {
          return ready() ? items.getStackInSlot(s).copy() : ItemStack.EMPTY;
        }

        public ItemStack insertItem(int s, ItemStack v, boolean sim) {
          return ready() ? items.insertItem(s, v, sim) : v;
        }

        public ItemStack extractItem(int s, int n, boolean sim) {
          return ready() ? items.extractItem(s, n, sim) : ItemStack.EMPTY;
        }

        public int getSlotLimit(int s) {
          return 64;
        }

        public boolean isItemValid(int s, ItemStack v) {
          return ready() && items.isItemValid(s, v);
        }
      };

  public IItemHandler inventory() {
    return port;
  }

  public void tick() {
    if (!factorycraft.circuits.CircuitBinding.allowed(this)) {
      if (getBlockState().getValue(FactoryBlock.ACTIVE))
        level.setBlock(worldPosition, getBlockState().setValue(FactoryBlock.ACTIVE, false), 3);
      return;
    }
    boolean active = false;
    if (ready()
        && level instanceof ServerLevel server
        && ResearchTeams.exists(server.getServer(), scope)) {
      var data = ResearchData.get(server.getServer());
      var ledger = data.team(scope);
      var catalog = ResearchCatalog.get(level);
      ResourceLocation id = ResourceLocation.tryParse(ledger.selected());
      var entry = id == null ? null : catalog.entry(id);
      if (entry != null
          && !entry.recipe().sandboxOnly()
          && entry.recipe().trigger().isEmpty()
          && ledger.available(entry.logic())) {
        working = workKey(entry, ledger);
        var effect = effects();
        var charge =
            factorycraft.simulation.production.PaidWork.charge(8, effect.energy(), energyRemainder);
        if (work.canTrack(working)
            && plan(entry.recipe()) != null
            && power.spend(charge.energy())) {
          active = true;
          energyRemainder = charge.remainder();
          transaction = true;
          try {
            work.advance(
                working,
                entry.recipe().ticks(),
                effect.speed(),
                researchSpeed(),
                effect.productivity(),
                () ->
                    ledger.selected().equals(entry.id().toString())
                        && ledger.available(entry.logic())
                        && plan(entry.recipe()) != null,
                () -> {
                  int[] used = plan(entry.recipe());
                  return used != null
                      && ledger.completeUnit(
                          entry.logic(),
                          () -> {
                            for (int slot = 0; slot < SciencePacks.COUNT; slot++)
                              if (used[slot] > 0)
                                items.setStackInSlot(
                                    slot,
                                    items
                                        .getStackInSlot(slot)
                                        .copyWithCount(
                                            items.getStackInSlot(slot).getCount() - used[slot]));
                          });
                },
                () -> ledger.completeUnit(entry.logic(), () -> {}));
            data.setDirty();
            markPersistentState();
          } finally {
            transaction = false;
            if (inventoryDirty) {
              inventoryDirty = false;
              sync();
            }
          }
        }
      }
    }
    if (getBlockState().getValue(FactoryBlock.ACTIVE) != active)
      level.setBlock(worldPosition, getBlockState().setValue(FactoryBlock.ACTIVE, active), 3);
  }

  private int[] plan(TechnologyRecipe tech) {
    int[] used = new int[SciencePacks.COUNT];
    for (var e : tech.packs().entrySet()) {
      int needed = e.getValue();
      for (int s = 0; s < SciencePacks.COUNT && needed > 0; s++)
        if (net.minecraft.core.registries.BuiltInRegistries.ITEM
            .getKey(items.getStackInSlot(s).getItem())
            .equals(e.getKey())) {
          int n = Math.min(needed, items.getStackInSlot(s).getCount() - used[s]);
          used[s] += n;
          needed -= n;
        }
      if (needed > 0) return null;
    }
    return used;
  }

  public void onLoad() {
    super.onLoad();
    loaded = true;
    assembly.invalidate();
    if (!level.isClientSide) {
      PowerGrid.add(this);
      factorycraft.modules.BeaconIndex.add(this);
    }
  }

  public void onChunkUnloaded() {
    loaded = false;
    assembly.invalidate();
    if (!level.isClientSide) {
      PowerGrid.remove(this);
      factorycraft.modules.BeaconIndex.remove(this);
    }
    super.onChunkUnloaded();
  }

  public void setRemoved() {
    loaded = false;
    assembly.invalidate();
    if (level != null && !level.isClientSide) {
      PowerGrid.remove(this);
      factorycraft.modules.BeaconIndex.remove(this);
    }
    super.setRemoved();
  }

  public void dropContents() {
    assembly.removeWhole(true);
  }

  public Component getDisplayName() {
    return Component.translatable("block.factorycraft.lab");
  }

  public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
    return ResearchTeams.member(player, scope) ? new ResearchMenu(id, inv, this) : null;
  }

  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
    super.saveAdditional(tag, r);
    tag.putInt("Schema", 3);
    tag.put("Items", items.serializeNBT(r));
    tag.putInt("Energy", power.getEnergyStored());
    tag.putString("ResearchScope", scope);
    tag.putString("Working", working);
    tag.put("Modules", modules.serializeNBT(r));
    tag.putInt("EnergyRemainder", energyRemainder);
    tag.putLong("ProductivityCredit", work.bonus());
    var partials = new net.minecraft.nbt.ListTag();
    work.partials()
        .forEach(
            (key, value) -> {
              var t = new CompoundTag();
              t.putString("Key", key);
              t.putLong("Work", value.work());
              t.putLong("Remainder", value.productivityRemainder());
              partials.add(t);
            });
    tag.put("PartialWork", partials);
    assembly.save(tag);
  }

  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
    super.loadAdditional(tag, r);
    if (tag.getCompound("Items").getInt("Size") != SciencePacks.COUNT
        || tag.getCompound("Modules").getInt("Size") != 2)
      throw new IllegalArgumentException("Unsupported lab inventory layout");
    items.deserializeNBT(r, tag.getCompound("Items"));
    power.restore(tag.getInt("Energy"));
    scope = tag.getString("ResearchScope");
    working = tag.getString("Working");
    if (tag.getCompound("Items").getInt("Size") != SciencePacks.COUNT)
      throw new IllegalArgumentException("Unsupported lab science inventory");
    modules.deserializeNBT(r, tag.getCompound("Modules"));
    if (modules.getSlots() != 2) throw new IllegalArgumentException("Invalid lab modules");
    for (int i = 0; i < 2; i++)
      if (!modules.getStackInSlot(i).isEmpty()
          && (!modules.isItemValid(i, modules.getStackInSlot(i))
              || modules.getStackInSlot(i).getCount() != 1))
        throw new IllegalArgumentException("Invalid lab module stack");
    energyRemainder = Math.clamp(tag.getInt("EnergyRemainder"), 0, 9999);
    var partials =
        new java.util.HashMap<String, factorycraft.simulation.research.LabWork.Partial>();
    var values = tag.getList("PartialWork", 10);
    if (values.size() > factorycraft.simulation.research.LabWork.MAX_DEFINITIONS)
      throw new IllegalArgumentException("Lab partial work limit");
    for (var value : values) {
      var t = (CompoundTag) value;
      var key = t.getString("Key");
      if (key.length() > 128
          || partials.put(
                  key,
                  new factorycraft.simulation.research.LabWork.Partial(
                      t.getLong("Work"), t.getLong("Remainder")))
              != null) throw new IllegalArgumentException("Invalid laboratory work key");
    }
    work.restore(tag.getLong("ProductivityCredit"), partials);
    beaconDirty = true;
    assembly.load(tag);
  }
}
