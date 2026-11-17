package factorycraft.rail;

import factorycraft.simulation.rail.*;
import java.util.UUID;

/**
 * Compact immutable nonowning edge projection. Client curves are cached once per edge, not per
 * part.
 */
public record RailDescriptor(UUID id, RailEnd a, RailEnd b, double handle) {
  public RailDescriptor(RailwayData.Segment s) {
    this(s.id, s.curve.start, s.curve.end, s.curve.handle);
  }

  public RailCurve curve() {
    return new RailCurve(a, b, handle);
  }
}
