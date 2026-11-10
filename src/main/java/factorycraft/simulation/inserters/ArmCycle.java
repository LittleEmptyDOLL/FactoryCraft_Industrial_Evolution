package factorycraft.simulation.inserters;

import factorycraft.simulation.EnergyBuffer;
import java.util.function.IntPredicate;

/** 60-Hz paid movement units. Three units per Minecraft tick, including residual leg time. */
public final class ArmCycle {
  public record Spec(
      int reach, int units, int fe, int capacity, boolean burner, boolean bulk, boolean stack) {
    public Spec {
      if (reach < 1
          || reach > 2
          || units < 6
          || units % 2 != 0
          || fe < 1
          || capacity < 1
          || capacity > 16) throw new IllegalArgumentException("Invalid arm spec");
    }

    public int swing() {
      return units / 2;
    }

    public double ticksPerCycle() {
      return units / 3d;
    }

    public int energyPerCycle() {
      return fe;
    }
  }

  // Relative cycle energy follows original 5/5/7/20/40 kJ movement/rotation ratios.
  public static final Spec BURNER = new Spec(1, 76, 700, 1, true, false, false),
      STANDARD = new Spec(1, 70, 70, 1, false, false, false),
      LONG = new Spec(2, 48, 70, 1, false, false, false),
      FAST = new Spec(1, 24, 98, 1, false, false, false),
      BULK = new Spec(1, 24, 280, 2, false, true, false),
      STACK = new Spec(1, 24, 560, 6, false, true, true);
  private final Spec spec;
  private int remaining, energyRemainder;

  public ArmCycle(Spec spec) {
    this.spec = spec;
  }

  public int remaining() {
    return remaining;
  }

  public int energyRemainder() {
    return energyRemainder;
  }

  public void restore(int remaining) {
    restore(remaining, 0);
  }

  public void restore(int remaining, int remainder) {
    this.remaining = Math.clamp(remaining, 0, spec.swing());
    energyRemainder = Math.clamp(remainder, 0, spec.units() - 1);
  }

  public void begin() {
    remaining = spec.swing();
  }

  /** Fractional energy is conserved across every movement, leg and restart. */
  public boolean payUnit(IntPredicate spend) {
    int next = energyRemainder + spec.fe();
    int debit = next / spec.units();
    if (!spend.test(debit)) return false;
    energyRemainder = next % spec.units();
    return true;
  }

  public boolean advanceUnit(IntPredicate spend) {
    if (remaining == 0 || !payUnit(spend)) return false;
    remaining--;
    return true;
  }

  public boolean advance(EnergyBuffer buffer) {
    return advanceUnit(buffer::spend);
  }
}
