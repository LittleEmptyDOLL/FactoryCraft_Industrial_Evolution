package factorycraft.fluids;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.FluidBlockEntity;
import factorycraft.blocks.FluidDeviceBlock;
import factorycraft.config.FactoryConfig;
import factorycraft.multiblock.*;
import factorycraft.simulation.fluids.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Loaded-only pipeline membership and physical boundary adapter. Passive edges are topology only;
 * runtime volume belongs to a single pool. Chunk snapshots/packing materialize exact member shares.
 * Sleeping pumps wake from cached segment boundaries, energy, circuits and structural changes.
 */
@EventBusSubscriber(modid = FactoryCraft.ID)
public final class FluidSimulation {
  public static final int PUMP_RATE = FluidUnits.PUMP_PER_TICK, PUMP_FE = 20;
  private static final boolean PROFILE =
      System.getProperty("factorycraft.fluidBenchmark.fixture") != null;
  private static final Map<Level, State> STATES = new IdentityHashMap<>();

  private static final class Link {
    final FluidBlockEntity be;
    final java.util.UUID identity;
    final Set<Link> neighbors = new HashSet<>();
    final Set<Long> chunks = new HashSet<>();
    final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<java.util.List<FluidCell<FluidKey>>>
        ports = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    PipelineNetwork<FluidKey>.Node node;
    boolean queued, dirty, removed;
    long paidTick = Long.MIN_VALUE;
    FluidKey water;

    Link(FluidBlockEntity be) {
      this.be = be;
      identity = be.fluidOwner();
    }
  }

  private static final class ChunkRef {
    final LevelChunk chunk;
    int members;

    ChunkRef(LevelChunk chunk) {
      this.chunk = chunk;
    }
  }

  private static final class GroupInfo {
    final Map<Long, ChunkRef> chunks = new HashMap<>();
    final Set<Link> pumps = new HashSet<>();
    long dirtyTick = Long.MIN_VALUE;
    boolean queued;
  }

  private static final class State {
    final Level level;
    final PipelineNetwork<FluidKey> network;
    final Map<BlockPos, Link> nodes = new HashMap<>();
    final Map<PipelineNetwork<FluidKey>.Node, Link> owners = new IdentityHashMap<>();
    final Map<PipelineNetwork<FluidKey>.Segment, GroupInfo> groups = new IdentityHashMap<>();
    final ArrayDeque<PipelineNetwork<FluidKey>.Segment> changedGroups = new ArrayDeque<>();
    final Map<Long, Set<Link>> chunks = new HashMap<>();
    final LinkedHashSet<Link> recheck = new LinkedHashSet<>();
    final ArrayDeque<Link> pumps = new ArrayDeque<>();
    final ArrayList<Link> dirty = new ArrayList<>();
    long nanos, pumped, worldWater, chunkMarks, rawChanges;

    State(Level level) {
      this.level = level;
      network =
          new PipelineNetwork<>(
              k -> k.stack(1).getFluidType(),
              this::changed,
              n -> {
                var l = owners.get(n);
                if (l != null) mark(this, l);
              },
              this::prepare,
              this::detach);
    }

    void sizedMark() {
      if (PROFILE) chunkMarks++;
    }

    void prepare(PipelineNetwork<FluidKey>.Segment group, PipelineNetwork<FluidKey>.Node node) {
      var info = groups.computeIfAbsent(group, g -> new GroupInfo());
      var l = owners.get(node);
      if (l == null) return;
      for (long cp : l.chunks) {
        var chunk =
            ((ServerLevel) level)
                .getChunkSource()
                .getChunkNow(ChunkPos.getX(cp), ChunkPos.getZ(cp));
        if (chunk != null) info.chunks.computeIfAbsent(cp, k -> new ChunkRef(chunk)).members++;
      }
      for (var next : l.neighbors) if (next.be.kind() == FluidKind.PUMP) info.pumps.add(next);
      info.dirtyTick = Long.MIN_VALUE;
    }

    void detach(PipelineNetwork<FluidKey>.Segment group, PipelineNetwork<FluidKey>.Node node) {
      var info = groups.get(group);
      var l = owners.get(node);
      if (info == null || l == null) return;
      for (long cp : l.chunks) {
        var ref = info.chunks.get(cp);
        if (ref != null && --ref.members == 0) info.chunks.remove(cp);
      }
      for (var pump : l.neighbors)
        if (pump.be.kind() == FluidKind.PUMP) {
          boolean still = false;
          for (var other : pump.neighbors)
            if (other != l && other.node != null && other.node.segment() == group) {
              still = true;
              break;
            }
          if (!still) info.pumps.remove(pump);
        }
      info.dirtyTick = Long.MIN_VALUE;
    }

