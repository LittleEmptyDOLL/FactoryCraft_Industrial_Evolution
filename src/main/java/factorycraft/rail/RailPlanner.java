package factorycraft.rail;

import factorycraft.simulation.rail.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.*;

/** Per-player nonowning selection, never stored inside or charged to a preview item. */
public final class RailPlanner {
  public record Selection(
      RailEnd start,
      int goal,
      net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
      long selectedAt,
      UUID anchor,
      int slot) {}

  private static final Map<UUID, Selection> selections = new HashMap<>();

  public static Selection get(ServerPlayer p) {
    var s = selections.get(p.getUUID());
    if (s != null
        && (s.dimension() != p.level().dimension()
            || p.level().getGameTime() - s.selectedAt() > 1200
            || !p.isAlive()
            || p.isSpectator()
            || p.getInventory().selected != s.slot()
            || !p.getMainHandItem().is(RailContent.RAIL.get())
                && !p.getOffhandItem().is(RailContent.RAIL.get())
            || !RailwayData.get(p.serverLevel()).available(s.anchor()))) {
      clear(p);
      return null;
    }
    return s;
  }

  public static void clear(ServerPlayer p) {
    selections.remove(p.getUUID());
    RailNetwork.send(p, null);
  }

  public static void forget(UUID id) {
    selections.remove(id);
  }

  public static RailEnd target(
      net.minecraft.world.level.Level level,
      BlockHitResult hit,
      boolean source,
      RailHeading heading) {
    if (hit.getType() != HitResult.Type.BLOCK || !level.hasChunkAt(hit.getBlockPos())) return null;
    if (level.getBlockEntity(hit.getBlockPos()) instanceof RailCellBlockEntity cell) {
      RailEnd best = null;
      double distance = Double.POSITIVE_INFINITY, alignment = -Double.MAX_VALUE;
      for (var d : cell.descriptors)
        for (var e : List.of(d.a(), d.b())) {
          var q = e.point();
          double sq = hit.getLocation().distanceToSqr(q.x(), q.y(), q.z());
          var candidate = source ? (e == d.a() ? e.reverse() : e) : (e == d.a() ? e : e.reverse());
          double dot = candidate.heading().vector().dot(heading.vector());
          if (sq < distance - 1e-8 || Math.abs(sq - distance) < 1e-8 && dot > alignment) {
            best = candidate;
            distance = sq;
            alignment = dot;
          }
        }
      return best;
    }
    var p = hit.getBlockPos().relative(hit.getDirection());
    return new RailEnd(p.getX(), p.getY(), p.getZ(), heading);
  }

  public static void edit(ServerPlayer p, int action) {
    var s = get(p);
    if (action == 2) {
      clear(p);
      return;
    }
    if (s == null) return;
    var next =
        action == 0
            ? new Selection(
                s.start(),
                s.goal() < 0 ? 0 : s.goal() == 15 ? -1 : s.goal() + 1,
                s.dimension(),
                p.level().getGameTime(),
                s.anchor(),
                s.slot())
            : new Selection(
                s.start().reverse(),
                s.goal(),
                s.dimension(),
                p.level().getGameTime(),
                s.anchor(),
                s.slot());
    selections.put(p.getUUID(), next);
    RailNetwork.send(p, next);
  }

  public static String click(ServerPlayer p, BlockHitResult hit) {
    if (p.isSpectator()
        || p.getEyePosition().distanceToSqr(hit.getLocation())
            > p.blockInteractionRange() * p.blockInteractionRange() + .01) return "reach";
    var look = p.getLookAngle();
    var heading = RailHeading.nearest(look.x, look.z);
    var s = get(p);
    var end = target(p.level(), hit, s == null, heading);
    if (end == null) return "endpoint";
    if (s == null) {
      if (!p.level().mayInteract(p, hit.getBlockPos()) || !p.mayBuild()) return "permission";
      if (!(p.level().getBlockEntity(hit.getBlockPos()) instanceof RailCellBlockEntity cell)) {
        try {
          return RailPlacement.place(p, new RailPlan(starter(end)));
        } catch (IllegalArgumentException invalid) {
          return invalid.getMessage();
        }
      }
      UUID anchor = null;
      for (var id : cell.segments()) {
        var owner = RailwayData.get(p.serverLevel()).segment(id);
        if (owner != null
            && (owner.curve.start.samePosition(end) || owner.curve.end.samePosition(end))) {
          anchor = id;
          break;
        }
      }
      if (anchor == null || !RailwayData.get(p.serverLevel()).available(anchor)) return "unloaded";
      s =
          new Selection(
              end,
              -1,
              p.level().dimension(),
              p.level().getGameTime(),
              anchor,
              p.getInventory().selected);
      selections.put(p.getUUID(), s);
      RailNetwork.send(p, s);
      return "selected";
    }
    if (!p.level().getBlockState(hit.getBlockPos()).is(RailContent.BED.get())) {
      var delta = end.point().subtract(s.start().point());
      end =
          new RailEnd(
              end.x(),
              end.y(),
              end.z(),
              s.goal() < 0 ? RailHeading.nearest(delta.x(), delta.z()) : new RailHeading(s.goal()));
    }
    try {
      var plan = new RailPlan(RailCurve.plan(s.start(), end));
      var result = RailPlacement.place(p, plan);
      if (result.equals("ready")) {
        clear(p);
      }
      return result;
    } catch (IllegalArgumentException invalid) {
      return invalid.getMessage();
    }
  }

  public static RailCurve starter(RailEnd start) {
    var v = start.heading().vector();
    double scale =
        2
            / Math.min(
                Math.abs(v.x()) < 1e-8 ? 1 : Math.abs(v.x()),
                Math.abs(v.z()) < 1e-8 ? 1 : Math.abs(v.z()));
    return RailCurve.plan(
        start,
        new RailEnd(
            start.x() + (int) Math.round(v.x() * scale),
            start.y(),
            start.z() + (int) Math.round(v.z() * scale),
            start.heading()));
  }

  public static void tick(ServerPlayer player) {
    if (selections.containsKey(player.getUUID())) get(player);
  }

  public static void removed(ServerLevel level, UUID anchor) {
    for (var entry : List.copyOf(selections.entrySet()))
      if (entry.getValue().dimension() == level.dimension()
          && entry.getValue().anchor().equals(anchor)) {
        var player = level.getServer().getPlayerList().getPlayer(entry.getKey());
        if (player != null) clear(player);
        else selections.remove(entry.getKey());
      }
  }

  private RailPlanner() {}
}
