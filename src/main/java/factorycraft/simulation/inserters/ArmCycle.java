package factorycraft.simulation.inserters;

import factorycraft.simulation.EnergyBuffer;

/** Paid movement only; inventory ownership remains in the world adapter's single hand. */
public final class ArmCycle {
  public record Spec(int reach, int swing, int fe) {
    public Spec {
      if (reach < 1 || reach > 2 || swing < 1 || swing > 100 || fe < 0)
        throw new IllegalArgumentException("Invalid arm specification");
    }

    public int ticksPerCycle() {
      return 2 * (swing + 1);
    }

    public int energyPerCycle() {
      return ticksPerCycle() * fe;
    }
  }

  public static final Spec MECHANICAL = new Spec(1, 12, 0),
      FAST = new Spec(1, 3, 4),
      LONG = new Spec(2, 7, 3);
  private final Spec spec;
  private int remaining;

  public ArmCycle(Spec spec) {
    this.spec = spec;
  }

  public int remaining() {
    return remaining;
  }

  public void restore(int n) {
    remaining = Math.clamp(n, 0, spec.swing);
  }

  public void begin() {
    remaining = spec.swing;
  }

  public boolean pay(EnergyBuffer power) {
    return spec.fe == 0 || power.spend(spec.fe);
  }

  public boolean advance(EnergyBuffer power) {
    if (remaining == 0 || !pay(power)) return false;
    remaining--;
    return true;
  }
}
