package factorycraft.nuclear;

import factorycraft.blocks.FactoryKind;
import factorycraft.energy.GridSupply;
import factorycraft.fluids.*;
import factorycraft.simulation.fluids.FluidCell;
import factorycraft.simulation.nuclear.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;

/** Solar output, tertiary charge, or a hot-steam generator. Exactly one charge owner per device. */
public final class ElectricDeviceBlockEntity extends NuclearOwner implements GridSupply {
  public final ChargeStore battery = new ChargeStore();
  public final SolarCycle solar = new SolarCycle();
  public final FluidCell<FluidKey> steam = new FluidCell<>(2000, this::changed);
  private long stock, offerTick = Long.MIN_VALUE, productionTick = Long.MIN_VALUE;
  private int solarOffer, outputUsed, lastSolar;
  private boolean sky;
  private long skyAt = Long.MIN_VALUE;

  public ElectricDeviceBlockEntity(BlockPos p, BlockState s) {
    super(p, s);
  }

  public boolean accumulator() {
    return kind() == FactoryKind.ACCUMULATOR;
  }

  public boolean panel() {
    return kind() == FactoryKind.SOLAR_PANEL;
  }

  public boolean turbine() {
    return kind() == FactoryKind.STEAM_TURBINE;
  }

  public boolean generatesElectricity() {
    return !accumulator();
  }

  public int supplyPriority() {
    return panel() ? 0 : accumulator() ? 2 : 1;
  }

  public boolean supplyReady() {
    return ready();
  }

  public FluidCell<FluidKey> cell(int c) {
    return turbine() && c == 0 ? steam : null;
  }

  public boolean accepts(int c, FluidKey key) {
    return turbine()
        && c == 0
        && key != null
        && (key.stack(1).getFluid() == HotSteam.STEAM.get()
            || key.stack(1).getFluid() == factorycraft.thermal.SteamFluids.STEAM.get());
  }

  private long now() {
    return level.getGameTime();
  }

  private void lease() {
    if (offerTick != now()) {
      offerTick = now();
      outputUsed = 0;
      solarOffer = 0;
    }
  }

  public long supplyAvailable() {
    if (!ready()) return 0;
    lease();
    return accumulator()
        ? battery.drain(now(), Long.MAX_VALUE, true)
        : panel() ? solarOffer : Math.min(stock, EnergyUnits.TURBINE_PER_TICK - outputUsed);
  }

  public long takeSupply(long requested) {
    long n = Math.min(Math.max(0, requested), supplyAvailable());
    if (accumulator()) n = battery.drain(now(), n, false);
    else if (panel()) solarOffer -= (int) n;
    else {
      stock -= n;
      outputUsed += (int) n;
    }
    if (n > 0) changed();
    return n;
  }

  public void refundSupply(long n) {
    if (n < 0) throw new IllegalArgumentException("Negative power refund");
    if (accumulator()) battery.refund(n);
    else if (panel()) solarOffer += (int) n;
    else {
      if (n > outputUsed) throw new IllegalArgumentException("Turbine refund");
      stock += n;
      outputUsed -= (int) n;
    }
    if (n > 0) changed();
  }

  public long chargeSupply(long amount) {
    if (!ready() || !accumulator()) return 0;
    long n = battery.fill(now(), amount, false);
    if (n > 0) changed();
    return n;
  }

  public void endSupplyTick() {
    if (panel()) solarOffer = 0;
  }

  public final IEnergyStorage energy =
      new IEnergyStorage() {
        public int receiveEnergy(int n, boolean sim) {
          if (!ready() || !accumulator()) return 0;
          long whole = battery.fill(now(), Math.max(0L, n) * 15, true) / 15;
          if (!sim && whole > 0) {
            battery.fill(now(), whole * 15, false);
            changed();
          }
          return (int) whole;
        }

        public int extractEnergy(int n, boolean sim) {
          long whole = Math.min(Math.max(0L, n), supplyAvailable() / 15);
          if (!sim && whole > 0) takeSupply(whole * 15);
          return (int) whole;
        }

        public int getEnergyStored() {
          return ready()
              ? (int) ((accumulator() ? battery.units() : turbine() ? stock : solarOffer) / 15)
              : 0;
        }

        public int getMaxEnergyStored() {
          return accumulator() ? 13333 : turbine() ? 16000 : 8;
        }

        public boolean canReceive() {
          return ready() && accumulator();
        }

        public boolean canExtract() {
          return ready();
        }
      };

