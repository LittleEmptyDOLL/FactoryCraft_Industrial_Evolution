package factorycraft.rail.automation;

import factorycraft.rail.*;
import factorycraft.simulation.rail.*;
import factorycraft.simulation.trains.TrackOccupancy;
import factorycraft.trains.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

public final class TracksidePlacement {
  public static boolean writing, checking;

  public record Plan(
      TracksideKind kind,
      BlockPos anchor,
      UUID edge,
      double offset,
      boolean reverse,
      RailCurve.Frame frame,
      String reason) {
    public List<BlockPos> cells() {
      var out = new ArrayList<BlockPos>();
      for (int i = 0; i < kind.parts(); i++) out.add(TracksideBlock.offset(anchor, kind, i));
      return List.copyOf(out);
    }
  }

  private record Before(BlockPos p, BlockState state, List<ItemStack> drops) {}

  public static BlockPos target(UseOnContext context) {
    var state = context.getLevel().getBlockState(context.getClickedPos());
    return state.canBeReplaced(new BlockPlaceContext(context))
        ? context.getClickedPos()
        : context.getClickedPos().relative(context.getClickedFace());
  }

  private static Map<UUID, RailCurve> nearby(Level level, BlockPos anchor) {
    var result = new LinkedHashMap<UUID, RailCurve>();
    if (level instanceof net.minecraft.server.level.ServerLevel l) {
      var data = RailwayData.get(l);
      for (var s : data.nearby(anchor)) result.put(s.id, s.curve);
    } else {
      int cx = anchor.getX() >> 4, cz = anchor.getZ() >> 4;
      for (int x = cx - 1; x <= cx + 1; x++)
        for (int z = cz - 1; z <= cz + 1; z++) {
          var probe = new BlockPos(x << 4, anchor.getY(), z << 4);
          if (!level.hasChunkAt(probe)) continue;
          var chunk = level.getChunk(x, z);
          for (var be : chunk.getBlockEntities().values())
            if (be instanceof RailCellBlockEntity cell)
              for (var d : cell.descriptors)
                if (result.size() < 256) result.computeIfAbsent(d.id(), id -> d.curve());
        }
    }
    return result;
  }

  public static Plan plan(Level level, BlockPos anchor, TracksideKind kind, float yaw) {
    var center =
        new RailVector(
            anchor.getX() + kind.width * .5, anchor.getY() + .125, anchor.getZ() + kind.width * .5);
    RailCurve.Frame bestFrame = null;
    UUID bestId = null;
    double bestOffset = 0, best = Double.POSITIVE_INFINITY;
    boolean reverse = false;
    var look = RailHeading.fromYaw(yaw).vector();
    double align = -2;
    for (var entry : nearby(level, anchor).entrySet()) {
      var curve = entry.getValue();
      double length = curve.length(), near = 0, distance = Double.POSITIVE_INFINITY;
      int samples = Math.max(8, (int) Math.ceil(length / .75));
      for (int i = 0; i <= samples; i++) {
        double offset = length * i / samples;
        double d = curve.sample(offset, false).position().distance(center);
        if (d < distance) {
          distance = d;
          near = offset;
        }
      }
      double low = Math.max(0, near - length / samples),
          high = Math.min(length, near + length / samples);
      for (int i = 0; i < 12; i++) {
        double a = (2 * low + high) / 3, b = (low + 2 * high) / 3;
        if (curve.sample(a, false).position().distance(center)
            < curve.sample(b, false).position().distance(center)) high = b;
        else low = a;
      }
      double offset = Math.clamp(Math.rint((low + high) * 4) / 8, .125, length - .125);
      var frame = curve.sample(offset, false);
      var delta = center.subtract(frame.position());
      double side = -frame.tangent().z() * delta.x() + frame.tangent().x() * delta.z();
      double minSide = kind == TracksideKind.STOP ? 2.1 : 1.65;
      if (Math.abs(side) < minSide || Math.abs(side) > 4.25 || Math.abs(delta.y()) > 1.25) continue;
      double d = delta.length(), alignment = Math.abs(frame.tangent().dot(look));
      if (d < best - 1e-6
          || Math.abs(d - best) < 1e-6
              && (alignment > align + 1e-8
                  || Math.abs(alignment - align) < 1e-8
                      && (bestId == null || entry.getKey().compareTo(bestId) < 0))) {
        best = d;
        align = alignment;
        bestId = entry.getKey();
        bestOffset = offset;
        bestFrame = frame;
        reverse = side < 0;
      }
    }
    return new Plan(
        kind,
        anchor.immutable(),
        bestId,
        bestOffset,
        reverse,
        bestFrame,
        bestId == null ? "unattached" : "ready");
  }

