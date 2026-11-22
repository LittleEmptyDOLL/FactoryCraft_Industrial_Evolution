package factorycraft.trains;

import factorycraft.circuits.*;
import factorycraft.rail.*;
import factorycraft.rail.automation.*;
import factorycraft.research.ResearchTeams;
import factorycraft.simulation.circuits.*;
import factorycraft.simulation.rail.*;
import factorycraft.simulation.trains.TrackOccupancy;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Canonical trackside configuration/shared definitions; train resources stay exclusively in
 * TrainData.
 */
public final class RailAutomationData extends SavedData {
  private static final Map<ServerLevel, RailAutomationData> LIVE = new IdentityHashMap<>();

  public static final class Device {
    public final UUID id, owner;
    public final String scope;
    public final BlockPos anchor;
    public final TracksideKind kind;
    public final RailEdgePoints.Point point;
    public String name = "Station";
    public boolean enabled = true, circuitLimit, circuitPriority, paintArrivals;
    public int limit = -1, priority = 50, color = 0x54a58d, health;

    public Device(
        UUID id,
        UUID owner,
        String scope,
        BlockPos anchor,
        TracksideKind kind,
        UUID edge,
        double offset,
        boolean reverse) {
      this.id = id;
      this.owner = owner;
      this.scope = scope;
      this.anchor = anchor.immutable();
      this.kind = kind;
      point = new RailEdgePoints.Point(id, edge, offset, reverse, kind.point);
      health = kind.health;
      if (scope.isBlank() || scope.length() > 512)
        throw new IllegalArgumentException("Device scope");
    }

    public List<BlockPos> cells() {
      var out = new ArrayList<BlockPos>();
      for (int i = 0; i < kind.parts(); i++) out.add(TracksideBlock.offset(anchor, kind, i));
      return List.copyOf(out);
    }

    public boolean mayUse(ServerPlayer p) {
      return !p.isSpectator()
          && p.mayBuild()
          && (p.isCreative() || owner.equals(p.getUUID()) || ResearchTeams.member(p, scope));
    }

    public CompoundTag settings() {
      var n = new CompoundTag();
      n.putString("Name", name);
      n.putBoolean("Enabled", enabled);
      n.putInt("Limit", limit);
      n.putInt("Priority", priority);
      n.putInt("Color", color);
      n.putBoolean("CircuitLimit", circuitLimit);
      n.putBoolean("CircuitPriority", circuitPriority);
      n.putBoolean("PaintArrivals", paintArrivals);
      return n;
    }

    public void settings(CompoundTag n) {
      String name = n.getString("Name");
      int limit = n.getInt("Limit"), priority = n.getInt("Priority");
      if (name.isBlank()
          || name.length() > 128
          || limit < -1
          || limit > 65535
          || priority < 0
          || priority > 255) throw new IllegalArgumentException("Stop settings");
      this.name = name;
      this.limit = limit;
      this.priority = priority;
      color = n.getInt("Color") & 0xffffff;
      enabled = n.getBoolean("Enabled");
      circuitLimit = n.getBoolean("CircuitLimit");
      circuitPriority = n.getBoolean("CircuitPriority");
      paintArrivals = n.getBoolean("PaintArrivals");
    }
  }

  public record Group(String scope, String name, long version, RailSchedule schedule) {}

  public record SharedInterrupt(String scope, RailInterrupt definition) {}

  private record Return(UUID id, ItemStack stack, Vec3 position) {}

  public final Map<UUID, Device> devices = new LinkedHashMap<>();
  public final Map<String, Group> groups = new LinkedHashMap<>();
  public final Map<UUID, SharedInterrupt> interrupts = new LinkedHashMap<>();
  private final Map<BlockPos, UUID> anchors = new HashMap<>();
  private final Map<UUID, Set<UUID>> byEdge = new HashMap<>();
  private final Map<Long, Set<UUID>> byChunk = new HashMap<>();

  private record Remnant(BlockPos position, BlockPos anchor, TracksideKind kind) {}

