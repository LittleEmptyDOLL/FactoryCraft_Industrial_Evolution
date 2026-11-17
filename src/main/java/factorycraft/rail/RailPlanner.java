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
      long selectedAt) {}

  private static final Map<UUID, Selection> selections = new HashMap<>();

  public static Selection get(ServerPlayer p) {
    var s = selections.get(p.getUUID());
    if (s != null
        && (s.dimension() != p.level().dimension()
            || p.level().getGameTime() - s.selectedAt() > 12000)) {
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
      double distance = 4, alignment = -Double.MAX_VALUE;
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
                p.level().getGameTime())
            : new Selection(s.start().reverse(), s.goal(), s.dimension(), p.level().getGameTime());
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
      s = new Selection(end, -1, p.level().dimension(), p.level().getGameTime());
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
        var next = new Selection(end, -1, p.level().dimension(), p.level().getGameTime());
        selections.put(p.getUUID(), next);
        RailNetwork.send(p, next);
      }
      return result;
    } catch (IllegalArgumentException invalid) {
      return invalid.getMessage();
    }
  }

  private RailPlanner() {}
}
