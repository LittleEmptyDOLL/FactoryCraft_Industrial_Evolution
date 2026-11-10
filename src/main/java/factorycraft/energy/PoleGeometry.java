package factorycraft.energy;

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
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Same original box/attachment resource feeds physical shapes, block meshes and wire endpoints. */
public final class PoleGeometry {
  private static final JsonObject DATA;

  static {
    try (var in =
        Objects.requireNonNull(
            PoleGeometry.class.getResourceAsStream("/factorycraft/pole_geometry.json"))) {
      DATA =
          JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
              .getAsJsonObject();
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private record Key(MachineProfile p, Direction f, int x, int y, int z) {}

  private static final Map<Key, VoxelShape> CACHE = new HashMap<>();

  public static Vec3 attachment(factorycraft.blockentity.PoleBlockEntity p) {
    var profile = p.assembly().profile();
    var a = DATA.getAsJsonObject(profile.id).getAsJsonArray("attachment");
    var c = profile.controllerLocal();
    var origin = Vec3.atLowerCornerOf(p.getBlockPos());
    double x = a.get(0).getAsDouble() - c.getX(),
        y = a.get(1).getAsDouble() - c.getY(),
        z = a.get(2).getAsDouble() - c.getZ();
    var f = profile.facing(p.getBlockState());
    var right = f.getClockWise();
    return origin.add(
        right.getStepX() * (x - .5) - f.getStepX() * (z - .5) + .5,
        y,
        right.getStepZ() * (x - .5) - f.getStepZ() * (z - .5) + .5);
  }

  public static VoxelShape shape(BlockState state, BlockGetter level, BlockPos pos) {
    var profile = MachineProfile.of(state);
    var be = level.getBlockEntity(pos);
    BlockPos controller = pos;
    if (be instanceof CasingBlockEntity part) {
      profile = state.getValue(FactoryBlock.PROFILE);
      controller = part.controllerPosition();
    }
    if (profile == null || controller == null) return Shapes.block();
    var f = profile.facing(state);
    var at = profile.local(controller, f, pos);
    var key = new Key(profile, f, at.getX(), at.getY(), at.getZ());
    return CACHE.computeIfAbsent(key, PoleGeometry::bake);
  }

  private static VoxelShape bake(Key k) {
    var result = Shapes.empty();
    for (var raw : DATA.getAsJsonObject(k.p.id).getAsJsonArray("boxes")) {
      var box = raw.getAsJsonObject();
      var a = box.getAsJsonArray("from");
      var b = box.getAsJsonArray("to");
      double x0 = Math.max(0, a.get(0).getAsDouble() - k.x),
          x1 = Math.min(1, b.get(0).getAsDouble() - k.x),
          y0 = Math.max(0, a.get(1).getAsDouble() - k.y),
          y1 = Math.min(1, b.get(1).getAsDouble() - k.y),
          z0 = Math.max(0, a.get(2).getAsDouble() - k.z),
          z1 = Math.min(1, b.get(2).getAsDouble() - k.z);
      if (x0 >= x1 || y0 >= y1 || z0 >= z1) continue;
      var right = k.f.getClockWise();
      double ax = (x0 - .5) * right.getStepX() - (z0 - .5) * k.f.getStepX() + .5,
          az = (x0 - .5) * right.getStepZ() - (z0 - .5) * k.f.getStepZ() + .5,
          bx = (x1 - .5) * right.getStepX() - (z1 - .5) * k.f.getStepX() + .5,
          bz = (x1 - .5) * right.getStepZ() - (z1 - .5) * k.f.getStepZ() + .5;
      result =
          Shapes.or(
              result,
              Shapes.box(
                  Math.min(ax, bx), y0, Math.min(az, bz), Math.max(ax, bx), y1, Math.max(az, bz)));
    }
    return result;
  }

  private PoleGeometry() {}
}
