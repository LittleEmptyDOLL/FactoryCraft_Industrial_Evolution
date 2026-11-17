package factorycraft.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import factorycraft.combat.TurretBlockEntity;
import factorycraft.multiblock.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

public final class TurretRenderer implements BlockEntityRenderer<TurretBlockEntity> {
  public TurretRenderer(BlockEntityRendererProvider.Context c) {}

  public void render(
      TurretBlockEntity be,
      float partial,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      int overlay) {
    var p = be.assembly().profile();
    if (!be.assembly().formed()) return;
    var c =
        be.muzzle()
            .subtract(be.getBlockPos().getX(), be.getBlockPos().getY(), be.getBlockPos().getZ());
    pose.pushPose();
    pose.translate(c.x, 1.4, c.z);
    pose.mulPose(Axis.YP.rotationDegrees(180 - be.yaw));
    pose.mulPose(Axis.XP.rotationDegrees(-be.pitch));
    float recoil =
        be.shotTick == Long.MIN_VALUE
            ? 0
            : Math.max(0, 1 - (be.getLevel().getGameTime() + partial - be.shotTick) / 3f);
    pose.translate(-p.layout.width() / 2.0, -1.4, -p.layout.depth() / 2.0 + .08 * recoil);
    int unit = Math.max(p.layout.width(), p.layout.depth());
    pose.scale(unit, unit, unit);
    var model =
        Minecraft.getInstance()
            .getModelManager()
            .getModel(
                ModelResourceLocation.standalone(
                    ResourceLocation.fromNamespaceAndPath(
                        "factorycraft", "block/machine/" + p.id + "_moving")));
    Minecraft.getInstance()
        .getBlockRenderer()
        .getModelRenderer()
        .renderModel(
            pose.last(),
            buffers.getBuffer(RenderType.solid()),
            null,
            model,
            1,
            1,
            1,
            light,
            overlay);
    pose.popPose();
  }

  public AABB getRenderBoundingBox(TurretBlockEntity be) {
    return new AABB(be.getBlockPos()).inflate(3);
  }
}
