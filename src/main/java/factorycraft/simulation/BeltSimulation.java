package factorycraft.simulation;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.BeltBlockEntity;
import factorycraft.simulation.belts.BeltTransport;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Loaded-only topology adapter. The independent kernel schedules moving lanes, not the entire
 * factory.
 */
@EventBusSubscriber(modid = FactoryCraft.ID)
public final class BeltSimulation {
  private static final Map<Level, State> STATES = new IdentityHashMap<>();

  private static final class State {
    final BeltTransport<ItemStack> transport = new BeltTransport<>();
    final ArrayList<BeltBlockEntity> dirty = new ArrayList<>();
    final Map<Long, Set<BeltBlockEntity>> byChunk = new HashMap<>();
    final Map<BeltBlockEntity, long[]> ownerChunks = new IdentityHashMap<>();
    final Set<Long> tickingTickets = new HashSet<>(),
        tickingChunks = new HashSet<>(),
        pendingActivation = new HashSet<>();
    final ArrayDeque<BeltBlockEntity> refreshQueue = new ArrayDeque<>();
    final Set<BeltBlockEntity> refreshQueued = Collections.newSetFromMap(new IdentityHashMap<>());
    int loaded;
    long nanos;
  }

  public static void register(BeltBlockEntity be) {
    var s = STATES.computeIfAbsent(be.getLevel(), l -> new State());
    if (s.ownerChunks.containsKey(be)) return;
    s.loaded++;
    index(be, s);
    be.bindTransport(s.transport);
    topologyChanged(be);
    activate(be);
  }

  public static void unregister(BeltBlockEntity be) {
    var s = STATES.get(be.getLevel());
    if (s != null) {
      s.loaded--;
      unindex(be, s);
    }
    be.simulationLoaded = false;
    be.unbindTransport();
    topologyChanged(be);
  }

  private static void unindex(BeltBlockEntity be, State s) {
    var chunks = s.ownerChunks.remove(be);
    if (chunks == null) return;
    for (long chunk : chunks) {
      var owners = s.byChunk.get(chunk);
      if (owners != null) {
        owners.remove(be);
        if (owners.isEmpty()) {
          s.byChunk.remove(chunk);
          s.pendingActivation.remove(chunk);
          s.tickingChunks.remove(chunk);
        }
      }
    }
  }

  private static void index(BeltBlockEntity be, State s) {
    var chunks =
        be.physicalCells().stream()
            .mapToLong(p -> new net.minecraft.world.level.ChunkPos(p).toLong())
            .distinct()
            .sorted()
            .toArray();
    if (Arrays.equals(s.ownerChunks.get(be), chunks)) return;
    unindex(be, s);
    s.ownerChunks.put(be, chunks);
    for (long chunk : chunks) {
      s.byChunk
          .computeIfAbsent(chunk, k -> Collections.newSetFromMap(new IdentityHashMap<>()))
          .add(be);
      if (((net.minecraft.server.level.ServerLevel) be.getLevel())
                  .getChunkSource()
                  .getChunkNow(
                      net.minecraft.world.level.ChunkPos.getX(chunk),
                      net.minecraft.world.level.ChunkPos.getZ(chunk))
              != null
          && ((net.minecraft.server.level.ServerLevel) be.getLevel()).shouldTickBlocksAt(chunk))
        s.tickingChunks.add(chunk);
      else if (s.tickingTickets.contains(chunk)) s.pendingActivation.add(chunk);
    }
  }

  public static boolean tickable(BeltBlockEntity be) {
    var s = STATES.get(be.getLevel());
    if (s == null) return false;
    var chunks = s.ownerChunks.get(be);
    if (chunks == null) return false;
    for (long chunk : chunks) if (!s.tickingChunks.contains(chunk)) return false;
    return true;
  }

  private static void queueRefresh(State s, BeltBlockEntity be) {
    if (be.simulationLoaded && !be.isRemoved() && s.refreshQueued.add(be)) s.refreshQueue.add(be);
  }

