package factorycraft.items;

import factorycraft.blocks.*;
import factorycraft.multiblock.*;
import factorycraft.registry.Content;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** One held item, one complete protected structure; portable data never imports an old identity. */
public final class MachineItem extends BlockItem {
  public MachineItem(Block block, Properties properties) {
    super(block, properties);
  }

  @Override
  public BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
    if (context instanceof CenteredPlacementContext) return context;
    var state = getBlock().getStateForPlacement(context);
    if (state == null) return null;
    var profile = MachineProfile.of(state);
    return new CenteredPlacementContext(context, PlacementPlan.controllerFor(context, state));
  }

  @Override
  protected boolean updateCustomBlockEntityTag(
      net.minecraft.core.BlockPos pos,
      net.minecraft.world.level.Level level,
      net.minecraft.world.entity.player.Player player,
      ItemStack stack,
      BlockState state) {
    boolean updated = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
    if (!level.isClientSide) {
      var owner = AssemblyAccess.owner(level.getBlockEntity(pos));
      if (owner != null && owner.assembly().mirrored()) owner.assembly().refreshParts();
    }
    return updated;
  }

  protected boolean canPlace(BlockPlaceContext context, BlockState state) {
    if (!super.canPlace(context, state)) return false;
    boolean valid = PlacementPlan.inspect(context, state).valid();
    if (!valid && context.getPlayer() != null)
      context
          .getPlayer()
          .displayClientMessage(
              Component.translatable("message.factorycraft.placement_space"), true);
    return valid;
  }

  protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
    if (context.getLevel().isClientSide) return super.placeBlock(context, state);
    var level = context.getLevel();
    var plan = PlacementPlan.inspect(context, state);
    if (!plan.valid()) return false;
    var old = plan.cells().stream().map(level::getBlockState).toList();
    int snapshot = level.capturedBlockSnapshots.size();
    boolean success = false;
    try {
      if (!super.placeBlock(context, state)) return false;
      var part =
          Content.FACTORY
              .get(FactoryKind.CASING)
              .get()
              .defaultBlockState()
              .setValue(FactoryBlock.FACING, plan.profile().facing(state))
              .setValue(FactoryBlock.PROFILE, plan.profile());
      for (int i = 1; i < plan.cells().size(); i++)
        if (!level.setBlock(plan.cells().get(i), part, 2)) return false;
      var owner = AssemblyAccess.owner(level.getBlockEntity(plan.anchor()));
      success = owner != null && owner.assembly().formAutomatic();
      return success;
    } finally {
      if (!success) {
        boolean restoring = level.restoringBlockSnapshots;
        level.restoringBlockSnapshots = true;
        try {
          for (int i = plan.cells().size() - 1; i >= 0; i--)
            level.setBlock(plan.cells().get(i), old.get(i), 3);
        } finally {
          level.restoringBlockSnapshots = restoring;
        }
        level.capturedBlockSnapshots.subList(snapshot, level.capturedBlockSnapshots.size()).clear();
      }
    }
  }

  public void appendHoverText(
      ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flags) {
    super.appendHoverText(stack, context, lines, flags);
    var profile = MachineProfile.of(getBlock().defaultBlockState());
    lines.add(
        Component.translatable(
            "tooltip.factorycraft.machine_dimensions",
            profile.layout.width(),
            profile.layout.depth(),
            profile.layout.height()));
    lines.add(Component.translatable("tooltip.factorycraft.machine_preview"));
    lines.add(
        Component.translatable(
            "tooltip.factorycraft." + (profile.splitter() ? "splitter" : profile.id)));
    if (profile.splitter()) {
      int tier = ((FactoryBlock) getBlock()).kind.beltTier;
      lines.add(Component.translatable("tooltip.factorycraft.splitter_rate", tier * 30));
      if (tier > 1) lines.add(Component.translatable("tooltip.factorycraft.splitter_upgrade"));
    }
    if (profile.poleType() != null) {
      var pole = profile.poleType();
      lines.add(
          Component.translatable(
              "tooltip.factorycraft.pole_properties", pole.supply, pole.supply, pole.wire));
    }
    switch (profile) {
      case ADVANCED_ASSEMBLER, PUMPJACK, REFINERY, CHEMICAL, BOILER, ENGINE, TANK, PUMP ->
          lines.add(Component.translatable("tooltip.factorycraft.fluid_nozzles"));
      default -> {}
    }
  }
}
