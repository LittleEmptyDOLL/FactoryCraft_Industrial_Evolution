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
        var a=be.getBlockPos();var b=be.outputPosition();return new net.minecraft.world.phys.AABB(a).minmax(new net.minecraft.world.phys.AABB(b)).minmax(new net.minecraft.world.phys.AABB(be.trackPosition(be.tracks()-1))).inflate(.5);
    }
    @Override public void render(BeltBlockEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        var facing=be.getBlockState().getValue(FactoryBlock.FACING);
        double elapsed=Math.clamp(be.getLevel().getGameTime()+partial-be.snapshotTick(),0,4);
        for(int lane=0;lane<be.laneCount();lane++) {
            var route=be.route();double limit=be.lane(lane).length();
            for(var carrier:be.lane(lane).carriers()) {
                double position=Math.min(limit,carrier.positionUnits()+(be.circuitRunning() ? elapsed*be.speed()*96 : 0));limit=position-24;
                if(!route.visible(position,lane%2))continue;
                var point=route.point(lane%2,position);var tangent=route.tangent(lane%2,position);
                pose.pushPose();pose.translate((lane/2)*facing.getClockWise().getStepX(),0,(lane/2)*facing.getClockWise().getStepZ());pose.translate(.5,0,.5);pose.mulPose(Axis.YP.rotationDegrees(180-facing.toYRot()));
                pose.translate(point.x()-.5,point.y()+.08,point.z()-.5);
                double yaw=Math.atan2(-tangent.x(),-tangent.z());double pitch=Math.atan2(tangent.y(),Math.hypot(tangent.x(),tangent.z()));
                pose.mulPose(Axis.YP.rotation((float)yaw));pose.mulPose(Axis.XP.rotation((float)(Math.PI/2+pitch)));pose.scale(.3f,.3f,.3f);
                Minecraft.getInstance().getItemRenderer().renderStatic(carrier.value,ItemDisplayContext.FIXED,light,overlay,pose,buffers,be.getLevel(),lane);
                pose.popPose();
            }
        }
    }
}