  public IEnergyStorage energyPort() {
    return energy;
  }

  public void tick() {
    if (!ready()) return;
    lease();
    boolean moving = false;
    if (panel()) {
      var d = level.dimensionType();
      if (skyAt == Long.MIN_VALUE || now() - skyAt >= 20) {
        var p = assembly().profile();
        var probe = p.center(worldPosition, p.facing(getBlockState())).above(p.layout.height());
        sky = level.hasChunkAt(probe) && level.canSeeSky(probe);
        skyAt = now();
      }
      solarOffer =
          lastSolar =
              solar.generate(
                  now(),
                  level.getDayTime(),
                  sky && d.hasSkyLight() && !d.hasCeiling() && d.fixedTime().isEmpty());
      if (solarOffer > 0) changed();
    } else if (turbine() && productionTick != now()) {
      productionTick = now();
      pullFluids();
      if (accepts(0, steam.key())) {
        int enthalpy = steam.key().stack(1).getFluid() == HotSteam.STEAM.get() ? 388 : 120;
        int n = Math.min(30, Math.min(steam.amount(), (int) ((240000 - stock) / enthalpy)));
        if (n > 0) moving = true;
        if (n > 0)
          FluidCell.commitBatch(
              java.util.List.of(new FluidCell.Mutation<>(steam, steam.key(), steam.amount() - n)),
              () -> stock += (long) n * enthalpy,
              false);
      }
      pushFluids();
    }
    active(panel() ? solarOffer > 0 : accumulator() ? battery.units() > 0 : moving);
    syncPeriodic();
  }

  public int[] metrics() {
    return new int[] {
      (int) (accumulator() ? battery.units() : turbine() ? stock : lastSolar),
      accumulator() ? 200000 : turbine() ? 240000 : 120,
      turbine() ? 500000 : 15000,
      0,
      0,
      ready() ? 1 : 0,
      0,
      steam.amount()
    };
  }

  protected void saveAdditional(CompoundTag n, HolderLookup.Provider r) {
    super.saveAdditional(n, r);
    n.putLong("Stock", stock);
    n.putLong("Charge", battery.units());
    n.putLong("ChargeTick", battery.leaseTick());
    n.putInt("ChargeUsed", battery.used());
    n.putInt("ChargeDirection", battery.direction());
    n.putInt("SolarRemainder", solar.remainder());
    n.putLong("SolarTick", solar.lastTick());
    if (steam.amount() > 0) n.put("Steam", steam.key().stack(steam.amount()).save(r));
    // Tick leases are persisted with the owning device to prevent same-tick packing exploits.
    n.putLong("ProductionTick", productionTick);
    n.putLong("OfferTick", offerTick);
    n.putInt("OutputUsed", outputUsed);
  }

  protected void loadAdditional(CompoundTag n, HolderLookup.Provider r) {
    super.loadAdditional(n, r);
    stock = n.getLong("Stock");
    if (stock < 0 || stock > 240000) throw new IllegalArgumentException("Turbine charge");
    battery.restore(
        n.getLong("Charge"),
        n.getLong("ChargeTick"),
        n.getInt("ChargeUsed"),
        n.getInt("ChargeDirection"));
    solar.restore(
        n.getInt("SolarRemainder"),
        n.contains("SolarTick") ? n.getLong("SolarTick") : Long.MIN_VALUE);
    productionTick = n.contains("ProductionTick") ? n.getLong("ProductionTick") : Long.MIN_VALUE;
    offerTick = n.contains("OfferTick") ? n.getLong("OfferTick") : Long.MIN_VALUE;
    outputUsed = n.getInt("OutputUsed");
    if (outputUsed < 0 || outputUsed > EnergyUnits.TURBINE_PER_TICK)
      throw new IllegalArgumentException("Turbine lease");
    var f = FluidStack.parseOptional(r, n.getCompound("Steam"));
    if (!f.isEmpty() && (f.getAmount() > 2000 || !accepts(0, new FluidKey(f))))
      throw new IllegalArgumentException("Hot steam");
    steam.restore(f.isEmpty() ? null : new FluidKey(f), f.getAmount());
  }
}
