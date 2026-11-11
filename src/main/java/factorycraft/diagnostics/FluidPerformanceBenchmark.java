package factorycraft.diagnostics;

import com.google.gson.*;
import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.fluids.*;
import factorycraft.multiblock.*;
import factorycraft.registry.Content;
import factorycraft.simulation.benchmark.TickSamples;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;

/** Opt-in isolated real-server workload. Runtime production code never pins these chunks. */
@EventBusSubscriber(modid = FactoryCraft.ID)
public final class FluidPerformanceBenchmark {
  private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
  private static final com.sun.management.ThreadMXBean THREAD =
      (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
  private static Session active;

  @SubscribeEvent
  public static void started(ServerStartedEvent e) throws Exception {
    var fixture = System.getProperty("factorycraft.fluidBenchmark.fixture");
    if (fixture == null) return;
    var root = e.getServer().getServerDirectory().toAbsolutePath().normalize();
    FactoryBenchmarkWorld.require(
        root.getParent().getFileName().toString().equals("run-benchmark")
            && root.getFileName().toString().equals(fixture)
            && Files.readString(root.resolve("fluid-benchmark.marker"))
                .equals("FactoryCraft fluid performance v1\n"),
        "Not an isolated fluid fixture");
    active = new Session(e.getServer(), root);
    try {
      active.start();
    } catch (Throwable x) {
      active.fail(x);
    }
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void pre(ServerTickEvent.Pre e) {
    if (active != null && !active.done) {
      active.startNs = System.nanoTime();
      active.startAllocated = THREAD.getThreadAllocatedBytes(Thread.currentThread().threadId());
      active.measuredPhase = active.phase;
      try {
        active.step();
      } catch (Throwable x) {
        active.fail(x);
      }
    }
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void post(ServerTickEvent.Post e) {
    if (active != null && !active.done) {
      var s = active;
      s.times
          .computeIfAbsent(s.measuredPhase, k -> new TickSamples(2000))
          .add(System.nanoTime() - s.startNs);
      s.allocations.merge(
          s.measuredPhase,
          THREAD.getThreadAllocatedBytes(Thread.currentThread().threadId()) - s.startAllocated,
          Long::sum);
      if (s.phase.equals("finish") && s.measuredPhase.equals("finish")) {
        try {
          s.audit();
          s.finish();
        } catch (Throwable x) {
          s.fail(x);
        }
      }
    }
  }

  @SubscribeEvent
  public static void stopped(ServerStoppedEvent e) {
    if (active != null && active.recording != null) active.recording.close();
    FactoryTelemetry.end();
    active = null;
  }

  private static final class Session {
    final MinecraftServer server;
    final ServerLevel level;
    final Path root;
    final boolean verify;
    final int size;
    final ArrayList<BlockPos> path = new ArrayList<>();
    final ArrayList<BlockPos> mainExtras = new ArrayList<>();
    final TreeSet<Long> chunks = new TreeSet<>();
    final Map<String, TickSamples> times = new LinkedHashMap<>();
    final Map<String, Long> allocations = new LinkedHashMap<>();
    final ArrayList<FluidBlockEntity> sources = new ArrayList<>(), targets = new ArrayList<>();
    final FluidKey water = new FluidKey(new FluidStack(Fluids.WATER, 1));
    String phase = "build", measuredPhase = "build";
    int cursor, tick, cycle;
    boolean done, seeded;
    long startNs, startAllocated, bank = 1000000, delivered, auxConsumed, auxInitial = 64000;
    long releasedChunk;
    final ArrayList<Long> released = new ArrayList<>();
    Map<BlockPos, Integer> frozen = new HashMap<>();
    ItemStack packed = ItemStack.EMPTY;
    long baseline = 500000;
    BenchmarkRecording recording;
    Instant window;
    FactoryTelemetry.Window telemetry;
    long heap;

    Session(MinecraftServer server, Path root) {
      this.server = server;
      level = server.overworld();
      this.root = root;
      verify = System.getProperty("factorycraft.fluidBenchmark.mode").equals("verify");
      size = Integer.getInteger("factorycraft.fluidBenchmark.size", 10000);
      if (size != 10000) throw new IllegalArgumentException("Invalid pipe count");
      for (int row = 0; path.size() < size; row++) {
        for (int x = 0; x < 128 && path.size() < size; x++)
          path.add(new BlockPos(1024 + (row % 2 == 0 ? x : 127 - x), 80, 1024 + row * 2));
        if (path.size() < size) path.add(path.getLast().south());
      }
      for (int i = 0; i < 8; i++) {
        var base = path.get(300 + i * 1100);
        var stem = base.offset(-1, 0, -1);
        for (int y = 0; y <= 4; y++) mainExtras.add(stem.above(y));
        mainExtras.add(base.above(4).south());
      }
      for (var p : path) chunks.add(new ChunkPos(p).toLong());
      for (int x = 63; x <= 75; x++)
        for (int z = 63; z <= 84; z++) chunks.add(ChunkPos.asLong(x, z));
    }

    void start() throws Exception {
      Files.createDirectories(root.resolve("reports"));
      level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
      level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, server);
      for (long c : chunks) {
        level.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), true);
        level.getChunk(ChunkPos.getX(c), ChunkPos.getZ(c));
      }
      if (verify) {
        var expected =
            JsonParser.parseString(Files.readString(root.resolve("checkpoint.json")))
                .getAsJsonObject();
        FactoryBenchmarkWorld.require(
            hash().equals(expected.get("hash").getAsString()),
            "Fluid checkpoint hash mismatch; refusing reseed");
        var report = new JsonObject();
        report.addProperty("status", "PASS");
        report.addProperty("checkpointHash", expected.get("hash").getAsString());
        var saved =
            JsonParser.parseString(Files.readString(root.resolve("reports/measure.json")))
                .getAsJsonObject();
        FactoryBenchmarkWorld.require(
            mainMass() == saved.get("mainBalance").getAsLong()
                && mainMass() + saved.get("bank").getAsLong() + saved.get("delivered").getAsLong()
                    == 1500000,
            "Saved finite main ledger mismatch");
        report.addProperty("mainBalance", mainMass());
        report.addProperty("bank", saved.get("bank").getAsLong());
        report.addProperty("delivered", saved.get("delivered").getAsLong());

        Files.writeString(root.resolve("reports/verify.json"), JSON.toJson(report));
        done = true;
        server.halt(false);
        return;
      }
      for (var p : path)
        FactoryBenchmarkWorld.require(
            level.getBlockState(p).isAir(), "Occupied measurement fixture");
      telemetry = FactoryTelemetry.begin(level);
      recording = new BenchmarkRecording(root.resolve("reports/profile.jfr"));
      window = Instant.now();
    }

    FluidBlockEntity pipe(BlockPos p) {
      level.setBlockAndUpdate(
          p, Content.FLUID_BLOCKS.get(FluidKind.PIPE).get().defaultBlockState());
      return (FluidBlockEntity) level.getBlockEntity(p);
    }

    FluidBlockEntity be(BlockPos p) {
      return (FluidBlockEntity) level.getBlockEntity(p);
    }

    void next(String p) {
      phase = p;
      tick = 0;
    }

    boolean ready() {
      var a = be(path.getFirst());
      return a != null
          && FluidSimulation.segment(a) != null
          && FluidSimulation.segment(a).valid()
          && FluidSimulation.segment(a).size() >= size;
    }

    void auxiliaries() {
      for (int i = 0; i < 8; i++) {
        var base = path.get(Math.min(size - 1, 300 + i * 1100));
        var stem = base.offset(-1, 0, -1);
        for (int y = 0; y <= 4; y++) {
          pipe(stem.above(y));
        }
        var tank = base.above(4).south();
        FluidCircuitFactory.fluid(level, tank, FluidKind.TANK);
      }
      for (int i = 0; i < 16; i++) {
        var o = new BlockPos(1024 + (i % 4) * 20, 80, 1200 + (i / 4) * 20);
        FluidCircuitFactory.build(level, o);
        sources.add(be(o.offset(3, 0, 5)));
        targets.add(be(o.offset(11, 0, 7)));
      }
      factorycraft.core.FactoryCommands.oilLine(level, new BlockPos(1120, 80, 1260));
    }

    void flow() {
      if (!ready()) return;
      var a = be(path.getFirst()).hydraulic(path.getFirst(), Direction.UP);
      var b = be(path.getLast()).hydraulic(path.getLast(), Direction.UP);
      for (int i = 0; i < 100; i++) {
        int n = b.drain(1, false);
        delivered += n;
        if (bank > 0) {
          int accepted = a.fill(water, 1, false);
          bank -= accepted;
        }
      }
      for (var target : targets) {
        var p = target.assembly().ports().getFirst();
        var c = target.hydraulic(p.position(), p.side());
        auxConsumed += FluidSimulation.manual(() -> c.drain(10, false));
      }
    }

    long mainMass() {
      long n = 0;
      for (var p : path) {
        var b = be(p);
        if (b != null) n += b.cell().amount();
        else if (frozen.containsKey(p)) n += frozen.get(p);
      }
      for (var p : mainExtras) {
        var b = be(p);
        if (b != null) n += b.cell().amount();
        else if (frozen.containsKey(p)) n += frozen.get(p);
      }
      if (!packed.isEmpty()) {
        var tag = packed.get(DataComponents.BLOCK_ENTITY_DATA).copyTag();
        n +=
            FluidStack.parseOptional(level.registryAccess(), tag.getCompound("StoredFluid"))
                .getAmount();
      }
      return n;
    }

    void audit() {
      FactoryBenchmarkWorld.require(
          mainMass() + bank + delivered == baseline + 1000000,
          "Main fluid ledger changed: " + mainMass() + " bank=" + bank + " delivered=" + delivered);
      long aux = auxConsumed;
      for (int i = 0; i < 16; i++) {
        var o = new BlockPos(1024 + (i % 4) * 20, 80, 1200 + (i / 4) * 20);
        for (var p : BlockPos.betweenClosed(o, o.offset(14, 3, 9)))
          if (level.getBlockEntity(p) instanceof FluidBlockEntity f) aux += f.cell().amount();
      }
      FactoryBenchmarkWorld.require(aux == auxInitial, "Auxiliary pump ledger changed: " + aux);
    }

    void pack(BlockPos p) {
      var b = be(p);
      packed = new ItemStack(Content.FLUID_BLOCKS.get(FluidKind.PIPE).get());
      b.saveToItem(packed, level.registryAccess());
      level.removeBlock(p, false);
    }

    void replace(BlockPos p) {
      pipe(p)
          .loadWithComponents(
              packed.get(DataComponents.BLOCK_ENTITY_DATA).copyTag(), level.registryAccess());
      packed = ItemStack.EMPTY;
    }

    void step() throws Exception {
      tick++;
      switch (phase) {
        case "build" -> {
          for (int end = Math.min(size, cursor + 64); cursor < end; cursor++)
            pipe(path.get(cursor));
          if (cursor == size) {
            auxiliaries();
            next("warm");
          }
        }
        case "warm" -> {
          if (ready() && !seeded) {
            int n =
                FluidSimulation.manual(
                    () ->
                        be(path.getFirst())
                            .hydraulic(path.getFirst(), Direction.UP)
                            .fill(water, (int) baseline, false));
            FactoryBenchmarkWorld.require(n == baseline, "Seed refused");
            seeded = true;
          }
          if (tick > 120 && ready() && seeded) {
            System.gc();
            heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
            audit();
            next("stable");
          }
          if (tick > 800) throw new IllegalStateException("Initial topology not ready");
        }
        case "stable" -> {
          flow();
          if (tick >= 120) {
            audit();
            next("construction");
          }
        }
        case "construction" -> { // Real leaf placement/removal on alternating ticks; filled blocks
          // are carried, never voided.
          var p = path.getLast().above();
          if (tick % 4 == 1) pipe(p);
          if (tick % 4 == 3) {
            pack(p);
            long carried =
                FluidStack.parseOptional(
                        level.registryAccess(),
                        packed
                            .get(DataComponents.BLOCK_ENTITY_DATA)
                            .copyTag()
                            .getCompound("StoredFluid"))
                    .getAmount();
            int restored =
                FluidSimulation.manual(
                    () ->
                        be(path.getFirst())
                            .hydraulic(path.getFirst(), Direction.UP)
                            .fill(water, (int) carried, false));
            bank += carried - restored;
            packed = ItemStack.EMPTY;
          }
          if (tick >= 80) {
            next("settle-cut");
          }
        }
        case "settle-cut" -> {
          if (ready()) {
            audit();
            next("split-merge");
          }
          if (tick > 500) throw new IllegalStateException("No construction recovery");
        }
        case "split-merge" -> {
          if (tick % 12 == 1) pack(path.get(size / 2));
          if (tick % 12 == 6) replace(path.get(size / 2));
          if (tick >= 72) {
            next("settle-unload");
          }
        }
        case "settle-unload" -> {
          if (ready()) {
            audit();
            releasedChunk = new ChunkPos(path.get(size / 2)).toLong();
            released.clear();
            int centerX = ChunkPos.getX(releasedChunk), centerZ = ChunkPos.getZ(releasedChunk);
            for (int x = centerX - 2; x <= centerX + 2; x++)
              for (int z = centerZ - 2; z <= centerZ + 2; z++) {
                long cp = ChunkPos.asLong(x, z);
                if (chunks.contains(cp)) released.add(cp);
              }
            var all = new ArrayList<>(path);
            all.addAll(mainExtras);
            for (var p : all)
              if (released.contains(new ChunkPos(p).toLong())) frozen.put(p, be(p).cell().amount());
            for (long cp : released)
              level.setChunkForced(ChunkPos.getX(cp), ChunkPos.getZ(cp), false);
            next("unload");
          }
          if (tick > 500) throw new IllegalStateException("No split recovery");
        }
        case "unload" -> {
          if (level
                  .getChunkSource()
                  .getChunkNow(ChunkPos.getX(releasedChunk), ChunkPos.getZ(releasedChunk))
              == null) {
            next("reload");
          }
          if (tick > 400) throw new IllegalStateException("Chunk did not naturally unload");
        }
        case "reload" -> {
          if (tick == 1)
            for (long cp : released) {
              level.setChunkForced(ChunkPos.getX(cp), ChunkPos.getZ(cp), true);
              level.getChunk(ChunkPos.getX(cp), ChunkPos.getZ(cp));
            }
          if (tick > 15 && ready()) {
            frozen.clear();
            audit();
            if (++cycle < 3) next("stable");
            else next("finish");
          }
          if (tick > 500) throw new IllegalStateException("No reload recovery");
        }
        case "finish" -> {}
      }
    }

    String hash() throws Exception {
      var digest = MessageDigest.getInstance("SHA-256");
      var all = new TreeMap<BlockPos, net.minecraft.world.level.block.entity.BlockEntity>();
      for (long cp : chunks) {
        var c = level.getChunkSource().getChunkNow(ChunkPos.getX(cp), ChunkPos.getZ(cp));
        if (c != null) all.putAll(c.getBlockEntities());
      }
      for (var entry : all.entrySet()) {
        digest.update(entry.getKey().toString().getBytes(StandardCharsets.UTF_8));
        var tag = entry.getValue().saveWithoutMetadata(level.registryAccess());
        tag.remove("SnapshotTick");
        tag.remove("PhaseTick");
        FactoryBenchmarkWorld.hashTag(digest, tag);
      }
      return HexFormat.of().formatHex(digest.digest());
    }

    void finish() throws Exception {
      var report = new JsonObject();
      report.addProperty("status", "PASS");
      report.addProperty("nodes", size);
      report.addProperty("clients", server.getPlayerCount());
      int plastic = 0;
      var output =
          (net.minecraft.world.Container) level.getBlockEntity(new BlockPos(1139, 80, 1263));
      for (int slot = 0; slot < output.getContainerSize(); slot++)
        if (output
            .getItem(slot)
            .is(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        FactoryCraft.ID, "plastic_bar"))))
          plastic += output.getItem(slot).getCount();
      FactoryBenchmarkWorld.require(
          plastic > 0, "Finite petrochemical line did not produce plastic");
      report.addProperty("plasticBars", plastic);

