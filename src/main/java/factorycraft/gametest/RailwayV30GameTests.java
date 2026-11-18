package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.rail.*;
import factorycraft.research.*;
import factorycraft.simulation.rail.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class RailwayV30GameTests {
  static ResourceLocation id(String s) {
    return ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID, s);
  }

  static RailEnd end(BlockPos o, int x, int y, int z, int h) {
    return new RailEnd(o.getX() + x, o.getY() + y, o.getZ() + z, new RailHeading(h));
  }

  static RailPlan plan(BlockPos o, int x, int y, int z, int h) {
    return new RailPlan(RailCurve.plan(end(o, 0, 0, 0, 0), end(o, x, y, z, h)));
  }

  static void prepare(GameTestHelper h, RailPlan p) {
    for (long c : p.footprint.chunks()) h.getLevel().getChunk((int) c, (int) (c >> 32));
    for (var e : List.of(p.curve.start, p.curve.end))
      h.getLevel()
          .setBlockAndUpdate(
              new BlockPos(e.x(), e.y() - 1, e.z()), Blocks.STONE.defaultBlockState());
  }

  static net.neoforged.neoforge.common.util.FakePlayer player(
      GameTestHelper h, RailPlan p, boolean researched) {
    var player =
        net.neoforged.neoforge.common.util.FakePlayerFactory.get(
            h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "RailQA30"));
    player.setGameMode(GameType.SURVIVAL);
    player.setPos(p.curve.end.x() + .5, p.curve.end.y() + 2, p.curve.end.z() + .5);
    player.setYRot(180);
    player.setXRot(80);
    if (net.neoforged.fml.ModList.get().isLoaded("ftbteams"))
      try {
        var api =
            Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI").getMethod("api").invoke(null);
        var m =
            Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI$API")
                .getMethod("getManager")
                .invoke(api);
        m.getClass()
            .getMethod("playerLoggedIn", ServerPlayer.class, UUID.class, String.class)
            // FTB explicitly permits a null online player: create a real persisted
            // personal team without attempting to sync packets to FakePlayer's absent channel.
            .invoke(m, null, player.getUUID(), player.getGameProfile().getName());
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("Actual FTB QA player setup failed", e);
      }
    if (researched)
      ResearchTestSupport.grant(
          h.getLevel(), ResearchTeams.resolve(player).orElseThrow(), "railway");
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailContent.RAIL.get(), 64));
    return player;
  }

  static void ok(GameTestHelper h, String reason) {
    h.assertTrue(reason.equals("ready"), "rail transaction: " + reason);
  }

  static UUID placed(GameTestHelper h, RailPlan p, ServerPlayer player) {
    player.setPos(p.curve.end.x() + .5, p.curve.end.y() + 2, p.curve.end.z() + .5);
    ok(h, RailPlacement.place(player, p));
    var d = RailwayData.get(h.getLevel());
    return d.at(new BlockPos(p.curve.start.x(), p.curve.start.y(), p.curve.start.z())).stream()
        .filter(id -> d.segment(id).curve.end.equals(p.curve.end))
        .findFirst()
        .orElseThrow();
  }

  static void remove(GameTestHelper h, ServerPlayer player, UUID id) {
    var c = RailwayData.get(h.getLevel()).segment(id).curve.start;
    player.setPos(c.x() + .5, c.y() + 2, c.z() + .5);
    ok(h, RailPlacement.remove(player, id));
  }

  @GameTest(template = "empty", batch = "rail_v0300", timeoutTicks = 100)
  public static void survivalSourceHandcraftHasRealResearchAndMaterialDebit(GameTestHelper h) {
    var p = plan(PowerV20GameTests.site(h, 210), 0, 0, -16, 0);
    prepare(h, p);
    var player = player(h, p, false);
    var holder = h.getLevel().getRecipeManager().byKey(id("craft_rail")).orElseThrow();
    var input =
        new net.minecraft.world.inventory.TransientCraftingContainer(player.inventoryMenu, 3, 3);
    input.setItem(4, new ItemStack(Items.COBBLESTONE, 2));
    input.setItem(5, new ItemStack(RailContent.IRON_STICK.get(), 2));
    input.setItem(
        7,
        new ItemStack(
            factorycraft.registry.Content.ITEMS.getEntries().stream()
                .filter(x -> x.getId().getPath().equals("steel_plate"))
                .findFirst()
                .orElseThrow()
                .get(),
            2));
    var r = (factorycraft.recipes.CountedCraftingRecipe) holder.value();
    var result = new net.minecraft.world.inventory.ResultContainer();
    result.setRecipeUsed(holder);
    result.setItem(0, r.assemble(input.asCraftInput(), h.getLevel().registryAccess()));
    var slot = new net.minecraft.world.inventory.ResultSlot(player, input, result, 0, 0, 0);
    h.assertTrue(!slot.mayPickup(player), "unresearched rail handcraft");
    ResearchTestSupport.grant(h.getLevel(), ResearchTeams.resolve(player).orElseThrow(), "railway");
    h.assertTrue(slot.mayPickup(player), "researched craft unavailable");
    var made = slot.remove(2);
    slot.onTake(player, made);
    h.assertTrue(
        made.is(RailContent.RAIL.get())
            && made.getCount() == 2
            && input.getItem(4).getCount() == 1
            && input.getItem(5).getCount() == 1
            && input.getItem(7).getCount() == 1,
        "source rail craft/debit");
    var tech = ResearchCatalog.get(h.getLevel()).definition(id("railway")).recipe();
    h.assertTrue(
        tech.units() == 75
            && tech.ticks() == 600
            && tech.prerequisites().equals(List.of(id("logistics_2"), id("engine"))),
        "source railway gate");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "rail_v0300", timeoutTicks = 100)
  public static void twoClickItemPlacesPaidWholeCrossChunkTrackAndKeepsContinuation(
      GameTestHelper h) {
    var p = plan(PowerV20GameTests.site(h, 211), 0, 0, -32, 0);
    prepare(h, p);
    var player = player(h, p, true);
    var starter =
        new RailPlan(
            RailPlanner.starter(
                new RailEnd(
                    p.curve.start.x(), p.curve.start.y(), p.curve.start.z(), new RailHeading(8))));
    prepare(h, starter);
    var origin = p.curve.start;
    player.setPos(origin.x() + .5, origin.y() + 2, origin.z() + .5);
    player.setYRot(0);
    use(h, player, new BlockPos(origin.x(), origin.y() - 1, origin.z()), origin.y());
    h.assertTrue(
        RailPlanner.get(player) == null && RailPlacement.count(player) == 63,
        "ground click must build a paid short piece, not an invisible selection");
    player.setYRot(180);
    use(h, player, new BlockPos(origin.x(), origin.y(), origin.z()), origin.y() + .3);
    var e = p.curve.end;
    player.setPos(e.x() + .5, e.y() + 2, e.z() + .5);
    use(h, player, new BlockPos(e.x(), e.y() - 1, e.z()), e.y());
    var data = RailwayData.get(h.getLevel());
    var ids = data.at(new BlockPos(p.curve.start.x(), p.curve.start.y(), p.curve.start.z()));
    h.assertTrue(
        ids.size() == 2 && RailPlacement.count(player) == 63 - p.curve.cost(),
        "paid initial piece plus whole cross-chunk connection");
    var id =
        ids.stream()
            .filter(i -> data.segment(i).curve.end.samePosition(p.curve.end))
            .findFirst()
            .orElseThrow();
    h.assertTrue(
        data.available(id) && RailPlanner.get(player) == null,
        "whole edge unavailable or selection failed to clear");
    for (var c : p.footprint.bed.keySet()) {
      var be = h.getLevel().getBlockEntity(RailwayData.pos(c));
      h.assertTrue(
          be instanceof RailCellBlockEntity && ((RailCellBlockEntity) be).segments().contains(id),
          "physical part identity");
    }
    for (var edge : List.copyOf(ids)) remove(h, player, edge);
    h.assertTrue(
        RailPlacement.count(player) == 64 && data.segment(id) == null,
        "single-owner exact refunds");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "rail_v0300", timeoutTicks = 100)
  public static void typedRailMaterialsReturnOnceAndPartsNeverOwnInventory(GameTestHelper h) {
    var p = plan(PowerV20GameTests.site(h, 212), 0, 0, -16, 0);
    prepare(h, p);
    var player = player(h, p, true);
    player
        .getMainHandItem()
        .set(DataComponents.CUSTOM_NAME, Component.literal("Rail exact grade30"));
    var id = placed(h, p, player);
    var cell =
        (RailCellBlockEntity)
            h.getLevel()
                .getBlockEntity(
                    new BlockPos(p.curve.start.x(), p.curve.start.y(), p.curve.start.z()));
    var n = cell.saveWithoutMetadata(h.getLevel().registryAccess());
    h.assertTrue(
        !n.contains("Paid") && !n.contains("Items"), "part has duplicate resource inventory");
    remove(h, player, id);
    h.assertTrue(
        RailPlacement.count(player) == 64
            && player.getMainHandItem().getHoverName().getString().equals("Rail exact grade30"),
        "component-exact refund");
    h.assertTrue(RailwayData.get(h.getLevel()).remove(id).isEmpty(), "second refund");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "rail_v0300", timeoutTicks = 100)
  public static void obstructionFutureTrainClearanceAndInsufficientPriceLeaveNoPartialRails(
      GameTestHelper h) {
    var p = plan(PowerV20GameTests.site(h, 213), 0, 0, -20, 0);
    prepare(h, p);
    var player = player(h, p, true);
    var block = new BlockPos(p.curve.start.x(), p.curve.start.y() + 3, p.curve.start.z() - 10);
    h.getLevel().setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
    h.assertTrue(
        RailPlacement.place(player, p).equals("blocked") && RailPlacement.count(player) == 64,
        "overhead collision/debit");
    h.getLevel().removeBlock(block, false);
    player.getMainHandItem().setCount(1);
    h.assertTrue(RailPlacement.place(player, p).equals("price"), "price accepted");
    for (var c : p.footprint.bed.keySet())
      h.assertTrue(
          !h.getLevel().getBlockState(RailwayData.pos(c)).is(RailContent.BED.get()),
          "partial rails");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "rail_v0300", timeoutTicks = 100)
  public static void multiblockProtectionCancellationRollsBackAllCellsWithoutCharging(
      GameTestHelper h) {
    var p = plan(PowerV20GameTests.site(h, 214), 8, 0, -40, 0);
    prepare(h, p);
    var player = player(h, p, true);
    var event =
        new java.util.function.Consumer<
            net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent>() {
          public void accept(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent e) {
            if (e.getEntity() == player) e.setCanceled(true);
          }
        };
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(event);
    try {
      h.assertTrue(RailPlacement.place(player, p).equals("permission"), "cancelled claim accepted");
    } finally {
      net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(event);
    }
    h.assertTrue(RailPlacement.count(player) == 64, "cancelled placement charged");
    for (var c : p.footprint.bed.keySet())
      h.assertTrue(h.getLevel().getBlockState(RailwayData.pos(c)).isAir(), "failed rollback");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "rail_v0300", timeoutTicks = 100)
  public static void actualInclineAndHalfDiagonalMeshHaveIndependentSavedProjections(
      GameTestHelper h) {
    var o = PowerV20GameTests.site(h, 215);
    var plans =
        List.of(
            plan(o, 0, 4, -32, 0),
            new RailPlan(RailCurve.plan(end(o, 16, 0, 0, 1), end(o, 32, 0, -32, 1))));
    for (var p : plans) {
      prepare(h, p);
      var player = player(h, p, true);
      var id = placed(h, p, player);
      for (var c : p.footprint.bed.keySet()) {
        var be = (RailCellBlockEntity) h.getLevel().getBlockEntity(RailwayData.pos(c));
        var copy = new RailCellBlockEntity(be.getBlockPos(), be.getBlockState());
        copy.loadWithComponents(
            be.saveWithoutMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(
            copy.segments().contains(id)
                && !copy.shape().isEmpty()
                && copy.getModelData().get(RailProjection.MODEL) != null,
            "slope projection persistence");
      }
      remove(h, player, id);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "rail_v0300", timeoutTicks = 100)
  public static void junctionBranchesSurviveIndependentDemolitionAndRejoin(GameTestHelper h) {
    var o = PowerV20GameTests.site(h, 216);
    var incoming = new RailPlan(RailCurve.plan(end(o, 0, 0, 16, 0), end(o, 0, 0, 0, 0)));
    var straight = plan(o, 0, 0, -40, 0);
    var branch = plan(o, 24, 0, -24, 4);
    for (var p : List.of(incoming, straight, branch)) prepare(h, p);
    var player = player(h, straight, true);
    player.getInventory().setItem(1, new ItemStack(RailContent.RAIL.get(), 64));
    var a = placed(h, incoming, player);
    var b = placed(h, straight, player);
    var c = placed(h, branch, player);
    var data = RailwayData.get(h.getLevel());
    h.assertTrue(
        data.graph.successors(new RailGraph.Directed(a, false)).size() == 2,
        "junction missing branches");
    int count = RailPlacement.count(player);
    remove(h, player, c);
    h.assertTrue(
        data.graph.successors(new RailGraph.Directed(a, false)).size() == 1
            && RailPlacement.count(player) == count + branch.curve.cost(),
        "branch removal/refund");
    c = placed(h, branch, player);
    h.assertTrue(
        data.graph.successors(new RailGraph.Directed(a, false)).size() == 2, "branch rejoin");
    for (var id : List.of(a, b, c)) remove(h, player, id);
    h.assertTrue(RailPlacement.count(player) == 128, "junction material conservation");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "rail_v0300", timeoutTicks = 100)
  public static void atGradeCrossingIsRejectedButFiveBlockOverpassRemainsIndependent(
      GameTestHelper h) {
    var o = PowerV20GameTests.site(h, 217);
    var a = new RailPlan(RailCurve.plan(end(o, -16, 0, 0, 4), end(o, 16, 0, 0, 4)));
    var b = new RailPlan(RailCurve.plan(end(o, 0, 0, 16, 0), end(o, 0, 0, -16, 0)));
    var bridge = new RailPlan(RailCurve.plan(end(o, 0, 5, 16, 0), end(o, 0, 5, -16, 0)));
    for (var p : List.of(a, b)) prepare(h, p);
    var player = player(h, a, true);
    var first = placed(h, a, player);
    h.assertTrue(
        RailPlacement.place(player, b).equals("crossing"), "at-grade unrelated crossing connected");
    prepare(h, bridge);
    var second = placed(h, bridge, player);
    var data = RailwayData.get(h.getLevel());
    h.assertTrue(
        data.graph.successors(new RailGraph.Directed(first, false)).isEmpty()
            && data.graph.successors(new RailGraph.Directed(second, false)).isEmpty(),
        "overpass connected");
    remove(h, player, first);
    remove(h, player, second);
    h.assertTrue(RailPlacement.count(player) == 64, "bridge refund");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "rail_v0300", timeoutTicks = 100)
  public static void availabilityQuarantinesUnloadedChunkWithoutDestroyingSavedEdge(
      GameTestHelper h) {
    var o = PowerV20GameTests.site(h, 218);
    var p = plan(o, 0, 0, -40, 0);
    prepare(h, p);
    var player = player(h, p, true);
    var id = placed(h, p, player);
    var data = RailwayData.get(h.getLevel());
    var cp =
        new net.minecraft.world.level.ChunkPos(new BlockPos(o.getX(), o.getY(), o.getZ() - 20));
    data.unloadChunk(cp.toLong());
    h.assertTrue(
        !data.available(id) && data.segment(id) != null, "unload dropped/released segment");
    for (var c : p.footprint.bed.keySet())
      if (new net.minecraft.world.level.ChunkPos(RailwayData.pos(c)).equals(cp))
        ((RailCellBlockEntity) h.getLevel().getBlockEntity(RailwayData.pos(c))).onLoad();
    h.assertTrue(data.available(id), "loaded projections did not rejoin");
    remove(h, player, id);
    h.succeed();
  }

  @GameTest(template = "empty", batch = "rail_v0300_disk", timeoutTicks = 100)
  public static void exactRailGraphAndPaidComponentsSurviveIndependentJvm(GameTestHelper h) {
    var l = h.getLevel();
    var marker =
        l.getDataStorage()
            .computeIfAbsent(
                new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                    RailDisk::new, RailDisk::load),
                "factorycraft_v0300_rail_checkpoint");
    var data = RailwayData.get(l);
    if (marker.id == null) {
      var p = plan(new BlockPos(3008, 72, 3008), 24, 3, -24, 4);
      prepare(h, p);
      var player = player(h, p, true);
      player
          .getMainHandItem()
          .set(DataComponents.CUSTOM_NAME, Component.literal("disk rail thirty"));
      marker.id = placed(h, p, player);
      marker.length = p.curve.length();
      marker.price = p.curve.cost();
      marker.start = new BlockPos(p.curve.start.x(), p.curve.start.y(), p.curve.start.z());
      marker.setDirty();
      System.out.println(
          "FACTORYCRAFT_V0300_RAIL_DISK: SEEDED "
              + marker.id
              + " cost="
              + marker.price
              + " length="
              + marker.length);
    } else {
      var segment = data.segment(marker.id);
      h.assertTrue(
          segment != null
              && segment.paidCount() == marker.price
              && Math.abs(segment.curve.length() - marker.length) < 1e-9,
          "saved edge/price/metric changed");
      for (long cp : segment.footprint.chunks()) l.getChunk((int) cp, (int) (cp >> 32));
      for (var c : segment.footprint.bed.keySet()) {
        var be = l.getBlockEntity(RailwayData.pos(c));
        h.assertTrue(
            be instanceof RailCellBlockEntity
                && ((RailCellBlockEntity) be).segments().contains(marker.id),
            "disk projection missing");
        ((RailCellBlockEntity) be).onLoad();
      }
      h.assertTrue(data.available(marker.id), "saved curve not available after reload");
      var n = data.save(new CompoundTag(), l.registryAccess());
      var entries = n.getList("Segments", Tag.TAG_COMPOUND);
      boolean typed = false;
      for (var raw : entries) {
        var t = (CompoundTag) raw;
        if (t.getUUID("Id").equals(marker.id)) {
          var stack =
              ItemStack.parseOptional(
                  l.registryAccess(), t.getList("Paid", Tag.TAG_COMPOUND).getCompound(0));
          typed = stack.getHoverName().getString().equals("disk rail thirty");
        }
      }
      h.assertTrue(typed, "paid components lost");
      System.out.println(
          "FACTORYCRAFT_V0300_RAIL_DISK: VERIFIED "
              + marker.id
              + " cost="
              + marker.price
              + " length="
              + marker.length);
    }
    h.succeed();
  }

  @GameTest(template = "empty", batch = "rail_v0300", timeoutTicks = 100)
  public static void ordinaryBlockPlacementCannotFillTheReservedTrainCorridor(GameTestHelper h) {
    var p = plan(PowerV20GameTests.site(h, 219), 0, 0, -16, 0);
    prepare(h, p);
    var player = player(h, p, true);
    var id = placed(h, p, player);
    player.getInventory().setItem(1, player.getMainHandItem().copy());
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 2));
    var at = new BlockPos(p.curve.end.x(), p.curve.end.y(), p.curve.end.z());
    var hit =
        new BlockHitResult(
            new Vec3(at.getX() + .5, at.getY() + .3, at.getZ() + .5), Direction.UP, at, false);
    player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    h.assertTrue(
        h.getLevel().getBlockState(at.above()).isAir() && player.getMainHandItem().getCount() == 2,
        "ordinary placement intruded into future train envelope");
    remove(h, player, id);
    h.assertTrue(RailPlacement.count(player) == 64, "clearance denial lost rail resources");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "rail_v0300", timeoutTicks = 100)
  public static void inventoryChangedByClaimCallbackAbortsBeforeDebitOrPublication(
      GameTestHelper h) {
    var p = plan(PowerV20GameTests.site(h, 220), 24, 0, -24, 4);
    prepare(h, p);
    var player = player(h, p, true);
    var listener =
        new java.util.function.Consumer<
            net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent>() {
          public void accept(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent e) {
            if (e.getEntity() == player)
              player
                  .getMainHandItem()
                  .set(DataComponents.CUSTOM_NAME, Component.literal("claim changed components"));
          }
        };
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
    try {
      h.assertTrue(
          RailPlacement.place(player, p).equals("price"), "stale component reservation committed");
    } finally {
      net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
    }
    h.assertTrue(
        RailPlacement.count(player) == 64
            && player
                .getMainHandItem()
                .getHoverName()
                .getString()
                .equals("claim changed components"),
        "caller change or rail count overwritten");
    for (var c : p.footprint.bed.keySet())
      h.assertTrue(
          h.getLevel().getBlockState(RailwayData.pos(c)).isAir(),
          "callback-race rollback left a part");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "rail_v0300", timeoutTicks = 100)
  public static void competingPlayersCannotPayForTheSameGeometryTwice(GameTestHelper h) {
    var p = plan(PowerV20GameTests.site(h, 222), 0, 0, -24, 0);
    prepare(h, p);
    var a = player(h, p, true);
    var b = player(h, p, true);
    var id = placed(h, p, a);
    h.assertTrue(
        RailPlacement.place(b, p).equals("crossing") && RailPlacement.count(b) == 64,
        "second player created duplicate geometry/debit");
    a.setPos(p.curve.start.x() + 100, p.curve.start.y() + 10, p.curve.start.z());
    h.assertTrue(RailPlacement.remove(a, id).equals("reach"), "out-of-reach demolition accepted");
    remove(h, a, id);
    h.assertTrue(
        RailPlacement.count(a) + RailPlacement.count(b) == 128, "two-player refund ledger");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "rail_v0300", timeoutTicks = 100)
  public static void environmentalBreakRetiresExactlyOneWholeMaterialClaim(GameTestHelper h) {
    var p = plan(PowerV20GameTests.site(h, 223), 0, 0, -16, 0);
    prepare(h, p);
    var player = player(h, p, true);
    var pinned = p.footprint.chunks();
    for (long cp : pinned) h.getLevel().setChunkForced((int) cp, (int) (cp >> 32), true);
    h.runAfterDelay(
        6,
        () -> {
          try {
            var id = placed(h, p, player);
            var at = new BlockPos(p.curve.start.x(), p.curve.start.y(), p.curve.start.z() - 8);
            h.getLevel().removeBlock(at, false);
            h.getLevel().removeBlock(at, false);
            var drops =
                h.getLevel()
                    .getEntitiesOfClass(
                        net.minecraft.world.entity.item.ItemEntity.class, new AABB(at).inflate(3));
            int n = 0;
            for (var e : drops)
              if (e.getItem().is(RailContent.RAIL.get())) {
                n += e.getItem().getCount();
                e.discard();
              }
            h.assertTrue(
                n == p.curve.cost()
                    && RailPlacement.count(player) + n == 64
                    && RailwayData.get(h.getLevel()).segment(id) == null,
                "environmental ledger: dropped="
                    + n
                    + " held="
                    + RailPlacement.count(player)
                    + " restoring="
                    + h.getLevel().restoringBlockSnapshots);
            h.succeed();
          } finally {
            for (long cp : pinned) h.getLevel().setChunkForced((int) cp, (int) (cp >> 32), false);
          }
        });
  }

  @GameTest(template = "empty", batch = "rail_v0300_profile", timeoutTicks = 150)
  public static void boundedPhysicalRailNetworkHasNoIdleTopologyRebuilds(GameTestHelper h) {
    var o = PowerV20GameTests.site(h, 221);
    var plans = new ArrayList<RailPlan>();
    for (int row = 0; row < 16; row++)
      for (int i = 0; i < 8; i++) {
        var p =
            new RailPlan(
                RailCurve.plan(end(o, i * 2, 0, row * 4, 4), end(o, i * 2 + 2, 0, row * 4, 4)));
        prepare(h, p);
        plans.add(p);
      }
    var pinned = new HashSet<Long>();
    for (var p : plans) pinned.addAll(p.footprint.chunks());
    for (long cp : pinned) h.getLevel().setChunkForced((int) cp, (int) (cp >> 32), true);
    var player = player(h, plans.getFirst(), true);
    player.getInventory().setItem(1, new ItemStack(RailContent.RAIL.get(), 64));
    var ids = new ArrayList<UUID>();
    long[] timings = new long[128];
    // GameTest's same-tick task map is unordered. Batch inside one callback so
    // a continuous line is actually built in endpoint order, four edges per tick.
    for (int batch = 0; batch < 32; batch++) {
      final int first = batch * 4;
      h.runAfterDelay(
          batch + 1,
          () -> {
            for (int index = first; index < first + 4; index++) {
              long t = System.nanoTime();
              try {
                ids.add(placed(h, plans.get(index), player));
              } catch (Throwable error) {
                for (long cp : pinned)
                  h.getLevel().setChunkForced((int) cp, (int) (cp >> 32), false);
                throw error;
              }
              timings[index] = System.nanoTime() - t;
            }
          });
    }
    h.runAfterDelay(
        40,
        () -> {
          try {
            var d = RailwayData.get(h.getLevel());
            h.assertTrue(
                ids.size() == 128 && RailPlacement.count(player) == 0,
                "physical benchmark material ledger");
            long revision = d.graph.revisions();
            for (int i = 0; i < 10000; i++)
              d.graph.successors(new RailGraph.Directed(ids.get(i % 128), false));
            h.assertTrue(d.graph.revisions() == revision, "read-only graph query rebuilt topology");
            Arrays.sort(timings);
            System.out.println(
                "FACTORYCRAFT_V0300_RAIL_PROFILE:128 physical edges; meanNs="
                    + Arrays.stream(timings).average().orElseThrow()
                    + " p50Ns="
                    + timings[64]
                    + " p95Ns="
                    + timings[121]
                    + " p99Ns="
                    + timings[126]
                    + " maxNs="
                    + timings[127]
                    + "; 10000 queries, zero rebuilds; exact128-item claim");
            for (var id : ids) remove(h, player, id);
            h.assertTrue(RailPlacement.count(player) == 128, "physical benchmark refund changed");
            h.succeed();
          } finally {
            for (long cp : pinned) h.getLevel().setChunkForced((int) cp, (int) (cp >> 32), false);
          }
        });
  }

  @GameTest(template = "empty", batch = "rail_v0300", timeoutTicks = 100)
  public static void itemFirstSelectionConnectsAnExistingEndpointInsteadOfOpeningInspection(
      GameTestHelper h) {
    var o = PowerV20GameTests.site(h, 224);
    var a = plan(o, 0, 0, -16, 0);
    var b = new RailPlan(RailCurve.plan(end(o, 0, 0, -16, 0), end(o, 0, 0, -32, 0)));
    prepare(h, a);
    prepare(h, b);
    var player = player(h, a, true);
    var id = placed(h, a, player);
    RailPlanner.clear(player);
    var at = new BlockPos(b.curve.start.x(), b.curve.start.y(), b.curve.start.z());
    player.setPos(at.getX() + .5, at.getY() + 2, at.getZ() + .5);
    player.setYRot(180);
    var first =
        new UseOnContext(
            player,
            InteractionHand.MAIN_HAND,
            new BlockHitResult(
                new Vec3(at.getX() + .5, at.getY() + .3, at.getZ() + .5), Direction.UP, at, false));
    player.getMainHandItem().getItem().onItemUseFirst(player.getMainHandItem(), first);
    h.assertTrue(
        RailPlanner.get(player) != null
            && RailPlanner.get(player).start().samePosition(b.curve.start),
        "rail block inspection swallowed planner click");
    var end = new BlockPos(b.curve.end.x(), b.curve.end.y(), b.curve.end.z());
    player.setPos(end.getX() + .5, end.getY() + 2, end.getZ() + .5);
    var second =
        new UseOnContext(
            player,
            InteractionHand.MAIN_HAND,
            new BlockHitResult(
                new Vec3(end.getX() + .5, end.getY(), end.getZ() + .5),
                Direction.UP,
                end.below(),
                false));
    player.getMainHandItem().getItem().onItemUseFirst(player.getMainHandItem(), second);
    var data = RailwayData.get(h.getLevel());
    var successors = data.graph.successors(new RailGraph.Directed(id, false));
    h.assertTrue(
        successors.size() == 1 && RailPlacement.count(player) == 48,
        "existing endpoint did not connect/debit");
    h.assertTrue(
        !h.getLevel().getBlockState(at).getCollisionShape(h.getLevel(), at).isEmpty(),
        "native state cached an empty rail collision");
    remove(h, player, successors.getFirst().edge());
    remove(h, player, id);
    h.assertTrue(RailPlacement.count(player) == 64, "endpoint connection refund");
    h.succeed();
  }

  @GameTest(template = "empty", batch = "rail_v0300", timeoutTicks = 100)
  public static void itemStackWrapperCannotCancelASecondTransactionAfterRailDebit(
      GameTestHelper h) {
    var p = plan(PowerV20GameTests.site(h, 225), 0, 0, -24, 0);
    prepare(h, p);
    var player = player(h, p, true);
    var initial =
        new RailPlan(
            RailPlanner.starter(
                new RailEnd(
                    p.curve.start.x(), p.curve.start.y(), p.curve.start.z(), new RailHeading(8))));
    prepare(h, initial);
    var initialId = placed(h, initial, player);
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    var listener =
        new java.util.function.Consumer<
            net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent>() {
          public void accept(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent e) {
            if (e.getEntity() == player && calls.incrementAndGet() > 1) e.setCanceled(true);
          }
        };
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
    try {
      for (var end : List.of(p.curve.start, p.curve.end)) {
        player.setPos(end.x() + .5, end.y() + 2, end.z() + .5);
        var floor = new BlockPos(end.x(), end.y() - (end == p.curve.start ? 0 : 1), end.z());
        player
            .getMainHandItem()
            .useOn(
                new UseOnContext(
                    player,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(
                        new Vec3(end.x() + .5, end.y(), end.z() + .5),
                        Direction.UP,
                        floor,
                        false)));
      }
    } finally {
      net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
    }
    var data = RailwayData.get(h.getLevel());
    var ids = data.at(new BlockPos(p.curve.start.x(), p.curve.start.y(), p.curve.start.z()));
    h.assertTrue(
        calls.get() == 1 && ids.size() == 2 && RailPlacement.count(player) == 63 - p.curve.cost(),
        "generic wrapper double-claimed or rolled back a paid track");
    for (var edge : List.copyOf(ids)) remove(h, player, edge);
    h.assertTrue(RailPlacement.count(player) == 64, "wrapper refund ledger");
    h.succeed();
  }

  static void use(GameTestHelper h, ServerPlayer p, BlockPos at, double y) {
    p.getMainHandItem()
        .useOn(
            new UseOnContext(
                p,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(
                    new Vec3(at.getX() + .5, y, at.getZ() + .5), Direction.UP, at, false)));
  }

  static final class RailDisk extends net.minecraft.world.level.saveddata.SavedData {
    UUID id;
    double length;
    int price;
    BlockPos start;

    static RailDisk load(CompoundTag n, HolderLookup.Provider r) {
      var d = new RailDisk();
      d.id = n.getUUID("Id");
      d.length = n.getDouble("Length");
      d.price = n.getInt("Price");
      d.start = BlockPos.of(n.getLong("Start"));
      return d;
    }

    public CompoundTag save(CompoundTag n, HolderLookup.Provider r) {
      if (id != null) {
        n.putUUID("Id", id);
        n.putDouble("Length", length);
        n.putInt("Price", price);
        n.putLong("Start", start.asLong());
      }
      return n;
    }
  }
}
