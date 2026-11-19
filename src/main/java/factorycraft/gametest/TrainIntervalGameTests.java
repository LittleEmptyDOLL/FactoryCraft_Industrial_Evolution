package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.fluids.FluidKey;
import factorycraft.rail.*;
import factorycraft.simulation.rail.*;
import factorycraft.simulation.trains.*;
import factorycraft.trains.*;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;

/** Regression for the supplied 0.31.1 TrackOccupancy.span server crash. */
@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class TrainIntervalGameTests {
  @GameTest(template = "empty", batch = "train_interval_disk", timeoutTicks = 160)
  public static void paidReverseFractionalCurveBoundaryAndExactOwnersSurviveIndependentJvm(
      GameTestHelper h) {
    var level = h.getLevel();
    var data = TrainData.get(level);
    var checkpoint =
        level
            .getDataStorage()
            .computeIfAbsent(
                new SavedData.Factory<>(TrainV31GameTests.Disk::new, TrainV31GameTests.Disk::load),
                "factorycraft_reverse_interval_checkpoint");
    if (checkpoint.data.hasUUID("Train")) {
      var id = checkpoint.data.getUUID("Train");
      var train = data.train(id);
      h.assertTrue(train != null, "reverse-boundary train missing after JVM restart");
      h.assertTrue(
          saved(data, id, level).equals(checkpoint.data.getCompound("State")),
          "reverse-boundary pose/route/fuel/components/UUID changed on disk");
      var reversed = train.path.legs().getFirst();
      h.assertTrue(
          reversed.edge().reverse()
              && train.rear() < reversed.end()
              && train.front() > reversed.end(),
          "checkpoint does not straddle reverse boundary");
      assertClaims(h, data, train);
      System.out.println("FACTORYCRAFT_REVERSE_INTERVAL_DISK: VERIFIED " + id);
      h.succeed();
      return;
    }

    var site = TrainV31GameTests.rail(h, 438);
    var join = site.plan().curve.end;
    var turn =
        new RailPlan(
            RailCurve.plan(
                join, new RailEnd(join.x() + 24, join.y(), join.z() - 24, new RailHeading(4))));
    var turnId = TrainCompletionGameTests.extra(h, site, turn);
    h.runAfterDelay(
        8,
        () -> {
          var loco = TrainV31GameTests.stock(h, site, StockSpec.LOCOMOTIVE, 85);
          var cargo = TrainV31GameTests.stock(h, site, StockSpec.CARGO, 78);
          var tanker = TrainV31GameTests.stock(h, site, StockSpec.FLUID, 71);
          var train = data.owner(loco.getUUID());
          h.assertTrue(train.cars.size() == 3, "actual paid three-car coupling failed");
          var engine = data.car(loco.getUUID());
          engine.items.setStackInSlot(0, new ItemStack(Items.COAL, 7));
          var item = new ItemStack(Items.DIAMOND, 31);
          item.set(DataComponents.CUSTOM_NAME, Component.literal("reverse-boundary finite cargo"));
          data.car(cargo.getUUID()).items.setStackInSlot(0, item);
          var water = new FluidStack(Fluids.WATER, 234567);
          water.set(DataComponents.CUSTOM_NAME, Component.literal("reverse-boundary finite water"));
          data.car(tanker.getUUID()).fluid.restore(new FluidKey(water), water.getAmount());

          // Reframe this diagnostic consist onto the real reverse curve. No resource is reseeded
          // after this one-time setup. A nonzero historical route origin is legal persisted state.
          double length = turn.curve.length(), begin = 1;
          while (begin <= 1_048_576 && (begin + length) - begin <= length) begin *= 2;
          h.assertTrue(
              begin <= 1_048_576, "fixture must exercise an actual endpoint ULP overshoot");
          var first = new TrainPath.Leg(new RailGraph.Directed(turnId, true), begin, length);
          train.path =
              new TrainPath(
                  List.of(
                      first,
                      new TrainPath.Leg(
                          new RailGraph.Directed(site.edge(), true),
                          first.end(),
                          site.plan().curve.length())));
          train.head = first.end() - 4.5;
          train.direction = 1;
          engine.reversed = false;
          data.wakeChunk(net.minecraft.world.level.ChunkPos.asLong(loco.blockPosition()));
          TrainWorld.views(data, train);
          var rider = TrainCompletionGameTests.rider(level, loco, site.player());
          train.speed = 120 / 216.;
          double before = train.head;
          for (int tick = 0; tick < 3; tick++) {
            h.assertTrue(
                TrainNetwork.control(rider, loco, 1, false, 0), "real drive intent refused");
            TrainSimulation.tick(data, train);
            assertClaims(h, data, train);
          }
          h.assertTrue(
              train.head > first.end()
                  && train.rear() < first.end()
                  && train.head > before
                  && train.speed >= 120 / 216.
                  && engine.spentJ > 0,
              "real paid 120km/h tick did not cross the reverse fractional boundary: "
                  + train.head
                  + " "
                  + train.status);
          h.assertTrue(
              engine.fuelJ + engine.spentJ + 4_000_000L * engine.items.getStackInSlot(0).getCount()
                  == 28_000_000,
              "finite coal/buffer/spent ledger changed");
          h.assertTrue(
              engine.health == 1000
                  && data.pendingWrecks() == 0
                  && ItemStack.matches(data.car(cargo.getUUID()).items.getStackInSlot(0), item)
                  && data.car(tanker.getUUID()).fluid.amount() == 234567,
              "boundary traversal changed health/cargo/fluid owners");
          rider.stopRiding();
          data.unloadChunk(net.minecraft.world.level.ChunkPos.asLong(loco.blockPosition()));
          checkpoint.data.putUUID("Train", train.id);
          checkpoint.data.put("State", saved(data, train.id, level).copy());
          checkpoint.setDirty();
          data.setDirty();
          for (long key : site.pins()) level.setChunkForced((int) key, (int) (key >> 32), false);
          System.out.println(
              "FACTORYCRAFT_REVERSE_INTERVAL_DISK: SEEDED "
                  + train.id
                  + " begin="
                  + begin
                  + " length="
                  + length
                  + " rawReverseMin="
                  + (length - ((begin + length) - begin)));
          h.succeed();
        });
  }

  private static void assertClaims(GameTestHelper h, TrainData data, TrainData.Train train) {
    var claims = TrackOccupancy.span(train.path, train.rear(), train.front());
    h.assertTrue(
        data.occupancy.intervals(train.id).equals(claims),
        "committed body has different canonical reservations");
    h.assertTrue(data.occupancy.canReserve(train.id, claims), "self claims conflict");
    h.assertTrue(
        !data.occupancy.canReserve(UUID.randomUUID(), claims),
        "another train can steal the occupied boundary");
    for (var claim : claims) {
      var leg =
          train.path.legs().stream()
              .filter(x -> x.edge().edge().equals(claim.edge()))
              .findFirst()
              .orElseThrow();
      h.assertTrue(
          claim.min() >= 0 && claim.max() <= leg.length(),
          "interval escaped the physical rail domain");
      if (leg.edge().reverse() && train.front() >= leg.end())
        h.assertTrue(claim.min() == 0, "reverse endpoint was not reserved exactly");
    }
  }

  private static CompoundTag saved(
      TrainData data, UUID id, net.minecraft.server.level.ServerLevel level) {
    return data
        .save(new CompoundTag(), level.registryAccess())
        .getList("Trains", Tag.TAG_COMPOUND)
        .stream()
        .map(x -> (CompoundTag) x)
        .filter(x -> x.getUUID("Id").equals(id))
        .findFirst()
        .orElseThrow();
  }

  private TrainIntervalGameTests() {}
}
