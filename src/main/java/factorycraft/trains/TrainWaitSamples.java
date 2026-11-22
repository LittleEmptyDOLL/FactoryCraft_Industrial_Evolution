package factorycraft.trains;

import factorycraft.simulation.rail.*;
import factorycraft.simulation.trains.StockSpec;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;

/** Nonowning native train-wide observations. Unavailable circuitry is unknown, never zero. */
public final class TrainWaitSamples {
  public static long threshold(RailWait.Condition c) {
    return c.type() == RailWait.Type.FLUID_COUNT ? Math.multiplyExact(c.value(), 10) : c.value();
  }

  public static OptionalLong sample(
      TrainData d, TrainData.Train t, RailAutomationData s, RailWait.Condition c) {
    var a = t.automation;
    return switch (c.type()) {
      case TIME -> OptionalLong.of(a.stationTicks);
      case INACTIVITY -> OptionalLong.of(a.idleTicks);
      case EMPTY, HAS_CARGO, ITEM_COUNT, FLUID_COUNT, FULL -> cargo(t, c);
      case ANY_FUEL, ALL_FUEL, FULL_FUEL -> fuel(t, c.type());
      case PASSENGER, NO_PASSENGER -> {
        boolean rider = false;
        for (var car : t.cars)
          if (d.level.getEntity(car.id) instanceof StockEntity stock)
            rider |=
                stock.getPassengers().stream()
                    .anyMatch(p -> p instanceof net.minecraft.world.entity.player.Player);
        yield bool(c.type() == RailWait.Type.PASSENGER ? rider : !rider);
      }
      case AT_STATION, NOT_AT_STATION -> {
        var stop = s.device(TrainAutomation.station(t, s));
        boolean at = stop != null && (c.filter().isEmpty() || stop.name.equals(c.filter()));
        yield bool(c.type() == RailWait.Type.AT_STATION ? at : !at);
      }
      case NO_PATH -> bool(a.phase == AutoTrainState.Phase.NO_PATH);
      case DESTINATION_FULL -> bool(a.phase == AutoTrainState.Phase.DESTINATION_FULL);
      case STATION_FULL, STATION_AVAILABLE -> {
        var named = s.slots.named(t.scope, c.filter());
        boolean free = named.stream().anyMatch(stop -> s.slots.available(stop.id(), t.id));
        yield bool(c.type() == RailWait.Type.STATION_AVAILABLE ? free : !free);
      }
      case CIRCUIT -> {
        var device = s.device(TrainAutomation.station(t, s));
        var root = device == null ? null : s.entity(device);
        var circuit = root == null ? null : root.circuit(false);
        if (circuit == null || !circuit.available() || !circuit.inputReady)
          yield OptionalLong.empty();
        try {
          var key = factorycraft.simulation.circuits.SignalKey.virtual(c.filter());
          yield OptionalLong.of(circuit.input.get(key));
        } catch (IllegalArgumentException malformedSignal) {
          yield OptionalLong.empty();
        }
      }
    };
  }

  private static OptionalLong cargo(TrainData.Train t, RailWait.Condition condition) {
    long items = 0, fluid = 0;
    boolean full = true, capacity = false;
    String filter =
        condition.type() == RailWait.Type.ITEM_COUNT
                || condition.type() == RailWait.Type.FLUID_COUNT
            ? condition.filter()
            : "";
    for (var car : t.cars) {
      if (car.kind == StockSpec.CARGO) {
        for (int slot = 0; slot < car.bar; slot++) {
          capacity = true;
          var stack = car.items.getStackInSlot(slot);
          if (filter.isEmpty()
              || BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(filter))
            items += stack.getCount();
          if (stack.isEmpty()
              || stack.getCount() < Math.min(stack.getMaxStackSize(), car.items.getSlotLimit(slot)))
            full = false;
        }
      } else if (car.kind == StockSpec.FLUID) {
        capacity = true;
        if (car.fluid.amount() < car.fluid.capacity()) full = false;
        if (car.fluid.amount() > 0
            && (filter.isEmpty()
                || BuiltInRegistries.FLUID
                    .getKey(car.fluid.key().stack(1).getFluid())
                    .toString()
                    .equals(filter))) fluid += car.fluid.amount();
      }
    }
    return switch (condition.type()) {
      case EMPTY -> bool(items == 0 && fluid == 0);
      case HAS_CARGO -> bool(items > 0 || fluid > 0);
      case FULL -> bool(capacity && full);
      case ITEM_COUNT -> OptionalLong.of(items);
      // Compare native mB to a threshold multiplied by ten; never truncate a fractional fluid unit.
      case FLUID_COUNT -> OptionalLong.of(fluid);
      default -> OptionalLong.empty();
    };
  }

  private static OptionalLong fuel(TrainData.Train t, RailWait.Type type) {
    int locomotives = 0, ready = 0, full = 0;
    for (var car : t.cars)
      if (car.kind == StockSpec.LOCOMOTIVE) {
        locomotives++;
        boolean powered = car.fuelJ > 0, filled = true;
        for (int i = 0; i < 3; i++) {
          var stack = car.items.getStackInSlot(i);
          powered |= TrainFuel.value(stack) != null;
          filled &=
              !stack.isEmpty()
                  && TrainFuel.value(stack) != null
                  && stack.getCount() == stack.getMaxStackSize();
        }
        if (powered) ready++;
        if (filled) full++;
      }
    return bool(
        locomotives > 0
            && switch (type) {
              case ANY_FUEL -> ready > 0;
              case ALL_FUEL -> ready == locomotives;
              default -> full == locomotives;
            });
  }

  private static OptionalLong bool(boolean value) {
    return OptionalLong.of(value ? 1 : 0);
  }

  private TrainWaitSamples() {}
}
