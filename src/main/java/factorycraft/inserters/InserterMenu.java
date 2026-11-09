package factorycraft.inserters;

import factorycraft.blockentity.InserterBlockEntity;
import factorycraft.blocks.FactoryKind;
import factorycraft.registry.Content;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Both preview slots own no items. The server owns the arm's sole persistent hand. */
public final class InserterMenu extends AbstractContainerMenu {
  public final InserterBlockEntity owner;
  public final ContainerData data;
  private final SimpleContainer preview = new SimpleContainer(2);

  public InserterMenu(int id, Inventory inv, RegistryFriendlyByteBuf b) {
    this(
        id,
        inv,
        (InserterBlockEntity) inv.player.level().getBlockEntity(b.readBlockPos()),
        new SimpleContainerData(5));
  }

  public InserterMenu(int id, Inventory inv, InserterBlockEntity arm) {
    this(
        id,
        inv,
        arm,
        new ContainerData() {
          public int get(int i) {
            return switch (i) {
              case 0 -> arm.enabled() ? 1 : 0;
              case 1 -> arm.blacklist() ? 1 : 0;
              case 2 -> arm.power.getEnergyStored();
              case 3 -> arm.reach();
              case 4 -> arm.kind().armSpec().ticksPerCycle();
              default -> 0;
            };
          }

          public void set(int i, int v) {}

          public int getCount() {
            return 5;
          }
        });
  }

  private InserterMenu(int id, Inventory inv, InserterBlockEntity arm, ContainerData data) {
    super(Content.INSERTER_MENU.get(), id);
    owner = arm;
    this.data = data;
    addDataSlots(data);
    for (int i = 0; i < 2; i++) {
      final int n = i;
      addSlot(
          new Slot(preview, i, 79 + i * 36, 60) {
            public boolean mayPlace(ItemStack s) {
              return false;
            }

            public boolean mayPickup(Player p) {
              return false;
            }

            public int getMaxStackSize() {
              return 0;
            }

            public boolean isActive() {
              return n == 1 || filterable();
            }
          });
    }
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(inv, col + row * 9 + 9, 8 + 18 * col, 114 + 18 * row));
    for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + 18 * col, 172));
    refresh(inv.player);
  }

  public boolean filterable() {
    return owner != null && owner.kind() == FactoryKind.FILTER_INSERTER;
  }

  private void refresh(Player p) {
    if (!p.level().isClientSide && owner != null) {
      preview.setItem(0, owner.filterIcon());
      preview.setItem(1, owner.held().copy());
    }
  }

  @Override
  public void broadcastChanges() {
    if (owner != null && !owner.getLevel().isClientSide) {
      preview.setItem(0, owner.filterIcon());
      preview.setItem(1, owner.held().copy());
    }
    super.broadcastChanges();
  }

  @Override
  public boolean stillValid(Player p) {
    return p.level().isClientSide
        || owner != null
            && p.level() == owner.getLevel()
            && owner.electric()
            && owner.ready()
            && p.distanceToSqr(owner.getBlockPos().getCenter()) <= 64;
  }

  private boolean allowed(Player p) {
    return !p.isSpectator() && p.containerMenu == this && stillValid(p);
  }

  @Override
  public void clicked(int slot, int button, ClickType type, Player p) {
    if (!p.level().isClientSide && !allowed(p)) return;
    if (slot == 0 || slot == 1) {
      if (slot == 0
          && filterable()
          && !p.level().isClientSide
          && type == ClickType.PICKUP
          && (button == 0 || button == 1)) {
        var held = getCarried();
        owner.configure(
            owner.enabled(),
            owner.blacklist(),
            button == 1 || held.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(held.getItem()));
        broadcastChanges();
      }
      return;
    }
    super.clicked(slot, button, type, p);
  }

  @Override
  public boolean clickMenuButton(Player p, int button) {
    if (!allowed(p) || button < 0 || button > 2 || button != 0 && !filterable()) return false;
    boolean changed =
        owner.configure(
            button == 0 ? !owner.enabled() : owner.enabled(),
            button == 1 ? !owner.blacklist() : owner.blacklist(),
            button == 2 ? null : owner.filter());
    if (changed) broadcastChanges();
    return changed;
  }

  @Override
  public ItemStack quickMoveStack(Player p, int slot) {
    return ItemStack.EMPTY;
  }

  @Override
  public boolean canTakeItemForPickAll(ItemStack s, Slot slot) {
    return slot.index >= 2 && super.canTakeItemForPickAll(s, slot);
  }

  public static boolean open(InserterBlockEntity arm, Player p) {
    if (p.level().isClientSide
        || p.isSpectator()
        || !arm.electric()
        || !arm.ready()
        || p.distanceToSqr(arm.getBlockPos().getCenter()) > 64) return false;
    p.openMenu(
        new SimpleMenuProvider(
            (id, inv, player) -> new InserterMenu(id, inv, arm),
            Component.translatable("gui.factorycraft.inserter")),
        arm.getBlockPos());
    return true;
  }
}
