package factorycraft.trains;

import factorycraft.circuits.*;
import factorycraft.rail.automation.*;
import factorycraft.simulation.circuits.*;
import factorycraft.simulation.trains.StockSpec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;

/** Nonowning rail I/O. Four existing buses and unknown availability semantics are unchanged. */
public final class RailCircuitAdapter {
  public static void changed(TracksideBlockEntity root) {
    if (root.getLevel() instanceof ServerLevel l) {
      var data = RailAutomationData.peek(l);
      if (data != null) data.circuitChanged(root.identity);
    }
  }

  public static CircuitDevices.Reading read(TracksideBlockEntity root, CircuitAttachment circuit) {
    if (!root.ready() || !(root.getLevel() instanceof ServerLevel l))
      return new CircuitDevices.Reading(false, SignalSet.EMPTY);
    var data = RailAutomationData.get(l);
    var d = data.device(root.identity);
    var out = new SignalSet.Builder();
    if (d.kind != TracksideKind.STOP) {
      if (!data.runtimeReady()) return new CircuitDevices.Reading(false, SignalSet.EMPTY);
      if (circuit.settings().state()) {
        var aspect = data.aspect(d);
        if (aspect == factorycraft.simulation.rail.RailSignals.Aspect.INVALID)
          return new CircuitDevices.Reading(false, SignalSet.EMPTY);
        out.add(SignalKey.virtual("signal_" + aspect.name().toLowerCase(java.util.Locale.ROOT)), 1);
      }
    } else {
      if (circuit.settings().state())
        out.add(SignalKey.virtual("signal_c"), data.slots.count(d.id));
      // Commitments are already indexed by station; avoid scanning unrelated cargo owners.
      for (var trainId : data.slots.trains(d.id)) {
        var t = TrainData.get(l).train(trainId);
        if (t == null || !t.scope.equals(d.scope) || !d.id.equals(TrainAutomation.station(t, data)))
          continue;
        if (!TrainData.get(l).stopped(t)) return new CircuitDevices.Reading(false, SignalSet.EMPTY);
        if (circuit.settings().state()) out.add(SignalKey.virtual("train_id"), data.trainNumber(t));
        if (circuit.settings().contents())
          for (var car : t.cars) {
            if (car.kind == StockSpec.CARGO)
              for (int i = 0; i < car.items.getSlots(); i++) {
                var stack = car.items.getStackInSlot(i);
                if (!stack.isEmpty())
                  out.add(
                      SignalKey.item(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()),
                      stack.getCount());
              }
            else if (car.kind == StockSpec.FLUID && car.fluid.amount() > 0)
              out.add(
                  SignalKey.fluid(
                      BuiltInRegistries.FLUID
                          .getKey(car.fluid.key().stack(1).getFluid())
                          .toString()),
                  car.fluid.amount());
          }
      }
    }
    return new CircuitDevices.Reading(true, out.build());
  }

  private RailCircuitAdapter() {}
}
