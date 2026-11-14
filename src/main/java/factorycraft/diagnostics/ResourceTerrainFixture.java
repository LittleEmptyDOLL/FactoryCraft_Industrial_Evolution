package factorycraft.diagnostics;

import factorycraft.blockentity.ResourceDepositBlockEntity;
import factorycraft.registry.Content;
import factorycraft.simulation.resources.ResourceVeins;
import factorycraft.worldgen.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/** Explicit diagnostic scene only. Runtime worldgen never constructs terrain or pins chunks. */
public final class ResourceTerrainFixture {
  public record Result(
      BlockPos center,
      int members,
      int deep,
      int aboveCenter,
      long nanos,
      long allocatedBytes,
      int chunks) {}

  public static Result build(ServerLevel level, int index, int mode, boolean legacy) {
    var settings = new ResourceVeins.Settings(8, 8, 17, 31, 2, 5, 8, .12);
    long salt = 0x251733;
    var patch = ResourceVeins.patch(level.getSeed(), salt, 5000 + index * 4, 6000, settings);
    int y = 70;
    var chunks =
        new TreeSet<BlockPos>(
            Comparator.<BlockPos>comparingInt(p -> p.getX()).thenComparingInt(p -> p.getZ()));
    for (int x = patch.x() - 24; x <= patch.x() + 24; x++)
      for (int z = patch.z() - 24; z <= patch.z() + 24; z++) {
        var p = new BlockPos(x, y, z);
        level.getChunkAt(p);
        int top = top(mode, y, x - patch.x());
        for (int yy = top - 12; yy <= top; yy++) {
          var at = new BlockPos(x, yy, z);
          if (level.getBlockEntity(at) != null || !level.getBlockState(at).isAir())
            throw new IllegalStateException(
                "Occupied terrain diagnostic, refusing overwrite: " + at);
          level.setBlock(at, Blocks.STONE.defaultBlockState(), 2);
        }
        chunks.add(new BlockPos(x & ~15, y, z & ~15));
      }
    if (mode == 3)
      for (int x = patch.x() + 1; x <= patch.x() + 24; x++)
        for (int z = patch.z() - 24; z <= patch.z() + 24; z++)
          for (int yy = y - 1; yy <= y; yy++)
            level.setBlock(new BlockPos(x, yy, z), Blocks.WATER.defaultBlockState(), 2);
    var cave = new BlockPos(patch.x(), y - 3, patch.z());
    level.setBlock(cave, Blocks.AIR.defaultBlockState(), 2);
    var protectedPos = new BlockPos(patch.x(), y - 1, patch.z() + 1);
    level.setBlock(protectedPos, Blocks.GOLD_BLOCK.defaultBlockState(), 2);
    var water = new BlockPos(patch.x() - 4, y + (mode == 1 ? -1 : 0) + 1, patch.z());
    level.setBlock(water, Blocks.WATER.defaultBlockState(), 2);
    var primed = new HashSet<net.minecraft.world.level.chunk.LevelChunk>();
    for (var c : chunks)
      for (int dx = -1; dx <= 1; dx++)
        for (int dz = -1; dz <= 1; dz++)
          primed.add(level.getChunk((c.getX() >> 4) + dx, (c.getZ() >> 4) + dz));
    for (var c : primed) Heightmap.primeHeightmaps(c, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG));
    // Vanilla structure lookups may leave empty reference sets; they are not actual structures.
    if (mode == 0)
      for (var c : chunks) {
        var chunk = level.getChunkAt(c);
        var references = new java.util.HashMap<>(chunk.getAllReferences());
        var village =
            level
                .registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                .get(
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace(
                        "village_plains"));
        references.put(village, new it.unimi.dsi.fastutil.longs.LongOpenHashSet());
        chunk.setAllReferences(references);
        if (ResourceTerrain.protectedStructure(chunk))
          throw new IllegalStateException("Empty references blocked natural resources");
      }
    var bean =
        (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
    long allocated = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()),
        start = System.nanoTime();
    var feature =
        new ConfiguredFeature<>(
            Content.RESOURCE_PATCH_FEATURE.get(),
            new ResourceVeinConfiguration(
                Content.COPPER_ORE.get().defaultBlockState(), salt, false, settings));
    var originalTerrain = generator(level, (x, z) -> top(mode, y, x - patch.x()));
    for (var c : chunks) {
      if (!legacy)
        feature.place(level, originalTerrain, net.minecraft.util.RandomSource.create(1), c);
      else {
        var candidates =
            ResourceVeins.inChunk(level.getSeed(), salt, c.getX() >> 4, c.getZ() >> 4, settings);
        var window =
            new LegacySurfaceWindow(
                c.getX(), c.getZ(), 16, 16, 8, (x, z) -> top(mode, y, x - patch.x()));
        for (int x = c.getX(); x < c.getX() + 16; x++)
          for (int z = c.getZ(); z < c.getZ() + 16; z++) {
            if (mode == 3 && x > patch.x()) continue;
            int roof = window.height(x, z);
            for (int down = 0; down < 12; down++) {
              int stock = 0;
              for (var candidate : candidates)
                stock = Math.max(stock, legacyStock(candidate, x, z, down));
              if (stock == 0) continue;
              var p = new BlockPos(x, roof - down, z);
              if (p.getY() > top(mode, y, x - patch.x())
                  || !ResourceTerrain.replaceable(level.getBlockState(p))
                  || level.getBlockEntity(p) != null) continue;
              level.setBlock(p, Content.COPPER_ORE.get().defaultBlockState(), 2);
              ((ResourceDepositBlockEntity) level.getBlockEntity(p))
                  .initializeGeneratedReserve(stock);
            }
          }
      }
    }
    long nanos = System.nanoTime() - start,
        bytes = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocated;
    int members = 0, deep = 0, aboveCenter = 0;
    for (int x = patch.x() - 24; x <= patch.x() + 24; x++)
      for (int z = patch.z() - 24; z <= patch.z() + 24; z++)
        for (int yy = y - 24; yy <= y + 8; yy++) {
          var p = new BlockPos(x, yy, z);
          if (level.getBlockEntity(p) instanceof ResourceDepositBlockEntity) {
            int top = top(mode, y, x - patch.x());
            // Existing solid sediments may intersect a body below water; water itself is protected.
            if (yy > top) throw new IllegalStateException("Ore placed above original terrain");
            members++;
            if (yy > y) aboveCenter++;
            if (yy < top - 2) deep++;
          }
        }
    if (!level.getBlockState(cave).isAir()
        || !level.getBlockState(protectedPos).is(Blocks.GOLD_BLOCK)
        || !level.getBlockState(water).is(Blocks.WATER))
      throw new IllegalStateException("Worldgen reshaped air/water/structure fixture");
    return new Result(
        new BlockPos(patch.x(), y, patch.z()),
        members,
        deep,
        aboveCenter,
        nanos,
        bytes,
        chunks.size());
  }

  /**
   * Exact 0.25.0 downward-body sampling formula; this reference is diagnostic-only, never runtime
   * worldgen.
   */
  private static int legacyStock(ResourceVeins.Patch p, int x, int z, int down) {
    double dx = x - p.x(),
        dz = z - p.z(),
        angle = StrictMath.atan2(dz, dx),
        phase = unit(p.seed()) * Math.PI * 2;
    double edge =
        p.radius()
            * (.88
                + .08 * StrictMath.sin(angle * 3 + phase)
                + .04 * StrictMath.sin(angle * 5 - phase));
    double distance = StrictMath.sqrt(dx * dx + dz * dz) / edge;
    if (distance >= 1) return 0;
    double undulation =
        1 - .12 * .5 + .12 * .5 * StrictMath.sin(x * .19 + z * .13 + unit(p.seed()) * 6);
    int depth =
        Math.max(
            1, (int) StrictMath.ceil(12 * StrictMath.sqrt(1 - distance * distance) * undulation));
    if (down >= depth) return 0;
    double richness =
        (1 - distance) * (.7 + .3 * unit(mix(p.seed() ^ ((long) x << 32) ^ (z & 0xffffffffL))));
    return p.settings().minStock()
        + (int)
            Math.round(
                (p.settings().maxStock() - p.settings().minStock())
                    * richness
                    * (1 - down / (double) depth));
  }

  private static double unit(long v) {
    return (v >>> 11) * 0x1.0p-53;
  }

  private static long mix(long v) {
    v = (v ^ (v >>> 30)) * 0xbf58476d1ce4e5b9L;
    v = (v ^ (v >>> 27)) * 0x94d049bb133111ebL;
    return v ^ (v >>> 31);
  }

  private static int top(int mode, int y, int dx) {
    return y
        + (mode == 1
            ? Math.floorDiv(dx, 4)
            : mode == 2 && dx > 0 ? -10 : mode == 3 && dx > 0 ? -2 : mode == 4 && dx > 0 ? 10 : 0);
  }

  /** Explicit prepared-terrain source for diagnostics; normal worlds always use their generator. */
  public static net.minecraft.world.level.chunk.ChunkGenerator generator(
      ServerLevel level, java.util.function.IntBinaryOperator height) {
    var flat =
        (net.minecraft.world.level.levelgen.FlatLevelSource) level.getChunkSource().getGenerator();
    return new net.minecraft.world.level.levelgen.FlatLevelSource(flat.settings()) {
      @Override
      public int getBaseHeight(
          int x,
          int z,
          Heightmap.Types type,
          net.minecraft.world.level.LevelHeightAccessor bounds,
          net.minecraft.world.level.levelgen.RandomState random) {
        return height.applyAsInt(x, z) + 1;
      }
    };
  }

  private ResourceTerrainFixture() {}
}
