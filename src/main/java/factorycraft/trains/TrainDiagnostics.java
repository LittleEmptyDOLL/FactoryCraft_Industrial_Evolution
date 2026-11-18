package factorycraft.trains;

import factorycraft.FactoryCraft;
import factorycraft.rail.*;
import factorycraft.simulation.rail.*;
import factorycraft.simulation.trains.*;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Explicit, marked-world QA only. Never refills or rebuilds an existing diagnostic scene. */
@EventBusSubscriber(modid = FactoryCraft.ID)
public final class TrainDiagnostics {
  public static final class Marker extends SavedData {
    boolean built;

    static Marker load(CompoundTag n, HolderLookup.Provider r) {
      var m = new Marker();
      m.built = n.getBoolean("Built");
      return m;
    }

    public CompoundTag save(CompoundTag n, HolderLookup.Provider r) {
      n.putBoolean("Built", built);
      return n;
    }
  }

  @SubscribeEvent
  public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent e) {
    e.getDispatcher()
        .register(
            Commands.literal("factorycraft")
                .then(
                    Commands.literal("trains")
                        .requires(s -> s.hasPermission(2))
                        .executes(
                            c -> {
                              var d = TrainData.get(c.getSource().getLevel());
                              c.getSource()
                                  .sendSuccess(
                                      () ->
                                          Component.literal(
                                              "Trains="
                                                  + d.trains().size()
                                                  + " cars="
                                                  + d.trains().stream()
                                                      .mapToInt(t -> t.cars.size())
                                                      .sum()
                                                  + " active="
                                                  + d.active.size()
                                                  + " steps="
                                                  + d.ticks
                                                  + " worldReads="
                                                  + d.worldReads
                                                  + " projectionUpdates="
                                                  + d.snapshots
                                                  + " simulationNs="
                                                  + d.simulationNs
                                                  + " maxTickNs="
                                                  + d.maxTickNs),
                                      false);
                              return 1;
                            }))
                .then(
                    Commands.literal("trainstockqa")
                        .requires(s -> s.hasPermission(4))
                        .executes(c -> stocks(c.getSource())))
                .then(
                    Commands.literal("trainqa")
                        .requires(s -> s.hasPermission(4))
                        .executes(c -> build(c.getSource()))));
  }

  private static int build(CommandSourceStack source)
      throws com.mojang.brigadier.exceptions.CommandSyntaxException {
    var l = source.getLevel();
    var p = source.getPlayerOrException();
    var root =
        l.getServer()
            .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .toAbsolutePath()
            .normalize()
            .getParent()
            .getParent();
    if (root == null
        || !java.nio.file.Files.isRegularFile(root.resolve("factorycraft-network.marker"))
        || !p.isCreative()) {
      source.sendFailure(
          Component.literal(
              "Requires creative OP in the explicitly marked isolated network QA world."));
      return 0;
    }
    var marker =
        l.getDataStorage()
            .computeIfAbsent(
                new SavedData.Factory<>(Marker::new, Marker::load), "factorycraft_v0310_scene");
    if (marker.built) {
      source.sendFailure(
          Component.literal(
              "Scene already claimed: refusing reset/refill. Existing stock/resources remain"
                  + " untouched."));
      return 0;
    }
    // Reserve the marker before doing anything: failed geometry is retained as evidence.
    marker.built = true;
    marker.setDirty();
    var plans = new ArrayList<RailPlan>();
    for (int x : new int[] {0, 12, 24, 36})
      plans.add(
          new RailPlan(
              RailCurve.plan(
                  new RailEnd(x, 70, 48, new RailHeading(0)),
                  new RailEnd(x, 70, -48, new RailHeading(0)))));
    plans.add(
        new RailPlan(
            RailCurve.plan(
                new RailEnd(60, 70, 48, new RailHeading(0)),
                new RailEnd(60, 70, -16, new RailHeading(0)))));
    plans.add(
        new RailPlan(
            RailCurve.plan(
                new RailEnd(60, 70, -16, new RailHeading(0)),
                new RailEnd(84, 70, -40, new RailHeading(4)))));
    plans.add(
        new RailPlan(
            RailCurve.plan(
                new RailEnd(84, 70, -40, new RailHeading(4)),
                new RailEnd(148, 78, -40, new RailHeading(4)))));
    var previous = p.getMainHandItem().copy();
    var position = p.position();
    float yaw = p.getYRot(), pitch = p.getXRot();
    try {
      var foundations = new HashSet<BlockPos>();
      for (var plan : plans) {
        // Admin fixture generation/tickets only; production trains never request chunk loads.
        for (long key : plan.footprint.chunks()) {
          l.getChunk((int) key, (int) (key >> 32));
          l.setChunkForced((int) key, (int) (key >> 32), true);
        }
        for (var end : List.of(plan.curve.start, plan.curve.end)) {
          var at = new BlockPos(end.x(), end.y() - 1, end.z());
          if (!l.getBlockState(at).isAir() && !foundations.contains(at))
            throw new IllegalStateException("QA foundation occupied " + at);
          foundations.add(at);
          l.setBlockAndUpdate(at, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        }
        p.setPos(plan.curve.end.x() + .5, plan.curve.end.y() + 2, plan.curve.end.z() + .5);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailContent.RAIL.get(), 64));
        String result = RailPlacement.place(p, plan);
        if (!result.equals("ready"))
          throw new IllegalStateException("QA native rail transaction: " + result);
      }
      source.sendSuccess(
          () ->
              Component.literal(
                  "Seven real paid-claim rail segments created; stock placement deferred until"
                      + " native entity chunks are ready. Run /factorycraft trainstockqa once."),
          false);
    } finally {
      p.setItemInHand(InteractionHand.MAIN_HAND, previous);
      p.connection.teleport(position.x, position.y, position.z, yaw, pitch);
    }
    return 1;
  }

  public static int stocks(CommandSourceStack source)
      throws com.mojang.brigadier.exceptions.CommandSyntaxException {
    var l = source.getLevel();
    var p = source.getPlayerOrException();
    var root =
        l.getServer()
            .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .toAbsolutePath()
            .normalize()
            .getParent()
            .getParent();
    if (root == null
        || !java.nio.file.Files.isRegularFile(root.resolve("factorycraft-network.marker"))
        || !p.isCreative()) return 0;
    var marker =
        l.getDataStorage()
            .computeIfAbsent(
                new SavedData.Factory<>(Marker::new, Marker::load),
                "factorycraft_v0310_stockscene");
    if (marker.built) {
      source.sendFailure(Component.literal("Stock scene already claimed; no reset/refill."));
      return 0;
    }
    marker.built = true;
    marker.setDirty();
    var previous = p.getMainHandItem().copy();
    var position = p.position();
    float yaw = p.getYRot(), pitch = p.getXRot();
    try {
      for (int x : new int[] {0, 12, 24, 36, 60}) {
        put(p, x, 20, StockSpec.LOCOMOTIVE, false);
        if (x == 12) {
          put(p, x, 13, StockSpec.CARGO, false);
          put(p, x, 6, StockSpec.CARGO, false);
        }
        if (x == 24) put(p, x, 13, StockSpec.FLUID, false);
        if (x == 36) {
          put(p, x, 13, StockSpec.CARGO, false);
          put(p, x, 6, StockSpec.LOCOMOTIVE, true);
        }
      }
      source.sendSuccess(
          () ->
              Component.literal(
                  "Five canonical trains / ten cars. Declared finite QA inputs:8 coal/loco,31 named"
                      + " iron/cargo,123457mB water/tanker. No refill on repeated command."),
          false);
    } finally {
      p.setItemInHand(InteractionHand.MAIN_HAND, previous);
      p.connection.teleport(position.x, position.y, position.z, yaw, pitch);
    }
    return 1;
  }

  private static void put(ServerPlayer p, int x, int z, StockSpec kind, boolean reverse) {
    var at = new BlockPos(x, 70, z);
    p.setPos(x + 3, 71, z + .5);
    p.setYRot(reverse ? 0 : 180);
    p.setXRot(0);
    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TrainContent.ITEMS.get(kind).get()));
    String result =
        StockPlacement.place(
            p,
            new UseOnContext(
                p,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(x + .5, 70.3, z + .5), Direction.UP, at, false)),
            kind);
    if (!result.equals("placed"))
      throw new IllegalStateException("QA native stock placement " + kind + ": " + result);
    var views =
        p.serverLevel()
            .getEntitiesOfClass(StockEntity.class, new AABB(at).inflate(2), e -> e.kind() == kind);
    if (views.isEmpty()) throw new IllegalStateException("Missing QA stock projection");
    var entity = views.getFirst();
    var car = TrainData.get(p.serverLevel()).car(entity.getUUID());
    if (kind == StockSpec.LOCOMOTIVE) car.items.setStackInSlot(0, new ItemStack(Items.COAL, 8));
    if (kind == StockSpec.CARGO) {
      var stack = new ItemStack(Items.IRON_INGOT, 31);
      stack.set(
          net.minecraft.core.component.DataComponents.CUSTOM_NAME,
          Component.literal("finite train QA iron"));
      car.items.setStackInSlot(0, stack);
    }
    if (kind == StockSpec.FLUID) {
      var key =
          new factorycraft.fluids.FluidKey(
              new net.neoforged.neoforge.fluids.FluidStack(
                  net.minecraft.world.level.material.Fluids.WATER, 1));
      car.fluid.fill(key, 123457, false);
    }
    car.changed();
  }

  private TrainDiagnostics() {}
}
