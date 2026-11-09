package factorycraft.multiblock;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.*;

/** Shared physical/visual schema. Channels refer to the controller's sole existing fluid cells. */
public final class MachinePorts {
  public enum Mode {
    INPUT,
    OUTPUT,
    BOTH
  }

  public record Port(
      String name, BlockPos at, BlockPos oddAt, Direction face, Mode mode, int input, int output) {
    public BlockPos local(Direction facing) {
      return oddAt != null && facing.getAxis() == Direction.Axis.X ? oddAt : at;
    }

    public Direction side(Direction facing) {
      return switch (face) {
        case NORTH -> facing;
        case SOUTH -> facing.getOpposite();
        case EAST -> facing.getClockWise();
        case WEST -> facing.getCounterClockWise();
        default -> face;
      };
    }

    public BlockPos position(MachineProfile profile, BlockPos controller, Direction facing) {
      var p = local(facing);
      return profile.offset(controller, facing, p.getX(), p.getY(), p.getZ());
    }
  }

  private static final JsonObject DATA;

  static {
    try (var in =
        Objects.requireNonNull(
            MachinePorts.class.getResourceAsStream("/factorycraft/machine_layouts.json"))) {
      DATA =
          JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
              .getAsJsonObject()
              .getAsJsonObject("machines");
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static BlockPos pos(JsonArray a) {
    return new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
  }

  public static BlockPos controller(MachineProfile profile) {
    var d = DATA.getAsJsonObject(profile.id);
    var p =
        d != null && d.has("controller")
            ? pos(d.getAsJsonArray("controller"))
            : new BlockPos(profile.layout.width() / 2, 0, 0);
    if (!inside(profile, p)) throw new IllegalArgumentException("Controller outside " + profile.id);
    return p;
  }

  private static boolean inside(MachineProfile p, BlockPos at) {
    return at.getX() >= 0
        && at.getX() < p.layout.width()
        && at.getY() >= 0
        && at.getY() < p.layout.height()
        && at.getZ() >= 0
        && at.getZ() < p.layout.depth();
  }

  private static final Map<MachineProfile, List<Port>> CACHE = new EnumMap<>(MachineProfile.class);

  public static synchronized List<Port> definitions(MachineProfile profile) {
    return CACHE.computeIfAbsent(
        profile,
        p -> {
          var out = new ArrayList<Port>();
          var d = DATA.getAsJsonObject(p.id);
          if (d != null && d.has("ports"))
            for (var e : d.getAsJsonArray("ports")) {
              var o = e.getAsJsonObject();
              var at = pos(o.getAsJsonArray("at"));
              if (at.getX() < 0
                  || at.getX() >= p.layout.width()
                  || at.getY() < 0
                  || at.getY() >= p.layout.height()
                  || at.getZ() < 0
                  || at.getZ() >= p.layout.depth())
                throw new IllegalArgumentException("Port outside " + p.id);
              int input = o.has("input") ? o.get("input").getAsInt() : -1,
                  output = o.has("output") ? o.get("output").getAsInt() : -1;
              var mode = Mode.valueOf(o.get("mode").getAsString());
              if (mode == Mode.INPUT && (input < 0 || output >= 0)
                  || mode == Mode.OUTPUT && (output < 0 || input >= 0)
                  || mode == Mode.BOTH && (input < 0 || output < 0))
                throw new IllegalArgumentException("Invalid port channels");
              if (Direction.byName(o.get("face").getAsString()) == null)
                throw new IllegalArgumentException("Invalid port face");
              out.add(
                  new Port(
                      o.get("name").getAsString(),
                      at,
                      o.has("odd_at") ? pos(o.getAsJsonArray("odd_at")) : null,
                      Direction.byName(o.get("face").getAsString()),
                      mode,
                      input,
                      output));
            }
          var occupied = new HashSet<String>();
          int channels =
              switch (p) {
                case REFINERY -> 5;
                case BOILER, ENGINE -> 2;
                case TANK, PUMP -> 1;
                default -> 4;
              };
          for (var port : out)
            for (var facing : Direction.Plane.HORIZONTAL) {
              var at = port.local(facing);
              if (!inside(p, at) || port.input() >= channels || port.output() >= channels)
                throw new IllegalArgumentException("Invalid physical channel: " + p.id);
              boolean edge =
                  switch (port.face()) {
                    case NORTH -> at.getZ() == 0;
                    case SOUTH -> at.getZ() == p.layout.depth() - 1;
                    case WEST -> at.getX() == 0;
                    case EAST -> at.getX() == p.layout.width() - 1;
                    case UP -> at.getY() == p.layout.height() - 1;
                    case DOWN -> at.getY() == 0;
                  };
              if (!edge || !occupied.add(facing + "/" + at + "/" + port.face()))
                throw new IllegalArgumentException("Ambiguous/internal port: " + p.id);
            }
          return List.copyOf(out);
        });
  }

  private static final Map<MachineProfile, List<Port>> MIRRORED =
      new EnumMap<>(MachineProfile.class);

  public static synchronized List<Port> definitions(MachineProfile profile, boolean mirror) {
    if (!mirror) return definitions(profile);
    return MIRRORED.computeIfAbsent(
        profile,
        p ->
            definitions(p).stream()
                .map(
                    port ->
                        new Port(
                            port.name(),
                            reflect(p, port.at()),
                            port.oddAt() == null ? null : reflect(p, port.oddAt()),
                            port.face().getAxis() == Direction.Axis.X
                                ? port.face().getOpposite()
                                : port.face(),
                            port.mode(),
                            port.input(),
                            port.output()))
                .toList());
  }

  private static BlockPos reflect(MachineProfile p, BlockPos at) {
    return new BlockPos(p.layout.width() - 1 - at.getX(), at.getY(), at.getZ());
  }

  public static List<Port> forController(factorycraft.blockentity.FactoryBlockEntity be) {
    var owner = AssemblyAccess.owner(be);
    return owner == null
        ? List.of()
        : definitions(owner.assembly().profile(), owner.assembly().mirrored());
  }

  public static Port at(
      factorycraft.blockentity.FactoryBlockEntity be, BlockPos physical, Direction side) {
    var profile = MachineProfile.of(be.getBlockState());
    if (profile == null || side == null) return null;
    var owner = AssemblyAccess.owner(be);
    if (owner == null) return null;
    for (var site : owner.assembly().ports())
      if (site.side() == side && site.position().equals(physical)) return site.port();
    return null;
  }

  public static Port at(
      MachineProfile profile,
      BlockPos controller,
      Direction facing,
      BlockPos physical,
      Direction side) {
    if (side == null) return null;
    for (var p : definitions(profile))
      if (p.side(facing) == side && p.position(profile, controller, facing).equals(physical))
        return p;
    return null;
  }

  private MachinePorts() {}
}
