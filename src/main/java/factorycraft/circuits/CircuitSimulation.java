package factorycraft.circuits;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.config.FactoryConfig;
import factorycraft.fluids.*;
import factorycraft.simulation.circuits.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** The original cached circuit graph, now with typed maps and universal canonical attachments. */
@EventBusSubscriber(modid = FactoryCraft.ID)
public final class CircuitSimulation {
  public record Port(CircuitAttachment owner, int port, int color) {}

  private static final Map<Level, State> STATES = new IdentityHashMap<>();

  private static final class State {
    final Map<BlockPos, CircuitAttachment> nodes = new HashMap<>();
    final Map<BlockPos, Set<CircuitAttachment>> watchers = new HashMap<>();
    final Map<CircuitAttachment, Set<BlockPos>> watched = new IdentityHashMap<>();
    final Map<Long, Set<CircuitAttachment>> chunks = new HashMap<>(),
        sensorChunks = new HashMap<>();
    final Map<CircuitAttachment, Set<Long>> physicalChunks = new IdentityHashMap<>(),
        sensorDependencies = new IdentityHashMap<>();
    final Map<BlockPos, Set<BeltBlockEntity>> controlledSplitters = new HashMap<>();
    final LinkedHashSet<CircuitAttachment> pending = new LinkedHashSet<>(),
        dirty = new LinkedHashSet<>(),
        poll = new LinkedHashSet<>(),
        pulses = new LinkedHashSet<>(),
        clearPulses = new LinkedHashSet<>();
    final CircuitGraph<Port, SignalKey> graph =
        new CircuitGraph<>(
            FactoryConfig.CIRCUIT_PORTS.get(),
            8,
            p -> {
              dirty.add(p.owner);
              // Value changes queue one bounded evaluation. Topology loss closes direct gates
              // immediately without merging thousands of signals inside graph notifications.
              if (!p.owner.combinator() && !inputReady(p.owner)) {
                p.owner.inputReady = false;
                if (p.owner.permitted && p.owner.settings().gate()) {
                  p.owner.permitted = false;
                  CircuitDevices.controlChanged(p.owner);
                }
              }
              controlChanged(p.owner);
            });
  }

  private static Port key(CircuitAttachment n, int p, int color) {
    return new Port(n, p, color);
  }

  public static CircuitNodeBlockEntity at(Level level, BlockPos pos) {
    var owner = FluidConnections.endpoint(level, pos);
    return owner instanceof CircuitNodeBlockEntity n ? n : null;
  }

  public static CircuitAttachment resolve(Level level, BlockPos pos) {
    return CircuitAccess.at(level, pos);
  }

  public static CircuitAttachment find(Level level, BlockPos pos) {
    var s = STATES.get(level);
    return s == null ? null : s.nodes.get(pos);
  }

  public static void add(CircuitNodeBlockEntity n) {
    var a = n.circuit(true);
    a.configured = true;
    add(a);
  }

  public static void add(CircuitAttachment n) {
    if (n.level() == null || n.level().isClientSide) return;
    var s = STATES.computeIfAbsent(n.level(), l -> new State());
    var previous = s.nodes.get(n.pos());
    if (previous == n) {
      physicalIndex(n, s);
      index(n, s);
      s.pending.add(n);
      notifyPeers(n, s);
      return;
    }
    if (previous != null) remove(previous);
    s.nodes.put(n.pos(), n);
    physicalIndex(n, s);
    index(n, s);
    s.pending.add(n);
    notifyPeers(n, s);
  }

  private static void physicalIndex(CircuitAttachment n, State s) {
    var next = new HashSet<Long>();
    for (var cell : n.cells())
      next.add(net.minecraft.world.level.ChunkPos.asLong(cell.getX() >> 4, cell.getZ() >> 4));
    var previous = s.physicalChunks.get(n);
    if (next.equals(previous)) return;
    if (previous != null)
      for (long chunk : previous) {
        var set = s.chunks.get(chunk);
        if (set != null) {
          set.remove(n);
          if (set.isEmpty()) s.chunks.remove(chunk);
        }
      }
    s.physicalChunks.put(n, next);
    for (long chunk : next) s.chunks.computeIfAbsent(chunk, k -> new HashSet<>()).add(n);
  }