  private final Map<UUID, Return> returns = new LinkedHashMap<>();
  private final ArrayDeque<UUID> returnQueue = new ArrayDeque<>();
  private final Map<Long, Map<BlockPos, Remnant>> remnants = new HashMap<>();
  private final LinkedHashSet<Long> cleanChunks = new LinkedHashSet<>();
  private final Set<UUID> returning = new HashSet<>();
  private final ListTag quarantined = new ListTag();
  public final RailEdgePoints points = new RailEdgePoints();
  public final RailBlockClaims claims = new RailBlockClaims();
  public final RailStopSlots slots = new RailStopSlots();
  public final RailDispatchQueue dispatch = new RailDispatchQueue();
  public RailSignalBlocks blocks;
  public RailSignals signals;
  public ServerLevel level;
  public RailwayData rails;
  private final LinkedHashSet<UUID> reconcile = new LinkedHashSet<>(),
      dirtySignals = new LinkedHashSet<>();
  private final Map<UUID, Set<UUID>> pendingBodyEdges = new HashMap<>(),
      pendingEdgeBodies = new HashMap<>();
  private final Map<UUID, RailSignals.View> views = new HashMap<>();
  private final Map<UUID, Set<UUID>> groupSignals = new HashMap<>(),
      signalParents = new HashMap<>();
  private final Map<UUID, Boolean> forcedCache = new HashMap<>();
  private int nextTrainId = 1;
  private long nextDispatchOrder = 1;
  public long revision, pathNodes, pathNanos, topologyNanos, aspectUpdates;
  private long lastTick = Long.MIN_VALUE;
  private int pathBudget;

  public static RailAutomationData peek(ServerLevel level) {
    return LIVE.get(level);
  }

  public static void forget(ServerLevel level) {
    LIVE.remove(level);
  }

  public static RailAutomationData get(ServerLevel level) {
    var old = LIVE.get(level);
    if (old != null) return old;
    var d =
        level
            .getDataStorage()
            .computeIfAbsent(
                new SavedData.Factory<>(RailAutomationData::new, RailAutomationData::load),
                "factorycraft_rail_automation");
    d.level = level;
    LIVE.put(level, d);
    d.initialize();
    return d;
  }

  private void initialize() {
    rails = RailwayData.get(level);
    blocks = new RailSignalBlocks(rails.graph, points);
    signals = new RailSignals(rails.graph, points, blocks, claims, this::online, this::forced);
    for (var device : devices.values()) {
      index(device);
      if (rails.graph.edge(device.point.edge()) != null) {
        try {
          points.put(device.point, rails.graph);
        } catch (IllegalArgumentException invalid) {
          /* visible invalid attachment */
        }
      }
      if (device.kind == TracksideKind.STOP) slots.put(stop(device));
      else dirtySignals.add(device.id);
    }
    for (var edge : rails.graph.edges()) blocks.changed(edge.id());
    var trains = TrainData.get(level);
    for (var t : trains.trains()) {
      pendingTrain(t);
      var origin = device(t.automation.departure);
      if (origin != null && origin.scope.equals(t.scope)) slots.restoreOrigin(t.id, origin.id);
      var target = device(t.automation.destination);
      if (target != null && target.scope.equals(t.scope)) slots.restore(t.id, target.id);
      nextDispatchOrder = Math.max(nextDispatchOrder, t.automation.dispatchOrder + 1);
    }
  }

  private void index(Device device) {
    var old = anchors.putIfAbsent(device.anchor, device.id);
    if (old != null && !old.equals(device.id))
      throw new IllegalArgumentException("Duplicate trackside anchor");
    byEdge.computeIfAbsent(device.point.edge(), k -> new HashSet<>()).add(device.id);
    for (var p : device.cells())
      byChunk
          .computeIfAbsent(new net.minecraft.world.level.ChunkPos(p).toLong(), k -> new HashSet<>())
          .add(device.id);
  }

  public Device device(UUID id) {
    return devices.get(id);
  }

  public Device at(BlockPos anchor) {
    var id = anchors.get(anchor);
    return id == null ? null : devices.get(id);
  }

  public TracksideBlockEntity entity(Device d) {
    var be = factorycraft.fluids.FluidConnections.at(level, d.anchor);
    return be instanceof TracksideBlockEntity root && root.identity.equals(d.id) ? root : null;
  }

  public boolean online(RailEdgePoints.Point point) {
    var d = devices.get(point.id());
    if (d == null) return false;
    var root = entity(d);
    return root != null && root.ready() && rails.available(point.edge());
  }

  public boolean forced(RailEdgePoints.Point point) {
    var d = devices.get(point.id());
    var root = d == null ? null : entity(d);
    var circuit = root == null ? null : root.circuit(false);
    return circuit != null
        && circuit.settings().gate()
        && (!circuit.available() || !circuit.inputReady || circuit.permitted);
  }

