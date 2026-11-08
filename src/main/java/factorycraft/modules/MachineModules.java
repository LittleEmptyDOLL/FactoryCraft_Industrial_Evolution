package factorycraft.modules;

import factorycraft.blockentity.MachineBlockEntity;
import factorycraft.blocks.FactoryKind;
import factorycraft.simulation.modules.ModuleEffects;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Sole controller-owned module inventory and recipe-specific fractional credit. */
public final class MachineModules {
  public static final TagKey<Item> PRODUCTIVE =
      TagKey.create(
          Registries.ITEM,
          ResourceLocation.fromNamespaceAndPath("factorycraft", "productivity_outputs"));
  private final MachineBlockEntity machine;
  public final ItemStackHandler items;
  private boolean installed;
  private ModuleEffects cachedEffects;
  private int lastBeaconSpeed, lastBeaconEnergy, lastBeacons;
  private int localSpeed, localEnergy, localProductivity, fraction, credit, productiveRemainder;
  private String recipe = "";
  private Object recipeObject;
  private String cachedIdentity = "";

  public String identity(
      net.minecraft.world.item.crafting.RecipeHolder<factorycraft.recipes.FactoryRecipe> holder) {
    if (recipeObject != holder.value()) {
      recipeObject = holder.value();
      var ops =
          net.minecraft.resources.RegistryOps.create(
              com.mojang.serialization.JsonOps.INSTANCE, machine.getLevel().registryAccess());
      cachedIdentity =
          holder.id()
              + "|"
              + factorycraft.recipes.FactoryRecipe.CODEC
                  .codec()
                  .encodeStart(ops, holder.value())
                  .getOrThrow();
    }
    return cachedIdentity;
  }

  public boolean dirty = true;
  private List<factorycraft.blockentity.BeaconBlockEntity> beacons = List.of();

  public MachineModules(MachineBlockEntity machine) {
    this.machine = machine;
    items =
        new ItemStackHandler(
            machine.kind().processing()
                ? (machine.kind() == FactoryKind.REFINERY
                        || machine.kind() == FactoryKind.CHEMICAL_PLANT
                    ? 3
                    : 2)
                : 0) {
          public int getSlotLimit(int slot) {
            return 1;
          }

          public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof ModuleItem;
          }

          protected void onContentsChanged(int slot) {
            recalculate();
            machine.syncPetroleum();
            machine.setChanged();
          }
        };
  }

  private void recalculate() {
    cachedEffects = null;
    localSpeed = localEnergy = localProductivity = 0;
    installed = false;
    for (int s = 0; s < items.getSlots(); s++)
      if (items.getStackInSlot(s).getItem() instanceof ModuleItem item) {
        installed = true;
        localSpeed += item.kind.speed;
        localEnergy += item.kind.energy;
        localProductivity += item.kind.productivity;
      }
  }

  public ModuleEffects effects() {
    if (dirty && machine.getLevel() != null && !machine.getLevel().isClientSide) {
      beacons = BeaconIndex.nearby(machine);
      dirty = false;
    }
    int speed = 0, energy = 0, n = 0;
    for (var beacon : beacons)
      if (beacon.broadcasting()) {
        n++;
        speed += beacon.speedBonus();
        energy += beacon.energyBonus();
      }
    if (!installed && n == 0) return ModuleEffects.NONE;
    if (cachedEffects == null
        || lastBeaconSpeed != speed
        || lastBeaconEnergy != energy
        || lastBeacons != n) {
      lastBeaconSpeed = speed;
      lastBeaconEnergy = energy;
      lastBeacons = n;
      cachedEffects =
          ModuleEffects.combine(localSpeed, localEnergy, localProductivity, speed, energy, n);
    }
    return cachedEffects;
  }

  public boolean enabled() {
    return installed
        || fraction != 0
        || credit != 0
        || productiveRemainder != 0
        || effects() != ModuleEffects.NONE;
  }

  public int fraction() {
    return fraction;
  }

  public int credit() {
    return credit;
  }

  public boolean productive(ItemStack output) {
    return output.is(PRODUCTIVE);
  }

  public int batches(String identity, int rate, int remaining, int duration) {
    boolean same = recipe.equals(identity);
    var earned = ModuleEffects.accrue(rate, remaining, duration, same ? productiveRemainder : 0);
    return ModuleEffects.batches(same ? credit : 0, earned.credit());
  }

  public boolean identityChanged(String identity) {
    return !recipe.isEmpty() && !recipe.equals(identity);
  }

  public void advance(String identity, int remainder, int work, int duration, int rate) {
    if (!recipe.equals(identity)) {
      recipe = identity;
      credit = 0;
      productiveRemainder = 0;
    }
    var earned = ModuleEffects.accrue(rate, work, duration, productiveRemainder);
    credit += earned.credit();
    productiveRemainder = earned.remainder();
    fraction = remainder;
    machine.markPetroleumState();
  }

  public void finish() {
    credit %= ModuleEffects.UNIT;
    machine.markPetroleumState();
  }

  public void save(CompoundTag tag, HolderLookup.Provider registry) {
    if (!installed && fraction == 0 && credit == 0 && productiveRemainder == 0) return;
    var state = new CompoundTag();
    state.putInt("Schema", 1);
    state.put("Items", items.serializeNBT(registry));
    state.putInt("Fraction", fraction);
    state.putInt("Credit", credit);
    state.putInt("ProductiveRemainder", productiveRemainder);
    state.putString("Recipe", recipe);
    tag.put("Modules", state);
  }

  public void load(CompoundTag tag, HolderLookup.Provider registry) {
    var state = tag.getCompound("Modules");
    if (state.contains("Items")) items.deserializeNBT(registry, state.getCompound("Items"));
    fraction = Math.clamp(state.getInt("Fraction"), 0, 9999);
    credit = Math.clamp(state.getInt("Credit"), 0, 19999);
    productiveRemainder = Math.clamp(state.getInt("ProductiveRemainder"), 0, 71999);
    recipe = state.getString("Recipe");
    recalculate();
    dirty = true;
  }
}
