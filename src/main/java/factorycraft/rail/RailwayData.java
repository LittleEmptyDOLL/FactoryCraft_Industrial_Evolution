package factorycraft.rail;

import factorycraft.simulation.rail.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;

/** One saved owner per segment and paid item. World cells hold only nonowning geometry/IDs. */
public final class RailwayData extends SavedData {
  public static final class Segment {
    public final UUID id;
    public final RailCurve curve;
    public final RailFootprint footprint;
    private Map<RailFootprint.Cell, List<RailMesh.Face>> mesh;
    private final List<ItemStack> paid;
    final Set<BlockPos> live = new HashSet<>();

    public Segment(UUID id, RailCurve curve, List<ItemStack> paid) {
      if (!curve.validated()) throw new IllegalArgumentException("Unvalidated railway ghost");
      this.id = id;
      this.curve = curve;
      this.footprint = new RailFootprint(curve);
      this.paid = paid.stream().map(ItemStack::copy).toList();
    }

    List<RailMesh.Face> faces(BlockPos p) {
      if (mesh == null) mesh = RailMesh.build(curve);
      return mesh.getOrDefault(cell(p), List.of());
    }

    public int paidCount() {
      return paid.stream().mapToInt(ItemStack::getCount).sum();
    }
  }

  public final RailGraph graph = new RailGraph();
  private final Map<UUID, Segment> segments = new LinkedHashMap<>();
  private final Map<BlockPos, LinkedHashSet<UUID>> cells = new HashMap<>();
  private final Map<Long, LinkedHashSet<UUID>> chunks = new HashMap<>();
  private ServerLevel level;
  int writing;

  public static RailwayData get(ServerLevel level) {
    var d =
        level
            .getDataStorage()
            .computeIfAbsent(
                new SavedData.Factory<>(RailwayData::new, RailwayData::load),
                "factorycraft_railways");
    d.level = level;
    return d;
  }

  public static RailFootprint.Cell cell(BlockPos p) {
    return new RailFootprint.Cell(p.getX(), p.getY(), p.getZ());
  }

  public static BlockPos pos(RailFootprint.Cell c) {
    return new BlockPos(c.x(), c.y(), c.z());
  }

  public Segment segment(UUID id) {
    return segments.get(id);
  }

  public Collection<Segment> segments() {
    return Collections.unmodifiableCollection(segments.values());
  }

  public Set<UUID> at(BlockPos p) {
    var ids = cells.get(p);
    return ids == null ? Set.of() : Collections.unmodifiableSet(ids);
  }

  public boolean reserved(BlockPos p) {
    var ids = chunks.get(new net.minecraft.world.level.ChunkPos(p).toLong());
    if (ids == null) return false;
    var c = cell(p);
    for (var id : ids) if (segments.get(id).footprint.clearance.contains(c)) return true;
    return false;
  }

  public Set<UUID> candidates(RailFootprint footprint) {
    var ids = new LinkedHashSet<UUID>();
    for (long c : footprint.chunks()) ids.addAll(chunks.getOrDefault(c, new LinkedHashSet<>()));
    return ids;
  }

  public void add(Segment segment) {
    if (segments.containsKey(segment.id))
      throw new IllegalArgumentException("Duplicate saved railway");
    segments.put(segment.id, segment);
    graph.add(new RailGraph.Edge(segment.id, segment.curve));
    for (var c : segment.footprint.bed.keySet())
      cells.computeIfAbsent(pos(c), k -> new LinkedHashSet<>()).add(segment.id);
    for (long c : segment.footprint.chunks())
      chunks.computeIfAbsent(c, k -> new LinkedHashSet<>()).add(segment.id);
    setDirty();
  }

  public void refresh(BlockPos p) {
    if (!level.hasChunkAt(p)) return;
    var expected = at(p);
    if (expected.isEmpty()) {
      if (level.getBlockState(p).is(RailContent.BED.get())) {
        writing++;
        try {
          level.removeBlock(p, false);
        } finally {
          writing--;
        }
      }
      return;
    }
    if (!(level.getBlockEntity(p) instanceof RailCellBlockEntity be)) return;
    var faces = new ArrayList<RailMesh.Face>();
    var boxes = new ArrayList<RailFootprint.Box>();
    for (var id : expected) {
      var s = segments.get(id);
      faces.addAll(s.faces(p));
      boxes.addAll(s.footprint.bed.getOrDefault(cell(p), List.of()));
      s.live.add(p.immutable());
    }
    be.bind(
        expected,
        faces,
        boxes,
        expected.stream().map(segments::get).map(RailDescriptor::new).toList());
  }

