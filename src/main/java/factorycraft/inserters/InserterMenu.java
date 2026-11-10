package factorycraft.inserters;

import factorycraft.blockentity.InserterBlockEntity;
import factorycraft.registry.Content;
import factorycraft.simulation.inserters.ItemFilters;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Five ghost filters and a non-extractable counted hand; only fuel/recovery slots own menu items.
 */
public final class InserterMenu extends AbstractContainerMenu {
  public final InserterBlockEntity owner;
  public final ContainerData data;
  private final SimpleContainer previews = new SimpleContainer(6);

  public InserterMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
    this(
        id,
        inventory,
        (InserterBlockEntity) inventory.player.level().getBlockEntity(buffer.readBlockPos()),
        new SimpleContainerData(15));
  }

  public InserterMenu(int id, Inventory inventory, InserterBlockEntity arm) {
    this(
        id,
        inventory,
        arm,
        new ContainerData() {
          public int getCount() {
            return 15;
          }

          public void set(int i, int n) {}

          public int get(int i) {
            return switch (i) {
              case 0 -> arm.enabled() ? 1 : 0;
              case 1 -> arm.blacklist() ? 1 : 0;
              case 2 -> arm.power.getEnergyStored();
              case 3 -> arm.reach();
              case 4 -> arm.kind().armSpec().units();
              case 5 -> arm.manualLimit();
              case 6 -> arm.researchedMaximum();
              case 7 -> unknownFilters(arm);
              case 8, 9, 10, 11 -> (int) (arm.fuelEnergy() >> ((i - 8) * 16)) & 65535;
              case 12 -> arm.status();
              case 13 -> arm.stage();
              case 14 -> arm.beltStackHeight();
              default -> 0;
            };
          }
        });
  }

  private InserterMenu(int id, Inventory inventory, InserterBlockEntity arm, ContainerData data) {
    super(Content.INSERTER_MENU.get(), id);
    owner = arm;
    this.data = data;
    addDataSlots(data);
    for (int i = 0; i < 6; i++)
      addSlot(
          new Slot(previews, i, i < 5 ? 8 + i * 18 : 144, 54) {
            public boolean mayPlace(ItemStack stack) {
              return false;
            }

            public boolean mayPickup(Player player) {
              return false;
            }

            public int getMaxStackSize() {
              return 0;
            }
          });
    addSlot(
        new SlotItemHandler(arm.fuel, 0, 8, 91) {
          public boolean isActive() {
            return owner.burner();
          }
        });
    addSlot(
        new SlotItemHandler(arm.fuel, 1, 144, 91) {
          public boolean mayPlace(ItemStack stack) {
            return false;
          }
        });
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(inventory, col + row * 9 + 9, 8 + 18 * col, 151 + 18 * row));
    for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + 18 * col, 209));
    refresh();
  }

  private static int unknownFilters(InserterBlockEntity arm) {
    int bits = 0;
    for (int i = 0; i < 5; i++)
      if (!arm.filterIds().get(i).isEmpty() && arm.filterIcon(i).isEmpty()) bits |= 1 << i;
    return bits;
  }

  public boolean filterable() {
    return true;
  }

  private void refresh() {
    if (owner != null && !owner.getLevel().isClientSide) {
      for (int i = 0; i < 5; i++) previews.setItem(i, owner.filterIcon(i));
      previews.setItem(5, owner.held().copy());
    }
  }

  public void broadcastChanges() {
    refresh();
    super.broadcastChanges();
  }

  public boolean stillValid(Player player) {
    return player.level().isClientSide
        || owner != null
            && player.level() == owner.getLevel()
            && owner.ready()
            && player.distanceToSqr(owner.getBlockPos().getCenter()) <= 64
            && player.level().mayInteract(player, owner.getBlockPos());
  }

  private boolean allowed(Player player) {
    return !player.isSpectator()
        && player.getAbilities().mayBuild
        && player.containerMenu == this
        && stillValid(player);
  }

  public void clicked(int slot, int button, ClickType type, Player player) {
    if (!player.level().isClientSide && !allowed(player)) return;
    if (slot >= 0 && slot < 6) {
      if (slot < 5
          && !player.level().isClientSide
          && type == ClickType.PICKUP
          && (button == 0 || button == 1)) {
        var sample = getCarried();
        owner.setFilter(
            slot,
            button == 1 || sample.isEmpty()
                ? null
                : BuiltInRegistries.ITEM.getKey(sample.getItem()));
        broadcastChanges();
      }
      return;
    }
    super.clicked(slot, button, type, player);
  }

  public boolean clickMenuButton(Player player, int id) {
    if (!allowed(player) || id < 0 || id > 5) return false;
    int maximum = owner.researchedMaximum(), limit = Math.min(owner.manualLimit(), maximum);
    if (id == 3) limit = Math.max(1, (limit == 0 ? maximum : limit) - 1);
    if (id == 4) limit = Math.min(maximum, limit + 1);
    if (id == 5) limit = 0;
    var ids = id == 2 ? Collections.nCopies(5, "") : owner.filterIds();
    boolean changed =
        owner.configure(
            id == 0 ? !owner.enabled() : owner.enabled(),
            new ItemFilters(ids, id == 1 ? !owner.blacklist() : owner.blacklist()),
            limit);
    if (changed) broadcastChanges();
    return changed;
  }

  public ItemStack quickMoveStack(Player player, int slot) {
    if (!allowed(player) || slot < 6 || slot >= slots.size()) return ItemStack.EMPTY;
    var source = slots.get(slot);
    if (!source.hasItem()) return ItemStack.EMPTY;
    var original = source.getItem().copy();
    var moving = source.getItem();
    if (slot < 8) {
      if (!moveItemStackTo(moving, 8, slots.size(), true)) return ItemStack.EMPTY;
    } else if (!owner.burner() || !moveItemStackTo(moving, 6, 7, false)) return ItemStack.EMPTY;
    source.setChanged();
    if (moving.isEmpty()) source.setByPlayer(ItemStack.EMPTY);
    return original;
  }

  public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
    return slot.index >= 6 && super.canTakeItemForPickAll(stack, slot);
  }

  public static boolean open(InserterBlockEntity arm, Player player) {
    if (player.level().isClientSide
        || player.isSpectator()
        || !arm.ready()
        || player.distanceToSqr(arm.getBlockPos().getCenter()) > 64
        || !player.level().mayInteract(player, arm.getBlockPos())) return false;
    player.openMenu(
        new SimpleMenuProvider(
            (id, inventory, p) -> new InserterMenu(id, inventory, arm),
            arm.getBlockState().getBlock().getName()),
        arm.getBlockPos());
    return true;
  }
}
