package factorycraft.nuclear;

import factorycraft.blockentity.MachineBlockEntity;
import factorycraft.recipes.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Source bills above a visible input stack accumulate as ordinary saved stacks, never compressed.
 */
public final class ConstructionWork {
  private final MachineBlockEntity machine;
  private boolean inventoryDirty = true, occupied;
  public final ItemStackHandler escrow =
      new ItemStackHandler(32) {
        protected void onContentsChanged(int s) {
          inventoryDirty = true;
        }
      };
  private long cachedEpoch = -1;
  private String cachedDefinition = "";
  private String recipe = "", definition = "";
  private long work, energyRemainder;
  private boolean busy;
  private String cachedId = "";
  private Object manager;
  private FactoryRecipe selected;

  public ConstructionWork(MachineBlockEntity machine) {
    this.machine = machine;
  }

  private FactoryRecipe selected() {
    String id = machine.selectedRecipeId();
    var rm = machine.getLevel().getRecipeManager();
    long epoch = factorycraft.recipes.RecipeSelectionNetwork.generation;
    if (!id.equals(cachedId) || manager != rm || cachedEpoch != epoch) {
      cachedId = id;
      manager = rm;
      cachedEpoch = epoch;
      cachedDefinition = "";
      var key = ResourceLocation.tryParse(id);
      selected =
          key == null
              ? null
              : rm.byKey(key)
                  .map(h -> h.value())
                  .filter(FactoryRecipe.class::isInstance)
                  .map(FactoryRecipe.class::cast)
                  .filter(r -> r.categories().contains("nuclear_construction"))
                  .orElse(null);
      if (selected != null)
        cachedDefinition =
            FactoryRecipe.CODEC
                .codec()
                .encodeStart(
                    net.minecraft.resources.RegistryOps.create(
                        com.mojang.serialization.JsonOps.INSTANCE,
                        machine.getLevel().registryAccess()),
                    selected)
                .getOrThrow()
                .toString();
    }
    return selected;
  }

  public boolean ownsMaterials() {
    if (inventoryDirty) {
      occupied = false;
      for (int s = 0; s < 32; s++)
        if (!escrow.getStackInSlot(s).isEmpty()) {
          occupied = true;
          break;
        }
      inventoryDirty = false;
    }
    return occupied;
  }

  public boolean handles() {
    return machine.kind().assembler() && (ownsMaterials() || work > 0 || selected() != null);
  }

  public boolean tick() {
    if (busy) return false;
    busy = true;
    machine.beginPetroleumInventoryTransaction();
    try {
      return perform();
    } finally {
      machine.endPetroleumInventoryTransaction();
      busy = false;
    }
  }

  private boolean perform() {
    var r = selected();
    if (r == null || !machine.recipeAllowed(ResourceLocation.parse(cachedId))) return false;
    String signature = cachedDefinition;
    if (!recipe.isEmpty() && (!recipe.equals(cachedId) || !definition.equals(signature)))
      return false;
    if (r.inputs().stream().mapToInt(FactoryRecipe.CountedIngredient::count).sum() > 2048)
      return false;
    boolean filled = false;
    int[] assigned = new int[32];
    var inputs = new FactoryInput(machine.items);
    for (var ingredient : r.inputs()) {
      int own = 0;
      for (int s = 0; s < 32 && own < ingredient.count(); s++)
        if (ingredient.ingredient().test(escrow.getStackInSlot(s))) {
          int n =
              Math.min(ingredient.count() - own, escrow.getStackInSlot(s).getCount() - assigned[s]);
          own += n;
          assigned[s] += n;
        }
      int need = ingredient.count() - own;
      for (int s = 0; s < inputs.size() && need > 0; s++) {
        var source = inputs.getItem(s);
        if (!ingredient.ingredient().test(source)) continue;
        var offered = source.copyWithCount(Math.min(need, source.getCount()));
        int before = offered.getCount();
        for (int slot = 0; slot < 32 && !offered.isEmpty(); slot++)
          offered = escrow.insertItem(slot, offered, false);
        int moved = before - offered.getCount();
        if (moved > 0) {
          machine.items.extractItem(FactoryInput.realSlot(s), moved, false);
          need -= moved;
          filled = true;
          recipe = cachedId;
          definition = signature;
        }
      }
      if (need <= 0) {
        // Reserve newly collected units too; overlapping datapack predicates may not claim twice.
        int reserve = ingredient.count() - own;
        for (int s = 0; s < 32 && reserve > 0; s++)
          if (ingredient.ingredient().test(escrow.getStackInSlot(s))) {
            int n = Math.min(reserve, escrow.getStackInSlot(s).getCount() - assigned[s]);
            assigned[s] += n;
            reserve -= n;
          }
      }
      if (need > 0) {
        if (filled) {
          machine.markPetroleumState();
          machine.syncPetroleum();
        }
        return false;
      }
    }
    if (!machine.nuclearOutput(r.result(), true).isEmpty()) return false;
    var effect = machine.modules.effects();
    long raw = energyRemainder + (long) machine.kind().activeFE() * effect.energy();
    int charge = (int) (raw / 10000);
    if (!machine.power.spend(charge)) return false;
    energyRemainder = raw % 10000;
    work += (long) machine.kind().workQuarters() * effect.speed();
    machine.nuclearProgress((int) (work / 40000), r.ticks());
    if (work >= (long) r.ticks() * 40000) {
      for (int slot = 0; slot < 32; slot++) escrow.setStackInSlot(slot, ItemStack.EMPTY);
      if (!machine.nuclearOutput(r.result(), false).isEmpty())
        throw new IllegalStateException("Reserved construction output changed");
      work -= (long) r.ticks() * 40000;
      recipe = "";
      definition = "";
      machine.nuclearProgress(0, r.ticks());
    }
    machine.markPetroleumState();
    if (machine.getLevel().getGameTime() % 20 == 0) machine.syncPetroleum();
    return true;
  }

  public boolean returnMaterials(Player p) {
    if (busy) return false;
    busy = true;
    try {
      for (int s = 0; s < 32; s++) {
        var stack = escrow.getStackInSlot(s).copy();
        if (stack.isEmpty()) continue;
        p.getInventory().add(stack);
        escrow.setStackInSlot(s, stack);
      }
      if (!ownsMaterials()) {
        recipe = definition = "";
        work = energyRemainder = 0;
      }
      machine.markPetroleumState();
      machine.syncPetroleum();
      return true;
    } finally {
      busy = false;
    }
  }

  public void save(CompoundTag n, HolderLookup.Provider r) {
    if (!ownsMaterials() && work == 0) return;
    var t = new CompoundTag();
    t.put("Items", escrow.serializeNBT(r));
    t.putString("Recipe", recipe);
    t.putString("Definition", definition);
    t.putLong("Work", work);
    t.putLong("EnergyRemainder", energyRemainder);
    n.put("Construction", t);
  }

  public void load(CompoundTag n, HolderLookup.Provider r) {
    if (!n.contains("Construction")) return;
    var t = n.getCompound("Construction");
    if (t.getCompound("Items").getInt("Size") != 32)
      throw new IllegalArgumentException("Construction inventory");
    escrow.deserializeNBT(r, t.getCompound("Items"));
    inventoryDirty = true;
    recipe = t.getString("Recipe");
    definition = t.getString("Definition");
    work = t.getLong("Work");
    energyRemainder = t.getLong("EnergyRemainder");
    if (work < 0 || work > 2880000000L || energyRemainder < 0 || energyRemainder >= 10000)
      throw new IllegalArgumentException("Construction work");
  }
}
