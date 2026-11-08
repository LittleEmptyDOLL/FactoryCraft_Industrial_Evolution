package factorycraft.belts;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/** Cached capabilities resolve their live physical endpoint on every operation. */
public final class GuardedTransportPort implements IItemHandler {
  private final Supplier<IItemHandler> current;

  public GuardedTransportPort(Supplier<IItemHandler> current) {
    this.current = current;
  }

  private IItemHandler port(int slot) {
    Objects.checkIndex(slot, 8);
    return current.get();
  }

  public int getSlots() {
    return 8;
  }

  public ItemStack getStackInSlot(int slot) {
    var p = port(slot);
    return p == null ? ItemStack.EMPTY : p.getStackInSlot(slot);
  }

  public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
    var p = port(slot);
    return p == null ? stack : p.insertItem(slot, stack, simulate);
  }

  public ItemStack extractItem(int slot, int amount, boolean simulate) {
    var p = port(slot);
    return p == null ? ItemStack.EMPTY : p.extractItem(slot, amount, simulate);
  }

  public int getSlotLimit(int slot) {
    port(slot);
    return 1;
  }

  public boolean isItemValid(int slot, ItemStack stack) {
    var p = port(slot);
    return p != null && p.isItemValid(slot, stack);
  }
}
