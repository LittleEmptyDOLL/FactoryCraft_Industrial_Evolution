package factorycraft.trains;

import factorycraft.FactoryCraft;
import factorycraft.simulation.trains.StockSpec;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = FactoryCraft.ID)
public final class TrainEvents {
  @SubscribeEvent
  public static void tick(LevelTickEvent.Post e) {
    if (e.getLevel() instanceof ServerLevel l) TrainData.get(l).tick();
  }

  @SubscribeEvent
  public static void load(ChunkEvent.Load e) {
    if (e.getLevel() instanceof ServerLevel l) {
      var d = TrainData.get(l);
      d.ensureIndex();
      d.wakeChunk(e.getChunk().getPos().toLong());
    }
  }

  @SubscribeEvent
  public static void unload(ChunkEvent.Unload e) {
    if (e.getLevel() instanceof ServerLevel l)
      TrainData.get(l).unloadChunk(e.getChunk().getPos().toLong());
  }

  @SubscribeEvent
  public static void projectionRemoved(
      net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent e) {
    if (e.getLevel() instanceof ServerLevel l && e.getEntity() instanceof StockEntity stock) {
      var d = TrainData.get(l);
      var t = d.owner(stock.getUUID());
      if (t != null && !t.provisional) d.active.add(t.id);
    }
  }

  @SubscribeEvent
  public static void tickets(net.neoforged.neoforge.event.level.ChunkTicketLevelUpdatedEvent e) {
    var d = TrainData.get(e.getLevel());
    if (net.minecraft.server.level.ChunkLevel.isBlockTicking(e.getNewTicketLevel())) {
      d.ensureIndex();
      if (d.relevantChunk(e.getChunkPos())) d.pendingChunks.add(e.getChunkPos());
    } else {
      d.pendingChunks.remove(e.getChunkPos());
      d.unloadChunk(e.getChunkPos());
    }
  }

  public static void capabilities(RegisterCapabilitiesEvent e) {
    for (var kind : StockSpec.values()) {
      if (kind != StockSpec.FLUID)
        e.registerEntity(
            Capabilities.ItemHandler.ENTITY,
            TrainContent.TYPES.get(kind).get(),
            (entity, unused) -> TrainPorts.inventory(entity));
      else
        e.registerEntity(
            Capabilities.FluidHandler.ENTITY,
            TrainContent.TYPES.get(kind).get(),
            TrainPorts::fluids);
    }
  }
}
