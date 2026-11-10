package factorycraft.energy;

import factorycraft.blockentity.PoleBlockEntity;
import factorycraft.simulation.power.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Explicit reciprocal UUID edges; unloaded endpoints are retained but never conduct. */
public final class PowerWires {
  public record Link(BlockPos pos, UUID identity) {}

  public static PoleBlockEntity at(Level level, BlockPos pos) {
    return level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof PoleBlockEntity p
        ? p
        : null;
  }

  public static boolean reachable(PoleBlockEntity a, PoleBlockEntity b) {
    return a != b
        && a.getLevel() == b.getLevel()
        && PowerGrid.footprint(a).wireReach(PowerGrid.footprint(b), a.spec(), b.spec());
  }

  public static boolean connect(PoleBlockEntity a, PoleBlockEntity b) {
    if (!a.available() || !b.available() || !reachable(a, b)) return false;
    a.putLink(b);
    b.putLink(a);
    PowerGrid.edge(a, b, true);
    return true;
  }

  public static void disconnect(PoleBlockEntity a, PoleBlockEntity b) {
    PowerGrid.edge(a, b, false);
    a.eraseLink(b.getBlockPos());
    b.eraseLink(a.getBlockPos());
  }

  public static void clear(PoleBlockEntity a) {
    for (var link : List.copyOf(a.links())) {
      var b = at(a.getLevel(), link.pos());
      if (b != null && b.assembly().owns(link.identity())) disconnect(a, b);
      else a.eraseLink(link.pos());
    }
  }

  private static final class Incoming {
    final Map<BlockPos, Set<PoleBlockEntity>> owners = new HashMap<>();
    final Map<Long, Set<BlockPos>> chunks = new HashMap<>();
  }

  private static final Map<Level, Incoming> INCOMING = new WeakHashMap<>();

  private static long chunkKey(BlockPos p) {
    return net.minecraft.world.level.ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4);
  }

  public static void track(PoleBlockEntity p) {
    for (var l : p.links()) reference(p, l.pos(), true);
  }

  public static void untrack(PoleBlockEntity p) {
    for (var l : p.links()) reference(p, l.pos(), false);
  }

  public static void reference(PoleBlockEntity p, BlockPos target, boolean add) {
    var level = p.getLevel();
    if (level == null || level.isClientSide) return;
    var s = add ? INCOMING.computeIfAbsent(level, l -> new Incoming()) : INCOMING.get(level);
    if (s == null) return;
    if (add) {
      s.owners
          .computeIfAbsent(target, k -> Collections.newSetFromMap(new IdentityHashMap<>()))
          .add(p);
      s.chunks.computeIfAbsent(chunkKey(target), k -> new HashSet<>()).add(target);
    } else {
      var owners = s.owners.get(target);
      if (owners != null) {
        owners.remove(p);
        if (owners.isEmpty()) {
          s.owners.remove(target);
          var bucket = s.chunks.get(chunkKey(target));
          if (bucket != null) {
            bucket.remove(target);
            if (bucket.isEmpty()) s.chunks.remove(chunkKey(target));
          }
        }
      }
      if (s.owners.isEmpty()) INCOMING.remove(level);
    }
  }

  private static void validate(PoleBlockEntity a, BlockPos pos) {
    var l = a.linkAt(pos);
    if (l == null) return;
    var level = a.getLevel();
    if (!level.hasChunkAt(pos)) return;
    var b = at(level, pos);
    if (b == null) {
      if (!(level.getBlockState(pos).getBlock() instanceof factorycraft.blocks.FactoryBlock block
          && block.kind.pole())) a.eraseLink(pos);
      return;
    }
    // Block/BE tags and onLoad callbacks can arrive in different orders. Unknown is not destroyed.
    if (!b.loaded() || !b.assembly().formed()) return;
    if (!b.assembly().owns(l.identity()) || !b.hasLink(a) || !reachable(a, b)) {
      PowerGrid.edge(a, b, false);
      a.eraseLink(pos);
      if (b.assembly().owns(l.identity())) b.eraseLink(a.getBlockPos());
      return;
    }
    if (b.available()) PowerGrid.edge(a, b, true);
  }

  public static void reconcile(PoleBlockEntity a) {
    for (var link : List.copyOf(a.links())) validate(a, link.pos());
    var s = INCOMING.get(a.getLevel());
    if (s == null) return;
    var peers = s.owners.get(a.getBlockPos());
    if (peers != null)
      for (var peer : List.copyOf(peers))
        if (peer != a && peer.loaded() && peer.assembly().formed()) validate(peer, a.getBlockPos());
  }

  public static void loadedChunk(Level level, net.minecraft.world.level.chunk.LevelChunk chunk) {
    var s = INCOMING.get(level);
    if (s == null) return;
    var positions = s.chunks.get(chunk.getPos().toLong());
    if (positions == null) return;
    for (var pos : List.copyOf(positions))
      if (!(chunk.getBlockState(pos).getBlock() instanceof factorycraft.blocks.FactoryBlock b
          && b.kind.pole())) {
        var peers = s.owners.get(pos);
        if (peers != null) for (var owner : List.copyOf(peers)) owner.eraseLink(pos);
      }
  }

  public static void clearLevel(Level level) {
    INCOMING.remove(level);
  }

  /**
   * Nearest-first selection suppresses existing short triangles, without a connection-count cap.
   */
  public static List<PoleBlockEntity> candidates(
      Level level, PowerFootprint bounds, PoleSpec spec, BlockPos pos) {
    var candidates =
        PoleIndex.near(level, pos, spec.wire).stream()
            .filter(
                p ->
                    p.loaded()
                        && p.assembly().formed()
                        && (level.isClientSide ? p.visualAvailable() : p.available())
                        && bounds.wireReach(PowerGrid.footprint(p), spec, p.spec()))
            .sorted(
                Comparator.<PoleBlockEntity>comparingDouble(
                        p -> bounds.distanceSquared(PowerGrid.footprint(p)))
                    .thenComparing(PoleBlockEntity::getBlockPos))
            .toList();
    var out = new ArrayList<PoleBlockEntity>();
    for (var p : candidates) {
      double d = bounds.distanceSquared(PowerGrid.footprint(p));
      if (d == 0) continue;
      boolean redundant = false;
      for (var chosen : out)
        if (chosen.hasLink(p)
            && p.hasLink(chosen)
            && reachable(chosen, p)
            && PowerGrid.footprint(chosen).distanceSquared(PowerGrid.footprint(p)) < d - 1e-9) {
          redundant = true;
          break;
        }
      if (!redundant) out.add(p);
    }
    return out;
  }

  public static void autoConnect(PoleBlockEntity p) {
    for (var n : candidates(p.getLevel(), PowerGrid.footprint(p), p.spec(), p.getBlockPos()))
      if (n != p) connect(p, n);
  }

  private PowerWires() {}
}