  /** Ticket callback: bookkeeping/quarantine only. No world mutation, neighbor lookup or save. */
  private static void activateChunk(State s, long chunk, boolean ticking) {
    if (ticking) s.tickingChunks.add(chunk);
    else s.tickingChunks.remove(chunk);
    var owners = s.byChunk.get(chunk);
    if (owners == null) return;
    for (var be : owners) {
      if (!ticking) be.suspendTransport();
      queueRefresh(s, be);
      var pair = be.paired();
      if (pair != null) {
        if (!ticking) pair.suspendTransport();
        queueRefresh(s, pair);
      }
    }
  }

  /** Safe tick/event geometry guard, independent of stale loaded/ticket flags. */
  public static boolean refreshReady(BeltBlockEntity be) {
    if (!be.loaded() || !(be.getLevel() instanceof net.minecraft.server.level.ServerLevel server))
      return false;
    var s = STATES.get(server);
    var chunks = s == null ? null : s.ownerChunks.get(be);
    if (chunks == null) return false;
    // Reuse the topology's physical-chunk index: moving/dirty belts must not allocate
    // and enumerate their complete geometry on every tick.
    for (long key : chunks) {
      var chunk =
          server
              .getChunkSource()
              .getChunkNow(
                  net.minecraft.world.level.ChunkPos.getX(key),
                  net.minecraft.world.level.ChunkPos.getZ(key));
      if (chunk == null || !server.shouldTickBlocksAt(key)) return false;
    }
    var p = be.getBlockPos();
    var owner = server.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
    if (owner == null || owner.getBlockEntity(p) != be) return false;
    return be.structureReady();
  }

  @SubscribeEvent
  public static void ticketLevel(
      net.neoforged.neoforge.event.level.ChunkTicketLevelUpdatedEvent event) {
    var s = STATES.computeIfAbsent(event.getLevel(), l -> new State());
    long chunk = event.getChunkPos();
    boolean ticking =
        net.minecraft.server.level.ChunkLevel.isBlockTicking(event.getNewTicketLevel());
    if (ticking) {
      s.tickingTickets.add(chunk);
      if (s.byChunk.containsKey(chunk)) {
        activateChunk(s, chunk, false);
        s.pendingActivation.add(chunk);
      }
    } else {
      s.tickingTickets.remove(chunk);
      s.pendingActivation.remove(chunk);
      activateChunk(s, chunk, false);
    }
  }

  public static void activate(BeltBlockEntity be) {
    if (be.simulationLoaded) be.wakeTransport();
  }

  public static BeltBlockEntity at(Level level, net.minecraft.core.BlockPos pos) {
    var be = factorycraft.fluids.FluidConnections.at(level, pos);
    if (be instanceof factorycraft.blockentity.TransportPartBlockEntity part) return part.owner();
    if (be instanceof factorycraft.blockentity.CasingBlockEntity part) {
      var owner = part.genericForRemoval();
      return owner instanceof BeltBlockEntity belt ? belt : null;
    }
    return be instanceof BeltBlockEntity belt ? belt : null;
  }

  private static final Set<BeltBlockEntity> REFRESHING =
      Collections.newSetFromMap(new IdentityHashMap<>());

  public static void topologyChanged(BeltBlockEntity be) {
    var level = be.getLevel();
    if (level == null || level.isClientSide || !REFRESHING.add(be)) return;
    try {
      if (be.simulationLoaded) {
        var state = STATES.get(level);
        if (state != null) index(be, state);
      }
      var nearby = Collections.newSetFromMap(new IdentityHashMap<BeltBlockEntity, Boolean>());
      if (be.simulationLoaded) nearby.add(be);
      var cells = new ArrayList<>(be.physicalCells());
      cells.add(be.outputPosition());
      for (var cell : cells)
        for (var d : Direction.Plane.HORIZONTAL)
          for (int y = -1; y <= 1; y++) {
            var neighbor = at(level, cell.relative(d).above(y));
            if (neighbor != null && neighbor.simulationLoaded) nearby.add(neighbor);
          }
      // Pair discovery is bounded and happens only at topology events, never in microsteps.
      if (be.kind().underground())
        for (var d : Direction.Plane.HORIZONTAL)
          for (int n = 1; n <= 9; n++) {
            var neighbor = at(level, be.getBlockPos().relative(d, n));
            if (neighbor != null && neighbor.simulationLoaded && neighbor.kind().underground())
              nearby.add(neighbor);
          }
      for (var b : List.copyOf(nearby))
        if (b.paired() != null && b.paired().simulationLoaded) nearby.add(b.paired());
      // Geometry first, then connect sources against the final lane mapping.
      for (var b : nearby) {
        if (refreshReady(b)) b.refreshConnections();
        else {
          b.suspendTransport();
          var state = STATES.get(level);
          if (state != null) queueRefresh(state, b);
        }
      }
      for (var b : nearby) {
        if (refreshReady(b)) b.refreshConnections();
        else {
          b.suspendTransport();
          var state = STATES.get(level);
          if (state != null) queueRefresh(state, b);
        }
      }
      // Direct contacts follow the final route/physical footprint, including neighbor reshapes.
      for (var b : nearby)
        if (b.circuit(false) != null)
          factorycraft.circuits.CircuitSimulation.changed(b.circuit(false));
    } finally {
      REFRESHING.remove(be);
    }
  }