    void markChunks(GroupInfo info) {
      for (var ref : info.chunks.values())
        if (!ref.chunk.isUnsaved()) {
          ref.chunk.setUnsaved(true);
          sizedMark();
        }
    }

    void changed(PipelineNetwork<FluidKey>.Segment segment) {
      var info = groups.get(segment);
      if (!segment.valid()) {
        groups.remove(segment);
        if (info != null && segment.published()) {
          markChunks(info);
          for (var pump : info.pumps) wake(this, pump);
        }
        return;
      }
      if (info == null) throw new IllegalStateException("Unprepared published pipeline");
      long tick = level.getServer().getTickCount();
      if (info.dirtyTick != tick) {
        info.dirtyTick = tick;
        markChunks(info);
      }
      if (!info.queued) {
        info.queued = true;
        changedGroups.addLast(segment);
      }
    }
  }

  private static final ThreadLocal<Integer> MANUAL = ThreadLocal.withInitial(() -> 0);

  public static boolean manual() {
    return MANUAL.get() > 0;
  }

  public static <T> T manual(java.util.function.Supplier<T> action) {
    MANUAL.set(MANUAL.get() + 1);
    try {
      return action.get();
    } finally {
      MANUAL.set(MANUAL.get() - 1);
    }
  }

  public static FluidCell<FluidKey> owned(FluidBlockEntity be) {
    var state = STATES.get(be.getLevel());
    var l = state == null ? null : state.nodes.get(be.getBlockPos());
    return l == null || l.be != be || l.node == null ? be.localCell() : l.node.owned();
  }

  private static boolean eligible(State s, Link l) {
    if (l.removed
        || !l.identity.equals(l.be.fluidOwner())
        || s.nodes.get(l.be.getBlockPos()) != l
        || !l.be.simulationAvailable()) return false;
    var server = (ServerLevel) s.level;
    for (long cp : l.chunks) if (!server.shouldTickBlocksAt(cp)) return false;
    return true;
  }

  private static boolean powered(State s, Link l) {
    return l.be.kind() != FluidKind.PUMP
        || factorycraft.circuits.CircuitBinding.allowed(l.be)
            && (l.paidTick == s.level.getGameTime() || l.be.power.getEnergyStored() >= PUMP_FE);
  }

  private static void pay(State s, Link l) {
    if (l.be.kind() == FluidKind.PUMP && l.paidTick != s.level.getGameTime()) {
      if (!powered(s, l) || !l.be.power.spend(PUMP_FE))
        throw new IllegalStateException("Pump energy reservation changed");
      l.paidTick = s.level.getGameTime();
    }
  }

  public static FluidCell<FluidKey> hydraulic(
      FluidBlockEntity be, BlockPos physical, Direction side) {
    var s = STATES.get(be.getLevel());
    var l = s == null ? null : s.nodes.get(be.getBlockPos());
    if (l == null || l.be != be || l.removed) return null;
    var ports = l.ports.get(physical.asLong());
    if (ports == null) {
      ports = new ArrayList<>(Collections.nCopies(7, null));
      l.ports.put(physical.asLong(), ports);
    }
    int index = side == null ? 6 : side.get3DDataValue();
    var existing = ports.get(index);
    if (existing != null) return existing;
    Object token = new Object();
    var view =
        new BoundaryFluidCell<FluidKey>(
            () -> l.node == null ? be.localCell() : l.node.port(token),
            () ->
                eligible(s, l)
                    && (be.kind() == FluidKind.PIPE
                        || MachineFluidAccess.definition(be, physical, side) != null)
                    && (l.node == null || l.node.segment() != null && l.node.segment().valid()),
            s.level::getGameTime,
            l.node == null ? PUMP_RATE : boundaryRate(be, physical, side),
            () -> MANUAL.get() > 0,
            () -> powered(s, l),
            () -> pay(s, l),
            () ->
                l.node == null
                    ? Double.NaN
                    : l.node.segment() == null
                        ? 0
                        : (double) l.node.segment().amount()
                            / Math.max(1, l.node.segment().capacity()));
    ports.set(index, view);
    return view;
  }

