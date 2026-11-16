package factorycraft.simulation.thermal;

import factorycraft.simulation.fluids.FluidCell;

/** Progress and unspent fuel heat are persistent; a blocked recipe never burns another tick. */
public final class BoilerProcess {
  private int fuel, progress;
  private String recipe = "";

  public int fuel() {
    return fuel;
  }

  public int progress() {
    return progress;
  }

  public String recipe() {
    return recipe;
  }

  public void restore(int fuel, int progress, String recipe) {
    if (fuel < 0 || progress < 0 || recipe == null)
      throw new IllegalArgumentException("Invalid boiler state");
    this.fuel = fuel;
    this.progress = progress;
    this.recipe = recipe;
  }

  public void refuel(int ticks) {
    if (ticks < 1 || fuel > Integer.MAX_VALUE - ticks)
      throw new IllegalArgumentException("Invalid refuel");
    fuel += ticks;
  }

  public <K> boolean advance(
      String identity,
      int ticks,
      FluidCell<K> input,
      int amount,
      FluidCell<K> output,
      K result,
      int produced) {
    return advance(identity, ticks, input, amount, output, result, produced, 1);
  }

  public <K> boolean advance(
      String identity,
      int ticks,
      FluidCell<K> input,
      int amount,
      FluidCell<K> output,
      K result,
      int produced,
      int burnCost) {
    if (burnCost < 1 || ticks < 1 || amount < 1 || produced < 1)
      throw new IllegalArgumentException("Invalid conversion");
    if (fuel < burnCost
        || input.amount() < amount
        || output.space() < produced
        || !output.accepts(result)) return false;
    if (!recipe.equals(identity)) {
      recipe = identity;
      progress = 0;
    }
    fuel -= burnCost;
    progress++;
    if (progress >= ticks) {
      progress = 0;
      recipe = "";
      if (!input.convertTo(output, amount, result, produced, false))
        throw new IllegalStateException("Validated conversion failed");
    }
    return true;
  }
}
