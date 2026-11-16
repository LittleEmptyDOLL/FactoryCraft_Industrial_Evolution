package factorycraft.simulation.research;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Paid, exact fixed-point lab work. Only the shared ledger callbacks may complete a unit. */
public final class LabWork {
  public static final long UNIT = 100_000_000L;
  public static final int MAX_DEFINITIONS = 1024;

  public record Partial(long work, long productivityRemainder) {
    public Partial {
      if (work < 0
          || work > 72000L * UNIT
          || productivityRemainder < 0
          || productivityRemainder >= 72000L * 10000)
        throw new IllegalArgumentException("Invalid laboratory partial work");
    }
  }

  private final Map<String, Partial> partials = new HashMap<>();
  private long bonus;

  public boolean canTrack(String key) {
    return partials.containsKey(key) || partials.size() < MAX_DEFINITIONS;
  }

  public long bonus() {
    return bonus;
  }

  public Map<String, Partial> partials() {
    return Map.copyOf(partials);
  }

  public Partial partial(String key) {
    return partials.getOrDefault(key, new Partial(0, 0));
  }

  public static int researchSpeed(ResearchLedger ledger) {
    int total = 10000;
    int[] increments = {2000, 3000, 4000, 5000, 5000, 6000};
    for (int i = 0; i < increments.length; i++)
      if (ledger.level("factorycraft:lab_research_speed_" + (i + 1)) > 0) total += increments[i];
    return total;
  }

  public void restore(long bonus, Map<String, Partial> values) {
    if (bonus < 0 || bonus > 1_000_000 * UNIT || values.size() > MAX_DEFINITIONS)
      throw new IllegalArgumentException("Invalid laboratory state");
    this.bonus = bonus;
    partials.clear();
    partials.putAll(values);
  }

  /** Module and research factors multiply; all terms and the unit denominator are integral. */
  public void advance(
      String key,
      int duration,
      int moduleSpeed,
      int researchSpeed,
      int productivity,
      BooleanSupplier eligible,
      BooleanSupplier baseUnit,
      BooleanSupplier bonusUnit) {
    if (duration < 1
        || duration > 72000
        || moduleSpeed < 2000
        || moduleSpeed > factorycraft.simulation.modules.ModuleEffects.MAX_SPEED
        || researchSpeed < 10000
        || researchSpeed > 35000
        || productivity < 0
        || productivity > 10000) throw new IllegalArgumentException("Invalid laboratory work");
    if (!partials.containsKey(key) && partials.size() >= MAX_DEFINITIONS)
      throw new IllegalStateException("Laboratory definition limit");
    var old = partial(key);
    long work = old.work(), remainder = old.productivityRemainder();
    long limit = duration * UNIT, denominator = duration * 10000L;
    if (work >= limit || remainder >= denominator)
      throw new IllegalArgumentException("Changed definition must use a new signature");
    long budget = (long) moduleSpeed * researchSpeed;
    while (budget > 0 && eligible.getAsBoolean()) {
      long delta = Math.min(budget, limit - work);
      work += delta;
      budget -= delta;
      long earned = delta * productivity + remainder;
      bonus += earned / denominator;
      remainder = earned % denominator;
      if (work == limit) {
        if (!baseUnit.getAsBoolean())
          throw new IllegalStateException("Research eligibility changed during commit");
        work = 0;
      }
      while (bonus >= UNIT && bonusUnit.getAsBoolean()) bonus -= UNIT;
    }
    partials.put(key, new Partial(work, remainder));
  }
}
