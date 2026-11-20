package factorycraft.rail.automation;

import factorycraft.FactoryCraft;
import factorycraft.trains.RailAutomationData;
import net.minecraft.server.level.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.*;

@EventBusSubscriber(modid = FactoryCraft.ID)
public final class TracksideEvents {
  @SubscribeEvent
  public static void breakBlock(BlockEvent.BreakEvent e) {
    if (TracksidePlacement.checking
        || !(e.getState().getBlock() instanceof TracksideBlock)
        || e.isCanceled()) return;
    e.setCanceled(true);
    if (e.getPlayer() instanceof ServerPlayer p) {
      var result = TracksidePlacement.remove(p, e.getPos());
      if (!result.equals("ready"))
        p.displayClientMessage(
            net.minecraft.network.chat.Component.translatable("rail.factorycraft." + result), true);
    }
  }

  @SubscribeEvent
  public static void loaded(ChunkEvent.Load e) {
    if (e.getLevel() instanceof ServerLevel l) {
      var d = RailAutomationData.peek(l);
      if (d != null) d.chunkChanged(e.getChunk().getPos().toLong());
    }
  }

  @SubscribeEvent
  public static void unloaded(ChunkEvent.Unload e) {
    if (e.getLevel() instanceof ServerLevel l) {
      var d = RailAutomationData.peek(l);
      if (d != null) d.chunkChanged(e.getChunk().getPos().toLong());
    }
  }

  @SubscribeEvent
  public static void tickets(ChunkTicketLevelUpdatedEvent e) {
    var d = RailAutomationData.peek(e.getLevel());
    if (d != null) d.chunkChanged(e.getChunkPos());
  }

  @SubscribeEvent
  public static void levelUnload(LevelEvent.Unload e) {
    if (e.getLevel() instanceof ServerLevel l) RailAutomationData.forget(l);
  }

  private TracksideEvents() {}
}
