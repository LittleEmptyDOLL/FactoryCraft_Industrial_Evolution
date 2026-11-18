package factorycraft.simulation.combat;

import factorycraft.simulation.EnergyBuffer;

/** Exact 3.2 FE active idle billing without consuming the 801/800-kJ shot headroom first. */
public final class LaserStandby {
  private int remainder, debt;

  public boolean begin(EnergyBuffer power) {
    if (power.getEnergyStored() == 0 || debt > 0 && !power.spend(debt)) return false;
    debt = 0;
    return true;
  }

  public void finish(EnergyBuffer power) {
    int owed = (remainder + 16) / 5;
    remainder = (remainder + 16) % 5;
    int paid = Math.min(owed, power.getEnergyStored());
    power.spend(paid);
    debt = owed - paid;
  }

  public int remainder() {
    return remainder;
  }

  public int debt() {
    return debt;
  }

  public void restore(int remainder, int debt) {
    if (remainder < 0 || remainder > 4 || debt < 0 || debt > 4)
      throw new IllegalArgumentException("Invalid laser standby ledger");
    this.remainder = remainder;
    this.debt = debt;
  }
}
