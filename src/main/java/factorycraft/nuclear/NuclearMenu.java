package factorycraft.nuclear;

import factorycraft.multiblock.MachineInteraction;
import factorycraft.registry.Content;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/** Metrics split into unsigned halves; native container packets otherwise truncate above 32767. */
public final class NuclearMenu extends AbstractContainerMenu {
  public final NuclearOwner owner;
  private final ContainerData data;
  private final int machineSlots;

  public NuclearMenu(int id, Inventory inv, RegistryFriendlyByteBuf b) {
    this(id, inv, (NuclearOwner) inv.player.level().getBlockEntity(b.readBlockPos()), null);
  }

  public NuclearMenu(int id, Inventory inv, NuclearOwner owner) {
    this(id, inv, owner, null);
  }

  private NuclearMenu(int id, Inventory inv, NuclearOwner owner, ContainerData ignored) {
    super(Content.NUCLEAR_MENU.get(), id);
    this.owner = owner;
    data =
        inv.player.level().isClientSide
            ? new SimpleContainerData(16)
            : new ContainerData() {
              public int getCount() {
                return 16;
              }

              public int get(int i) {
                return (owner.metrics()[i / 2] >>> (16 * (i % 2))) & 65535;
              }

              public void set(int i, int v) {}
            };
    addDataSlots(data);
    machineSlots = owner instanceof ReactorBlockEntity ? 2 : 0;
    if (machineSlots > 0) {
      var reactor = (ReactorBlockEntity) owner;
      addSlot(new SlotItemHandler(reactor.items, 0, 60, 90));
      addSlot(
          new SlotItemHandler(reactor.items, 1, 98, 90) {
            public boolean mayPlace(ItemStack s) {
              return false;
            }
          });
    }
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(inv, 9 + row * 9 + col, 8 + col * 18, 136 + row * 18));
    for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 194));
  }

  public long metric(int i) {
    return Integer.toUnsignedLong(
        (data.get(i * 2) & 65535) | ((data.get(i * 2 + 1) & 65535) << 16));
  }

  public boolean stillValid(Player p) {
    return !p.isSpectator()
        && p.level() == owner.getLevel()
        && owner.loaded()
        && MachineInteraction.inRange(owner, p);
  }

  public void clicked(int s, int b, ClickType t, Player p) {
    if (p.level().isClientSide || p.containerMenu == this && stillValid(p))
      super.clicked(s, b, t, p);
  }

  public ItemStack quickMoveStack(Player p, int index) {
    if (p.containerMenu != this || !stillValid(p) || index < 0 || index >= slots.size())
      return ItemStack.EMPTY;
    var slot = slots.get(index);
    var stack = slot.getItem();
    var copy = stack.copy();
    if (machineSlots == 0
        || !moveItemStackTo(
            stack,
            index < machineSlots ? machineSlots : 0,
            index < machineSlots ? slots.size() : 1,
            false)) return ItemStack.EMPTY;
    if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
    else slot.setChanged();
    return copy;
  }
}
