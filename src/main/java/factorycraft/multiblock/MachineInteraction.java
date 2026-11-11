package factorycraft.multiblock;

import factorycraft.blockentity.*;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Server-only menu contacts: proximity belongs to the clicked physical part, not a remote owner.
 */
public final class MachineInteraction {
  private record Contact(FactoryBlockEntity owner, UUID identity, BlockPos cell) {}

  private static final WeakHashMap<AbstractContainerMenu, Contact> CONTACTS = new WeakHashMap<>();

  private static boolean physical(FactoryBlockEntity be, BlockPos cell) {
    var owner = AssemblyAccess.owner(be);
    if (be.getLevel() == null
        || be.isRemoved()
        || !be.getLevel().hasChunkAt(be.getBlockPos())
        || !be.getLevel().hasChunkAt(cell)
        || be.getLevel().getBlockEntity(be.getBlockPos()) != be) return false;
    if (owner == null) return be.getBlockPos().equals(cell);
    if (!owner.loaded() || !owner.assembly().ready()) return false;
    if (cell.equals(be.getBlockPos())) return true;
    return be.getLevel().getBlockEntity(cell) instanceof CasingBlockEntity part
        && part.boundTo(be.getBlockPos(), owner.assembly().identity());
  }

  public static boolean open(FactoryBlockEntity be, Player player, BlockPos cell) {
    if (be instanceof factorycraft.circuits.CircuitNodeBlockEntity node
        && node.kind() != factorycraft.blocks.FactoryKind.CIRCUIT_READER
        && node.kind() != factorycraft.blocks.FactoryKind.CIRCUIT_CONTROLLER)
      return factorycraft.circuits.LogicMenu.open(node.circuit(true), player, cell);
    return be instanceof MenuProvider menu && open(be, player, cell, menu);
  }

  public static boolean open(
      FactoryBlockEntity be, Player player, BlockPos cell, MenuProvider menu) {
    if (player.level().isClientSide
        || player.isSpectator()
        || player.level() != be.getLevel()
        || !physical(be, cell)
        || !player.canInteractWithBlock(cell, 0)
        || !be.getLevel().mayInteract(player, cell)) return false;
    if (be instanceof LabBlockEntity lab
        && !factorycraft.research.ResearchTeams.member(player, lab.scope())) return false;
    var previous = player.containerMenu;
    if (be instanceof factorycraft.blockentity.FluidBlockEntity f)
      player.openMenu(
          menu,
          b -> {
            b.writeBlockPos(be.getBlockPos());
            factorycraft.fluids.FluidMenu.writePreview(f, b);
          });
    else player.openMenu(menu, be.getBlockPos());
    if (player.containerMenu == previous) return false;
    var owner = AssemblyAccess.owner(be);
    CONTACTS.put(
        player.containerMenu,
        new Contact(be, owner == null ? null : owner.assembly().identity(), cell.immutable()));
    return true;
  }

  public static boolean inRange(FactoryBlockEntity be, Player player) {
    if (player.level().isClientSide) return true;
    var contact = CONTACTS.get(player.containerMenu);
    var cell = contact != null && contact.owner == be ? contact.cell : be.getBlockPos();
    var owner = AssemblyAccess.owner(be);
    return !player.isSpectator()
        && player.level() == be.getLevel()
        && physical(be, cell)
        && (contact == null
            || contact.owner == be && (owner == null || owner.assembly().owns(contact.identity)))
        && player.distanceToSqr(cell.getCenter()) <= 64;
  }

  public static void inherit(AbstractContainerMenu previous, AbstractContainerMenu next) {
    var contact = CONTACTS.get(previous);
    if (contact != null) CONTACTS.put(next, contact);
  }

  private MachineInteraction() {}
}
