package factorycraft.energy;

import factorycraft.blockentity.PoleBlockEntity;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Loaded-only chunk buckets, shared for placement planning and client visualization. */
public final class PoleIndex {
  private static final Map<Level, Map<Long, Set<PoleBlockEntity>>> LEVELS = new WeakHashMap<>();

  private static long key(int x, int z) {
    return ((long) x << 32) ^ (z & 0xffffffffL);
  }

  public static void add(PoleBlockEntity p) {
    var b = p.getBlockPos();
    LEVELS
        .computeIfAbsent(p.getLevel(), l -> new HashMap<>())
        .computeIfAbsent(
            key(b.getX() >> 4, b.getZ() >> 4),
            k -> Collections.newSetFromMap(new IdentityHashMap<>()))
        .add(p);
  }

  public static void remove(PoleBlockEntity p) {
    var s = LEVELS.get(p.getLevel());
    if (s == null) return;
    var b = p.getBlockPos();
    long k = key(b.getX() >> 4, b.getZ() >> 4);
    var set = s.get(k);
    if (set != null) {
      set.remove(p);
      if (set.isEmpty()) s.remove(k);
    }
    if (s.isEmpty()) LEVELS.remove(p.getLevel());
  }

  public static List<PoleBlockEntity> near(Level level, BlockPos pos, double reach) {
    var s = LEVELS.get(level);
    if (s == null) return List.of();
    var out = new ArrayList<PoleBlockEntity>();
    int x = pos.getX() >> 4, z = pos.getZ() >> 4, range = (int) Math.ceil((reach + 2) / 16);
    for (int dx = -range; dx <= range; dx++)
      for (int dz = -range; dz <= range; dz++) {
        var b = s.get(key(x + dx, z + dz));
        if (b != null) out.addAll(b);
      }
    return out;
  }

  public static void clear(Level level) {
    LEVELS.remove(level);
  }

  private PoleIndex() {}
}