      report.addProperty("heapAfterWarmGc", heap);
      report.addProperty("mainBalance", mainMass());
      report.addProperty("bank", bank);
      report.addProperty("delivered", delivered);
      report.addProperty("auxConsumed", auxConsumed);
      report.add("metrics", JSON.toJsonTree(FluidSimulation.metrics(level)));
      report.addProperty("syncRequests", telemetry.syncRequests);
      var stages = new JsonObject();
      for (var e : times.entrySet()) {
        var o = JSON.toJsonTree(e.getValue().summarize()).getAsJsonObject();
        o.addProperty("allocatedBytes", allocations.get(e.getKey()));
        stages.add(e.getKey(), o);
      }
      report.add("stages", stages);
      var checkpoint = new JsonObject();
      checkpoint.addProperty("hash", hash());
      Files.writeString(root.resolve("checkpoint.json"), JSON.toJson(checkpoint));
      long t = System.nanoTime();
      server.saveEverything(true, true, true);
      report.addProperty("saveMs", (System.nanoTime() - t) / 1e6);
      report.add("packets", JSON.toJsonTree(recording.finish(window, Instant.now())));
      Files.writeString(root.resolve("reports/measure.json"), JSON.toJson(report));
      System.out.println(
          "FACTORYCRAFT_FLUID_PROFILE PASS "
              + root.getFileName()
              + " "
              + FluidSimulation.statistics(level));
      done = true;
      server.halt(false);
    }

    void fail(Throwable x) {
      x.printStackTrace();
      try {
        Files.createDirectories(root.resolve("reports"));
        var r = new JsonObject();
        r.addProperty("status", "FAIL");
        r.addProperty("phase", phase);
        r.addProperty("error", x.toString());
        Files.writeString(
            root.resolve("reports/" + (verify ? "verify" : "measure") + ".json"), JSON.toJson(r));
      } catch (Exception ignored) {
      }
      done = true;
      server.halt(false);
    }
  }

  private FluidPerformanceBenchmark() {}
}
