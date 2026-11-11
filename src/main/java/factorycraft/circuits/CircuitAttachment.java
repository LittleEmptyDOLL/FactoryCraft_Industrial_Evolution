package factorycraft.circuits;

import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.multiblock.*;
import factorycraft.simulation.circuits.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.Vec3;

/** One canonical owner and logical state; never an extra BlockEntity or a resource inventory. */
public final class CircuitAttachment {
  private final BlockEntity owner;
  private UUID identity = UUID.randomUUID();
  public final List<CircuitLink> links;
  private CircuitSettings settings;
  private SignalProgram program;
  private CompoundTag invalidConfig;
  private List<BlockPos> cachedCells;
  private Direction cachedFacing;
  private Object cachedRoute;
  public boolean configured, legacyMode, active, inputReady, suspended, overCapacity;
  public boolean permitted = true, sensorReady = true;
  public SignalSet input = SignalSet.EMPTY,
      output = SignalSet.EMPTY,
      pendingPulse = SignalSet.EMPTY;
  public SignalProgram.Inputs inputs = SignalProgram.Inputs.single(SignalSet.EMPTY);
  public Set<String> filterIds = Set.of();
  public int handLimit;
  public final long randomSeed;

  public CircuitAttachment(BlockEntity owner) {
    this.owner = owner;
    links = owner instanceof CircuitNodeBlockEntity node ? node.links : new ArrayList<>();
    settings =
        CircuitSettings.defaults(
            owner instanceof ChestBlockEntity || owner instanceof FluidBlockEntity);
    program = SignalProgram.defaults(kind());
    legacyMode =
        owner instanceof CircuitNodeBlockEntity n
            && (n.kind() == FactoryKind.CIRCUIT_READER
                || n.kind() == FactoryKind.CIRCUIT_CONTROLLER);
    randomSeed = owner.getBlockPos().asLong() ^ 0x675842d9e513L;
  }

  public BlockEntity owner() {
    return owner;
  }

  public CircuitNodeBlockEntity legacy() {
    return owner instanceof CircuitNodeBlockEntity n ? n : null;
  }

  public Level level() {
    return owner.getLevel();
  }

  public BlockPos pos() {
    return owner.getBlockPos();
  }

  public UUID identity() {
    return owner instanceof CircuitNodeBlockEntity n ? n.assembly().identity() : identity;
  }

  public SignalProgram.Kind kind() {
    if (owner instanceof CircuitNodeBlockEntity n)
      return switch (n.kind()) {
        case ARITHMETIC -> SignalProgram.Kind.ARITHMETIC;
        case DECIDER -> SignalProgram.Kind.DECIDER;
        case SELECTOR -> SignalProgram.Kind.SELECTOR;
        default -> SignalProgram.Kind.CONSTANT;
      };
    return SignalProgram.Kind.CONSTANT;
  }

  public boolean combinator() {
    return owner instanceof CircuitNodeBlockEntity n
        && n.kind() != FactoryKind.CIRCUIT_READER
        && n.kind() != FactoryKind.CIRCUIT_CONTROLLER;
  }

  public CircuitSettings settings() {
    return settings;
  }

  public SignalProgram program() {
    return program;
  }

  public boolean invalid() {
    return invalidConfig != null;
  }

  public int ports() {
    return owner instanceof CircuitNodeBlockEntity n ? n.ports() : 1;
  }

  public int outputPort() {
    return ports() == 2 ? 1 : 0;
  }

  public Direction facing() {
    var state = owner.getBlockState();
    if (state.hasProperty(FactoryBlock.FACING)) return state.getValue(FactoryBlock.FACING);
    if (state.hasProperty(FluidDeviceBlock.FACING)) return state.getValue(FluidDeviceBlock.FACING);
    if (state.hasProperty(ThermalBlock.FACING)) return state.getValue(ThermalBlock.FACING);
    return Direction.NORTH;
  }

  public BlockPos portPosition(int port) {
    if (port < 0 || port >= ports()) throw new IllegalArgumentException("Invalid circuit port");
    if (owner instanceof CircuitNodeBlockEntity n) return n.portPosition(port);
    var profile = MachineProfile.of(owner.getBlockState());
    return profile == null
        ? pos()
        : profile.offset(
            pos(), profile.facing(owner.getBlockState()), profile.layout.width() / 2, 0, 0);
  }

  public Vec3 point(int port, int color) {
    var p = portPosition(port);
    double y =
        owner instanceof PoleBlockEntity pole
            ? pos().getY() + pole.spec().height - .45
            : owner instanceof CircuitNodeBlockEntity
                ? p.getY() + .515
                : owner instanceof BeltBlockEntity ? p.getY() + .22 : p.getY() + .72;
    var f = facing();
    return new Vec3(
        p.getX() + .5 + f.getStepX() * (owner instanceof CircuitNodeBlockEntity ? .37 : .505),
        y + color * .035,
        p.getZ() + .5 + f.getStepZ() * (owner instanceof CircuitNodeBlockEntity ? .37 : .505));
  }

  public int portAt(BlockPos physical) {
    if (owner instanceof CircuitNodeBlockEntity n) return n.portAt(physical);
    if (physical.equals(pos())) return 0;
    if (owner instanceof BeltBlockEntity belt)
      return belt.physicalCells().contains(physical) ? 0 : -1;
    var assembly = AssemblyAccess.owner(owner);
    if (assembly == null || !assembly.assembly().positions().contains(physical)) return -1;
    var profile = assembly.assembly().profile();
    var f = profile.facing(owner.getBlockState());
    for (int x = 0; x < profile.layout.width(); x++)
      for (int z = 0; z < profile.layout.depth(); z++)
        for (int y = 0; y < profile.layout.height(); y++)
          if (profile.offset(pos(), f, x, y, z).equals(physical))
            return x == 0
                    || z == 0
                    || x == profile.layout.width() - 1
                    || z == profile.layout.depth() - 1
                    || y == profile.layout.height() - 1
                ? 0
                : -1;
    return -1;
  }

