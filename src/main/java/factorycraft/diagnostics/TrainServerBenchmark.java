package factorycraft.diagnostics;

import com.google.gson.*;
import factorycraft.FactoryCraft;
import factorycraft.rail.*;
import factorycraft.simulation.benchmark.TickSamples;
import factorycraft.simulation.rail.*;
import factorycraft.simulation.trains.*;
import factorycraft.trains.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Opt-in actual-server workload; all chunk tickets and driving decisions belong to this fixture.
 */
@EventBusSubscriber(modid = FactoryCraft.ID)
public final class TrainServerBenchmark {
  private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
  private static final com.sun.management.ThreadMXBean THREAD =
      (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
  private static Session active;

  @SubscribeEvent
  public static void started(ServerStartedEvent e) throws Exception {
    String fixture = System.getProperty("factorycraft.trainBenchmark.fixture");
    if (fixture == null) return;
    var root = e.getServer().getServerDirectory().toAbsolutePath().normalize();
    FactoryBenchmarkWorld.require(
        root.getParent().getFileName().toString().equals("run-benchmark")
            && root.getFileName().toString().equals(fixture)
            && Files.readString(root.resolve("train-benchmark.marker"))
                .equals("FactoryCraft train workload v1\n"),
        "Unmarked train world");
    active = new Session(e.getServer(), root);
    try {
      active.start();
    } catch (Throwable x) {
      active.fail(x);
    }
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void pre(ServerTickEvent.Pre e) {
    if (active == null || active.done) return;
    var s = active;
    s.startNs = System.nanoTime();
    s.startAllocated = allocated();
    s.measured = s.phase;
    s.beforeSimulation = s.data.simulationNs;
    s.beforeReads = s.data.worldReads;
    try {
      s.step();
    } catch (Throwable x) {
      s.fail(x);
    }
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void post(ServerTickEvent.Post e) {
    if (active == null || active.done) return;
    var s = active;
    s.samples
        .computeIfAbsent(s.measured, k -> new TickSamples(4096))
        .add(System.nanoTime() - s.startNs);
    s.simulation
        .computeIfAbsent(s.measured, k -> new TickSamples(4096))
        .add(s.data.simulationNs - s.beforeSimulation);
    s.allocations.merge(s.measured, allocated() - s.startAllocated, Long::sum);
    s.reads.merge(s.measured, s.data.worldReads - s.beforeReads, Long::sum);
    try {
      if (s.phase.equals("finish")) {
        s.audit();
        s.finish();
      }
    } catch (Throwable x) {
      s.fail(x);
    }
  }

  @SubscribeEvent
  public static void stopped(ServerStoppedEvent e) {
    if (active != null && active.recording != null) active.recording.close();
    active = null;
  }

  private static long allocated() {
    return THREAD.getThreadAllocatedBytes(Thread.currentThread().threadId());
  }

  private static final class Session {
    final MinecraftServer server;
    final ServerLevel level;
    final Path root;
    final TrainData data;
    final RailwayData rails;
    final boolean verify;
    final int[] sizes = {1, 3, 8, 16, 32, 64, 8, 16};
    final List<List<UUID>> lines = new ArrayList<>();
    final List<Set<Long>> tickets = new ArrayList<>();
    final ArrayList<TrainData.Train> trains = new ArrayList<>();
    final ArrayList<ServerPlayer> drivers = new ArrayList<>();
    final int[] intent = new int[8];
    final long[] reverseSamples = new long[8];
    final Map<String, TickSamples> samples = new LinkedHashMap<>(),
        simulation = new LinkedHashMap<>();
    final Map<String, Long> allocations = new LinkedHashMap<>(), reads = new LinkedHashMap<>();
    String phase = "build", measured;
    int tick, build;
    boolean done;
    long startNs, startAllocated, beforeSimulation, beforeReads, movingSamples;
    ServerPlayer builder;
    CompoundTag frozen;
    long releasedChunk;
    boolean reallyUnloaded;
    long initialFuel, initialItems, initialFluid;
    net.neoforged.neoforge.items.IItemHandler stale;
    BenchmarkRecording recording;
    Instant window;

    Session(MinecraftServer s, Path r) {
      server = s;
      level = s.overworld();
      root = r;
      data = TrainData.get(level);
      rails = RailwayData.get(level);
      verify = System.getProperty("factorycraft.trainBenchmark.mode").equals("verify");
    }

    void start() throws Exception {
      Files.createDirectories(root.resolve("reports"));
      if (verify) {
        var expected =
            NbtIo.readCompressed(root.resolve("checkpoint.nbt"), NbtAccounter.unlimitedHeap());
        FactoryBenchmarkWorld.require(
            expected
                .getCompound("Trains")
                .equals(data.save(new CompoundTag(), level.registryAccess())),
            "Independent JVM train owners/motion mismatch");
        FactoryBenchmarkWorld.require(
            expected
                .getCompound("Rails")
                .equals(rails.save(new CompoundTag(), level.registryAccess())),
            "Independent JVM rail claims mismatch");
        var r = new JsonObject();
        r.addProperty("status", "PASS");
        r.addProperty("independentJvmExactOwners", true);
        r.addProperty("trains", data.trains().size());
        r.addProperty("cars", data.trains().stream().mapToInt(t -> t.cars.size()).sum());
        Files.writeString(root.resolve("reports/verify.json"), JSON.toJson(r));
        done = true;
        server.halt(false);
        return;
      }
      FactoryBenchmarkWorld.require(
          data.trains().isEmpty() && rails.segments().isEmpty(), "Refusing train world reseed");
      level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
      level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
      builder =
          net.neoforged.neoforge.common.util.FakePlayerFactory.get(
              level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "TrainLoadQA"));
      builder.setGameMode(GameType.CREATIVE);
      for (int i = 0; i < 8; i++) {
        lines.add(new ArrayList<>());
        tickets.add(new HashSet<>());
        intent[i] = 1;
      }
      recording = new BenchmarkRecording(root.resolve("reports/server.jfr"));
      window = Instant.now();
    }

    void next(String s) {
      phase = s;
      tick = 0;
      System.out.println("TRAIN_PROFILE_PHASE " + s);
    }

    RailEnd end(int line, int at) {
      return new RailEnd(2048 + line * 128, 80, -2048 - at, new RailHeading(0));
    }

    void build() {
      int line = build / 6, segment = build % 6;
      var plan =
          new RailPlan(RailCurve.plan(end(line, segment * 128), end(line, (segment + 1) * 128)));
      for (long key : plan.footprint.chunks())
        for (int dx = -1; dx <= 1; dx++)
          for (int dz = -1; dz <= 1; dz++) {
            long c = ChunkPos.asLong(ChunkPos.getX(key) + dx, ChunkPos.getZ(key) + dz);
            if (tickets.get(line).add(c)) {
              level.getChunk(ChunkPos.getX(c), ChunkPos.getZ(c));
              level.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), true);
            }
          }
      for (var e : List.of(plan.curve.start, plan.curve.end))
        level.setBlockAndUpdate(
            new BlockPos(e.x(), e.y() - 1, e.z()), Blocks.STONE.defaultBlockState());
      builder.setPos(plan.curve.end.x() + .5, 82, plan.curve.end.z() + .5);
      FactoryBenchmarkWorld.require(
          RailPlacement.place(builder, plan).equals("ready"), "Native benchmark rail transaction");
      lines
          .get(line)
          .add(
              rails.at(new BlockPos(plan.curve.start.x(), 80, plan.curve.start.z())).stream()
                  .filter(id -> rails.segment(id).curve.end.equals(plan.curve.end))
                  .findFirst()
                  .orElseThrow());
      if (++build == 48) next("settle");
    }

    void seed() {
      for (int line = 0; line < 8; line++) {
        var legs = new ArrayList<TrainPath.Leg>();
        double at = 0;
        for (var edge : lines.get(line)) {
          double len = rails.segment(edge).curve.length();
          legs.add(new TrainPath.Leg(new RailGraph.Directed(edge, false), at, len));
          at += len;
        }
        var t =
            new TrainData.Train(
                UUID.randomUUID(),
                builder.getUUID(),
                "vanilla:train-load-qa",
                new TrainPath(legs),
                Math.max(96, sizes[line] * 7 + 20));
        for (int i = 0; i < sizes[line]; i++) {
          var kind =
              i % 8 == 0 || i == sizes[line] - 1 && i > 0
                  ? StockSpec.LOCOMOTIVE
                  : i % 3 == 0 ? StockSpec.FLUID : StockSpec.CARGO;
          var c = new TrainCar(UUID.randomUUID(), kind, data::setDirty);
          if (kind == StockSpec.LOCOMOTIVE) {
            c.items.setStackInSlot(0, new ItemStack(Items.COAL, 8));
            initialFuel += 32000000L;
            c.reversed = i == sizes[line] - 1 && i > 0;
          } else if (kind == StockSpec.CARGO) {
            var item = new ItemStack(Items.IRON_INGOT, 31);
            item.set(DataComponents.CUSTOM_NAME, Component.literal("finite train load cargo"));
            c.items.setStackInSlot(0, item);
            initialItems += 31;
          } else {
            c.fluid.fill(
                new factorycraft.fluids.FluidKey(
                    new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1)),
                123457,
                false);
            initialFluid += 123457;
          }
          t.cars.add(c);
        }
        data.add(t);
        trains.add(t);
        TrainWorld.views(data, t);
      }
    }