  public RailStopSlots.Stop stop(Device d) {
    var root = entity(d);
    var c = root == null ? null : root.circuit(false);
    boolean enabled = d.enabled;
    int limit = d.limit, priority = d.priority;
    if (c != null) {
      if (c.settings().gate()) enabled &= c.available() && c.inputReady && c.permitted;
      if (d.circuitLimit)
        limit =
            c.available() && c.inputReady
                ? Math.clamp(c.input.get(SignalKey.virtual("signal_l")), 0, 65535)
                : 0;
      if (d.circuitPriority)
        priority =
            c.available() && c.inputReady
                ? Math.clamp(c.input.get(SignalKey.virtual("signal_p")), 0, 255)
                : 0;
    } else if (d.circuitLimit) limit = 0;
    return new RailStopSlots.Stop(d.id, d.scope, d.name, enabled, limit, priority);
  }

  public String attachmentStatus(TracksideKind kind, UUID edge, double offset, boolean reverse) {
    var rail = rails.graph.edge(edge);
    if (rail == null
        || !rails.available(edge)
        || !Double.isFinite(offset)
        || offset < 0
        || offset > rail.curve().length()) return "unloaded";
    var attached = points.on(edge);
    if (attached.size() >= 256) return "capacity";
    for (var old : attached)
      if (Math.abs(old.offset() - offset) < 1e-7
          && old.reverse() == reverse
          && (old.boundary() == (kind != TracksideKind.STOP))) return "occupied";
    return "ready";
  }

  public void add(Device device) {
    if (devices.containsKey(device.id) || anchors.containsKey(device.anchor))
      throw new IllegalArgumentException("Device already owned");
    points.put(device.point, rails.graph);
    devices.put(device.id, device);
    index(device);
    if (device.kind == TracksideKind.STOP) syncStop(device);
    else {
      blocks.changed(device.point.edge());
      dirtySignals.add(device.id);
    }
    revision++;
    setDirty();
  }

  public void configure(Device d, CompoundTag settings) {
    if (devices.get(d.id) != d) throw new IllegalArgumentException("Stale device");
    d.settings(settings);
    changed(d);
    setDirty();
    for (var cell : d.cells())
      if (level.hasChunkAt(cell)) {
        var state = level.getBlockState(cell);
        if (state.getBlock() == TracksideContent.BLOCKS.get(d.kind).get())
          level.sendBlockUpdated(cell, state, state, 2);
      }
  }

  public void changed(Device d) {
    if (d.kind == TracksideKind.STOP) syncStop(d);
    queueSignal(d.id);
    revision++;
    var root = entity(d);
    if (root != null) root.sync();
  }

  public void circuitChanged(UUID id) {
    var d = devices.get(id);
    if (d == null) return;
    if (d.kind == TracksideKind.STOP) {
      var next = stop(d);
      if (next.equals(slots.get(id))) return;
      syncStop(d);
    } else {
      boolean next = forced(d.point);
      var old = forcedCache.put(id, next);
      if (old != null && old == next) return;
      queueSignal(id);
    }
    revision++;
  }

  private void invalidateStops(Set<UUID> trains) {
    var data = TrainData.get(level);
    for (var id : trains) {
      var t = data.train(id);
      if (t == null) continue;
      var a = t.automation;
      if (a.departure != null && !a.departure.equals(slots.origin(id))) a.departure = null;
      if (a.destination != null && !a.destination.equals(slots.destination(id))) {
        a.destination = null;
        a.routed = false;
        a.arrived = false;
      }
      a.routeJob = null;
      a.dispatchTarget = null;
      dispatch.remove(id);
      t.automation.retryAt = 0;
      data.active.add(id);
      data.setDirty();
    }
  }

  private void syncStop(Device device) {
    var old = slots.get(device.id);
    var current = stop(device);
    invalidateStops(slots.put(current));
    if (old != null) refreshDispatchName(old.scope(), old.name());
    refreshDispatchName(current.scope(), current.name());
    dispatch.priority(device.id, current.priority());
  }

  private void refreshDispatchName(String scope, String name) {
    var targets = new HashSet<UUID>();
    for (var stop : slots.named(scope, name)) targets.add(stop.id());
    dispatch.targets(scope, name, targets);
  }

