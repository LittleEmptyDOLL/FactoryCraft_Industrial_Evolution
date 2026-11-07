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
    @Override public void render(InserterBlockEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        double phase=be.visualPhase(partial);
        double angle=be.held().isEmpty() ? (1-phase)*Math.PI : phase*Math.PI;
        pose.pushPose();pose.translate(.5,.35,.5);pose.mulPose(Axis.YP.rotationDegrees(180-be.getBlockState().getValue(FactoryBlock.FACING).toYRot()));
        pose.mulPose(Axis.YP.rotation((float)angle));
        for(int i=0;i<2;i++) {
            pose.pushPose();pose.translate(-.045,.07+i*.1,-.35-i*.35);pose.scale(.09f,.09f,.44f);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.IRON_BLOCK.defaultBlockState(),pose,buffers,light,overlay);pose.popPose();
        }
        if(!be.held().isEmpty()) {
            pose.pushPose();pose.translate(0,.27,-.85);pose.scale(.35f,.35f,.35f);
            Minecraft.getInstance().getItemRenderer().renderStatic(be.held(),ItemDisplayContext.FIXED,light,overlay,pose,buffers,be.getLevel(),0);pose.popPose();
        }
        pose.popPose();
    }
    @Override public boolean shouldRenderOffScreen(InserterBlockEntity be) { return true; }
}
