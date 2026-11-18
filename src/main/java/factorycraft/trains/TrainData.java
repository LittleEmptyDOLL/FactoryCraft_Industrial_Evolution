package factorycraft.trains;

import factorycraft.rail.*;
import factorycraft.simulation.rail.*;
import factorycraft.simulation.trains.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Sole dimension-local owner. Entities persist only IDs/visual projection, never these resources.
 */
public final class TrainData extends SavedData {
  public static final int MAX_CARS = 64;

  public enum Status {
    STOPPED,
    DRIVING,
    BRAKING,
    NO_FUEL,
    END_OF_TRACK,
    UNLOADED,
    BLOCKED,
    RESERVED,
    BROKEN_TRACK,
    DESTROYED
  }

  public static final class Train {
    public final UUID id;
    public UUID owner;
    public String scope;
    public final ArrayList<TrainCar> cars = new ArrayList<>();
    public TrainPath path;
    public double head, speed, distance;
    public int direction = 1, desired = 0, branch;
    public boolean brake, provisional;
    public long lastInput = Long.MIN_VALUE;
    public UUID driver;
    long checkedTick = Long.MIN_VALUE, checkedEpoch = -1;
    boolean checkedAvailable;
    public Status status = Status.STOPPED;

    public Train(UUID id, UUID owner, String scope, TrainPath path, double head) {
      this.id = id;
      this.owner = owner;
      this.scope = scope;
      this.path = path;
      this.head = head;
    }

    public double center(int index) {
      return head - index * StockSpec.SPACING;
    }

    public double rear() {
      return center(cars.size() - 1) - StockSpec.HALF_LENGTH;
    }

    public double front() {
      return head + StockSpec.HALF_LENGTH;
    }
  }

  private final LinkedHashMap<UUID, Train> trains = new LinkedHashMap<>();
  private final Map<UUID, Train> byCar = new HashMap<>();
  private final Map<Long, Set<UUID>> byChunk = new HashMap<>();
  private final Map<UUID, Set<Long>> trainChunks = new HashMap<>();
  final Set<UUID> active = new LinkedHashSet<>();
  final Set<Long> pendingChunks = new LinkedHashSet<>();
  public final TrackOccupancy occupancy = new TrackOccupancy();
  public final SpatialReservations spatial = new SpatialReservations();

  private record Wreck(
      UUID id, net.minecraft.world.item.ItemStack item, net.minecraft.world.phys.Vec3 at) {}

  private final LinkedHashMap<UUID, Wreck> wrecks = new LinkedHashMap<>();
  private final Set<UUID> spawningWrecks = new HashSet<>();
  private final ArrayDeque<UUID> wreckQueue = new ArrayDeque<>();

  /** Retired stock transfers to a persistent recovery claim before native spawn callbacks. */
  void wreck(net.minecraft.world.item.ItemStack item, net.minecraft.world.phys.Vec3 at) {
    var id = UUID.randomUUID();
    wrecks.put(id, new Wreck(id, item, at));
    wreckQueue.addLast(id);
    setDirty();
    flushWrecks();
  }

  public int pendingWrecks() {
    return wrecks.size();
  }