  public long dispatchOrder() {
    if (nextDispatchOrder == Long.MAX_VALUE)
      throw new IllegalStateException("Dispatch order capacity");
    setDirty();
    return nextDispatchOrder++;
  }

  public void trackChanged(UUID edge) {
    if (blocks == null) return;
    blocks.changed(edge);
    for (var id : byEdge.getOrDefault(edge, Set.of())) queueSignal(id);
    revision++;
  }

  public void deviceLoaded(UUID id) {
    queueSignal(id);
  }

  public void chunkChanged(long chunk) {
    for (var id : byChunk.getOrDefault(chunk, Set.of())) {
      queueSignal(id);
      circuitChanged(id);
    }
    if (remnants.containsKey(chunk)) cleanChunks.add(chunk);
  }

  public void queueSignal(UUID id) {
    dirtySignals.add(id);
    dirtySignals.addAll(signalParents.getOrDefault(id, Set.of()));
  }

  public boolean runtimeReady() {
    return !blocks.busy() && reconcile.isEmpty();
  }

  /** Readiness of this body only. Unrelated rebuilds must not freeze a ready network. */
  public boolean runtimeReady(TrainData.Train t) {
    if (reconcile.contains(t.id)) return false;
    var body = claims.body(t.id);
    if (body.isEmpty()) return false;
    for (var region : body) if (blocks.region(region) == null) return false;
    return true;
  }

  private void pendingBody(UUID train, UUID edge) {
    reconcile.add(train);
    if (pendingBodyEdges.computeIfAbsent(train, k -> new HashSet<>()).add(edge)) {
      pendingEdgeBodies.computeIfAbsent(edge, k -> new HashSet<>()).add(train);
      blocks.hold(edge);
    }
  }

  private void pendingTrain(TrainData.Train t) {
    for (var span : TrackOccupancy.span(t.path, t.rear(), t.front()))
      pendingBody(t.id, span.edge());
  }

  private void bodyReconciled(UUID train) {
    reconcile.remove(train);
    var edges = pendingBodyEdges.remove(train);
    if (edges == null) return;
    for (var edge : edges) {
      var owners = pendingEdgeBodies.get(edge);
      owners.remove(train);
      if (owners.isEmpty()) {
        pendingEdgeBodies.remove(edge);
        blocks.release(edge);
        for (var id : byEdge.getOrDefault(edge, Set.of())) queueSignal(id);
      }
    }
  }

  public void trainChanged(TrainData.Train t) {
    var body = new HashSet<UUID>();
    for (var span : TrackOccupancy.span(t.path, t.rear(), t.front())) {
      if (rails.graph.edge(span.edge()) == null)
        continue; // quarantined owner; physical recovery hull still blocks
      var members = blocks.coveringForReconciliation(span.edge(), span.min(), span.max());
      if (members == null) {
        pendingTrain(t);
        return;
      }
      body.addAll(members);
    }
    claims.occupy(t.id, body);
    bodyReconciled(t.id);
  }

  public void trainRemoved(UUID id) {
    dispatch.remove(id);
    claims.remove(id);
    slots.release(id);
    bodyReconciled(id);
  }

  public int trainNumber(TrainData.Train t) {
    if (t.automation.number == 0) {
      if (nextTrainId == Integer.MAX_VALUE)
        throw new IllegalStateException("Train number capacity");
      t.automation.number = nextTrainId++;
      setDirty();
      TrainData.get(level).setDirty();
    }
    return t.automation.number;
  }

  public int searchBudget() {
    return pathBudget;
  }

  public void spentSearch(int nodes, long nanos) {
    pathBudget -= nodes;
    pathNodes += nodes;
    pathNanos += nanos;
  }