  private static int boundaryRate(FluidBlockEntity be, BlockPos physical, Direction side) {
    if (side != null) {
      var other = FluidConnections.fluidDevice(be.getLevel(), physical.relative(side));
      if (other != null && other.kind() == FluidKind.PUMP) return PUMP_RATE * 5;
    }
    return FluidUnits.boundaryRate(boundaryCapacity(be, physical, side));
  }

  private static int boundaryCapacity(FluidBlockEntity be, BlockPos physical, Direction side) {
    int capacity = be.kind().capacity;
    if (side != null) {
      var other = FluidConnections.endpoint(be.getLevel(), physical.relative(side));
      if (other != null && !(other instanceof FluidBlockEntity)) {
        var p = MachineFluidAccess.definition(other, physical.relative(side), side.getOpposite());
        if (p != null) {
          for (int channel : new int[] {p.input(), p.output()}) {
            var cell = MachineFluidAccess.cell(other, channel);
            if (cell != null) capacity = Math.max(capacity, cell.capacity());
          }
        }
      }
    }
    return capacity;
  }

  private static void mark(State s, Link l) {
    if (l.removed) return;
    l.be.markCircuitStatePersistent();
    if (!l.dirty) {
      l.dirty = true;
      s.dirty.add(l);
    }
    wake(s, l);
  }

  public static boolean registered(FluidBlockEntity be) {
    var s = STATES.get(be.getLevel());
    var l = s == null ? null : s.nodes.get(be.getBlockPos());
    return l != null && l.be == be && !l.removed;
  }

  public record View(
      long id,
      long amount,
      long capacity,
      int cells,
      int conflicts,
      int x,
      int z,
      int y,
      int status,
      FluidKey key) {}

  public static View view(FluidBlockEntity be) {
    var g = segment(be);
    if (g != null && g.valid())
      return new View(
          g.id(),
          g.amount(),
          g.capacity(),
          g.size(),
          g.conflicts(),
          g.maxX() - g.minX() + 1,
          g.maxZ() - g.minZ() + 1,
          g.maxY() - g.minY() + 1,
          g.status() == PipelineNetwork.Status.READY && g.conflicts() > 0
              ? 5
              : g.status().ordinal(),
          g.key());
    var c = owned(be);
    return new View(
        0,
        c.amount(),
        c.capacity(),
        be.kind() == FluidKind.PUMP ? 1 : 0,
        0,
        1,
        1,
        1,
        be.kind() == FluidKind.PUMP && be.simulationAvailable() ? 1 : 0,
        c.key());
  }

  public static List<BlockPos> preview(FluidBlockEntity be) {
    var state = STATES.get(be.getLevel());
    var root = segment(be);
    if (state == null || root == null || !root.valid()) return List.of();
    var out = new ArrayList<BlockPos>();
    int visited = 0;
    int cx = be.getBlockPos().getX() >> 4, cz = be.getBlockPos().getZ() >> 4;
    outer:
    for (int x = cx - 2; x <= cx + 2; x++)
      for (int z = cz - 2; z <= cz + 2; z++)
        for (var link : state.chunks.getOrDefault(ChunkPos.asLong(x, z), Set.of())) {
          if (++visited > 2048) break outer;
          if (link.node != null
              && link.node.segment() == root
              && !out.contains(link.be.getBlockPos())) out.add(link.be.getBlockPos());
          if (out.size() >= 128) break outer;
        }
    return List.copyOf(out);
  }

  public static void register(FluidBlockEntity be) {
    var s = STATES.computeIfAbsent(be.getLevel(), State::new);
    var old = s.nodes.get(be.getBlockPos());
    if (old != null) {
      if (old.be == be) return;
      unregister(old.be);
      s = STATES.computeIfAbsent(be.getLevel(), State::new);
    }
    var l = new Link(be);
    s.nodes.put(be.getBlockPos(), l);
    var profile = MachineProfile.of(be.getBlockState());
    var cells =
        profile == null
            ? List.of(be.getBlockPos())
            : profile.cells(be.getBlockPos(), profile.facing(be.getBlockState()));
    int minX = Integer.MAX_VALUE,
        minY = minX,
        minZ = minX,
        maxX = Integer.MIN_VALUE,
        maxY = maxX,
        maxZ = maxX;
    for (var cell : cells) {
      long cp = ChunkPos.asLong(cell.getX() >> 4, cell.getZ() >> 4);
      l.chunks.add(cp);
      s.chunks.computeIfAbsent(cp, k -> new HashSet<>()).add(l);
      minX = Math.min(minX, cell.getX());
      minY = Math.min(minY, cell.getY());
      minZ = Math.min(minZ, cell.getZ());
      maxX = Math.max(maxX, cell.getX());
      maxY = Math.max(maxY, cell.getY());
      maxZ = Math.max(maxZ, cell.getZ());
    }
    if (be.kind() != FluidKind.PUMP) {
      l.node =
          s.network.add(
              new PipelineNetwork.Site(
                  be.getBlockPos().asLong(), minX, maxX, minY, maxY, minZ, maxZ),
              be.localCell());
      s.owners.put(l.node, l);
      s.network.active(l.node, eligible(s, l));
    }
    s.recheck.add(l);
    topologyChanged(be);
    changed(be);
  }

