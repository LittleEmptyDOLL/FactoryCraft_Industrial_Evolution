package factorycraft.client.models;

import com.google.gson.*;
import factorycraft.blocks.FactoryBlock;
import factorycraft.multiblock.*;
import factorycraft.simulation.visual.CellMesh;
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

/** Reusable chunk-baked mesh: model-data selects a surface fragment; no world reads in getQuads. */
public final class MachineGeometryLoader
    implements IGeometryLoader<MachineGeometryLoader.Geometry> {
  private static final ChunkRenderTypeSet SOLID = ChunkRenderTypeSet.of(RenderType.solid());

  public Geometry read(JsonObject json, JsonDeserializationContext context) {
    var specs = new EnumMap<MachineProfile, List<CellMesh.Box>>(MachineProfile.class);
    for (var entry : json.getAsJsonArray("machines")) {
      var spec = entry.getAsJsonObject();
      var profile = MachineProfile.byId(spec.get("profile").getAsString());
      if (profile == null) throw new JsonParseException("Unknown machine profile");
      var boxes = new ArrayList<CellMesh.Box>();
      for (var e : spec.getAsJsonArray("boxes")) {
        var b = e.getAsJsonObject();
        var a = b.getAsJsonArray("from");
        var z = b.getAsJsonArray("to");
        boxes.add(
            new CellMesh.Box(
                a.get(0).getAsDouble(),
                a.get(1).getAsDouble(),
                a.get(2).getAsDouble(),
                z.get(0).getAsDouble(),
                z.get(1).getAsDouble(),
                z.get(2).getAsDouble(),
                b.get("material").getAsString()));
      }
      specs.put(profile, List.copyOf(boxes));
    }
    return new Geometry(specs);
  }

  public record Geometry(Map<MachineProfile, List<CellMesh.Box>> specs)
      implements IUnbakedGeometry<Geometry> {
    public BakedModel bake(
        IGeometryBakingContext context,
        ModelBaker baker,
        Function<Material, TextureAtlasSprite> sprites,
        ModelState state,
        ItemOverrides overrides) {
      var models = new EnumMap<MachineProfile, List<BakedQuad>[][]>(MachineProfile.class);
      var bakery = new FaceBakery();
      for (var entry : specs.entrySet()) {
        var profile = entry.getKey();
        int width = profile.layout.width(),
            height = profile.layout.height(),
            depth = profile.layout.depth();
        @SuppressWarnings("unchecked")
        List<BakedQuad>[][] cells = new List[width * height * depth][16];
        for (int cell = 0; cell < cells.length; cell++)
          for (int variant = 0; variant < 16; variant++) cells[cell][variant] = new ArrayList<>();
        for (int rotation = 0; rotation < 4; rotation++)
          for (int mirror = 0; mirror < 2; mirror++) {
            var boxes = new ArrayList<>(entry.getValue());
            var facing = Direction.NORTH;
            for (int turn = 0; turn < rotation; turn++) facing = facing.getClockWise();
            for (var p : MachinePorts.definitions(profile, mirror == 1)) {
              var at = p.local(facing);
              double x = at.getX(), y = at.getY(), z = at.getZ();
              double x0 = x + .27, x1 = x + .73, z0 = z + .27, z1 = z + .73;
              switch (p.face()) {
                case NORTH -> {
                  z0 = z + .001;
                  z1 = z + .16;
                }
                case SOUTH -> {
                  z0 = z + .84;
                  z1 = z + .999;
                }
                case WEST -> {
                  x0 = x + .001;
                  x1 = x + .16;
                }
                case EAST -> {
                  x0 = x + .84;
                  x1 = x + .999;
                }
                default -> {}
              }
              boxes.add(
                  new CellMesh.Box(
                      x0,
                      y + .27,
                      z0,
                      x1,
                      y + .73,
                      z1,
                      switch (p.mode()) {
                        case INPUT -> "inlet";
                        case OUTPUT -> "outlet";
                        case BOTH -> "bidi";
                      }));
            }
            for (var box : boxes)
              for (var face : CellMesh.split(box, width, height, depth))
                for (int active = 0; active < 2; active++) {
                  String material = face.material();
                  if (material.equals("piston") && active == 0) material = "steel";
                  if (material.equals("indicator"))
                    material = active == 1 ? "indicator_on" : "indicator_off";
                  var sprite = sprites.apply(context.getMaterial(material));
                  var uv =
                      new BlockFaceUV(
                          new float[] {
                            (float) face.u0() * 16,
                            (float) face.v0() * 16,
                            (float) face.u1() * 16,
                            (float) face.v1() * 16
                          },
                          0);
                  var element = new BlockElementFace(null, -1, "#" + material, uv);
                  var quad =
                      bakery.bakeQuad(
                          new Vector3f(
                              (float) face.x0() * 16,
                              (float) face.y0() * 16,
                              (float) face.z0() * 16),
                          new Vector3f(
                              (float) face.x1() * 16,
                              (float) face.y1() * 16,
                              (float) face.z1() * 16),
                          element,
                          sprite,
                          Direction.from3DDataValue(face.side()),
                          BlockModelRotation.by(0, rotation * 90),
                          null,
                          true);
                  cells[face.cellX() + width * (face.cellZ() + depth * face.cellY())][
                      rotation * 4 + mirror * 2 + active]
                      .add(quad);
                }
          }
        for (var variants : cells)
          for (int i = 0; i < variants.length; i++) variants[i] = List.copyOf(variants[i]);
        models.put(profile, cells);
      }
      return new Model(
          models, sprites.apply(context.getMaterial("shell")), context.getTransforms());
    }
  }

  private record Model(
      Map<MachineProfile, List<BakedQuad>[][]> models,
      TextureAtlasSprite particle,
      ItemTransforms transforms)
      implements IDynamicBakedModel {
    public List<BakedQuad> getQuads(
        BlockState state,
        Direction side,
        RandomSource random,
        ModelData data,
        RenderType renderType) {
      if (state == null || side != null) return List.of();
      var cell = data.get(MachineModelData.CELL);
      var profile = cell == null ? MachineProfile.of(state) : cell.profile();
      if (profile == null) return List.of();
      int index =
          cell == null
              ? profile.controllerLocal().getX()
                  + profile.layout.width()
                      * (profile.controllerLocal().getZ()
                          + profile.layout.depth() * profile.controllerLocal().getY())
              : cell.right()
                  + profile.layout.width() * (cell.back() + profile.layout.depth() * cell.up());
      var cells = models.get(profile);
      if (cells == null || index < 0 || index >= cells.length) return List.of();
      int rotation =
          switch (state.getValue(FactoryBlock.FACING)) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
          };
      return cells[index][
          rotation * 4
              + (cell != null && cell.mirrored() ? 2 : 0)
              + (state.getValue(FactoryBlock.ACTIVE) ? 1 : 0)];
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
      return particle;
    }

    public ItemTransforms getTransforms() {
      return transforms;
    }

    public ItemOverrides getOverrides() {
      return ItemOverrides.EMPTY;
    }

    public ChunkRenderTypeSet getRenderTypes(
        BlockState state, RandomSource random, ModelData data) {
      return SOLID;
    }
  }
}
