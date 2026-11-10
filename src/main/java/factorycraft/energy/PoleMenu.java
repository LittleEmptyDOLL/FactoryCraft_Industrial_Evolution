package factorycraft.energy;

import factorycraft.blockentity.PoleBlockEntity;
import factorycraft.registry.Content;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Readonly live status. No invented generation rating or consumption estimate. */
public final class PoleMenu extends AbstractContainerMenu {
  public final PoleBlockEntity pole;
  public final ContainerData data;

  public PoleMenu(int id, Inventory inv, RegistryFriendlyByteBuf b) {
    this(
        id,
        inv,
        (PoleBlockEntity) inv.player.level().getBlockEntity(b.readBlockPos()),
        new SimpleContainerData(6));
  }

  public PoleMenu(int id, Inventory inv, PoleBlockEntity pole, ContainerData data) {
    super(Content.POLE_MENU.get(), id);
    this.pole = pole;
    this.data = data;
    addDataSlots(data);
  }

  public boolean stillValid(Player p) {
    return factorycraft.multiblock.MachineInteraction.inRange(pole, p);
  }

  public ItemStack quickMoveStack(Player p, int slot) {
    return ItemStack.EMPTY;
  }
}
