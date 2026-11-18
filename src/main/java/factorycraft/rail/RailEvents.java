package factorycraft.rail;

import factorycraft.FactoryCraft;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.*;

@EventBusSubscriber(modid = FactoryCraft.ID)
public final class RailEvents {
  static boolean checking;

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void broken(BlockEvent.BreakEvent e) {
    if (checking
        || e.isCanceled()
        || !(e.getLevel() instanceof ServerLevel level)
        || !(e.getPlayer() instanceof ServerPlayer p)
        || !e.getState().is(RailContent.BED.get())) return;
    var data = RailwayData.get(level);
    var hit = p.pick(p.blockInteractionRange(), 0, false);
    var id = data.nearest(e.getPos(), hit.getLocation());
    if (id == null) return;
    e.setCanceled(true);
    String result = RailPlacement.remove(p, id);
    p.displayClientMessage(
        net.minecraft.network.chat.Component.translatable("rail.factorycraft." + result), true);
  }

  public static InteractionResult wrench(UseOnContext c) {
    if (c.getPlayer() instanceof ServerPlayer p) {
      var data = RailwayData.get(p.serverLevel());
      var id = data.nearest(c.getClickedPos(), c.getClickLocation());
      if (id != null) {
        String result = RailPlacement.remove(p, id);
        p.displayClientMessage(
            net.minecraft.network.chat.Component.translatable("rail.factorycraft." + result), true);
      }
    }
    return InteractionResult.sidedSuccess(c.getLevel().isClientSide);
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void occupied(BlockEvent.EntityPlaceEvent e) {
    if (e.isCanceled() || !(e.getLevel() instanceof ServerLevel level)) return;
    var data = RailwayData.get(level);
    var positions =
        e instanceof BlockEvent.EntityMultiPlaceEvent multi
            ? multi.getReplacedBlockSnapshots().stream()
                .map(net.neoforged.neoforge.common.util.BlockSnapshot::getPos)
                .toList()
            : java.util.List.of(e.getPos());
    for (var p : positions) {
      var state = level.getBlockState(p);
      if (!state.isAir() && !state.is(RailContent.BED.get()) && data.reserved(p)) {
        e.setCanceled(true);
        if (e.getEntity() instanceof net.minecraft.world.entity.player.Player player)
          player.displayClientMessage(
              net.minecraft.network.chat.Component.translatable("rail.factorycraft.blocked"), true);
        return;
      }
    }
  }

  @SubscribeEvent
  public static void chunk(ChunkEvent.Unload e) {
    if (e.getLevel() instanceof ServerLevel l)
      RailwayData.get(l).unloadChunk(e.getChunk().getPos().toLong());
  }

  @SubscribeEvent
  public static void playerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e) {
    if (e.getEntity() instanceof ServerPlayer player) RailPlanner.tick(player);
  }

  @SubscribeEvent
  public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
    RailPlanner.forget(e.getEntity().getUUID());
  }

  @SubscribeEvent
  public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent e) {
    e.getDispatcher()
        .register(
            net.minecraft.commands.Commands.literal("factorycraft")
                .then(
                    net.minecraft.commands.Commands.literal("rail")
                        .requires(s -> s.hasPermission(2))
                        .executes(
                            c -> {
                              var data = RailwayData.get(c.getSource().getLevel());
                              c.getSource()
                                  .sendSuccess(
                                      () ->
                                          net.minecraft.network.chat.Component.literal(
                                              "Railway: "
                                                  + data.graph.edges().size()
                                                  + " edges, "
                                                  + data.graph.nodes()
                                                  + " oriented nodes, revision "
                                                  + data.graph.revisions()
                                                  + "; loaded "
                                                  + data.segments().stream()
                                                      .filter(s -> data.available(s.id))
                                                      .count()
                                                  + "; "
                                                  + data.segments().stream()
                                                      .mapToInt(RailwayData.Segment::paidCount)
                                                      .sum()
                                                  + " owned rail items"),
                                      false);
                              return 1;
                            })));
  }
}