  public static void unregister(FluidBlockEntity be) {
    var s = STATES.get(be.getLevel());
    if (s == null) return;
    var l = s.nodes.get(be.getBlockPos());
    if (l == null || l.be != be) return;
    if (l.node != null) {
      s.network.remove(l.node);
      s.owners.remove(l.node);
    }
    s.nodes.remove(be.getBlockPos());
    l.removed = true;
    s.recheck.remove(l);
    for (long cp : l.chunks) {
      var set = s.chunks.get(cp);
      if (set != null) {
        set.remove(l);
        if (set.isEmpty()) s.chunks.remove(cp);
      }
    }
    for (var neighbor : List.copyOf(l.neighbors)) {
      neighbor.neighbors.remove(l);
      refresh(s, neighbor);
    }
    if (s.nodes.isEmpty()) STATES.remove(be.getLevel());
  }

  public static void topologyChanged(FluidBlockEntity be) {
    var s = STATES.get(be.getLevel());
    if (s == null) return;
    var link = s.nodes.get(be.getBlockPos());
    if (link == null) return;
    link.ports.clear();
    s.recheck.add(link);
    if (link.node != null) s.network.active(link.node, eligible(s, link));
    refresh(s, link);
    for (var direction : Direction.values()) {
      var other = s.nodes.get(be.getBlockPos().relative(direction));
      if (other != null) refresh(s, other);
    }
  }

  private static void refresh(State s, Link link) {
    if (link.removed) return;
    var expected = new HashSet<Link>();
    Object required = null;
    for (var connection : link.be.connections()) {
      var physical = connection.pos().relative(connection.side());
      var endpoint = FluidConnections.endpoint(link.be.getLevel(), physical);
      if (endpoint instanceof factorycraft.blockentity.ThermalBlockEntity thermal) {
        var p = MachineFluidAccess.definition(thermal, physical, connection.side().getOpposite());
        if (p != null && p.input() >= 0 && p.input() == p.output()) {
          Object filter =
              p.input() == 1
                  ? factorycraft.thermal.SteamFluids.TYPE.get()
                  : thermal.kind() == factorycraft.thermal.ThermalKind.BOILER
                      ? Fluids.WATER.getFluidType()
                      : null;
          if (required == null) required = filter;
          else if (filter != null && !required.equals(filter))
            required = PipelineNetwork.REJECT_ALL;
        }
      }
      var other = endpoint instanceof FluidBlockEntity f ? f : null;
      if (other == null
          || other == link.be
          || other.structurePort(physical, connection.side().getOpposite()) == null) continue;
      var next = s.nodes.get(other.getBlockPos());
      if (next != null && !next.removed) expected.add(next);
    }
    if (link.node != null) {
      if (!expected.equals(link.neighbors)) s.network.boundaryChanged(link.node);
      s.network.filter(link.node, required);
    }
    for (var previous : List.copyOf(link.neighbors))
      if (!expected.contains(previous)) {
        if (!previous.removed
            && link.be.kind() != FluidKind.PUMP
            && previous.be.kind() != FluidKind.PUMP) {
          s.network.disconnect(link.node, previous.node);
        }
        link.neighbors.remove(previous);
        previous.neighbors.remove(link);
        wake(s, previous);
      }
    for (var next : expected)
      if (link.neighbors.add(next)) {
        next.neighbors.add(link);
        if (link.be.kind() != FluidKind.PUMP && next.be.kind() != FluidKind.PUMP) {
          s.network.connect(link.node, next.node, expected.size() == 1);
        }
        wake(s, next);
      }

    // Only topology changes rebuild the cached adjacent-pump wake set.
    if (link.node != null && link.node.segment() != null) {
      var info = s.groups.get(link.node.segment());
      if (info != null) {
        info.pumps.removeIf(p -> p.removed);
        for (var next : link.neighbors) if (next.be.kind() == FluidKind.PUMP) info.pumps.add(next);
      }
    }
    wake(s, link);
  }

