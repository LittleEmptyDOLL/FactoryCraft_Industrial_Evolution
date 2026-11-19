package factorycraft.trains;

import factorycraft.FactoryCraft;
import factorycraft.rail.*;
import factorycraft.simulation.rail.*;
import factorycraft.simulation.trains.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * One-shot, explicitly marked actual-client fixture. No reset/refill or production chunk tickets.
 */
@EventBusSubscriber(modid = FactoryCraft.ID)
public final class TrainHotfixDiagnostics {
  public static final class State extends SavedData {
    boolean built, seeded, curved, annexSeeded;
    final List<UUID> edges = new ArrayList<>(), cars = new ArrayList<>();

    static State load(CompoundTag n, HolderLookup.Provider r) {
      var s = new State();
      s.built = n.getBoolean("Built");
      s.seeded = n.getBoolean("Seeded");
      s.curved = n.getBoolean("Curved");
      s.annexSeeded = n.getBoolean("AnnexSeeded");
      for (var x : n.getList("Edges", Tag.TAG_INT_ARRAY)) s.edges.add(NbtUtils.loadUUID(x));
      for (var x : n.getList("Cars", Tag.TAG_INT_ARRAY)) s.cars.add(NbtUtils.loadUUID(x));
      return s;
    }

    public CompoundTag save(CompoundTag n, HolderLookup.Provider r) {
      n.putBoolean("Built", built);
      n.putBoolean("Seeded", seeded);
      n.putBoolean("Curved", curved);
      n.putBoolean("AnnexSeeded", annexSeeded);
      var e = new ListTag();
      for (var id : edges) e.add(NbtUtils.createUUID(id));
      n.put("Edges", e);
      var c = new ListTag();
      for (var id : cars) c.add(NbtUtils.createUUID(id));
      n.put("Cars", c);
      return n;
    }
  }

  static State state(ServerLevel l) {
    return l.getDataStorage()
        .computeIfAbsent(
            new SavedData.Factory<>(State::new, State::load), "factorycraft_v0311_scene");
  }

  static void require(boolean b, String why) {
    if (!b) throw new IllegalStateException(why);
  }

