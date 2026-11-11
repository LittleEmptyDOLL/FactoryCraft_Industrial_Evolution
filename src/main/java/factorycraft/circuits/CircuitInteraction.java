package factorycraft.circuits;

import factorycraft.FactoryCraft;
import factorycraft.blocks.FactoryKind;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;

/** Native interaction and tracked-chunk sync, never an arbitrary world discovery scan. */
@EventBusSubscriber(modid = FactoryCraft.ID)
public final class CircuitInteraction {
  @SubscribeEvent
  public static void open(PlayerInteractEvent.RightClickBlock event) {
    var player = event.getEntity();
    if (!event.getItemStack().isEmpty() || !player.getOffhandItem().isEmpty()) return;
    var node = CircuitSimulation.at(event.getLevel(), event.getPos());
    boolean modern =
        node != null
            && node.kind() != FactoryKind.CIRCUIT_READER
            && node.kind() != FactoryKind.CIRCUIT_CONTROLLER;
    if (!modern && !player.isShiftKeyDown()) return;
    if (event.getLevel().isClientSide) {
      if (modern || CircuitAccess.supported(event.getLevel().getBlockEntity(event.getPos()))) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
      }
      return;
    }
    var attachment = CircuitAccess.at(event.getLevel(), event.getPos());
    if (LogicMenu.open(attachment, player, event.getPos())) {
      event.setCanceled(true);
      event.setCancellationResult(InteractionResult.CONSUME);
    }
  }

  @SubscribeEvent
  public static void watch(ChunkWatchEvent.Sent event) {
    for (var node : CircuitSimulation.chunkNodes(event.getLevel(), event.getPos().toLong()))
      if (node.configured)
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
            event.getPlayer(), new CircuitNetwork.Snapshot(CircuitNetwork.visual(node)));
  }

  private CircuitInteraction() {}
}
