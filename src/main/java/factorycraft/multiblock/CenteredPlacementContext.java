package factorycraft.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.*;

/** Moves native BlockItem NBT/components/sound handling to the internal controller, exactly once.
 * A blocked controller must never silently move placement upward as BlockPlaceContext.at does. */
public final class CenteredPlacementContext extends BlockPlaceContext {
    public final BlockPos placementTarget;
    public CenteredPlacementContext(BlockPlaceContext original,BlockPos controller) {
        super(original.getLevel(),original.getPlayer(),original.getHand(),original.getItemInHand(),
                new BlockHitResult(Vec3.atCenterOf(controller),original.getClickedFace(),controller,false));
        replaceClicked=true;
        placementTarget=original.getClickedPos().immutable();
    }
}
