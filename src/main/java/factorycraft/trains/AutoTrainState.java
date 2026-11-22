package factorycraft.trains;

import factorycraft.simulation.rail.*;
import java.util.*;
import net.minecraft.nbt.*;

/** Configuration/runtime metadata only, attached to the sole TrainData resource owner. */
public final class AutoTrainState {
  public enum Phase {
    MANUAL,
    DISPATCHING,
    DRIVING,
    SIGNAL_WAIT,
    STATION_WAIT,
    NO_PATH,
    DESTINATION_FULL,
    NO_FUEL,
    UNLOADED,
    ROUTE_LIMIT,
    INVALID_CONFIG
  }

  public boolean automatic, routed, arrived;
  public RailSchedule schedule = RailSchedule.EMPTY;
  public String group = "";
  public int entry, stationTicks, idleTicks, temporaryEntry, returnEntry;
  public UUID destination, activeInterrupt;
  public UUID departure;
  public double departureHead;
  public boolean departurePass;

  /** Prepared future stops; the installed TrainPath owns geometry, never duplicate resources. */
  public record Waypoint(int entry, UUID destination, double head) {}

  public final List<Waypoint> upcoming = new ArrayList<>();
  public int waypointDirection = 1;
  public transient TrainRouteJob continuationJob;
  public transient long continuationRetry;

  public double horizon() {
    return upcoming.isEmpty() ? targetHead : upcoming.getLast().head();
  }

  public int departureDirection = 1;
  public long dispatchOrder;
  public transient UUID dispatchTarget;
  public UUID heldSignal;

  /** Current scout refusal only; never another resource/reservation owner. */
  public transient UUID blockingSignal;

  public int signalWaitTicks;
  public transient boolean repathing;
  public transient long repathAt;

  public int number;
  public Set<UUID> reserved = Set.of();
  public RailInterrupt.Match binding;
  public double targetHead, reservationEnd;
  public Phase phase = Phase.MANUAL;
  public long lastCargo = Long.MIN_VALUE, checkedGraph = -1, checkedRules = -1, retryAt;
  public RailPathSearch searching;
  public TrainRouteJob routeJob;
  public int searchingDirection;
  public CompoundTag invalid;

  public boolean empty() {
    return number == 0
        && reserved.isEmpty()
        && !routed
        && !arrived
        && !automatic
        && schedule.equals(RailSchedule.EMPTY)
        && group.isEmpty()
        && entry == 0
        && destination == null
        && departure == null
        && upcoming.isEmpty()
        && !departurePass
        && dispatchOrder == 0
        && heldSignal == null
        && signalWaitTicks == 0
        && activeInterrupt == null
        && invalid == null;
  }

  public CompoundTag save() {
    if (invalid != null) return invalid.copy();
    var n = new CompoundTag();
    n.putInt("Schema", 1);
    n.putBoolean("Automatic", automatic);
    n.put("Schedule", RailScheduleCodec.save(schedule));
    if (!group.isEmpty()) n.putString("Group", group);
    if (number != 0) n.putInt("Number", number);
    if (!reserved.isEmpty()) {
      var claims = new ListTag();
      for (var id : new TreeSet<>(reserved)) {
        var claim = new CompoundTag();
        claim.putUUID("Block", id);
        claims.add(claim);
      }
      n.put("Reserved", claims);
    }
    n.putInt("Entry", entry);
    n.putBoolean("Routed", routed);
    n.putBoolean("Arrived", arrived);
    n.putDouble("ReservationEnd", reservationEnd);
    n.putDouble("TargetHead", targetHead);
    n.putInt("StationTicks", stationTicks);
    n.putInt("IdleTicks", idleTicks);
    if (destination != null) n.putUUID("Destination", destination);
    if (departure != null) {
      n.putUUID("Departure", departure);
      n.putDouble("DepartureHead", departureHead);
      n.putInt("DepartureDirection", departureDirection);
      if (departurePass) n.putBoolean("DeparturePass", true);
    }
    if (!upcoming.isEmpty()) {
      var rows = new ListTag();
      for (var w : upcoming) {
        var row = new CompoundTag();
        row.putInt("Entry", w.entry());
        row.putUUID("Destination", w.destination());
        row.putDouble("Head", w.head());
        rows.add(row);
      }
      n.put("Upcoming", rows);
      n.putInt("WaypointDirection", waypointDirection);
    }
    if (dispatchOrder != 0) n.putLong("DispatchOrder", dispatchOrder);
    if (heldSignal != null) n.putUUID("HeldSignal", heldSignal);
    if (signalWaitTicks != 0) n.putInt("SignalWaitTicks", signalWaitTicks);
    if (activeInterrupt != null) {
      n.putUUID("ActiveInterrupt", activeInterrupt);
      n.putInt("TemporaryEntry", temporaryEntry);
      n.putInt("ReturnEntry", returnEntry);
      if (binding != null) {
        n.putString("WildcardType", binding.type().name());
        n.putString("WildcardId", binding.id());
      }
    }
    n.putString("Phase", phase.name());
    return n;
  }

