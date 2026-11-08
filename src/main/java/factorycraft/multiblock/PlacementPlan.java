package factorycraft.multiblock;

import factorycraft.blocks.FactoryBlock;
import factorycraft.registry.Content;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.*;

/** Identical loaded-footprint validation for server item placement and client preview. */
public record PlacementPlan(MachineProfile profile,BlockPos anchor,net.minecraft.core.Direction facing,List<BlockPos> cells,List<BlockPos> blocked) {
    public BlockPos center() { return profile.center(anchor,facing); }
    public boolean valid() { return blocked.isEmpty(); }
    public static PlacementPlan inspect(BlockPlaceContext context,BlockState state) {
        var profile=Objects.requireNonNull(MachineProfile.of(state));var level=context.getLevel();var player=context.getPlayer();var anchor=context instanceof CenteredPlacementContext ? context.getClickedPos():profile.anchorAtCenter(context.getClickedPos(),profile.facing(state));
        var cells=profile.cells(anchor,profile.facing(state));var blocked=new ArrayList<BlockPos>();
        var collision=player==null ? CollisionContext.empty():CollisionContext.of(player);var part=Content.FACTORY.get(factorycraft.blocks.FactoryKind.CASING).get().defaultBlockState().setValue(FactoryBlock.PROFILE,profile);
        for(var pos:cells) {
            var shape=pos.equals(anchor) ? state:part;
            if(level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.hasChunkAt(pos) || level.getBlockEntity(pos)!=null ||
                    !level.getBlockState(pos).canBeReplaced(BlockPlaceContext.at(context,pos,context.getClickedFace())) || !level.isUnobstructed(shape,pos,collision) ||
                    player!=null && (!level.mayInteract(player,pos) || !player.mayUseItemAt(pos,context.getClickedFace(),context.getItemInHand())))blocked.add(pos);
        }
        return new PlacementPlan(profile,anchor.immutable(),profile.facing(state),cells,List.copyOf(blocked));
    }
}