  public void tick(TrainData trains) {
    if (!level.tickRateManager().runsNormally() || lastTick == level.getGameTime()) return;
    lastTick = level.getGameTime();
    pathBudget = 2048;
    flushReturns();
    cleanRemnants();
    long start = System.nanoTime();
    blocks.advance(512);
    topologyNanos += System.nanoTime() - start;
    for (var edge : blocks.takeInvalidatedEdges()) {
      for (var owner : trains.occupancy.owners(edge)) pendingBody(owner, edge);
      for (var id : byEdge.getOrDefault(edge, Set.of())) queueSignal(id);
    }
    for (var edge : blocks.takePublishedEdges())
      for (var id : byEdge.getOrDefault(edge, Set.of())) queueSignal(id);
    int work = 0;
    for (var id : List.copyOf(reconcile)) {
      if (++work > 128) break;
      var t = trains.train(id);
      if (t == null) {
        trainRemoved(id);
        continue;
      }
      reconcile.remove(id);
      reconcile.add(id); // rotate unavailable owners instead of starving later ready bodies
      trainChanged(t);
    }
    {
      for (var block : claims.takeChanged())
        dirtySignals.addAll(groupSignals.getOrDefault(block, Set.of()));
      work = 0;
      for (var it = dirtySignals.iterator(); it.hasNext() && work++ < 32; ) {
        var id = it.next();
        it.remove();
        var point = points.get(id);
        if (point == null || !point.boundary()) continue;
        var old = views.get(id);
        if (old != null) {
          for (var b : old.blocks()) {
            var set = groupSignals.get(b);
            if (set != null) set.remove(id);
          }
          for (var s : old.signals()) {
            var set = signalParents.get(s);
            if (set != null) set.remove(id);
          }
        }
        var view = signals.view(point);
        views.put(id, view);
        aspectUpdates++;
        for (var b : view.blocks()) groupSignals.computeIfAbsent(b, k -> new HashSet<>()).add(id);
        for (var s : view.signals()) signalParents.computeIfAbsent(s, k -> new HashSet<>()).add(id);
        var d = devices.get(id);
        var root = d == null ? null : entity(d);
        if (root != null
            && root.ready()
            && root.getBlockState().getValue(TracksideBlock.ASPECT) != view.aspect().ordinal()) {
          for (var cell : d.cells())
            if (level.hasChunkAt(cell)) {
              var state = level.getBlockState(cell);
              if (state.getBlock() instanceof TracksideBlock)
                level.setBlock(
                    cell, state.setValue(TracksideBlock.ASPECT, view.aspect().ordinal()), 2);
            }
          root.sync();
          var c = root.circuit(false);
          if (c != null) CircuitSimulation.changed(c);
        }
      }
    }
  }

  public RailSignals.Aspect aspect(Device d) {
    if (d.kind == TracksideKind.STOP)
      return online(d.point) ? RailSignals.Aspect.GREEN : RailSignals.Aspect.INVALID;
    var view = views.get(d.id);
    return view == null || !online(d.point) ? RailSignals.Aspect.INVALID : view.aspect();
  }

  public CompoundTag visual(UUID id) {
    var n = new CompoundTag();
    var d = devices.get(id);
    if (d == null) return n;
    n.putString("Name", d.name);
    n.putInt("Color", d.color);
    n.putInt("Aspect", aspect(d).ordinal());
    var edge = rails.graph.edge(d.point.edge());
    if (edge != null) {
      var sample = edge.curve().sample(d.point.offset(), false);
      var p = sample.position();
      var v = sample.tangent();
      n.putDouble("X", p.x());
      n.putDouble("Y", p.y());
      n.putDouble("Z", p.z());
      n.putDouble("DX", v.x() * (d.point.reverse() ? -1 : 1));
      n.putDouble("DZ", v.z() * (d.point.reverse() ? -1 : 1));
    }
    return n;
  }

  public Device retire(UUID id, boolean drop, CompoundTag circuit) {
    var d = devices.remove(id);
    if (d == null) return null;
    anchors.remove(d.anchor, id);
    var e = byEdge.get(d.point.edge());
    if (e != null) e.remove(id);
    for (var p : d.cells()) {
      var ids = byChunk.get(new net.minecraft.world.level.ChunkPos(p).toLong());
      if (ids != null) ids.remove(id);
    }
    points.remove(id);
    invalidateStops(slots.remove(id));
    if (d.kind == TracksideKind.STOP) refreshDispatchName(d.scope, d.name);
    views.remove(id);
    if (d.kind != TracksideKind.STOP) blocks.changed(d.point.edge());
    for (var p : d.cells())
      if (!level.hasChunkAt(p)) {
        long key = new net.minecraft.world.level.ChunkPos(p).toLong();
        remnants
            .computeIfAbsent(key, k -> new LinkedHashMap<>())
            .put(p, new Remnant(p, d.anchor, d.kind));
      }
    if (drop) {
      var stack = new ItemStack(TracksideContent.ITEMS.get(d.kind).get());
      var tag = new CompoundTag();
      tag.put("RailDeviceSettings", d.settings());
      if (circuit != null) {
        tag.put("DirectCircuit", circuit);
        CircuitAttachment.portable(tag);
      }
      stack.set(
          net.minecraft.core.component.DataComponents.CUSTOM_DATA,
          net.minecraft.world.item.component.CustomData.of(tag));
      var claim = new Return(UUID.randomUUID(), stack, Vec3.atCenterOf(d.anchor));
      returns.put(claim.id, claim);
      returnQueue.addLast(claim.id);
    }
    revision++;
    setDirty();
    return d;
  }

