package factorycraft.simulation.thermal;

import factorycraft.simulation.fluids.FluidCell;
import java.util.Objects;

/** Finite steam-to-energy conversion. Energy is owned before the fluid observer can run. */
public final class SteamEngine<K> {
  public static final int CAPACITY = 16000, FE_NUMERATOR = 6, MB_DENOMINATOR = 5, MB_PER_TICK = 100;
  private final FluidCell<K> steam;
  private final Runnable changed;
  private int energy, residue;

  public int residue() {
    return residue;
  }

  public SteamEngine(FluidCell<K> steam, Runnable changed) {
    this.steam = Objects.requireNonNull(steam);
    this.changed = Objects.requireNonNull(changed);
  }

  public int energy() {
    return energy;
  }

  public void restore(int energy) {
    restore(energy, 0);
  }

  public void restore(int energy, int residue) {
    if (residue < 0 || residue >= MB_DENOMINATOR)
      throw new IllegalArgumentException("Invalid steam residue");
    this.residue = residue;
    if (energy < 0) throw new IllegalArgumentException("Negative energy");
    this.energy = energy;
  }

  public int generate(boolean validSteam) {
    if (!validSteam) return 0;
    int amount =
        Math.min(
            MB_PER_TICK,
            Math.min(
                steam.amount(),
                (int)
                    Math.min(
                        Integer.MAX_VALUE,
                        (Math.max(0L, (long) CAPACITY - energy) * MB_DENOMINATOR
                                + MB_DENOMINATOR
                                - 1
                                - residue)
                            / FE_NUMERATOR)));
    if (amount == 0) return 0;
    int units = amount * FE_NUMERATOR + residue, made = units / MB_DENOMINATOR;
    residue = units % MB_DENOMINATOR;
    energy += made;
    steam.drain(amount, false);
    return made;
  }

  public int extract(int requested, boolean simulate) {
    int accepted = Math.min(Math.max(0, requested), energy);
    if (accepted > 0 && !simulate) {
      energy -= accepted;
      changed.run();
    }
    return accepted;
  }
}
