package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.rail.*;
import factorycraft.simulation.rail.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class RailConnectionGameTests {
  @GameTest(template = "empty", batch = "rail_bidirectional", timeoutTicks = 160)
  public static void sameSelectedPhysicalTrackBuildsFromEitherEndAndMatchesPreviewAndRefunds(
      GameTestHelper h) {
    var site = TrainV31GameTests.rail(h, 480);
    var origin = site.plan().curve.start;
    var ends =
        List.of(
            new RailEnd(origin.x(), origin.y(), origin.z() + 32, new RailHeading(8)),
            new RailEnd(origin.x(), origin.y(), origin.z() - 128, new RailHeading(0)));
    for (var end : ends)
      RailwayV30GameTests.prepare(
          h,
          new RailPlan(
              RailCurve.plan(
                  end.z() > origin.z() ? origin.reverse() : site.plan().curve.end, end)));
    h.runAfterDelay(
        8,
        () -> {
          var p = site.player();
          p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailContent.RAIL.get(), 64));
          var data = RailwayData.get(h.getLevel());
          var created = new ArrayList<UUID>();
          int price = 0;
          for (int index = 0; index < 2; index++) {
            var startPos = new BlockPos(origin.x(), origin.y(), origin.z() - 4);
            p.setPos(origin.x() + .5, origin.y() + 2, origin.z() - 4 + .5);
            p.setYRot(index == 0 ? 180 : 0);
            var first =
                new BlockHitResult(Vec3.atCenterOf(startPos), Direction.UP, startPos, false);
            h.assertTrue(
                RailPlanner.click(p, first).equals("selected"), "physical source selection failed");
            var selection = RailPlanner.get(p);
            var end = ends.get(index);
            var below = new BlockPos(end.x(), end.y() - 1, end.z());
            p.setPos(end.x() + .5, end.y() + 2, end.z() + .5);
            var hit =
                new BlockHitResult(
                    Vec3.atCenterOf(below).add(0, .5, 0), Direction.UP, below, false);
            var request =
                RailPlanner.request(
                    h.getLevel(),
                    hit,
                    selection.source(),
                    RailHeading.fromYaw(p.getYRot()),
                    selection.goal());
            var preview = RailConnectionResolver.resolve(request);
            h.assertTrue(preview.valid(), "shared preview geometry: " + preview.reason());
            h.assertTrue(
                preview.start().equals(index == 0 ? origin.reverse() : site.plan().curve.end),
                "wrong physical source end");
            price += preview.curve().cost();
            h.assertTrue(
                RailPlanner.click(p, hit).equals("ready"),
                "server connection rejected shared green geometry");
            h.assertTrue(
                RailPlanner.get(p) == null && RailPlacement.count(p) == 64 - price,
                "selection/payment not settled once");
            var actual =
                data.graph.edges().stream()
                    .filter(
                        e ->
                            !e.id().equals(site.edge())
                                && e.curve().start.equals(preview.start())
                                && e.curve().end.equals(preview.end()))
                    .findFirst()
                    .orElseThrow();
            created.add(actual.id());
            var direction = new RailGraph.Directed(site.edge(), index == 0);
            h.assertTrue(
                data.graph
                    .successors(direction)
                    .contains(new RailGraph.Directed(actual.id(), false)),
                "committed edge is disconnected");
          }
          for (var id : created) RailwayV30GameTests.remove(h, p, id);
          h.assertTrue(
              RailPlacement.count(p) == 64, "bidirectional refunds duplicated/lost paid rails");
          TrainV31GameTests.cleanup(h, site);
          h.succeed();
        });
  }

  @GameTest(template = "empty", batch = "rail_bidirectional", timeoutTicks = 160)
  public static void manualSourceChoiceResetsAndCancelledOppositeEndBuildKeepsMaterialsAndSelection(
      GameTestHelper h) {
    var site = TrainV31GameTests.rail(h, 481);
    var a = site.plan().curve.start;
    var end = new RailEnd(a.x(), a.y(), a.z() + 32, new RailHeading(8));
    RailwayV30GameTests.prepare(h, new RailPlan(RailCurve.plan(a.reverse(), end)));
    h.runAfterDelay(
        8,
        () -> {
          var p = site.player();
          p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RailContent.RAIL.get(), 64));
          p.setPos(a.x() + .5, a.y() + 2, a.z() + .5);
          var at = new BlockPos(a.x(), a.y(), a.z());
          h.assertTrue(
              RailPlanner.click(p, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false))
                  .equals("selected"),
              "source not selected");
          for (int side : new int[] {2, 1, 0}) {
            RailPlanner.edit(p, 1);
            h.assertTrue(RailPlanner.get(p).side() == side, "X did not cycle back to automatic");
          }
          var below = new BlockPos(end.x(), end.y() - 1, end.z());
          p.setPos(end.x() + .5, end.y() + 2, end.z() + .5);
          var hit =
              new BlockHitResult(Vec3.atCenterOf(below).add(0, .5, 0), Direction.UP, below, false);
          java.util.function.Consumer<BlockEvent.EntityPlaceEvent> listener =
              e -> {
                if (e.getEntity() == p) e.setCanceled(true);
              };
          NeoForge.EVENT_BUS.addListener(listener);
          try {
            h.assertTrue(
                RailPlanner.click(p, hit).equals("permission"), "opposite end bypassed protection");
          } finally {
            NeoForge.EVENT_BUS.unregister(listener);
          }
          h.assertTrue(
              RailPlacement.count(p) == 64 && RailPlanner.get(p) != null,
              "cancelled transaction spent/cleared source");
          RailPlanner.edit(p, 2);
          h.assertTrue(RailPlanner.get(p) == null, "C did not clear source override");
          TrainV31GameTests.cleanup(h, site);
          h.succeed();
        });
  }

  private RailConnectionGameTests() {}
}
