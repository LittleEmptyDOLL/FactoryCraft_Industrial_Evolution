package factorycraft.diagnostics;

import factorycraft.blockentity.*;
import java.util.IdentityHashMap;
import net.minecraft.world.level.Level;

/**
 * Opt-in measurement only. No per-machine allocation or map lookup when diagnostics are disabled.
 */
public final class FactoryTelemetry {
  public static final class Counters {
    public long worked, produced, transferred, energySpent;
  }

  public static final class Window {
    public final Level level;
    public final IdentityHashMap<FactoryBlockEntity, Counters> entities = new IdentityHashMap<>();
    public long syncRequests;

    Window(Level level) {
      this.level = level;
    }

    Counters entity(FactoryBlockEntity be) {
      return entities.computeIfAbsent(be, k -> new Counters());
    }
  }

  private static Window active;

  public static Window begin(Level level) {
    return active = new Window(level);
  }

  public static Window end() {
    var result = active;
    active = null;
    return result;
  }

  public static void worked(MachineBlockEntity be, int energy) {
    var w = active;
    if (w != null && w.level == be.getLevel()) {
      var c = w.entity(be);
      c.worked++;
      c.energySpent += energy;
    }
  }

  public static void armWorked(InserterBlockEntity be, int energy) {
    var w = active;
    if (w != null && w.level == be.getLevel()) {
      var c = w.entity(be);
      c.worked++;
      c.energySpent += energy;
    }
  }

  public static void produced(MachineBlockEntity be) {
    var w = active;
    if (w != null && w.level == be.getLevel()) w.entity(be).produced++;
  }

  public static void transferred(InserterBlockEntity be, int count) {
    var w = active;
    if (w != null && w.level == be.getLevel()) w.entity(be).transferred += count;
  }

  public static void beltChanged(BeltBlockEntity be) {
    var w = active;
    if (w != null && w.level == be.getLevel()) w.entity(be).worked++;
  }

  public static void synced(FactoryBlockEntity be) {
    var w = active;
    if (w != null && w.level == be.getLevel()) w.syncRequests++;
  }

  private FactoryTelemetry() {}
}
