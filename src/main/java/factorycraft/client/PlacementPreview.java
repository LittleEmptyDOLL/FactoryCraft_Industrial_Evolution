package factorycraft.client;

import factorycraft.FactoryCraft;
import factorycraft.items.MachineItem;
import factorycraft.multiblock.PlacementPlan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.*;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Geometry only: the server repeats the complete placement validation before writing anything. */
@EventBusSubscriber(modid = FactoryCraft.ID, value = Dist.CLIENT)
public final class PlacementPreview {
  private static PlacementPlan current;

  @SubscribeEvent
  public static void render(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    current = null;
    var mc = Minecraft.getInstance();
    if (mc.level == null
        || mc.player == null
        || mc.screen != null
        || !(mc.hitResult instanceof BlockHitResult hit)
        || !validTarget(mc, hit)) return;
    for (var hand : InteractionHand.values()) {
      var stack = mc.player.getItemInHand(hand);
      if (!(stack.getItem() instanceof MachineItem item)) continue;
      var context = new BlockPlaceContext(new UseOnContext(mc.player, hand, hit));
      var state = item.getBlock().getStateForPlacement(context);
      if (state == null) return;
      current = PlacementPlan.inspect(context, state);
      break;
    }
    if (current == null) return;
    var pose = event.getPoseStack();
    var camera = event.getCamera().getPosition();
    pose.pushPose();
    pose.translate(-camera.x, -camera.y, -camera.z);
    var buffers = mc.renderBuffers().bufferSource();
    var lines = buffers.getBuffer(RenderType.lines());
    float red = current.valid() ? .15f : 1f, green = current.valid() ? 1f : .15f;
    AABB volume = new AABB(current.anchor());
    for (var cell : current.cells()) volume = volume.minmax(new AABB(cell));
    LevelRenderer.renderLineBox(pose, lines, volume.inflate(.005), red, green, .3f, .85f);
    for (var cell : current.cells())
      if (cell.getY() == current.anchor().getY())
        LevelRenderer.renderLineBox(
            pose,
            lines,
            new AABB(cell).setMaxY(cell.getY() + .025).inflate(.004),
            red,
            green,
            .3f,
            .7f);
    for (var cell : current.blocked())
      LevelRenderer.renderLineBox(pose, lines, new AABB(cell).inflate(.012), 1f, .05f, .05f, 1f);
    // Highlight the selected central base cell, independently of the internal controller.
    LevelRenderer.renderLineBox(
        pose, lines, new AABB(current.target()).inflate(.025), .2f, .65f, 1f, 1f);
    buffers.endBatch(RenderType.lines());
    pose.popPose();
  }

  @SubscribeEvent
  public static void hud(RenderGuiEvent.Post event) {
    var mc = Minecraft.getInstance();
    if (mc.screen != null || mc.level == null || mc.player == null) return;
    if (!(mc.hitResult instanceof BlockHitResult hit) || !validTarget(mc, hit)) {
      current = null;
      return;
    }
    var name = factorycraft.multiblock.MachineInspection.name(mc.level, hit.getBlockPos());
    if (name != null)
      event
          .getGuiGraphics()
          .drawCenteredString(
              mc.font,
              name,
              event.getGuiGraphics().guiWidth() / 2,
              event.getGuiGraphics().guiHeight() / 2 + 16,
              0xc0d8e0);
    if (current == null) return;
    boolean held = false;
    for (var hand : InteractionHand.values())
      if (mc.player.getItemInHand(hand).getItem() instanceof MachineItem) held = true;
    if (!held) {
      current = null;
      return;
    }
    var p = current.profile();
    var label =
        Component.translatable(
            "preview.factorycraft." + (current.valid() ? "valid" : "blocked"),
            p.layout.width(),
            p.layout.depth(),
            p.layout.height(),
            current.blocked().size());
    event
        .getGuiGraphics()
        .drawCenteredString(
            mc.font,
            label,
            event.getGuiGraphics().guiWidth() / 2,
            event.getGuiGraphics().guiHeight() / 2 + 30,
            current.valid() ? 0x70ff90 : 0xff7070);
  }

  public static boolean validTarget(Minecraft mc, BlockHitResult hit) {
    return factorycraft.multiblock.PlacementTarget.valid(mc.level, mc.player, hit);
  }

  private PlacementPreview() {}
}
