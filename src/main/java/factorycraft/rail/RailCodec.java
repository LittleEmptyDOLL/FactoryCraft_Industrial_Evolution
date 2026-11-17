package factorycraft.rail;

import factorycraft.simulation.rail.*;
import java.util.*;
import net.minecraft.nbt.*;

public final class RailCodec {
  public static CompoundTag end(RailEnd e) {
    var n = new CompoundTag();
    n.putIntArray("P", new int[] {e.x(), e.y(), e.z(), e.heading().index()});
    return n;
  }

  public static RailEnd end(CompoundTag n) {
    var a = n.getIntArray("P");
    if (a.length != 4) throw new IllegalArgumentException("rail endpoint");
    return new RailEnd(a[0], a[1], a[2], new RailHeading(a[3]));
  }

  public static CompoundTag curve(RailCurve c) {
    var n = new CompoundTag();
    n.put("A", end(c.start));
    n.put("B", end(c.end));
    n.putDouble("H", c.handle);
    return n;
  }

  public static RailCurve curve(CompoundTag n) {
    return new RailCurve(end(n.getCompound("A")), end(n.getCompound("B")), n.getDouble("H"));
  }

  public static ListTag faces(List<RailMesh.Face> faces) {
    var out = new ListTag();
    for (var f : faces) {
      var n = new CompoundTag();
      n.putInt("M", f.material());
      var coords = new ListTag();
      for (var p : f.vertices()) {
        coords.add(DoubleTag.valueOf(p.x()));
        coords.add(DoubleTag.valueOf(p.y()));
        coords.add(DoubleTag.valueOf(p.z()));
      }
      n.put("V", coords);
      out.add(n);
    }
    return out;
  }

  public static List<RailMesh.Face> faces(ListTag tags) {
    if (tags.size() > 4096) throw new IllegalArgumentException("Rail mesh too large");
    var out = new ArrayList<RailMesh.Face>();
    for (var raw : tags) {
      var n = (CompoundTag) raw;
      var coords = n.getList("V", Tag.TAG_DOUBLE);
      if (coords.size() < 9
          || coords.size() > 48
          || coords.size() % 3 != 0
          || n.getInt("M") < 0
          || n.getInt("M") > 2) throw new IllegalArgumentException("Rail face");
      var vertices = new ArrayList<RailVector>();
      for (int i = 0; i < coords.size(); i += 3) {
        var p =
            new RailVector(coords.getDouble(i), coords.getDouble(i + 1), coords.getDouble(i + 2));
        if (p.x() < -.001
            || p.x() > 1.001
            || p.y() < -.001
            || p.y() > 1.001
            || p.z() < -.001
            || p.z() > 1.001) throw new IllegalArgumentException("Rail face bounds");
        vertices.add(p);
      }
      out.add(new RailMesh.Face(vertices, n.getInt("M")));
    }
    return List.copyOf(out);
  }

  private RailCodec() {}
}
