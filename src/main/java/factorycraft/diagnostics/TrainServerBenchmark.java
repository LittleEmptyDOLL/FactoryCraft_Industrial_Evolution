package factorycraft.diagnostics;

import com.google.gson.*;
import factorycraft.FactoryCraft;
import factorycraft.rail.*;
import factorycraft.rail.automation.*;
import factorycraft.research.ResearchTeams;
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
    final boolean verify, automatic;
    final int[] sizes = {1, 3, 8, 16, 32, 64, 8, 16};
    final long[] movingByConsist = new long[8], visits = new long[8];
    final boolean[] wasArrived = new boolean[8];
    final double[] lastDistance = new double[8],
        forwardDistance = new double[8],
        reverseDistance = new double[8];
    boolean devicesSeeded;
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
      automatic = Boolean.getBoolean("factorycraft.trainBenchmark.automatic");
      if (automatic) sizes[0] = 3; // a real outward locomotive at each end, no fake driver
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
        FactoryBenchmarkWorld.require(
            expected.getBoolean("Automatic") == automatic, "Checkpoint control profile mismatch");
        if (automatic) {
          var system = RailAutomationData.get(level);
          FactoryBenchmarkWorld.require(
              expected
                  .getCompound("Automation")
                  .equals(system.save(new CompoundTag(), level.registryAccess())),
              "Independent JVM trackside identity/settings mismatch");
          for (var t : data.trains())
            FactoryBenchmarkWorld.require(
                Objects.equals(system.slots.destination(t.id), t.automation.destination),
                "Independent JVM destination commitment mismatch");
        }
        var r = new JsonObject();
        r.addProperty("status", "PASS");
        r.addProperty("automatic", automatic);
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
                automatic ? ResearchTeams.resolve(builder).orElseThrow() : "vanilla:train-load-qa",
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
            c.reversed = i == sizes[line] - 1 && i > 0 || automatic && i % 8 == 0 && i / 8 % 2 == 1;
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
      return (!automatic || devicesSeeded && RailAutomationData.get(level).runtimeReady())
          && trains.stream()
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
      if (automatic) {
        observeAutomatic(release);
        return;
      }
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

    RailAutomationData.Device device(
        int line, int at, boolean reverse, TracksideKind kind, String name) {
      int x = 2048 + line * 128;
      var anchor = new BlockPos(x + (reverse ? -4 : 3), 80, -2048 - at);
      for (int dx = 0; dx < kind.width; dx++)
        for (int dz = 0; dz < kind.width; dz++)
          level.setBlockAndUpdate(anchor.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
      builder.setPos(anchor.getX() + .5, 80, anchor.getZ() + 2.5);
      builder.setItemInHand(
          net.minecraft.world.InteractionHand.MAIN_HAND,
          new ItemStack(TracksideContent.ITEMS.get(kind).get()));
      var plan = TracksidePlacement.plan(level, anchor, kind, reverse ? 0 : 180);
      FactoryBenchmarkWorld.require(
          plan.edge() != null && plan.reverse() == reverse,
          "Native auto-profile directed attachment");
      FactoryBenchmarkWorld.require(
          TracksidePlacement.place(builder, net.minecraft.world.InteractionHand.MAIN_HAND, plan)
              .equals("ready"),
          "Native auto-profile device placement");
      var system = RailAutomationData.get(level);
      var device = system.at(anchor);
      var config = device.settings();
      config.putString("Name", name);
      config.putInt("Limit", 1);
      system.configure(device, config);
      return device;
    }

    void seedDevices() {
      for (int i = 0; i < 8; i++) {
        device(i, 744, false, TracksideKind.STOP, "Profile north " + i);
        device(i, 24, true, TracksideKind.STOP, "Profile south " + i);
        if (sizes[i] <= 16) {
          for (int at : new int[] {192, 256, 448, 512}) {
            boolean entry = at == 192 || at == 448;
            device(
                i, at, false, entry ? TracksideKind.CHAIN : TracksideKind.SIGNAL, "Forward " + at);
            device(
                i, at, true, entry ? TracksideKind.SIGNAL : TracksideKind.CHAIN, "Reverse " + at);
          }
        } else
          for (int at : new int[] {256, 512})
            for (boolean reverse : new boolean[] {false, true})
              device(i, at, reverse, TracksideKind.SIGNAL, "Long block " + at);
        var wait = new RailWait.Condition(RailWait.Type.TIME, "", RailWait.Comparison.GE, 2, false);
        trains.get(i).automation.schedule =
            new RailSchedule(
                List.of(
                    new RailSchedule.Entry("Profile north " + i, List.of(wait)),
                    new RailSchedule.Entry("Profile south " + i, List.of(wait))),
                List.of());
        lastDistance[i] = trains.get(i).distance;
      }
      data.setDirty();
      devicesSeeded = true;
    }

    void observeAutomatic(boolean release) {
      for (int i = 0; i < 8; i++) {
        var t = trains.get(i);
        FactoryBenchmarkWorld.require(
            t.automation.automatic
                && t.driver == null
                && t.cars.stream()
                    .noneMatch(
                        c ->
                            level.getEntity(c.id) instanceof StockEntity view
                                && !view.getPassengers().isEmpty()),
            "Automatic profile has a driver or lost its mode");
        if (t.automation.arrived && !wasArrived[i]) visits[i]++;
        wasArrived[i] = t.automation.arrived;
        double delta = t.distance - lastDistance[i];
        lastDistance[i] = t.distance;
        if (delta > 0) forwardDistance[i] += delta;
        else reverseDistance[i] -= delta;
        if (t.speed > 0) {
          movingSamples++;
          movingByConsist[i]++;
          if (t.direction < 0) reverseSamples[i]++;
        }
      }
    }

    boolean allServiced() {
      for (int i = 0; i < 8; i++)
        if (visits[i] < 2 || forwardDistance[i] < 16 || reverseDistance[i] < 16) return false;
      return true;
    }

    CompoundTag snapshot(TrainData.Train t) {
      var n = new CompoundTag();
      n.putDouble("Head", t.head);
      n.putDouble("Speed", t.speed);
      n.putDouble("Distance", t.distance);
      if (automatic) n.put("Automation", t.automation.save());
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
          if (automatic && !trains.isEmpty() && !devicesSeeded) seedDevices();
          if (!trains.isEmpty() && ready()) {
            if (automatic) {
              for (var t : trains)
                FactoryBenchmarkWorld.require(
                    TrainAutomation.automatic(data, t), "Automatic profile startup");
            }
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
          if (automatic ? allServiced() : tick >= 180) {
            var t = trains.get(3);
            if (!automatic) drivers.get(3).stopRiding();
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
          if (automatic && tick > 2400)
            throw new IllegalStateException(
                "Automatic complete outbound/return service timeout " + Arrays.toString(visits));
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
      if (automatic) {
        checkpoint.putBoolean("Automatic", true);
        checkpoint.put(
            "Automation",
            RailAutomationData.get(level).save(new CompoundTag(), level.registryAccess()));
        FactoryBenchmarkWorld.require(
            drivers.isEmpty(), "Automatic workload used a mounted driver");
        for (int i = 0; i < 8; i++)
          FactoryBenchmarkWorld.require(
              visits[i] >= 2
                  && forwardDistance[i] > 16
                  && reverseDistance[i] > 16
                  && movingByConsist[i] > 20,
              "Incomplete real two-ended automatic service");
      }
      checkpoint.put("Trains", data.save(new CompoundTag(), level.registryAccess()));
      checkpoint.put("Rails", rails.save(new CompoundTag(), level.registryAccess()));
      NbtIo.writeCompressed(checkpoint, root.resolve("checkpoint.nbt"));
      var r = new JsonObject();
      r.addProperty("status", "PASS");
      r.addProperty("trains", 8);
      r.addProperty("automatic", automatic);
      r.add("sizes", JSON.toJsonTree(sizes));
      if (automatic) {
        r.add("movingSamplesByConsist", JSON.toJsonTree(movingByConsist));
        r.add("completedStopVisits", JSON.toJsonTree(visits));
        r.add("forwardMeters", JSON.toJsonTree(forwardDistance));
        r.add("reverseMeters", JSON.toJsonTree(reverseDistance));
        var system = RailAutomationData.get(level);
        r.addProperty("tracksideDevices", system.devices.size());
        r.addProperty("topologyNanos", system.topologyNanos);
        r.addProperty("routeNodes", system.pathNodes);
        r.addProperty("routeNanos", system.pathNanos);
        r.addProperty("aspectUpdates", system.aspectUpdates);
      }
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
