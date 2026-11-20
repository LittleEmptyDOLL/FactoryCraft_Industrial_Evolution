package factorycraft.simulation.rail;

import java.util.*;

/**
 * Budgeted directed frontier/predecessor search adapted from Create entity/Navigation.search (MIT).
 * Copyright (c) The Create Team / The Creators of Create. Upstream37ea4afc; see notices. Changes:
 * FactoryCraft UUID geometry, fractional source/targets, caller-provided Factorio costs,
 * deterministic ties, resumable work, revision quarantine and no global train scan/dependencies.
 */
public final class RailPathSearch {
  public enum Status {
    SEARCHING,
    FOUND,
    NO_PATH,
    LIMIT,
    STALE
  }

  public record Target(UUID id, RailEdgePoints.Point point, int priority) {
    public Target {
      if (point.kind() != RailEdgePoints.Kind.STOP || priority < 0 || priority > 255)
        throw new IllegalArgumentException("Destination");
    }
  }

  public record Route(
      Target target, List<RailGraph.Directed> edges, double distance, double cost) {}

  @FunctionalInterface
  public interface Rules {
    /** Extra nonnegative cost; infinity means illegal direction/point, not a traversable red. */
    double penalty(RailGraph.Directed edge, double canonicalFrom, double canonicalTo);
  }

  private record Key(RailGraph.Directed edge, boolean initial) {}

  private record Node(Key key, double position, double distance, double cost, Node parent) {}

  private record Candidate(Target target, Node node, double distance, double cost) {}

  private final RailGraph graph;
  private final long revision;
  private final Rules rules;
  private final int maxNodes, highestPriority;
  private final Map<UUID, List<Target>> targets = new HashMap<>();
  private final Map<Key, Double> best = new HashMap<>();
  private final PriorityQueue<Node> frontier =
      new PriorityQueue<>(
          Comparator.comparingDouble(Node::cost)
              .thenComparing(n -> n.key.edge.edge())
              .thenComparing(n -> n.key.edge.reverse())
              .thenComparing(n -> n.key.initial));
  private Status status = Status.SEARCHING;
  private Candidate candidate;
  private Route result;
  private int visited;

  public RailPathSearch(
      RailGraph graph,
      RailGraph.Directed start,
      double canonicalOffset,
      Collection<Target> destinations,
      Rules rules,
      int maxNodes) {
    this.graph = graph;
    revision = graph.revisions();
    this.rules = rules;
    if (maxNodes < 1 || maxNodes > 1_000_000) throw new IllegalArgumentException("Search limit");
    this.maxNodes = maxNodes;
    var edge = graph.edge(start.edge());
    if (edge == null
        || !Double.isFinite(canonicalOffset)
        || canonicalOffset < 0
        || canonicalOffset > edge.curve().length()) throw new IllegalArgumentException("Start");
    int priority = -1;
    for (var target : destinations) {
      targets.computeIfAbsent(target.point.edge(), k -> new ArrayList<>()).add(target);
      priority = Math.max(priority, target.priority());
    }
    highestPriority = priority;
    if (destinations.isEmpty()) status = Status.NO_PATH;
    double position = start.reverse() ? edge.curve().length() - canonicalOffset : canonicalOffset;
    var node = new Node(new Key(start, true), position, 0, 0, null);
    frontier.add(node);
    best.put(node.key, 0.0);
  }

  public Status status() {
    return status;
  }

  public int visited() {
    return visited;
  }

  public Route result() {
    return result;
  }

  private double penalty(RailGraph.Directed direction, double from, double to, double length) {
    double value =
        rules.penalty(
            direction,
            direction.reverse() ? length - from : from,
            direction.reverse() ? length - to : to);
    if (Double.isNaN(value) || value < 0) throw new IllegalArgumentException("Negative route cost");
    return value;
  }

  public int advance(int budget) {
    if (budget < 0) throw new IllegalArgumentException("Budget");
    if (status != Status.SEARCHING) return 0;
    if (graph.revisions() != revision) {
      status = Status.STALE;
      return 0;
    }
    int work = 0;
    while (work < budget && !frontier.isEmpty()) {
      var node = frontier.remove();
      if (node.cost > best.getOrDefault(node.key, Double.POSITIVE_INFINITY)) continue;
      if (++visited > maxNodes) {
        status = Status.LIMIT;
        return work;
      }
      work++;
      var edge = graph.edge(node.key.edge.edge());
      double length = edge.curve().length();
      for (var target : targets.getOrDefault(edge.id(), List.of())) {
        if (target.point.reverse() != node.key.edge.reverse()) continue;
        double offset =
            node.key.edge.reverse() ? length - target.point.offset() : target.point.offset();
        if (offset < node.position - 1e-8 || offset > length) continue;
        double extra = penalty(node.key.edge, node.position, offset, length);
        if (!Double.isFinite(extra)) continue;
        var next =
            new Candidate(
                target,
                node,
                node.distance + offset - node.position,
                node.cost + offset - node.position + extra);
        if (candidate == null
            || next.target.priority() > candidate.target.priority()
            || next.target.priority() == candidate.target.priority()
                && (next.cost < candidate.cost - 1e-8
                    || Math.abs(next.cost - candidate.cost) < 1e-8
                        && next.target.id().compareTo(candidate.target.id()) < 0)) candidate = next;
      }
      double extra = penalty(node.key.edge, node.position, length, length);
      if (Double.isFinite(extra)) {
        double distance = node.distance + length - node.position;
        double cost = node.cost + length - node.position + extra;
        for (var direction : graph.successors(node.key.edge)) {
          var key = new Key(direction, false);
          if (cost >= best.getOrDefault(key, Double.POSITIVE_INFINITY) - 1e-9) continue;
          best.put(key, cost);
          frontier.add(new Node(key, 0, distance, cost, node));
        }
      }
      if (candidate != null
          && candidate.target.priority() == highestPriority
          && (frontier.isEmpty() || frontier.peek().cost >= candidate.cost - 1e-9)) {
        finish();
        return work;
      }
    }
    if (frontier.isEmpty()) finish();
    return work;
  }

  private void finish() {
    if (candidate == null) {
      status = Status.NO_PATH;
      return;
    }
    var path = new ArrayList<RailGraph.Directed>();
    for (var n = candidate.node; n != null; n = n.parent) path.add(n.key.edge);
    Collections.reverse(path);
    result = new Route(candidate.target, List.copyOf(path), candidate.distance, candidate.cost);
    status = Status.FOUND;
  }
}
