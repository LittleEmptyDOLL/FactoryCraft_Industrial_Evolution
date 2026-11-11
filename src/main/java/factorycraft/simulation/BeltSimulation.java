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
    int loaded;
    long nanos;
  }

  public static void register(BeltBlockEntity be) {
    var s = STATES.computeIfAbsent(be.getLevel(), l -> new State());
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
      if (((net.minecraft.server.level.ServerLevel) be.getLevel()).shouldTickBlocksAt(chunk))
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

  private static void activateChunk(State s, long chunk, boolean ticking) {
    if (ticking) s.tickingChunks.add(chunk);
    else s.tickingChunks.remove(chunk);
    var owners = s.byChunk.get(chunk);
    if (owners == null) return;
    var affected = Collections.newSetFromMap(new IdentityHashMap<BeltBlockEntity, Boolean>());
    affected.addAll(owners);
    for (var be : owners)
      if (be.paired() != null && be.paired().loaded()) affected.add(be.paired());
    for (var be : affected) if (be.loaded()) be.refreshConnections();
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
        if (event.getLevel().shouldTickBlocksAt(chunk)) activateChunk(s, chunk, true);
        else s.pendingActivation.add(chunk);
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
      for (var b : nearby) b.refreshConnections();
      for (var b : nearby) b.refreshConnections();
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
        if (((net.minecraft.server.level.ServerLevel) e.getLevel()).shouldTickBlocksAt(chunk)) {
          iterator.remove();
          ready.add(chunk);
        }
      }
      for (long chunk : ready) activateChunk(s, chunk, true);
    }
    long start = System.nanoTime();
    if (e.getLevel().tickRateManager().runsNormally()) s.transport.tick();
    s.nanos += System.nanoTime() - start;
    for (int i = 0; i < s.dirty.size(); i++) {
      var be = s.dirty.get(i);
      if (!be.isRemoved()) be.flushSimulationChanges(e.getLevel().getGameTime());
    }
    s.dirty.clear();
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
