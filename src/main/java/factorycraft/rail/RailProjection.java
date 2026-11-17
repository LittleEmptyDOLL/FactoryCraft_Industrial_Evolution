package factorycraft.rail;

import factorycraft.simulation.rail.RailMesh;
import java.util.*;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Nonowning immutable per-cell mesh projection, safe for asynchronous chunk baking. */
public record RailProjection(List<RailMesh.Face> faces) {
  public RailProjection {
    faces = List.copyOf(faces);
  }

  public static final ModelProperty<RailProjection> MODEL = new ModelProperty<>();
}
