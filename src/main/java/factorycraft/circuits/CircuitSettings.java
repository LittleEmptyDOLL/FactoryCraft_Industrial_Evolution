package factorycraft.circuits;

import factorycraft.simulation.circuits.*;
import java.util.*;
import net.minecraft.nbt.*;

/**
 * Persistent, independent I/O masks. Transient commands never overwrite manual machine settings.
 */
public record CircuitSettings(
    int inputColors,
    int outputColors,
    boolean contents,
    boolean state,
    boolean ingredients,
    boolean results,
    boolean researchInfo,
    boolean gate,
    List<SignalProgram.Condition> conditions,
    boolean filters,
    boolean hand,
    SignalKey handSignal,
    boolean recipe,
    boolean selectResearch,
    boolean pulse) {
  public static CircuitSettings defaults(boolean sensor) {
    return new CircuitSettings(
        15,
        15,
        sensor,
        false,
        false,
        false,
        false,
        false,
        List.of(),
        false,
        false,
        SignalKey.virtual("hand_size"),
        false,
        false,
        false);
  }

  public CircuitSettings {
    if (inputColors < 0
        || inputColors > 15
        || outputColors < 0
        || outputColors > 15
        || conditions.size() > 16)
      throw new IllegalArgumentException("Invalid circuit device settings");
    conditions = List.copyOf(conditions);
    Objects.requireNonNull(handSignal);
  }

  public CompoundTag save() {
    var t = new CompoundTag();
    t.putInt("InputColors", inputColors);
    t.putInt("OutputColors", outputColors);
    t.putBoolean("Contents", contents);
    t.putBoolean("State", state);
    t.putBoolean("Ingredients", ingredients);
    t.putBoolean("Results", results);
    t.putBoolean("Research", researchInfo);
    t.putBoolean("Gate", gate);
    t.put("Conditions", SignalProgramData.conditions(conditions));
    t.putBoolean("Filters", filters);
    t.putBoolean("Hand", hand);
    t.put("HandSignal", SignalProgramData.key(handSignal));
    t.putBoolean("Recipe", recipe);
    t.putBoolean("SelectResearch", selectResearch);
    t.putBoolean("Pulse", pulse);
    return t;
  }

  public static CircuitSettings load(CompoundTag t) {
    return new CircuitSettings(
        t.getInt("InputColors"),
        t.getInt("OutputColors"),
        t.getBoolean("Contents"),
        t.getBoolean("State"),
        t.getBoolean("Ingredients"),
        t.getBoolean("Results"),
        t.getBoolean("Research"),
        t.getBoolean("Gate"),
        SignalProgramData.conditions(t.getList("Conditions", 10)),
        t.getBoolean("Filters"),
        t.getBoolean("Hand"),
        SignalProgramData.key(t.getCompound("HandSignal")),
        t.getBoolean("Recipe"),
        t.getBoolean("SelectResearch"),
        t.getBoolean("Pulse"));
  }
}
