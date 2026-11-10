package factorycraft.energy;

import factorycraft.blockentity.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;

/** Selection is server-only and transient. Wires themselves live at their UUID-bound owners. */
public final class PowerWireTool extends Item {
  private record Selection(
      net.minecraft.world.level.Level level, BlockPos pos, UUID id, BlockPos contact) {}

  private static final Map<Player, Selection> SELECTED = new WeakHashMap<>();

  public PowerWireTool(Properties p) {
    super(p);
  }

  public static PoleBlockEntity pole(net.minecraft.world.level.Level level, BlockPos at) {
    if (!level.hasChunkAt(at)) return null;
    var be = level.getBlockEntity(at);
    if (be instanceof PoleBlockEntity p) return p;
    if (be instanceof CasingBlockEntity part
        && part.genericForRemoval() instanceof PoleBlockEntity p) return p;
    return null;
  }

  @Override
  public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
    return useOn(context);
  }

  public InteractionResult useOn(UseOnContext c) {
    var player = c.getPlayer();
    if (player == null) return InteractionResult.FAIL;
    var level = c.getLevel();
    var pole = pole(level, c.getClickedPos());
    if (pole == null) return InteractionResult.PASS;
    if (level.isClientSide) return InteractionResult.SUCCESS;
    if (player.isSpectator()
        || !player.getAbilities().mayBuild
        || !player.canInteractWithBlock(c.getClickedPos(), 0)
        || !level.mayInteract(player, c.getClickedPos())
        || !level.mayInteract(player, pole.getBlockPos())
        || !pole.available()) return InteractionResult.FAIL;
    if (player.isShiftKeyDown()) {
      for (var link : pole.links()) {
        var other = PowerWires.at(level, link.pos());
        if (other != null && !level.mayInteract(player, other.getBlockPos()))
          return InteractionResult.FAIL;
      }
      PowerWires.clear(pole);
      SELECTED.remove(player);
      message(player, "cleared");
      return InteractionResult.CONSUME;
    }
    var first = SELECTED.remove(player);
    if (first == null) {
      SELECTED.put(
          player,
          new Selection(
              level,
              pole.getBlockPos(),
              pole.assembly().identity(),
              c.getClickedPos().immutable()));
      message(player, "selected");
      return InteractionResult.CONSUME;
    }
    var a = first.level == level ? PowerWires.at(level, first.pos) : null;
    if (a == null
        || !a.available()
        || !a.assembly().owns(first.id)
        || !level.mayInteract(player, first.contact)
        || !level.mayInteract(player, a.getBlockPos())) {
      message(player, "unavailable");
      return InteractionResult.FAIL;
    }
    if (a == pole) {
      message(player, "cancelled");
      return InteractionResult.CONSUME;
    }
    if (!PowerWires.reachable(a, pole)) {
      message(player, "too_far");
      return InteractionResult.FAIL;
    }
    if (a.hasLink(pole)) {
      PowerWires.disconnect(a, pole);
      message(player, "removed");
    } else {
      PowerWires.connect(a, pole);
      message(player, "connected");
    }
    return InteractionResult.CONSUME;
  }

  private static void message(Player p, String key) {
    p.displayClientMessage(Component.translatable("message.factorycraft.power_wire_" + key), true);
  }
}
