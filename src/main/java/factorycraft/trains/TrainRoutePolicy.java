package factorycraft.trains;

import factorycraft.simulation.rail.*;
import java.util.*;
import net.minecraft.world.entity.player.Player;

/** Indexed, loaded-only route pricing. Hard safety remains in the motion scout/claims. */
final class TrainRoutePolicy implements RailPathSearch.Rules {
  private final TrainData data;
  private final TrainData.Train train;
  private final RailAutomationData system;

  TrainRoutePolicy(TrainData data, TrainData.Train train, RailAutomationData system) {
    this.data = data;
    this.train = train;
    this.system = system;
  }

  public double penalty(RailGraph.Directed travel, double from, double to) {
    return price(travel, from, to, RailPathSearch.Progress.START, null).penalty();
  }

  public RailPathSearch.Price price(
      RailGraph.Directed travel,
      double from,
      double to,
      RailPathSearch.Progress progress,
      UUID destination) {
    if (!system.rails.available(travel.edge())
        || !system.signals.directionAllowed(travel, from, to))
      return new RailPathSearch.Price(Double.POSITIVE_INFINITY, progress);
    double cost = 0;
    var previous = progress.region();
    int ordinal = progress.blocks();
    var spans = system.blocks.spans(travel.edge());
    if (spans.isEmpty() && Math.abs(to - from) > 1e-8)
      return new RailPathSearch.Price(Double.POSITIVE_INFINITY, progress);
    for (int n = 0; n < spans.size(); n++) {
      var span = spans.get(travel.reverse() ? spans.size() - 1 - n : n);
      if (span.to() <= Math.min(from, to) + 1e-8 || span.from() >= Math.max(from, to) - 1e-8)
        continue;
      var region = system.blocks.at(span.edge(), (span.from() + span.to()) / 2, true);
      if (region == null) return new RailPathSearch.Price(Double.POSITIVE_INFINITY, progress);
      if (region.id.equals(previous)) continue;
      previous = region.id;
      ordinal++;
      var occupants = system.claims.occupants(region.id);
      boolean occupied = false;
      for (var id : occupants) {
        if (id.equals(train.id)) continue;
        occupied = true;
        var other = data.train(id);
        if (other == null) return new RailPathSearch.Price(Double.POSITIVE_INFINITY, progress);
        cost += RailRoutePenalties.train(obstruction(other), other.automation.signalWaitTicks);
      }
      if (occupied) cost += RailRoutePenalties.occupied(region.length, ordinal);
      // A prospective lease is not an actual train body and has no source occupied-block cost.
    }
    for (var point : system.points.ahead(travel, from, to)) {
      if (point.reverse() != travel.reverse()) continue;
      if (!system.online(point))
        return new RailPathSearch.Price(Double.POSITIVE_INFINITY, progress);
      if (point.boundary()) {
        if (system.forced(point)) cost += RailRoutePenalties.CIRCUIT_RED;
      } else if (!point.id().equals(destination)) cost += RailRoutePenalties.INTERMEDIATE_STOP;
    }
    return new RailPathSearch.Price(cost, new RailPathSearch.Progress(previous, ordinal));
  }

  private RailRoutePenalties.Obstruction obstruction(TrainData.Train other) {
    var a = other.automation;
    if (!a.automatic) {
      if (other.speed != 0) return RailRoutePenalties.Obstruction.MOVING;
      boolean passenger = false;
      for (var car : other.cars)
        if (data.level.getEntity(car.id) instanceof StockEntity stock)
          passenger |= stock.getPassengers().stream().anyMatch(p -> p instanceof Player);
      return passenger
          ? RailRoutePenalties.Obstruction.MANUAL_PASSENGER
          : RailRoutePenalties.Obstruction.MANUAL_EMPTY;
    }
    if (a.schedule.entries().isEmpty()) return RailRoutePenalties.Obstruction.EMPTY_SCHEDULE;
    if (a.arrived && other.speed == 0) {
      String current =
          a.schedule.entries().get(Math.min(a.entry, a.schedule.entries().size() - 1)).station();
      boolean another = a.schedule.entries().stream().anyMatch(e -> !e.station().equals(current));
      return another
          ? RailRoutePenalties.Obstruction.STATION
          : RailRoutePenalties.Obstruction.TERMINAL_STATION;
    }
    if (a.phase == AutoTrainState.Phase.NO_PATH) return RailRoutePenalties.Obstruction.NO_PATH;
    if (a.phase == AutoTrainState.Phase.SIGNAL_WAIT)
      return other.speed == 0
          ? RailRoutePenalties.Obstruction.SIGNAL_WAIT
          : RailRoutePenalties.Obstruction.ARRIVING;
    if (other.status == TrainData.Status.BRAKING) return RailRoutePenalties.Obstruction.ARRIVING;
    return RailRoutePenalties.Obstruction.MOVING;
  }
}
