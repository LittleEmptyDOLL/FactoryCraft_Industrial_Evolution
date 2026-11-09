package factorycraft.circuits;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.config.FactoryConfig;
import factorycraft.fluids.*;
import factorycraft.simulation.circuits.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Server-only cached graph and staged synchronous signal updates; never force-loads a chunk. */
@EventBusSubscriber(modid = FactoryCraft.ID)
public final class CircuitSimulation {
  public record Port(CircuitNodeBlockEntity owner, int port, int color) {}

  private static final Map<Level, State> STATES = new IdentityHashMap<>();

  private static final class State {
    final Map<BlockPos, CircuitNodeBlockEntity> nodes = new HashMap<>();
    final Map<BlockPos, Set<CircuitNodeBlockEntity>> watchers = new HashMap<>();
    final Map<Long, Set<CircuitNodeBlockEntity>> chunks = new HashMap<>();
    final Map<CircuitNodeBlockEntity, Set<BlockPos>> watched = new IdentityHashMap<>();
    final Map<CircuitNodeBlockEntity, Set<Long>> physicalChunks = new IdentityHashMap<>();
    final LinkedHashSet<CircuitNodeBlockEntity> pending = new LinkedHashSet<>(),
        dirty = new LinkedHashSet<>(),
        poll = new LinkedHashSet<>();
    final CircuitGraph<Port, net.minecraft.resources.ResourceLocation> graph =
        new CircuitGraph<>(FactoryConfig.CIRCUIT_PORTS.get(), 8, p -> dirty.add(p.owner));
  }

  private static Port key(CircuitNodeBlockEntity n, int p, int color) {
    return new Port(n, p, color);
  }

  public static CircuitNodeBlockEntity at(Level level, BlockPos p) {
    var b = FluidConnections.at(level, p);
    if (b instanceof CasingBlockEntity part) {
      var owner = part.genericForRemoval();
      return owner instanceof CircuitNodeBlockEntity n ? n : null;
    }
    return b instanceof CircuitNodeBlockEntity n ? n : null;
  }

