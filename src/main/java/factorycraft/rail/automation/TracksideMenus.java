package factorycraft.rail.automation;

import factorycraft.trains.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

public final class TracksideMenus {
  public static void open(ServerPlayer p, TracksideBlockEntity root, BlockPos clicked) {
    if (!root.ready()) return;
    var d = RailAutomationData.get(p.serverLevel()).device(root.identity);
    if (d == null
        || !d.mayUse(p)
        || p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(clicked)) > 64) return;
    p.openMenu(
        new SimpleMenuProvider(
            (id, inv, user) -> new RailAutomationMenu(id, inv, d.id, false, clicked),
            Component.translatable("rail.factorycraft.automation")),
        b -> {
          b.writeUUID(d.id);
          b.writeBoolean(false);
          b.writeBlockPos(clicked);
          b.writeNbt(RailAutomationMenu.snapshot(p, d.id, false));
        });
  }

  public static void train(ServerPlayer p, TrainData.Train t, StockEntity entity) {
    if (!TrainWorld.mayUse(t, p) || p.distanceToSqr(entity) > 64) return;
    var clicked = entity.blockPosition();
    p.openMenu(
        new SimpleMenuProvider(
            (id, inv, user) -> new RailAutomationMenu(id, inv, t.id, true, clicked),
            Component.translatable("rail.factorycraft.automation")),
        b -> {
          b.writeUUID(t.id);
          b.writeBoolean(true);
          b.writeBlockPos(clicked);
          b.writeNbt(RailAutomationMenu.snapshot(p, t.id, true));
        });
  }

  private TracksideMenus() {}
}