  public static AutoTrainState load(CompoundTag n) {
    var state = new AutoTrainState();
    try {
      if (n.getInt("Schema") != 1) throw new IllegalArgumentException("Automatic train schema");
      state.automatic = n.getBoolean("Automatic");
      state.schedule = RailScheduleCodec.load(n.getCompound("Schedule"));
      state.group = n.getString("Group");
      state.entry = n.getInt("Entry");
      state.number = n.getInt("Number");
      var claims = n.getList("Reserved", Tag.TAG_COMPOUND);
      if (claims.size() > 4096) throw new IllegalArgumentException("Block claims");
      var ids = new HashSet<UUID>();
      for (var raw : claims)
        if (!ids.add(((CompoundTag) raw).getUUID("Block")))
          throw new IllegalArgumentException("Duplicate block claim");
      state.reserved = Set.copyOf(ids);
      state.routed = n.getBoolean("Routed");
      state.arrived = n.getBoolean("Arrived");
      state.reservationEnd = n.getDouble("ReservationEnd");
      state.targetHead = n.getDouble("TargetHead");
      state.stationTicks = n.getInt("StationTicks");
      state.idleTicks = n.getInt("IdleTicks");
      if (n.hasUUID("Destination")) state.destination = n.getUUID("Destination");
      if (n.hasUUID("Departure")) {
        state.departure = n.getUUID("Departure");
        state.departureHead = n.getDouble("DepartureHead");
        state.departureDirection = n.getInt("DepartureDirection");
        state.departurePass = n.getBoolean("DeparturePass");
      }
      state.dispatchOrder = n.getLong("DispatchOrder");
      if (n.hasUUID("HeldSignal")) state.heldSignal = n.getUUID("HeldSignal");
      state.signalWaitTicks = n.getInt("SignalWaitTicks");
      if (n.hasUUID("ActiveInterrupt")) {
        state.activeInterrupt = n.getUUID("ActiveInterrupt");
        state.temporaryEntry = n.getInt("TemporaryEntry");
        state.returnEntry = n.getInt("ReturnEntry");
        if (n.contains("WildcardType"))
          state.binding =
              new RailInterrupt.Match(
                  RailInterrupt.Type.valueOf(n.getString("WildcardType")),
                  n.getString("WildcardId"));
      }
      var upcoming = n.getList("Upcoming", Tag.TAG_COMPOUND);
      if (upcoming.size() > 64) throw new IllegalArgumentException("Waypoint window");
      if (!upcoming.isEmpty()) {
        state.waypointDirection = n.getInt("WaypointDirection");
        if (Math.abs(state.waypointDirection) != 1
            || !state.automatic
            || !state.routed
            || state.arrived
            || state.entry >= state.schedule.entries().size())
          throw new IllegalArgumentException("Waypoint state");
        int entry = state.entry;
        double head = state.targetHead;
        for (var raw : upcoming) {
          var row = (CompoundTag) raw;
          if (!state.schedule.entries().get(entry).waits().isEmpty())
            throw new IllegalArgumentException("Waypoint after wait");
          entry = (entry + 1) % state.schedule.entries().size();
          double next = row.getDouble("Head");
          if (row.getInt("Entry") != entry
              || !Double.isFinite(next)
              || (next - head) * state.waypointDirection < .001)
            throw new IllegalArgumentException("Waypoint order");
          state.upcoming.add(new Waypoint(entry, row.getUUID("Destination"), next));
          head = next;
        }
      }
      state.phase = Phase.valueOf(n.getString("Phase"));
      if (state.number < 0
          || state.group.length() > 128
          || state.entry < 0
          || state.entry >= 64
          || state.stationTicks < 0
          || state.idleTicks < 0
          || state.signalWaitTicks < 0
          || state.dispatchOrder < 0
          || state.dispatchOrder == Long.MAX_VALUE
          || state.departure != null
              && (!state.automatic
                  || !Double.isFinite(state.departureHead)
                  || Math.abs(state.departureDirection) != 1)
          || !Double.isFinite(state.targetHead + state.reservationEnd)
          || state.temporaryEntry < 0
          || state.temporaryEntry >= 16
          || state.returnEntry < 0
          || state.returnEntry >= 64
          || state.routed && state.destination == null)
        throw new IllegalArgumentException("Automatic runtime bounds");
    } catch (RuntimeException malformed) {
      state = new AutoTrainState();
      state.invalid = n.copy();
      state.phase = Phase.INVALID_CONFIG;
    }
    return state;
  }
}