  /** Retired unloaded parts are removed by their saved positions, never by a chunk-wide scan. */
  private void cleanRemnants() {
    int work = 0;
    for (var it = cleanChunks.iterator(); it.hasNext() && work < 64; ) {
      long key = it.next();
      var entries = remnants.get(key);
      if (entries == null) {
        it.remove();
        continue;
      }
      var chunk = level.getChunkSource().getChunkNow((int) key, (int) (key >> 32));
      if (chunk == null) {
        it.remove();
        continue;
      }
      for (var parts = entries.values().iterator(); parts.hasNext() && work < 64; work++) {
        var remnant = parts.next();
        if (at(remnant.anchor) == null) {
          var state = chunk.getBlockState(remnant.position);
          if (state.getBlock() == TracksideContent.BLOCKS.get(remnant.kind).get()
              && TracksideBlock.origin(remnant.position, state).equals(remnant.anchor)) {
            boolean old = TracksidePlacement.writing;
            TracksidePlacement.writing = true;
            try {
              level.setBlock(
                  remnant.position,
                  net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                  3);
            } finally {
              TracksidePlacement.writing = old;
            }
          }
        }
        parts.remove();
        setDirty();
      }
      if (entries.isEmpty()) {
        remnants.remove(key);
        it.remove();
      }
    }
  }

