package factorycraft.gametest;

import factorycraft.multiblock.MachineProfile;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.*;
import net.minecraft.world.phys.*;

/** Legacy fixture coordinates name internal controllers; real item targeting now uses base centers. */
final class PlacementTestSupport {
    static BlockPos center(Player player,BlockPos controller) {
        var item=(BlockItem)player.getMainHandItem().getItem();var raw=new BlockPlaceContext(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(controller),Direction.UP,controller,false)));
        var state=item.getBlock().getStateForPlacement(raw);var profile=MachineProfile.of(state);return profile.center(controller,profile.facing(state));
    }
    static InteractionResult place(Player player,BlockPos controller) {
        var at=center(player,controller);if(!player.level().hasChunkAt(at))return InteractionResult.FAIL;
        if(player.level().getBlockState(at.below()).isAir())player.level().setBlockAndUpdate(at.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        at=at.below();return player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(at),Direction.UP,at,false)));
    }
    private PlacementTestSupport() {}
}
