package factorycraft.nuclear;

import com.google.gson.*;
import factorycraft.blockentity.CasingBlockEntity;
import factorycraft.blocks.FactoryBlock;
import factorycraft.multiblock.MachineProfile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

/** Chunk-local, cached base collision uses the same original boxes as the baked shell. */
public final class NuclearGeometry {
  private record Key(MachineProfile profile, Direction facing, int x, int y, int z) {}

  private static final Map<Key, VoxelShape> CACHE = new HashMap<>();
  private static final Map<String, JsonArray> BOXES = new HashMap<>();

  static {
    try (var in =
            Objects.requireNonNull(
                NuclearGeometry.class.getResourceAsStream("/factorycraft/nuclear_bases.json"));
        var r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
      for (var e : JsonParser.parseReader(r).getAsJsonObject().entrySet())
        BOXES.put(e.getKey(), e.getValue().getAsJsonArray());
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  public static boolean has(MachineProfile p) {
    return BOXES.containsKey(p.id);
  }

  public static VoxelShape shape(BlockState s, BlockGetter l, BlockPos pos) {
    var profile = MachineProfile.of(s);
    BlockPos controller = pos;
    var be =
        l instanceof net.minecraft.server.level.ServerLevel server
            ? factorycraft.fluids.FluidConnections.at(server, pos)
            : l.getBlockEntity(pos);
    if (be instanceof CasingBlockEntity part) {
      profile = s.getValue(FactoryBlock.PROFILE);
      controller = part.controllerPosition();
    }
    if (profile == null || controller == null) return Shapes.block();
    var f = profile.facing(s);
    var p = profile.local(controller, f, pos);
    return CACHE.computeIfAbsent(
        new Key(profile, f, p.getX(), p.getY(), p.getZ()), NuclearGeometry::bake);
  }

  private static VoxelShape bake(Key k) {
    var out = Shapes.empty();
    var right = k.facing.getClockWise();
    for (var e : BOXES.get(k.profile.id)) {
      var b = e.getAsJsonObject();
      var a = b.getAsJsonArray("from");
      var z = b.getAsJsonArray("to");
      double x0 = Math.max(0, a.get(0).getAsDouble() - k.x),
          x1 = Math.min(1, z.get(0).getAsDouble() - k.x),
          y0 = Math.max(0, a.get(1).getAsDouble() - k.y),
          y1 = Math.min(1, z.get(1).getAsDouble() - k.y),
          z0 = Math.max(0, a.get(2).getAsDouble() - k.z),
          z1 = Math.min(1, z.get(2).getAsDouble() - k.z);
      if (x0 >= x1 || y0 >= y1 || z0 >= z1) continue;
      double ax = (x0 - .5) * right.getStepX() - (z0 - .5) * k.facing.getStepX() + .5,
          az = (x0 - .5) * right.getStepZ() - (z0 - .5) * k.facing.getStepZ() + .5,
          bx = (x1 - .5) * right.getStepX() - (z1 - .5) * k.facing.getStepX() + .5,
          bz = (x1 - .5) * right.getStepZ() - (z1 - .5) * k.facing.getStepZ() + .5;
      out =
          Shapes.or(
              out,
              Shapes.box(
                  Math.min(ax, bx), y0, Math.min(az, bz), Math.max(ax, bx), y1, Math.max(az, bz)));
    }
    return out.optimize();
  }

  private NuclearGeometry() {}
}
