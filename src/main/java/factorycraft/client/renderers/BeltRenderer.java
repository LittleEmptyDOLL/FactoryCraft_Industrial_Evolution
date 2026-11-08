package factorycraft.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import factorycraft.blockentity.BeltBlockEntity;
import factorycraft.blocks.FactoryBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.item.ItemDisplayContext;

public final class BeltRenderer implements BlockEntityRenderer<BeltBlockEntity> {
    public BeltRenderer(BlockEntityRendererProvider.Context c) {}
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(BeltBlockEntity be) {
        var a=be.getBlockPos();var b=be.trackPosition(be.tracks()-1);
        return new net.minecraft.world.phys.AABB(Math.min(a.getX(),b.getX()),a.getY(),Math.min(a.getZ(),b.getZ()),Math.max(a.getX(),b.getX())+1,a.getY()+1,Math.max(a.getZ(),b.getZ())+1);
    }
    @Override public void render(BeltBlockEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        var facing=be.getBlockState().getValue(FactoryBlock.FACING);
        double elapsed=Math.clamp(be.getLevel().getGameTime()+partial-be.snapshotTick(),0,4);
        for(int lane=0;lane<be.laneCount();lane++) {
            double limit=1;
            for(var carrier:be.lane(lane).carriers()) {
                double position=Math.min(limit,carrier.position()+elapsed*be.speed());limit=position-.25;
                pose.pushPose();pose.translate((lane/2)*facing.getClockWise().getStepX(),0,(lane/2)*facing.getClockWise().getStepZ());pose.translate(.5,.27,.5);pose.mulPose(Axis.YP.rotationDegrees(180-facing.toYRot()));
                pose.translate(lane%2==0 ? -.22 : .22,0,.5-position);
                pose.mulPose(Axis.XP.rotationDegrees(90));pose.scale(.3f,.3f,.3f);
                Minecraft.getInstance().getItemRenderer().renderStatic(carrier.value,ItemDisplayContext.FIXED,light,overlay,pose,buffers,be.getLevel(),lane);
                pose.popPose();
            }
        }
    }
}
