package factorycraft.nuclear;

import factorycraft.fluids.FluidConnections;
import factorycraft.simulation.nuclear.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.*;

/** A cell owns a fixed 4000-tick burn. Cap losses never extend/refund fuel. */
public final class ReactorBlockEntity extends NuclearOwner implements HeatOwner {
  public final HeatStore heat = new HeatStore(400_000);
  public int burnTicks, neighbors;
  private ItemStack burning = ItemStack.EMPTY;
  private boolean active;
  private long lastBurn = Long.MIN_VALUE;
  public long generated, dumped;
  public final ItemStackHandler items =
      new ItemStackHandler(2) {
        public boolean isItemValid(int s, ItemStack stack) {
          return s == 0
              && NuclearItems.is(stack, "uranium_fuel_cell")
              && !stack.has(DataComponents.BLOCK_ENTITY_DATA)
              && !stack.has(DataComponents.CONTAINER);
        }

        protected void onContentsChanged(int s) {
          changed();
        }
      };
  private final IItemHandler ports =
      new IItemHandler() {
        public int getSlots() {
          return 2;
        }

        public ItemStack getStackInSlot(int s) {
          return ready() ? items.getStackInSlot(s).copy() : ItemStack.EMPTY;
        }

        public ItemStack insertItem(int s, ItemStack stack, boolean sim) {
          return ready() && s == 0 ? items.insertItem(s, stack, sim) : stack;
        }

        public ItemStack extractItem(int s, int n, boolean sim) {
          return ready() ? items.extractItem(s, n, sim) : ItemStack.EMPTY;
        }

        public int getSlotLimit(int s) {
          return 64;
        }

        public boolean isItemValid(int s, ItemStack stack) {
          return ready() && items.isItemValid(s, stack);
        }
      };

  public ReactorBlockEntity(BlockPos p, BlockState s) {
    super(p, s);
  }

  public ItemStack burning() {
    return burning.copy();
  }

  public IItemHandler inventory() {
    return ports;
  }

  public NuclearOwner heatController() {
    return this;
  }

  public HeatStore heat() {
    return heat;
  }

  public long maximumTransfer() {
    return 20_000_000;
  }

  public List<HeatOwner.Port> heatPorts() {
    var result = new ArrayList<HeatOwner.Port>();
    var p = assembly().profile();
    var f = p.facing(getBlockState());
    for (int t : new int[] {0, 2, 4}) {
      result.add(new HeatOwner.Port(p.offset(worldPosition, f, t, 0, 0), f));
      result.add(new HeatOwner.Port(p.offset(worldPosition, f, t, 0, 4), f.getOpposite()));
      result.add(new HeatOwner.Port(p.offset(worldPosition, f, 0, 0, t), f.getCounterClockWise()));
      result.add(new HeatOwner.Port(p.offset(worldPosition, f, 4, 0, t), f.getClockWise()));
    }
    return List.copyOf(result);
  }

  public void prepareFuel() {
    if (!ready() || burnTicks > 0 || heat.units() == heat.maximum) return;
    var fuel = items.getStackInSlot(0);
    var spent = items.getStackInSlot(1);
    if (!items.isItemValid(0, fuel)
        || fuel.isEmpty()
        || (!spent.isEmpty()
            && (!ItemStack.isSameItemSameComponents(
                    spent, NuclearItems.stack("depleted_uranium_fuel_cell", 1))
                || spent.getCount() >= spent.getMaxStackSize()))) return;
    burning = items.extractItem(0, 1, false);
    burnTicks = 4000;
    changed();
  }

  public void snapshotActive() {
    active = ready() && burnTicks > 0;
  }

  public void burnForTick() {
    if (!active || lastBurn == level.getGameTime()) return;
    lastBurn = level.getGameTime();
    neighbors = 0;
    var profile = assembly().profile();
    var center = profile.center(worldPosition, profile.facing(getBlockState()));
    for (var direction : Direction.Plane.HORIZONTAL) {
      var otherCenter = center.relative(direction, 5);
      var other = FluidConnections.endpoint(level, otherCenter);
      if (other instanceof ReactorBlockEntity r
          && r != this
          && r.active
          && r.simulating()
          && r.getBlockPos().getY() == worldPosition.getY()) {
        var rp = r.assembly().profile();
        if (rp.center(r.getBlockPos(), rp.facing(r.getBlockState())).equals(otherCenter))
          neighbors++;
      }
    }
    long amount = (1L + neighbors) * EnergyUnits.REACTOR_PER_TICK;
    generated += amount;
    dumped += amount - heat.add(amount);
    if (--burnTicks == 0) {
      var spent = items.getStackInSlot(1).copy();
      if (spent.isEmpty()) spent = NuclearItems.stack("depleted_uranium_fuel_cell", 1);
      else spent.grow(1);
      items.setStackInSlot(1, spent);
      burning = ItemStack.EMPTY;
    }
    changed();
  }

  public void tick() {
    if (ready()) {
      active(burnTicks > 0);
      syncPeriodic();
    }
  }

  public int[] metrics() {
    return new int[] {
      (int) heat.units(),
      (int) heat.maximum,
      (int) (heat.temperature() * 1000),
      burnTicks,
      neighbors,
      ready() ? 1 : 0,
      0,
      0
    };
  }

  protected void saveAdditional(CompoundTag n, HolderLookup.Provider r) {
    super.saveAdditional(n, r);
    n.putLong("Heat", heat.units());
    n.putInt("BurnTicks", burnTicks);
    if (!burning.isEmpty()) n.put("Burning", burning.save(r));
    n.put("FuelItems", items.serializeNBT(r));
    n.putLong("LastBurn", lastBurn);
    n.putLong("GeneratedHeat", generated);
    n.putLong("DumpedHeat", dumped);
  }

  protected void loadAdditional(CompoundTag n, HolderLookup.Provider r) {
    super.loadAdditional(n, r);
    heat.restore(n.getLong("Heat"));
    burnTicks = n.getInt("BurnTicks");
    burning = ItemStack.parseOptional(r, n.getCompound("Burning"));
    if (burnTicks < 0
        || burnTicks > 4000
        || (burnTicks > 0) != (!burning.isEmpty())
        || !burning.isEmpty() && (burning.getCount() != 1 || !items.isItemValid(0, burning)))
      throw new IllegalArgumentException("Reactor fuel ownership");
    if (n.contains("FuelItems")) {
      if (n.getCompound("FuelItems").getInt("Size") != 2)
        throw new IllegalArgumentException("Reactor inventory");
      items.deserializeNBT(r, n.getCompound("FuelItems"));
    }
    lastBurn = n.contains("LastBurn") ? n.getLong("LastBurn") : Long.MIN_VALUE;
    generated = n.getLong("GeneratedHeat");
    dumped = n.getLong("DumpedHeat");
  }
}
