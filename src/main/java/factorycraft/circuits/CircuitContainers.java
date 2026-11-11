package factorycraft.circuits;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;

/** Circuit-only data for explicitly opted-in external owners; no writes to their BE NBT. */
public final class CircuitContainers extends SavedData {
  private static final class Record {
    final BlockPos pos;
    final String type, block;
    CompoundTag saved;
    CircuitAttachment live;

    Record(BlockPos pos, String type, String block, CompoundTag saved) {
      this.pos = pos;
      this.type = type;
      this.block = block;
      this.saved = saved;
    }
  }

  private final Map<BlockPos, Record> records = new HashMap<>();
  private final Map<Long, Set<BlockPos>> chunks = new HashMap<>();

  public static CircuitContainers get(ServerLevel level) {
    return level
        .getDataStorage()
        .computeIfAbsent(
            new SavedData.Factory<>(CircuitContainers::new, CircuitContainers::load),
            "factorycraft_container_circuits");
  }

  public static void dirty(ServerLevel level) {
    get(level).setDirty();
  }

  private static long chunk(BlockPos p) {
    return net.minecraft.world.level.ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4);
  }

  private void put(Record r) {
    records.put(r.pos, r);
    chunks.computeIfAbsent(chunk(r.pos), k -> new HashSet<>()).add(r.pos);
  }

  public static CircuitAttachment attach(BlockEntity owner) {
    if (!(owner.getLevel() instanceof ServerLevel level) || owner.isRemoved()) return null;
    var cell =
        level
            .getChunkSource()
            .getChunkNow(owner.getBlockPos().getX() >> 4, owner.getBlockPos().getZ() >> 4);
    if (cell == null || cell.getBlockEntity(owner.getBlockPos()) != owner) return null;
    var data = get(level);
    var p = owner.getBlockPos();
    String type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(owner.getType()).toString(),
        block = BuiltInRegistries.BLOCK.getKey(owner.getBlockState().getBlock()).toString();
    var r = data.records.get(p);
    if (r != null && r.live != null && r.live.owner() == owner) return r.live;
    if (r != null
        && (!r.type.equals(type)
            || !r.block.equals(block)
            || r.live != null && r.live.owner() != owner)) {
      if (r.live != null) {
        CircuitSimulation.destroyed(r.live);
        CircuitSimulation.remove(r.live);
      }
      data.records.remove(p);
      r = null;
      data.setDirty();
    }
    var a = new CircuitAttachment(owner);
    a.configured = true;
    if (r == null) {
      r = new Record(p.immutable(), type, block, new CompoundTag());
      data.put(r);
      data.setDirty();
    } else if (!r.saved.isEmpty()) a.load(r.saved);
    r.live = a;
    CircuitSimulation.add(a);
    return a;
  }

  public static void loaded(LevelChunk chunk) {
    if (!(chunk.getLevel() instanceof ServerLevel level)) return;
    var data = get(level);
    var cells = data.chunks.get(chunk.getPos().toLong());
    if (cells == null) return;
    for (var p : List.copyOf(cells)) {
      var r = data.records.get(p);
      var owner = chunk.getBlockEntity(p);
      if (owner != null
          && BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(owner.getType()).toString().equals(r.type)
          && BuiltInRegistries.BLOCK
              .getKey(owner.getBlockState().getBlock())
              .toString()
              .equals(r.block)) attach(owner);
      else {
        if (r.live != null) {
          CircuitSimulation.destroyed(r.live);
          CircuitSimulation.remove(r.live);
        }
        data.records.remove(p);
        cells.remove(p);
        data.setDirty();
      }
    }
  }

  public static void unloaded(LevelChunk chunk) {
    if (!(chunk.getLevel() instanceof ServerLevel level)) return;
    var data = get(level);
    var cells = data.chunks.get(chunk.getPos().toLong());
    if (cells == null) return;
    for (var p : List.copyOf(cells)) {
      var r = data.records.get(p);
      if (r != null && r.live != null) {
        r.saved = r.live.save();
        r.live.suspended = true;
        CircuitSimulation.remove(r.live);
        r.live = null;
        data.setDirty();
      }
    }
  }

  public static void changed(ServerLevel level, BlockPos pos) {
    var data = get(level);
    var r = data.records.get(pos);
    if (r == null) return;
    var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
    if (chunk == null) return;
    var be = chunk.getBlockEntity(pos);
    if (r.live != null && (be != r.live.owner() || be.isRemoved())) {
      CircuitSimulation.destroyed(r.live);
      CircuitSimulation.remove(r.live);
      data.records.remove(pos);
      var positions = data.chunks.get(chunk(pos));
      if (positions != null) positions.remove(pos);
      data.setDirty();
    }
  }

  private static CircuitContainers load(CompoundTag tag, HolderLookup.Provider registry) {
    var data = new CircuitContainers();
    for (var item : tag.getList("Owners", 10)) {
      var r = (CompoundTag) item;
      data.put(
          new Record(
              BlockPos.of(r.getLong("Pos")),
              r.getString("Type"),
              r.getString("Block"),
              r.getCompound("Circuit").copy()));
    }
    return data;
  }

  public CompoundTag save(CompoundTag tag, HolderLookup.Provider registry) {
    tag.putInt("Schema", 2);
    var rows = new ListTag();
    for (var r : records.values()) {
      var row = new CompoundTag();
      row.putLong("Pos", r.pos.asLong());
      row.putString("Type", r.type);
      row.putString("Block", r.block);
      row.put("Circuit", r.live == null ? r.saved : r.live.save());
      rows.add(row);
    }
    tag.put("Owners", rows);
    return tag;
  }
}
