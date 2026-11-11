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
          new TextureAtlasSprite[] {
            sprites.apply(context.getMaterial("frame")),
            sprites.apply(context.getMaterial("rail")),
            sprites.apply(context.getMaterial("accent")),
            sprites.apply(context.getMaterial("roller")),
            sprites.apply(context.getMaterial("accent_fast")),
            sprites.apply(context.getMaterial("accent_express"))
          },
          context.getTransforms());
    }
  }

  private static final class Model implements IDynamicBakedModel {
    private final TextureAtlasSprite[] sprites;
    private final ItemTransforms transforms;
    private final TextureAtlasSprite[] details;
    private final Map<BeltModelData.Cell, List<BakedQuad>> cache =
        new LinkedHashMap<>(64, .75f, true) {
          protected boolean removeEldestEntry(
              Map.Entry<BeltModelData.Cell, List<BakedQuad>> entry) {
            return size() > 512;
          }
        };

    Model(TextureAtlasSprite[] sprites, TextureAtlasSprite[] details, ItemTransforms transforms) {
      this.sprites = sprites;
      this.details = details;
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
      var mesh = TransportMesh.build(cell.route(), cell.offsetY(), cell.output());
      for (var face : mesh.faces()) {
        var points = face.vertices();
        if (points.size() == 4)
          quad(
              result,
              cell,
              skin(face.skin(), cell.tier()),
              points.toArray(TransportMesh.Vertex[]::new),
              face.doubleSided());
        else
          for (int i = 1; i < points.size() - 1; i++)
            quad(
                result,
                cell,
                skin(face.skin(), cell.tier()),
                new TransportMesh.Vertex[] {
                  points.getFirst(), points.get(i), points.get(i + 1), points.get(i + 1)
                },
                face.doubleSided());
      }
      return List.copyOf(result);
    }

    private TextureAtlasSprite skin(TransportMesh.Skin skin, int tier) {
      return skin == TransportMesh.Skin.SURFACE
          ? sprites[tier - 1]
          : details[skin == TransportMesh.Skin.ACCENT && tier > 1 ? tier + 2 : skin.ordinal() - 1];
    }

    private void quad(
        List<BakedQuad> out,
        BeltModelData.Cell cell,
        TextureAtlasSprite sprite,
        TransportMesh.Vertex[] points,
        boolean doubleSided) {
      var p0 = points[0];
      var p1 = points[1];
      var p2 = points[2];
      var normal =
          new Vector3f(
                  (float) (p1.x() - p0.x()), (float) (p1.y() - p0.y()), (float) (p1.z() - p0.z()))
              .cross(
                  new Vector3f(
                      (float) (p2.x() - p0.x()),
                      (float) (p2.y() - p0.y()),
                      (float) (p2.z() - p0.z())));
      if (normal.lengthSquared() < 1e-12) return;
      normal.normalize();
      int[] vertices = new int[32];
      for (int i = 0; i < 4; i++) {
        var p = points[i];
        var rotated = TransportShapes.rotate(p.x(), p.z(), cell.facing());
        int n = i * 8;
        vertices[n] = Float.floatToRawIntBits((float) rotated[0]);
        vertices[n + 1] = Float.floatToRawIntBits((float) p.y());
        vertices[n + 2] = Float.floatToRawIntBits((float) rotated[1]);
        vertices[n + 3] = -1;
        vertices[n + 4] = Float.floatToRawIntBits(sprite.getU((float) Math.clamp(p.u(), 0, 1)));
        vertices[n + 5] = Float.floatToRawIntBits(sprite.getV((float) Math.clamp(p.v(), 0, 1)));
      }
      var rotatedNormal = TransportShapes.rotate(normal.x + .5, normal.z + .5, cell.facing());
      Direction direction =
          Direction.getNearest(
              (float) (rotatedNormal[0] - .5), normal.y, (float) (rotatedNormal[1] - .5));
      out.add(new BakedQuad(vertices, -1, direction, sprite, true, false));
      // Ribbons are visible from either side, including downhill undersides.
      if (!doubleSided) return;
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
