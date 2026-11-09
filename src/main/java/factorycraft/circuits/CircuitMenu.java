package factorycraft.circuits;

import factorycraft.registry.Content;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class CircuitMenu extends AbstractContainerMenu {
  public final CircuitNodeBlockEntity owner;
  public final ContainerData data;
  private final SimpleContainer ghosts = new SimpleContainer(2);

  public CircuitMenu(int id, Inventory inv, RegistryFriendlyByteBuf b) {
    this(
        id,
        inv,
        (CircuitNodeBlockEntity) inv.player.level().getBlockEntity(b.readBlockPos()),
        new SimpleContainerData(9));
  }

  public CircuitMenu(int id, Inventory inv, CircuitNodeBlockEntity n) {
    this(
        id,
        inv,
        n,
        new ContainerData() {
          public int get(int i) {
            return switch (i) {
              case 0 -> n.program().operation();
              case 1 -> n.program().operand() & 65535;
              case 2 -> n.program().operand() >>> 16;
              case 3 -> n.program().outputCount() ? 1 : 0;
              case 4 -> n.inputValue & 65535;
              case 5 -> n.inputValue >>> 16;
              case 6 -> n.lastOutput & 65535;
              case 7 -> n.lastOutput >>> 16;
              case 8 -> n.gate ? 1 : 0;
              default -> 0;
            };
          }

          public void set(int i, int v) {}

          public int getCount() {
            return 9;
          }
        });
  }

  private CircuitMenu(int id, Inventory inv, CircuitNodeBlockEntity n, ContainerData data) {
    super(Content.CIRCUIT_MENU.get(), id);
    owner = n;
    this.data = data;
    addDataSlots(data);
    for (int i = 0; i < 2; i++) {
      final int index = i;
      addSlot(
          new Slot(ghosts, i, 24 + i * 112, 42) {
            public boolean isActive() {
              if (owner == null) return true;
              return index == 0
                  ? owner.kind() != factorycraft.blocks.FactoryKind.CONSTANT
                  : owner.kind() != factorycraft.blocks.FactoryKind.CIRCUIT_CONTROLLER;
            }

            public boolean mayPlace(ItemStack s) {
              return false;
            }

            public boolean mayPickup(Player p) {
              return false;
            }

            public int getMaxStackSize() {
              return 0;
            }
          });
    }
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(inv, 9 + row * 9 + col, 8 + 18 * col, 154 + 18 * row));
    for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + 18 * col, 212));
    refresh(inv.player);
  }

  private void refresh(Player p) {
    if (!p.level().isClientSide && owner != null) {
      ghosts.setItem(0, CircuitProgram.icon(owner.program().input()));
      ghosts.setItem(1, CircuitProgram.icon(owner.program().output()));
    }
  }

  public int operand() {
    return data.get(1) & 65535 | (data.get(2) & 65535) << 16;
  }

  public int input() {
    return data.get(4) & 65535 | (data.get(5) & 65535) << 16;
  }

  public int output() {
    return data.get(6) & 65535 | (data.get(7) & 65535) << 16;
  }

  @Override
  public boolean stillValid(Player p) {
    return p.level().isClientSide
        || owner != null
            && p.level() == owner.getLevel()
            && owner.available()
            && p.distanceToSqr(owner.getBlockPos().getCenter()) <= 64;
  }

  public boolean allowed(Player p) {
    return !p.isSpectator() && p.containerMenu == this && stillValid(p);
  }

  @Override
  public void broadcastChanges() {
    if (owner != null && !owner.getLevel().isClientSide) {
      ghosts.setItem(0, CircuitProgram.icon(owner.program().input()));
      ghosts.setItem(1, CircuitProgram.icon(owner.program().output()));
    }
    super.broadcastChanges();
  }

  @Override
  public void clicked(int slot, int button, ClickType type, Player p) {
    if (!p.level().isClientSide && !allowed(p)) return;
    if (slot == 0 || slot == 1) {
      if (!slots.get(slot).isActive()) return;
      if (!p.level().isClientSide && type == ClickType.PICKUP && (button == 0 || button == 1)) {
        var old = owner.program();
        var sample = getCarried();
        var signal =
            button == 1 || sample.isEmpty()
                ? CircuitProgram.A
                : BuiltInRegistries.ITEM.getKey(sample.getItem());
        owner.configure(
            new CircuitProgram(
                slot == 0 ? signal : old.input(),
                slot == 1 ? signal : old.output(),
                old.operation(),
                old.operand(),
                old.outputCount()));
        broadcastChanges();
      }
      return;
    }
    super.clicked(slot, button, type, p);
  }

  @Override
  public boolean clickMenuButton(Player p, int b) {
    if (!allowed(p) || b < 0 || b > 1) return false;
    var old = owner.program();
    boolean ok =
        owner.configure(
            new CircuitProgram(
                old.input(),
                old.output(),
                b == 0
                    ? (old.operation() + 1)
                        % (owner.kind() == factorycraft.blocks.FactoryKind.ARITHMETIC ? 10 : 6)
                    : old.operation(),
                old.operand(),
                b == 1 ? !old.outputCount() : old.outputCount()));
    if (ok) broadcastChanges();
    return ok;
  }

  public boolean operand(Player p, int value) {
    if (!allowed(p)) return false;
    var old = owner.program();
    boolean ok =
        owner.configure(
            new CircuitProgram(
                old.input(), old.output(), old.operation(), value, old.outputCount()));
    if (ok) broadcastChanges();
    return ok;
  }

  @Override
  public ItemStack quickMoveStack(Player p, int s) {
    return ItemStack.EMPTY;
  }

  @Override
  public boolean canTakeItemForPickAll(ItemStack s, Slot slot) {
    return slot.index >= 2 && super.canTakeItemForPickAll(s, slot);
  }
}