  public static String inspect(Level l, Player p, Plan plan) {
    if (!plan.reason.equals("ready")) return plan.reason;
    if (l instanceof net.minecraft.server.level.ServerLevel server) {
      var data = RailAutomationData.get(server);
      String attachment = data.attachmentStatus(plan.kind, plan.edge, plan.offset, plan.reverse);
      if (!attachment.equals("ready")) return attachment;
    }
    var block = TracksideContent.BLOCKS.get(plan.kind).get();
    var item = new ItemStack(TracksideContent.ITEMS.get(plan.kind).get());
    for (int i = 0; i < plan.kind.parts(); i++) {
      var at = TracksideBlock.offset(plan.anchor, plan.kind, i);
      if (!RailPlan.loaded(l, at)) return "unloaded";
      if (l.isOutsideBuildHeight(at) || !l.getWorldBorder().isWithinBounds(at)) return "border";
      var old = l.getBlockState(at);
      var context =
          new BlockPlaceContext(
              l,
              p,
              InteractionHand.MAIN_HAND,
              item,
              new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
      if (old.hasBlockEntity() || !old.getFluidState().isEmpty() || !old.canBeReplaced(context))
        return "blocked";
      var state = block.defaultBlockState().setValue(TracksideBlock.PART, i);
      var shape = state.getCollisionShape(l, at).move(at.getX(), at.getY(), at.getZ());
      if (!shape.isEmpty() && !l.noCollision(null, shape.bounds())) return "entity";
      if (l instanceof net.minecraft.server.level.ServerLevel s
          && RailwayData.get(s).obstructed(at, state)) return "blocked";
      if (i < plan.kind.width * plan.kind.width) {
        var below = at.below();
        if (!RailPlan.loaded(l, below)
            || !l.getBlockState(below).isFaceSturdy(l, below, Direction.UP)) return "foundation";
      }
      if (!p.mayBuild() || !p.mayUseItemAt(at, Direction.UP, item)) return "permission";
      if (p instanceof ServerPlayer sp && !l.mayInteract(sp, at)) return "permission";
    }
    return "ready";
  }

  public static String place(ServerPlayer player, InteractionHand hand, Plan plan) {
    var level = player.serverLevel();
    var stack = player.getItemInHand(hand);
    if (player.isSpectator() || !player.mayBuild() || !player.canInteractWithBlock(plan.anchor, 0))
      return "permission";
    if (!stack.is(TracksideContent.ITEMS.get(plan.kind).get()) || stack.isEmpty()) return "price";
    var original = stack.copy();
    String reason = inspect(level, player, plan);
    if (!reason.equals("ready")) return reason;
    var scope = factorycraft.research.ResearchTeams.resolve(player);
    if (scope.isEmpty()) return "permission";
    if (!player.isCreative()
        && !factorycraft.research.ResearchCatalog.get(level)
            .allowed(
                scope.get(),
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    "factorycraft", plan.kind.id),
                level)) return "research";
    var before = new ArrayList<Before>();
    var snapshots = new ArrayList<BlockSnapshot>();
    var staged = new ArrayList<Before>();
    boolean oldCapture = level.captureBlockSnapshots, oldWriting = writing, committed = false;
    level.captureBlockSnapshots = false;
    writing = true;
    try {
      for (var at : plan.cells()) {
        var state = level.getBlockState(at);
        snapshots.add(BlockSnapshot.create(level.dimension(), level, at));
        before.add(
            new Before(
                at,
                state,
                player.isCreative() || state.isAir()
                    ? List.of()
                    : Block.getDrops(state, level, at, null, player, original)));
      }
      var facing =
          Direction.fromYRot(
              Math.toDegrees(
                  Math.atan2(
                      -plan.frame.tangent().x() * (plan.reverse ? -1 : 1),
                      plan.frame.tangent().z() * (plan.reverse ? -1 : 1))));
      for (int i = 0; i < before.size(); i++) {
        var old = before.get(i);
        if (!level.getBlockState(old.p).equals(old.state) || level.getBlockEntity(old.p) != null)
          return "blocked";
        staged.add(old);
        if (!level.setBlock(
            old.p,
            TracksideContent.BLOCKS
                .get(plan.kind)
                .get()
                .defaultBlockState()
                .setValue(TracksideBlock.PART, i)
                .setValue(TracksideBlock.FACING, facing),
            18)) return "blocked";
      }
      if (EventHooks.onMultiBlockPlace(player, snapshots, Direction.UP)) return "permission";
      if (!ItemStack.matches(stack, original)) return "price";
      if (player.serverLevel() != level
          || player.isSpectator()
          || !player.mayBuild()
          || !player.canInteractWithBlock(plan.anchor, 0)
          || !factorycraft.research.ResearchTeams.resolve(player).equals(scope))
        return "permission";
      if (!(level.getBlockEntity(plan.anchor) instanceof TracksideBlockEntity root))
        return "blocked";
      for (int i = 0; i < plan.kind.parts(); i++) {
        var cell = TracksideBlock.offset(plan.anchor, plan.kind, i);
        if (!level.mayInteract(player, cell) || !player.mayUseItemAt(cell, Direction.UP, stack))
          return "permission";
        var s = level.getBlockState(cell);
        if (s.getBlock() != root.getBlockState().getBlock() || s.getValue(TracksideBlock.PART) != i)
          return "blocked";
      }
      var system = RailAutomationData.get(level);
      String attachment = system.attachmentStatus(plan.kind, plan.edge, plan.offset, plan.reverse);
      if (!attachment.equals("ready")) return attachment;
      if (system.device(root.identity) != null || system.at(plan.anchor) != null) return "occupied";
      var device =
          new RailAutomationData.Device(
              root.identity,
              player.getUUID(),
              scope.get(),
              plan.anchor,
              plan.kind,
              plan.edge,
              plan.offset,
              plan.reverse);
      var config = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
      if (config.contains("RailDeviceSettings"))
        device.settings(config.getCompound("RailDeviceSettings"));
      if (config.contains("DirectCircuit"))
        root.circuit(true).load(config.getCompound("DirectCircuit"));
      RailAutomationData.get(level).add(device);
      if (!player.isCreative()) stack.shrink(1);
      committed = true;
      root.sync();
      if (root.circuit(false) != null)
        factorycraft.circuits.CircuitSimulation.add(root.circuit(false));
      for (var old : before) {
        if (!old.drops.isEmpty()) {
          var drops = new ArrayList<net.minecraft.world.entity.item.ItemEntity>();
          for (var s : old.drops)
            drops.add(
                new net.minecraft.world.entity.item.ItemEntity(
                    level, old.p.getX() + .5, old.p.getY() + .5, old.p.getZ() + .5, s));
          net.neoforged.neoforge.common.CommonHooks.handleBlockDrops(
              level, old.p, old.state, null, drops, player, original);
        }
        old.state.updateIndirectNeighbourShapes(level, old.p, 3, 512);
        level.getBlockState(old.p).updateNeighbourShapes(level, old.p, 3, 512);
      }
      player.getInventory().setChanged();
      return "ready";
    } finally {
      if (!committed) {
        boolean restoring = level.restoringBlockSnapshots;
        level.restoringBlockSnapshots = true;
        try {
          for (int i = staged.size() - 1; i >= 0; i--) {
            var old = staged.get(i);
            level.setBlock(old.p, old.state, 18);
          }
        } finally {
          level.restoringBlockSnapshots = restoring;
        }
      }
      writing = oldWriting;
      level.captureBlockSnapshots = oldCapture;
    }
  }