  public static void machineChanged(factorycraft.blockentity.FactoryBlockEntity be) {
    var s = STATES.get(be.getLevel());
    if (s == null) return;
    var owner = AssemblyAccess.owner(be);
    if (owner == null) return;
    for (var site : owner.assembly().ports()) {
      var neighbor = FluidConnections.fluidDevice(be.getLevel(), site.adjacent());
      if (neighbor != null) {
        var link = s.nodes.get(neighbor.getBlockPos());
        if (link != null) {
          link.ports.clear();
          refresh(s, link);
        }
      }
    }
  }

  private static Link neighbor(State s, FluidBlockEntity be, boolean input) {
    var physical =
        (input ? be.inputNozzle() : be.outputNozzle())
            .relative(input ? be.facing().getOpposite() : be.facing());
    var other = FluidConnections.fluidDevice(be.getLevel(), physical);
    return other == null
            || other.kind() == FluidKind.PUMP && other.facing() != be.facing()
            || other.structurePort(physical, input ? be.facing() : be.facing().getOpposite())
                == null
        ? null
        : s.nodes.get(other.getBlockPos());
  }

  private static void wake(State s, Link link) {
    if (!link.removed && link.be.kind() == FluidKind.PUMP && !link.queued) {
      link.queued = true;
      s.pumps.addLast(link);
    }
  }

  public static void activatePump(FluidBlockEntity be) {
    var s = STATES.get(be.getLevel());
    if (s != null) {
      var link = s.nodes.get(be.getBlockPos());
      if (link != null) wake(s, link);
    }
  }

  public static void changed(FluidBlockEntity be) {
    var s = STATES.get(be.getLevel());
    var l = s == null ? null : s.nodes.get(be.getBlockPos());
    if (l == null || l.be != be) return;
    if (PROFILE) s.rawChanges++;
    if (l.node != null) s.network.contentChanged(l.node);
    mark(s, l);
    for (var neighbor : l.neighbors) wake(s, neighbor);
  }

  public static PipelineNetwork<FluidKey>.Segment segment(FluidBlockEntity be) {
    var s = STATES.get(be.getLevel());
    var l = s == null ? null : s.nodes.get(be.getBlockPos());
    return l == null || l.be != be || l.node == null ? null : l.node.segment();
  }

  public static FluidAggregateIndex.Snapshot<factorycraft.simulation.circuits.SignalKey>
      circuitContents(FluidBlockEntity be) {
    var s = STATES.get(be.getLevel());
    var l = s == null ? null : s.nodes.get(be.getBlockPos());
    var g = segment(be);
    boolean ready = l != null && eligible(s, l) && (l.node == null || g != null && g.valid());
    long amount =
        l == null ? 0 : l.node == null ? be.localCell().amount() : g == null ? 0 : g.amount();
    var k = l == null ? null : l.node == null ? be.localCell().key() : g == null ? null : g.key();
    var values =
        !ready || k == null
            ? Map.<factorycraft.simulation.circuits.SignalKey, Integer>of()
            : Map.of(
                factorycraft.simulation.circuits.SignalKey.fluid(
                    net.minecraft.core.registries.BuiltInRegistries.FLUID
                        .getKey(k.stack(1).getFluid())
                        .toString()),
                (int) amount);
    return new FluidAggregateIndex.Snapshot<>(ready, g == null ? 1 : g.size(), values);
  }

