package factorycraft.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import factorycraft.blockentity.InserterBlockEntity;
import factorycraft.blocks.FactoryBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;

/** Two articulated original block arms; client phase interpolation is visual only. */
public final class InserterRenderer implements BlockEntityRenderer<InserterBlockEntity> {
  public InserterRenderer(BlockEntityRendererProvider.Context c) {}

  @Override
  public void render(
      InserterBlockEntity be,
      float partial,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      int overlay) {
    double angle = be.visualAngle(partial) * (be.reflected() ? -1 : 1);
    pose.pushPose();
    pose.translate(.5, .35, .5);
    pose.mulPose(
        Axis.YP.rotationDegrees(180 - be.getBlockState().getValue(FactoryBlock.FACING).toYRot()));
    pose.mulPose(Axis.YP.rotation((float) angle));
    pose.translate(0, .1 * Math.sin(angle), 0);
    for (int i = 0; i < 2; i++) {
      pose.pushPose();
      pose.translate(-.045, .07 + i * .1, (-.30 - i * .45) * be.reach());
      pose.scale(.09f, .09f, .52f * be.reach());
      Minecraft.getInstance()
          .getBlockRenderer()
          .renderSingleBlock(
              switch (be.kind()) {
                case BURNER_INSERTER -> Blocks.GRAY_CONCRETE.defaultBlockState();
                case LONG_INSERTER -> Blocks.RED_CONCRETE.defaultBlockState();
                case FAST_INSERTER -> Blocks.BLUE_CONCRETE.defaultBlockState();
                case BULK_INSERTER -> Blocks.LIME_CONCRETE.defaultBlockState();
                case STACK_INSERTER -> Blocks.WHITE_CONCRETE.defaultBlockState();
                default -> Blocks.YELLOW_CONCRETE.defaultBlockState();
              },
              pose,
              buffers,
              light,
              overlay);
      pose.popPose();
    }
    // Fingers open with an empty hand and close on the sole server-owned cargo.
    double gap = be.held().isEmpty() ? .12 : .045;
    for (int side = -1; side <= 1; side += 2) {
      pose.pushPose();
      pose.translate(side * gap - .025, .18, -be.reach() - .10);
      pose.scale(.05f, .07f, .22f);
      Minecraft.getInstance()
          .getBlockRenderer()
          .renderSingleBlock(Blocks.IRON_BLOCK.defaultBlockState(), pose, buffers, light, overlay);
      pose.popPose();
    }
    if (!be.held().isEmpty()) {
      pose.pushPose();
      pose.translate(0, .27, -be.reach());
      pose.scale(.35f, .35f, .35f);
      Minecraft.getInstance()
          .getItemRenderer()
          .renderStatic(
              be.held(), ItemDisplayContext.FIXED, light, overlay, pose, buffers, be.getLevel(), 0);
      pose.popPose();
    }
    pose.popPose();
    if (!be.held().isEmpty() && be.held().getCount() > 1) {
      pose.pushPose();
      pose.translate(.5, 1.05, .5);
      pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
      pose.scale(-.018f, -.018f, .018f);
      var font = Minecraft.getInstance().font;
      String count = Integer.toString(be.held().getCount());
      font.drawInBatch(
          count,
          -font.width(count) / 2f,
          0,
          0xffffff,
          true,
          pose.last().pose(),
          buffers,
          net.minecraft.client.gui.Font.DisplayMode.NORMAL,
          0x66000000,
          light);
      pose.popPose();
    }
  }

  @Override
  public boolean shouldRenderOffScreen(InserterBlockEntity be) {
    return true;
  }
}
