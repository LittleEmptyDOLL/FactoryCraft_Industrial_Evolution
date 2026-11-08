package factorycraft.modules;

import factorycraft.blockentity.*;
import factorycraft.fluids.FluidConnections;
import factorycraft.multiblock.AssemblyAccess;
import factorycraft.registry.Content;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.*;

public final class ModuleMenu extends AbstractContainerMenu {
  public final FactoryBlockEntity owner;
  public final ItemStackHandler modules;
  public final ContainerData data;

  public ModuleMenu(int id, Inventory inv, RegistryFriendlyByteBuf buffer) {
    this(
        id,
        inv,
        (FactoryBlockEntity) inv.player.level().getBlockEntity(buffer.readBlockPos()),
        new SimpleContainerData(5));
  }

  public ModuleMenu(int id, Inventory inv, FactoryBlockEntity owner) {
    this(
        id,
        inv,
        owner,
        new ContainerData() {
          public int get(int i) {
            if (owner instanceof MachineBlockEntity m) {
              var effects = m.modules.effects();
              return switch (i) {
                case 0 -> m.power.getEnergyStored();
                case 1 -> effects.speed() / 100;
                case 2 -> effects.energy() / 100;
                case 3 -> effects.productivity() / 100;
                case 4 -> m.modules.credit();
                default -> 0;
              };
            }
            var b = (BeaconBlockEntity) owner;
            return switch (i) {
              case 0 -> b.power.getEnergyStored();
              case 1 -> 100 + b.speedBonus() / 100;
              case 2 -> 100 + b.energyBonus() / 100;
              case 3 -> b.broadcasting() ? 1 : 0;
              default -> 0;
            };
          }

          public void set(int i, int value) {}

          public int getCount() {
            return 5;
          }
        });
  }

  private ModuleMenu(int id, Inventory inv, FactoryBlockEntity owner, ContainerData data) {
    super(Content.MODULE_MENU.get(), id);
    this.owner = owner;
    this.data = data;
    modules =
        owner instanceof MachineBlockEntity m
            ? m.modules.items
            : ((BeaconBlockEntity) owner).modules;
    addDataSlots(data);
    for (int s = 0; s < modules.getSlots(); s++)
      addSlot(new SlotItemHandler(modules, s, 28 + s * 22, 31));
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 108 + row * 18));
    for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 166));
  }

  public boolean stillValid(Player p) {
    return p.level().isClientSide
        || p.level() == owner.getLevel()
            && FluidConnections.canonical(owner)
            && AssemblyAccess.ready(owner)
            && p.distanceToSqr(owner.getBlockPos().getCenter()) <= 64;
  }

  public void clicked(int slot, int button, ClickType type, Player p) {
    if (!p.level().isClientSide && (p.isSpectator() || p.containerMenu != this || !stillValid(p)))
      return;
    super.clicked(slot, button, type, p);
  }

  public ItemStack quickMoveStack(Player p, int slot) {
    if (!stillValid(p) || p.isSpectator() || slot < 0 || slot >= slots.size())
      return ItemStack.EMPTY;
    var source = slots.get(slot);
    if (!source.hasItem()) return ItemStack.EMPTY;
    var stack = source.getItem();
    var original = stack.copy();
    int count = modules.getSlots();
    if (slot < count) {
      if (!moveItemStackTo(stack, count, slots.size(), true)) return ItemStack.EMPTY;
    } else if (!moveItemStackTo(stack, 0, count, false)) return ItemStack.EMPTY;
    if (stack.isEmpty()) source.setByPlayer(ItemStack.EMPTY);
    else source.setChanged();
    return original;
  }

  public static boolean open(MachineBlockEntity machine, Player player) {
    if (player.level().isClientSide
        || player.isSpectator()
        || !machine.kind().processing()
        || !machine.loaded()
        || !AssemblyAccess.ready(machine)
        || player.distanceToSqr(machine.getBlockPos().getCenter()) > 64) return false;
    player.openMenu(
        new net.minecraft.world.SimpleMenuProvider(
            (id, inv, p) -> new ModuleMenu(id, inv, machine),
            net.minecraft.network.chat.Component.translatable("gui.factorycraft.modules")),
        machine.getBlockPos());
    return true;
  }
}
