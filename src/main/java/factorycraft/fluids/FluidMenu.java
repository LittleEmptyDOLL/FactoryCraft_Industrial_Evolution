package factorycraft.fluids;

import factorycraft.blockentity.FluidBlockEntity;
import factorycraft.registry.Content;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/**
 * Read-only status menu. Volumes/registry IDs use two 16-bit fields to avoid vanilla data-slot
 * truncation.
 */
public final class FluidMenu extends AbstractContainerMenu {
  public final FluidBlockEntity machine;
  public final ContainerData data;
  public java.util.List<net.minecraft.core.BlockPos> preview = java.util.List.of();

  public FluidMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
    this(
        id,
        inventory,
        (FluidBlockEntity) inventory.player.level().getBlockEntity(buffer.readBlockPos()),
        new SimpleContainerData(20));
    int n = buffer.readVarInt();
    if (n < 0 || n > 128) throw new IllegalArgumentException("Pipeline preview too large");
    var cells = new java.util.ArrayList<net.minecraft.core.BlockPos>();
    for (int i = 0; i < n; i++) cells.add(buffer.readBlockPos());
    preview = java.util.List.copyOf(cells);
  }

  public static void writePreview(FluidBlockEntity be, RegistryFriendlyByteBuf buffer) {
    var cells = FluidSimulation.preview(be);
    buffer.writeVarInt(cells.size());
    for (var p : cells) buffer.writeBlockPos(p);
  }

  public long totalAmount() {
    return (amount() & 0xffffffffL)
        | ((data.get(16) & 65535L) << 32)
        | ((data.get(17) & 65535L) << 48);
  }

  public long totalCapacity() {
    return (((data.get(2) & 65535L) | ((data.get(3) & 65535L) << 16)))
        | ((data.get(18) & 65535L) << 32)
        | ((data.get(19) & 65535L) << 48);
  }

  public int cells() {
    return (data.get(7) & 65535) | ((data.get(8) & 65535) << 16);
  }

  public long segmentId() {
    return (data.get(14) & 65535L) | ((data.get(15) & 65535L) << 16);
  }

  public FluidMenu(int id, Inventory inventory, FluidBlockEntity machine, ContainerData data) {
    super(Content.FLUID_MENU.get(), id);
    this.machine = machine;
    this.data = data;
    addDataSlots(data);
  }

  public int amount() {
    return (data.get(0) & 65535) | ((data.get(1) & 65535) << 16);
  }

  public int capacity() {
    return Math.max(amount(), (data.get(2) & 65535) | ((data.get(3) & 65535) << 16));
  }

  public int fluidId() {
    return (data.get(5) & 65535) | ((data.get(6) & 65535) << 16);
  }

  @Override
  public boolean stillValid(Player player) {
    return machine != null
        && machine.loaded()
        && player.level() == machine.getLevel()
        && player.level().getBlockEntity(machine.getBlockPos()) == machine
        && factorycraft.multiblock.MachineInteraction.inRange(machine, player);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    return ItemStack.EMPTY;
  }
}