  public static boolean occupied(RailAutomationData data, RailAutomationData.Device device) {
    var trains = TrainData.get(data.level);
    for (var owner : trains.occupancy.owners(device.point.edge())) {
      var train = trains.train(owner);
      if (train == null) continue;
      for (var span : TrackOccupancy.span(train.path, train.rear(), train.front()))
        if (span.edge().equals(device.point.edge())
            && span.min() <= device.point.offset() + .25
            && span.max() >= device.point.offset() - .25) return true;
    }
    if (data.rails.graph.edge(device.point.edge()) == null)
      return !trains.occupancy.owners(device.point.edge()).isEmpty();
    if (device.kind != TracksideKind.STOP) {
      boolean paired =
          data.points.on(device.point.edge()).stream()
              .anyMatch(
                  p ->
                      p.boundary()
                          && !p.id().equals(device.id)
                          && Math.abs(p.offset() - device.point.offset()) < 1e-7);
      if (!paired) {
        var a = data.blocks.at(device.point.edge(), device.point.offset(), false);
        var b = data.blocks.at(device.point.edge(), device.point.offset(), true);
        if (a == null || b == null) return true;
        if (data.claims.reservation(a.id) != null || data.claims.reservation(b.id) != null)
          return true;
        var owners = new HashSet<>(data.claims.occupants(a.id));
        owners.addAll(data.claims.occupants(b.id));
        if (owners.size() > 1) return true;
      }
    }
    return false;
  }

