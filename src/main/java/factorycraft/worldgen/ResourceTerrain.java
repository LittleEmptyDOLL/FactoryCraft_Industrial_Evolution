package factorycraft.worldgen;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Read WG terrain only from supplied available region. Never obtains a server chunk. */
public final class ResourceTerrain {
  private static final net.neoforged.neoforge.registries.DeferredRegister<
          net.neoforged.neoforge.attachment.AttachmentType<?>>
      ATTACHMENTS =
          net.neoforged.neoforge.registries.DeferredRegister.create(
              net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.ATTACHMENT_TYPES,
              "factorycraft");
  private static final java.util.function.Supplier<
          net.neoforged.neoforge.attachment.AttachmentType<int[]>>
      SURFACE =
          ATTACHMENTS.register(
              "resource_surface",
              () ->
                  net.neoforged.neoforge.attachment.AttachmentType.builder(() -> new int[0])
                      .serialize(
                          com.mojang.serialization.Codec.INT_STREAM.xmap(
                              java.util.stream.IntStream::toArray, java.util.Arrays::stream))
                      .build());

  public static void register(net.neoforged.bus.api.IEventBus bus) {
    ATTACHMENTS.register(bus);
  }

  /** Freeze only this decorating chunk, including chunks without a resource patch. */
  public static void capture(net.minecraft.world.level.chunk.ChunkAccess chunk) {
    if (chunk.hasData(SURFACE.get())) return;
    if (!chunk.hasPrimedHeightmap(Heightmap.Types.OCEAN_FLOOR_WG))
      throw new IllegalStateException("Resource surface requires pre-decoration WG heightmap");
    int[] heights = new int[256];
    var map = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 16; z++) heights[x * 16 + z] = map.getFirstAvailable(x, z) - 1;
    chunk.setData(SURFACE.get(), heights);
  }

  /** Snapshot survives FULL conversion/reload, where vanilla remaps WG to decorated heightmaps. */
  public static int frozenSurface(net.minecraft.world.level.chunk.ChunkAccess chunk, int x, int z) {
    var heights = chunk.getExistingDataOrNull(SURFACE.get());
    if (heights != null) {
      if (heights.length != 256)
        throw new IllegalStateException("Invalid resource surface snapshot");
      return heights[(x & 15) * 16 + (z & 15)];
    }
    // Not-yet-decorated supplied neighbors still own the original WG heightmap.
    if (!chunk.hasPrimedHeightmap(Heightmap.Types.OCEAN_FLOOR_WG))
      throw new IllegalStateException(
          "Missing original resource terrain; pre-0.25 chunks are unsupported");
    return chunk
            .getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG)
            .getFirstAvailable(x & 15, z & 15)
        - 1;
  }

  public static int surface(WorldGenLevel level, int x, int z) {
    if (level instanceof net.minecraft.server.level.WorldGenRegion)
      return frozenSurface(level.getChunk(x >> 4, z >> 4), x, z);
    // Explicit prepared-terrain server fixtures use their primed diagnostic heightmaps.
    return level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
  }

  public static boolean protectedStructure(net.minecraft.world.level.chunk.ChunkAccess chunk) {
    for (var start : chunk.getAllStarts().values()) if (start.isValid()) return true;
    for (var references : chunk.getAllReferences().values()) if (!references.isEmpty()) return true;
    return false;
  }

  public static boolean replaceable(BlockState s) {
    return s.getFluidState().isEmpty()
        && (s.is(BlockTags.DIRT)
            || s.is(BlockTags.SAND)
            || s.is(BlockTags.BASE_STONE_OVERWORLD)
            || s.is(Blocks.GRAVEL)
            || s.is(Blocks.CLAY));
  }

  private ResourceTerrain() {}
}
