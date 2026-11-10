package factorycraft.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import factorycraft.blockentity.PoleBlockEntity;
import factorycraft.energy.*;
import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.phys.*;

/** One cached sagging polyline per reciprocal copper edge; no global search or item entities. */
public final class PowerWireRenderer implements BlockEntityRenderer<PoleBlockEntity> {

  private static final class Cache {
    long revision = -1;
    final Map<PowerWires.Link, float[]> lines = new HashMap<>();
  }

  private final Map<PoleBlockEntity, Cache> geometry = new WeakHashMap<>();

  public PowerWireRenderer(BlockEntityRendererProvider.Context context) {}

  public AABB getRenderBoundingBox(PoleBlockEntity p) {
    return new AABB(p.getBlockPos()).inflate(p.spec().wire + 5);
  }

  public int getViewDistance() {
    return 96;
  }

  public void render(
      PoleBlockEntity pole,
      float partial,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      int overlay) {
    if (!pole.visualAvailable()) return;
    var record = geometry.computeIfAbsent(pole, p -> new Cache());
    if (record.revision != pole.wireRevision()) {
      record.revision = pole.wireRevision();
      record.lines.clear();
    }
    var cache = record.lines;
    for (var link : pole.links()) {
      if (pole.getBlockPos().compareTo(link.pos()) >= 0) continue;
      var other = PowerWires.at(pole.getLevel(), link.pos());
      if (other == null
          || !other.visualAvailable()
          || !other.assembly().owns(link.identity())
          || !other.hasLink(pole)) continue;
      var points = cache.get(link);
      if (points == null) {
        points =
            curve(
                PoleGeometry.attachment(pole).subtract(Vec3.atLowerCornerOf(pole.getBlockPos())),
                PoleGeometry.attachment(other).subtract(Vec3.atLowerCornerOf(pole.getBlockPos())));
        cache.put(link, points);
      }
      var out = buffers.getBuffer(RenderType.lines());
      var matrix = pose.last();
      for (int i = 0; i < points.length - 3; i += 3) {
        float dx = points[i + 3] - points[i],
            dy = points[i + 4] - points[i + 1],
            dz = points[i + 5] - points[i + 2],
            len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len == 0) continue;
        out.addVertex(matrix, points[i], points[i + 1], points[i + 2])
            .setColor(.79f, .43f, .16f, 1)
            .setNormal(matrix, dx / len, dy / len, dz / len);
        out.addVertex(matrix, points[i + 3], points[i + 4], points[i + 5])
            .setColor(.79f, .43f, .16f, 1)
            .setNormal(matrix, dx / len, dy / len, dz / len);
      }
    }
  }

  public static float[] curve(Vec3 a, Vec3 b) {
    var points = new float[51];
    double sag = Math.min(1.2, .15 + a.distanceTo(b) * .025);
    for (int i = 0; i <= 16; i++) {
      double t = i / 16d;
      points[i * 3] = (float) (a.x + (b.x - a.x) * t);
      points[i * 3 + 1] = (float) (a.y + (b.y - a.y) * t - 4 * sag * t * (1 - t));
      points[i * 3 + 2] = (float) (a.z + (b.z - a.z) * t);
    }
    return points;
  }
}
