package factorycraft.combat;

import factorycraft.multiblock.MachineInteraction;
import factorycraft.registry.Content;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class TurretMenu extends AbstractContainerMenu {
  public final TurretBlockEntity turret;
  public final ContainerData data;

  public TurretMenu(int id, Inventory inv, RegistryFriendlyByteBuf b) {
    this(
        id,
        inv,
        (TurretBlockEntity) inv.player.level().getBlockEntity(b.readBlockPos()),
        new SimpleContainerData(7));
  }

  public TurretMenu(int id, Inventory inv, TurretBlockEntity be) {
    this(
        id,
        inv,
        be,
        new ContainerData() {
          public int getCount() {
            return 7;
          }

          public void set(int i, int v) {}

          public int get(int i) {
            return switch (i) {
              case 0 -> be.power.getEnergyStored();
              case 1 -> be.oil.amount();
              case 2 -> MagazineItem.rounds(be.items.getStackInSlot(1));
              case 3 -> be.laser() ? 1 : be.flame() ? 2 : 0;
              case 5 -> be.pipeOil.amount();
              case 6 -> be.status().ordinal();
              default -> be.ready() ? 1 : 0;
            };
          }
        });
  }

  private TurretMenu(int id, Inventory inv, TurretBlockEntity be, ContainerData data) {
    super(Content.TURRET_MENU.get(), id);
    turret = be;
    this.data = data;
    addDataSlots(data);
    addSlot(
        new SlotItemHandler(be.items, 0, 62, 45) {
          public boolean isActive() {
            return !turret.laser() && !turret.flame();
          }
        });
    addSlot(
        new SlotItemHandler(be.items, 1, 98, 45) {
          public boolean isActive() {
            return !turret.laser() && !turret.flame();
          }

          public boolean mayPlace(ItemStack s) {
            return false;
          }
        });
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 112 + row * 18));
    for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 170));
  }

  public boolean stillValid(Player p) {
    return turret.loaded() && MachineInteraction.inRange(turret, p);
  }

  public void clicked(int s, int b, ClickType t, Player p) {
    if (p.level().isClientSide || p.containerMenu == this && stillValid(p))
      super.clicked(s, b, t, p);
  }

  public ItemStack quickMoveStack(Player p, int index) {
    if (p.containerMenu != this || !stillValid(p) || index < 0 || index >= slots.size())
      return ItemStack.EMPTY;
    var slot = slots.get(index);
    var s = slot.getItem();
    var copy = s.copy();
    if (!moveItemStackTo(s, index < 2 ? 2 : 0, index < 2 ? slots.size() : 1, false))
      return ItemStack.EMPTY;
    if (s.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
    else slot.setChanged();
    return copy;
  }
}
