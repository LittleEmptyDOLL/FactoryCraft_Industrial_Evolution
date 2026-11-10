package factorycraft.diagnostics;

import static factorycraft.diagnostics.FactoryBenchmarkWorld.require;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.config.FactoryConfig;
import factorycraft.energy.PowerGrid;
import factorycraft.simulation.BeltSimulation;
import factorycraft.simulation.benchmark.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.*;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Explicit dev-server harness. Never starts, pins chunks or mutates a regular game world. */
@EventBusSubscriber(modid = FactoryCraft.ID)
public final class FactoryBenchmark {
  public static final String MARKER = "FactoryCraft isolated benchmark v1\n";
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final Gson JSON =
      new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
  private static Session session;

  @SubscribeEvent
  public static void started(ServerStartedEvent event) throws Exception {
    String profile = System.getProperty("factorycraft.benchmark.profile");
    if (profile == null) return;
    var server = event.getServer();
    var root = server.getServerDirectory().toAbsolutePath().normalize();
    require(
        server.isDedicatedServer()
            && root.getFileName().toString().matches(profile + "(?:-[a-z0-9]+)*")
            && root.getParent().getFileName().toString().equals("run-benchmark")
            && Files.readString(root.resolve("factorycraft-benchmark.marker")).equals(MARKER),
        "Not an isolated FactoryCraft benchmark directory");
    session =
        new Session(
            server,
            root,
            BenchmarkPlan.Profile.parse(profile),
            System.getProperty("factorycraft.benchmark.mode", "measure"));
    try {
      session.start();
    } catch (Exception error) {
      session.fail(error);
    }
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void pre(ServerTickEvent.Pre event) {
    var s = session;
    if (s == null || s.phase != Phase.MEASURE) return;
    s.tickStart = System.nanoTime();
    if (s.samples.size() == 0) {
      s.windowStart = s.tickStart;
      s.packetWindowStart = Instant.now();
    }
    try {
      if (s.samples.size() == s.profile.ticks / 2 - 1) s.world.removeProbe();
      if (s.samples.size() == s.profile.ticks / 2 + 19) s.world.restoreProbe();
    } catch (Exception error) {
      s.fail(error);
    }
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void post(ServerTickEvent.Post event) {
    var s = session;
    if (s == null || s.phase == Phase.DONE) return;
    long end = System.nanoTime();
    try {
      s.tick(end);
    } catch (Exception error) {
      s.fail(error);
    }
  }

  @SubscribeEvent
  public static void chunkUnloaded(net.neoforged.neoforge.event.level.ChunkEvent.Unload event) {
    var s = session;
    if (s != null
        && s.phase == Phase.UNLOAD
        && event.getLevel() == s.world.level
        && s.world.chunkKeys().contains(event.getChunk().getPos().toLong()))
      s.naturallyUnloaded.add(event.getChunk().getPos().toLong());
  }

  @SubscribeEvent
  public static void stopped(ServerStoppedEvent event) {
    if (session != null) session.close();
    session = null;
  }

  private enum Phase {
    BUILD,
    SETTLE,
    WARM,
    ARMING,
    MEASURE,
    UNLOAD,
    RELOADED,
    DONE
  }

  private static final class Session {
    final MinecraftServer server;
    final Path root, reportPath, fixturePath;
    final BenchmarkPlan.Profile profile;
    final boolean verify, resume;
    final FactoryBenchmarkWorld world;
    final TickSamples samples;
    final JsonObject result = new JsonObject();
    final Set<Long> naturallyUnloaded = new HashSet<>();
    FactoryBenchmarkWorld.Audit beforeUnload;
    Phase phase = Phase.BUILD;
    int delay = 3, maxPlayers;
    long tickStart, windowStart, gcCount, gcMillis;
    Instant packetWindowStart;
    FactoryBenchmarkWorld.Audit before;
    BeltSimulation.Snapshot beltsBefore;
    PowerGrid.Snapshot powerBefore;
    FactoryTelemetry.Window telemetry;
    BenchmarkRecording packets;

    Session(MinecraftServer server, Path root, BenchmarkPlan.Profile profile, String mode)
        throws Exception {
      require(
          mode.equals("measure") || mode.equals("verify") || mode.equals("resume"),
          "Unknown benchmark mode");
      this.server = server;
      this.root = root;
      this.profile = profile;
      verify = mode.equals("verify");
      resume = mode.equals("resume");
      world = new FactoryBenchmarkWorld(server.overworld(), profile);
      samples = new TickSamples(profile.ticks);
      Files.createDirectories(root.resolve("reports"));
      reportPath =
          root.resolve("reports/" + profile.name().toLowerCase(Locale.ROOT) + "-" + mode + ".json");
      fixturePath = root.resolve("factorycraft-fixture.json");
      result.addProperty("schema", 1);
      result.addProperty("profile", profile.name());
      result.addProperty("mode", mode);
      result.addProperty("startedUtc", Instant.now().toString());
      result.addProperty("minecraft", "1.21.1");
      result.addProperty(
          "modVersion",
          ModList.get()
              .getModContainerById(FactoryCraft.ID)
              .orElseThrow()
              .getModInfo()
              .getVersion()
              .toString());
      result.addProperty(
          "neoForge",
          ModList.get()
              .getModContainerById("neoforge")
              .orElseThrow()
              .getModInfo()
              .getVersion()
              .toString());
      result.addProperty("java", System.getProperty("java.version"));
      result.addProperty("os", System.getProperty("os.name"));
      result.addProperty("arch", System.getProperty("os.arch"));
      result.addProperty("availableProcessors", Runtime.getRuntime().availableProcessors());
      result.addProperty("maxHeapBytes", Runtime.getRuntime().maxMemory());
      result.addProperty(
          "measurementScope",
          "Dedicated server tick work with forced ticking chunks; harness census, report writing,"
              + " startup and save are outside samples. JFR bytes are encoded packet bytes, not TCP"
              + " bytes. No client FPS claim.");
    }

    void start() throws Exception {
      require(
          verify == Files.exists(fixturePath),
          verify
              ? "No saved fixture to verify"
              : "Fixture already exists; use -PbenchmarkMode=verify or resetBenchmarkWorld");
      JsonObject saved =
          verify ? JsonParser.parseString(Files.readString(fixturePath)).getAsJsonObject() : null;
      if (verify)
        require(
            saved.get("schema").getAsInt() == 1
                && saved.get("profile").getAsString().equals(profile.name()),
            "Wrong saved fixture profile/schema");
      var rules = world.level.getGameRules();
      rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
      rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
      rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
      rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
      world.pin();
      result.addProperty("forcedTickingChunks", world.pinnedChunks());
      if (verify) {
        String actual = world.persistentHash();
        result.addProperty("initialReloadedStateHash", actual);
        require(
            saved.get("stateHash").getAsString().equals(actual),
            "Exact persisted machine/belt/hand state changed across restart");
      }
      if (verify || resume) {
        phase = Phase.SETTLE;
        delay = 10;
      }
      if (resume) {
        require(
            profile == BenchmarkPlan.Profile.MIXED && !Files.exists(fixturePath),
            "Resume is only for the unreleased mixed unload diagnostic");
        var prior =
            JsonParser.parseString(Files.readString(root.resolve("reports/mixed-measure.json")))
                .getAsJsonObject();
        require(
            prior.get("status").getAsString().equals("FAIL")
                && prior.get("failure").getAsString().contains("Native chunk unload timed out"),
            "Wrong diagnostic for resume");
        result.add("initialMeasurement", prior);
      }

      LOGGER.info(
          "FACTORYCRAFT_BENCHMARK: {} {} starting; {} cells, {} pinned chunks",
          profile,
          verify ? "verify" : "measure",
          world.plan.blocks().size(),
          world.pinnedChunks());
    }

    void tick(long tickEnd) throws Exception {
      switch (phase) {
        case BUILD -> {
          if (world.buildStep(256)) {
            phase = Phase.SETTLE;
            delay = 3;
          }
        }
        case SETTLE -> {
          if (--delay > 0) return;
          if (verify) {
            verifyDisk();
            return;
          }
          if (resume) {
            beforeUnload = world.audit();
            var expected =
                result
                    .getAsJsonObject("initialMeasurement")
                    .getAsJsonObject("after")
                    .get("identityHash")
                    .getAsString();
            require(
                beforeUnload.identityHash().equals(expected), "Resume changed prior identities");
            result.add("beforeNaturalUnload", JSON.toJsonTree(beforeUnload));
            phase = Phase.UNLOAD;
            delay = 1000;
            world.release();
            return;
          }
          world.seed();
          world.audit();
          phase = Phase.WARM;
          delay = profile.warmup;
          LOGGER.info("FACTORYCRAFT_BENCHMARK: seeded; warming up for {} real server ticks", delay);
        }
        case WARM -> {
          if (--delay == 0) {
            packets =
                new BenchmarkRecording(
                    root.resolve(
                        "reports/" + profile.name().toLowerCase(Locale.ROOT) + "-profile.jfr"));
            phase = Phase.ARMING;
            delay = 200;
          }
        }
        case ARMING -> {
          if (--delay == 0) beginMeasurement();
        }
        case MEASURE -> {
          samples.add(tickEnd - tickStart);
          maxPlayers = Math.max(maxPlayers, server.getPlayerCount());
          if (samples.size() == profile.ticks) finish(tickEnd);
        }
        case UNLOAD -> {
          if (naturallyUnloaded.size() == world.chunkKeys().size()) {
            server.saveEverything(true, true, true);
            result.addProperty("naturallyUnloadedChunks", naturallyUnloaded.size());
            world.pin();
            result.addProperty("naturalReloadStateHash", world.persistentHash());
            phase = Phase.RELOADED;
            delay = 10;
          } else if (--delay == 0)
            throw new IllegalStateException(
                "Native chunk unload timed out: "
                    + naturallyUnloaded.size()
                    + "/"
                    + world.chunkKeys().size());
        }
        case RELOADED -> {
          if (--delay == 0) {
            var actual = world.audit();
            require(
                actual.identityHash().equals(beforeUnload.identityHash()),
                "Natural reload changed structure owners");
            result.add("afterNaturalReload", JSON.toJsonTree(actual));
            result.addProperty("savedStateHash", checkpoint(actual));
            pass();
          }
        }
        case DONE -> {}
      }
    }

    void beginMeasurement() {
      before = world.audit();
      require(before.activeBelts() >= profile.loops * 50, "Belt workload is mostly asleep");
      beltsBefore = BeltSimulation.snapshot(world.level);
      powerBefore = PowerGrid.snapshot(world.level);
      require(!powerBefore.pending(), "Power topology did not finish warming up");
      telemetry = FactoryTelemetry.begin(world.level);
      gcCount = gc(false);
      gcMillis = gc(true);
      phase = Phase.MEASURE;
      LOGGER.info("FACTORYCRAFT_BENCHMARK: measuring {} real server ticks", profile.ticks);
    }

    void finish(long tickEnd) throws Exception {
      var packetWindowEnd = Instant.now();
      long collections = gc(false) - gcCount, collectionMillis = gc(true) - gcMillis;
      FactoryTelemetry.end();
      var packetSummary = packets.finish(packetWindowStart, packetWindowEnd);
      packets.close();
      packets = null;
      result.addProperty("measurementStartedUtc", packetWindowStart.toString());
      result.addProperty("measurementEndedUtc", packetWindowEnd.toString());
      var after = world.audit();
      var belts = BeltSimulation.snapshot(world.level);
      var power = PowerGrid.snapshot(world.level);
      long work = 0, production = 0, transferred = 0, beltChangedTicks = 0, spent = 0;
      int working = 0, producing = 0, arms = 0, changedBelts = 0;
      for (var entry : telemetry.entities.entrySet()) {
        var c = entry.getValue();
        if (entry.getKey() instanceof MachineBlockEntity) {
          if (c.worked > 0) working++;
          if (c.produced > 0) producing++;
          work += c.worked;
          production += c.produced;
          spent += c.energySpent;
        } else if (entry.getKey() instanceof InserterBlockEntity) {
          if (c.transferred > 0) arms++;
          transferred += c.transferred;
        } else if (entry.getKey() instanceof BeltBlockEntity) {
          if (c.worked > 0) changedBelts++;
          beltChangedTicks += c.worked;
        }
      }
      result.add("tickWork", JSON.toJsonTree(samples.summarize()));
      result.addProperty("observedWallTps", samples.size() * 1e9 / (tickEnd - windowStart));
      result.addProperty("targetTps", server.tickRateManager().tickrate());
      result.addProperty("sampleWindowSeconds", (tickEnd - windowStart) / 1e9);
      result.addProperty("recordingSettleTicks", 200);
      result.addProperty("warmupTicks", profile.warmup);
      result.addProperty("maxConnectedPlayers", maxPlayers);
      result.addProperty("gcCollections", collections);
      result.addProperty("gcMillis", collectionMillis);
      result.add("before", JSON.toJsonTree(before));
      result.add("after", JSON.toJsonTree(after));
      result.addProperty("loadedBeltSegments", belts.loaded());
      result.addProperty("expectedMovingResourceOwners", world.plan.movingBelts());
      if (profile == BenchmarkPlan.Profile.MIXED) {
        var kinds = new TreeMap<String, Long>();
        for (var cell : world.plan.blocks()) kinds.merge(cell.role().name(), 1L, Long::sum);
        result.add("physicalFixtureKinds", JSON.toJsonTree(kinds));
      }
      result.addProperty("productionMachinesWithWork", working);
      result.addProperty("beltsWithMovementOrTransfer", changedBelts);
      result.addProperty("beltChangedTicks", beltChangedTicks);
      result.addProperty("productionMachinesWithResults", producing);
      result.addProperty("additionalGenerators", profile.cells);
      result.addProperty("insertersWithDeliveries", arms);
      result.addProperty("machineWorkTicks", work);
      result.addProperty("completedRecipes", production);
      result.addProperty("inserterDeliveredItems", transferred);
      result.addProperty("beltCrossSegmentTransfers", belts.transfers() - beltsBefore.transfers());
      result.addProperty(
          "beltKernelCpuMs", (belts.kernelNanos() - beltsBefore.kernelNanos()) / 1e6);
      result.addProperty(
          "powerSnapshotsDuringMeasurement", power.rebuilds() - powerBefore.rebuilds());
      result.addProperty("powerNetworksAtStart", powerBefore.networks());
      result.addProperty("powerNetworksAtEnd", power.networks());
      result.addProperty("powerNodesAtEnd", power.nodes());
      result.addProperty("powerCandidateChecks", power.candidates() - powerBefore.candidates());
      result.addProperty(
          "powerRebuildCpuMs", (power.rebuildNanos() - powerBefore.rebuildNanos()) / 1e6);
      result.addProperty("powerDeliveredFE", power.delivered() - powerBefore.delivered());
      result.addProperty("productionEnergySpentFE", spent);
      result.addProperty("powerGraphWorkBudget", FactoryConfig.POWER_GRAPH_WORK.get());
      result.addProperty("generatorFePerTick", FactoryConfig.GENERATOR_FE.get());
      result.addProperty("powerVerticalReach", FactoryConfig.POWER_VERTICAL_REACH.get());
      result.addProperty("beSyncRequests", telemetry.syncRequests);
      result.add("network", JSON.toJsonTree(packetSummary));
      require(before.identityHash().equals(after.identityHash()), "Structure identities changed");
      require(
          before.storedFE() + power.delivered() - powerBefore.delivered()
              == after.storedFE() + spent,
          "Power energy conservation failed");
      require(
          working == profile.cells * 4
              && producing == profile.cells * 4
              && arms == profile.cells * 2,
          "Not all producers/inserters performed real work");
      require(changedBelts == world.plan.movingBelts(), "Not all belt segments moved items");
      require(
          after.activeBelts() >= profile.loops * 50 && belts.transfers() > beltsBefore.transfers(),
          "Belt workload stopped moving");
      require(
          !power.pending()
              && power.nodes() == profile.cells * 5 + BenchmarkPlan.poleCount(profile)
              && power.networks() == 1
              && power.rebuilds() - powerBefore.rebuilds() >= 2,
          "Power split/rejoin did not complete");
      if (profile == BenchmarkPlan.Profile.MIXED) {
        require(server.getPlayerCount() == 0, "Mixed unload proof requires no player tickets");
        beforeUnload = after;
        phase = Phase.UNLOAD;
        delay = 1000;
        world.release();
        LOGGER.info(
            "FACTORYCRAFT_BENCHMARK: released all fixture tickets; waiting for native"
                + " ChunkEvent.Unload");
      } else {
        result.addProperty("savedStateHash", checkpoint(after));
        pass();
      }
    }

    void verifyDisk() throws Exception {
      var fixture = JsonParser.parseString(Files.readString(fixturePath)).getAsJsonObject();
      require(
          fixture.get("schema").getAsInt() == 1
              && fixture.get("profile").getAsString().equals(profile.name()),
          "Wrong saved fixture profile/schema");
      var saved = JSON.fromJson(fixture.get("audit"), FactoryBenchmarkWorld.Audit.class);
      var actual = world.audit();
      require(
          saved.beltItems() == actual.beltItems()
              && saved.ironUnits() == actual.ironUnits()
              && saved.identityHash().equals(actual.identityHash()),
          "Saved factory failed resource/identity verification");
      result.add("saved", JSON.toJsonTree(saved));
      result.add("reloaded", JSON.toJsonTree(actual));
      // Advance the checkpoint only after exact pre-tick verification and a successful resumed
      // audit.
      // Further verify runs must compare against the state actually saved by this process.
      result.addProperty("savedStateHash", checkpoint(actual));
      LOGGER.info(
          "FACTORYCRAFT_BENCHMARK_DISK: VERIFIED {} belts, {} producers, {} inserters, {} casings",
          profile.loops * 100,
          actual.producers(),
          actual.inserters(),
          actual.casings());
      pass();
    }

    String checkpoint(FactoryBenchmarkWorld.Audit audit) throws Exception {
      String hash = world.persistentHash();
      server.saveEverything(true, true, true);
      var fixture = new JsonObject();
      fixture.addProperty("schema", 1);
      fixture.addProperty("profile", profile.name());
      fixture.add("audit", JSON.toJsonTree(audit));
      fixture.addProperty("stateHash", hash);
      Path temporary = fixturePath.resolveSibling("factorycraft-fixture.json.tmp");
      Files.writeString(temporary, JSON.toJson(fixture) + "\n");
      try {
        Files.move(
            temporary,
            fixturePath,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temporary, fixturePath, StandardCopyOption.REPLACE_EXISTING);
      }
      return hash;
    }

    void pass() throws Exception {
      result.addProperty("status", "PASS");
      Files.writeString(reportPath, JSON.toJson(result) + "\n");
      LOGGER.info("FACTORYCRAFT_BENCHMARK: PASS {}; report {}", profile, reportPath.getFileName());
      phase = Phase.DONE;
      world.release();
      server.halt(false);
    }

    void fail(Exception error) {
      LOGGER.error("FACTORYCRAFT_BENCHMARK: FAIL", error);
      result.addProperty("status", "FAIL");
      result.addProperty("failure", error.toString());
      try {
        Files.writeString(reportPath, JSON.toJson(result) + "\n");
      } catch (Exception writeError) {
        LOGGER.error("Cannot write benchmark failure", writeError);
      }
      phase = Phase.DONE;
      close();
      world.release();
      server.halt(false);
    }

    void close() {
      FactoryTelemetry.end();
      if (packets != null) {
        packets.close();
        packets = null;
      }
    }

    static long gc(boolean millis) {
      long sum = 0;
      for (var bean : ManagementFactory.getGarbageCollectorMXBeans()) {
        long n = millis ? bean.getCollectionTime() : bean.getCollectionCount();
        if (n >= 0) sum += n;
      }
      return sum;
    }
  }

  private FactoryBenchmark() {}
}
