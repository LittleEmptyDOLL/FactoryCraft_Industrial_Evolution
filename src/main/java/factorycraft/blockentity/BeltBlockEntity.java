package factorycraft.blockentity;

import factorycraft.belts.*;
import factorycraft.blocks.*;
import factorycraft.fluids.FluidConnections;
import factorycraft.registry.Content;
import factorycraft.simulation.BeltSimulation;
import factorycraft.simulation.belts.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/** One controller owns either one two-lane belt or both physical splitter tracks. */
public final class BeltBlockEntity extends FactoryBlockEntity
    implements factorycraft.multiblock.AssemblyOwner {
  private final factorycraft.multiblock.IndustrialAssembly assembly;
  private final List<BeltLane<ItemStack>> lanes;
  private final List<BeltTransport.Node<ItemStack>> nodes = new ArrayList<>(4);
  private final boolean[] alternate;
  private final IItemHandler[] handlers;
  private BeltTransport<ItemStack> transport;
  private boolean queuedForSync, contentsChanged;
  public boolean simulationLoaded;
  private long snapshotTick;
  private factorycraft.logistics.SplitterSettings splitterSettings =
      factorycraft.logistics.SplitterSettings.DEFAULT;
  private final int[] committedOutputs = new int[4];
  public factorycraft.circuits.CircuitBinding circuitBinding;
  private boolean circuitRunning = true;

  public boolean circuitRunning() {
    return circuitRunning;
  }

  public void refreshCircuitControl() {
    if (level == null || level.isClientSide) return;
    boolean enabled = factorycraft.circuits.CircuitBinding.allowed(this);
    if (enabled == circuitRunning) return;
    circuitRunning = enabled;
    if (transport != null) for (var n : nodes) transport.setEnabled(n, enabled);
    // Ticket callbacks can run after a holder has gone away. Persist locally, but never
    // trigger collision/neighbour queries or block packets for a demoted physical structure.
    if (!BeltSimulation.tickable(this) || !FluidConnections.canonical(this)) {
      var server = (net.minecraft.server.level.ServerLevel) level;
      var chunk =
          server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
      if (chunk != null) chunk.setUnsaved(true);
      return;
    }
    sync();
  }

  private void circuitArrival(ItemStack stack) {
    factorycraft.circuits.CircuitSimulation.pulse(this, stack);
  }

  public void updateCircuitPulseWatch() {
    if (transport == null) return;
    var logic = circuit(false);
    for (var node : nodes)
      transport.arrivalListener(
          node,
          logic != null && logic.settings().pulse() && logic.settings().contents()
              ? this::circuitArrival
              : null);
  }

  public factorycraft.logistics.SplitterSettings splitterSettings() {
    return splitterSettings;
  }

  public boolean configureSplitter(factorycraft.logistics.SplitterSettings settings) {
    if (!kind().splitter() || !loaded() || !structureReady() || !FluidConnections.canonical(this))
      return false;
    splitterSettings = Objects.requireNonNull(settings);
    applySplitterSettings();
    sync();
    return true;
  }

  private void applySplitterSettings() {
    if (transport == null || !kind().splitter()) return;
    var settings = splitterSettings;
    var policy =
        settings.equals(factorycraft.logistics.SplitterSettings.DEFAULT)
            ? null
            : new SplitterPolicy<ItemStack>(
                settings.output(), settings.filter() == null ? null : settings::matches);
    for (int i = 0; i < nodes.size(); i++)
      transport.configure(
          node(i), policy, settings.input() != 0 && i / 2 == settings.input() - 1 ? 1 : 0);
  }

  private BeltRoute route = BeltRoute.FLAT, desiredRoute;
  private UUID transportIdentity = UUID.randomUUID();
  private int formMode;
  private boolean undergroundOutput, removing, refreshing;
  private BeltBlockEntity paired;

  public FactoryKind kind() {
    return ((FactoryBlock) getBlockState().getBlock()).kind;
  }

  public Direction facing() {
    return getBlockState().getValue(FactoryBlock.FACING);
  }

  public BeltRoute route() {
    return route;
  }

  public UUID transportIdentity() {
    return transportIdentity;
  }

  public boolean undergroundOutput() {
    return undergroundOutput;
  }

  public boolean special() {
    return kind().lift() || route.heightDelta() != 0;
  }

  public Direction outputFacing() {
    var d = facing();
    for (int i = 0; i < route.exitTurn; i++) d = d.getClockWise();
    return kind().lift() ? d : facing();
  }

  public BlockPos outputPosition() {
    return worldPosition
        .above(route.heightDelta())
        .relative(facing(), undergroundOutput ? 0 : route.reach());
  }

  public List<BlockPos> physicalCells() {
    return tracks() == 2
        ? List.of(trackPosition(0), trackPosition(1))
        : TransportPlan.cells(worldPosition, route);
  }

  public BeltModelData.Cell modelCell(BlockPos physical) {
    return new BeltModelData.Cell(
        route,
        facing(),
        kind().beltTier,
        physical.getY() - worldPosition.getY(),
        undergroundOutput);
  }

  @Override
  public net.neoforged.neoforge.client.model.data.ModelData getModelData() {
    return net.neoforged.neoforge.client.model.data.ModelData.builder()
        .with(BeltModelData.CELL, modelCell(worldPosition))
        .build();
  }

  public boolean initializeRoute(BeltRoute next) {
    if (!resize(next)) return false;
    desiredRoute = null;
    sync();
    return true;
  }

  private boolean resize(BeltRoute next) {
    for (int i = 0; i < laneCount(); i++) {
      int length = next.length(i % 2);
      if (!lane(i).canResize(length)
          || transport != null && !nodes.isEmpty() && !transport.canResize(node(i), length))
        return false;
    }
    for (int i = 0; i < laneCount(); i++) {
      lane(i).resize(next.length(i % 2));
      if (transport != null && !nodes.isEmpty()) transport.geometryChanged(node(i));
    }
    route = next;
    return true;
  }

  public void notifyGeometry() {
    requestModelDataUpdate();
    if (level != null) {
      level.invalidateCapabilities(worldPosition);
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
  }

  private boolean sameRoute(BeltRoute a, BeltRoute b) {
    return a.shape == b.shape && a.span == b.span && a.exitTurn == b.exitTurn;
  }

  private void clearParts() {
    boolean old = level.restoringBlockSnapshots;
    level.restoringBlockSnapshots = true;
    try {
      for (var p : TransportPlan.cells(worldPosition, route))
        if (!p.equals(worldPosition)
            && FluidConnections.at(level, p) instanceof TransportPartBlockEntity part
            && part.boundTo(worldPosition, transportIdentity)) level.removeBlock(p, false);
    } finally {
      level.restoringBlockSnapshots = old;
    }
  }

  private boolean changeRoute(BeltRoute next) {
    if (sameRoute(route, next)) {
      desiredRoute = null;
      return true;
    }
    for (var p : TransportPlan.cells(worldPosition, next))
      if (!p.equals(worldPosition)) {
        var be = FluidConnections.at(level, p);
        if (be instanceof TransportPartBlockEntity part
            && part.boundTo(worldPosition, transportIdentity)) continue;
        if (!level.hasChunkAt(p)
            || level.isOutsideBuildHeight(p)
            || !level.getWorldBorder().isWithinBounds(p)
            || be != null
            || !level.getBlockState(p).isAir()
            || !level.isUnobstructed(
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),
                p,
                net.minecraft.world.phys.shapes.CollisionContext.empty())) return false;
      }
    for (int i = 0; i < laneCount(); i++)
      if (!lane(i).canResize(next.length(i % 2))
          || transport != null
              && !nodes.isEmpty()
              && !transport.canResize(node(i), next.length(i % 2))) return false;
    clearParts();
    resize(next);
    TransportPlan.buildParts(this);
    desiredRoute = null;
    notifyGeometry();
    sync();
    return true;
  }

  public int formMode() {
    return formMode;
  }

  public boolean correctForm() {
    if (!kind().ordinaryBelt()) return false;
    formMode = (formMode + 1) % 4;
    desiredRoute =
        switch (formMode) {
          case 1 -> BeltRoute.FLAT;
          case 2 -> new BeltRoute(BeltRoute.Shape.UP, 0, 0);
          case 3 -> new BeltRoute(BeltRoute.Shape.DOWN, 0, 0);
          default -> TransportPlan.automatic(level, worldPosition, facing());
        };
    boolean ok = changeRoute(desiredRoute);
    BeltSimulation.topologyChanged(this);
    return ok;
  }

  public boolean rotateExit() {
    if (!kind().lift()) return false;
    var next = new BeltRoute(BeltRoute.Shape.LIFT, route.span, (route.exitTurn + 1) % 4);
    if (hasItems()) return false;
    boolean ok = changeRoute(next);
    BeltSimulation.topologyChanged(this);
    return ok;
  }

  public boolean toggleUnderground() {
    if (!kind().underground() || hasItems() || paired != null && paired.hasItems()) return false;
    var currentTransport = transport;
    unbindTransport();
    undergroundOutput = !undergroundOutput;
    if (currentTransport != null) bindTransport(currentTransport);
    BeltSimulation.topologyChanged(this);
    sync();
    return true;
  }

  public void chooseUndergroundRole() {
    if (!kind().underground() || undergroundOutput) return;
    for (int distance = 1; distance <= kind().beltTier * 2 + 3; distance++) {
      var p = worldPosition.relative(facing().getOpposite(), distance);
      if (!level.hasChunkAt(p)) return;
      var be = FluidConnections.at(level, p);
      if (be instanceof BeltBlockEntity other
          && other.kind() == kind()
          && other.facing() == facing()) {
        if (!other.undergroundOutput) toggleUnderground();
        return;
      }
    }
  }

  private BeltBlockEntity findPair() {
    var d = undergroundOutput ? facing().getOpposite() : facing();
    int max = kind().beltTier * 2 + 3;
    for (int n = 1; n <= max; n++) {
      var p = worldPosition.relative(d, n);
      if (!level.hasChunkAt(p)) return null;
      var b = FluidConnections.at(level, p);
      if (b instanceof BeltBlockEntity other
          && other.kind() == kind()
          && other.facing() == facing())
        return other.undergroundOutput != undergroundOutput && other.loaded() ? other : null;
    }
    return null;
  }

  public BeltBlockEntity paired() {
    return paired;
  }

  public boolean canSample(BlockPos physical, int slot) {
    if (kind().underground() && undergroundOutput)
      return paired != null && paired.canSample(worldPosition, slot);
    int track = trackAt(physical);
    if (track < 0) return false;
    int index = track * 2 + (slot / 4) % 2, k = slot % 4;
    var l = lane(index);
    if (k >= l.carriers().size()) return false;
    int u = l.carriers().get(k).positionUnits();
    return physical.equals(outputPosition()) ? u >= l.length() - 24 : u >= 72 && u <= 96;
  }

  public ItemStack portable() {
    var stack = new ItemStack(getBlockState().getBlock());
    saveToItem(stack, level.registryAccess());
    var data = stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
    if (data != null) {
      var tag = data.copyTag();
      tag.remove("TransportIdentity");
      tag.remove("Assembly");
      stack.set(
          net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,
          net.minecraft.world.item.component.CustomData.of(tag));
    }
    return stack;
  }

  public void removeTransport(boolean drop) {
    if (removing || level == null || level.isClientSide) return;
    removing = true;
    var stack = drop ? portable() : ItemStack.EMPTY;
    clearParts();
    for (var lane : lanes) lane.clear();
    if (FluidConnections.at(level, worldPosition) == this) level.removeBlock(worldPosition, false);
    if (!stack.isEmpty())
      net.minecraft.world.Containers.dropItemStack(
          level,
          worldPosition.getX() + .5,
          worldPosition.getY() + .5,
          worldPosition.getZ() + .5,
          stack);
  }

  public BeltBlockEntity(BlockPos p, BlockState s) {
    super(Content.BELT.get(), p, s);
    boolean split = ((FactoryBlock) s.getBlock()).kind.splitter();
    assembly = split ? new factorycraft.multiblock.IndustrialAssembly(this) : null;
    lanes = new ArrayList<>(split ? 4 : 2);
    for (int i = 0; i < (split ? 4 : 2); i++) lanes.add(new BeltLane<>());
    if (kind().underground()) {
      route = new BeltRoute(BeltRoute.Shape.UNDERGROUND, 1, 0);
      for (var lane : lanes) lane.resize(route.length(0));
    }
    alternate = new boolean[lanes.size()];
    handlers = new IItemHandler[split ? 2 : 1];
    for (int i = 0; i < handlers.length; i++) handlers[i] = new TrackHandler(i);
  }

  public factorycraft.multiblock.IndustrialAssembly assembly() {
    return assembly;
  }

  public boolean loaded() {
    return !isRemoved() && (simulationLoaded || level != null && level.isClientSide);
  }

  public boolean structureReady() {
    if (assembly != null) return assembly.ready();
    if (level == null) return false;
    if (route.heightDelta() == 0) return true;
    for (var p : TransportPlan.cells(worldPosition, route))
      if (!p.equals(worldPosition)
          && (!(FluidConnections.at(level, p) instanceof TransportPartBlockEntity part)
              || !part.belongs(worldPosition, transportIdentity))) return false;
    return true;
  }

  private boolean available() {
    return loaded() && structureReady() && factorycraft.fluids.FluidConnections.canonical(this);
  }

  public int laneCount() {
    return lanes.size();
  }

  public int tracks() {
    return handlers.length;
  }

  public BeltLane<ItemStack> lane(int i) {
    return lanes.get(i);
  }

  private BeltTransport.Node<ItemStack> node(int i) {
    return nodes.get(i);
  }

  public BlockPos trackPosition(int track) {
    Objects.checkIndex(track, tracks());
    return worldPosition.relative(
        getBlockState().getValue(FactoryBlock.FACING).getClockWise(), track);
  }

  public int trackAt(BlockPos physical) {
    if (tracks() == 1 && (physical.equals(worldPosition) || physical.equals(outputPosition())))
      return 0;
    for (int i = 0; i < tracks(); i++) if (trackPosition(i).equals(physical)) return i;
    return -1;
  }

  public IItemHandler inventoryAt(BlockPos physical) {
    if (kind().underground() && undergroundOutput)
      return new GuardedTransportPort(
          () ->
              !available() || paired == null || paired.paired != this
                  ? null
                  : paired.port(worldPosition, false));
    int track = trackAt(physical);
    return track < 0
        ? null
        : tracks() == 2 ? handlers[track] : port(physical, physical.equals(worldPosition));
  }

  private IItemHandler port(BlockPos physical, boolean input) {
    if (!kind().lift() && !kind().underground() && route.heightDelta() == 0) return handlers[0];
    return new IItemHandler() {
      public int getSlots() {
        return 8;
      }

      public ItemStack getStackInSlot(int s) {
        return canSample(physical, s) ? handlers[0].getStackInSlot(s) : ItemStack.EMPTY;
      }

      public ItemStack insertItem(int s, ItemStack v, boolean sim) {
        return input ? handlers[0].insertItem(s, v, sim) : v;
      }

      public ItemStack extractItem(int s, int n, boolean sim) {
        return canSample(physical, s) ? handlers[0].extractItem(s, n, sim) : ItemStack.EMPTY;
      }

      public int getSlotLimit(int s) {
        return 4;
      }

      public boolean isItemValid(int s, ItemStack v) {
        return input;
      }
    };
  }

  public double speed() {
    return ((FactoryBlock) getBlockState().getBlock()).kind.beltTier * .09375;
  }

  public long snapshotTick() {
    return snapshotTick;
  }

  public IItemHandler inventory() {
    var p = inventoryAt(worldPosition);
    return p == null ? new net.neoforged.neoforge.items.ItemStackHandler(0) : p;
  }

  public boolean hasItems() {
    for (var lane : lanes) if (!lane.carriers().isEmpty()) return true;
    return false;
  }

  public boolean isScheduled() {
    for (var n : nodes) if (n.isActive()) return true;
    return false;
  }

  /** Explicit stack-inserter entry. Ordinary capability insertion deliberately stays one item. */
  public ItemStack insertStack(
      BlockPos physical, int slot, ItemStack stack, int maxHeight, boolean simulate) {
    int track = trackAt(physical);
    if (track < 0
        || slot < 0
        || slot >= 8
        || !available()
        || transport == null
        || undergroundOutput
        || (kind().lift() || kind().underground() || route.heightDelta() != 0)
            && !physical.equals(worldPosition)) return stack;
    int index = track * 2 + slot / 4;
    if (stack.isEmpty() || !transport.canInsert(node(index))) return stack;
    int count =
        Math.min(stack.getCount(), Math.min(stack.getMaxStackSize(), Math.clamp(maxHeight, 1, 4)));
    if (!simulate) {
      if (!transport.insert(node(index), stack.copyWithCount(count))) return stack;
      sync();
    }
    return stack.getCount() == count
        ? ItemStack.EMPTY
        : stack.copyWithCount(stack.getCount() - count);
  }

  private final class TrackHandler implements IItemHandler {
    private final int track;

    TrackHandler(int track) {
      this.track = track;
    }

    public int getSlots() {
      return 8;
    }

    private int index(int slot) {
      Objects.checkIndex(slot, 8);
      return track * 2 + slot / 4;
    }

    public ItemStack getStackInSlot(int slot) {
      var lane = lane(index(slot));
      return slot % 4 < lane.carriers().size()
          ? lane.carriers().get(slot % 4).value.copy()
          : ItemStack.EMPTY;
    }

    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
      int index = index(slot);
      if (stack.isEmpty() || !available() || transport == null || !transport.canInsert(node(index)))
        return stack;
      if (!simulate) {
        if (!transport.insert(node(index), stack.copyWithCount(1))) return stack;
        sync();
      }
      return stack.getCount() == 1 ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - 1);
    }

    public ItemStack extractItem(int slot, int amount, boolean simulate) {
      var lane = lane(index(slot));
      if (!available() || amount <= 0 || slot % 4 >= lane.carriers().size()) return ItemStack.EMPTY;
      var stored = lane.carriers().get(slot % 4).value;
      int count = Math.min(amount, stored.getCount());
      var result = stored.copyWithCount(count);
      if (!simulate) {
        if (count == stored.getCount()) lane.remove(slot % 4);
        else stored.shrink(count);
        sync();
        wakeTransport();
      }
      return result;
    }

    public int getSlotLimit(int slot) {
      index(slot);
      return 4;
    }

    public boolean isItemValid(int slot, ItemStack stack) {
      index(slot);
      return true;
    }
  }

  public void bindTransport(BeltTransport<ItemStack> transport) {
    this.transport = transport;
    if (undergroundOutput) return;
    int tier = ((FactoryBlock) getBlockState().getBlock()).kind.beltTier;
    for (int i = 0; i < laneCount(); i++) {
      var n = transport.create(lane(i), tier, c -> BeltSimulation.changed(this, c));
      n.restorePreference(alternate[i]);
      n.restoreCommittedOutput(committedOutputs[i]);
      nodes.add(n);
      transport.setEnabled(n, circuitRunning);
      var logic = circuit(false);
      if (logic != null && logic.settings().pulse() && logic.settings().contents())
        transport.arrivalListener(n, this::circuitArrival);
    }
    applySplitterSettings();
  }

  public void unbindTransport() {
    if (transport == null) return;
    for (int i = 0; i < nodes.size(); i++) {
      alternate[i] = node(i).prefersSecondary();
      committedOutputs[i] = node(i).committedOutput();
      transport.remove(node(i));
    }
    nodes.clear();
    transport = null;
  }

  public void wakeTransport() {
    if (desiredRoute != null && simulationLoaded && !removing) BeltSimulation.changed(this, false);
    if (transport != null) for (var n : nodes) transport.changedExternally(n);
  }

  public void refreshConnections() {
    if (transport == null || level == null || refreshing || removing) return;
    refreshing = true;
    try {
      if (kind().ordinaryBelt()
          && BeltSimulation.tickable(this)
          && (route.heightDelta() == 0 || structureReady())) {
        var next =
            switch (formMode) {
              case 1 -> BeltRoute.FLAT;
              case 2 -> new BeltRoute(BeltRoute.Shape.UP, 0, 0);
              case 3 -> new BeltRoute(BeltRoute.Shape.DOWN, 0, 0);
              default -> TransportPlan.automatic(level, worldPosition, facing());
            };
        desiredRoute = next;
        changeRoute(next);
      }
      paired = null;
      if (kind().underground()) {
        var other = findPair();
        if (other != null && other.findPair() == this) paired = other;
        if (!undergroundOutput && paired != null) {
          int distance = worldPosition.distManhattan(paired.worldPosition);
          if (route.shape != BeltRoute.Shape.UNDERGROUND || route.span != distance) {
            if (!hasItems()) changeRoute(new BeltRoute(BeltRoute.Shape.UNDERGROUND, distance, 0));
            else paired = null;
          }
        }
        if (undergroundOutput) {
          level.invalidateCapabilities(worldPosition);
          return;
        }
      }
      boolean ready =
          loaded()
              && BeltSimulation.tickable(this)
              && structureReady()
              && (!kind().underground()
                  || paired != null && paired.loaded() && BeltSimulation.tickable(paired));
      var output = outputFacing();
      for (int i = 0; i < nodes.size(); i++) {
        var n = node(i);
        transport.setAvailable(n, ready);
        transport.connect(
            n,
            ready ? targetAt(i % 2, outputPosition().relative(output), output) : null,
            ready && tracks() == 2
                ? targetAt(i % 2, trackPosition(1).relative(output), output)
                : null);
      }
    } finally {
      refreshing = false;
    }
  }

  private BeltTransport.Node<ItemStack> targetAt(int lane, BlockPos physical, Direction output) {
    var target = BeltSimulation.at(level, physical);
    if (target == null
        || !target.loaded()
        || target.transport == null
        || target.nodes.isEmpty()
        || target.undergroundOutput
        || !target.structureReady()) return null;
    // Only an entry cell accepts a neighboring transport node; the lift head is output-only.
    int track = target.trackAt(physical);
    if (track < 0 || !target.trackPosition(track).equals(physical)) return null;
    var facing = target.facing();
    if (facing == output.getOpposite()) return null;
    boolean curved =
        target.route.shape == BeltRoute.Shape.LEFT && output == facing.getClockWise()
            || target.route.shape == BeltRoute.Shape.RIGHT
                && output == facing.getCounterClockWise();
    int side = facing == output || curved ? lane : output == facing.getClockWise() ? 0 : 1;
    return target.node(track * 2 + side);
  }

  public boolean markSimulationChanged(boolean contents) {
    contentsChanged |= contents;
    if (queuedForSync) return false;
    queuedForSync = true;
    return true;
  }

  public void flushSimulationChanges(long tick) {
    if (desiredRoute != null
        && simulationLoaded
        && structureReady()
        && !sameRoute(route, desiredRoute)
        && changeRoute(desiredRoute)) BeltSimulation.topologyChanged(this);
    factorycraft.diagnostics.FactoryTelemetry.beltChanged(this);
    if (contentsChanged) setChanged();
    else markPersistentState();
    if (contentsChanged && route.length(0) == 96 && route.length(1) == 96 || (tick & 3) == 0)
      sendUpdate();
    queuedForSync = false;
    contentsChanged = false;
  }

  public void setBlockState(BlockState state) {
    boolean rotated =
        getBlockState().getValue(FactoryBlock.FACING) != state.getValue(FactoryBlock.FACING);
    super.setBlockState(state);
    if (rotated && level != null) {
      requestModelDataUpdate();
      if (simulationLoaded) {
        BeltSimulation.topologyChanged(this);
        for (var p : TransportPlan.cells(worldPosition, route))
          if (!p.equals(worldPosition)
              && FluidConnections.at(level, p) instanceof TransportPartBlockEntity part
              && part.boundTo(worldPosition, transportIdentity)) part.bind(this);
        sync();
      }
    }
  }

  public void onLoad() {
    super.onLoad();
    if (!level.isClientSide && !simulationLoaded) {
      simulationLoaded = true;
      BeltSimulation.register(this);
      if (kind().splitter()) factorycraft.circuits.CircuitSimulation.watchSplitter(this, null);
      if (assembly != null) assembly.invalidate();
    }
  }

  public void setRemoved() {
    if (simulationLoaded) BeltSimulation.unregister(this);
    if (kind().splitter())
      factorycraft.circuits.CircuitSimulation.watchSplitter(this, circuitBinding);
    super.setRemoved();
  }

  public void onChunkUnloaded() {
    if (simulationLoaded) BeltSimulation.unregister(this);
    if (kind().splitter())
      factorycraft.circuits.CircuitSimulation.watchSplitter(this, circuitBinding);
    super.onChunkUnloaded();
  }

  public void dropContents() {
    if (level == null || level.isClientSide) return;
    if (assembly != null) {
      assembly.removeWhole(true);
      return;
    }
    if (special()) {
      removeTransport(true);
      return;
    }
    for (var lane : lanes)
      while (!lane.carriers().isEmpty())
        net.minecraft.world.Containers.dropItemStack(
            level,
            worldPosition.getX() + .5,
            worldPosition.getY() + .5,
            worldPosition.getZ() + .5,
            lane.remove(0));
    setChanged();
    wakeTransport();
  }

  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
    super.saveAdditional(tag, r);
    if (circuitBinding != null) {
      tag.put("CircuitControl", circuitBinding.save());
      tag.putBoolean("CircuitMoving", circuitRunning);
    }
    if (assembly != null) assembly.save(tag);
    tag.putInt("Schema", tracks() == 2 ? 3 : 2);
    tag.putLong("SnapshotTick", level == null ? 0 : level.getGameTime());
    if (route.shape != BeltRoute.Shape.FLAT
        || kind().lift()
        || kind().underground()
        || formMode != 0) {
      var geometry = new CompoundTag();
      TransportPlan.saveCell(geometry, modelCell(worldPosition));
      geometry.putInt("Mode", formMode);
      tag.put("Route", geometry);
    }
    if (special()) tag.putUUID("TransportIdentity", transportIdentity);
    if (undergroundOutput) tag.putBoolean("UndergroundOutput", true);
    if (kind().splitter()
        && (!splitterSettings.equals(factorycraft.logistics.SplitterSettings.DEFAULT)
            || circuitBinding != null)) {
      var settings = splitterSettings.save();
      int[] outputs = committedOutputs.clone();
      if (transport != null && !nodes.isEmpty())
        for (int i = 0; i < nodes.size(); i++) outputs[i] = node(i).committedOutput();
      settings.putIntArray("CommittedOutputs", outputs);
      tag.put("Splitter", settings);
    }
    for (int i = 0; i < laneCount(); i++) {
      tag.putBoolean(
          i == 0 ? "Alternate" : i == 1 ? "AlternateRight" : "Alternate" + i,
          transport == null || nodes.isEmpty() ? alternate[i] : node(i).prefersSecondary());
      var list = new ListTag();
      for (var carrier : lane(i).carriers()) {
        var n = new CompoundTag();
        n.put("Stack", carrier.value.save(r));
        n.putDouble("Position", carrier.position());
        n.putInt("Units", carrier.positionUnits());
        list.add(n);
      }
      tag.put("Lane" + i, list);
    }
  }

  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
    super.loadAdditional(tag, r);
    var oldBinding = circuitBinding;
    circuitBinding = factorycraft.circuits.CircuitBinding.load(tag.getCompound("CircuitControl"));
    circuitRunning =
        circuitBinding == null
            || level != null && level.isClientSide && tag.getBoolean("CircuitMoving");
    if (transport != null) for (var n : nodes) transport.setEnabled(n, circuitRunning);
    if (level != null && !level.isClientSide && kind().splitter())
      factorycraft.circuits.CircuitSimulation.watchSplitter(this, oldBinding);
    if (assembly != null) assembly.load(tag);
    snapshotTick = tag.getLong("SnapshotTick");
    if (kind().splitter()) {
      splitterSettings = factorycraft.logistics.SplitterSettings.load(tag.getCompound("Splitter"));
      var outputs = tag.getCompound("Splitter").getIntArray("CommittedOutputs");
      for (int i = 0; i < committedOutputs.length; i++)
        committedOutputs[i] = i < outputs.length ? Math.clamp(outputs[i], 0, 2) : 0;
    }
    var cell = TransportPlan.loadCell(tag.getCompound("Route"));
    route = cell == null ? BeltRoute.FLAT : cell.route();
    formMode = tag.getCompound("Route").getInt("Mode");
    undergroundOutput = tag.getBoolean("UndergroundOutput");
    if (tag.hasUUID("TransportIdentity")) transportIdentity = tag.getUUID("TransportIdentity");
    for (int i = 0; i < laneCount(); i++) {
      lane(i).clear();
      lane(i).resize(route.length(i % 2));
    }
    for (int i = 0; i < laneCount(); i++) {
      alternate[i] =
          tag.getBoolean(i == 0 ? "Alternate" : i == 1 ? "AlternateRight" : "Alternate" + i);
      if (i == 1 && !tag.contains("AlternateRight")) alternate[i] = alternate[0];
      lane(i).clear();
      var list = tag.getList("Lane" + i, Tag.TAG_COMPOUND);
      for (int j = 0; j < list.size(); j++) {
        var n = list.getCompound(j);
        var stack = ItemStack.parseOptional(r, n.getCompound("Stack"));
        double position =
            n.contains("Units")
                ? (double) n.getInt("Units") / lane(i).length()
                : n.getDouble("Position");
        if (!stack.isEmpty()) lane(i).restore(stack, position);
      }
      if (transport != null && !nodes.isEmpty()) node(i).restorePreference(alternate[i]);
    }
    if (transport != null) {
      applySplitterSettings();
      wakeTransport();
    }
    if (level != null && level.isClientSide) {
      requestModelDataUpdate();
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
  }
}
