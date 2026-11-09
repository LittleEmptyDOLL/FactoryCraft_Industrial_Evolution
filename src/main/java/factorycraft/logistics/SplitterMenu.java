package factorycraft.logistics;

import factorycraft.blockentity.BeltBlockEntity;
import factorycraft.fluids.FluidConnections;
import factorycraft.registry.Content;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Server-owned settings. The preview slot never owns or dispenses an item. */
public final class SplitterMenu extends AbstractContainerMenu {
  public final BeltBlockEntity owner;
  public final ContainerData data;
  private final SimpleContainer ghost = new SimpleContainer(1);

  public SplitterMenu(int id, Inventory inv, RegistryFriendlyByteBuf buffer) {
    this(
        id,
        inv,
        (BeltBlockEntity) inv.player.level().getBlockEntity(buffer.readBlockPos()),
        new SimpleContainerData(3));
  }

  public SplitterMenu(int id, Inventory inv, BeltBlockEntity owner) {
    this(
        id,
        inv,
        owner,
        new ContainerData() {
          public int get(int i) {
            return switch (i) {
              case 0 -> owner.splitterSettings().input();
              case 1 -> owner.splitterSettings().output();
              case 2 -> owner.splitterSettings().filter() == null ? 0 : 1;
              default -> 0;
            };
          }

          public void set(int i, int value) {}

          public int getCount() {
            return 3;
          }
        });
  }

  private SplitterMenu(int id, Inventory inv, BeltBlockEntity owner, ContainerData data) {
    super(Content.SPLITTER_MENU.get(), id);
    this.owner = owner;
    this.data = data;
    addDataSlots(data);
    addSlot(
        new Slot(ghost, 0, 79, 83) {
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
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 146 + row * 18));
    for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 204));
    if (!inv.player.level().isClientSide) ghost.setItem(0, owner.splitterSettings().icon());
  }

  public boolean stillValid(Player player) {
    return player.level().isClientSide
        || owner != null
            && player.level() == owner.getLevel()
            && owner.kind().splitter()
            && owner.loaded()
            && owner.structureReady()
            && FluidConnections.canonical(owner)
            && player.distanceToSqr(owner.getBlockPos().getCenter()) <= 64;
  }

  private boolean permitted(Player player) {
    return !player.isSpectator() && player.containerMenu == this && stillValid(player);
  }

  @Override
  public void broadcastChanges() {
    if (owner != null && !owner.getLevel().isClientSide)
      ghost.setItem(0, owner.splitterSettings().icon());
    super.broadcastChanges();
  }

  @Override
  public void clicked(int slot, int button, ClickType type, Player player) {
    if (!player.level().isClientSide && !permitted(player)) return;
    if (slot == 0) {
      if (!player.level().isClientSide
          && type == ClickType.PICKUP
          && (button == 0 || button == 1)) {
        var current = owner.splitterSettings();
        var carried = getCarried();
        owner.configureSplitter(
            new SplitterSettings(
                current.input(),
                current.output(),
                button == 1 || carried.isEmpty()
                    ? null
                    : BuiltInRegistries.ITEM.getKey(carried.getItem())));
        broadcastChanges();
      }
      return;
    }
    super.clicked(slot, button, type, player);
  }

  @Override
  public boolean clickMenuButton(Player player, int button) {
    if (!permitted(player) || button < 0 || button > 2) return false;
    var old = owner.splitterSettings();
    var next =
        switch (button) {
          case 0 -> new SplitterSettings((old.input() + 1) % 3, old.output(), old.filter());
          case 1 ->
              new SplitterSettings(
                  old.input(),
                  old.filter() != null ? old.output() == 1 ? 2 : 1 : (old.output() + 1) % 3,
                  old.filter());
          default -> new SplitterSettings(old.input(), old.output(), null);
        };
    boolean changed = owner.configureSplitter(next);
    if (changed) broadcastChanges();
    return changed;
  }

  @Override
  public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
    return slot.index != 0 && super.canTakeItemForPickAll(stack, slot);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int slot) {
    return ItemStack.EMPTY;
  }

  public static boolean open(BeltBlockEntity belt, Player player) {
    if (player.level().isClientSide
        || player.isSpectator()
        || !belt.kind().splitter()
        || !belt.loaded()
        || !belt.structureReady()
        || !FluidConnections.canonical(belt)
        || player.distanceToSqr(belt.getBlockPos().getCenter()) > 64) return false;
    player.openMenu(
        new SimpleMenuProvider(
            (id, inv, p) -> new SplitterMenu(id, inv, belt),
            Component.translatable("gui.factorycraft.splitter")),
        belt.getBlockPos());
    return true;
  }
}