  public List<BlockPos> cells() {
    var facing = facing();
    var route = owner instanceof BeltBlockEntity belt ? belt.route() : null;
    if (cachedCells != null && cachedFacing == facing && cachedRoute == route) return cachedCells;
    cachedFacing = facing;
    cachedRoute = route;
    var assembly = AssemblyAccess.owner(owner);
    cachedCells =
        owner instanceof BeltBlockEntity belt
            ? List.copyOf(belt.physicalCells())
            : assembly == null
                ? List.of(pos())
                : assembly
                    .assembly()
                    .profile()
                    .cells(pos(), assembly.assembly().profile().facing(owner.getBlockState()));
    return cachedCells;
  }

  public boolean available() {
    if (suspended
        || owner.isRemoved()
        || !(level() instanceof ServerLevel server)
        || !server.tickRateManager().runsNormally()) return false;
    var chunk = server.getChunkSource().getChunkNow(pos().getX() >> 4, pos().getZ() >> 4);
    if (chunk == null || chunk.getBlockEntity(pos()) != owner) return false;
    if (owner instanceof CircuitNodeBlockEntity n) return n.available();
    if (owner instanceof FactoryBlockEntity f) {
      if (f instanceof BeltBlockEntity b
              && (!b.loaded()
                  || !b.structureReady()
                  || !factorycraft.simulation.BeltSimulation.tickable(b))
          || f instanceof InserterBlockEntity i && !i.ready()
          || f instanceof MachineBlockEntity m && !m.loaded()
          || f instanceof FluidBlockEntity p && !p.simulationAvailable()
          || f instanceof ThermalBlockEntity t && !t.available()
          || f instanceof LabBlockEntity l && !l.ready()
          || f instanceof PoleBlockEntity p && !p.available()) return false;
      if (!AssemblyAccess.ready(f)) return false;
    }
    for (var p : cells())
      if (server.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4) == null
          || !server.shouldTickBlocksAt(
              net.minecraft.world.level.ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4)))
        return false;
    return true;
  }

  public boolean configure(CircuitSettings next, SignalProgram nextProgram) {
    if (!available()
        || nextProgram.kind() != kind()
        || !CircuitDevices.validSettings(this, next)
        || nextProgram.kind() == SignalProgram.Kind.SELECTOR && !nextProgram.selector().available())
      return false;
    settings = next;
    program = nextProgram;
    legacyMode = false;
    invalidConfig = null;
    configured = true;
    changed();
    CircuitSimulation.programChanged(this);
    return true;
  }

  public void useLegacy() {
    if (legacy() == null) return;
    legacyMode = true;
    configured = true;
    program = SignalProgramData.legacy(legacy());
    changed();
    CircuitSimulation.programChanged(this);
  }

  public void changed() {
    persistent();
    if (owner instanceof FactoryBlockEntity f && available()) f.sync();
    CircuitNetwork.changed(this);
  }

  public void persistent() {
    if (owner instanceof FactoryBlockEntity f) f.markCircuitStatePersistent();
    else if (level() instanceof ServerLevel s) CircuitContainers.dirty(s);
  }

  public CompoundTag save() {
    var tag = new CompoundTag();
    tag.putInt("Schema", 2);
    if (identity() != null) tag.putUUID("Identity", identity());
    tag.putBoolean("Legacy", legacyMode);
    tag.put("Settings", settings.save());
    tag.put(
        "Program", invalidConfig == null ? SignalProgramData.save(program) : invalidConfig.copy());
    tag.put("Output", SignalProgramData.signals(output));
    tag.put("Pulse", SignalProgramData.signals(pendingPulse));
    if (!(owner instanceof CircuitNodeBlockEntity)) {
      var list = new ListTag();
      for (var link : links) list.add(link.save());
      tag.put("Links", list);
    }
    return tag;
  }

  public void load(CompoundTag tag) {
    configured = true;
    if (tag.hasUUID("Identity")) identity = tag.getUUID("Identity");
    legacyMode = tag.getBoolean("Legacy");
    try {
      if (tag.getInt("Schema") != 2)
        throw new IllegalArgumentException("Unknown direct-circuit schema");
      settings = CircuitSettings.load(tag.getCompound("Settings"));
      program = SignalProgramData.load(tag.getCompound("Program"));
      if (program.kind() != kind())
        throw new IllegalArgumentException("Changed circuit device kind");
      if (!CircuitDevices.validSettings(this, settings))
        throw new IllegalArgumentException("Unsupported device settings");
      output = SignalProgramData.signals(tag.getList("Output", 10));
      pendingPulse = SignalProgramData.signals(tag.getList("Pulse", 10));
      invalidConfig = null;
    } catch (RuntimeException bad) {
      invalidConfig = tag.getCompound("Program").copy();
      permitted = false;
    }
    if (!(owner instanceof CircuitNodeBlockEntity)) {
      links.clear();
      for (var raw : tag.getList("Links", 10)) {
        var link = CircuitLink.load((CompoundTag) raw);
        if (link != null && links.size() < WireColors.MAX_LINKS && !links.contains(link))
          links.add(link);
      }
    }
    inputReady = false;
    permitted = !settings.gate() && !invalid();
  }

  public static void portable(CompoundTag parent) {
    if (!parent.contains("DirectCircuit")) return;
    var tag = parent.getCompound("DirectCircuit");
    tag.remove("Identity");
    tag.remove("Links");
    tag.remove("Output");
    tag.remove("Pulse");
  }
}