  @SubscribeEvent
  public static void tick(LevelTickEvent.Post event) {
    var level = event.getLevel();
    if (level.isClientSide) return;
    var s = STATES.get(level);
    if (s == null) return;
    long start = System.nanoTime();
    int jobs = Math.min(s.pumps.size(), FactoryConfig.FLUID_WORK.get());
    for (int i = 0; i < jobs && !s.pumps.isEmpty(); i++) {
      var link = s.pumps.removeFirst();
      link.queued = false;
      if (link.removed || !link.be.loaded()) continue;
      if (!link.be.simulationAvailable()) {
        wake(s, link);
        continue;
      }
      boolean worked = pump(s, link);
      if (link.be.loaded() && link.be.getBlockState().getValue(FluidDeviceBlock.ACTIVE) != worked)
        level.setBlock(
            link.be.getBlockPos(),
            link.be.getBlockState().setValue(FluidDeviceBlock.ACTIVE, worked),
            3);
    }
    for (int i = 0, size = Math.min(256, s.recheck.size()); i < size && !s.recheck.isEmpty(); i++) {
      var link = s.recheck.iterator().next();
      s.recheck.remove(link);
      boolean ready = eligible(s, link);
      if (link.node != null) s.network.active(link.node, ready);
      if (!ready && !link.removed) s.recheck.add(link);
      else if (ready) wake(s, link);
    }
    s.network.advance(FactoryConfig.FLUID_WORK.get());
    for (int remaining = s.changedGroups.size(); remaining > 0; remaining--) {
      var group = s.changedGroups.removeFirst();
      var info = s.groups.get(group);
      if (info != null) {
        info.queued = false;
        for (var pump : info.pumps) wake(s, pump);
      }
    }

    for (int i = 0; i < s.dirty.size(); ) {
      var link = s.dirty.get(i);
      if (link.removed
          || Math.floorMod(level.getGameTime() + link.be.getBlockPos().asLong(), 5) == 0) {
        if (!link.removed) link.be.flushUpdate();
        link.dirty = false;
        int last = s.dirty.size() - 1;
        s.dirty.set(i, s.dirty.get(last));
        s.dirty.remove(last);
      } else i++;
    }
    s.nanos += System.nanoTime() - start;
  }

  private static boolean pump(State s, Link link) {
    if (!eligible(s, link) || !powered(s, link)) return false;
    var be = link.be;
    var cell = be.localCell();
    var input = neighbor(s, be, true);
    var output = neighbor(s, be, false);
    var from =
        input == null
            ? null
            : hydraulic(
                input.be, be.inputNozzle().relative(be.facing().getOpposite()), be.facing());
    var to =
        output == null
            ? null
            : hydraulic(
                output.be, be.outputNozzle().relative(be.facing()), be.facing().getOpposite());
    var intake = hydraulic(be, be.inputNozzle(), be.facing().getOpposite());
    var outlet = hydraulic(be, be.outputNozzle(), be.facing());
    int out = to == null ? 0 : outlet.transferTo(to, PUMP_RATE, true);
    int in = from == null ? 0 : Math.min(PUMP_RATE, from.outputLimit(PUMP_RATE));
    if (from != null && (from.key() == null || cell.amount() > out && !cell.accepts(from.key())))
      in = 0;
    boolean source = false;
    if (from == null) {
      var p = be.inputNozzle().relative(be.facing().getOpposite());
      source =
          s.level.hasChunkAt(p)
              && s.level.getBlockState(p).is(Blocks.WATER)
              && s.level.getFluidState(p).isSource();
      if (source) {
        if (link.water == null) link.water = new FluidKey(new FluidStack(Fluids.WATER, 1));
        if (cell.amount() == out || cell.accepts(link.water)) in = PUMP_RATE;
      }
    }
    in = Math.min(in, cell.space() + out);
    if (out == 0 && in > 0) in = intake.inputLimit(source ? link.water : from.key(), in);
    if (out == 0 && in == 0) {
      // Sub-mB pressure credit needs future ticks even when no volume mutation can wake us.
      boolean inputCredit =
          cell.space() > 0
              && ((source && intake.accepts(link.water))
                  || (from != null
                      && from.key() != null
                      && from.amount() > 0
                      && cell.accepts(from.key())
                      && intake.accepts(from.key())));
      boolean outputCredit =
          cell.amount() > 0 && to != null && to.space() > 0 && to.accepts(cell.key());
      if (inputCredit || outputCredit) wake(s, link);
      return false;
    }
    pay(s, link);
    int moved = 0;
    if (out > 0) moved += outlet.transferTo(to, out, false);
    if (!link.removed && be.loaded() && in > 0) {
      if (source) {
        int added = intake.fill(link.water, in, false);
        s.worldWater += added;
        moved += added;
      } else if (from != null) moved += from.transferTo(intake, in, false);
    }
    s.pumped += moved;
    return moved > 0;
  }

  public record Metrics(
      long visits,
      long rebuilds,
      long chunkMarks,
      long rawChanges,
      int pending,
      long finalizeWork,
      long aborts,
      long frozenMembers,
      long localEdits) {}

