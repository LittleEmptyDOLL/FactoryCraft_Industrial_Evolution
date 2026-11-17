package factorycraft.energy;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.config.FactoryConfig;
import factorycraft.simulation.power.*;
import java.util.*;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Server adapter. Graph rebuilding has a separate per-level work budget and never loads chunks. */
@EventBusSubscriber(modid = FactoryCraft.ID)
public final class PowerGrid {
  private static final Map<Level, State> STATES = new IdentityHashMap<>();

  private static final class State {
    final PowerTopology<FactoryBlockEntity> graph =
        new PowerTopology<>(FactoryConfig.POWER_VERTICAL_REACH.get());
    long delivered, totalNanos;
    final Set<FactoryBlockEntity> supplies = new LinkedHashSet<>();
    List<FactoryBlockEntity> supplySnapshot = List.of();
    boolean suppliesDirty, dispatching;
    int modern;
  }

  public static void add(FactoryBlockEntity be) {
    var state = STATES.computeIfAbsent(be.getLevel(), l -> new State());
    var bounds = footprint(be);

    if (be instanceof GridSupply && state.supplies.add(be)) {
      state.suppliesDirty = true;
      if (!(be instanceof factorycraft.blockentity.ThermalBlockEntity)) state.modern++;
    }
    if (be instanceof GridConsumer consumer) consumer.gridBuffer().observe(() -> changed(be));
    var kind =
        be instanceof GridSupply supply && supply.storage()
            ? PowerTopology.Kind.STORAGE
            : be instanceof PoleBlockEntity
                ? PowerTopology.Kind.POLE
                : be instanceof GridGenerator generator && generator.generatesElectricity()
                    ? PowerTopology.Kind.SOURCE
                    : PowerTopology.Kind.CONSUMER;
    state.graph.add(
        be,
        bounds,
        be instanceof PoleBlockEntity p ? p.spec() : null,
        kind,
        kind == PowerTopology.Kind.CONSUMER ? ((GridConsumer) be)::receiveGridEnergy : null);
  }

  public static PowerFootprint footprint(FactoryBlockEntity be) {
    var p = be.getBlockPos();
    var profile = factorycraft.multiblock.MachineProfile.of(be.getBlockState());
    if (profile == null) return PowerFootprint.cell(new PowerPoint(p.getX(), p.getY(), p.getZ()));
    var f = profile.facing(be.getBlockState());
    var a = profile.origin(p, f);
    var b = profile.offset(p, f, profile.layout.width() - 1, 0, profile.layout.depth() - 1);
    return new PowerFootprint(
        Math.min(a.getX(), b.getX()),
        Math.max(a.getX(), b.getX()),
        a.getY(),
        Math.min(a.getZ(), b.getZ()),
        Math.max(a.getZ(), b.getZ()));
  }

  public static void refreshPole(PoleBlockEntity p) {
    if (p.getLevel() == null || p.getLevel().isClientSide) return;
    if (!p.available()) {
      remove(p);
      return;
    }
    add(p);
    p.completeAutoConnections();
    PowerWires.reconcile(p);
  }

  public static boolean edge(PoleBlockEntity a, PoleBlockEntity b, boolean connect) {
    var s = STATES.get(a.getLevel());
    if (s == null) return false;
    return connect
        ? s.graph.link(s.graph.find(a), s.graph.find(b))
        : s.graph.unlink(s.graph.find(a), s.graph.find(b));
  }

  public static boolean hasEdge(PoleBlockEntity a, PoleBlockEntity b) {
    var s = STATES.get(a.getLevel());
    return s != null && s.graph.hasEdge(s.graph.find(a), s.graph.find(b));
  }

  public static PowerTopology.NetworkInfo info(PoleBlockEntity p) {
    var s = STATES.get(p.getLevel());
    return s == null
        ? new PowerTopology.NetworkInfo(false, 0, 0, 0)
        : s.graph.info(s.graph.find(p));
  }

  public static void remove(FactoryBlockEntity be) {
    var s = STATES.get(be.getLevel());
    if (s != null) {
      s.graph.remove(be);
      if (s.supplies.remove(be)) {
        s.suppliesDirty = true;
        if (!(be instanceof factorycraft.blockentity.ThermalBlockEntity)) s.modern--;
      }
    }
  }

  @SubscribeEvent
  public static void tick(LevelTickEvent.Post e) {
    if (e.getLevel().isClientSide) return;
    var state = STATES.get(e.getLevel());
    if (state == null) return;
    state.graph.setVerticalReach(FactoryConfig.POWER_VERTICAL_REACH.get());
    long start = System.nanoTime();
    state.graph.advance(FactoryConfig.POWER_GRAPH_WORK.get());
    state.totalNanos += System.nanoTime() - start;
    if (state.modern > 0) settle(state);
  }

  @SubscribeEvent
  public static void unload(LevelEvent.Unload e) {
    STATES.remove(e.getLevel());
    if (e.getLevel() instanceof Level level) {
      PoleIndex.clear(level);
      PowerWires.clearLevel(level);
    }
  }

