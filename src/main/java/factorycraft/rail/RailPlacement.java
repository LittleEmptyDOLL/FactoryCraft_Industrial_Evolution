package factorycraft.rail;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Main-thread transaction: inspect -> stage -> all-voxel claim event -> revalidate -> debit ->
 * publish.
 */
public final class RailPlacement {
  private record Before(BlockPos p, BlockState state, CompoundTag tag) {}

  private record Debit(int slot, ItemStack original, int count) {}

  public static int count(Player p) {
    int n = 0;
    for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
      var s = p.getInventory().getItem(i);
      if (s.is(RailContent.RAIL.get())) n += s.getCount();
    }
    return n;
  }

  public static String place(ServerPlayer player, RailPlan plan) {
    var level = player.serverLevel();
    var data = RailwayData.get(level);
    String reason = plan.inspect(level, player);
    if (!reason.equals("ready")) return reason;
    if (player.isSpectator() || !player.mayBuild()) return "permission";
    var scope = factorycraft.research.ResearchTeams.resolve(player);
    if (!player.isCreative()
        && (scope.isEmpty()
            || !factorycraft.research.ResearchCatalog.get(level)
                .allowed(
                    scope.get(),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        "factorycraft", "rail"),
                    level))) return "research";
    var debits = new ArrayList<Debit>();
    var paid = new ArrayList<ItemStack>();
    int left = plan.curve.cost();
    for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
      var s = player.getInventory().getItem(i);
      if (!s.is(RailContent.RAIL.get())) continue;
      int take = Math.min(left, s.getCount());
      debits.add(new Debit(i, s.copy(), take));
      paid.add(s.copyWithCount(take));
      left -= take;
    }
    if (left > 0 && !player.isCreative()) return "price";
    if (player.isCreative()) {
      paid.clear();
      int cost = plan.curve.cost();
      while (cost > 0) {
        int n = Math.min(64, cost);
        paid.add(new ItemStack(RailContent.RAIL.get(), n));
        cost -= n;
      }
    }
    var snapshots = new ArrayList<BlockSnapshot>();
    var before = new ArrayList<Before>();
    boolean committed = false;
    int oldWriting = data.writing;
    // This item owns an all-voxel transaction. Do not let the generic ItemStack
    // wrapper capture it again and fire a second cancellation after resource commit.
    boolean oldCapture = level.captureBlockSnapshots;
    level.captureBlockSnapshots = false;
    try {
      for (var c : plan.footprint.clearance) {
        var p = RailwayData.pos(c);
        if (!level.mayInteract(player, p)
            || !player.mayUseItemAt(p, Direction.UP, new ItemStack(RailContent.RAIL.get())))
          return "permission";
        snapshots.add(BlockSnapshot.create(level.dimension(), level, p));
      }
      for (var c : plan.footprint.bed.keySet()) {
        var p = RailwayData.pos(c);
        if (level.getBlockState(p).is(RailContent.BED.get())) continue;
        var state = level.getBlockState(p);
        var be = level.getBlockEntity(p);
        before.add(
            new Before(
                p, state, be == null ? null : be.saveWithFullMetadata(level.registryAccess())));
        if (!level.setBlock(p, RailContent.BED.get().defaultBlockState(), 2)) return "blocked";
      }
      if (EventHooks.onMultiBlockPlace(player, snapshots, Direction.UP)) return "permission";
      reason = plan.inspect(level, player);
      if (!reason.equals("ready")) return reason;
      for (var debit : debits)
        if (!ItemStack.matches(player.getInventory().getItem(debit.slot), debit.original))
          return "price";
      for (var c : plan.footprint.bed.keySet())
        if (!(level.getBlockEntity(RailwayData.pos(c)) instanceof RailCellBlockEntity))
          return "blocked";
      if (!player.isCreative())
        for (var debit : debits) player.getInventory().getItem(debit.slot).shrink(debit.count);
      var segment = new RailwayData.Segment(UUID.randomUUID(), plan.curve, paid);
      data.add(segment);
      committed = true;
      for (var c : plan.footprint.bed.keySet()) data.refresh(RailwayData.pos(c));
      player.getInventory().setChanged();
      return "ready";
    } finally {
      if (!committed) {
        boolean restoring = level.restoringBlockSnapshots;
        level.restoringBlockSnapshots = true;
        try {
          for (int i = before.size() - 1; i >= 0; i--) {
            var b = before.get(i);
            level.setBlock(b.p, b.state, 3);
            if (b.tag != null && level.getBlockEntity(b.p) != null)
              level.getBlockEntity(b.p).loadWithComponents(b.tag, level.registryAccess());
          }
        } finally {
          level.restoringBlockSnapshots = restoring;
        }
      }
      data.writing = oldWriting;
      level.captureBlockSnapshots = oldCapture;
    }
  }

  public static String remove(ServerPlayer player, UUID id) {
    var level = player.serverLevel();
    var data = RailwayData.get(level);
    var s = data.segment(id);
    if (s == null) return "missing";
    if (factorycraft.trains.TrainData.get(level).occupied(id)) return "occupied";
    if (player.isSpectator() || !player.mayBuild()) return "permission";
    boolean inRange = false;
    for (var cell : s.footprint.bed.keySet()) {
      var p = RailwayData.pos(cell);
      var eye = player.getEyePosition();
      double dx = Math.max(p.getX() - eye.x, Math.max(0, eye.x - (p.getX() + 1))),
          dy = Math.max(p.getY() - eye.y, Math.max(0, eye.y - (p.getY() + 1))),
          dz = Math.max(p.getZ() - eye.z, Math.max(0, eye.z - (p.getZ() + 1)));
      if (dx * dx + dy * dy + dz * dz
          <= player.blockInteractionRange() * player.blockInteractionRange()) {
        inRange = true;
        break;
      }
    }
    if (!inRange) return "reach";

    RailEvents.checking = true;
    try {
      for (var c : s.footprint.clearance) {
        var p = RailwayData.pos(c);
        if (!level.hasChunkAt(p)) return "unloaded";
        if (!level.mayInteract(player, p) || !player.mayUseItemAt(p, Direction.UP, ItemStack.EMPTY))
          return "permission";
        var event =
            new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(
                level, p, level.getBlockState(p), player);
        if (net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event).isCanceled())
          return "permission";
      }
    } finally {
      RailEvents.checking = false;
    }
    for (var stack : data.remove(id))
      if (!player.isCreative()) {
        if (!player.addItem(stack)) player.drop(stack, false);
      }
    return "ready";
  }

  private RailPlacement() {}
}
