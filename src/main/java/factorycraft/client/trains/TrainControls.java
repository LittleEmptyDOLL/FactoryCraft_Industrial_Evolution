package factorycraft.client.trains;

import factorycraft.FactoryCraft;
import factorycraft.trains.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends bounded intent only. Vanilla vehicle-position packets never control this entity. */
@EventBusSubscriber(modid = FactoryCraft.ID, value = Dist.CLIENT)
public final class TrainControls {
  private static int lastEntity = -1, lastIntent = 99, lastBranch = 99, ticks;
  private static boolean lastBrake;
  private static int cameraEntity = -1;
  private static float cameraYaw, cameraPitch;

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    var mc = Minecraft.getInstance();
    if (mc.player == null || !(mc.player.getVehicle() instanceof StockEntity e)) {
      lastEntity = -1;
      cameraEntity = -1;
      return;
    }
    // Preserve the driver's free look while carrying its frame with the car through curves/grades.
    if (cameraEntity == e.getId()) {
      float relative = net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot() - cameraYaw);
      mc.player.setYRot(
          mc.player.getYRot() + net.minecraft.util.Mth.wrapDegrees(e.getYRot() - cameraYaw));
      mc.player.setXRot(
          net.minecraft.util.Mth.clamp(
              mc.player.getXRot()
                  + (e.getXRot() - cameraPitch) * (float) Math.cos(Math.toRadians(relative)),
              -90,
              90));
    }
    cameraEntity = e.getId();
    cameraYaw = e.getYRot();
    cameraPitch = e.getXRot();
    boolean input = mc.screen == null;
    int intent =
        input ? (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0) : 0;
    int branch =
        input ? (mc.options.keyRight.isDown() ? 1 : 0) - (mc.options.keyLeft.isDown() ? 1 : 0) : 0;
    boolean brake = !input || mc.options.keyJump.isDown();
    if (e.automatic()
        && (mc.screen != null || intent == 0 && branch == 0 && !mc.options.keyJump.isDown()))
      return;
    if (e.getId() != lastEntity
        || intent != lastIntent
        || branch != lastBranch
        || brake != lastBrake
        || ++ticks >= 5) {
      PacketDistributor.sendToServer(new TrainNetwork.Drive(e.getId(), intent, brake, branch));
      lastEntity = e.getId();
      lastIntent = intent;
      lastBranch = branch;
      lastBrake = brake;
      ticks = 0;
    }
    if (input)
      mc.player.displayClientMessage(
          Component.translatable(
              "train.factorycraft.driving",
              Math.round(e.speed() * 216),
              Component.translatable("train.factorycraft.branch_" + branch)),
          true);
  }

  private TrainControls() {}
}
