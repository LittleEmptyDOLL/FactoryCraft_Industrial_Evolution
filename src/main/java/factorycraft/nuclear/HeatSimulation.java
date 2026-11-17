package factorycraft.nuclear;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.FactoryBlockEntity;
import factorycraft.simulation.nuclear.HeatStore;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Loaded physical-port graph. Only local changed owners rebuild contacts; edge work is budgeted.
 */
@EventBusSubscriber(modid = FactoryCraft.ID)
public final class HeatSimulation {
  private record Contact(BlockPos p, Direction side) {}

  private static final class Node {
    final HeatOwner owner;
    final UUID identity;
    final List<Contact> contacts = new ArrayList<>();
    final Set<Edge> edges = new HashSet<>();
    boolean alive = true;
    long tick = Long.MIN_VALUE, used;

    long allowance(long now) {
      if (tick != now) {
        tick = now;
        used = 0;
      }
      return Math.max(0, owner.maximumTransfer() - used);
    }

    Node(HeatOwner owner) {
      this.owner = owner;
      identity = owner.heatController().assembly().identity();
    }

    boolean ready() {
      return alive
          && owner.heatController().simulating()
          && owner.heatController().assembly().owns(identity);
    }
  }

  private record Edge(Node a, Node b) {}

  private static final class State {
    final Map<FactoryBlockEntity, Node> nodes = new IdentityHashMap<>();
    final Map<Contact, Node> contacts = new HashMap<>();
    final Set<FactoryBlockEntity> dirty = new LinkedHashSet<>();
    final ArrayList<Edge> edges = new ArrayList<>();
    final Set<ReactorBlockEntity> reactors = new LinkedHashSet<>();
    List<ReactorBlockEntity> reactorSnapshot = List.of();
    boolean reactorDirty;
    int cursor;
    long transferred, work, nanos;
  }

  private static final Map<Level, State> STATES = new IdentityHashMap<>();

  public static void changed(FactoryBlockEntity be) {
    if (be instanceof HeatOwner && be.getLevel() != null && !be.getLevel().isClientSide)
      STATES.computeIfAbsent(be.getLevel(), l -> new State()).dirty.add(be);
  }

  public static void remove(FactoryBlockEntity be) {
    var s = STATES.get(be.getLevel());
    if (s == null) return;
    s.dirty.remove(be);
    var n = s.nodes.remove(be);
    if (n == null) return;
    n.alive = false;
    for (var p : n.contacts) s.contacts.remove(p, n);
    if (be instanceof ReactorBlockEntity reactor && s.reactors.remove(reactor))
      s.reactorDirty = true;
    // Dead edges are retired lazily by the same transfer budget, without a full edge-list copy.
  }

  private static void rebuild(State s, FactoryBlockEntity be) {
    remove(be);
    if (!(be instanceof HeatOwner h) || !h.heatController().ready()) return;
    var n = new Node(h);
    s.nodes.put(be, n);
    if (be instanceof ReactorBlockEntity reactor && s.reactors.add(reactor)) s.reactorDirty = true;
    for (var p : h.heatPorts()) {
      var key = new Contact(p.position(), p.side());
      var other =
          s.contacts.get(new Contact(p.position().relative(p.side()), p.side().getOpposite()));
      if (other != null && other.alive && other.owner != h) {
        boolean exists = n.edges.stream().anyMatch(e -> e.a == other || e.b == other);
        if (!exists) {
          var edge = new Edge(n, other);
          n.edges.add(edge);
          other.edges.add(edge);
          s.edges.add(edge);
        }
      }
      n.contacts.add(key);
      s.contacts.put(key, n);
    }
  }

  @SubscribeEvent
  public static void tick(LevelTickEvent.Post event) {
    if (event.getLevel().isClientSide) return;
    var s = STATES.get(event.getLevel());
    if (s == null) return;
    long start = System.nanoTime();
    for (int i = 0; i < 32 && !s.dirty.isEmpty(); i++) {
      var be = s.dirty.iterator().next();
      s.dirty.remove(be);
      rebuild(s, be);
    }
    if (s.reactorDirty) {
      s.reactorSnapshot = List.copyOf(s.reactors);
      s.reactorDirty = false;
    }
    for (var r : s.reactorSnapshot) if (r.simulating()) r.prepareFuel();
    for (var r : s.reactorSnapshot) r.snapshotActive();
    for (var r : s.reactorSnapshot) if (r.simulating()) r.burnForTick();
    int budget = Math.min(2048, s.edges.size());
    while (budget-- > 0 && !s.edges.isEmpty()) {
      if (s.cursor >= s.edges.size()) s.cursor = 0;
      var e = s.edges.get(s.cursor);
      if (!e.a.alive || !e.b.alive) {
        var last = s.edges.removeLast();
        if (s.cursor < s.edges.size()) s.edges.set(s.cursor, last);
        e.a.edges.remove(e);
        e.b.edges.remove(e);
        continue;
      }
      s.cursor++;
      s.work++;
      if (!e.a.ready() || !e.b.ready()) continue;
      long moved =
          HeatStore.transfer(
              e.a.owner.heat(),
              e.b.owner.heat(),
              Math.min(
                  e.a.allowance(event.getLevel().getGameTime()),
                  e.b.allowance(event.getLevel().getGameTime())));
      if (moved != 0) {
        e.a.used += Math.abs(moved);
        e.b.used += Math.abs(moved);
        e.a.owner.heatController().changed();
        e.b.owner.heatController().changed();
        s.transferred += Math.abs(moved);
      }
    }
    s.nanos += System.nanoTime() - start;
  }

  public record Snapshot(
      int nodes, int edges, int pending, long work, long transferred, long nanos) {}

  public static Snapshot snapshot(Level l) {
    var s = STATES.get(l);
    return s == null
        ? new Snapshot(0, 0, 0, 0, 0, 0)
        : new Snapshot(
            s.nodes.size(), s.edges.size(), s.dirty.size(), s.work, s.transferred, s.nanos);
  }

  public static String statistics(Level l) {
    var s = STATES.get(l);
    return s == null
        ? "Heat nodes: 0"
        : "Heat nodes: "
            + s.nodes.size()
            + ", edges: "
            + s.edges.size()
            + ", pending: "
            + s.dirty.size()
            + ", work: "
            + s.work
            + ", moved: "
            + s.transferred
            + " quanta, CPU ns: "
            + s.nanos;
  }

  @SubscribeEvent
  public static void unload(LevelEvent.Unload event) {
    STATES.remove(event.getLevel());
  }

  private HeatSimulation() {}
}
