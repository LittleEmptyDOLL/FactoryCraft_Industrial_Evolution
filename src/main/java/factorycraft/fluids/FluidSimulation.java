package factorycraft.fluids;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.FluidBlockEntity;
import factorycraft.blocks.FluidDeviceBlock;
import factorycraft.config.FactoryConfig;
import factorycraft.simulation.fluids.FluidTransport;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Loaded-only adjacency. Empty/balanced edges and blocked/unpowered pumps sleep until relevant
 * changes.
 */
@EventBusSubscriber(modid = FactoryCraft.ID)
public final class FluidSimulation {
  public static final int PUMP_RATE = 100, PUMP_FE = 20, EDGE_RATE = 100;
  private static final Map<Level, State> STATES = new IdentityHashMap<>();

  private static final class Link {
    final FluidBlockEntity be;
    final FluidTransport.Node<FluidKey> node;
    final Set<Link> neighbors = new HashSet<>();
    boolean queued, dirty, removed;
    final Set<Long> chunks = new HashSet<>();
    FluidKey water;

    Link(FluidBlockEntity be, FluidTransport.Node<FluidKey> node) {
      this.be = be;
      this.node = node;
    }
  }

  private static final class State {
    final FluidTransport<FluidKey> transport = new FluidTransport<>();
    final factorycraft.simulation.fluids.FluidAggregateIndex<
            Link, factorycraft.simulation.circuits.SignalKey>
        circuits = new factorycraft.simulation.fluids.FluidAggregateIndex<>();
    final Map<BlockPos, Link> nodes = new HashMap<>();
    final Map<Long, Set<Link>> chunks = new HashMap<>();
    final LinkedHashSet<Link> recheck = new LinkedHashSet<>();
    final ArrayDeque<Link> pumps = new ArrayDeque<>();
    final ArrayList<Link> dirty = new ArrayList<>();
    long nanos, pumped;
  }

  public static void register(FluidBlockEntity be) {
    var s = STATES.computeIfAbsent(be.getLevel(), level -> new State());
    if (s.nodes.containsKey(be.getBlockPos()))
      throw new IllegalStateException("Duplicate fluid node");
    var created = new Link(be, s.transport.add(be.cell(), be::simulationAvailable));
    s.nodes.put(be.getBlockPos(), created);
    s.circuits.add(created);
    var profile = factorycraft.multiblock.MachineProfile.of(be.getBlockState());
    var cells =
        profile == null
            ? List.of(be.getBlockPos())
            : profile.cells(be.getBlockPos(), profile.facing(be.getBlockState()));
    for (var cell : cells) {
      long chunk = net.minecraft.world.level.ChunkPos.asLong(cell.getX() >> 4, cell.getZ() >> 4);
      created.chunks.add(chunk);
      s.chunks.computeIfAbsent(chunk, k -> new HashSet<>()).add(created);
    }
    s.recheck.add(created);
    topologyChanged(be);
    changed(be);
  }

  public static void unregister(FluidBlockEntity be) {
    var s = STATES.get(be.getLevel());
    if (s == null) return;
    var link = s.nodes.remove(be.getBlockPos());
    if (link == null) return;
    link.removed = true;
    s.recheck.remove(link);
    for (long chunk : link.chunks) {
      var set = s.chunks.get(chunk);
      if (set != null) {
        set.remove(link);
        if (set.isEmpty()) s.chunks.remove(chunk);
      }
    }
    s.transport.remove(link.node);
    s.circuits.remove(link);
    for (var neighbor : List.copyOf(link.neighbors)) refresh(s, neighbor);
    if (s.nodes.isEmpty()) STATES.remove(be.getLevel());
  }

  public static void topologyChanged(FluidBlockEntity be) {
    var s = STATES.get(be.getLevel());
    if (s == null) return;
    var link = s.nodes.get(be.getBlockPos());
    if (link == null) return;
    s.recheck.add(link);
    s.circuits.active(link, be.simulationAvailable());
    refresh(s, link);
    for (var direction : Direction.values()) {
      var other = s.nodes.get(be.getBlockPos().relative(direction));
      if (other != null) refresh(s, other);
    }
  }

  private static void refresh(State s, Link link) {
    if (link.removed) return;
    var expected = new HashSet<Link>();
    for (var connection : link.be.connections()) {
      var physical = connection.pos().relative(connection.side());
      var other = FluidConnections.fluidDevice(link.be.getLevel(), physical);
      if (other == null
          || other == link.be
          || other.structurePort(physical, connection.side().getOpposite()) == null) continue;
      var next = s.nodes.get(other.getBlockPos());
      if (next != null && !next.removed) expected.add(next);
    }
    for (var previous : List.copyOf(link.neighbors))
      if (!expected.contains(previous)) {
        if (!previous.removed
            && link.be.kind() != FluidKind.PUMP
            && previous.be.kind() != FluidKind.PUMP) {
          s.transport.disconnect(link.node, previous.node);
          s.circuits.disconnect(link, previous);
        }
        link.neighbors.remove(previous);
        previous.neighbors.remove(link);
        wake(s, previous);
      }
    for (var next : expected)
      if (link.neighbors.add(next)) {
        next.neighbors.add(link);
        if (link.be.kind() != FluidKind.PUMP && next.be.kind() != FluidKind.PUMP) {
          s.transport.connect(link.node, next.node);
          s.circuits.connect(link, next);
        }
        wake(s, next);
      }
    s.transport.wake(link.node);
    wake(s, link);
  }

