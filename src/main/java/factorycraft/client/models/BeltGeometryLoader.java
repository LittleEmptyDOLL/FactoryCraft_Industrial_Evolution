package factorycraft.client.models;

import com.google.gson.*;
import factorycraft.belts.*;
import factorycraft.blocks.*;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.*;
import org.joml.Vector3f;

/** Chunk-baked curved ribbons. Cache keys contain geometry only, never world state. */
public final class BeltGeometryLoader implements IGeometryLoader<BeltGeometryLoader.Geometry> {
  public Geometry read(JsonObject json, JsonDeserializationContext context) {
    return new Geometry();
  }

  public record Geometry() implements IUnbakedGeometry<Geometry> {
    public BakedModel bake(
        IGeometryBakingContext context,
        ModelBaker baker,
        Function<Material, TextureAtlasSprite> sprites,
        ModelState state,
        ItemOverrides overrides) {
      return new Model(
          new TextureAtlasSprite[] {
            sprites.apply(context.getMaterial("normal")),
            sprites.apply(context.getMaterial("fast")),
            sprites.apply(context.getMaterial("express"))
          },
          context.getTransforms());
    }
  }

  private static final class Model implements IDynamicBakedModel {
    private final TextureAtlasSprite[] sprites;
    private final ItemTransforms transforms;
    private final Map<BeltModelData.Cell, List<BakedQuad>> cache = new HashMap<>();

    Model(TextureAtlasSprite[] sprites, ItemTransforms transforms) {
      this.sprites = sprites;
      this.transforms = transforms;
    }

    public List<BakedQuad> getQuads(
        BlockState state,
        Direction side,
        RandomSource random,
        ModelData data,
        RenderType renderType) {
      if (side != null) return List.of();
      var cell = data.get(BeltModelData.CELL);
      if (cell == null) {
        int tier =
            state != null && state.getBlock() instanceof FactoryBlock f
                ? Math.max(1, f.kind.beltTier)
                : 1;
        cell =
            new BeltModelData.Cell(
                BeltRoute.FLAT,
                state == null ? Direction.NORTH : state.getValue(FactoryBlock.FACING),
                tier,
                0,
                false);
      }
      synchronized (cache) {
        return cache.computeIfAbsent(cell, this::build);
      }
    }

    private List<BakedQuad> build(BeltModelData.Cell cell) {
      var result = new ArrayList<BakedQuad>();
      var r = cell.route();
      var sprite = sprites[cell.tier() - 1];
      if (r.shape == BeltRoute.Shape.LIFT) {
        box(result, cell, sprite, .04, 0, .04, .14, 1, .14);
        box(result, cell, sprite, .86, 0, .04, .96, 1, .14);
        box(result, cell, sprite, .04, 0, .86, .14, 1, .96);
        box(result, cell, sprite, .86, 0, .86, .96, 1, .96);
        if (cell.offsetY() != 0 && cell.offsetY() != r.span) {
          box(result, cell, sprite, .1, .43, .1, .9, .50, .9);
        }
      }
      if (r.shape == BeltRoute.Shape.UNDERGROUND || cell.output()) {
        // Only the two terminal surfaces exist physically; the intervening route is virtual.
        box(result, cell, sprite, 0, 0, 0, 1, .1875, 1);
        box(
            result,
            cell,
            sprite,
            .05,
            .1875,
            cell.output() ? .55 : .05,
            .95,
            .6,
            cell.output() ? .95 : .45);
        return List.copyOf(result);
      }
      int steps =
          r.shape == BeltRoute.Shape.FLAT
              ? 1
              : r.shape == BeltRoute.Shape.LIFT ? 32 + Math.abs(r.span) * 16 : 32;
      for (int i = 0; i < steps; i++) {
        var a = edge(r, 0, (double) i / steps, cell.offsetY());
        var b = edge(r, 1, (double) i / steps, cell.offsetY());
        var c = edge(r, 1, (double) (i + 1) / steps, cell.offsetY());
        var d = edge(r, 0, (double) (i + 1) / steps, cell.offsetY());
        polygon(result, cell, sprite, List.of(a, b, c, d), 0, 1);
        polygon(result, cell, sprite, List.of(lower(d), lower(c), lower(b), lower(a)), 0, 1);
        polygon(result, cell, sprite, List.of(a, d, lower(d), lower(a)), 0, 1);
        polygon(result, cell, sprite, List.of(c, b, lower(b), lower(c)), 0, 1);
        if (i == 0) polygon(result, cell, sprite, List.of(b, a, lower(a), lower(b)), 0, 1);
        if (i == steps - 1) polygon(result, cell, sprite, List.of(d, c, lower(c), lower(d)), 0, 1);
      }
      return List.copyOf(result);
    }

    private Vector3f edge(BeltRoute r, int side, double t, int offset) {
      var a = r.point(0, r.length(0) * t);
      var b = r.point(1, r.length(1) * t);
      double k = side == 0 ? -.28 / .44 : 1 + .28 / .44;
      return new Vector3f(
          (float) (a.x() + (b.x() - a.x()) * k),
          (float) (a.y() + (b.y() - a.y()) * k - offset),
          (float) (a.z() + (b.z() - a.z()) * k));
    }