  @SubscribeEvent
  public static void loadedChunk(net.neoforged.neoforge.event.level.ChunkEvent.Load event) {
    if (event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk
        && !chunk.getLevel().isClientSide) PowerWires.loadedChunk(chunk.getLevel(), chunk);
  }

  public static boolean managed(FactoryBlockEntity be) {
    var s = STATES.get(be.getLevel());
    return s != null && s.modern > 0;
  }

  public static void changed(FactoryBlockEntity be) {
    if (be.getLevel() == null || be.getLevel().isClientSide) return;
    var s = STATES.get(be.getLevel());
    if (s != null) s.graph.changed(be);
  }

  private static void settle(State s) {
    if (s.dispatching) return;
    s.dispatching = true;
    try {
      if (s.suppliesDirty) {
        s.supplySnapshot = List.copyOf(s.supplies);
        s.suppliesDirty = false;
      }
      int budget = FactoryConfig.POWER_GRAPH_WORK.get();
      budget -= s.graph.refreshDemand(budget);
      // Dispatch actual generation to loads before touching tertiary storage.
      for (int priority = 0; priority < 3 && budget > 0; priority++) {
        if (priority == 2 && s.graph.demandPending()) break;
        for (var be : s.supplySnapshot) {
          var supply = (GridSupply) be;
          if (supply.supplyPriority() != priority || !supply.supplyReady()) continue;
          while (budget > 0 && supply.supplyAvailable() >= 15) {
            var target = s.graph.nextHungry(be);
            if (target == null) break;
            budget--;
            var consumer = (GridConsumer) target;
            int requested = (int) Math.min(Integer.MAX_VALUE, supply.supplyAvailable() / 15);
            int possible = consumer.receiveGridEnergy(requested, true);
            if (possible <= 0) {
              s.graph.refreshDemand(target);
              continue;
            }
            long reserved = supply.takeSupply((long) possible * 15);
            int whole = (int) (reserved / 15);
            int accepted = consumer.receiveGridEnergy(whole, false);
            long refund = reserved - (long) accepted * 15;
            if (refund > 0) supply.refundSupply(refund);
            s.delivered += accepted;
            s.graph.refreshDemand(target);
          }
        }
      }
      // If work is incomplete, retain source buffers and conservatively defer storage entirely.
      if (budget > 0 && !s.graph.demandPending())
        for (int priority = 0; priority < 2 && budget > 0; priority++)
          for (var be : s.supplySnapshot) {
            var source = (GridSupply) be;
            if (source.supplyPriority() != priority
                || !source.supplyReady()
                || s.graph.nextHungry(be) != null) continue;
            int count = s.graph.storageCount(be);
            for (int i = 0; i < count && budget > 0 && source.supplyAvailable() > 0; i++) {
              var target = s.graph.nextStorage(be);
              budget--;
              if (!(target instanceof GridSupply store) || !store.supplyReady()) continue;
              long reserved = source.takeSupply(Math.min(600, source.supplyAvailable()));
              long accepted = store.chargeSupply(reserved);
              if (reserved > accepted) source.refundSupply(reserved - accepted);
            }
          }
    } finally {
      for (var be : s.supplySnapshot) ((GridSupply) be).endSupplyTick();
      s.dispatching = false;
    }
  }

  public static boolean hasDemand(FactoryBlockEntity source) {
    var s = STATES.get(source.getLevel());
    return s != null && s.graph.prepareDemand(s.graph.find(source), 32);
  }

  public static int distribute(FactoryBlockEntity source, int available) {
    var s = STATES.get(source.getLevel());
    if (s == null) return 0;
    int delivered = s.graph.distributePrepared(s.graph.find(source), available);
    s.delivered += delivered;
    return delivered;
  }

  public static String statistics(Level level) {
    var s = STATES.get(level);
    if (s == null) return "Power nodes: 0";
    var g = s.graph;
    return "Power nodes: "
        + g.size()
        + ", copper edges: "
        + g.edges()
        + ", networks: "
        + g.networks()
        + ", rebuilding: "
        + g.pending()
        + ", rebuild work: "
        + g.lastWork()
        + "/"
        + FactoryConfig.POWER_GRAPH_WORK.get()
        + ", snapshots: "
        + g.rebuilds()
        + ", candidates: "
        + g.candidateChecks()
        + ", delivered: "
        + s.delivered
        + " FE, rebuild CPU: "
        + String.format(Locale.ROOT, "%.3f ms", s.totalNanos / 1e6);
  }

  public record Snapshot(
      int nodes,
      int networks,
      boolean pending,
      long rebuilds,
      long candidates,
      long delivered,
      long rebuildNanos) {}

  public static Snapshot snapshot(Level level) {
    var s = STATES.get(level);
    if (s == null) return new Snapshot(0, 0, false, 0, 0, 0, 0);
    return new Snapshot(
        s.graph.size(),
        s.graph.networks(),
        s.graph.pending(),
        s.graph.rebuilds(),
        s.graph.candidateChecks(),
        s.delivered,
        s.totalNanos);
  }

  private PowerGrid() {}
}