  private void flushReturns() {
    int work = Math.min(8, returnQueue.size());
    while (work-- > 0) {
      var id = returnQueue.removeFirst();
      var claim = returns.get(id);
      if (claim == null) continue;
      if (!returning.add(id)) {
        returnQueue.addLast(id);
        continue;
      }
      try {
        var p = BlockPos.containing(claim.position);
        if (!level.hasChunkAt(p)
            || !level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(p).toLong())
            || level.getEntity(id) != null) continue;
        var item =
            new net.minecraft.world.entity.item.ItemEntity(
                level, claim.position.x, claim.position.y, claim.position.z, claim.stack.copy());
        item.setUUID(id);
        item.setDefaultPickUpDelay();
        if (level.addFreshEntity(item)) {
          returns.remove(id);
          setDirty();
        }
      } finally {
        returning.remove(id);
        if (returns.containsKey(id)) returnQueue.addLast(id);
      }
    }
  }

  public static RailAutomationData load(CompoundTag n, HolderLookup.Provider r) {
    if (n.getInt("Schema") != 1) throw new IllegalArgumentException("Rail automation schema");
    var data = new RailAutomationData();
    data.nextTrainId = Math.max(1, n.getInt("NextTrainId"));
    data.nextDispatchOrder = n.contains("NextDispatchOrder") ? n.getLong("NextDispatchOrder") : 1;
    if (data.nextDispatchOrder < 1) throw new IllegalArgumentException("Dispatch order");
    for (var raw : n.getList("Devices", Tag.TAG_COMPOUND)) {
      var x = (CompoundTag) raw;
      try {
        var kind = TracksideKind.valueOf(x.getString("Kind"));
        var d =
            new Device(
                x.getUUID("Id"),
                x.getUUID("Owner"),
                x.getString("Scope"),
                BlockPos.of(x.getLong("Anchor")),
                kind,
                x.getUUID("Edge"),
                x.getDouble("Offset"),
                x.getBoolean("Reverse"));
        d.settings(x.getCompound("Settings"));
        d.health = x.getInt("Health");
        if (d.health <= 0 || d.health > kind.health || data.devices.putIfAbsent(d.id, d) != null)
          throw new IllegalArgumentException("Duplicate device or health");
      } catch (RuntimeException invalid) {
        data.quarantined.add(x.copy());
      }
    }
    for (var raw : n.getList("Groups", Tag.TAG_COMPOUND)) {
      var x = (CompoundTag) raw;
      var group =
          new Group(
              x.getString("Scope"),
              x.getString("Name"),
              x.getLong("Version"),
              RailScheduleCodec.load(x.getCompound("Schedule")));
      data.groups.put(group.scope + "\n" + group.name, group);
    }
    for (var raw : n.getList("Interrupts", Tag.TAG_COMPOUND)) {
      var x = (CompoundTag) raw;
      var definition = RailScheduleCodec.interrupt(x.getCompound("Definition"));
      data.interrupts.put(definition.id(), new SharedInterrupt(x.getString("Scope"), definition));
    }
    for (var raw : n.getList("Returns", Tag.TAG_COMPOUND)) {
      var x = (CompoundTag) raw;
      var stack = ItemStack.parseOptional(r, x.getCompound("Stack"));
      if (stack.isEmpty() || stack.getCount() != 1)
        throw new IllegalArgumentException("Return owner");
      var claim =
          new Return(
              x.getUUID("Id"),
              stack,
              new Vec3(x.getDouble("X"), x.getDouble("Y"), x.getDouble("Z")));
      if (!Double.isFinite(claim.position.x + claim.position.y + claim.position.z)
          || data.returns.putIfAbsent(claim.id, claim) != null)
        throw new IllegalArgumentException("Duplicate return");
    }
    data.returnQueue.addAll(data.returns.keySet());
    for (var raw : n.getList("Remnants", Tag.TAG_COMPOUND)) {
      var x = (CompoundTag) raw;
      var p = BlockPos.of(x.getLong("Position"));
      var remnant =
          new Remnant(
              p, BlockPos.of(x.getLong("Anchor")), TracksideKind.valueOf(x.getString("Kind")));
      long key = new net.minecraft.world.level.ChunkPos(p).toLong();
      data.remnants.computeIfAbsent(key, k -> new LinkedHashMap<>()).put(p, remnant);
      data.cleanChunks.add(key);
    }
    data.quarantined.addAll(n.getList("Quarantine", Tag.TAG_COMPOUND));
    return data;
  }

  public CompoundTag save(CompoundTag n, HolderLookup.Provider r) {
    n.putInt("Schema", 1);
    n.putInt("NextTrainId", nextTrainId);
    if (nextDispatchOrder != 1) n.putLong("NextDispatchOrder", nextDispatchOrder);
    var list = new ListTag();
    for (var d : devices.values()) {
      var x = new CompoundTag();
      x.putUUID("Id", d.id);
      x.putUUID("Owner", d.owner);
      x.putString("Scope", d.scope);
      x.putLong("Anchor", d.anchor.asLong());
      x.putString("Kind", d.kind.name());
      x.putUUID("Edge", d.point.edge());
      x.putDouble("Offset", d.point.offset());
      x.putBoolean("Reverse", d.point.reverse());
      x.putInt("Health", d.health);
      x.put("Settings", d.settings());
      list.add(x);
    }
    n.put("Devices", list);
    var gs = new ListTag();
    for (var g : groups.values()) {
      var x = new CompoundTag();
      x.putString("Scope", g.scope);
      x.putString("Name", g.name);
      x.putLong("Version", g.version);
      x.put("Schedule", RailScheduleCodec.save(g.schedule));
      gs.add(x);
    }
    n.put("Groups", gs);
    var ins = new ListTag();
    for (var def : interrupts.values()) {
      var x = new CompoundTag();
      x.putString("Scope", def.scope);
      x.put("Definition", RailScheduleCodec.save(def.definition));
      ins.add(x);
    }
    n.put("Interrupts", ins);
    var rs = new ListTag();
    for (var claim : returns.values()) {
      var x = new CompoundTag();
      x.putUUID("Id", claim.id);
      x.put("Stack", claim.stack.save(r));
      x.putDouble("X", claim.position.x);
      x.putDouble("Y", claim.position.y);
      x.putDouble("Z", claim.position.z);
      rs.add(x);
    }
    n.put("Returns", rs);
    var pieces = new ListTag();
    for (var chunk : remnants.values())
      for (var remnant : chunk.values()) {
        var x = new CompoundTag();
        x.putLong("Position", remnant.position.asLong());
        x.putLong("Anchor", remnant.anchor.asLong());
        x.putString("Kind", remnant.kind.name());
        pieces.add(x);
      }
    n.put("Remnants", pieces);
    if (!quarantined.isEmpty()) n.put("Quarantine", quarantined.copy());
    return n;
  }
}
