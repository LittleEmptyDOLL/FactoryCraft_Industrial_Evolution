package factorycraft.rail;

import factorycraft.simulation.rail.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Identity/mesh only. Paid materials exist exclusively in RailwayData, not in this projection. */
public final class RailCellBlockEntity extends BlockEntity {
  public static final int PROJECTION_VERSION = 2;
  public int projectionVersion = PROJECTION_VERSION;
  private Set<UUID> segments = Set.of();
  public List<RailDescriptor> descriptors = List.of();
  private RailProjection projection = new RailProjection(List.of());
  private VoxelShape shape = Shapes.empty();

  public RailCellBlockEntity(BlockPos p, BlockState s) {
    super(RailContent.CELL.get(), p, s);
  }

  public Set<UUID> segments() {
    return segments;
  }

  public VoxelShape shape() {
    return shape;
  }

  public void bind(
      Set<UUID> ids,
      List<RailMesh.Face> faces,
      List<RailFootprint.Box> boxes,
      List<RailDescriptor> descriptors) {
    projectionVersion = PROJECTION_VERSION;
    this.descriptors = List.copyOf(descriptors);
    segments = Set.copyOf(ids);
    projection = new RailProjection(faces);
    var parts = new ArrayList<VoxelShape>();
    for (var b : boxes)
      if (b.x1() > b.x0() && b.y1() > b.y0() && b.z1() > b.z0())
        parts.add(Shapes.box(b.x0(), b.y0(), b.z0(), b.x1(), b.y1(), b.z1()));
    shape =
        parts.isEmpty()
            ? Shapes.empty()
            : Shapes.or(Shapes.empty(), parts.toArray(VoxelShape[]::new)).optimize();
    setChanged();
    if (level != null) {
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
      if (level.isClientSide) {
        requestModelDataUpdate();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);
      }
    }
  }

  public ModelData getModelData() {
    return ModelData.builder().with(RailProjection.MODEL, projection).build();
  }

  public void onLoad() {
    super.onLoad();
    if (level instanceof net.minecraft.server.level.ServerLevel server)
      RailwayData.get(server).loaded(this);
    else {
      requestModelDataUpdate();
    }
  }

  @Override
  public void setRemoved() {
    if (level instanceof net.minecraft.server.level.ServerLevel server)
      RailwayData.get(server).unloaded(this);
    super.setRemoved();
  }

  public void onChunkUnloaded() {
    if (level instanceof net.minecraft.server.level.ServerLevel server)
      RailwayData.get(server).unloaded(this);
    super.onChunkUnloaded();
  }

  protected void saveAdditional(CompoundTag n, HolderLookup.Provider r) {
    super.saveAdditional(n, r);
    var ids = new ListTag();
    for (var id : segments) {
      var t = new CompoundTag();
      t.putUUID("Id", id);
      ids.add(t);
    }
    n.putInt("Projection", projectionVersion);
    n.put("Segments", ids);
    var descriptions = new ListTag();
    for (var d : descriptors) {
      var t = new CompoundTag();
      t.put("A", RailCodec.end(d.a()));
      t.put("B", RailCodec.end(d.b()));
      t.putDouble("H", d.handle());
      t.putUUID("Id", d.id());
      descriptions.add(t);
    }
    n.put("Edges", descriptions);
    n.put("Faces", RailCodec.faces(projection.faces()));
    var boxes = new ListTag();
    for (var b : shape.toAabbs()) {
      var t = new ListTag();
      for (double d : new double[] {b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ})
        t.add(DoubleTag.valueOf(d));
      boxes.add(t);
    }
    n.put("Boxes", boxes);
  }

  protected void loadAdditional(CompoundTag n, HolderLookup.Provider r) {
    super.loadAdditional(n, r);
    projectionVersion = n.getInt("Projection");
    var ids = new HashSet<UUID>();
    for (var raw : n.getList("Segments", Tag.TAG_COMPOUND)) {
      var t = (CompoundTag) raw;
      if (t.hasUUID("Id")) ids.add(t.getUUID("Id"));
    }
    if (ids.size() > 8) throw new IllegalArgumentException("Rail junction capacity");
    segments = Set.copyOf(ids);
    var ds = new ArrayList<RailDescriptor>();
    for (var raw : n.getList("Edges", Tag.TAG_COMPOUND)) {
      var t = (CompoundTag) raw;
      ds.add(
          new RailDescriptor(
              t.getUUID("Id"),
              RailCodec.end(t.getCompound("A")),
              RailCodec.end(t.getCompound("B")),
              t.getDouble("H")));
    }
    descriptors = List.copyOf(ds);
    projection = new RailProjection(RailCodec.faces(n.getList("Faces", Tag.TAG_COMPOUND)));
    var parts = new ArrayList<VoxelShape>();
    for (var raw : n.getList("Boxes", Tag.TAG_LIST)) {
      var t = (ListTag) raw;
      if (t.size() != 6) throw new IllegalArgumentException("Rail box");
      double[] v = new double[6];
      for (int i = 0; i < 6; i++) {
        v[i] = t.getDouble(i);
        if (!Double.isFinite(v[i]) || v[i] < 0 || v[i] > 1)
          throw new IllegalArgumentException("Rail shape bounds");
      }
      parts.add(Shapes.box(v[0], v[1], v[2], v[3], v[4], v[5]));
    }
    shape =
        parts.isEmpty()
            ? Shapes.empty()
            : Shapes.or(Shapes.empty(), parts.toArray(VoxelShape[]::new)).optimize();
    if (level != null && level.isClientSide) {
      requestModelDataUpdate();
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);
    }
  }

  public CompoundTag getUpdateTag(HolderLookup.Provider r) {
    return saveWithoutMetadata(r);
  }

  public ClientboundBlockEntityDataPacket getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }
}
