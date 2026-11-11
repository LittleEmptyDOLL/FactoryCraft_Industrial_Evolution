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
    private ArrayList<Node> members = new ArrayList<>();
    private List<Node> exposedMembers;
    private boolean cancelled, published;
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

    public boolean published() {
      return published;
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
      return exposedMembers == null ? List.of() : exposedMembers;
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

  public final class Node implements Comparable<Node> {
    public final Site site;

    /**
     * Sole checkpoint owner only while unpublished; inert bytes when attached. Save through
     * owned().
     */
    public final FluidCell<K> raw;

    private final ArrayList<Node> edges = new ArrayList<>(6);
    private int pendingIndex = -1;
    private long queuedBuild;
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

    private Segment current() {
      return segment != null && segment.valid ? segment : null;
    }

    public int compareTo(Node other) {
      return Long.compare(site.order, other.site.order);
    }

    public Segment segment() {
      return current();
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
      return active() && current() != null && segment.flowing();
    }
  }

  private final class Owned extends FluidCell<K> {
    final Node n;

    Owned(Node n) {
      super(n.raw.capacity(), () -> {});
      this.n = n;
    }

    public int amount() {
      var g = n.current();
      return g == null ? n.raw.amount() : g.share(n);
    }

    public int capacity() {
      return n.raw.capacity();
    }

    public K key() {
      var g = n.current();
      return amount() == 0 ? null : g == null ? n.raw.key() : g.key;
    }

    public int space() {
      var g = n.current();
      return Math.min(super.space(), g == null ? Integer.MAX_VALUE : clamp(g.capacity - g.amount));
    }

    public boolean accepts(K k) {
      var g = n.current();
      return g == null ? n.raw.accepts(k) : g.accepts(k);
    }

    public Object ownerToken() {
      var g = n.current();
      return g == null ? n.raw : g;
    }

    protected void adjust(K k, int delta) {
      var g = n.current();
      if (g == null) {
        touch(n);
        n.raw.adjust(k, delta);
      } else g.adjust(delta < 0 ? g.key : k, delta);
    }

    protected void setContents(K k, int value) {
      var g = n.current();
      if (g == null) {
        touch(n);
        n.raw.restore(k, value);
      } else {
        int delta = value - amount();
        if (delta != 0) {
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
      return n.active() && n.current() != null;
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
      return n.current() == null ? n : n.segment;
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
    final long id = ++nextBuild;
    final Segment group = new Segment();
    final ArrayDeque<Node> queue = new ArrayDeque<>();
    ArrayList<Node> src, dst;
    int phase, width = 1, left, fromA, fromB, mid, end, write, staged;
    long prefix;
    boolean ordered = true;
    Node lastMember;

    Build(Node root) {
      offer(root);
    }

    void offer(Node n) {
      if (n.queuedBuild != id) {
        n.queuedBuild = id;
        queue.addLast(n);
      }
    }

    void sorting() {
      if (ordered) {
        group.exposedMembers = Collections.unmodifiableList(group.members);
        phase = 2;
        return;
      }
      phase = 1;
      src = group.members;
      dst = new ArrayList<>(src.size());
    }

    boolean sortStep() {
      if (dst.size() < src.size()) {
        dst.add(null);
        return false;
      }
      if (width >= src.size()) {
        group.members = src;
        group.exposedMembers = Collections.unmodifiableList(src);
        phase = 2;
        return false;
      }
      if (write == end) {
        if (left >= src.size()) {
          var swap = src;
          src = dst;
          dst = swap;
          width *= 2;
          left = write = end = 0;
          return false;
        }
        fromA = left;
        mid = Math.min(src.size(), left + width);
        fromB = mid;
        end = Math.min(src.size(), left + 2 * width);
        write = left;
        left = end;
      }
      var n =
          fromA < mid && (fromB >= end || src.get(fromA).compareTo(src.get(fromB)) <= 0)
              ? src.get(fromA++)
              : src.get(fromB++);
      dst.set(write++, n);
      return false;
    }
  }

  private final Map<Long, Node> nodes = new HashMap<>();

  /**
   * Indexed heap: deterministic smallest root, no per-invalidation tree entries or stale tickets.
   */
  private final ArrayList<Node> pending = new ArrayList<>();

  private final ArrayDeque<Segment> cancelled = new ArrayDeque<>();
  private final Function<K, Object> type;
  private final Consumer<Segment> changed;
  private final Consumer<Node> localChanged;
  private final java.util.function.BiConsumer<Segment, Node> prepare, detach;
  private Build build;
  private long nextId, nextBuild, visits, rebuilds, finalizeWork, aborts, freezes, frozenMembers;
  private int edges;
  private long localEdits;

  public PipelineNetwork(
      Function<K, Object> type, Consumer<Segment> changed, Consumer<Node> localChanged) {
    this(type, changed, localChanged, (g, n) -> {});
  }

  public PipelineNetwork(
      Function<K, Object> type,
      Consumer<Segment> changed,
      Consumer<Node> localChanged,
      java.util.function.BiConsumer<Segment, Node> prepare) {
    this(type, changed, localChanged, prepare, (g, n) -> {});
  }

  public PipelineNetwork(
      Function<K, Object> type,
      Consumer<Segment> changed,
      Consumer<Node> localChanged,
      java.util.function.BiConsumer<Segment, Node> prepare,
      java.util.function.BiConsumer<Segment, Node> detach) {
    this.type = type;
    this.changed = changed;
    this.localChanged = localChanged;
    this.prepare = prepare;
    this.detach = detach;
  }

  public PipelineNetwork() {
    this(k -> k, s -> {}, n -> {});
  }

  private void swap(int a, int b) {
    var x = pending.get(a);
    var y = pending.get(b);
    pending.set(a, y);
    pending.set(b, x);
    x.pendingIndex = b;
    y.pendingIndex = a;
  }

  private void up(int at) {
    while (at > 0) {
      int parent = (at - 1) / 2;
      if (pending.get(parent).compareTo(pending.get(at)) <= 0) break;
      swap(parent, at);
      at = parent;
    }
  }

  private void down(int at) {
    while (true) {
      int child = at * 2 + 1;
      if (child >= pending.size()) return;
      if (child + 1 < pending.size() && pending.get(child + 1).compareTo(pending.get(child)) < 0)
        child++;
      if (pending.get(at).compareTo(pending.get(child)) <= 0) return;
      swap(at, child);
      at = child;
    }
  }

  private void enqueue(Node n) {
    if (!n.active() || n.pendingIndex >= 0) return;
    n.pendingIndex = pending.size();
    pending.add(n);
    up(n.pendingIndex);
  }

  private void dequeue(Node n) {
    int i = n.pendingIndex;
    if (i < 0) return;
    int last = pending.size() - 1;
    if (i != last) swap(i, last);
    pending.removeLast();
    n.pendingIndex = -1;
    if (i < pending.size()) {
      if (i > 0 && pending.get(i).compareTo(pending.get((i - 1) / 2)) < 0) up(i);
      else down(i);
    }
  }

  private void touch(Node n) {
    if (build != null && n.queuedBuild == build.id) {
      var b = build;
      build = null;
      b.group.cancelled = true;
      aborts++;
      changed.accept(b.group);
      if (b.staged > 0) cancelled.addLast(b.group);
    }
  }

  public Node add(Site site, FluidCell<K> cell) {
    if (nodes.containsKey(site.order))
      throw new IllegalArgumentException("Duplicate pipeline owner");
    var n = new Node(site, cell);
    nodes.put(site.order, n);
    enqueue(n);
    return n;
  }

  private void invalidate(Segment s) {
    if (s == null || !s.valid) return;
    // One exact division per member. Raw checkpoints remain inert until the entire pool retires.
    long previous = 0;
    for (var n : s.members) {
      long edge = ratio(s.amount, n.prefix + n.raw.capacity(), s.capacity);
      int share = Math.toIntExact(edge - previous);
      previous = edge;
      n.raw.restore(share == 0 ? null : s.key, share);
    }
    s.valid = false;
    freezes++;
    frozenMembers += s.members.size();
    for (var n : s.members) {
      n.segment = null;
      enqueue(n);
    }
    s.amount = 0;
    s.key = null;
    changed.accept(s);
  }

  /**
   * A changed pump/port boundary invalidates prepared metadata only in the affected unfinished
   * build.
   */
  public void boundaryChanged(Node n) {
    if (n != null && !n.removed) touch(n);
  }

  public void topology(Node n) {
    if (n == null || n.removed) return;
    touch(n);
    invalidate(n.current());
    enqueue(n);
    for (var e : n.edges) {
      touch(e);
      invalidate(e.current());
      enqueue(e);
    }
  }

  private boolean attachLeaf(Node n, Node parent) {
    var g = parent.current();
    if (g == null
        || g.status != Status.READY
        || g.filter != null
        || g.conflicts != 0
        || n.current() != null
        || !n.active()
        || !n.edges.isEmpty()
        || n.filter != null
        || n.raw.amount() > n.raw.capacity()
        || n.raw.key() != null && !g.accepts(n.raw.key())) return false;
    var p = n.site;
    if (Math.max(g.maxX, p.maxX) - Math.min(g.minX, p.minX) + 1 > FluidUnits.MAX_HORIZONTAL_SPAN
        || Math.max(g.maxZ, p.maxZ) - Math.min(g.minZ, p.minZ) + 1 > FluidUnits.MAX_HORIZONTAL_SPAN)
      return false;
    touch(n);
    int at = Collections.binarySearch(g.members, n);
    if (at >= 0) throw new IllegalStateException("Duplicate pool member");
    at = -at - 1;
    g.valid = false;
    g.members.add(at, n);
    n.prefix = at == 0 ? 0 : g.members.get(at - 1).prefix + g.members.get(at - 1).raw.capacity();
    for (int i = at + 1; i < g.members.size(); i++) g.members.get(i).prefix += n.raw.capacity();
    g.capacity += n.raw.capacity();
    g.amount += n.raw.amount();
    if (n.raw.key() != null) g.key = n.raw.key();
    g.lastKey = g.key;
    n.segment = g;
    dequeue(n);
    g.minX = Math.min(g.minX, p.minX);
    g.maxX = Math.max(g.maxX, p.maxX);
    g.minY = Math.min(g.minY, p.minY);
    g.maxY = Math.max(g.maxY, p.maxY);
    g.minZ = Math.min(g.minZ, p.minZ);
    g.maxZ = Math.max(g.maxZ, p.maxZ);
    int ix = Collections.binarySearch(parent.edges, n);
    parent.edges.add(-ix - 1, n);
    n.edges.add(parent);
    edges++;
    g.valid = true;
    localEdits++;
    prepare.accept(g, n);
    changed.accept(g);
    return true;
  }

  private boolean removeLeaf(Node n) {
    var g = n.current();
    if (g == null
        || g.status != Status.READY
        || g.filter != null
        || g.conflicts != 0
        || g.members.size() < 2
        || n.edges.size() > 1
        || !n.edges.isEmpty() && n.edges.getFirst().current() != g) return false;
    int share = g.share(n), at = Collections.binarySearch(g.members, n);
    var key = g.key;
    g.valid = false;
    n.raw.restore(share == 0 ? null : key, share);
    g.amount -= share;
    g.capacity -= n.raw.capacity();
    g.key = g.amount == 0 ? null : key;
    g.lastKey = g.key;
    g.members.remove(at);
    n.segment = null;
    g.minX = g.minY = g.minZ = Integer.MAX_VALUE;
    g.maxX = g.maxY = g.maxZ = Integer.MIN_VALUE;
    long prefix = 0;
    for (var m : g.members) {
      m.prefix = prefix;
      prefix += m.raw.capacity();
      var p = m.site;
      g.minX = Math.min(g.minX, p.minX);
      g.maxX = Math.max(g.maxX, p.maxX);
      g.minY = Math.min(g.minY, p.minY);
      g.maxY = Math.max(g.maxY, p.maxY);
      g.minZ = Math.min(g.minZ, p.minZ);
      g.maxZ = Math.max(g.maxZ, p.maxZ);
    }
    if (!n.edges.isEmpty()) {
      var other = n.edges.removeFirst();
      other.edges.remove(Collections.binarySearch(other.edges, n));
      edges--;
    }
    n.removed = true;
    n.active = false;
    nodes.remove(n.site.order);
    dequeue(n);
    g.valid = true;
    localEdits++;
    detach.accept(g, n);
    changed.accept(g);
    return true;
  }

  public void connect(Node a, Node b) {
    connect(a, b, true);
  }

  /**
   * Known multi-connection placement defers grouping to preserve stable conflicting-boundary
   * choice.
   */
  public void connect(Node a, Node b, boolean allowLocal) {
    if (a == b || a.removed || b.removed) return;
    int i = Collections.binarySearch(a.edges, b);
    if (i >= 0) return;
    if (a.current() != null && a.current() == b.current()) {
      a.edges.add(-i - 1, b);
      int j = Collections.binarySearch(b.edges, a);
      b.edges.add(-j - 1, a);
      edges++;
      localEdits++;
      return;
    }
    if (allowLocal && (attachLeaf(a, b) || attachLeaf(b, a))) return;
    topology(a);
    topology(b);
    a.edges.add(-i - 1, b);
    int j = Collections.binarySearch(b.edges, a);
    b.edges.add(-j - 1, a);
    edges++;
  }

  public void disconnect(Node a, Node b) {
    int i = Collections.binarySearch(a.edges, b);
    if (i < 0) return;
    a.edges.remove(i);
    b.edges.remove(Collections.binarySearch(b.edges, a));
    edges--;
    topology(a);
    topology(b);
  }

  public void active(Node n, boolean on) {
    if (n.removed || n.active == on) return;
    topology(n);
    n.active = on;
    if (on) enqueue(n);
    else dequeue(n);
  }

  public void filter(Node n, Object filter) {
    if (Objects.equals(n.filter, filter)) return;
    topology(n);
    n.filter = filter;
  }

  public void remove(Node n) {
    if (n.removed) return;
    if (removeLeaf(n)) return;
    topology(n);
    while (!n.edges.isEmpty()) disconnect(n, n.edges.getLast());
    n.removed = true;
    n.active = false;
    nodes.remove(n.site.order);
    dequeue(n);
  }

  /**
   * The owning cell already committed content. Only a real identity conflict needs topology work.
   */
  public void contentChanged(Node n) {
    mutated(n);
  }

  public void externalChanged(Node n) {
    contentChanged(n);
  }

  private void mutated(Node n) {
    var s = n.current();
    if (s == null) {
      touch(n);
      enqueue(n);
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
      if (!cancelled.isEmpty()) {
        var s = cancelled.getFirst();
        if (s.members.isEmpty()) {
          cancelled.removeFirst();
          continue;
        }
        var n = s.members.removeLast();
        if (n.segment == s) n.segment = null;
        enqueue(n);
        work++;
        finalizeWork++;
        continue;
      }
      if (build == null) {
        while (!pending.isEmpty()
            && (!pending.getFirst().active() || pending.getFirst().current() != null))
          dequeue(pending.getFirst());
        if (pending.isEmpty()) break;
        build = new Build(pending.getFirst());
      }
      var b = build;
      var s = b.group;
      if (b.phase == 1) {
        b.sortStep();
        work++;
        finalizeWork++;
        continue;
      }
      if (b.phase == 2) {
        if (b.staged < s.members.size()) {
          var n = s.members.get(b.staged++);
          n.prefix = b.prefix;
          b.prefix += n.raw.capacity();
          n.segment = s;
          dequeue(n);
          prepare.accept(s, n);
          work++;
          finalizeWork++;
          continue;
        }
        // Single publication point. Before this flag every staged node reads its own raw
        // checkpoint;
        // afterwards all raw records are inert and the complete pool is the only resource owner.
        s.lastKey = s.key;
        s.status =
            s.maxX - s.minX + 1 > FluidUnits.MAX_HORIZONTAL_SPAN
                    || s.maxZ - s.minZ + 1 > FluidUnits.MAX_HORIZONTAL_SPAN
                ? Status.TOO_LONG
                : s.filter == REJECT_ALL
                        || s.filter != null && s.key != null && !s.filter.equals(type.apply(s.key))
                    ? Status.FILTER_CONFLICT
                    : s.amount > s.capacity ? Status.OVERFULL : Status.READY;
        s.valid = true;
        s.published = true;
        rebuilds++;
        build = null;
        changed.accept(s);
        continue;
      }
      if (b.queue.isEmpty()) {
        b.sorting();
        continue;
      }
      var n = b.queue.removeFirst();
      work++;
      visits++;
      if (!n.active() || n.current() != null) continue;
      if (!compatible(s, n) && !s.members.isEmpty()) {
        s.conflicts++;
        s.boundaries.add(n);
        continue;
      }
      if (b.lastMember != null && b.lastMember.compareTo(n) > 0) b.ordered = false;
      b.lastMember = n;
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
          if (e.current() != null) {
            s.conflicts++;
            s.boundaries.add(e);
          } else b.offer(e);
        }
    }
    return work;
  }

  public int activeEdges() {
    int count = 0;
    for (var n : nodes.values())
      if (n.ready())
        for (var e : n.edges)
          if (e.site.order > n.site.order && e.current() == n.current() && e.ready()) count++;
    return count;
  }

  public int pending() {
    return pending.size() + (build == null ? 0 : 1) + cancelled.size();
  }

  public long visits() {
    return visits;
  }

  public long rebuilds() {
    return rebuilds;
  }

  public long finalizeWork() {
    return finalizeWork;
  }

  public long aborts() {
    return aborts;
  }

  public long localEdits() {
    return localEdits;
  }

  public long freezes() {
    return freezes;
  }

  public long frozenMembers() {
    return frozenMembers;
  }

  public int edges() {
    return edges;
  }

  public Collection<Node> nodes() {
    return Collections.unmodifiableCollection(nodes.values());
  }

  public Set<Segment> segments() {
    var result = Collections.newSetFromMap(new IdentityHashMap<Segment, Boolean>());
    for (var n : nodes.values()) if (n.current() != null) result.add(n.current());
    return result;
  }

  private static int clamp(long n) {
    return (int) Math.clamp(n, 0, Integer.MAX_VALUE);
  }

  static long ratio(long total, long prefix, long capacity) {
    if (capacity == 0 || prefix == 0) return 0;
    long quotient = total / capacity, remainder = total % capacity;
    long base = Math.multiplyExact(quotient, prefix);
    if (remainder == 0) return base;
    if (remainder <= Long.MAX_VALUE / prefix) return base + remainder * prefix / capacity;
    return base
        + BigInteger.valueOf(remainder)
            .multiply(BigInteger.valueOf(prefix))
            .divide(BigInteger.valueOf(capacity))
            .longValueExact();
  }
}