  public static void changed(BeltBlockEntity be, boolean contents) {
    var state = STATES.get(be.getLevel());
    if (state != null && be.markSimulationChanged(contents)) state.dirty.add(be);
  }

  @SubscribeEvent
  public static void tick(LevelTickEvent.Post e) {
    if (e.getLevel().isClientSide) return;
    var s = STATES.get(e.getLevel());
    if (s == null) return;
    if (!s.pendingActivation.isEmpty()) {
      var ready = new ArrayList<Long>();
      var iterator = s.pendingActivation.iterator();
      while (iterator.hasNext()) {
        long chunk = iterator.next();
        if (((net.minecraft.server.level.ServerLevel) e.getLevel())
                    .getChunkSource()
                    .getChunkNow(
                        net.minecraft.world.level.ChunkPos.getX(chunk),
                        net.minecraft.world.level.ChunkPos.getZ(chunk))
                != null
            && ((net.minecraft.server.level.ServerLevel) e.getLevel()).shouldTickBlocksAt(chunk)) {
          iterator.remove();
          ready.add(chunk);
        }
      }
      for (long chunk : ready) activateChunk(s, chunk, true);
    }
    int refreshes = Math.min(4096, s.refreshQueue.size());
    for (int i = 0; i < refreshes; i++) {
      var be = s.refreshQueue.remove();
      s.refreshQueued.remove(be);
      if (!be.simulationLoaded || be.isRemoved()) continue;
      if (refreshReady(be)) topologyChanged(be);
      else {
        be.suspendTransport();
        queueRefresh(s, be);
      }
    }
    long start = System.nanoTime();
    if (e.getLevel().tickRateManager().runsNormally()) s.transport.tick();
    s.nanos += System.nanoTime() - start;
    for (int i = 0; i < s.dirty.size(); ) {
      var be = s.dirty.get(i);
      if (be.simulationLoaded && !be.isRemoved() && !refreshReady(be)) {
        i++;
        continue;
      }
      int last = s.dirty.size() - 1;
      s.dirty.set(i, s.dirty.get(last));
      s.dirty.remove(last);
      if (!be.isRemoved() && be.simulationLoaded)
        be.flushSimulationChanges(e.getLevel().getGameTime());
      else be.retireSimulationChanges();
    }
  }

  public static String statistics(Level level) {
    var s = STATES.get(level);
    return s == null
        ? "Belts: 0"
        : "Belts: "
            + s.loaded
            + ", active lanes: "
            + s.transport.activeLanes()
            + ", microstep visits: "
            + s.transport.visits()
            + ", transfers: "
            + s.transport.transfers()
            + ", CPU: "
            + String.format(Locale.ROOT, "%.3f ms", s.nanos / 1e6);
  }

  public record Snapshot(
      int loaded, int activeLanes, long visits, long transfers, long kernelNanos) {}

  public static Snapshot snapshot(Level level) {
    var s = STATES.get(level);
    return s == null
        ? new Snapshot(0, 0, 0, 0, 0)
        : new Snapshot(
            s.loaded,
            s.transport.activeLanes(),
            s.transport.visits(),
            s.transport.transfers(),
            s.nanos);
  }

  @SubscribeEvent
  public static void unload(LevelEvent.Unload e) {
    STATES.remove(e.getLevel());
  }

  private BeltSimulation() {}
}