  public static String remove(ServerPlayer p, BlockPos clicked) {
    var root = TracksideBlock.owner(p.level(), clicked);
    if (root == null || !root.ready()) return "unloaded";
    var data = RailAutomationData.get(p.serverLevel());
    var device = data.device(root.identity);
    if (device == null || !device.mayUse(p) || !p.canInteractWithBlock(clicked, 0))
      return "permission";
    if (occupied(data, device)) return "occupied";
    checking = true;
    try {
      for (var cell : device.cells()) {
        if (!p.level().mayInteract(p, cell)
            || !p.mayUseItemAt(cell, Direction.UP, p.getMainHandItem())) return "permission";
        if (net.neoforged.neoforge.common.NeoForge.EVENT_BUS
            .post(
                new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(
                    p.level(), cell, p.level().getBlockState(cell), p))
            .isCanceled()) return "permission";
      }
    } finally {
      checking = false;
    }
    if (p.serverLevel() != data.level
        || !root.ready()
        || data.device(device.id) != device
        || !device.mayUse(p)
        || !p.canInteractWithBlock(clicked, 0)) return "permission";
    if (occupied(data, device)) return "occupied";
    dismantle(data, device, !p.isCreative());
    return "ready";
  }

  private static void dismantle(
      RailAutomationData data, RailAutomationData.Device device, boolean drop) {
    var root = data.entity(device);
    var circuit = root == null ? null : root.circuit(false);
    var saved = circuit == null ? null : circuit.save();
    if (data.retire(device.id, drop, saved) == null) return;
    if (circuit != null) factorycraft.circuits.CircuitSimulation.destroyed(circuit);
    boolean old = writing;
    writing = true;
    try {
      for (var cell : device.cells())
        if (data.level.hasChunkAt(cell)) {
          var state = data.level.getBlockState(cell);
          if (state.getBlock() instanceof TracksideBlock block
              && block.kind == device.kind
              && TracksideBlock.origin(cell, state).equals(device.anchor))
            data.level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
        }
    } finally {
      writing = old;
    }
  }

  public static void environmentRemoved(Level level, BlockPos p, BlockState state) {
    if (!(level instanceof net.minecraft.server.level.ServerLevel l)) return;
    var data = RailAutomationData.get(l);
    var device = data.at(TracksideBlock.origin(p, state));
    if (device != null)
      dismantle(
          data,
          device,
          l.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS));
  }

  private TracksidePlacement() {}
}