  private static void unindex(CircuitAttachment n, State s) {
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

  private static void index(CircuitAttachment n, State s) {
    unindex(n, s);
    var positions = new HashSet<BlockPos>();
    for (var link : n.links) {
      positions.add(link.peer());
      s.watchers.computeIfAbsent(link.peer(), k -> new HashSet<>()).add(n);
    }
    s.watched.put(n, positions);
  }

  private static void notifyPeers(CircuitAttachment n, State s) {
    var peers = s.watchers.get(n.pos());
    if (peers != null) s.pending.addAll(peers);
  }

  private static void deactivate(CircuitAttachment n, State s) {
    if (!n.active) return;
    n.active = false;
    CircuitNetwork.changed(n);
    n.inputReady = false;
    n.permitted = !n.settings().gate();
    if (n.legacy() != null) {
      n.legacy().active = false;
      n.legacy().gate = false;
    }
    for (int p = 0; p < n.ports(); p++) for (int c = 0; c < 4; c++) s.graph.remove(key(n, p, c));
    s.dirty.remove(n);
    s.poll.remove(n);
    CircuitDevices.controlChanged(n);
    controlChanged(n);
    notifyPeers(n, s);
  }

  public static void remove(CircuitNodeBlockEntity n) {
    var a = n.circuit(false);
    if (a != null) remove(a);
  }

  public static void remove(CircuitAttachment n) {
    var s = STATES.get(n.level());
    if (s == null) return;
    deactivate(n, s);
    s.nodes.remove(n.pos(), n);
    s.pending.remove(n);
    unindex(n, s);
    unwatch(n, s);
    var cs = s.physicalChunks.remove(n);
    if (cs != null)
      for (long c : cs) {
        var set = s.chunks.get(c);
        if (set != null) {
          set.remove(n);
          if (set.isEmpty()) s.chunks.remove(c);
        }
      }
    notifyPeers(n, s);
  }

  public static void changed(CircuitNodeBlockEntity n) {
    var a = n.circuit(false);
    if (a != null) changed(a);
  }

  public static void changed(CircuitAttachment n) {
    var s = STATES.get(n.level());
    if (s == null || s.nodes.get(n.pos()) != n) return;
    physicalIndex(n, s);
    if (!n.available()) deactivate(n, s);
    s.pending.add(n);
  }

  public static void programChanged(CircuitNodeBlockEntity n) {
    var a = n.circuit(true);
    if (a.legacyMode) a.useLegacy();
    else programChanged(a);
  }

  public static void programChanged(CircuitAttachment n) {
    var s = STATES.get(n.level());
    if (s == null || s.nodes.get(n.pos()) != n) {
      add(n);
      s = STATES.get(n.level());
    }
    if (s == null) return;
    s.dirty.add(n);
    s.pending.add(n);
    if (n.legacy() != null && n.legacy().kind() == FactoryKind.CIRCUIT_READER) {
      n.legacy().readerReady = false;
      for (int c = 0; c < 4; c++) s.graph.complete(key(n, 0, c), false);
    }
    if (!n.combinator() && !n.legacyMode) {
      // Configuration changes must withdraw old sensor outputs, including Hold -> Pulse.
      s.pulses.remove(n);
      s.clearPulses.remove(n);
      n.pendingPulse = SignalSet.EMPTY;
      if (n.owner() instanceof BeltBlockEntity && n.settings().pulse()) {
        n.output = CircuitDevices.stateSignals(n);
        n.sensorReady = true;
      } else {
        CircuitDevices.Reading reading;
        try {
          reading = CircuitDevices.read(n);
        } catch (RuntimeException invalidSensor) {
          reading = new CircuitDevices.Reading(false, SignalSet.EMPTY);
        }
        n.sensorReady = reading.ready();
        if (reading.ready()) n.output = reading.signals();
      }
      if (!CircuitDevices.needsPoll(n)) {
        n.sensorReady = true;
        n.output = SignalSet.EMPTY;
      }
      n.persistent();
      publish(n, s);
    }
    if (n.owner() instanceof BeltBlockEntity b) b.updateCircuitPulseWatch();
    updateDirect(n, s);
  }

  private static CircuitLink reverse(CircuitAttachment n, CircuitLink l) {
    return new CircuitLink(n.pos(), n.identity(), l.remotePort(), l.localPort(), l.color());
  }

  public static boolean connect(
      CircuitNodeBlockEntity a, int pa, CircuitNodeBlockEntity b, int pb, int color) {
    return connect(a.circuit(true), pa, b.circuit(true), pb, color);
  }

  public static boolean connect(
      CircuitAttachment a, int pa, CircuitAttachment b, int pb, int color) {
    if (a.level() != b.level()
        || !a.available()
        || !b.available()
        || a.identity() == null
        || b.identity() == null
        || !WireColors.valid(color)
        || pa < 0
        || pa >= a.ports()
        || pb < 0
        || pb >= b.ports()
        || a == b && pa == pb
        || a.point(pa, color).distanceToSqr(b.point(pb, color)) > 256) return false;
    add(a);
    add(b);
    var s = STATES.get(a.level());
    refresh(a, s);
    refresh(b, s);
    var link = new CircuitLink(b.pos(), b.identity(), pa, pb, color);
    var back = reverse(a, link);
    if (a.links.contains(link)) {
      a.links.remove(link);
      b.links.remove(back);
      s.graph.disconnect(key(a, pa, color), key(b, pb, color));
    } else {
      if (a.links.stream().filter(l -> l.localPort() == pa && l.color() == color).count() >= 8
          || b.links.stream().filter(l -> l.localPort() == pb && l.color() == color).count() >= 8
          || !s.graph.connect(key(a, pa, color), key(b, pb, color))) return false;
      a.links.add(link);
      b.links.add(back);
    }
    a.configured = b.configured = true;
    index(a, s);
    index(b, s);
    s.pending.add(a);
    s.pending.add(b);
    a.changed();
    b.changed();
    return true;
  }

  public static void clear(CircuitNodeBlockEntity n, int port, int color) {
    clear(n.circuit(true), port, color);
  }

  public static void clear(CircuitAttachment n, int port, int color) {
    var s = STATES.get(n.level());
    if (s == null) return;
    for (var l : List.copyOf(n.links))
      if (l.localPort() == port && l.color() == color) {
        n.links.remove(l);
        var peer = resolve(n.level(), l.peer());
        if (peer != null && Objects.equals(peer.identity(), l.identity())) {
          peer.links.remove(reverse(n, l));
          if (n.active && peer.active)
            s.graph.disconnect(key(n, port, color), key(peer, l.remotePort(), color));
          index(peer, s);
          s.pending.add(peer);
          peer.changed();
        }
      }
    index(n, s);
    s.pending.add(n);
    n.changed();
  }

  public static void destroyed(CircuitNodeBlockEntity n) {
    var a = n.circuit(false);
    if (a != null) destroyed(a);
  }

  public static void destroyed(CircuitAttachment n) {
    if (n.level() == null || n.level().isClientSide) return;
    for (int p = 0; p < n.ports(); p++) for (int c = 0; c < 4; c++) clear(n, p, c);
    if (n.legacy() != null) {
      var t = target(n.legacy());
      if (t != null) {
        var binding = CircuitBinding.get(t);
        if (binding != null
            && binding.controller().equals(n.pos())
            && Objects.equals(binding.identity(), n.identity())) CircuitBinding.set(t, null);
      }
    }
  }

  private static void refresh(CircuitAttachment n, State s) {
    if (!n.available() || n.identity() == null) {
      deactivate(n, s);
      if (!n.owner().isRemoved() && !n.suspended) s.pending.add(n);
      return;
    }
    if (!n.active) {
      n.active = true;
      if (!n.pendingPulse.isEmpty()) s.pulses.add(n);
      if (n.settings().pulse()
          && n.output.values().keySet().stream().anyMatch(k -> k.type() == SignalKey.Type.ITEM))
        s.clearPulses.add(n);
      if (n.legacy() != null) n.legacy().active = true;
      for (int p = 0; p < n.ports(); p++) for (int c = 0; c < 4; c++) s.graph.add(key(n, p, c));
      if (CircuitDevices.needsPoll(n)) s.poll.add(n);
      publish(n, s);
      notifyPeers(n, s);
      CircuitNetwork.changed(n);
    }
    boolean pruned = false;
    for (var link : List.copyOf(n.links)) {
      var peer = resolve(n.level(), link.peer());
      if (peer != null
          && peer.identity() != null
          && (!peer.identity().equals(link.identity()) || !peer.links.contains(reverse(n, link)))) {
        n.links.remove(link);
        pruned = true;
        continue;
      }
      if (peer == null) {
        var chunk =
            ((ServerLevel) n.level())
                .getChunkSource()
                .getChunkNow(link.peer().getX() >> 4, link.peer().getZ() >> 4);
        if (chunk != null && chunk.getBlockState(link.peer()).isAir()) {
          n.links.remove(link);
          pruned = true;
        }
        continue;
      }
      if (peer.active
          && Objects.equals(peer.identity(), link.identity())
          && link.remotePort() < peer.ports())
        s.graph.connect(
            key(n, link.localPort(), link.color()), key(peer, link.remotePort(), link.color()));
    }
    if (CircuitDevices.needsPoll(n)) s.poll.add(n);
    else {
      s.poll.remove(n);
      n.sensorReady = true;
    }
    if (pruned) {
      index(n, s);
      n.changed();
      notifyPeers(n, s);
    }
    for (int p = 0; p < n.ports(); p++)
      for (int c = 0; c < 4; c++) {
        boolean complete =
            !n.invalid()
                && n.sensorReady
                && (n.legacy() == null
                    || n.legacy().kind() != FactoryKind.CIRCUIT_READER
                    || n.legacy().readerReady);
        for (var l : n.links)
          if (l.localPort() == p && l.color() == c) {
            var peer = s.nodes.get(l.peer());
            complete &=
                peer != null
                    && peer.active
                    && Objects.equals(peer.identity(), l.identity())
                    && peer.links.contains(reverse(n, l))
                    && s.graph.linked(key(n, p, c), key(peer, l.remotePort(), c));
          }
        s.graph.complete(key(n, p, c), complete);
      }
    s.dirty.add(n);
  }

  public static boolean inputReady(CircuitNodeBlockEntity n) {
    var a = n.circuit(false);
    return a != null && inputReady(a) && n.links.stream().anyMatch(l -> l.localPort() == 0);
  }

  public static boolean inputReady(CircuitAttachment n) {
    var s = STATES.get(n.level());
    if (s == null || !n.active) return false;
    for (int c = 0; c < 4; c++)
      if ((n.settings().inputColors() & (1 << c)) != 0 && !s.graph.valid(key(n, 0, c)))
        return false;
    return true;
  }

  private static SignalProgram.Inputs input(CircuitAttachment n, State s) {
    var values = new SignalSet[4];
    for (int c = 0; c < 4; c++)
      values[c] =
          (n.settings().inputColors() & (1 << c)) == 0
              ? SignalSet.EMPTY
              : SignalSet.copy(s.graph.signals(key(n, 0, c)));
    return new SignalProgram.Inputs(values);
  }

  private static void captureInput(CircuitAttachment n, State s) {
    try {
      var inputs = input(n, s);
      // A merged read is bounded too; reject the complete observation rather than truncate.
      var union = new HashSet<SignalKey>();
      for (int c = 0; c < 4; c++) union.addAll(inputs.read(1 << c).values().keySet());
      if (union.size() > SignalSet.LIMIT)
        throw new IllegalArgumentException("Merged signal capacity");
      n.inputs = inputs;
      n.input = inputs.read(15);
    } catch (IllegalArgumentException overCapacity) {
      n.inputReady = false;
      n.inputs = SignalProgram.Inputs.single(SignalSet.EMPTY);
      n.input = SignalSet.EMPTY;
    }
  }

  private static void publish(CircuitAttachment n, State s) {
    if (n.legacy() != null && n.legacy().kind() == FactoryKind.CIRCUIT_CONTROLLER) return;
    for (int c = 0; c < 4; c++)
      s.graph.output(
          key(n, n.outputPort(), c),
          (n.settings().outputColors() & (1 << c)) == 0 ? Map.of() : n.output.values());
  }

  private static void updateDirect(CircuitAttachment n, State s) {
    if (n.combinator() || n.legacyMode) return;
    boolean before = n.permitted;
    n.inputReady = inputReady(n);
    captureInput(n, s);
    n.permitted =
        !n.invalid() && (!n.settings().gate() || n.inputReady && CircuitDevices.condition(n));
    if (before != n.permitted) CircuitDevices.controlChanged(n);
  }

  public static boolean permitted(FactoryBlockEntity be) {
    var n = be.circuit(false);
    return n == null
        || !n.configured
        || !n.invalid()
            && (!n.settings().gate() || n.active && n.available() && n.inputReady && n.permitted);
  }

  public static void watchSplitter(BeltBlockEntity belt, CircuitBinding old) {
    if (belt.getLevel() == null || belt.getLevel().isClientSide) return;
    var s = STATES.computeIfAbsent(belt.getLevel(), l -> new State());
    if (old != null) {
      var set = s.controlledSplitters.get(old.controller());
      if (set != null) {
        set.remove(belt);
        if (set.isEmpty()) s.controlledSplitters.remove(old.controller());
      }
    }
    if (belt.loaded() && belt.circuitBinding != null)
      s.controlledSplitters
          .computeIfAbsent(
              belt.circuitBinding.controller(),
              k -> Collections.newSetFromMap(new IdentityHashMap<>()))
          .add(belt);
    if (belt.loaded()) belt.refreshCircuitControl();
  }

  private static void controlChanged(CircuitAttachment n) {
    if (n.legacy() == null || n.legacy().kind() != FactoryKind.CIRCUIT_CONTROLLER) return;
    var s = STATES.get(n.level());
    var belts = s == null ? null : s.controlledSplitters.get(n.pos());
    if (belts != null)
      for (var belt : List.copyOf(belts)) if (belt.loaded()) belt.refreshCircuitControl();
    var target = target(n.legacy());
    if (target instanceof FluidBlockEntity f) FluidSimulation.activatePump(f);
  }

  private static FactoryBlockEntity target(CircuitNodeBlockEntity n) {
    var b = FluidConnections.endpoint(n.getLevel(), n.targetPosition());
    return b instanceof InserterBlockEntity
            || b instanceof FluidBlockEntity f && f.kind() == FluidKind.PUMP
            || b instanceof BeltBlockEntity belt && belt.kind().splitter()
        ? b
        : null;
  }

  public static boolean controls(CircuitNodeBlockEntity n, FactoryBlockEntity device) {
    return target(n) == device;
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

  private static void unwatch(CircuitAttachment n, State s) {
    var old = s.sensorDependencies.remove(n);
    if (old != null)
      for (long c : old) {
        var set = s.sensorChunks.get(c);
        if (set != null) {
          set.remove(n);
          if (set.isEmpty()) s.sensorChunks.remove(c);
        }
      }
  }

  public static void watchSensor(
      CircuitAttachment n, BlockPos target, FactoryBlockEntity endpoint) {
    var s = STATES.get(n.level());
    if (s == null) return;
    var keys = new HashSet<Long>();
    keys.add(net.minecraft.world.level.ChunkPos.asLong(target.getX() >> 4, target.getZ() >> 4));
    var owner = factorycraft.multiblock.AssemblyAccess.owner(endpoint);
    if (owner != null)
      for (var p :
          owner
              .assembly()
              .profile()
              .cells(
                  endpoint.getBlockPos(),
                  owner.assembly().profile().facing(endpoint.getBlockState())))
        keys.add(net.minecraft.world.level.ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4));
    if (keys.equals(s.sensorDependencies.get(n))) return;
    unwatch(n, s);
    s.sensorDependencies.put(n, keys);
    for (long c : keys) s.sensorChunks.computeIfAbsent(c, k -> new HashSet<>()).add(n);
  }

  public static void pulse(BeltBlockEntity belt, net.minecraft.world.item.ItemStack stack) {
    var n = belt.circuit(false);
    if (n == null || !n.settings().pulse() || !n.settings().contents()) return;
    n.pendingPulse =
        new SignalSet.Builder()
            .addAll(n.pendingPulse)
            .add(
                SignalKey.item(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(stack.getItem())
                        .toString()),
                stack.getCount())
            .build();
    n.persistent();
    var s = STATES.get(n.level());
    if (s != null) s.pulses.add(n);
  }

  private static SignalSet evaluate(CircuitAttachment n, ServerLevel level) {
    try {
      var result =
          n.program()
              .evaluate(
                  n.inputs,
                  new SignalProgram.Clock(
                      level.getGameTime(), (int) Math.floorMod(level.getDayTime(), 24000), 24000),
                  n.randomSeed,
                  CircuitDevices::stackSize);
      n.overCapacity = false;
      return result;
    } catch (IllegalArgumentException outputLimit) {
      // A valid Each + specific-output program can exceed 4096 keys. Never publish its
      // partially built map, crash the server, discard the program, or invent a zero output.
      n.overCapacity = true;
      return n.output;
    }
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void tick(LevelTickEvent.Pre event) {
    if (!(event.getLevel() instanceof ServerLevel level) || !level.tickRateManager().runsNormally())
      return;
    var s = STATES.get(level);
    if (s == null) return;
    for (int i = 0, size = Math.min(256, s.pending.size()); i < size && !s.pending.isEmpty(); i++) {
      var n = s.pending.iterator().next();
      s.pending.remove(n);
      if (s.nodes.get(n.pos()) == n) refresh(n, s);
    }
    for (int i = 0, size = Math.min(FactoryConfig.CIRCUIT_SENSOR_WORK.get(), s.poll.size());
        i < size && !s.poll.isEmpty();
        i++) {
      var n = s.poll.iterator().next();
      s.poll.remove(n);
      if (!n.active) continue;
      s.poll.add(n);
      s.dirty.add(n);
      if (!n.combinator()) {
        CircuitDevices.Reading reading;
        try {
          reading = CircuitDevices.read(n);
        } catch (RuntimeException invalidSensor) {
          reading = new CircuitDevices.Reading(false, SignalSet.EMPTY);
        }
        if (n.sensorReady != reading.ready()) {
          n.sensorReady = reading.ready();
          refresh(n, s);
        }
        if (n.legacy() != null && n.legacy().kind() == FactoryKind.CIRCUIT_READER) {
          n.legacy().readerReady = reading.ready();
          refresh(n, s);
        }
        if (reading.ready() && !reading.signals().equals(n.output)) {
          n.output = reading.signals();
          n.persistent();
          publish(n, s);
        }
      }
    }
    s.graph.rebuild(FactoryConfig.CIRCUIT_COMPONENT_WORK.get());
    record Update(CircuitAttachment node, SignalSet output) {}
    var updates = new ArrayList<Update>();
    int signalWork = 0;
    for (int i = 0, size = Math.min(FactoryConfig.CIRCUIT_DEVICE_WORK.get(), s.dirty.size());
        i < size && !s.dirty.isEmpty();
        i++) {
      var n = s.dirty.iterator().next();
      s.dirty.remove(n);
      if (!n.active) continue;
      n.inputReady = inputReady(n);
      captureInput(n, s);
      int cost =
          Math.max(1, n.input.size())
              * Math.max(
                  1,
                  n.program().conditions().size()
                      + n.program().outputs().size()
                      + n.program().elseOutputs().size());
      if (signalWork > 0 && signalWork + cost > FactoryConfig.CIRCUIT_SIGNAL_WORK.get()) {
        s.dirty.add(n);
        break;
      }
      signalWork += cost;
      SignalSet out = n.output;
      if (n.legacyMode && n.legacy() != null) {
        var old = n.legacy();
        int in = n.input.get(SignalProgramData.legacy(old.program().input()));
        old.inputValue = in;
        switch (old.kind()) {
          case CIRCUIT_CONTROLLER ->
              old.gate =
                  bind(old)
                      && inputReady(old)
                      && SignalMath.test(
                          SignalMath.Compare.values()[Math.min(5, old.program().operation())],
                          in,
                          old.program().operand());
          case CIRCUIT_READER -> {}
          default -> {
            if (old.kind() == FactoryKind.CONSTANT || inputReady(old)) out = evaluate(n, level);
          }
        }
        old.persistSignal();
        controlChanged(n);
      } else if (n.combinator()) {
        if (!n.program().enabled()
            || n.inputReady
            || n.program().kind() == SignalProgram.Kind.CONSTANT
            || n.program().kind() == SignalProgram.Kind.SELECTOR
                && n.program().selector().mode() == SignalProgram.Selection.TIME)
          out = evaluate(n, level);
        if (n.program().kind() == SignalProgram.Kind.SELECTOR
            && (n.program().selector().mode() == SignalProgram.Selection.TIME
                || n.program().selector().mode() == SignalProgram.Selection.RANDOM)) s.dirty.add(n);
      } else {
        boolean before = n.permitted;
        n.permitted =
            !n.invalid() && (!n.settings().gate() || n.inputReady && CircuitDevices.condition(n));
        if (before != n.permitted) CircuitDevices.controlChanged(n);
        CircuitDevices.apply(n);
      }
      updates.add(new Update(n, out));
    }
    for (var u : updates) {
      var n = u.node;
      if (!n.output.equals(u.output)) {
        n.output = u.output;
        n.persistent();
      }
      if (n.legacy() != null) {
        n.legacy().lastOutput =
            n.output.get(SignalProgramData.legacy(n.legacy().program().output()));
        n.legacy().inputValue = n.input.get(SignalProgramData.legacy(n.legacy().program().input()));
      }
      publish(n, s);
    }
    CircuitDevices.selectResearch(level);
    for (var n : List.copyOf(s.clearPulses)) {
      if (n.active) {
        n.output = CircuitDevices.stateSignals(n);
        publish(n, s);
      }
      s.clearPulses.remove(n);
    }
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void pulses(LevelTickEvent.Post event) {
    var s = STATES.get(event.getLevel());
    if (s == null) return;
    for (var n : List.copyOf(s.pulses)) {
      if (n.available() && n.active) {
        n.output =
            new SignalSet.Builder()
                .addAll(n.pendingPulse)
                .addAll(CircuitDevices.stateSignals(n))
                .build();
        n.pendingPulse = SignalSet.EMPTY;
        publish(n, s);
        n.persistent();
        s.clearPulses.add(n);
      }
      if (n.pendingPulse.isEmpty()) s.pulses.remove(n);
    }
  }

  @SubscribeEvent
  public static void tickets(ChunkTicketLevelUpdatedEvent event) {
    var s = STATES.get(event.getLevel());
    if (s == null) return;
    var owners = s.chunks.get(event.getChunkPos());
    if (owners != null)
      for (var n : List.copyOf(owners)) {
        if (!net.minecraft.server.level.ChunkLevel.isBlockTicking(event.getNewTicketLevel()))
          deactivate(n, s);
        s.pending.add(n);
      }
    var sensors = s.sensorChunks.get(event.getChunkPos());
    if (sensors != null
        && !net.minecraft.server.level.ChunkLevel.isBlockTicking(event.getNewTicketLevel()))
      for (var n : List.copyOf(sensors)) {
        if (n.legacy() != null) n.legacy().readerReady = false;
        for (int c = 0; c < 4; c++) s.graph.complete(key(n, 0, c), false);
        s.pending.add(n);
      }
  }

  @SubscribeEvent
  public static void chunkLoaded(ChunkEvent.Load event) {
    if (event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk c
        && !c.getLevel().isClientSide) CircuitContainers.loaded(c);
  }

  @SubscribeEvent
  public static void chunkUnloading(ChunkEvent.Unload event) {
    if (!(event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk)
        || chunk.getLevel().isClientSide) return;
    CircuitContainers.unloaded(chunk);
    var s = STATES.get(chunk.getLevel());
    if (s == null) return;
    var owners = s.chunks.get(chunk.getPos().toLong());
    if (owners != null)
      for (var n : List.copyOf(owners)) {
        deactivate(n, s);
        s.pending.add(n);
      }
    var sensors = s.sensorChunks.get(chunk.getPos().toLong());
    if (sensors != null)
      for (var n : List.copyOf(sensors)) {
        if (n.legacy() != null) n.legacy().readerReady = false;
        for (int c = 0; c < 4; c++) s.graph.complete(key(n, 0, c), false);
        s.pending.add(n);
      }
  }

  @SubscribeEvent
  public static void blockChanged(BlockEvent.NeighborNotifyEvent event) {
    if (event.getLevel() instanceof ServerLevel level)
      CircuitContainers.changed(level, event.getPos());
  }

  @SubscribeEvent
  public static void unloading(LevelEvent.Unload event) {
    STATES.remove(event.getLevel());
    if (event.getLevel() instanceof Level level) CircuitDevices.unload(level);
  }

  public static Collection<CircuitAttachment> chunkNodes(Level level, long chunk) {
    var s = STATES.get(level);
    return s == null ? List.of() : List.copyOf(s.chunks.getOrDefault(chunk, Set.of()));
  }

  public static String statistics(Level level) {
    var s = STATES.get(level);
    return s == null
        ? "Circuits: 0"
        : "Circuit owners: "
            + s.nodes.size()
            + ", dirty devices: "
            + s.dirty.size()
            + ", pending topology: "
            + s.pending.size()
            + ", sensor polls: "
            + s.poll.size()
            + "/"
            + FactoryConfig.CIRCUIT_SENSOR_WORK.get()
            + ", rebuilds: "
            + s.graph.rebuilds()
            + ", graph visits: "
            + s.graph.visits();
  }

  private CircuitSimulation() {}
}
