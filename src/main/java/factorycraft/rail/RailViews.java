package factorycraft.rail;

import factorycraft.simulation.rail.*;
import java.util.*;
import net.minecraft.world.level.Level;

/** Client hook is installed only by the client. Never links a renderer from dedicated code. */
public final class RailViews {
  public record Route(UUID id, RailCurve curve, RailFootprint footprint) {
    public Route(UUID id, RailCurve curve) {
      this(id, curve, new RailFootprint(curve));
    }
  }

  public static java.util.function.BiFunction<Level, RailFootprint, Collection<Route>> client =
      (l, f) -> List.of();

  public static Collection<Route> routes(Level l, RailFootprint f) {
    if (l instanceof net.minecraft.server.level.ServerLevel s) {
      var d = RailwayData.get(s);
      return d.candidates(f).stream()
          .map(d::segment)
          .map(x -> new Route(x.id, x.curve, x.footprint))
          .toList();
    }
    return client.apply(l, f);
  }

  private RailViews() {}
}
