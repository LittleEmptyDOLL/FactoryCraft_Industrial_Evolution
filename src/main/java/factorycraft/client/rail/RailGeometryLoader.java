package factorycraft.client.rail;

import com.google.gson.*;
import factorycraft.rail.*;
import factorycraft.simulation.rail.*;
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

/** Ordinary native chunk mesh. No rail renderer/entity/ticker and no frame-time world lookup. */
public final class RailGeometryLoader implements IGeometryLoader<RailGeometryLoader.Geometry> {
  public Geometry read(JsonObject json, JsonDeserializationContext context) {
    return new Geometry();
  }

  public record Geometry() implements IUnbakedGeometry<Geometry> {
    public BakedModel bake(
        IGeometryBakingContext c,
        ModelBaker b,
        Function<Material, TextureAtlasSprite> textures,
        ModelState s,
        ItemOverrides o) {
      return new Model(
          new TextureAtlasSprite[] {
            textures.apply(c.getMaterial("bed")),
            textures.apply(c.getMaterial("metal")),
            textures.apply(c.getMaterial("sleepers"))
          },
          c.getTransforms());
    }
  }

  private static final class Model implements IDynamicBakedModel {
    private final TextureAtlasSprite[] textures;
    private final ItemTransforms transforms;
    private final Map<RailProjection, List<BakedQuad>> cache = new WeakHashMap<>();

    Model(TextureAtlasSprite[] t, ItemTransforms transforms) {
      this.textures = t;
      this.transforms = transforms;
    }

    public List<BakedQuad> getQuads(
        BlockState state, Direction side, RandomSource rand, ModelData data, RenderType type) {
      if (side != null) return List.of();
      var mesh = data.get(RailProjection.MODEL);
      if (mesh == null) return List.of();
      synchronized (cache) {
        return cache.computeIfAbsent(mesh, this::bake);
      }
    }

    private List<BakedQuad> bake(RailProjection p) {
      var quads = new ArrayList<BakedQuad>();
      for (var face : p.faces()) {
        var v = face.vertices();
        for (int i = 1; i < v.size() - 1; i++)
          quad(
              quads,
              new RailVector[] {v.get(0), v.get(i), v.get(i + 1), v.get(i + 1)},
              textures[face.material()]);
      }
      return List.copyOf(quads);
    }

    private void quad(List<BakedQuad> out, RailVector[] p, TextureAtlasSprite sprite) {
      var n = p[1].subtract(p[0]).cross(p[2].subtract(p[0]));
      if (n.length() < 1e-9) return;
      n = n.unit();
      int[] packed = new int[32];
      for (int i = 0; i < 4; i++) {
        int offset = i * 8;
        packed[offset] = Float.floatToRawIntBits((float) p[i].x());
        packed[offset + 1] = Float.floatToRawIntBits((float) p[i].y());
        packed[offset + 2] = Float.floatToRawIntBits((float) p[i].z());
        packed[offset + 3] = -1;
        packed[offset + 4] = Float.floatToRawIntBits(sprite.getU((i == 0 || i == 3) ? 0 : 1));
        packed[offset + 5] = Float.floatToRawIntBits(sprite.getV(i < 2 ? 0 : 1));
      }
      var direction = Direction.getNearest((float) n.x(), (float) n.y(), (float) n.z());
      out.add(new BakedQuad(packed, -1, direction, sprite, true, false));
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
      return textures[0];
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
