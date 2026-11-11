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
  private int progress;
  private String scope = "", working = "";
  public final PowerBuffer power = new PowerBuffer(16000, this::markPersistentState);
  public final ItemStackHandler items =
      new ItemStackHandler(4) {
        protected void onContentsChanged(int slot) {
          if (transaction) inventoryDirty = true;
          else sync();
        }

        public boolean isItemValid(int slot, ItemStack stack) {
          if (level == null)
            return stack.is(Content.SCIENCE_RED.get()) || stack.is(Content.SCIENCE_GREEN.get());
          var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
          return ResearchCatalog.get(level).entries().stream()
              .anyMatch(e -> e.recipe().packs().containsKey(id));
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
    return progress;
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
          return 4;
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
      if (entry != null && !entry.recipe().sandboxOnly() && ledger.available(entry.logic())) {
        String work = entry.logic().signature();
        if (!work.equals(working)) {
          working = work;
          progress = 0;
        }
        int[] used = plan(entry.recipe());
        int fe = Math.max(4, 20 - Math.min(16, ledger.level("factorycraft:lab_efficiency")));
        if (used != null && power.spend(fe)) {
          active = true;
          progress++;
          markPersistentState();
          if (progress >= entry.recipe().ticks()) {
            transaction = true;
            try {
              if (ledger.completeUnit(
                  entry.logic(),
                  () -> {
                    for (int s = 0; s < 4; s++)
                      if (used[s] > 0)
                        items.setStackInSlot(
                            s,
                            items
                                .getStackInSlot(s)
                                .copyWithCount(items.getStackInSlot(s).getCount() - used[s]));
                    progress = 0;
                  })) data.setDirty();
            } finally {
              transaction = false;
              if (inventoryDirty) {
                inventoryDirty = false;
                sync();
              }
            }
          }
        }
      } else if (progress != 0 || !working.isEmpty()) {
        progress = 0;
        working = "";
        markPersistentState();
      }
    }
    if (getBlockState().getValue(FactoryBlock.ACTIVE) != active)
      level.setBlock(worldPosition, getBlockState().setValue(FactoryBlock.ACTIVE, active), 3);
  }

  private int[] plan(TechnologyRecipe tech) {
    int[] used = new int[4];
    for (var e : tech.packs().entrySet()) {
      int needed = e.getValue();
      for (int s = 0; s < 4 && needed > 0; s++)
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
    if (!level.isClientSide) PowerGrid.add(this);
  }

  public void onChunkUnloaded() {
    loaded = false;
    assembly.invalidate();
    if (!level.isClientSide) PowerGrid.remove(this);
    super.onChunkUnloaded();
  }

  public void setRemoved() {
    loaded = false;
    assembly.invalidate();
    if (level != null && !level.isClientSide) PowerGrid.remove(this);
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
    tag.putInt("Schema", 1);
    tag.put("Items", items.serializeNBT(r));
    tag.putInt("Energy", power.getEnergyStored());
    tag.putString("ResearchScope", scope);
    tag.putString("Working", working);
    tag.putInt("Progress", progress);
    assembly.save(tag);
  }

  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
    super.loadAdditional(tag, r);
    items.deserializeNBT(r, tag.getCompound("Items"));
    power.restore(tag.getInt("Energy"));
    scope = tag.getString("ResearchScope");
    working = tag.getString("Working");
    progress = Math.clamp(tag.getInt("Progress"), 0, 72000);
    assembly.load(tag);
  }
}
