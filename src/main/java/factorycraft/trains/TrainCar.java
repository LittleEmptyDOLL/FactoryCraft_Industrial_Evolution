package factorycraft.trains;

import factorycraft.fluids.FluidKey;
import factorycraft.simulation.fluids.FluidCell;
import factorycraft.simulation.trains.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Canonical carriage record. Render entities never serialize this inventory or fluid. */
public final class TrainCar {
  public final UUID id;
  public final StockSpec kind;
  public final ItemStackHandler items;
  public final FluidCell<FluidKey> fluid;
  public boolean reversed;
  public double health;
  public int color = 0xb45c27, bar;
  public final String[] filters;
  public long fuelJ, spentJ;

  /** Transient native-transfer version; never a second cargo owner or persisted resource. */
  public long cargoVersion;

  public double fuelAcceleration = 1, fuelSpeed = 1;
  private final Runnable dirty;
  public ItemStack shell = ItemStack.EMPTY;
  public factorycraft.simulation.rail.RailVector recovery;
  public float recoveryYaw, recoveryPitch;
  factorycraft.simulation.fluids.FluidCell<FluidKey> guardedFluid;
  StockEntity fluidProjection;

  public TrainCar(UUID id, StockSpec kind, Runnable dirty) {
    this.id = id;
    this.kind = kind;
    this.dirty = dirty;
    health = kind.health;
    int count = kind == StockSpec.LOCOMOTIVE ? 4 : kind.slots;
    bar = kind == StockSpec.CARGO ? 40 : count;
    filters = new String[count];
    Arrays.fill(filters, "");
    items =
        new ItemStackHandler(count) {
          protected void onContentsChanged(int slot) {
            if (kind == StockSpec.CARGO) cargoVersion++;
            dirty.run();
          }

          public boolean isItemValid(int slot, ItemStack stack) {
            if (kind == StockSpec.LOCOMOTIVE) return slot < 3 && TrainFuel.value(stack) != null;
            return kind == StockSpec.CARGO
                && slot < bar
                && (filters[slot].isEmpty()
                    || BuiltInRegistries.ITEM
                        .getKey(stack.getItem())
                        .toString()
                        .equals(filters[slot]));
          }
        };
    fluid =
        new FluidCell<>(
            StockSpec.FLUID_CAPACITY,
            () -> {
              cargoVersion++;
              dirty.run();
            });
  }

  public void changed() {
    dirty.run();
  }

  public CompoundTag save(HolderLookup.Provider registries) {
    var n = new CompoundTag();
    if (!shell.isEmpty()) n.put("Shell", shell.save(registries));
    if (recovery != null) {
      n.putDouble("X", recovery.x());
      n.putDouble("Y", recovery.y());
      n.putDouble("Z", recovery.z());
      n.putFloat("Yaw", recoveryYaw);
      n.putFloat("Pitch", recoveryPitch);
    }
    n.putUUID("Id", id);
    n.putInt("Kind", kind.ordinal());
    n.putBoolean("Reversed", reversed);
    n.putDouble("Health", health);
    n.putInt("Color", color);
    n.putInt("Bar", bar);
    n.put("Items", items.serializeNBT(registries));
    if (fluid.amount() > 0) n.put("Fluid", fluid.key().stack(fluid.amount()).save(registries));
    n.putLong("FuelJ", fuelJ);
    n.putLong("SpentJ", spentJ);
    n.putDouble("FuelAcceleration", fuelAcceleration);
    n.putDouble("FuelSpeed", fuelSpeed);
    var fs = new ListTag();
    for (String f : filters) fs.add(StringTag.valueOf(f));
    n.put("Filters", fs);
    return n;
  }

