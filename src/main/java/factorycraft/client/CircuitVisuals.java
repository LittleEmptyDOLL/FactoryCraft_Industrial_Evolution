package factorycraft.client;

import factorycraft.FactoryCraft;
import factorycraft.circuits.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.*;

/** Snapshot-only client geometry. No graph simulation, neighbor search or resource copies. */
@EventBusSubscriber(modid = FactoryCraft.ID, value = Dist.CLIENT)
public final class CircuitVisuals {
  private record Node(
      BlockPos pos, UUID id, List<Vec3> points, List<CircuitLink> links, Set<Long> chunks) {}

  private record Edge(BlockPos a, BlockPos b, int local, int remote, int color) {}

  private record Wire(Vec3 center, float[] vertices, int rgb) {}

  private static final Map<BlockPos, Node> nodes = new HashMap<>();
  private static final Map<BlockPos, Set<BlockPos>> peers = new HashMap<>();
  private static final Map<Long, Set<BlockPos>> chunks = new HashMap<>();
  private static final Map<Edge, Wire> wires = new HashMap<>();
  private static final Map<BlockPos, Set<Edge>> incident = new HashMap<>();

  public static void receive(CircuitNetwork.Snapshot packet) {
    var tag = packet.data();
    var mc = Minecraft.getInstance();
    if (tag.contains("Menu")) {
      if (mc.player != null
          && mc.player.containerMenu instanceof LogicMenu menu
          && menu.containerId == tag.getInt("Menu")) {
        menu.clientState = tag;
        if (tag.contains("Accepted")) menu.lastAccepted = tag.getBoolean("Accepted");
      }
      return;
    }
    if (mc.level == null || !tag.hasUUID("Identity")) return;
    var pos = BlockPos.of(tag.getLong("Anchor"));
    remove(pos);
    if (!tag.getBoolean("Available")) return;
    var points = new ArrayList<Vec3>();
    for (var raw : tag.getList("Points", 10)) {
      var p = (CompoundTag) raw;
      points.add(new Vec3(p.getDouble("X"), p.getDouble("Y"), p.getDouble("Z")));
    }
    var links = new ArrayList<CircuitLink>();
    for (var raw : tag.getList("Links", 10)) {
      var link = CircuitLink.load((CompoundTag) raw);
      if (link != null) links.add(link);
    }
    var cells = new HashSet<Long>();
    for (long cell : tag.getLongArray("Cells")) {
      var p = BlockPos.of(cell);
      cells.add(net.minecraft.world.level.ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4));
    }
    if (cells.isEmpty())
      cells.add(net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
    for (long c : cells) if (!mc.level.hasChunk((int) c, (int) (c >> 32))) return;
    var node =
        new Node(
            pos,
            tag.getUUID("Identity"),
            List.copyOf(points),
            List.copyOf(links),
            Set.copyOf(cells));
    nodes.put(pos, node);
    for (long c : cells) chunks.computeIfAbsent(c, k -> new HashSet<>()).add(pos);
    for (var link : links) {
      peers.computeIfAbsent(link.peer(), k -> new HashSet<>()).add(pos);
      refresh(node, link);
    }
    for (var other : List.copyOf(peers.getOrDefault(pos, Set.of()))) {
      var n = nodes.get(other);
      if (n != null) for (var link : n.links) if (link.peer().equals(pos)) refresh(n, link);
    }
  }

  private static void remove(BlockPos pos) {
    var old = nodes.remove(pos);
    if (old != null) {
      for (long c : old.chunks) {
        var set = chunks.get(c);
        if (set != null) {
          set.remove(pos);
          if (set.isEmpty()) chunks.remove(c);
        }
      }
      for (var link : old.links) {
        var set = peers.get(link.peer());
        if (set != null) {
          set.remove(pos);
          if (set.isEmpty()) peers.remove(link.peer());
        }
      }
    }
    for (var edge : List.copyOf(incident.getOrDefault(pos, Set.of()))) {
      wires.remove(edge);
      for (var end : List.of(edge.a, edge.b)) {
        var set = incident.get(end);
        if (set != null) {
          set.remove(edge);
          if (set.isEmpty()) incident.remove(end);
        }
      }
    }
  }

