package factorycraft.nuclear;

import factorycraft.blocks.FactoryKind;
import factorycraft.fluids.*;
import factorycraft.simulation.fluids.FluidCell;
import factorycraft.simulation.nuclear.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Owned pipe heat, or a 10-MW exchanger with exact enthalpy credit and physical water/steam boxes.
 */
public final class HeatDeviceBlockEntity extends NuclearOwner implements HeatOwner {
  public final HeatStore heat = new HeatStore(40_000);
  public final FluidCell<FluidKey> water = new FluidCell<>(2000, this::changed),
      steam = new FluidCell<>(2000, this::changed);
  public int boilingCredit;
  private long lastWork = Long.MIN_VALUE;

  public HeatDeviceBlockEntity(BlockPos p, BlockState s) {
    super(p, s);
  }

  public boolean exchanger() {
    return kind() == FactoryKind.HEAT_EXCHANGER;
  }

  public NuclearOwner heatController() {
    return this;
  }

  public HeatStore heat() {
    return heat;
  }

  public long maximumTransfer() {
    return exchanger() ? 4_000_000 : 2_000_000;
  }

  public List<HeatOwner.Port> heatPorts() {
    if (!exchanger())
      return List.of(
          new HeatOwner.Port(worldPosition, Direction.NORTH),
          new HeatOwner.Port(worldPosition, Direction.SOUTH),
          new HeatOwner.Port(worldPosition, Direction.WEST),
          new HeatOwner.Port(worldPosition, Direction.EAST),
          new HeatOwner.Port(worldPosition, Direction.UP),
          new HeatOwner.Port(worldPosition, Direction.DOWN));
    var p = assembly().profile();
    var f = p.facing(getBlockState());
    return List.of(new HeatOwner.Port(p.offset(worldPosition, f, 1, 0, 1), f.getOpposite()));
  }

  public FluidCell<FluidKey> cell(int c) {
    return exchanger() ? (c == 0 ? water : c == 1 ? steam : null) : null;
  }

  public boolean accepts(int c, FluidKey k) {
    return exchanger() && c == 0 && k != null && k.stack(1).getFluid() == Fluids.WATER;
  }

  public void tick() {
    if (!ready()) return;
    if (exchanger()) {
      pullFluids();
      if (lastWork != level.getGameTime()) {
        lastWork = level.getGameTime();
        long above = Math.max(0, heat.units() - 485 * heat.specificHeat);
        int possible = (int) ((boilingCredit + Math.min(20_000, above)) / 388);
        int count = Math.min(possible, Math.min(water.amount(), steam.space()));
        if (count > 0) {
          var source = water.key().stack(1);
          var output = new FluidStack(HotSteam.STEAM.get(), 1);
          output.applyComponents(source.getComponentsPatch());
          var key = new FluidKey(output);
          if (steam.accepts(key)) {
            int take = (int) Math.min(Math.min(above, 20_000), count * 388L + 387 - boilingCredit);
            var wk = water.key();
            FluidCell.commitBatch(
                List.of(
                    new FluidCell.Mutation<>(water, wk, water.amount() - count),
                    new FluidCell.Mutation<>(steam, key, steam.amount() + count)),
                () -> {
                  long used = heat.drainAbove(take, 500);
                  if (used != take) throw new IllegalStateException("Reserved heat disappeared");
                  boilingCredit += take - count * 388;
                },
                false);
          }
        }
      }
      pushFluids();
    }
    syncPeriodic();
  }

  public int[] metrics() {
    return new int[] {
      (int) heat.units(),
      (int) heat.maximum,
      (int) (heat.temperature() * 1000),
      boilingCredit,
      0,
      ready() ? 1 : 0,
      water.amount(),
      steam.amount()
    };
  }

  protected void saveAdditional(CompoundTag n, HolderLookup.Provider r) {
    super.saveAdditional(n, r);
    n.putLong("Heat", heat.units());
    n.putInt("BoilingCredit", boilingCredit);
    n.putLong("LastWork", lastWork);
    if (water.amount() > 0) n.put("Water", water.key().stack(water.amount()).save(r));
    if (steam.amount() > 0) n.put("Steam", steam.key().stack(steam.amount()).save(r));
  }

  protected void loadAdditional(CompoundTag n, HolderLookup.Provider r) {
    super.loadAdditional(n, r);
    heat.restore(n.getLong("Heat"));
    boilingCredit = n.getInt("BoilingCredit");
    if (boilingCredit < 0 || boilingCredit >= 388)
      throw new IllegalArgumentException("Heat credit");
    lastWork = n.contains("LastWork") ? n.getLong("LastWork") : Long.MIN_VALUE;
    var w = FluidStack.parseOptional(r, n.getCompound("Water"));
    var s = FluidStack.parseOptional(r, n.getCompound("Steam"));
    if (w.getAmount() > 2000
        || s.getAmount() > 2000
        || !w.isEmpty() && !accepts(0, new FluidKey(w))
        || !s.isEmpty() && s.getFluid() != HotSteam.STEAM.get())
      throw new IllegalArgumentException("Exchanger fluid identity");
    water.restore(w.isEmpty() ? null : new FluidKey(w), w.getAmount());
    steam.restore(s.isEmpty() ? null : new FluidKey(s), s.getAmount());
  }
}