  @SubscribeEvent
  public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent e) {
    e.getDispatcher()
        .register(
            Commands.literal("factorycraft")
                .then(
                    Commands.literal("trainhotfix")
                        .requires(s -> s.hasPermission(4))
                        .then(Commands.literal("build").executes(c -> build(c.getSource())))
                        .then(Commands.literal("stock").executes(c -> stock(c.getSource())))
                        .then(Commands.literal("curves").executes(c -> curves(c.getSource())))
                        .then(
                            Commands.literal("ride")
                                .then(
                                    Commands.argument(
                                            "index",
                                            com.mojang.brigadier.arguments.IntegerArgumentType
                                                .integer(0, 3))
                                        .executes(
                                            c ->
                                                ride(
                                                    c.getSource(),
                                                    com.mojang.brigadier.arguments
                                                        .IntegerArgumentType.getInteger(
                                                        c, "index")))))
                        .then(Commands.literal("probe").executes(c -> probe(c.getSource())))));
  }

  static ServerPlayer player(CommandSourceStack source)
      throws com.mojang.brigadier.exceptions.CommandSyntaxException {
    var p = source.getPlayerOrException();
    require(
        marked(p.serverLevel()) && p.isCreative(), "Requires creative OP in marked network-v0311");
    return p;
  }

  private static boolean marked(ServerLevel level) {
    var parent = level.getServer().getServerDirectory().toAbsolutePath().normalize().getParent();
    return parent != null
        && parent.getFileName() != null
        && parent.getFileName().toString().equals("network-v0311")
        && Files.isRegularFile(parent.resolve("factorycraft-network.marker"));
  }

  static int build(CommandSourceStack src)
      throws com.mojang.brigadier.exceptions.CommandSyntaxException {
    var p = player(src);
    var l = p.serverLevel();
    var s = state(l);
    require(!s.built, "Scene already claimed; no reset");
    s.built = true;
    s.setDirty();
    var plans = new ArrayList<RailPlan>();
    for (int lane = 0; lane < 3; lane++)
      for (int part = 0; part < 8; part++)
        plans.add(
            new RailPlan(
                RailCurve.plan(
                    new RailEnd(lane * 16, 70, -part * 128, new RailHeading(0)),
                    new RailEnd(lane * 16, 70, -(part + 1) * 128, new RailHeading(0)))));
    for (var plan : plans) {
      for (long cp : plan.footprint.chunks())
        for (int dx = -1; dx <= 1; dx++)
          for (int dz = -1; dz <= 1; dz++) {
            int x = (int) cp + dx, z = (int) (cp >> 32) + dz;
            l.getChunk(x, z);
            if (!l.getForcedChunks().contains(net.minecraft.world.level.ChunkPos.asLong(x, z)))
              l.setChunkForced(x, z, true);
          }
      for (var end : List.of(plan.curve.start, plan.curve.end))
        l.setBlockAndUpdate(
            new BlockPos(end.x(), end.y() - 1, end.z()),
            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
      p.setPos(plan.curve.end.x() + .5, 73, plan.curve.end.z() + .5);
      require(RailPlacement.place(p, plan).equals("ready"), "QA rail placement failed");
      s.edges.add(
          RailwayData.get(l)
              .at(new BlockPos(plan.curve.start.x(), 70, plan.curve.start.z()))
              .stream()
              .filter(id -> RailwayData.get(l).segment(id).curve.end.equals(plan.curve.end))
              .findFirst()
              .orElseThrow());
    }
    p.connection.teleport(6, 72, -96, 180, 15);
    src.sendSuccess(
        () ->
            Component.literal(
                "24 real rail edges /3072 blocks. Diagnostic tickets only; run trainhotfix stock"
                    + " once."),
        false);
    return 1;
  }

  static int stock(CommandSourceStack src)
      throws com.mojang.brigadier.exceptions.CommandSyntaxException {
    var p = player(src);
    var l = p.serverLevel();
    var s = state(l);
    require(s.built && !s.seeded && s.edges.size() == 24, "Not a fresh completed scene");
    s.seeded = true;
    s.setDirty();
    var d = TrainData.get(l);
    var rails = RailwayData.get(l);
    int[] sizes = {1, 3, 8};
    for (int lane = 0; lane < 3; lane++) {
      var legs = new ArrayList<TrainPath.Leg>();
      double at = 0;
      for (int part = 0; part < 8; part++) {
        var id = s.edges.get(lane * 8 + part);
        double len = rails.segment(id).curve.length();
        legs.add(new TrainPath.Leg(new RailGraph.Directed(id, false), at, len));
        at += len;
      }
      var t =
          new TrainData.Train(
              UUID.randomUUID(),
              p.getUUID(),
              factorycraft.research.ResearchTeams.resolve(p).orElseThrow(),
              new TrainPath(legs),
              96);
      for (int i = 0; i < sizes[lane]; i++) {
        var k = i == 0 ? StockSpec.LOCOMOTIVE : i % 2 == 0 ? StockSpec.FLUID : StockSpec.CARGO;
        var c = new TrainCar(UUID.randomUUID(), k, d::setDirty);
        if (i == 0) c.items.setStackInSlot(0, new ItemStack(Items.COAL, 32));
        if (k == StockSpec.CARGO) c.items.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 31));
        if (k == StockSpec.FLUID)
          c.fluid.fill(
              new factorycraft.fluids.FluidKey(
                  new net.neoforged.neoforge.fluids.FluidStack(
                      net.minecraft.world.level.material.Fluids.WATER, 1)),
              123457,
              false);
        t.cars.add(c);
      }
      d.add(t);
      TrainWorld.views(d, t);
      s.cars.add(t.cars.getFirst().id);
    }
    src.sendSuccess(
        () ->
            Component.literal(
                "Declared finite QA inputs:3locos each32coal,5cargo each31iron,4tankers"
                    + " each123457mB. Three consists:1/3/8. No refill."),
        false);
    return 1;
  }

  static int curves(CommandSourceStack src)
      throws com.mojang.brigadier.exceptions.CommandSyntaxException {
    var p = player(src);
    var l = p.serverLevel();
    var s = state(l);
    require(
        s.seeded && !s.annexSeeded && s.cars.size() == 3,
        "Curved annex already claimed or base scene missing");
    s.curved = true;
    s.setDirty();
    var ends = new ArrayList<RailEnd>();
    for (int i = 0; i <= 6; i++) ends.add(new RailEnd(96, 70, -128 * i, new RailHeading(0)));
    ends.add(new RailEnd(120, 70, -792, new RailHeading(4)));
    ends.add(new RailEnd(248, 70, -792, new RailHeading(4)));
    ends.add(new RailEnd(312, 78, -768, new RailHeading(4)));
    ends.add(new RailEnd(440, 78, -768, new RailHeading(4)));
    ends.add(new RailEnd(568, 78, -768, new RailHeading(4)));
    var rails = RailwayData.get(l);
    var legs = new ArrayList<TrainPath.Leg>();
    double length = 0;
    for (int i = 1; i < ends.size(); i++) {
      var plan = new RailPlan(RailCurve.plan(ends.get(i - 1), ends.get(i)));
      for (long cp : plan.footprint.chunks())
        for (int dx = -1; dx <= 1; dx++)
          for (int dz = -1; dz <= 1; dz++) {
            int x = (int) cp + dx, z = (int) (cp >> 32) + dz;
            l.getChunk(x, z);
            if (!l.getForcedChunks().contains(net.minecraft.world.level.ChunkPos.asLong(x, z)))
              l.setChunkForced(x, z, true);
          }
      for (var end : List.of(plan.curve.start, plan.curve.end))
        l.setBlockAndUpdate(
            new BlockPos(end.x(), end.y() - 1, end.z()),
            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
      p.setPos(plan.curve.end.x() + .5, plan.curve.end.y() + 3, plan.curve.end.z() + .5);
      if (i > s.edges.size() - 24) {
        var status = RailPlacement.place(p, plan);
        require(status.equals("ready"), "Curved annex placement " + i + ": " + status);
      }
      var id =
          rails
              .at(new BlockPos(plan.curve.start.x(), plan.curve.start.y(), plan.curve.start.z()))
              .stream()
              .filter(k -> rails.segment(k).curve.end.equals(plan.curve.end))
              .findFirst()
              .orElseThrow();
      if (i > s.edges.size() - 24) {
        s.edges.add(id);
        s.setDirty();
      }
      legs.add(new TrainPath.Leg(new RailGraph.Directed(id, false), length, plan.curve.length()));
      length += plan.curve.length();
    }
    // A real empty-shape torch beside the line; a physically legal five-wide tunnel later on.
    var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
    for (int x = 340; x < 380; x++) {
      for (int y = 78; y < 84; y++)
        for (int z : new int[] {-771, -765}) l.setBlock(new BlockPos(x, y, z), stone, 18);
      for (int z = -771; z <= -765; z++) l.setBlock(new BlockPos(x, 83, z), stone, 18);
    }
    // Separate ground platform for actual starter/endpoint/camera/5x5 placement QA.
    for (int x = 80; x <= 145; x++)
      for (int z = 16; z <= 50; z++) l.setBlock(new BlockPos(x, 69, z), stone, 18);
    s.annexSeeded = true;
    s.setDirty(); // claim before allocating finite new stock, never refill on retry.
    var d = TrainData.get(l);
    var t =
        new TrainData.Train(
            UUID.randomUUID(),
            p.getUUID(),
            factorycraft.research.ResearchTeams.resolve(p).orElseThrow(),
            new TrainPath(legs),
            96);
    for (int i = 0; i < 3; i++) {
      var k = i == 0 ? StockSpec.LOCOMOTIVE : i == 1 ? StockSpec.CARGO : StockSpec.FLUID;
      var c = new TrainCar(UUID.randomUUID(), k, d::setDirty);
      if (i == 0) c.items.setStackInSlot(0, new ItemStack(Items.COAL, 32));
      if (i == 1) c.items.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 31));
      if (i == 2)
        c.fluid.fill(
            new factorycraft.fluids.FluidKey(
                new net.neoforged.neoforge.fluids.FluidStack(
                    net.minecraft.world.level.material.Fluids.WATER, 1)),
            123457,
            false);
      t.cars.add(c);
    }
    d.add(t);
    TrainWorld.views(d, t);
    s.cars.add(t.cars.getFirst().id);
    s.setDirty();
    src.sendSuccess(
        () ->
            Component.literal(
                "One-shot curved/grade/tunnel annex: 3 cars,32coal,31iron,123457mB; ride3. No old"
                    + " resources reset."),
        false);
    return 1;
  }

  static int ride(CommandSourceStack src, int index)
      throws com.mojang.brigadier.exceptions.CommandSyntaxException {
    var p = player(src);
    var s = state(p.serverLevel());
    require(index < s.cars.size(), "Missing stock");
    var v = p.serverLevel().getEntity(s.cars.get(index));
    require(v instanceof StockEntity, "Projection not ready");
    p.stopRiding();
    require(p.startRiding(v), "Ride refused");
    p.connection.teleport(p.getX(), p.getY(), p.getZ(), v.getYRot(), 0);
    return 1;
  }

  static int probe(CommandSourceStack src)
      throws com.mojang.brigadier.exceptions.CommandSyntaxException {
    var p = player(src);
    var d = TrainData.get(p.serverLevel());
    for (var id : state(p.serverLevel()).cars) {
      var t = d.owner(id);
      if (t != null) src.sendSuccess(() -> Component.literal(describe(t)), false);
    }
    return 1;
  }

  static String describe(TrainData.Train t) {
    return "TRAIN0311 id="
        + t.id
        + " sourceSpeed="
        + t.speed
        + " kmh="
        + t.speed * 216
        + " head="
        + t.head
        + " direction="
        + t.direction
        + " status="
        + t.status
        + " edges="
        + t.path.legs().stream().map(x -> x.edge().edge().toString()).toList();
  }

  @SubscribeEvent
  public static void trace(net.neoforged.neoforge.event.tick.LevelTickEvent.Post e) {
    if (e.getLevel() instanceof ServerLevel l && l.getGameTime() % 20 == 0 && marked(l))
      for (var t : TrainData.get(l).trains())
        if (t.speed > .05)
          System.out.println(describe(t) + " position=" + t.cars.getFirst().recovery);
  }

  private TrainHotfixDiagnostics() {}
}