  public static void add(CircuitNodeBlockEntity n) {
    var s = STATES.computeIfAbsent(n.getLevel(), l -> new State());
    s.nodes.put(n.getBlockPos(), n);
    for (var p :
        n.assembly()
            .profile()
            .cells(n.getBlockPos(), n.getBlockState().getValue(FactoryBlock.FACING))) {
      long chunk = net.minecraft.world.level.ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4);
      s.chunks.computeIfAbsent(chunk, k -> new HashSet<>()).add(n);
      s.physicalChunks.computeIfAbsent(n, k -> new HashSet<>()).add(chunk);
    }
    index(n, s);
    s.pending.add(n);
    notifyPeers(n, s);
  }

  private static void unindex(CircuitNodeBlockEntity n, State s) {
    var old = s.watched.remove(n);
    if (old != null)
      for (var p : old) {
        var set = s.watchers.get(p);
        if (set != null) {
          set.remove(n);
          if (set.isEmpty()) s.watchers.remove(p);
        }
      }
  }

  private static void index(CircuitNodeBlockEntity n, State s) {
    unindex(n, s);
    var keys = new HashSet<BlockPos>();
    for (var link : n.links) {
      keys.add(link.peer());
      s.watchers.computeIfAbsent(link.peer(), k -> new HashSet<>()).add(n);
    }
    s.watched.put(n, keys);
  }

  private static void notifyPeers(CircuitNodeBlockEntity n, State s) {
    var set = s.watchers.get(n.getBlockPos());
    if (set != null) s.pending.addAll(set);
  }

  private static void deactivate(CircuitNodeBlockEntity n, State s) {
    if (!n.active) return;
    n.active = false;
    n.gate = false;
    for (int p = 0; p < n.ports(); p++) for (int c = 0; c < 2; c++) s.graph.remove(key(n, p, c));
    s.dirty.remove(n);
    s.poll.remove(n);
    notifyPeers(n, s);
  }

  public static void remove(CircuitNodeBlockEntity n) {
    var s = STATES.get(n.getLevel());
    if (s == null) return;
    deactivate(n, s);
    s.nodes.remove(n.getBlockPos(), n);
    s.pending.remove(n);
    unindex(n, s);
    var chunks = s.physicalChunks.remove(n);
    if (chunks != null)
      for (long c : chunks) {
        var owners = s.chunks.get(c);
        if (owners != null) {
          owners.remove(n);
          if (owners.isEmpty()) s.chunks.remove(c);
        }
      }
    notifyPeers(n, s);
  }

  public static void changed(CircuitNodeBlockEntity n) {
    if (n.getLevel() == null || n.getLevel().isClientSide) return;
    var s = STATES.get(n.getLevel());
    if (s == null || s.nodes.get(n.getBlockPos()) != n) return;
    if (!n.available()) deactivate(n, s);
    s.pending.add(n);
  }

  public static void programChanged(CircuitNodeBlockEntity n) {
    var s = STATES.get(n.getLevel());
    if (s != null) {
      s.dirty.add(n);
      if (n.kind() == FactoryKind.CIRCUIT_READER) {
        n.readerReady = false;
        for (int c = 0; c < 2; c++) s.graph.complete(key(n, 0, c), false);
        s.poll.add(n);
      }
    }
  }

  private static CircuitLink reverse(CircuitNodeBlockEntity n, CircuitLink link) {
    return new CircuitLink(
        n.getBlockPos(),
        n.assembly().identity(),
        link.remotePort(),
        link.localPort(),
        link.color());
  }

  private static boolean near(CircuitNodeBlockEntity a, int pa, CircuitNodeBlockEntity b, int pb) {
    return a.portPosition(pa).distSqr(b.portPosition(pb)) <= 256;
  }

  public static boolean connect(
      CircuitNodeBlockEntity a, int pa, CircuitNodeBlockEntity b, int pb, int color) {
    if (a.getLevel() != b.getLevel()
        || !a.available()
        || !b.available()
        || color < 0
        || color > 1
        || pa < 0
        || pa >= a.ports()
        || pb < 0
        || pb >= b.ports()
        || a == b && pa == pb
        || !near(a, pa, b, pb)) return false;
    var l = new CircuitLink(b.getBlockPos(), b.assembly().identity(), pa, pb, color);
    var r = reverse(a, l);
    var s = STATES.get(a.getLevel());
    if (s == null) return false;
    if (a.links.contains(l)) {
      a.links.remove(l);
      b.links.remove(r);
      s.graph.disconnect(key(a, pa, color), key(b, pb, color));
    } else {
      if (a.links.stream().filter(x -> x.localPort() == pa && x.color() == color).count() >= 8
          || b.links.stream().filter(x -> x.localPort() == pb && x.color() == color).count() >= 8
          || !s.graph.connect(key(a, pa, color), key(b, pb, color))) return false;
      a.links.add(l);
      b.links.add(r);
    }
    index(a, s);
    index(b, s);
    s.pending.add(a);
    s.pending.add(b);
    a.sync();
    b.sync();
    return true;
  }

  public static void clear(CircuitNodeBlockEntity n, int port, int color) {
    var s = STATES.get(n.getLevel());
    if (s == null) return;
    for (var l : List.copyOf(n.links))
      if (l.localPort() == port && l.color() == color) {
        var peer = at(n.getLevel(), l.peer());
        n.links.remove(l);
        if (peer != null && peer.assembly().owns(l.identity())) {
          peer.links.remove(reverse(n, l));
          if (n.active && peer.active)
            s.graph.disconnect(key(n, port, color), key(peer, l.remotePort(), color));
          index(peer, s);
          s.pending.add(peer);
          peer.sync();
        }
      }
    index(n, s);
    s.pending.add(n);
    n.sync();
  }

  public static void destroyed(CircuitNodeBlockEntity n) {
    if (n.getLevel() == null || n.getLevel().isClientSide) return;
    for (int p = 0; p < n.ports(); p++) for (int c = 0; c < 2; c++) clear(n, p, c);
    var target = target(n);
    if (target != null) {
      var binding = CircuitBinding.get(target);
      if (binding != null
          && binding.controller().equals(n.getBlockPos())
          && java.util.Objects.equals(n.assembly().identity(), binding.identity()))
        CircuitBinding.set(target, null);
    }
  }

  private static void refresh(CircuitNodeBlockEntity n, State s) {
    if (!n.available()) {
      deactivate(n, s);
      if (n.loaded()) s.pending.add(n);
      return;
    }
    if (!n.active) {
      n.active = true;
      for (int p = 0; p < n.ports(); p++) for (int c = 0; c < 2; c++) s.graph.add(key(n, p, c));
      publish(n, s);
      if (n.kind() == FactoryKind.CIRCUIT_READER || n.kind() == FactoryKind.CIRCUIT_CONTROLLER)
        s.poll.add(n);
      notifyPeers(n, s);
    }
    boolean pruned = false;
    for (var link : List.copyOf(n.links)) {
      var peer = at(n.getLevel(), link.peer());
      if (peer != null
          && peer.loaded()
          && peer.assembly().identity() != null
          && (!peer.assembly().owns(link.identity()) || !peer.links.contains(reverse(n, link)))) {
        n.links.remove(link);
        pruned = true;
        continue;
      }
      if (peer == null) {
        var chunk =
            ((ServerLevel) n.getLevel())
                .getChunkSource()
                .getChunkNow(link.peer().getX() >> 4, link.peer().getZ() >> 4);
        if (chunk != null
            && !(chunk.getBlockState(link.peer()).getBlock() instanceof FactoryBlock block
                && block.kind.circuit())) {
          n.links.remove(link);
          pruned = true;
        }
        continue;
      }
      if (peer.active && peer.assembly().owns(link.identity()) && link.remotePort() < peer.ports())
        s.graph.connect(
            key(n, link.localPort(), link.color()), key(peer, link.remotePort(), link.color()));
    }
    if (pruned) {
      index(n, s);
      n.sync();
      notifyPeers(n, s);
    }
    for (int p = 0; p < n.ports(); p++)
      for (int c = 0; c < 2; c++) {
        boolean complete = n.kind() != FactoryKind.CIRCUIT_READER || n.readerReady;
        for (var l : n.links)
          if (l.localPort() == p && l.color() == c) {
            var peer = s.nodes.get(l.peer());
            complete &=
                peer != null
                    && peer.active
                    && peer.assembly().owns(l.identity())
                    && peer.links.contains(reverse(n, l))
                    && s.graph.linked(key(n, p, c), key(peer, l.remotePort(), c));
          }
        s.graph.complete(key(n, p, c), complete);
      }
    s.dirty.add(n);
  }

  public static boolean inputReady(CircuitNodeBlockEntity n) {
    var s = STATES.get(n.getLevel());
    return s != null
        && n.active
        && n.links.stream().anyMatch(l -> l.localPort() == 0)
        && s.graph.valid(key(n, 0, 0))
        && s.graph.valid(key(n, 0, 1));
  }

  private static int input(CircuitNodeBlockEntity n, State s) {
    return s.graph.value(key(n, 0, 0), n.program().input())
        + s.graph.value(key(n, 0, 1), n.program().input());
  }

  private static void publish(CircuitNodeBlockEntity n, State s) {
    if (n.kind() == FactoryKind.CIRCUIT_CONTROLLER) return;
    for (int c = 0; c < 2; c++)
      s.graph.output(key(n, n.outputPort(), c), n.program().output(), n.lastOutput);
  }

  private static FactoryBlockEntity target(CircuitNodeBlockEntity n) {
    var b = FluidConnections.endpoint(n.getLevel(), n.targetPosition());
    return b instanceof InserterBlockEntity a && a.electric()
            || b instanceof FluidBlockEntity f && f.kind() == FluidKind.PUMP
        ? b
        : null;
  }

  private static boolean bind(CircuitNodeBlockEntity n) {
    var target = target(n);
    if (target == null) return false;
    var old = CircuitBinding.get(target);
    if (old != null
        && (!old.controller().equals(n.getBlockPos())
            || !old.identity().equals(n.assembly().identity()))) return false;
    if (old == null)
      CircuitBinding.set(target, new CircuitBinding(n.getBlockPos(), n.assembly().identity()));
    return true;
  }

  private record Reading(boolean valid, int value) {}

  private static Reading read(CircuitNodeBlockEntity n) {
    var level = (ServerLevel) n.getLevel();
    var p = n.targetPosition();
    if (level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4) == null
        || !level.shouldTickBlocksAt(
            net.minecraft.world.level.ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4)))
      return new Reading(false, 0);
    var endpoint = FluidConnections.endpoint(level, p);
    var owner = factorycraft.multiblock.AssemblyAccess.owner(endpoint);
    if (owner != null) {
      if (!owner.loaded() || !owner.assembly().ready()) return new Reading(false, 0);
      for (var cell :
          owner
              .assembly()
              .profile()
              .cells(
                  endpoint.getBlockPos(),
                  owner.assembly().profile().facing(endpoint.getBlockState())))
        if (!level.shouldTickBlocksAt(
            net.minecraft.world.level.ChunkPos.asLong(cell.getX() >> 4, cell.getZ() >> 4)))
          return new Reading(false, 0);
    }
    if (endpoint instanceof BeltBlockEntity belt
        && (!belt.loaded() || !factorycraft.simulation.BeltSimulation.tickable(belt)))
      return new Reading(false, 0);
    var handler =
        level.getCapability(
            Capabilities.ItemHandler.BLOCK,
            p,
            n.getBlockState().getValue(FactoryBlock.FACING).getOpposite());
    if (handler == null || handler.getSlots() < 1 || handler.getSlots() > 256)
      return new Reading(false, 0);
    int total = 0;
    for (int i = 0; i < handler.getSlots(); i++) {
      var item = handler.getStackInSlot(i);
      if (n.program().input().equals(CircuitProgram.A)
          || BuiltInRegistries.ITEM.getKey(item.getItem()).equals(n.program().input()))
        total += item.getCount();
    }
    return new Reading(true, total);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void tick(LevelTickEvent.Pre e) {
    if (!(e.getLevel() instanceof ServerLevel level) || !level.tickRateManager().runsNormally())
      return;
    var s = STATES.get(level);
    if (s == null) return;
    for (int i = 0, size = Math.min(256, s.pending.size()); i < size; i++) {
      var n = s.pending.iterator().next();
      s.pending.remove(n);
      if (s.nodes.get(n.getBlockPos()) == n) refresh(n, s);
    }
    for (int i = 0, size = Math.min(32, s.poll.size()); i < size; i++) {
      var n = s.poll.iterator().next();
      s.poll.remove(n);
      if (n.active) {
        s.poll.add(n);
        s.dirty.add(n);
        if (n.kind() == FactoryKind.CIRCUIT_READER) {
          var sample = read(n);
          n.readerReady = sample.valid();
          if (sample.valid() && sample.value() != n.lastOutput) {
            n.lastOutput = sample.value();
            n.persistSignal();
          }
          publish(n, s);
          refresh(n, s);
        }
      }
    }
    s.graph.rebuild(FactoryConfig.CIRCUIT_COMPONENT_WORK.get());
    record Update(CircuitNodeBlockEntity node, int value, boolean gate) {}
    var updates = new ArrayList<Update>();
    for (int i = 0, size = Math.min(FactoryConfig.CIRCUIT_DEVICE_WORK.get(), s.dirty.size());
        i < size;
        i++) {
      var n = s.dirty.iterator().next();
      s.dirty.remove(n);
      if (!n.active) continue;
      var p = n.program();
      int in = input(n, s), out = n.lastOutput;
      boolean gate = false;
      switch (n.kind()) {
        case CONSTANT -> out = p.operand();
        case CIRCUIT_READER -> {}
        case ARITHMETIC -> {
          if (inputReady(n))
            out =
                SignalMath.calculate(
                    SignalMath.Arithmetic.values()[p.operation()], in, p.operand());
        }
        case DECIDER -> {
          if (inputReady(n))
            out =
                SignalMath.test(
                        SignalMath.Compare.values()[Math.min(5, p.operation())], in, p.operand())
                    ? (p.outputCount() ? in : 1)
                    : 0;
        }
        case CIRCUIT_CONTROLLER ->
            gate =
                bind(n)
                    && inputReady(n)
                    && SignalMath.test(
                        SignalMath.Compare.values()[Math.min(5, p.operation())], in, p.operand());
        default -> {}
      }
      if (n.inputValue != in) {
        n.inputValue = in;
        n.persistSignal();
      }
      updates.add(new Update(n, out, gate));
    }
    for (var u : updates) {
      var n = u.node();
      if (n.lastOutput != u.value()) {
        n.lastOutput = u.value();
        n.persistSignal();
      }
      n.gate = u.gate();
      publish(n, s);
      if (n.kind() == FactoryKind.CIRCUIT_CONTROLLER) {
        var target = target(n);
        if (target instanceof FluidBlockEntity pump) FluidSimulation.activatePump(pump);
      }
    }
  }

  @SubscribeEvent
  public static void tickets(ChunkTicketLevelUpdatedEvent e) {
    var s = STATES.get(e.getLevel());
    if (s == null) return;
    var owners = s.chunks.get(e.getChunkPos());
    if (owners == null) return;
    for (var n : owners) {
      if (!net.minecraft.server.level.ChunkLevel.isBlockTicking(e.getNewTicketLevel()))
        deactivate(n, s);
      s.pending.add(n);
    }
  }

  @SubscribeEvent
  public static void chunkUnloading(ChunkEvent.Unload e) {
    if (!(e.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk)) return;
    var s = STATES.get(chunk.getLevel());
    if (s == null) return;
    var owners = s.chunks.get(chunk.getPos().toLong());
    if (owners == null) return;
    for (var n : List.copyOf(owners)) {
      deactivate(n, s);
      s.pending.add(n);
    }
  }

  @SubscribeEvent
  public static void unloading(LevelEvent.Unload e) {
    STATES.remove(e.getLevel());
  }

  public static String statistics(Level level) {
    var s = STATES.get(level);
    return s == null
        ? "Circuits: 0"
        : "Circuit nodes: "
            + s.nodes.size()
            + ", dirty devices: "
            + s.dirty.size()
            + ", pending topology: "
            + s.pending.size()
            + ", rebuilds: "
            + s.graph.rebuilds()
            + ", graph visits: "
            + s.graph.visits();
  }

  private CircuitSimulation() {}
}
