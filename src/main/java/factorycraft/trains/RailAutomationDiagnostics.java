package factorycraft.trains;

import factorycraft.FactoryCraft;
import factorycraft.rail.*;
import factorycraft.rail.automation.*;
import factorycraft.simulation.rail.*;
import factorycraft.simulation.trains.StockSpec;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Explicit marked-world finite fixture. No runtime chunk tickets or ordinary-save generation. */
@EventBusSubscriber(modid = FactoryCraft.ID)
public final class RailAutomationDiagnostics {
  public static final class Marker extends SavedData {
    int stage;

    static Marker load(CompoundTag n, HolderLookup.Provider r) {
      var d = new Marker();
      d.stage = n.getInt("Stage");
      return d;
    }

    public CompoundTag save(CompoundTag n, HolderLookup.Provider r) {
      n.putInt("Stage", stage);
      return n;
    }
  }

  @SubscribeEvent
  public static void register(net.neoforged.neoforge.event.RegisterCommandsEvent e) {
    e.getDispatcher()
        .register(
            Commands.literal("factorycraft")
                .then(
                    Commands.literal("railautomation")
                        .requires(s -> s.hasPermission(2))
                        .executes(
                            c -> {
                              var d = RailAutomationData.get(c.getSource().getLevel());
                              c.getSource()
                                  .sendSuccess(
                                      () ->
                                          Component.literal(
                                              "Devices="
                                                  + d.devices.size()
                                                  + " topologyReady="
                                                  + d.runtimeReady()
                                                  + " points="
                                                  + d.points.size()
                                                  + " pathNodes="
                                                  + d.pathNodes
                                                  + " pathNs="
                                                  + d.pathNanos
                                                  + " topologyNs="
                                                  + d.topologyNanos
                                                  + " aspects="
                                                  + d.aspectUpdates),
                                      false);
                              return 1;
                            }))
                .then(
                    Commands.literal("railautoqa")
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
        || !root.getFileName().toString().startsWith("network-v0320")
        || !java.nio.file.Files.isRegularFile(root.resolve("factorycraft-network.marker"))
        || !p.isCreative()) return 0;
    var marker =
        l.getDataStorage()
            .computeIfAbsent(
                new SavedData.Factory<>(Marker::new, Marker::load),
                "factorycraft_v0320_auto_scene");
    if (marker.stage >= 2) {
      source.sendFailure(Component.literal("Scene already claimed; never reset/refill."));
      return 0;
    }
    var previous = p.getMainHandItem().copy();
    var pos = p.position();
    float yaw = p.getYRot(), pitch = p.getXRot();
    try {
      if (marker.stage == 0) {
        marker.stage = 1;
        marker.setDirty();
        for (int x : new int[] {0, 24}) {
          var plan =
              new RailPlan(
                  RailCurve.plan(
                      new RailEnd(x, 70, 32, new RailHeading(0)),
                      new RailEnd(x, 70, -96, new RailHeading(0))));
          for (long key : plan.footprint.chunks())
            for (int dx = -1; dx <= 1; dx++)
              for (int dz = -1; dz <= 1; dz++) {
                int cx = (int) key + dx, cz = (int) (key >> 32) + dz;
                l.getChunk(cx, cz);
                // Vanilla setChunkForced(false-change) clears SavedData.dirty; skip duplicate
                // requests.
                if (!l.getForcedChunks()
                    .contains(net.minecraft.world.level.ChunkPos.asLong(cx, cz)))
                  l.setChunkForced(cx, cz, true);
              }
          for (var end : List.of(plan.curve.start, plan.curve.end))
            l.setBlockAndUpdate(
                new BlockPos(end.x(), 69, end.z()), Blocks.STONE.defaultBlockState());
          p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailContent.RAIL.get(), 64));
          String result = RailPlacement.place(p, plan);
          if (!result.equals("ready")) throw new IllegalStateException(result);
        }
        source.sendSuccess(
            () ->
                Component.literal(
                    "Two native railways created. Wait for entity chunks, then repeat railautoqa"
                        + " once for finite stock/devices."),
            false);
        return 1;
      }
      marker.stage = 2;
      marker.setDirty();
      for (int x : new int[] {0, 24}) {
        var front = stock(p, x, 2, StockSpec.LOCOMOTIVE, false);
        stock(p, x, 9, StockSpec.CARGO, false);
        stock(p, x, 16, StockSpec.LOCOMOTIVE, true);
        var one = side(p, x + 3, 70, -79, TracksideKind.STOP, "Unload");
        var two = side(p, x - 4, 70, 23, TracksideKind.STOP, "Load");
        side(p, x + 3, 70, -8, TracksideKind.CHAIN, "Entry");
        side(p, x - 4, 70, -8, TracksideKind.SIGNAL, "Opposite exit");
        side(p, x + 3, 70, -40, TracksideKind.SIGNAL, "Exit");
        side(p, x - 4, 70, -40, TracksideKind.CHAIN, "Opposite entry");
        var train = TrainData.get(l).owner(front.getUUID());
        train.automation.schedule =
            new RailSchedule(
                List.of(
                    new RailSchedule.Entry("Unload", List.of(wait5())),
                    new RailSchedule.Entry("Load", List.of(wait5()))),
                List.of());
        // Player explicitly starts via the same real GUI used in survival, rather than a
        // diagnostic-only drive path.
        TrainData.get(l).setDirty();
      }
      source.sendSuccess(
          () ->
              Component.literal(
                  "Two manual trains / six cars. Finite32coal per locomotive,31iron per cargo."
                      + " Named two-way stops/signals. Open locomotive Schedule and switch"
                      + " Automatic. No refill."),
          false);
    } finally {
      p.setItemInHand(InteractionHand.MAIN_HAND, previous);
      p.connection.teleport(pos.x, pos.y, pos.z, yaw, pitch);
    }
    return 1;
  }

  private static RailWait.Condition wait5() {
    return new RailWait.Condition(RailWait.Type.TIME, "", RailWait.Comparison.GE, 100, false);
  }

  private static StockEntity stock(ServerPlayer p, int x, int z, StockSpec kind, boolean reverse) {
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
                new BlockHitResult(
                    new Vec3(x + .5, 70.3, z + .5), Direction.UP, new BlockPos(x, 70, z), false)),
            kind);
    if (!result.equals("placed")) throw new IllegalStateException("Native stock: " + result);
    var view =
        p.serverLevel()
            .getEntitiesOfClass(
                StockEntity.class,
                new AABB(new BlockPos(x, 70, z)).inflate(2),
                e -> e.kind() == kind)
            .getFirst();
    var car = TrainData.get(p.serverLevel()).car(view.getUUID());
    car.items.setStackInSlot(
        0,
        new ItemStack(
            kind == StockSpec.LOCOMOTIVE ? Items.COAL : Items.IRON_INGOT,
            kind == StockSpec.LOCOMOTIVE ? 32 : 31));
    return view;
  }

  private static RailAutomationData.Device side(
      ServerPlayer p, int x, int y, int z, TracksideKind kind, String name) {
    var l = p.serverLevel();
    var at = new BlockPos(x, y, z);
    for (int a = 0; a < kind.width; a++)
      for (int b = 0; b < kind.width; b++)
        l.setBlockAndUpdate(at.offset(a, -1, b), Blocks.STONE.defaultBlockState());
    p.setPos(x + .5, y, z + 2.5);
    p.setItemInHand(
        InteractionHand.MAIN_HAND, new ItemStack(TracksideContent.ITEMS.get(kind).get()));
    var plan = TracksidePlacement.plan(l, at, kind, 180);
    String result = TracksidePlacement.place(p, InteractionHand.MAIN_HAND, plan);
    if (!result.equals("ready")) throw new IllegalStateException("Native trackside: " + result);
    var d = RailAutomationData.get(l).at(at);
    var n = d.settings();
    n.putString("Name", name);
    n.putInt("Limit", 1);
    RailAutomationData.get(l).configure(d, n);
    return d;
  }

  private RailAutomationDiagnostics() {}
}