  public static TrainCar load(
      CompoundTag n, HolderLookup.Provider r, Runnable dirty, boolean fresh) {
    int type = n.getInt("Kind");
    if (type < 0 || type >= StockSpec.values().length)
      throw new IllegalArgumentException("Stock kind");
    var c =
        new TrainCar(fresh ? UUID.randomUUID() : n.getUUID("Id"), StockSpec.values()[type], dirty);
    c.health = n.getDouble("Health");
    c.reversed = n.getBoolean("Reversed");
    c.color = n.getInt("Color") & 0xffffff;
    c.bar = n.getInt("Bar");
    c.fuelJ = n.getLong("FuelJ");
    c.spentJ = n.getLong("SpentJ");
    c.fuelAcceleration = n.getDouble("FuelAcceleration");
    c.fuelSpeed = n.getDouble("FuelSpeed");
    if (!Double.isFinite(c.health)
        || c.health < 0
        || c.health > c.kind.health
        || c.fuelJ < 0
        || c.fuelJ > 20_000_000_000L
        || c.spentJ < 0
        || !Double.isFinite(c.fuelAcceleration + c.fuelSpeed)
        || c.fuelAcceleration < 1
        || c.fuelAcceleration > 2.5
        || c.fuelSpeed < 1
        || c.fuelSpeed > 1.15
        || c.bar < 0
        || c.bar > c.items.getSlots()) throw new IllegalArgumentException("Stock state");
    if (n.getCompound("Items").getInt("Size") != c.items.getSlots())
      throw new IllegalArgumentException("Stock inventory size");
    if (n.contains("Shell")) c.shell = ItemStack.parseOptional(r, n.getCompound("Shell"));
    if (n.contains("X")) {
      c.recovery =
          new factorycraft.simulation.rail.RailVector(
              n.getDouble("X"), n.getDouble("Y"), n.getDouble("Z"));
      c.recoveryYaw = n.getFloat("Yaw");
      c.recoveryPitch = n.getFloat("Pitch");
      if (!Float.isFinite(c.recoveryYaw + c.recoveryPitch))
        throw new IllegalArgumentException("Stock pose");
    }
    c.items.deserializeNBT(r, n.getCompound("Items"));
    if (c.items.getSlots() != (c.kind == StockSpec.LOCOMOTIVE ? 4 : c.kind.slots))
      throw new IllegalArgumentException("Stock inventory");
    var fs = n.getList("Filters", Tag.TAG_STRING);
    if (fs.size() != c.filters.length) throw new IllegalArgumentException("Stock filters");
    for (int i = 0; i < fs.size(); i++) {
      String s = fs.getString(i);
      if (!s.isEmpty() && net.minecraft.resources.ResourceLocation.tryParse(s) == null)
        throw new IllegalArgumentException("Stock filter ID");
      c.filters[i] = s;
    }
    var fluid = FluidStack.parseOptional(r, n.getCompound("Fluid"));
    if (fluid.getAmount() > StockSpec.FLUID_CAPACITY
        || !fluid.isEmpty() && c.kind != StockSpec.FLUID)
      throw new IllegalArgumentException("Stock fluid");
    c.fluid.restore(fluid.isEmpty() ? null : new FluidKey(fluid), fluid.getAmount());
    return c;
  }

  public ItemStack portable(HolderLookup.Provider registries) {
    var out =
        shell.isEmpty()
            ? new ItemStack(TrainContent.ITEMS.get(kind).get())
            : shell.copyWithCount(1);
    var tag = out.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    tag.put("FactoryTrainStock", save(registries));
    out.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    out.set(DataComponents.MAX_STACK_SIZE, 1);
    return out;
  }

  /** A prepared refuel/debit includes all remainders before publishing or notifying anything. */
  public record FuelPlan(
      TrainCar car,
      long left,
      double acceleration,
      double speed,
      List<ItemStack> inventory,
      long debit) {
    public void commit() {
      car.fuelJ = left;
      car.spentJ = Math.addExact(car.spentJ, debit);
      car.fuelAcceleration = acceleration;
      car.fuelSpeed = speed;
      for (int i = 0; i < inventory.size(); i++) car.items.setStackInSlot(i, inventory.get(i));
      car.changed();
    }
  }

  public FuelPlan reserveFuel(long debit) {
    if (kind != StockSpec.LOCOMOTIVE || debit < 0 || spentJ > Long.MAX_VALUE - debit) return null;
    if (fuelJ >= debit)
      return new FuelPlan(this, fuelJ - debit, fuelAcceleration, fuelSpeed, List.of(), debit);
    long left = fuelJ;
    double a = fuelAcceleration, s = fuelSpeed;
    var copy = new ArrayList<ItemStack>();
    for (int i = 0; i < 4; i++) copy.add(items.getStackInSlot(i).copy());
    while (left < debit) {
      int slot = -1;
      TrainFuel.Value value = null;
      for (int i = 0; i < 3; i++) {
        value = TrainFuel.value(copy.get(i));
        if (value != null) {
          slot = i;
          break;
        }
      }
      if (slot < 0) return null;
      var sample = copy.get(slot).copyWithCount(1);
      var rest = sample.getCraftingRemainingItem();
      if (!rest.isEmpty()) {
        var old = copy.get(3);
        if (!old.isEmpty() && !ItemStack.isSameItemSameComponents(old, rest)
            || old.getCount() + rest.getCount() > rest.getMaxStackSize()) return null;
        if (old.isEmpty()) copy.set(3, rest.copy());
        else old.grow(rest.getCount());
      }
      copy.get(slot).shrink(1);
      left = Math.addExact(left, value.joules());
      a = value.acceleration();
      s = value.speed();
    }
    return new FuelPlan(this, left - debit, a, s, List.copyOf(copy), debit);
  }
}