    boolean ready() {
      return trains.stream()
          .allMatch(
              t ->
                  TrainWorld.available(data, t)
                      && t.cars.stream()
                          .allMatch(c -> level.getEntity(c.id) instanceof StockEntity));
    }

    ServerPlayer driver(StockEntity view) {
      var p =
          new ServerPlayer(
              server, level, builder.getGameProfile(), ClientInformation.createDefault());
      p.connection =
          new net.minecraft.server.network.ServerGamePacketListenerImpl(
              server,
              new net.minecraft.network.Connection(
                  net.minecraft.network.protocol.PacketFlow.SERVERBOUND),
              p,
              net.minecraft.server.network.CommonListenerCookie.createInitial(
                  p.getGameProfile(), false)) {
            public void send(net.minecraft.network.protocol.Packet<?> packet) {}

            public void teleport(double x, double y, double z, float yaw, float pitch) {}
          };
      FactoryBenchmarkWorld.require(p.startRiding(view), "Actual mounted test driver");
      return p;
    }

    void drive(boolean release) {
      for (int i = 0; i < trains.size(); i++) {
        if (release && i == 3) continue;
        var t = trains.get(i);
        var view = level.getEntity(t.cars.getFirst().id);
        if (!(view instanceof StockEntity stock)) continue;
        if (drivers.size() <= i) drivers.add(driver(stock));
        var p = drivers.get(i);
        if (p.getVehicle() != stock)
          FactoryBenchmarkWorld.require(p.startRiding(stock), "Reload rider");
        double stop =
            TrainMotion.stoppingDistance(
                    t.speed,
                    t.cars.stream().mapToInt(c -> c.kind.weight).sum(),
                    t.cars.stream().mapToDouble(c -> c.kind.brake).sum())
                + 24;
        if (intent[i] > 0 && 768 - t.front() < stop) intent[i] = -1;
        else if (intent[i] < 0 && t.rear() < stop) intent[i] = 1;
        TrainNetwork.control(p, stock, intent[i], false, 0);
        if (t.speed > 0) {
          movingSamples++;
          if (t.direction < 0) reverseSamples[i]++;
        }
      }
    }

