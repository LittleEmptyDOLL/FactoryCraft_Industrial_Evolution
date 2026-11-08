package factorycraft.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import factorycraft.blockentity.FactoryBlockEntity;
import factorycraft.blocks.FactoryBlock;
import factorycraft.multiblock.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Only moving components use a BER. Complete shells remain in vanilla chunk meshes. */
public final class MachineAnimationRenderer<T extends FactoryBlockEntity & AssemblyOwner> implements BlockEntityRenderer<T> {
    private static final Map<MachineProfile,ModelResourceLocation> MODELS=new EnumMap<>(MachineProfile.class);
    static { for(var p:MachineProfile.values())if(p!=MachineProfile.TANK && p!=MachineProfile.PUMP && p!=MachineProfile.SPLITTER)MODELS.put(p,ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("factorycraft","block/machine/"+p.id+"_moving"))); }
    private static final class Phase { double time,last=Double.NaN; }
    private final Map<T,Phase> phases=new WeakHashMap<>();
    public MachineAnimationRenderer(BlockEntityRendererProvider.Context context) {}
    public void render(T be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        var profile=MachineProfile.of(be.getBlockState());if(profile==null || be.assembly()==null || !be.assembly().formed() || !MODELS.containsKey(profile))return;
        boolean active=be.getBlockState().getValue(FactoryBlock.ACTIVE);boolean glow=profile==MachineProfile.FURNACE || profile==MachineProfile.INDUSTRIAL || profile==MachineProfile.BOILER;
        if(glow && !active)return;
        var phase=phases.computeIfAbsent(be,b->new Phase());double now=be.getLevel().getGameTime()+partial;
        if(active && Double.isFinite(phase.last))phase.time=(phase.time+Math.clamp(now-phase.last,0,5))%7200;phase.last=now;
        pose.pushPose();var facing=profile.facing(be.getBlockState());pose.translate(.5,0,.5);pose.mulPose(Axis.YP.rotationDegrees(180-facing.toYRot()));pose.translate(-.5,0,-.5);
        float unit=Math.max(profile.layout.width(),Math.max(profile.layout.height(),profile.layout.depth()));pose.scale(unit,unit,unit);
        switch(profile) {
            case LAB -> rotate(pose,unit,1.5,1.35,1.5,Axis.YP,(float)(phase.time*8));
            case DRILL -> rotate(pose,unit,1.5,.9,1.5,Axis.YP,(float)(phase.time*18));
            case ASSEMBLER, ADVANCED_ASSEMBLER -> pose.translate(0,-.22*(1-Math.cos(phase.time*.32))/unit,0);
            case GENERATOR -> rotate(pose,unit,1.5,1.2,.2,Axis.ZP,(float)(phase.time*12));
            case PUMPJACK -> rotate(pose,unit,1.5,2.65,1.5,Axis.XP,(float)(Math.sin(phase.time*.15)*12));
            case REFINERY -> rotate(pose,unit,3.7,.77,.3,Axis.ZP,(float)(phase.time*10));
            case CHEMICAL -> rotate(pose,unit,1.5,2.3,1.5,Axis.YP,(float)(phase.time*9));
            case ENGINE -> rotate(pose,unit,1.5,1,.97,Axis.ZP,(float)(phase.time*15));
            default -> {}
        }
        var mc=Minecraft.getInstance();var model=mc.getModelManager().getModel(MODELS.get(profile));
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(),buffers.getBuffer(RenderType.solid()),null,model,1,1,1,glow ? LightTexture.FULL_BRIGHT:light,overlay);
        pose.popPose();
    }
    private static void rotate(PoseStack pose,float unit,double x,double y,double z,Axis axis,float degrees) { pose.translate(x/unit,y/unit,z/unit);pose.mulPose(axis.rotationDegrees(degrees));pose.translate(-x/unit,-y/unit,-z/unit); }
    public AABB getRenderBoundingBox(T be) {
        var p=MachineProfile.of(be.getBlockState());if(p==null)return new AABB(be.getBlockPos());var a=be.getBlockPos();var b=p.offset(a,p.facing(be.getBlockState()),p.layout.width()-1,p.layout.height()-1,p.layout.depth()-1);
        return new AABB(Math.min(a.getX(),b.getX()),a.getY(),Math.min(a.getZ(),b.getZ()),Math.max(a.getX(),b.getX())+1,b.getY()+1,Math.max(a.getZ(),b.getZ())+1);
    }
}