  public void loaded(RailCellBlockEntity be) {
    var p = be.getBlockPos();
    if (!level.hasChunkAt(p)
        || level.getBlockEntity(p) != be
        || !level.getBlockState(p).is(RailContent.BED.get())) return;
    var expected = at(p);
    if (expected.isEmpty()
        || !expected.equals(be.segments())
        || be.projectionVersion != RailCellBlockEntity.PROJECTION_VERSION) {
      refresh(p);
      return;
    }
    for (var id : expected) segments.get(id).live.add(p.immutable());
  }

  public void unloaded(RailCellBlockEntity be) {
    for (var id : be.segments()) {
      var s = segments.get(id);
      if (s != null) s.live.remove(be.getBlockPos());
    }
  }

  public void unloadChunk(long key) {
    for (var id : chunks.getOrDefault(key, new LinkedHashSet<>())) {
      var s = segments.get(id);
      s.live.removeIf(p -> new net.minecraft.world.level.ChunkPos(p).toLong() == key);
    }
  }

  public boolean available(UUID id) {
    var s = segments.get(id);
    if (s == null || s.live.size() != s.footprint.bed.size()) return false;
    for (long key : s.footprint.chunks())
      if (level.getChunkSource().getChunkNow((int) key, (int) (key >> 32)) == null) return false;
    return true;
  }

  /** Retire the sole refund claim before callbacks, projections or inventory observers. */
  public List<ItemStack> remove(UUID id) {
    var s = segments.remove(id);
    if (s == null) return List.of();
    graph.remove(id);
    RailPlanner.removed(level, id);
    setDirty();
    for (var c : s.footprint.bed.keySet()) {
      var p = pos(c);
      var ids = cells.get(p);
      ids.remove(id);
      if (ids.isEmpty()) cells.remove(p);
    }
    for (long c : s.footprint.chunks()) {
      var ids = chunks.get(c);
      ids.remove(id);
      if (ids.isEmpty()) chunks.remove(c);
    }
    writing++;
    try {
      for (var c : s.footprint.bed.keySet()) refresh(pos(c));
    } finally {
      writing--;
    }
    return s.paid.stream().map(ItemStack::copy).toList();
  }

  public void destroyed(BlockPos p) {
    if (writing > 0) return;
    for (var id : List.copyOf(at(p)))
      for (var stack : remove(id)) Block.popResource(level, p, stack);
  }

  public UUID nearest(BlockPos p, net.minecraft.world.phys.Vec3 hit) {
    UUID result = null;
    double best = Double.POSITIVE_INFINITY;
    for (var id : at(p)) {
      var s = segments.get(id);
      for (double d = 0; d <= s.curve.length(); d += .5) {
        var q = s.curve.sample(d, false).position();
        double distance = hit.distanceToSqr(q.x(), q.y(), q.z());
        if (distance < best) {
          best = distance;
          result = id;
        }
      }
    }
    return result;
  }

  public RailEnd endpoint(BlockPos p, net.minecraft.world.phys.Vec3 hit, boolean source) {
    RailEnd best = null;
    double distance = 4;
    for (var id : at(p)) {
      var c = segments.get(id).curve;
      for (var e : List.of(c.start, c.end)) {
        var q = e.point();
        double d = hit.distanceToSqr(q.x(), q.y(), q.z());
        if (d < distance) {
          distance = d;
          best = source ? (e == c.start ? e.reverse() : e) : (e == c.start ? e : e.reverse());
        }
      }
    }
    return best;
  }

  public static RailwayData load(CompoundTag n, HolderLookup.Provider registries) {
    if (n.getInt("Schema") != 1) throw new IllegalArgumentException("Unsupported railway save");
    var d = new RailwayData();
    for (var raw : n.getList("Segments", Tag.TAG_COMPOUND)) {
      var t = (CompoundTag) raw;
      var paid = new ArrayList<ItemStack>();
      for (var p : t.getList("Paid", Tag.TAG_COMPOUND)) {
        var item = ItemStack.parseOptional(registries, (CompoundTag) p);
        if (item.isEmpty() || !item.is(RailContent.RAIL.get()))
          throw new IllegalArgumentException("Rail material claim");
        paid.add(item);
      }
      var segment = new Segment(t.getUUID("Id"), RailCodec.curve(t.getCompound("Curve")), paid);
      if (segment.paidCount() != segment.curve.cost())
        throw new IllegalArgumentException("Rail price mismatch");
      d.add(segment);
    }
    d.setDirty(false);
    return d;
  }

  public CompoundTag save(CompoundTag n, HolderLookup.Provider registries) {
    n.putInt("Schema", 1);
    var list = new ListTag();
    for (var s : segments.values()) {
      var t = new CompoundTag();
      t.putUUID("Id", s.id);
      t.put("Curve", RailCodec.curve(s.curve));
      var paid = new ListTag();
      for (var item : s.paid) paid.add(item.save(registries));
      t.put("Paid", paid);
      list.add(t);
    }
    n.put("Segments", list);
    return n;
  }
}