    CompoundTag snapshot(TrainData.Train t) {
      var n = new CompoundTag();
      n.putDouble("Head", t.head);
      n.putDouble("Speed", t.speed);
      n.putDouble("Distance", t.distance);
      var list = new ListTag();
      for (var c : t.cars) list.add(c.save(level.registryAccess()));
      n.put("Cars", list);
      return n;
    }

    void audit() {
      long items = 0, fluid = 0, fuel = 0;
      int cars = 0;
      for (var t : data.trains())
        for (var c : t.cars) {
          cars++;
          FactoryBenchmarkWorld.require(
              c.health == c.kind.health, "Unexpected benchmark impact/health loss");
          if (c.kind == StockSpec.CARGO) {
            var s = c.items.getStackInSlot(0);
            items += s.getCount();
            FactoryBenchmarkWorld.require(
                s.getHoverName().getString().equals("finite train load cargo"),
                "Lost cargo components");
          }
          if (c.kind == StockSpec.FLUID) fluid += c.fluid.amount();
          if (c.kind == StockSpec.LOCOMOTIVE)
            fuel += c.fuelJ + c.spentJ + 4000000L * c.items.getStackInSlot(0).getCount();
        }
      FactoryBenchmarkWorld.require(
          cars == Arrays.stream(sizes).sum()
              && data.trains().size() == 8
              && items == initialItems
              && fluid == initialFluid
              && fuel == initialFuel,
          "Exact finite train resource ledger");
    }

