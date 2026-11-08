package factorycraft.blockentity;

import factorycraft.blocks.FactoryBlock;
import factorycraft.energy.*;
import factorycraft.fluids.FluidConnections;
import factorycraft.modules.*;
import factorycraft.multiblock.*;
import factorycraft.production.PowerBuffer;
import factorycraft.registry.Content;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.*;

public final class BeaconBlockEntity extends FactoryBlockEntity
    implements AssemblyOwner, GridConsumer, MenuProvider {
  private final IndustrialAssembly assembly = new IndustrialAssembly(this);
  private boolean loaded, paid;
  private long paidTick = Long.MIN_VALUE;
  public final PowerBuffer power = new PowerBuffer(16000, this::markPersistentState);
  public final ItemStackHandler modules =
      new ItemStackHandler(2) {
        public int getSlotLimit(int slot) {
          return 1;
        }

        public boolean isItemValid(int slot, ItemStack stack) {
          return stack.getItem() instanceof ModuleItem item
              && item.kind != ModuleItem.Kind.PRODUCTIVITY;
        }

        protected void onContentsChanged(int slot) {
          BeaconIndex.changed(BeaconBlockEntity.this);
          sync();
        }
      };

  public BeaconBlockEntity(BlockPos p, BlockState s) {
    super(Content.BEACON.get(), p, s);
  }

  public boolean loaded() {
    return loaded && !isRemoved();
  }

  public IndustrialAssembly assembly() {
    return assembly;
  }

  public boolean ready() {
    return loaded && !isRemoved() && FluidConnections.canonical(this) && assembly.ready();
  }

  public PowerBuffer gridBuffer() {
    return power;
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

  private int bonus(boolean speed) {
    int sum = 0;
    for (int s = 0; s < 2; s++)
      if (modules.getStackInSlot(s).getItem() instanceof ModuleItem item
          && item.kind != ModuleItem.Kind.PRODUCTIVITY)
        sum += speed ? item.kind.speed : item.kind.energy;
    return sum;
  }

  public int speedBonus() {
    return bonus(true);
  }

  public int energyBonus() {
    return bonus(false);
  }

  public boolean broadcasting() {
    return paid
        && level != null
        && (paidTick == level.getGameTime() || paidTick == level.getGameTime() + 1)
        && ready()
        && ((net.minecraft.server.level.ServerLevel) level)
            .shouldTickBlocksAt(new net.minecraft.world.level.ChunkPos(worldPosition).toLong())
        && (speedBonus() != 0 || energyBonus() != 0);
  }

  public void payForTick() {
    // A removed entry may still be in this tick's immutable index snapshot.
    if (!loaded() || level == null || !FluidConnections.canonical(this)) {
      paid = false;
      return;
    }
    paidTick = level.getGameTime() + 1;
    boolean next =
        ready()
            && ((net.minecraft.server.level.ServerLevel) level)
                .shouldTickBlocksAt(new net.minecraft.world.level.ChunkPos(worldPosition).toLong())
            && (speedBonus() != 0 || energyBonus() != 0)
            && power.spend(40);
    if (next != paid) {
      paid = next;
      BeaconIndex.changed(this);
    }
    if (getBlockState().getValue(FactoryBlock.ACTIVE) != next)
      level.setBlock(worldPosition, getBlockState().setValue(FactoryBlock.ACTIVE, next), 3);
  }

  private final IItemHandler port =
      new IItemHandler() {
        public int getSlots() {
          return 2;
        }

        public ItemStack getStackInSlot(int s) {
          return ready() ? modules.getStackInSlot(s).copy() : ItemStack.EMPTY;
        }

        public ItemStack insertItem(int s, ItemStack stack, boolean sim) {
          return ready() ? modules.insertItem(s, stack, sim) : stack;
        }

        public ItemStack extractItem(int s, int n, boolean sim) {
          return ready() ? modules.extractItem(s, n, sim) : ItemStack.EMPTY;
        }

        public int getSlotLimit(int s) {
          return 1;
        }

        public boolean isItemValid(int s, ItemStack stack) {
          return ready() && modules.isItemValid(s, stack);
        }
      };

  public IItemHandler inventory() {
    return port;
  }

  public void onLoad() {
    super.onLoad();
    loaded = true;
    assembly.invalidate();
    if (!level.isClientSide) {
      PowerGrid.add(this);
      BeaconIndex.add(this);
    }
  }

  public void onChunkUnloaded() {
    loaded = false;
    paid = false;
    assembly.invalidate();
    if (!level.isClientSide) {
      PowerGrid.remove(this);
      BeaconIndex.remove(this);
    }
    super.onChunkUnloaded();
  }

  public void setRemoved() {
    loaded = false;
    paid = false;
    assembly.invalidate();
    if (level != null && !level.isClientSide) {
      PowerGrid.remove(this);
      BeaconIndex.remove(this);
    }
    super.setRemoved();
  }

  public void dropContents() {
    assembly.removeWhole(true);
  }

  public Component getDisplayName() {
    return Component.translatable("block.factorycraft.beacon");
  }

  public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
    return new ModuleMenu(id, inv, this);
  }

  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
    super.saveAdditional(tag, r);
    tag.put("Modules", modules.serializeNBT(r));
    tag.putInt("Energy", power.getEnergyStored());
    assembly.save(tag);
  }

  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
    super.loadAdditional(tag, r);
    if (tag.contains("Modules")) modules.deserializeNBT(r, tag.getCompound("Modules"));
    power.restore(tag.getInt("Energy"));
    assembly.load(tag);
    paid = false;
  }
}