    private Vector3f lower(Vector3f p) {
      return new Vector3f(p.x, p.y - .1875f, p.z);
    }

    private void box(
        List<BakedQuad> out,
        BeltModelData.Cell cell,
        TextureAtlasSprite sprite,
        double x0,
        double y0,
        double z0,
        double x1,
        double y1,
        double z1) {
      var bakery = new FaceBakery();
      int rotation =
          switch (cell.facing()) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
          };
      for (var d : Direction.values())
        out.add(
            bakery.bakeQuad(
                new Vector3f((float) x0 * 16, (float) y0 * 16, (float) z0 * 16),
                new Vector3f((float) x1 * 16, (float) y1 * 16, (float) z1 * 16),
                new BlockElementFace(
                    null, -1, "#all", new BlockFaceUV(new float[] {0, 0, 16, 16}, 0)),
                sprite,
                d,
                BlockModelRotation.by(0, rotation),
                null,
                true));
    }

    private void polygon(
        List<BakedQuad> out,
        BeltModelData.Cell cell,
        TextureAtlasSprite sprite,
        List<Vector3f> input,
        float min,
        float max) {
      var points = clip(clip(input, min, true), max, false);
      if (points.size() < 3) return;
      if (points.size() == 4) {
        quad(out, cell, sprite, points.toArray(Vector3f[]::new));
        return;
      }
      for (int k = 1; k < points.size() - 1; k++)
        quad(
            out,
            cell,
            sprite,
            new Vector3f[] {points.get(0), points.get(k), points.get(k + 1), points.get(k + 1)});
    }

    private List<Vector3f> clip(List<Vector3f> input, float at, boolean above) {
      var out = new ArrayList<Vector3f>();
      if (input.isEmpty()) return out;
      var prev = input.getLast();
      boolean prevIn = above ? prev.y >= at : prev.y <= at;
      for (var next : input) {
        boolean inside = above ? next.y >= at : next.y <= at;
        if (inside != prevIn) {
          float t = (at - prev.y) / (next.y - prev.y);
          out.add(new Vector3f(prev).lerp(next, t));
        }
        if (inside) out.add(next);
        prev = next;
        prevIn = inside;
      }
      return out;
    }

    private void quad(
        List<BakedQuad> out,
        BeltModelData.Cell cell,
        TextureAtlasSprite sprite,
        Vector3f[] points) {
      var normal =
          new Vector3f(points[1]).sub(points[0]).cross(new Vector3f(points[2]).sub(points[0]));
      if (normal.lengthSquared() < 1e-12) return;
      normal.normalize();
      int[] vertices = new int[32];
      for (int i = 0; i < 4; i++) {
        var p = points[i];
        var rotated = TransportShapes.rotate(p.x, p.z, cell.facing());
        int n = i * 8;
        vertices[n] = Float.floatToRawIntBits((float) rotated[0]);
        vertices[n + 1] = Float.floatToRawIntBits(p.y);
        vertices[n + 2] = Float.floatToRawIntBits((float) rotated[1]);
        vertices[n + 3] = -1;
        double u = (i == 0 || i == 3) ? 0 : 1, v = i < 2 ? 1 : 0;
        var route = cell.route();
        if (route.shape == BeltRoute.Shape.LEFT || route.shape == BeltRoute.Shape.RIGHT) {
          double x = route.shape == BeltRoute.Shape.LEFT ? p.x : 1 - p.x;
          double radius = Math.hypot(x, p.z);
          u = route.shape == BeltRoute.Shape.LEFT ? radius : 1 - radius;
          v = Math.atan2(p.z, x) / (Math.PI / 2);
        } else if (route.shape != BeltRoute.Shape.LIFT) {
          u = p.x;
          v = p.z;
        }
        vertices[n + 4] = Float.floatToRawIntBits(sprite.getU((float) Math.clamp(u, 0, 1)));
        vertices[n + 5] = Float.floatToRawIntBits(sprite.getV((float) Math.clamp(v, 0, 1)));
      }
      Direction direction = Direction.getNearest(normal.x, normal.y, normal.z);
      out.add(new BakedQuad(vertices, -1, direction, sprite, true, false));
      // Ribbons are visible from either side, including downhill undersides.
      int[] reversed = new int[32];
      for (int i = 0; i < 4; i++) System.arraycopy(vertices, (3 - i) * 8, reversed, i * 8, 8);
      out.add(new BakedQuad(reversed, -1, direction.getOpposite(), sprite, true, false));
    }

    public boolean useAmbientOcclusion() {
      return false;
    }

    public boolean isGui3d() {
      return true;
    }

    public boolean usesBlockLight() {
      return true;
    }

    public boolean isCustomRenderer() {
      return false;
    }

    public TextureAtlasSprite getParticleIcon() {
      return sprites[0];
    }

    public ItemTransforms getTransforms() {
      return transforms;
    }

    public ItemOverrides getOverrides() {
      return ItemOverrides.EMPTY;
    }

    public ChunkRenderTypeSet getRenderTypes(BlockState s, RandomSource r, ModelData d) {
      return ChunkRenderTypeSet.of(RenderType.solid());
    }
  }
}
