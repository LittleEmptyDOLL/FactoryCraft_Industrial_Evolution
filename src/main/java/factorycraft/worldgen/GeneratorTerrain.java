package factorycraft.worldgen;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import factorycraft.FactoryCraft;
import java.util.concurrent.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Immutable generator inputs, no anchor chunk lookup, heightmap or live ServerLevel worker read.
 */
@EventBusSubscriber(modid = FactoryCraft.ID)
public final class GeneratorTerrain {
  private record Key(ServerLevel level, ChunkGenerator generator) {}

  private static final ConcurrentMap<Key, Sampler> SOURCES = new ConcurrentHashMap<>();
  private static final ConcurrentMap<ServerLevel, RandomState> BASE_RANDOM =
      new ConcurrentHashMap<>();

  private static final class Sampler {
    final ChunkGenerator generator;
    final RandomState random;
    final LevelHeightAccessor height;
    final Cache<Long, Integer> anchors = CacheBuilder.newBuilder().maximumSize(4096).build();

    Sampler(ChunkGenerator g, RandomState r, int min, int size) {
      generator = g;
      random = r;
      height = LevelHeightAccessor.create(min, size);
    }

    int surface(int x, int z) {
      long key = ((long) x << 32) ^ (z & 0xffffffffL);
      try {
        return anchors.get(
            key,
            () ->
                generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, height, random) - 1);
      } catch (ExecutionException e) {
        throw new IllegalStateException("Canonical resource anchor failed", e.getCause());
      }
    }
  }

  @SubscribeEvent
  public static void load(LevelEvent.Load event) {
    if (event.getLevel() instanceof ServerLevel l) {
      var g = l.getChunkSource().getGenerator();
      BASE_RANDOM.put(l, l.getChunkSource().randomState());
      SOURCES.putIfAbsent(
          new Key(l, g),
          new Sampler(g, l.getChunkSource().randomState(), l.getMinBuildHeight(), l.getHeight()));
    }
  }

  @SubscribeEvent
  public static void unload(LevelEvent.Unload event) {
    if (event.getLevel() instanceof ServerLevel l) {
      SOURCES.keySet().removeIf(k -> k.level == l);
      BASE_RANDOM.remove(l);
    }
  }

  public static int anchor(FeaturePlaceContext<?> context, int x, int z) {
    var region = context.level();
    var g = context.chunkGenerator();
    var key = new Key(region.getLevel(), g);
    var source =
        SOURCES.computeIfAbsent(
            key,
            k -> {
              // Safe early-generation/explicit diagnostic fallback: registry data and seed only.
              var settings =
                  g instanceof NoiseBasedChunkGenerator noise
                      ? noise.generatorSettings().value()
                      : region
                          .registryAccess()
                          .registryOrThrow(Registries.NOISE_SETTINGS)
                          .getOrThrow(NoiseGeneratorSettings.OVERWORLD);
              var random =
                  !(g instanceof NoiseBasedChunkGenerator)
                      ? BASE_RANDOM.get(region.getLevel())
                      : null;
              if (random == null)
                random =
                    RandomState.create(
                        settings,
                        region.registryAccess().lookupOrThrow(Registries.NOISE),
                        region.getSeed());
              return new Sampler(g, random, region.getMinBuildHeight(), region.getHeight());
            });
    return source.surface(x, z);
  }

  private GeneratorTerrain() {}
}