  public static Metrics metrics(Level level) {
    var s = STATES.get(level);
    return s == null
        ? new Metrics(0, 0, 0, 0, 0, 0, 0, 0, 0)
        : new Metrics(
            s.network.visits(),
            s.network.rebuilds(),
            s.chunkMarks,
            s.rawChanges,
            s.network.pending(),
            s.network.finalizeWork(),
            s.network.aborts(),
            s.network.frozenMembers(),
            s.network.localEdits());
  }

  public static String statistics(Level level) {
    var s = STATES.get(level);
    if (s == null) return "Pipeline segments: 0";
    long amount = 0, capacity = 0;
    int conflicts = 0, longs = 0, over = 0;
    for (var g : s.network.segments()) {
      amount += g.amount();
      capacity += g.capacity();
      conflicts += g.conflicts();
      if (g.status() == PipelineNetwork.Status.TOO_LONG) longs++;
      if (g.amount() > g.capacity()) over++;
    }
    int pumpCount = 0;
    long chambers = 0, quarantined = 0;
    for (var l : s.nodes.values()) {
      if (l.node == null) {
        pumpCount++;
        chambers += l.be.localCell().amount();
      } else if (l.node.segment() == null) quarantined += l.be.localCell().amount();
    }
    return "Fluid nodes: "
        + s.nodes.size()
        + ", pumps: "
        + pumpCount
        + " (chambers "
        + chambers
        + "/"
        + ((long) pumpCount * FluidKind.PUMP.capacity)
        + " mB), quarantined physical volume: "
        + quarantined
        + " mB"
        + ", segments: "
        + s.network.segments().size()
        + ", active passive edges: "
        + s.network.activeEdges()
        + ", physical passive edges: "
        + s.network.edges()
        + ", conflicts: "
        + conflicts
        + ", overfull: "
        + over
        + ", too long: "
        + longs
        + ", volume: "
        + amount
        + "/"
        + capacity
        + " mB, queued rebuild owners: "
        + s.network.pending()
        + ", rebuilds: "
        + s.network.rebuilds()
        + ", local edits: "
        + s.network.localEdits()
        + ", finalize units: "
        + s.network.finalizeWork()
        + ", aborted builds: "
        + s.network.aborts()
        + ", topology visits: "
        + s.network.visits()
        + ", queued pumps: "
        + s.pumps.size()
        + ", pump transfers: "
        + s.pumped
        + ", generated water: "
        + s.worldWater
        + ", CPU: "
        + String.format(Locale.ROOT, "%.3f ms", s.nanos / 1e6);
  }

  @SubscribeEvent
  public static void unload(LevelEvent.Unload event) {
    var s = STATES.remove(event.getLevel());
    if (s == null) return;
    for (var link : s.nodes.values()) {
      link.removed = true;
      if (link.node != null) s.network.remove(link.node);
      link.be.onChunkUnloaded();
    }
    s.nodes.clear();
    s.dirty.clear();
    s.pumps.clear();
  }

  @SubscribeEvent
  public static void tickets(
      net.neoforged.neoforge.event.level.ChunkTicketLevelUpdatedEvent event) {
    var s = STATES.get(event.getLevel());
    if (s == null) return;
    for (var link : List.copyOf(s.chunks.getOrDefault(event.getChunkPos(), Set.of()))) {
      if (!net.minecraft.server.level.ChunkLevel.isBlockTicking(event.getNewTicketLevel()))
        if (link.node != null) s.network.active(link.node, false);
      s.recheck.add(link);
    }
  }

  @SubscribeEvent
  public static void chunkUnloading(ChunkEvent.Unload event) {
    if (!(event.getChunk() instanceof LevelChunk chunk) || chunk.getLevel().isClientSide) return;
    var state = STATES.get(chunk.getLevel());
    if (state != null)
      for (var link : List.copyOf(state.chunks.getOrDefault(chunk.getPos().toLong(), Set.of())))
        if (link.node != null) {
          state.network.active(link.node, false);
          state.recheck.add(link);
        }
    for (var be : chunk.getBlockEntities().values())
      if (be instanceof FluidBlockEntity fluid) {
        // ChunkMap fires this before serializing an outgoing chunk, earlier than BE unload
        // callbacks.
        chunk.setUnsaved(true);
        fluid.onChunkUnloaded();
      }
  }

  private FluidSimulation() {}
}