  private static void refresh(Node a, CircuitLink link) {
    var b = nodes.get(link.peer());
    if (b == null
        || !b.id.equals(link.identity())
        || link.localPort() >= a.points.size()
        || link.remotePort() >= b.points.size()
        || !b.links.contains(
            new CircuitLink(a.pos, a.id, link.remotePort(), link.localPort(), link.color())))
      return;
    boolean reverse =
        a.pos.compareTo(b.pos) > 0 || a.pos.equals(b.pos) && link.localPort() > link.remotePort();
    var key =
        reverse
            ? new Edge(b.pos, a.pos, link.remotePort(), link.localPort(), link.color())
            : new Edge(a.pos, b.pos, link.localPort(), link.remotePort(), link.color());
    var from = a.points.get(link.localPort()).add(0, link.color() * .035, 0);
    var to = b.points.get(link.remotePort()).add(0, link.color() * .035, 0);
    var center = from.lerp(to, .5);
    float[] vertices = new float[13 * 3];
    double sag = Math.min(.65, from.distanceTo(to) * .035 + .08);
    for (int i = 0; i <= 12; i++) {
      double t = i / 12.;
      var p = from.lerp(to, t).add(0, -sag * 4 * t * (1 - t), 0);
      vertices[3 * i] = (float) (p.x - center.x);
      vertices[3 * i + 1] = (float) (p.y - center.y);
      vertices[3 * i + 2] = (float) (p.z - center.z);
    }
    incident.computeIfAbsent(key.a, k -> new HashSet<>()).add(key);
    incident.computeIfAbsent(key.b, k -> new HashSet<>()).add(key);
    wires.put(key, new Wire(center, vertices, WireColors.rgb(link.color())));
  }

  @SubscribeEvent
  public static void unload(ChunkEvent.Unload e) {
    if (!e.getLevel().isClientSide()) return;
    long c = e.getChunk().getPos().toLong();
    for (var pos : List.copyOf(chunks.getOrDefault(c, Set.of()))) remove(pos);
  }

  @SubscribeEvent
  public static void unload(LevelEvent.Unload e) {
    if (!e.getLevel().isClientSide()) return;
    nodes.clear();
    peers.clear();
    chunks.clear();
    wires.clear();
    incident.clear();
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent e) {
    if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    var mc = Minecraft.getInstance();
    if (mc.level == null || mc.player == null) return;
    var camera = e.getCamera().getPosition();
    var pose = e.getPoseStack();
    pose.pushPose();
    var buffers = mc.renderBuffers().bufferSource();
    var lines = buffers.getBuffer(RenderType.lines());
    for (var wire : wires.values()) {
      if (wire.center.distanceToSqr(camera) > 192 * 192) continue;
      pose.pushPose();
      pose.translate(wire.center.x - camera.x, wire.center.y - camera.y, wire.center.z - camera.z);
      float[] v = wire.vertices;
      float r = (wire.rgb >> 16 & 255) / 255f,
          g = (wire.rgb >> 8 & 255) / 255f,
          b = (wire.rgb & 255) / 255f;
      for (int i = 0; i < 12; i++) {
        int a = 3 * i, z = a + 3;
        float dx = v[z] - v[a],
            dy = v[z + 1] - v[a + 1],
            dz = v[z + 2] - v[a + 2],
            len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len == 0) continue;
        lines
            .addVertex(pose.last(), v[a], v[a + 1], v[a + 2])
            .setColor(r, g, b, 1)
            .setNormal(pose.last(), dx / len, dy / len, dz / len);
        lines
            .addVertex(pose.last(), v[z], v[z + 1], v[z + 2])
            .setColor(r, g, b, 1)
            .setNormal(pose.last(), dx / len, dy / len, dz / len);
      }
      pose.popPose();
    }
    // Contact studs share the exact server-provided attachment positions.
    for (var n : nodes.values())
      for (var p : n.points)
        if (p.distanceToSqr(camera) < 96 * 96)
          for (int c = 0; c < 4; c++) {
            int rgb = WireColors.rgb(c);
            var q = p.add(0, c * .035, 0);
            pose.pushPose();
            pose.translate(q.x - camera.x, q.y - camera.y, q.z - camera.z);
            LevelRenderer.renderLineBox(
                pose,
                lines,
                new AABB(-.025, -.013, -.025, .025, .013, .025),
                (rgb >> 16 & 255) / 255f,
                (rgb >> 8 & 255) / 255f,
                (rgb & 255) / 255f,
                1);
            pose.popPose();
          }
    buffers.endBatch(RenderType.lines());
    pose.popPose();
  }

  private CircuitVisuals() {}
}
