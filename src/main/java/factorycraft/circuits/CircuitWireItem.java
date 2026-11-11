package factorycraft.circuits;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

/** Reusable native two-click wiring tool. Client selection never creates a graph connection. */
public final class CircuitWireItem extends Item {
  public final int color;

  public CircuitWireItem(int color, Properties p) {
    super(p);
    this.color = color;
  }

  public static CompoundTag selection(ItemStack stack) {
    var data = stack.get(DataComponents.CUSTOM_DATA);
    return data == null ? new CompoundTag() : data.copyTag().getCompound("CircuitSelection");
  }

  private static void select(ItemStack stack, CompoundTag selection) {
    var old = stack.get(DataComponents.CUSTOM_DATA);
    var data = old == null ? new CompoundTag() : old.copyTag();
    if (selection.isEmpty()) data.remove("CircuitSelection");
    else data.put("CircuitSelection", selection);
    if (data.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
    else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
  }

  @Override
  public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
    // Standard NeoForge tool hook runs after protection events and before chest/fluid menus.
    return useOn(context);
  }

  @Override
  public InteractionResult useOn(UseOnContext ctx) {
    var player = ctx.getPlayer();
    if (player == null
        || player.isSpectator()
        || !player.getAbilities().mayBuild
        || !player.canInteractWithBlock(ctx.getClickedPos(), 0)) return InteractionResult.FAIL;
    if (ctx.getLevel().isClientSide) return InteractionResult.SUCCESS;
    var node = CircuitAccess.at(ctx.getLevel(), ctx.getClickedPos());
    if (node == null
        || !node.available()
        || !ctx.getLevel().mayInteract(player, node.pos())
        || node.owner() instanceof factorycraft.blockentity.LabBlockEntity lab
            && !factorycraft.research.ResearchTeams.member(player, lab.scope())) {
      player.displayClientMessage(
          Component.translatable("message.factorycraft.circuit_node_required"), true);
      return InteractionResult.FAIL;
    }
    int port = node.portAt(ctx.getClickedPos());
    if (port < 0
        || !ctx.getLevel().mayInteract(player, ctx.getClickedPos())
        || !player.mayUseItemAt(ctx.getClickedPos(), ctx.getClickedFace(), ctx.getItemInHand()))
      return InteractionResult.FAIL;
    if (player.isShiftKeyDown()) {
      CircuitSimulation.clear(node, port, color);
      select(ctx.getItemInHand(), new CompoundTag());
      player.displayClientMessage(
          Component.translatable("message.factorycraft.circuit_cleared"), true);
      return InteractionResult.CONSUME;
    }
    var old = selection(ctx.getItemInHand());
    if (old.isEmpty()) {
      var data = new CompoundTag();
      data.putString("Dimension", ctx.getLevel().dimension().location().toString());
      data.putLong("Anchor", node.pos().asLong());
      data.putUUID("Identity", node.identity());
      data.putInt("Port", port);
      select(ctx.getItemInHand(), data);
      player.displayClientMessage(
          Component.translatable(
              "message.factorycraft.circuit_first",
              node.ports() == 2 ? (port == 0 ? "IN" : "OUT") : "I/O"),
          true);
      return InteractionResult.CONSUME;
    }
    select(ctx.getItemInHand(), new CompoundTag());
    if (!old.getString("Dimension").equals(ctx.getLevel().dimension().location().toString())
        || !old.hasUUID("Identity")) return InteractionResult.FAIL;
    var anchor = net.minecraft.core.BlockPos.of(old.getLong("Anchor"));
    var first = CircuitAccess.at(ctx.getLevel(), anchor);
    int from = old.getInt("Port");
    boolean valid =
        first != null
            && first.available()
            && ctx.getLevel().mayInteract(player, first.pos())
            && (!(first.owner() instanceof factorycraft.blockentity.LabBlockEntity lab)
                || factorycraft.research.ResearchTeams.member(player, lab.scope()))
            && java.util.Objects.equals(first.identity(), old.getUUID("Identity"))
            && from >= 0
            && from < first.ports()
            && ctx.getLevel().mayInteract(player, first.portPosition(from))
            && player.mayUseItemAt(
                first.portPosition(from), ctx.getClickedFace(), ctx.getItemInHand());
    boolean ok = valid && CircuitSimulation.connect(first, from, node, port, color);
    player.displayClientMessage(
        Component.translatable(
            ok
                ? "message.factorycraft.circuit_connected"
                : "message.factorycraft.circuit_rejected"),
        true);
    return ok ? InteractionResult.CONSUME : InteractionResult.FAIL;
  }

  @Override
  public void appendHoverText(
      ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flags) {
    super.appendHoverText(stack, ctx, lines, flags);
    lines.add(Component.translatable("tooltip.factorycraft.circuit_wire"));
  }
}
