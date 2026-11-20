package factorycraft.trains;

import factorycraft.simulation.rail.*;
import java.util.*;
import net.minecraft.nbt.*;

/** Bounded configuration-only codec; no item/fluid owners are serialized into an editor packet. */
public final class RailScheduleCodec {
  public static ListTag waits(List<RailWait.Condition> conditions) {
    var out = new ListTag();
    for (var condition : conditions) {
      var n = new CompoundTag();
      n.putString("Type", condition.type().name());
      n.putString("Filter", condition.filter());
      n.putString("Compare", condition.comparison().name());
      n.putLong("Value", condition.value());
      if (condition.or()) n.putBoolean("Or", true);
      out.add(n);
    }
    return out;
  }

  public static List<RailWait.Condition> waits(ListTag list) {
    if (list.size() > 32) throw new IllegalArgumentException("Wait condition count");
    var out = new ArrayList<RailWait.Condition>();
    for (var raw : list) {
      var n = (CompoundTag) raw;
      out.add(
          new RailWait.Condition(
              RailWait.Type.valueOf(n.getString("Type")),
              n.getString("Filter"),
              RailWait.Comparison.valueOf(n.getString("Compare")),
              n.getLong("Value"),
              n.getBoolean("Or")));
    }
    return List.copyOf(out);
  }

  public static CompoundTag save(RailSchedule schedule) {
    var n = new CompoundTag();
    var entries = new ListTag();
    var interrupts = new ListTag();
    for (var entry : schedule.entries()) {
      var e = new CompoundTag();
      e.putString("Station", entry.station());
      e.put("Wait", waits(entry.waits()));
      entries.add(e);
    }
    for (var id : schedule.interrupts()) {
      var e = new CompoundTag();
      e.putUUID("Id", id);
      interrupts.add(e);
    }
    n.put("Entries", entries);
    if (!interrupts.isEmpty()) n.put("Interrupts", interrupts);
    return n;
  }

  public static RailSchedule load(CompoundTag n) {
    var entries = n.getList("Entries", Tag.TAG_COMPOUND);
    var interrupts = n.getList("Interrupts", Tag.TAG_COMPOUND);
    if (entries.size() > 64 || interrupts.size() > 32)
      throw new IllegalArgumentException("Schedule size");
    var result = new ArrayList<RailSchedule.Entry>();
    var references = new ArrayList<UUID>();
    for (var raw : entries) {
      var e = (CompoundTag) raw;
      result.add(
          new RailSchedule.Entry(
              e.getString("Station"), waits(e.getList("Wait", Tag.TAG_COMPOUND))));
    }
    for (var raw : interrupts) references.add(((CompoundTag) raw).getUUID("Id"));
    return new RailSchedule(result, references);
  }

  public static CompoundTag save(RailInterrupt interrupt) {
    var n = new CompoundTag();
    n.putUUID("Id", interrupt.id());
    n.putString("Name", interrupt.name());
    n.put("When", waits(interrupt.conditions()));
    n.put("Targets", save(new RailSchedule(interrupt.targets(), List.of())));
    return n;
  }

  public static RailInterrupt interrupt(CompoundTag n) {
    return new RailInterrupt(
        n.getUUID("Id"),
        n.getString("Name"),
        waits(n.getList("When", Tag.TAG_COMPOUND)),
        load(n.getCompound("Targets")).entries());
  }

  private RailScheduleCodec() {}
}
