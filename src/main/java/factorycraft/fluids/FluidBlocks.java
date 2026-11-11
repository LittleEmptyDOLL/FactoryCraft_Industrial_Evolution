package factorycraft.fluids;

import factorycraft.blockentity.FluidBlockEntity;
import factorycraft.blocks.*;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.fluids.FluidUtil;

public final class FluidBlocks {
  public static FluidKind kind(BlockState state) {
    return state.getBlock() instanceof FluidDeviceBlock device ? device.kind : FluidKind.PIPE;
  }

  public static boolean port(
      net.minecraft.world.level.LevelAccessor level, BlockPos pos, Direction side) {
    if (!level.hasChunkAt(pos)) return false;
    var state = level.getBlockState(pos);
    if (state.getBlock() instanceof FluidPipeBlock) return true;
    var profile = factorycraft.multiblock.MachineProfile.of(state);
    var be = level.getBlockEntity(pos);
    if (be instanceof factorycraft.blockentity.CasingBlockEntity part) {
      var owner =
          level instanceof Level actual && actual.isClientSide
              ? part.genericForRemoval()
              : part.genericOwner();
      return owner != null
          && factorycraft.multiblock.MachineFluidAccess.definition(owner.factoryEntity(), pos, side)
              != null;
    }
    return profile != null
        && be instanceof factorycraft.blockentity.FactoryBlockEntity owner
        && factorycraft.multiblock.MachineFluidAccess.definition(owner, pos, side) != null
        && (level instanceof Level actual && actual.isClientSide
            || factorycraft.multiblock.MachineFluidAccess.ready(owner, pos));
  }

  public static ItemInteractionResult useItem(
      ItemStack stack,
      Level level,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      Direction side) {
    if (FluidUtil.getFluidHandler(stack).isEmpty())
      return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    if (!level.isClientSide && stack.is(net.minecraft.world.item.Items.BUCKET)) {
      var handler =
          level.getCapability(
              net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, pos, side);
      var sampled =
          handler == null
              ? net.neoforged.neoforge.fluids.FluidStack.EMPTY
              : FluidSimulation.manual(
                  () ->
                      handler.drain(
                          1000,
                          net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
                              .SIMULATE));
      if (!sampled.isEmpty() && !sampled.getComponentsPatch().isEmpty()) {
        player.displayClientMessage(
            net.minecraft.network.chat.Component.translatable(
                "message.factorycraft.component_bucket"),
            true);
        return ItemInteractionResult.sidedSuccess(false);
      }
    }
    if (level.isClientSide
        || FluidSimulation.manual(
            () -> FluidUtil.interactWithFluidHandler(player, hand, level, pos, side)))
      return ItemInteractionResult.sidedSuccess(level.isClientSide);
    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
  }

  public static InteractionResult open(Level level, BlockPos pos, Player player) {
    if (!level.isClientSide && level.getBlockEntity(pos) instanceof FluidBlockEntity be)
      factorycraft.multiblock.MachineInteraction.open(be, player, pos);
    return InteractionResult.sidedSuccess(level.isClientSide);
  }

  public static List<ItemStack> preserve(List<ItemStack> drops, LootParams.Builder context) {
    if (context.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof FluidBlockEntity be)
      for (var stack : drops)
        if (stack.is(be.getBlockState().getBlock().asItem()))
          be.saveToItem(stack, context.getLevel().registryAccess());
    return drops;
  }

  private FluidBlocks() {}
}