    void step() throws Exception {
      tick++;
      switch (phase) {
        case "build" -> build();
        case "settle" -> {
          if (trains.isEmpty()
              && lines.stream().flatMap(Collection::stream).allMatch(rails::available)) seed();
          if (!trains.isEmpty() && ready()) {
            audit();
            next("warmup");
          }
          if (tick > 300) throw new IllegalStateException("Native stock chunks not ready");
        }
        case "warmup" -> {
          drive(false);
          if (tick >= 120) {
            audit();
            next("stable");
          }
        }
        case "stable" -> {
          drive(false);
          audit();
          if (tick >= 400) {
            for (int i = 0; i < 8; i++) intent[i] = -intent[i];
            next("reversals");
          }
        }
        case "reversals" -> {
          drive(false);
          audit();
          if (tick >= 180) {
            var t = trains.get(3);
            drivers.get(3).stopRiding();
            frozen = snapshot(t);
            stale =
                ((StockEntity) level.getEntity(t.cars.getFirst().id))
                    .getCapability(Capabilities.ItemHandler.ENTITY);
            releasedChunk =
                ChunkPos.asLong(BlockPos.containing(TrainWorld.vec(t.cars.getFirst().recovery)));
            data.unloadChunk(releasedChunk);
            for (long c : tickets.get(3))
              level.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), false);
            next("unload");
          }
        }
        case "unload" -> {
          drive(true);
          audit();
          if (level
                  .getChunkSource()
                  .getChunkNow(ChunkPos.getX(releasedChunk), ChunkPos.getZ(releasedChunk))
              == null) {
            reallyUnloaded = true;
            FactoryBenchmarkWorld.require(
                frozen.equals(snapshot(trains.get(3))), "Unloaded train changed paid state");
            FactoryBenchmarkWorld.require(
                stale.extractItem(0, 1, false).isEmpty(), "Stale unloaded fuel capability");
            next("reload");
          }
          if (tick > 450) throw new IllegalStateException("Train chunk did not actually unload");
        }
        case "reload" -> {
          drive(true);
          if (tick == 1)
            for (long c : tickets.get(3)) {
              level.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), true);
              level.getChunk(ChunkPos.getX(c), ChunkPos.getZ(c));
            }
          if (tick > 15 && ready()) {
            audit();
            next("finish");
          }
          if (tick > 300) throw new IllegalStateException("Reload readiness timeout");
        }
      }
    }

    void finish() throws Exception {
      FactoryBenchmarkWorld.require(
          reallyUnloaded && movingSamples > 2000, "Workload did not exercise motion/unload");
      for (long count : reverseSamples)
        FactoryBenchmarkWorld.require(count > 0, "A consist did not actually reverse");
      for (var p : drivers) p.stopRiding();
      // Freeze exactly at the measured paid pose, including nonzero speed, before the process
      // exits.
      var checkpoint = new CompoundTag();
      checkpoint.put("Trains", data.save(new CompoundTag(), level.registryAccess()));
      checkpoint.put("Rails", rails.save(new CompoundTag(), level.registryAccess()));
      NbtIo.writeCompressed(checkpoint, root.resolve("checkpoint.nbt"));
      var r = new JsonObject();
      r.addProperty("status", "PASS");
      r.addProperty("trains", 8);
      r.addProperty("cars", Arrays.stream(sizes).sum());
      r.addProperty("railSegments", 48);
      r.addProperty("railLength", 6144);
      r.addProperty("movingSamples", movingSamples);
      r.add("reverseSamplesByConsist", JSON.toJsonTree(reverseSamples));
      r.addProperty("initialFuelJ", initialFuel);
      r.addProperty("items", initialItems);
      r.addProperty("fluidMB", initialFluid);
      r.addProperty("actualChunkUnload", reallyUnloaded);
      r.addProperty("onlineClients", server.getPlayerCount());
      r.addProperty("projectionUpdateRequests", data.snapshots);
      r.addProperty("worldReads", data.worldReads);
      int entities = 0;
      for (var t : trains)
        for (var c : t.cars) if (level.getEntity(c.id) instanceof StockEntity) entities++;
      r.addProperty("stockEntities", entities);
      var stages = new JsonObject();
      for (var e : samples.entrySet()) {
        var o = JSON.toJsonTree(e.getValue().summarize()).getAsJsonObject();
        o.add("trainAdapter", JSON.toJsonTree(simulation.get(e.getKey()).summarize()));
        o.addProperty("mainThreadAllocatedBytes", allocations.get(e.getKey()));
        o.addProperty("worldReads", reads.get(e.getKey()));
        stages.add(e.getKey(), o);
      }
      r.add("stages", stages);
      server.saveEverything(true, true, true);
      r.add("packets", JSON.toJsonTree(recording.finish(window, Instant.now())));
      Files.writeString(root.resolve("reports/measure.json"), JSON.toJson(r));
      done = true;
      System.out.println("FACTORYCRAFT_TRAIN_PROFILE PASS " + root.getFileName());
      server.halt(false);
    }

    void fail(Throwable x) {
      x.printStackTrace();
      try {
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

  private TrainServerBenchmark() {}
}