  private void flushWrecks() {
    if (wrecks.isEmpty()) return;
    int work = Math.min(8, wreckQueue.size());
    while (work-- > 0) {
      var id = wreckQueue.pollFirst();
      var w = wrecks.get(id);
      if (w == null) continue;
      if (!spawningWrecks.add(w.id)) {
        wreckQueue.addLast(id);
        continue;
      }
      try {
        var p = BlockPos.containing(w.at);
        if (!level.hasChunkAt(p)
            || !level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(p))) continue;
        var old = level.getEntity(w.id);
        if (old != null) continue; // UUID collision: never take an unrelated entity's resources.
        var drop =
            new net.minecraft.world.entity.item.ItemEntity(
                level, w.at.x, w.at.y, w.at.z, w.item.copy());
        drop.setUUID(w.id);
        if (level.addFreshEntity(drop)) {
          wrecks.remove(w.id);
          setDirty();
        }
      } finally {
        spawningWrecks.remove(w.id);
        if (wrecks.containsKey(w.id)) wreckQueue.addLast(w.id);
      }
    }
  }

  ServerLevel level;
  public long ticks, worldReads, snapshots, availabilityEpoch, simulationNs, maxTickNs;

  public static TrainData get(ServerLevel level) {
    var d =
        level
            .getDataStorage()
            .computeIfAbsent(
                new SavedData.Factory<>(TrainData::new, TrainData::load), "factorycraft_trains");
    d.level = level;
    return d;
  }

  public Collection<Train> trains() {
    return Collections.unmodifiableCollection(trains.values());
  }

  public Train train(UUID id) {
    return trains.get(id);
  }

  public Train owner(UUID car) {
    return byCar.get(car);
  }

  public TrainCar car(UUID id) {
    var t = byCar.get(id);
    if (t == null) return null;
    for (var c : t.cars) if (c.id.equals(id)) return c;
    return null;
  }

  public boolean stopped(Train t) {
    return trains.get(t.id) == t
        && !t.provisional
        && t.speed == 0
        && t.status != Status.UNLOADED
        && t.status != Status.BROKEN_TRACK
        && TrainWorld.available(this, t);
  }

  public void add(Train t) {
    if (trains.containsKey(t.id) || t.cars.isEmpty() || t.cars.size() > MAX_CARS)
      throw new IllegalArgumentException("Train identity/count");
    for (var c : t.cars)
      if (byCar.containsKey(c.id)) throw new IllegalArgumentException("Duplicate carriage owner");
    if (!occupancy.canReserve(t.id, TrackOccupancy.span(t.path, t.rear(), t.front())))
      throw new IllegalArgumentException("Occupied train intervals");
    trains.put(t.id, t);
    for (var c : t.cars) byCar.put(c.id, t);
    index(t);
    occupancy.reserve(t.id, TrackOccupancy.span(t.path, t.rear(), t.front()));
    active.add(t.id);
    setDirty();
  }

  /** Retires membership before any caller drops items or reassigns existing car objects. */
  void retire(Train t) {
    if (trains.get(t.id) != t) throw new IllegalStateException("Stale train retirement");
    trains.remove(t.id);
    for (var c : t.cars) byCar.remove(c.id, t);
    occupancy.release(t.id);
    spatial.release(t.id);
    active.remove(t.id);
    unindex(t.id);
    setDirty();
  }

  void unindex(UUID id) {
    var old = trainChunks.remove(id);
    if (old != null)
      for (long key : old) {
        var set = byChunk.get(key);
        set.remove(id);
        if (set.isEmpty()) byChunk.remove(key);
      }
  }

  void index(Train t) {
    t.checkedTick = Long.MIN_VALUE;
    unindex(t.id);
    var keys = new HashSet<Long>();
    var graph = RailwayData.get(level).graph;
    for (int i = 0; i < t.cars.size(); i++)
      try {
        var p = BogiePose.sample(t.path, graph, t.center(i), false).center();
        int x = (int) Math.floor(p.x()) >> 4, z = (int) Math.floor(p.z()) >> 4;
        for (int a = -1; a <= 1; a++)
          for (int b = -1; b <= 1; b++)
            keys.add(net.minecraft.world.level.ChunkPos.asLong(x + a, z + b));
      } catch (IllegalArgumentException ignored) {
        var point = t.cars.get(i).recovery;
        if (point != null)
          keys.add(
              net.minecraft.world.level.ChunkPos.asLong(
                  (int) Math.floor(point.x()) >> 4, (int) Math.floor(point.z()) >> 4));
      }
    trainChunks.put(t.id, keys);
    for (long key : keys) byChunk.computeIfAbsent(key, k -> new HashSet<>()).add(t.id);
  }

  boolean relevantChunk(long key) {
    return byChunk.containsKey(key);
  }

  public void wakeChunk(long key) {
    availabilityEpoch++;
    for (var id : byChunk.getOrDefault(key, Set.of())) active.add(id);
  }

  public void unloadChunk(long key) {
    availabilityEpoch++;
    for (var id : byChunk.getOrDefault(key, Set.of())) {
      var t = trains.get(id);
      if (t != null) {
        t.status = Status.UNLOADED;
        for (var car : t.cars)
          if (level.getEntity(car.id) instanceof StockEntity view) view.pauseVisual();
        active.remove(id);
      }
    }
  }

  public boolean occupied(UUID edge) {
    return !occupancy.owners(edge).isEmpty();
  }

  public void trackRemoved(UUID edge) {
    availabilityEpoch++;
    for (var id : occupancy.owners(edge)) {
      var t = trains.get(id);
      if (t != null) {
        for (var car : t.cars)
          if (level.getEntity(car.id) instanceof StockEntity view) view.pauseVisual();
        t.status = Status.BROKEN_TRACK;
        t.speed = 0;
        t.desired = 0;
        active.remove(id);
      }
    }
    setDirty();
  }

  public void tick() {
    ensureIndex();
    if (!level.tickRateManager().runsNormally()) return;
    flushWrecks();
    int checks = 0;
    for (var it = pendingChunks.iterator(); it.hasNext() && checks++ < 64; ) {
      long key = it.next();
      if (level.getChunkSource().getChunkNow((int) key, (int) (key >> 32)) != null
          && level.shouldTickBlocksAt(key)
          && level.areEntitiesLoaded(key)) {
        it.remove();
        wakeChunk(key);
      }
    }
    long started = System.nanoTime();
    int work = 0;
    for (var id : List.copyOf(active)) {
      var t = trains.get(id);
      if (t == null) continue;
      if (++work > 128) break;
      active.remove(id);
      active.add(id);
      TrainSimulation.tick(this, t);
      ticks++;
    }
    long elapsed = System.nanoTime() - started;
    simulationNs += elapsed;
    maxTickNs = Math.max(maxTickNs, elapsed);
  }

  public static TrainData load(CompoundTag n, HolderLookup.Provider r) {
    if (n.getInt("Schema") != 1) throw new IllegalArgumentException("Train schema");
    var d = new TrainData();
    for (var raw : n.getList("Trains", Tag.TAG_COMPOUND)) {
      var tag = (CompoundTag) raw;
      var legs = new ArrayList<TrainPath.Leg>();
      for (var value : tag.getList("Route", Tag.TAG_COMPOUND)) {
        var x = (CompoundTag) value;
        legs.add(
            new TrainPath.Leg(
                new RailGraph.Directed(x.getUUID("Edge"), x.getBoolean("Reverse")),
                x.getDouble("Begin"),
                x.getDouble("Length")));
      }
      var t =
          new Train(
              tag.getUUID("Id"),
              tag.getUUID("Owner"),
              tag.getString("Scope"),
              new TrainPath(legs),
              tag.getDouble("Head"));
      t.speed = tag.getDouble("Speed");
      t.distance = tag.getDouble("Distance");
      t.direction = tag.getInt("Direction");
      if (!Double.isFinite(t.head + t.speed + t.distance)
          || t.speed < 0
          || t.speed > 1.38
          || Math.abs(t.direction) != 1) throw new IllegalArgumentException("Train motion save");
      for (var value : tag.getList("Cars", Tag.TAG_COMPOUND))
        t.cars.add(TrainCar.load((CompoundTag) value, r, d::setDirty, false));
      if (t.cars.isEmpty()
          || t.cars.size() > MAX_CARS
          || !t.path.contains(t.rear())
          || !t.path.contains(t.front())
          || d.trains.putIfAbsent(t.id, t) != null)
        throw new IllegalArgumentException("Train route/body");
      for (var c : t.cars)
        if (d.byCar.putIfAbsent(c.id, t) != null)
          throw new IllegalArgumentException("Duplicate car UUID");
      if (!d.occupancy.reserve(t.id, TrackOccupancy.span(t.path, t.rear(), t.front())))
        throw new IllegalArgumentException("Overlapping saved train owners");
      d.active.add(t.id);
    }
    for (var raw : n.getList("Wrecks", Tag.TAG_COMPOUND)) {
      var x = (CompoundTag) raw;
      var item = net.minecraft.world.item.ItemStack.parseOptional(r, x.getCompound("Item"));
      var at =
          new net.minecraft.world.phys.Vec3(x.getDouble("X"), x.getDouble("Y"), x.getDouble("Z"));
      var id = x.getUUID("Id");
      if (item.isEmpty()
          || item.getCount() != 1
          || !Double.isFinite(at.x + at.y + at.z)
          || d.byCar.containsKey(id)
          || d.wrecks.putIfAbsent(id, new Wreck(id, item, at)) != null)
        throw new IllegalArgumentException("Wreck owner");
      d.wreckQueue.addLast(id);
    }
    d.setDirty(false);
    return d;
  }

  private boolean indexed;

  void ensureIndex() {
    if (!indexed) {
      indexed = true;
      for (var t : trains.values()) index(t);
    }
  }

  public CompoundTag save(CompoundTag n, HolderLookup.Provider r) {
    n.putInt("Schema", 1);
    var list = new ListTag();
    for (var t : trains.values()) {
      if (t.provisional) continue;
      var tag = new CompoundTag();
      tag.putUUID("Id", t.id);
      tag.putUUID("Owner", t.owner);
      tag.putString("Scope", t.scope);
      tag.putDouble("Head", t.head);
      tag.putDouble("Speed", t.speed);
      tag.putDouble("Distance", t.distance);
      tag.putInt("Direction", t.direction);
      var route = new ListTag();
      for (var l : t.path.legs()) {
        var x = new CompoundTag();
        x.putUUID("Edge", l.edge().edge());
        x.putBoolean("Reverse", l.edge().reverse());
        x.putDouble("Begin", l.begin());
        x.putDouble("Length", l.length());
        route.add(x);
      }
      tag.put("Route", route);
      var cars = new ListTag();
      for (var c : t.cars) cars.add(c.save(r));
      tag.put("Cars", cars);
      list.add(tag);
    }
    n.put("Trains", list);
    var recoveries = new ListTag();
    for (var w : wrecks.values()) {
      var x = new CompoundTag();
      x.putUUID("Id", w.id);
      x.put("Item", w.item.save(r));
      x.putDouble("X", w.at.x);
      x.putDouble("Y", w.at.y);
      x.putDouble("Z", w.at.z);
      recoveries.add(x);
    }
    n.put("Wrecks", recoveries);
    return n;
  }
}
