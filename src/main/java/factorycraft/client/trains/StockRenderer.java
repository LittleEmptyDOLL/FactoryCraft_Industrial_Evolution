package factorycraft.client.trains;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import factorycraft.trains.StockEntity;
import java.util.List;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class StockRenderer extends EntityRenderer<StockEntity> {
  private static final ResourceLocation TEXTURE =
      ResourceLocation.fromNamespaceAndPath("factorycraft", "textures/entity/rolling_stock.png");

  public StockRenderer(EntityRendererProvider.Context c) {
    super(c);
    shadowRadius = 1.15f;
  }

  public ResourceLocation getTextureLocation(StockEntity e) {
    return TEXTURE;
  }

  public void render(
      StockEntity e, float yaw, float partial, PoseStack p, MultiBufferSource buffers, int light) {
    var out = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
    float bodyYaw = Mth.rotLerp(partial, e.yRotO, e.getYRot()),
        pitch = Mth.lerp(partial, e.xRotO, e.getXRot());
    p.pushPose();
    p.mulPose(Axis.YP.rotationDegrees(180 - bodyYaw));
    p.mulPose(Axis.XP.rotationDegrees(-pitch));
    mesh(StockMeshes.BODY.get(e.kind()), e, p, out, light);
    boolean detail = entityRenderDispatcher.distanceToSqr(e) < 64 * 64;
    for (int end : new int[] {-1, 1}) {
      p.pushPose();
      p.translate(0, 0, end * e.bogieDistance() / 2);
      float bogie = end < 0 ? e.frontYaw(partial) : e.rearYaw(partial);
      p.mulPose(Axis.YP.rotationDegrees(Mth.wrapDegrees(bodyYaw - bogie)));
      p.mulPose(
          Axis.XP.rotationDegrees(
              pitch - (end < 0 ? e.frontPitch(partial) : e.rearPitch(partial))));
      mesh(StockMeshes.TRUCK, e, p, out, light);
      for (int side : new int[] {-1, 1})
        for (double z :
            e.kind() == factorycraft.simulation.trains.StockSpec.LOCOMOTIVE
                ? new double[] {-.6, 0, .6}
                : new double[] {-.6, .6}) {
          p.pushPose();
          p.translate(side * .73, .41, z);
          if (detail)
            p.mulPose(
                Axis.XP.rotationDegrees((float) (e.wheelPhase(partial) / .39 * 180 / Math.PI)));
          mesh(StockMeshes.WHEEL, e, p, out, light);
          p.popPose();
        }
      p.popPose();
    }
    p.popPose();
    super.render(e, yaw, partial, p, buffers, light);
  }

  private static void mesh(
      List<StockMeshes.Face> faces, StockEntity e, PoseStack p, VertexConsumer out, int light) {
    var pose = p.last();
    for (var f : faces) {
      var v = f.vertices();
      int c = f.color() == StockMeshes.PAINT ? e.color() : f.color();
      float ax = v[3] - v[0],
          ay = v[4] - v[1],
          az = v[5] - v[2],
          bx = v[6] - v[0],
          by = v[7] - v[1],
          bz = v[8] - v[2];
      float nx = ay * bz - az * by,
          ny = az * bx - ax * bz,
          nz = ax * by - ay * bx,
          mag = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
      if (mag < 1e-9) continue;
      for (int i = 0; i < 4; i++)
        out.addVertex(pose, v[i * 3], v[i * 3 + 1], v[i * 3 + 2])
            .setColor(0xff000000 | c)
            .setUv((i == 0 || i == 3) ? 0 : 1, i < 2 ? 0 : 1)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(f.emissive() ? LightTexture.FULL_BRIGHT : light)
            .setNormal(pose, nx / mag, ny / mag, nz / mag);
    }
  }
}
