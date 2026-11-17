package factorycraft.nuclear;

import factorycraft.blockentity.MachineBlockEntity;
import factorycraft.simulation.nuclear.UraniumRoll;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Escrowed ore, single sampled outcome and productive paid work survive every pause/portable save.
 */
public final class UraniumWork {
  private final MachineBlockEntity machine;
  public final UraniumRoll roll = new UraniumRoll(UUID.randomUUID().getLeastSignificantBits());
  public ItemStack escrow = ItemStack.EMPTY;
  private long work, productiveRemainder, energyRemainder;
  private int credit;
  private String definition = "";
  private boolean running;

  public UraniumWork(MachineBlockEntity machine) {
    this.machine = machine;
  }

  private ItemStack isotope(int index, int n) {
    return NuclearItems.stack(index == 0 ? "uranium_235" : "uranium_238", n);
  }

  private boolean room(int slot, ItemStack result) {
    var existing = machine.items.getStackInSlot(slot);
    return existing.isEmpty()
        || ItemStack.isSameItemSameComponents(existing, result)
            && existing.getCount() + result.getCount() <= existing.getMaxStackSize();
  }

  private void put(int slot, ItemStack result) {
    var old = machine.items.getStackInSlot(slot);
    machine.items.setStackInSlot(
        slot, old.isEmpty() ? result : old.copyWithCount(old.getCount() + result.getCount()));
  }

  public boolean tick() {
    if (running) return false;
    running = true;
    try {
      return perform();
    } finally {
      running = false;
    }
  }

  private boolean perform() {
    var level = machine.getLevel();
    var id = ResourceLocation.fromNamespaceAndPath("factorycraft", "uranium_processing");
    if (!machine.recipeAllowed(id)) return false;
    var recipe =
        level
            .getRecipeManager()
            .byKey(id)
            .map(h -> h.value())
            .filter(UraniumRecipe.class::isInstance)
            .map(UraniumRecipe.class::cast)
            .orElse(null);
    if (recipe == null) return false;
    String key = recipe.toString();
    if (!definition.isEmpty() && !definition.equals(key))
      return false; // Paid work must not adopt another cost.
    var effect = machine.modules.effects();
    int remaining = recipe.ticks() * 10000 - (int) work;
    int step = Math.min(effect.speed(), remaining);
    long productive = productiveRemainder + (long) effect.productivity() * step;
    long finalCredit =
        credit
            + ((long) effect.productivity() * remaining + productiveRemainder)
                / (recipe.ticks() * 10000L);
    int reserve = 1 + (int) (finalCredit / 10000);
    for (int type = 0; type < 2; type++) {
      var result = NuclearItems.stack(type == 0 ? "uranium_235" : "uranium_238", reserve);
      var slot = machine.items.getStackInSlot(type + 4);
      if (!slot.isEmpty()
          && (!ItemStack.isSameItemSameComponents(slot, result)
              || slot.getCount() + reserve > slot.getMaxStackSize())) return false;
    }
    int[] takes = new int[4];
    ItemStack sample = ItemStack.EMPTY;
    if (escrow.isEmpty()) {
      int need = recipe.ore();
      for (int s = 0; s < 4 && need > 0; s++) {
        var stack = machine.items.getStackInSlot(s);
        if (!NuclearItems.is(stack, "uranium_ore")) continue;
        if (sample.isEmpty()) sample = stack.copy();
        if (!ItemStack.isSameItemSameComponents(sample, stack)) continue;
        takes[s] = Math.min(need, stack.getCount());
        need -= takes[s];
      }
      if (need > 0) return false;
    }
    long raw = energyRemainder + (long) recipe.energyNumerator() * effect.energy();
    long denominator = (long) recipe.energyDenominator() * 10000;
    int cost = (int) (raw / denominator);
    if (machine.power.getEnergyStored() < cost) return false;
    machine.beginPetroleumInventoryTransaction();
    try {
      if (!machine.power.spend(cost)) return false;
      energyRemainder = raw % denominator;
      if (escrow.isEmpty()) {
        escrow = sample.copyWithCount(recipe.ore());
        definition = key;
        roll.beginPaidWork();
        for (int slot = 0; slot < 4; slot++)
          if (takes[slot] > 0) machine.items.extractItem(slot, takes[slot], false);
      }
      work += step;
      credit += (int) (productive / (recipe.ticks() * 10000L));
      productiveRemainder = productive % (recipe.ticks() * 10000L);
      machine.nuclearProgress((int) (work / 10000), recipe.ticks());
      if (work >= recipe.ticks() * 10000L) {
        int outcome = roll.pending();
        put(outcome == 0 ? 4 : 5, isotope(outcome, 1));
        roll.completed();
        while (credit >= 10000) {
          int bonus = roll.beginPaidWork();
          put(bonus == 0 ? 4 : 5, isotope(bonus, 1));
          roll.completed();
          credit -= 10000;
        }
        escrow = ItemStack.EMPTY;
        work = 0;
        definition = "";
        machine.nuclearProgress(0, recipe.ticks());
      }
      machine.markPetroleumState();
      return true;
    } finally {
      machine.endPetroleumInventoryTransaction();
    }
  }

  public void save(CompoundTag n, HolderLookup.Provider r) {
    var t = new CompoundTag();
    t.putLong("Seed", roll.seed());
    t.putLong("Serial", roll.serial());
    t.putInt("Pending", roll.pending());
    if (!escrow.isEmpty()) t.put("Escrow", escrow.save(r));
    t.putLong("Work", work);
    t.putInt("Credit", credit);
    t.putLong("ProductiveRemainder", productiveRemainder);
    t.putLong("EnergyRemainder", energyRemainder);
    t.putString("Definition", definition);
    n.put("UraniumWork", t);
  }

  public void load(CompoundTag n, HolderLookup.Provider r) {
    if (!n.contains("UraniumWork")) return;
    var t = n.getCompound("UraniumWork");
    roll.restore(t.getLong("Seed"), t.getLong("Serial"), t.getInt("Pending"));
    escrow = ItemStack.parseOptional(r, t.getCompound("Escrow"));
    work = t.getLong("Work");
    credit = t.getInt("Credit");
    productiveRemainder = t.getLong("ProductiveRemainder");
    energyRemainder = t.getLong("EnergyRemainder");
    definition = t.getString("Definition");
    if (work < 0
        || work > 720000000
        || credit < 0
        || credit >= 20000
        || energyRemainder < 0
        || energyRemainder > 10000000
        || productiveRemainder < 0
        || productiveRemainder >= 720000000
        || !escrow.isEmpty() && (!NuclearItems.is(escrow, "uranium_ore") || escrow.getCount() > 64)
        || !escrow.isEmpty() && roll.pending() < 0
        || escrow.isEmpty() && roll.pending() >= 0)
      throw new IllegalArgumentException("Uranium work ownership");
  }
}
