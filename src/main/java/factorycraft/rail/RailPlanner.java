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
      int slot,
      RailEnd other,
      int side) {
    public RailConnectionResolver.Source source() {
      return new RailConnectionResolver.Source(start, other, side);
    }
  }

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
    if (hit.getType() != HitResult.Type.BLOCK || !RailPlan.loaded(level, hit.getBlockPos()))
      return null;
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

  public static RailEnd destination(
      net.minecraft.world.level.Level level, BlockHitResult hit, RailHeading look, int override) {
    var end = target(level, hit, false, look);
    if (end != null
        && override >= 0
        && !(level.getBlockEntity(hit.getBlockPos()) instanceof RailCellBlockEntity))
      return new RailEnd(end.x(), end.y(), end.z(), new RailHeading(override));
    return end;
  }

  public static RailConnectionResolver.Request request(
      net.minecraft.world.level.Level level,
      BlockHitResult hit,
      RailConnectionResolver.Source source,
      RailHeading look,
      int override) {
    if (hit.getType() != HitResult.Type.BLOCK
        || !RailPlan.loaded(level, hit.getBlockPos())
        || level.getBlockState(hit.getBlockPos()).isAir()) return null;
    if (level.getBlockEntity(hit.getBlockPos()) instanceof RailCellBlockEntity cell) {
      var ends = new ArrayList<RailEnd>();
      for (var d :
          cell.descriptors.stream().sorted(Comparator.comparing(RailDescriptor::id)).toList()) {
        ends.add(d.a());
        ends.add(d.b().reverse());
      }
      ends.sort(
          Comparator.<RailEnd>comparingDouble(
                  e -> {
                    var q = e.point();
                    return hit.getLocation().distanceToSqr(q.x(), q.y(), q.z());
                  })
              .thenComparingDouble(e -> -e.heading().vector().dot(look.vector()))
              .thenComparing(RailEnd::nodeKey));
      return ends.isEmpty()
          ? null
          : new RailConnectionResolver.Request(source, ends.stream().distinct().toList(), true);
    }
    return new RailConnectionResolver.Request(
        source, List.of(destination(level, hit, look, override)), override >= 0);
  }

  public static void edit(ServerPlayer p, int action) {
    var s = get(p);
    if (action == 2) {
      clear(p);
      return;
    }
    if (s == null) return;
    var next =
        new Selection(
            s.start(),
            action == 0
                ? s.goal() < 0
                    ? (RailHeading.fromYaw(p.getYRot()).index() + 1) % 16
                    : s.goal() == 15 ? -1 : s.goal() + 1
                : s.goal(),
            s.dimension(),
            p.level().getGameTime(),
            s.anchor(),
            s.slot(),
            s.other(),
            action == 1 ? s.side() == 0 ? 2 : s.side() == 2 ? 1 : 0 : s.side());
    selections.put(p.getUUID(), next);
    RailNetwork.send(p, next);
  }

  public static String click(ServerPlayer p, BlockHitResult hit) {
    if (p.isSpectator()
        || p.getEyePosition().distanceToSqr(hit.getLocation())
            > p.blockInteractionRange() * p.blockInteractionRange() + .01) return "reach";
    var look = p.getLookAngle();
    var heading = RailHeading.fromYaw(p.getYRot());
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
      RailDescriptor chosen = null;
      RailEnd preferred = null;
      double best = Double.POSITIVE_INFINITY, align = -2;
      for (var descriptor :
          cell.descriptors.stream().sorted(Comparator.comparing(RailDescriptor::id)).toList()) {
        // The clicked cell may be a curved/multi-axis projection: compare its physical curve,
        // not the player's standing position or only the distances to far-away endpoints.
        var curve = RailwayData.get(p.serverLevel()).segment(descriptor.id());
        if (curve == null) continue;
        double distance = Double.POSITIVE_INFINITY;
        for (int i = 0; i <= 32; i++) {
          var point = curve.curve.point(i / 32.);
          distance =
              Math.min(distance, hit.getLocation().distanceToSqr(point.x(), point.y(), point.z()));
        }
        double alignment =
            Math.max(
                Math.abs(descriptor.a().heading().vector().dot(heading.vector())),
                Math.abs(descriptor.b().heading().vector().dot(heading.vector())));
        if (distance < best - 1e-8 || Math.abs(distance - best) < 1e-8 && alignment > align) {
          chosen = descriptor;
          best = distance;
          align = alignment;
          var a = descriptor.a().point();
          var b = descriptor.b().point();
          preferred =
              hit.getLocation().distanceToSqr(a.x(), a.y(), a.z())
                      <= hit.getLocation().distanceToSqr(b.x(), b.y(), b.z())
                  ? descriptor.a().reverse()
                  : descriptor.b();
        }
      }
      if (chosen == null || !RailwayData.get(p.serverLevel()).available(chosen.id()))
        return "unloaded";
      var other = preferred.samePosition(chosen.a()) ? chosen.b() : chosen.a().reverse();
      s =
          new Selection(
              preferred,
              -1,
              p.level().dimension(),
              p.level().getGameTime(),
              chosen.id(),
              p.getInventory().selected,
              other,
              0);
      selections.put(p.getUUID(), s);
      RailNetwork.send(p, s);
      return "selected";
    }
    try {
      var intent = request(p.level(), hit, s.source(), heading, s.goal());
      if (intent == null) return "unloaded";
      var resolved = RailConnectionResolver.resolve(intent);
      if (!resolved.valid()) return resolved.reason();
      var plan = new RailPlan(resolved.curve());
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
