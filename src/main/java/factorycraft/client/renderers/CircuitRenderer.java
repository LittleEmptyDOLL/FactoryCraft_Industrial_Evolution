package factorycraft.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import factorycraft.circuits.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.phys.AABB;

/** Identity-checked wire geometry only; no graph discovery or signal evaluation on the client. */
public final class CircuitRenderer implements BlockEntityRenderer<CircuitNodeBlockEntity> {
  public CircuitRenderer(BlockEntityRendererProvider.Context c) {}

  @Override
  public AABB getRenderBoundingBox(CircuitNodeBlockEntity n) {
    return new AABB(n.getBlockPos()).inflate(17);
  }

  @Override
  public void render(
      CircuitNodeBlockEntity n,
      float partial,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      int overlay) {
    if (n.assembly().identity() == null) return;
    for (var link : n.links) {
      if (!n.getLevel().hasChunkAt(link.peer())) continue;
      var be = n.getLevel().getBlockEntity(link.peer());
      if (!(be instanceof CircuitNodeBlockEntity peer) || !peer.assembly().owns(link.identity()))
        continue;
      int order = n.getBlockPos().compareTo(peer.getBlockPos());
      if (order > 0 || order == 0 && link.localPort() > link.remotePort()) continue;
      var a = n.portPosition(link.localPort()).subtract(n.getBlockPos());
      var b = peer.portPosition(link.remotePort()).subtract(n.getBlockPos());
      float x = a.getX() + .5f,
          y = a.getY() + .54f + link.color() * .06f,
          z = a.getZ() + .5f,
          tx = b.getX() + .5f,
          ty = b.getY() + .54f + link.color() * .06f,
          tz = b.getZ() + .5f;
      var out = buffers.getBuffer(RenderType.lines());
      var matrix = pose.last();
      float dx = tx - x,
          dy = ty - y,
          dz = tz - z,
          len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (len == 0) continue;
      int rgb = WireColors.rgb(link.color());
      float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, bg = (rgb & 255) / 255f;
      for (int i = 0; i < 8; i++) {
        float t = i / 8f, u = (i + 1) / 8f;
        out.addVertex(matrix, x + dx * t, y + dy * t - .35f * 4 * t * (1 - t), z + dz * t)
            .setColor(r, g, bg, 1)
            .setNormal(matrix, dx / len, dy / len, dz / len);
        out.addVertex(matrix, x + dx * u, y + dy * u - .35f * 4 * u * (1 - u), z + dz * u)
            .setColor(r, g, bg, 1)
            .setNormal(matrix, dx / len, dy / len, dz / len);
      }
    }
  }
}
