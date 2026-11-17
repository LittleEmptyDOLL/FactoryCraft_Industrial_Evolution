package factorycraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import factorycraft.FactoryCraft;
import factorycraft.combat.FirearmItem;
import factorycraft.registry.Content;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.*;

@EventBusSubscriber(modid = FactoryCraft.ID, value = Dist.CLIENT)
public final class CombatClient {
  @SubscribeEvent
  public static void extensions(RegisterClientExtensionsEvent e) {
    var extension =
        new IClientItemExtensions() {
          public HumanoidModel.ArmPose getArmPose(
              LivingEntity entity, InteractionHand hand, ItemStack stack) {
            return HumanoidModel.ArmPose.CROSSBOW_HOLD;
          }

          public boolean applyForgeHandTransform(
              PoseStack pose,
              LocalPlayer p,
              HumanoidArm arm,
              ItemStack stack,
              float partial,
              float equip,
              float swing) {
            long shot = stack.getOrDefault(Content.SHOT_TIME.get(), Long.MIN_VALUE);
            float recoil =
                shot == Long.MIN_VALUE
                    ? 0
                    : (float) Math.max(0, 1 - (p.level().getGameTime() + partial - shot) / 3.0);
            int side = arm == HumanoidArm.RIGHT ? 1 : -1;
            pose.translate(side * .56, -.52 - equip * .6, -.72 + recoil * .09);
            pose.mulPose(Axis.XP.rotationDegrees(-recoil * 5));
            return true;
          }
        };
    for (var holder : Content.ITEMS.getEntries())
      if (holder.get() instanceof FirearmItem) e.registerItem(extension, holder.get());
  }

  private CombatClient() {}
}
