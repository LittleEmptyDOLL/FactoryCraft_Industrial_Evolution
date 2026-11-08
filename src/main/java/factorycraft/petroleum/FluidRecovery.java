package factorycraft.petroleum;

import factorycraft.items.FluidCanisterItem;
import factorycraft.simulation.fluids.FluidCell;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import java.util.List;

/** Stage a private item copy, then commit both owners before any fluid observers run. */
public final class FluidRecovery {
    public static boolean recover(PetroleumMenu menu, Player player, int tank) {
        if (player.level().isClientSide || player.containerMenu != menu || player.isSpectator()
                || !menu.stillValid(player) || !menu.machine.petroleum().ready()
                || menu.machine.kind() == factorycraft.blocks.FactoryKind.PUMPJACK
                || tank < 0 || tank > 1) return false;

        var carried = menu.getCarried();
        if (!(carried.getItem() instanceof FluidCanisterItem) || carried.getCount() != 1) return false;
        var source = menu.machine.petroleum().cells.get(tank);
        if (source.amount() == 0) return false;

        var container = FluidCanisterItem.handler(carried.copy());
        int moved = container.fill(source.key().stack(source.amount()), FluidAction.EXECUTE);
        if (moved <= 0) return false;
        return FluidCell.commitBatch(List.of(new FluidCell.Mutation<>(source, source.key(), source.amount() - moved)),
                () -> menu.setCarried(container.getContainer()), false);
    }

    private FluidRecovery() {}
}
