package factorycraft.simulation.fluids;

import java.math.BigInteger;
import java.util.*;
import java.util.function.*;

/**
 * One pooled owner per compatible loaded component; physical cells are exact lazy checkpoint
 * shares.
 */
public final class PipelineNetwork<K> {
  /** Intersection of incompatible filters: retains contents but admits no hydraulic flow. */
  public static final Object REJECT_ALL = new Object();

  public enum Status {
    REBUILDING,
    READY,
    TOO_LONG,
    FILTER_CONFLICT,
    OVERFULL
  }

  public record Site(long order, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {}

  public final class Segment {
    public final long id = ++nextId;
    private final ArrayList<Node> members = new ArrayList<>();
    private final Set<Node> boundaries = Collections.newSetFromMap(new IdentityHashMap<>());
    private K key, lastKey;
    private Object filter;
    private long amount, capacity;
    private boolean valid;
    private int minX = Integer.MAX_VALUE,
        minY = Integer.MAX_VALUE,
        minZ = Integer.MAX_VALUE,
        maxX = Integer.MIN_VALUE,
        maxY = Integer.MIN_VALUE,
        maxZ = Integer.MIN_VALUE,
        conflicts;
    private Status status = Status.REBUILDING;

    public long amount() {
      return amount;
    }

    public long capacity() {
      return capacity;
    }

    public K key() {
      return key;
    }

    public long id() {
      return id;
    }

    public boolean valid() {
      return valid;
    }

    public Status status() {
      return status;
    }

    public boolean flowing() {
      return valid && (status == Status.READY || status == Status.OVERFULL);
    }

    public int conflicts() {
      return conflicts;
    }

    public int size() {
      return members.size();
    }

    public List<Node> members() {
      return Collections.unmodifiableList(members);
    }

    public int minX() {
      return minX;
    }

    public int maxX() {
      return maxX;
    }

    public int minY() {
      return minY;
    }

    public int maxY() {
      return maxY;
    }

    public int minZ() {
      return minZ;
    }

    public int maxZ() {
      return maxZ;
    }

    private boolean accepts(K fluid) {
      return (key == null || key.equals(fluid))
          && (filter == null || filter.equals(type.apply(fluid)));
    }

    private int share(Node n) {
      return Math.toIntExact(
          ratio(amount, n.prefix + n.raw.capacity(), capacity) - ratio(amount, n.prefix, capacity));
    }

    private void adjust(K fluid, int delta) {
      if (delta > 0 && (!accepts(fluid) || delta > capacity - amount)
          || delta < 0 && -(long) delta > amount)
        throw new IllegalStateException("Pipeline reservation invalid");
      amount += delta;
      key = amount == 0 ? null : fluid;
      if (status == Status.READY || status == Status.OVERFULL)
        status = amount > capacity ? Status.OVERFULL : Status.READY;
    }
  }

  public final class Node {
    public final Site site;
    public final FluidCell<K> raw;
    private final TreeSet<Node> edges = new TreeSet<>(Comparator.comparingLong(n -> n.site.order));
    private final Map<Object, FluidCell<K>> ports = new HashMap<>();
    private final FluidCell<K> owned;
    private Segment segment;
    private Object filter;
    private boolean active = true, removed;
    private long prefix;

    private Node(Site site, FluidCell<K> cell) {
      this.site = site;
      raw = cell;
      owned = new Owned(this);
    }

    public Segment segment() {
      return segment;
    }

    public boolean active() {
      return active && !removed;
    }

    public FluidCell<K> owned() {
      return owned;
    }

    public FluidCell<K> port(Object physical) {
      return ports.computeIfAbsent(physical, p -> new Port(this));
    }

    public boolean ready() {
      return active() && segment != null && segment.flowing();
    }
  }

  private final class Owned extends FluidCell<K> {
    final Node n;

    Owned(Node n) {
      super(n.raw.capacity(), () -> {});
      this.n = n;
    }

    public int amount() {
      return n.segment != null && n.segment.valid ? n.segment.share(n) : n.raw.amount();
    }

    public int capacity() {
      return n.raw.capacity();
    }

    public K key() {
      return amount() == 0 ? null : n.segment != null ? n.segment.key : n.raw.key();
    }

    public int space() {
      return Math.min(
          super.space(),
          n.segment == null ? Integer.MAX_VALUE : clamp(n.segment.capacity - n.segment.amount));
    }

    public boolean accepts(K k) {
      return n.segment == null ? n.raw.accepts(k) : n.segment.accepts(k);
    }

    public Object ownerToken() {
      return n.segment == null ? n.raw : n.segment;
    }

    protected void adjust(K k, int delta) {
      if (n.segment == null) n.raw.adjust(k, delta);
      else n.segment.adjust(delta < 0 ? n.segment.key : k, delta);
    }

    protected void setContents(K k, int value) {
      if (n.segment == null) n.raw.restore(k, value);
      else {
        int delta = value - amount();
        if (delta != 0) {
          var g = n.segment;
          if (value > 0 && !g.accepts(k))
            throw new IllegalArgumentException("Checkpoint fluid conflicts with live segment");
          g.amount += delta;
          g.key = g.amount == 0 ? null : delta > 0 ? k : g.key;
          if (g.status == Status.READY || g.status == Status.OVERFULL)
            g.status = g.amount > g.capacity ? Status.OVERFULL : Status.READY;
        }
      }
    }

    void notifyChanged() {
      mutated(n);
    }
  }

  private final class Port extends FluidCell<K> {
    final Node n;

    Port(Node n) {
      super(1, () -> {});
      this.n = n;
    }

    private boolean readable() {
      return n.active() && n.segment != null && n.segment.valid;
    }

    public int outputLimit(int requested) {
      return n.ready() ? super.outputLimit(requested) : 0;
    }

    public int capacity() {
      return readable() ? clamp(n.segment.capacity) : 0;
    }

    public int amount() {
      return readable() ? clamp(n.segment.amount) : 0;
    }

    public K key() {
      return readable() ? n.segment.key : null;
    }

    public int space() {
      return n.ready() ? clamp(n.segment.capacity - n.segment.amount) : 0;
    }

    public boolean accepts(K k) {
      return n.ready() && n.segment.accepts(k);
    }

    public Object ownerToken() {
      return n.segment == null ? n : n.segment;
    }

    protected void adjust(K k, int delta) {
      if (!n.ready()) throw new IllegalStateException("Unavailable pipeline");
      n.segment.adjust(delta < 0 ? n.segment.key : k, delta);
    }

    protected void setContents(K k, int amount) {
      throw new UnsupportedOperationException("Port is not a checkpoint owner");
    }

    void notifyChanged() {
      mutated(n);
    }
  }

  private final class Build {
    final long revision = PipelineNetwork.this.revision;
    final Segment group = new Segment();
    final ArrayDeque<Node> queue = new ArrayDeque<>();
    final Set<Node> seen = Collections.newSetFromMap(new IdentityHashMap<>());

    Build(Node root) {
      queue.add(root);
    }
  }

  private final NavigableMap<Long, Node> nodes = new TreeMap<>();
  private final TreeSet<Node> pending = new TreeSet<>(Comparator.comparingLong(n -> n.site.order));
  private final Function<K, Object> type;
  private final Consumer<Segment> changed;
  private final Consumer<Node> localChanged;
  private Build build;
  private long nextId, revision, visits, rebuilds;
  private int edges;

  public PipelineNetwork(
      Function<K, Object> type, Consumer<Segment> changed, Consumer<Node> localChanged) {
    this.type = type;
    this.changed = changed;
    this.localChanged = localChanged;
  }

  public PipelineNetwork() {
    this(k -> k, s -> {}, n -> {});
  }

  public Node add(Site site, FluidCell<K> cell) {
    if (nodes.containsKey(site.order))
      throw new IllegalArgumentException("Duplicate pipeline owner");
    var n = new Node(site, cell);
    nodes.put(site.order, n);
    pending.add(n);
    revision++;
    return n;
  }

  private void invalidate(Segment s) {
    if (s == null || !s.valid) return;
    // Calculate all projections before retiring the sole pool, then expose independent frozen
    // owners.
    for (var n : s.members) n.raw.restore(s.share(n) == 0 ? null : s.key, s.share(n));
    s.valid = false;
    for (var n : s.members) {
      n.segment = null;
      if (n.active()) pending.add(n);
    }
    s.amount = 0;
    s.key = null;
    changed.accept(s);
    for (var n : s.members) localChanged.accept(n);
  }

  public void topology(Node n) {
    if (n == null || n.removed) return;
    revision++;
    invalidate(n.segment);
    if (n.active()) pending.add(n);
    for (var e : n.edges) {
      invalidate(e.segment);
      if (e.active()) pending.add(e);
    }
  }

  public void connect(Node a, Node b) {
    if (a == b || a.removed || b.removed || a.edges.contains(b)) return;
    topology(a);
    topology(b);
    a.edges.add(b);
    b.edges.add(a);
    edges++;
  }

  public void disconnect(Node a, Node b) {
    if (!a.edges.remove(b)) return;
    b.edges.remove(a);
    edges--;
    topology(a);
    topology(b);
  }

  public void active(Node n, boolean on) {
    if (n.removed || n.active == on) return;
    topology(n);
    n.active = on;
    if (!on) pending.remove(n);
    else pending.add(n);
  }

  public void filter(Node n, Object filter) {
    if (Objects.equals(n.filter, filter)) return;
    topology(n);
    n.filter = filter;
  }

  public void remove(Node n) {
    if (n.removed) return;
    topology(n);
    for (var e : List.copyOf(n.edges)) disconnect(n, e);
    n.removed = true;
    n.active = false;
    nodes.remove(n.site.order);
    pending.remove(n);
  }

  public void externalChanged(Node n) {
    topology(n);
    localChanged.accept(n);
  }

  private void mutated(Node n) {
    var s = n.segment;
    if (s == null) {
      topology(n);
      localChanged.accept(n);
      return;
    }
    changed.accept(s);
    if (!Objects.equals(s.lastKey, s.key)) {
      s.lastKey = s.key;
      if (s.conflicts > 0) {
        topology(n);
        for (var boundary : s.boundaries) topology(boundary);
      }
    }
  }

  private boolean compatible(Segment s, Node n) {
    K k = n.raw.key();
    Object f = n.filter;
    return (s.key == null || k == null || s.key.equals(k))
        && (s.filter == null || f == null || s.filter.equals(f))
        && (k == null || s.filter == null || s.filter.equals(type.apply(k)))
        && (s.key == null || f == null || f.equals(type.apply(s.key)));
  }

  public int advance(int budget) {
    int work = 0;
    while (work < budget) {
      if (build != null && build.revision != revision) build = null;
      if (build == null) {
        while (!pending.isEmpty() && (!pending.first().active() || pending.first().segment != null))
          pending.pollFirst();
        if (pending.isEmpty()) break;
        build = new Build(pending.first());
      }
      var b = build;
      if (b.queue.isEmpty()) {
        var s = b.group;
        long prefix = 0;
        s.members.sort(Comparator.comparingLong(n -> n.site.order));
        for (var n : s.members) {
          n.prefix = prefix;
          prefix += n.raw.capacity();
          n.segment = s;
          n.raw.restore(null, 0);
          pending.remove(n);
        }
        s.lastKey = s.key;
        s.valid = true;
        s.status =
            s.maxX - s.minX + 1 > FluidUnits.MAX_HORIZONTAL_SPAN
                    || s.maxZ - s.minZ + 1 > FluidUnits.MAX_HORIZONTAL_SPAN
                ? Status.TOO_LONG
                : s.filter == REJECT_ALL
                        || s.filter != null && s.key != null && !s.filter.equals(type.apply(s.key))
                    ? Status.FILTER_CONFLICT
                    : s.amount > s.capacity ? Status.OVERFULL : Status.READY;
        rebuilds++;
        build = null;
        changed.accept(s);
        continue;
      }
      var n = b.queue.removeFirst();
      work++;
      visits++;
      if (!n.active() || n.segment != null || b.seen.contains(n)) continue;
      if (!compatible(b.group, n) && !b.group.members.isEmpty()) {
        b.group.conflicts++;
        b.group.boundaries.add(n);
        continue;
      }
      b.seen.add(n);
      var s = b.group;
      s.members.add(n);
      s.capacity += n.raw.capacity();
      s.amount += n.raw.amount();
      if (n.raw.key() != null) s.key = n.raw.key();
      if (n.filter != null) s.filter = n.filter;
      var p = n.site;
      s.minX = Math.min(s.minX, p.minX);
      s.maxX = Math.max(s.maxX, p.maxX);
      s.minY = Math.min(s.minY, p.minY);
      s.maxY = Math.max(s.maxY, p.maxY);
      s.minZ = Math.min(s.minZ, p.minZ);
      s.maxZ = Math.max(s.maxZ, p.maxZ);
      for (var e : n.edges)
        if (e.active()) {
          if (e.segment != null) {
            if (e.segment != s) {
              s.conflicts++;
              s.boundaries.add(e);
            }
          } else if (!b.seen.contains(e)) b.queue.addLast(e);
        }
    }
    return work;
  }

  public int activeEdges() {
    int total = 0;
    for (var n : nodes.values())
      if (n.ready())
        for (var e : n.edges)
          if (e.site.order > n.site.order && e.segment == n.segment && e.ready()) total++;
    return total;
  }

  public int pending() {
    return pending.size();
  }

  public long visits() {
    return visits;
  }

  public long rebuilds() {
    return rebuilds;
  }

  public int edges() {
    return edges;
  }

  public Collection<Node> nodes() {
    return Collections.unmodifiableCollection(nodes.values());
  }

  public Set<Segment> segments() {
    var set = Collections.newSetFromMap(new IdentityHashMap<Segment, Boolean>());
    for (var n : nodes.values()) if (n.segment != null) set.add(n.segment);
    return set;
  }

  private static int clamp(long n) {
    return (int) Math.clamp(n, 0, Integer.MAX_VALUE);
  }

  private static long ratio(long total, long prefix, long capacity) {
    if (capacity == 0) return 0;
    try {
      return Math.multiplyExact(total, prefix) / capacity;
    } catch (ArithmeticException overflow) {
      return BigInteger.valueOf(total)
          .multiply(BigInteger.valueOf(prefix))
          .divide(BigInteger.valueOf(capacity))
          .longValueExact();
    }
  }
}
