package factorycraft.simulation.rail;

import java.util.*;

/**
 * Pure connection choices shared by server placement, client ghosts and endpoint hints. Endpoints
 * are real saved geometry; reversing an axis never invents a node in the track.
 */
public final class RailConnectionResolver {
  public record Source(RailEnd selected, RailEnd other, int side) {
    public Source {
      if (selected == null || other == null || side < 0 || side > 2)
        throw new IllegalArgumentException("endpoint");
    }
  }

  public record Request(Source source, List<RailEnd> destinations, boolean fixedDirection) {
    public Request {
      destinations = List.copyOf(destinations);
      if (destinations.isEmpty() || destinations.size() > 16)
        throw new IllegalArgumentException("endpoint");
    }
  }

  public record Result(RailEnd start, RailEnd end, RailCurve curve, String reason) {
    public boolean valid() {
      return curve != null;
    }
  }

  public static Result resolve(Request request) {
    var source = request.source();
    Result rejected = null;
    for (var rawEnd : request.destinations()) {
      var starts = new ArrayList<RailEnd>(2);
      if (source.side() != 2) starts.add(source.selected());
      if (source.side() != 1) starts.add(source.other());
      if (source.side() == 0)
        starts.sort(
            Comparator.<RailEnd>comparingDouble(s -> -alignment(s, rawEnd))
                .thenComparingDouble(s -> s.point().distance(rawEnd.point()))
                .thenComparing(s -> s.equals(source.selected()) ? 0 : 1));
      for (var start : starts) {
        var end = rawEnd;
        if (!request.fixedDirection()
            && end.heading().vector().dot(end.point().subtract(start.point())) < 0)
          end = end.reverse();
        if (rejected == null)
          rejected =
              new Result(
                  start, end, null, start.samePosition(end) ? "endpoint" : "source_direction");
        if (start.samePosition(end) || alignment(start, end) <= 1e-8) continue;
        try {
          return new Result(start, end, RailCurve.plan(start, end), "ready");
        } catch (IllegalArgumentException bad) {
          if (rejected.reason().equals("endpoint") || rejected.reason().equals("source_direction"))
            rejected = new Result(start, end, null, bad.getMessage());
        }
      }
    }
    return rejected;
  }

  private static double alignment(RailEnd start, RailEnd end) {
    var delta = end.point().subtract(start.point());
    return delta.length() < 1e-8 ? -2 : start.heading().vector().dot(delta.unit());
  }

  private RailConnectionResolver() {}
}
