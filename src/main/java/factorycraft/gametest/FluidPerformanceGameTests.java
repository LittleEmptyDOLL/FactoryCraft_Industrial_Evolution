package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.FluidBlockEntity;
import factorycraft.fluids.*;
import factorycraft.registry.Content;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class FluidPerformanceGameTests {
  @GameTest(template = "empty", batch = "fluid_performance_v0241", timeoutTicks = 100)
  public static void stableNativeSegmentDoesNotRebuildForTenThousandContentNotifications(
      GameTestHelper h) {
    var origin = h.absolutePos(new BlockPos(3, 3, 3));
    for (int i = 0; i < 8; i++)
      h.getLevel()
          .setBlockAndUpdate(
              origin.east(i), Content.FLUID_BLOCKS.get(FluidKind.PIPE).get().defaultBlockState());
    h.runAfterDelay(
        5,
        () -> {
          var a = (FluidBlockEntity) h.getLevel().getBlockEntity(origin);
          var cap = a.port(null);
          var f = new FluidStack(Fluids.WATER, 7000);
          var exec = IFluidHandler.FluidAction.EXECUTE;
          h.assertTrue(
              FluidSimulation.manual(() -> cap.fill(f, exec)) == 7000, "finite seed rejected");
          var before = FluidSimulation.metrics(h.getLevel());
          FluidSimulation.manual(
              () -> {
                for (int i = 0; i < 10000; i++) {
                  h.assertTrue(
                      cap.drain(1, exec).getAmount() == 1
                          && cap.fill(new FluidStack(Fluids.WATER, 1), exec) == 1,
                      "content operation lost ownership");
                  FluidSimulation.changed(a);
                }
                return true;
              });
          var after = FluidSimulation.metrics(h.getLevel());
          h.assertTrue(
              before.visits() == after.visits()
                  && before.rebuilds() == after.rebuilds()
                  && FluidSimulation.view(a).amount() == 7000,
              "content notification rebuilt topology or changed mass");
          h.succeed();
        });
  }
}
