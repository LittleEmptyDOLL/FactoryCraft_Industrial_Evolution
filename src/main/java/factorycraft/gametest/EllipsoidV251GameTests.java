package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.ResourceDepositBlockEntity;
import factorycraft.diagnostics.ResourceTerrainFixture;
import factorycraft.registry.Content;
import factorycraft.simulation.resources.ResourceVeins;
import factorycraft.worldgen.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.feature.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class EllipsoidV251GameTests {
  private static final long SALT = 0x251666;

  private static ResourceVeinConfiguration config(ResourceVeins.Settings settings) {
    return new ResourceVeinConfiguration(
        Content.COPPER_ORE.get().defaultBlockState(), SALT, false, settings);
  }

  private static final class Records extends net.minecraft.world.level.saveddata.SavedData {
    int next;
    String digest = "";
    BlockPos center = BlockPos.ZERO;
    long initial;

    static Records load(CompoundTag tag, HolderLookup.Provider r) {
      var d = new Records();
      d.next = tag.getInt("Next");
      d.digest = tag.getString("Digest");
      d.center = BlockPos.of(tag.getLong("Center"));
      d.initial = tag.getLong("Initial");
      return d;
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider r) {
      tag.putInt("Next", next);
      tag.putString("Digest", digest);
      tag.putLong("Center", center.asLong());
      tag.putLong("Initial", initial);
      return tag;
    }

    int claim() {
      if (next < 0 || next > 10000)
        throw new IllegalStateException("Geometry diagnostic exhausted");
      setDirty();
      return next++;
    }
  }

  private static Records records(ServerLevel level) {
    return level
        .getDataStorage()
        .computeIfAbsent(
            new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                Records::new, Records::load),
            "factorycraft_ellipsoid_v0251");
  }

  @GameTest(template = "empty", batch = "ellipsoid_v0251", timeoutTicks = 200)
  public static void noiseGeneratorSamplesDistantAnchorWithoutLoadingItsChunk(GameTestHelper h) {
    var level = h.getLevel();
    int x = 2300000 + records(level).claim() * 1024, z = 2300000;
    var settings =
        level
            .registryAccess()
            .registryOrThrow(Registries.NOISE_SETTINGS)
            .getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
    var generator =
        new NoiseBasedChunkGenerator(
            new net.minecraft.world.level.biome.FixedBiomeSource(
                level.getBiome(h.absolutePos(BlockPos.ZERO))),
            settings);
    var random =
        RandomState.create(
            settings.value(),
            level.registryAccess().lookupOrThrow(Registries.NOISE),
            level.getSeed());
    var context =
        new FeaturePlaceContext<ResourceVeinConfiguration>(
            Optional.empty(),
            level,
            generator,
            net.minecraft.util.RandomSource.create(1),
            BlockPos.ZERO,
            config(new ResourceVeins.Settings(48, 48, 17, 31, 32)));
    h.assertTrue(
        level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null,
        "distant anchor already loaded");
    int expected =
        generator.getBaseHeight(
                x,
                z,
                Heightmap.Types.OCEAN_FLOOR_WG,
                net.minecraft.world.level.LevelHeightAccessor.create(
                    level.getMinBuildHeight(), level.getHeight()),
                random)
            - 1;
    int first = GeneratorTerrain.anchor(context, x, z);
    GeneratorTerrain.anchor(context, x + 32, z - 32);
    h.assertTrue(
        first == expected && first == GeneratorTerrain.anchor(context, x, z),
        "anchor changed with query order or generator random state");
    h.assertTrue(
        level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null,
        "anchor sampling force-loaded terrain");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ellipsoid_v0251", timeoutTicks = 200)
  public static void farCenterAndMaximumDepthWriteOnlyTheDecoratingChunk(GameTestHelper h) {
    var level = h.getLevel();
    var settings = new ResourceVeins.Settings(48, 48, 17, 31, 32, 64, 64, .12);
    var patch =
        ResourceVeins.patch(
            level.getSeed(), SALT, 7000 + records(level).claim() * 4, 7000, settings);
    int cx = (patch.x() >> 4) + 2, cz = patch.z() >> 4;
    var origin = new BlockPos(cx * 16, 70, cz * 16);
    h.assertTrue(
        level.getChunkSource().getChunkNow(patch.x() >> 4, patch.z() >> 4) == null,
        "anchor must start outside loaded region");
    level.getChunkAt(origin);
    for (int x = cx * 16; x < cx * 16 + 16; x++)
      for (int z = cz * 16; z < cz * 16 + 16; z++)
        for (int y = 7; y <= 133; y++) {
          var p = new BlockPos(x, y, z);
          h.assertTrue(level.getBlockState(p).isAir(), "occupied maximum-depth fixture");
          level.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
        }
    var cave = origin.offset(1, -1, 1);
    var water = origin.offset(2, -1, 2);
    var protectedPos = origin.offset(3, -1, 3);
    level.setBlock(cave, Blocks.AIR.defaultBlockState(), 2);
    level.setBlock(water, Blocks.WATER.defaultBlockState(), 2);
    level.setBlock(protectedPos, Blocks.CHEST.defaultBlockState(), 2);
    var generator =
        ResourceTerrainFixture.generator(
            level,
            (x, z) -> {
              if (x != patch.x() || z != patch.z())
                throw new IllegalStateException("Sampled local surface instead of anchor");
              return 70;
            });
    var feature = new ConfiguredFeature<>(Content.RESOURCE_PATCH_FEATURE.get(), config(settings));
    long start = System.nanoTime();
    feature.place(level, generator, net.minecraft.util.RandomSource.create(1), origin);
    long elapsed = System.nanoTime() - start;
    long count = 0, total = 0;
    var body = patch.atHeight(70);
    for (int x = cx * 16; x < cx * 16 + 16; x++)
      for (int z = cz * 16; z < cz * 16 + 16; z++)
        for (int y = 7; y <= 133; y++) {
          var p = new BlockPos(x, y, z);
          if (p.equals(cave) || p.equals(water) || p.equals(protectedPos)) continue;
          int expected = body.stock(x, y, z);
          var be = level.getBlockEntity(p);
          h.assertTrue(
              expected == 0
                  ? be == null
                  : be instanceof ResourceDepositBlockEntity r && r.remaining() == expected,
              "world-coordinate maximum-depth shape/stock differs");
          if (be instanceof ResourceDepositBlockEntity r) {
            count++;
            total += r.remaining();
          }
        }
    h.assertTrue(
        count > 100
            && level.getBlockState(cave).isAir()
            && level.getBlockState(water).is(Blocks.WATER)
            && level.getBlockEntity(protectedPos) instanceof ChestBlockEntity,
        "cave/liquid/block entity overwritten");
    feature.place(level, generator, net.minecraft.util.RandomSource.create(2), origin);
    new ConfiguredFeature<>(
            Content.RESOURCE_PATCH_FEATURE.get(),
            new ResourceVeinConfiguration(
                Content.IRON_ORE.get().defaultBlockState(), SALT, false, settings))
        .place(level, generator, net.minecraft.util.RandomSource.create(3), origin);
    long retained = 0;
    for (var p : BlockPos.betweenClosed(origin.offset(0, -63, 0), origin.offset(15, 63, 15)))
      if (level.getBlockEntity(p) instanceof ResourceDepositBlockEntity r) {
        h.assertTrue(
            level.getBlockState(p).is(Content.COPPER_ORE.get()),
            "different ore identity overwrote existing owner");
        retained += r.remaining();
      }
    h.assertTrue(retained == total, "repeat generation changed finite stock");
    h.assertTrue(
        level.getChunkSource().getChunkNow(patch.x() >> 4, patch.z() >> 4) == null,
        "generation loaded distant center chunk");
    System.out.println(
        "FACTORYCRAFT_ELLIPSOID_MAX_SLICE: owners="
            + count
            + " volume="
            + total
            + " nanos="
            + elapsed
            + " chunksWritten=1");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "ellipsoid_v0251", timeoutTicks = 200)
  public static void generatedBodyAndPartiallyExhaustedStocksSurviveIndependentJvm(GameTestHelper h)
      throws Exception {
    var level = h.getLevel();
    var data = records(level);
    if (data.digest.isEmpty()) {
      var result = ResourceTerrainFixture.build(level, 9000 + data.claim(), 4, false);
      data.center = result.center();
      var chestPos = data.center.offset(20, 15, 0);
      h.assertTrue(level.getBlockState(chestPos).isAir(), "occupied ore ledger; refusing reset");
      level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 2);
      var chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
      data.initial = total(level, data.center);
      var be = (ResourceDepositBlockEntity) level.getBlockEntity(data.center);
      be.onLoad();
      int extracted = Math.min(7, be.remaining() - 1);
      chest.setItem(0, new ItemStack(Items.RAW_COPPER, extracted));
      for (int i = 0; i < extracted; i++)
        h.assertTrue(be.extractAccepted(), "accepted reserve not debited");
      h.assertTrue(
          total(level, data.center) + chest.getItem(0).getCount() == data.initial,
          "ore ledger not conserved");
      data.digest = digest(level, data.center);
      data.setDirty();
      System.out.println("FACTORYCRAFT_ELLIPSOID_251_DISK: CREATED " + data.digest);
    } else System.out.println("FACTORYCRAFT_ELLIPSOID_251_DISK: VERIFYING " + data.digest);
    var chest = (ChestBlockEntity) level.getBlockEntity(data.center.offset(20, 15, 0));
    h.assertTrue(
        data.digest.equals(digest(level, data.center))
            && total(level, data.center) + chest.getItem(0).getCount() == data.initial,
        "body/reserve/component or mined output changed on disk");
    h.succeed();
  }

  private static long total(ServerLevel level, BlockPos center) {
    long total = 0;
    for (var p : BlockPos.betweenClosed(center.offset(-12, -12, -12), center.offset(12, 12, 12)))
      if (level.getBlockEntity(p) instanceof ResourceDepositBlockEntity r) total += r.remaining();
    return total;
  }

  private static String digest(ServerLevel level, BlockPos center) throws Exception {
    var hash = java.security.MessageDigest.getInstance("SHA-256");
    for (var p : BlockPos.betweenClosed(center.offset(-12, -12, -12), center.offset(12, 12, 12))) {
      var be = level.getBlockEntity(p);
      String s =
          p.toShortString()
              + "/"
              + level.getBlockState(p)
              + "/"
              + (be instanceof ResourceDepositBlockEntity r ? r.remaining() : 0)
              + ";";
      hash.update(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    return HexFormat.of().formatHex(hash.digest());
  }
}