  private static Link neighbor(State s, FluidBlockEntity be, boolean input) {
    var physical =
        (input ? be.inputNozzle() : be.outputNozzle())
            .relative(input ? be.facing().getOpposite() : be.facing());
    var other = FluidConnections.fluidDevice(be.getLevel(), physical);
    return other == null
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
    if (s == null) return;
    var link = s.nodes.get(be.getBlockPos());
    if (link == null) return;
    var cell = be.cell();
    s.circuits.update(
        link,
        cell.amount() == 0
            ? java.util.Map.of()
            : java.util.Map.of(
                factorycraft.simulation.circuits.SignalKey.fluid(
                    net.minecraft.core.registries.BuiltInRegistries.FLUID
                        .getKey(cell.key().stack(1).getFluid())
                        .toString()),
                cell.amount()));
    s.transport.wake(link.node);
    if (!link.dirty) {
      link.dirty = true;
      s.dirty.add(link);
    }
    wake(s, link);
    for (var neighbor : link.neighbors) if (neighbor != null) wake(s, neighbor);
  }

  public static factorycraft.simulation.fluids.FluidAggregateIndex.Snapshot<
          factorycraft.simulation.circuits.SignalKey>
      circuitContents(FluidBlockEntity be) {
    var state = STATES.get(be.getLevel());
    var link = state == null ? null : state.nodes.get(be.getBlockPos());
    return link == null
        ? new factorycraft.simulation.fluids.FluidAggregateIndex.Snapshot<>(false, 0, Map.of())
        : state.circuits.snapshot(link);
  }

  @SubscribeEvent
  public static void tick(LevelTickEvent.Post event) {
    var level = event.getLevel();
    if (level.isClientSide) return;
    var s = STATES.get(level);
    if (s == null) return;
    long start = System.nanoTime();
    int jobs = Math.min(s.pumps.size(), FactoryConfig.FLUID_WORK.get());
    for (int i = 0; i < jobs; i++) {
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
    for (int i = 0, size = Math.min(256, s.recheck.size()); i < size; i++) {
      var link = s.recheck.iterator().next();
      s.recheck.remove(link);
      boolean ready = link.be.simulationAvailable();
      s.circuits.active(link, ready);
      if (!ready && !link.removed) s.recheck.add(link);
    }
    s.transport.tick(FactoryConfig.FLUID_WORK.get(), EDGE_RATE);
    s.circuits.advance(FactoryConfig.CIRCUIT_FLUID_WORK.get());
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
    if (!factorycraft.circuits.CircuitBinding.allowed(link.be)) return false;
    var be = link.be;
    var cell = be.cell();
    if (be.power.getEnergyStored() < PUMP_FE) return false;
    var facing = be.facing();
    var input = neighbor(s, be, true);
    var output = neighbor(s, be, false);
    if (input != null && !input.be.simulationAvailable()) input = null;
    if (output != null && !output.be.simulationAvailable()) output = null;
    if (input != null && input.be.kind() == FluidKind.PUMP && input.be.facing() != facing)
      input = null;
    if (output != null && output.be.kind() == FluidKind.PUMP && output.be.facing() != facing)
      output = null;
    int out = output == null ? 0 : cell.transferTo(output.be.cell(), PUMP_RATE, true);
    int in = input == null ? 0 : input.be.cell().transferTo(cell, PUMP_RATE, true);
    boolean source = false;
    if (input == null && cell.space() > 0) {
      var pos = be.inputNozzle().relative(facing.getOpposite());
      var level = be.getLevel();
      source =
          level.hasChunkAt(pos)
              && level.getBlockState(pos).is(Blocks.WATER)
              && level.getFluidState(pos).isSource();
      if (source) {
        if (link.water == null) link.water = new FluidKey(new FluidStack(Fluids.WATER, 1));
        in = cell.fill(link.water, PUMP_RATE, true);
      }
    }
    if (in == 0 && out == 0 || !be.power.spend(PUMP_FE)) return false;
    int moved = 0;
    if (in > 0) {
      if (source) moved += cell.fill(link.water, in, false);
      else if (input != null && !input.removed)
        moved += input.be.cell().transferTo(cell, in, false);
    }
    // A source container's neighbor callback may have removed the pump after the first commit.
    if (be.loaded() && out > 0 && output != null && !output.removed)
      moved += cell.transferTo(output.be.cell(), out, false);
    s.pumped += moved;
    return moved > 0;
  }

  public static String statistics(Level level) {
    var s = STATES.get(level);
    return s == null
        ? "Fluid nodes: 0"
        : "Fluid nodes: "
            + s.nodes.size()
            + ", passive segments: "
            + s.transport.components()
            + ", passive edges: "
            + s.transport.edges()
            + ", pending edges: "
            + s.transport.pendingEdges()
            + ", queued pumps: "
            + s.pumps.size()
            + ", edge visits: "
            + s.transport.visits()
            + ", transferred: "
            + s.transport.volumeMoved()
            + " mB, pump chamber transfers: "
            + s.pumped
            + " mB, CPU: "
            + String.format(Locale.ROOT, "%.3f ms", s.nanos / 1e6);
  }

  @SubscribeEvent
  public static void unload(LevelEvent.Unload event) {
    var s = STATES.remove(event.getLevel());
    if (s == null) return;
    for (var link : s.nodes.values()) {
      link.removed = true;
      s.transport.remove(link.node);
      s.circuits.remove(link);
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
        s.circuits.active(link, false);
      s.recheck.add(link);
    }
  }

  @SubscribeEvent
  public static void chunkUnloading(ChunkEvent.Unload event) {
    if (!(event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk)
        || chunk.getLevel().isClientSide) return;
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
