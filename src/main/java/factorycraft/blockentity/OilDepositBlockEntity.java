package factorycraft.blockentity;

import factorycraft.config.ResourceWorldgenConfig;
import factorycraft.fluids.FluidConnections;
import factorycraft.registry.Content;
import factorycraft.simulation.petroleum.OilYield;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.*;

/** A geological yield point, never a transferable fluid inventory. */
public final class OilDepositBlockEntity extends FactoryBlockEntity {
  private static final IItemHandler EMPTY = new ItemStackHandler(0);
  private boolean initialized, loaded;
  private net.minecraft.world.level.chunk.LevelChunk ownedChunk;
  private OilYield yield;
  private java.util.UUID identity = java.util.UUID.randomUUID();

  public java.util.UUID identity() {
    return identity;
  }

  public OilDepositBlockEntity(BlockPos pos, BlockState state) {
    super(Content.OIL_DEPOSIT_ENTITY.get(), pos, state);
  }

  public OilYield yield() {
    if (yield == null) throw new IllegalStateException("Uninitialized oil point");
    return yield;
  }

  public boolean available() {
    return loaded && initialized && FluidConnections.canonical(this);
  }

  public void initializeYield(int hundredths) {
    if (initialized) throw new IllegalStateException("Oil point already initialized");
    yield = new OilYield(hundredths);
    initialized = true;
    changed();
  }

  /** Worldgen owns the new BE; no server or neighbor callbacks on the generation worker. */
  public void initializeGeneratedYield(int hundredths) {
    if (initialized) throw new IllegalStateException("Oil point already initialized");
    yield = new OilYield(hundredths);
    initialized = true;
  }

  public void changed() {
    if (level != null && !level.isClientSide && loaded) {
      if (ownedChunk != null) ownedChunk.setUnsaved(true);
      markPersistentState();
    }
  }

  @Override
  public IItemHandler inventory() {
    return EMPTY;
  }

  @Override
  public void onLoad() {
    super.onLoad();
    if (level.isClientSide) return;
    loaded = true;
    ownedChunk =
        ((net.minecraft.server.level.ServerLevel) level)
            .getChunkSource()
            .getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
    if (!initialized) initializeYield(ResourceWorldgenConfig.OIL_MIN_YIELD.get());
  }

  @Override
  public void onChunkUnloaded() {
    loaded = false;
    ownedChunk = null;
    super.onChunkUnloaded();
  }

  @Override
  public void setRemoved() {
    loaded = false;
    ownedChunk = null;
    super.setRemoved();
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
    super.saveAdditional(tag, r);
    tag.putInt("Schema", 2);
    tag.putUUID("OilIdentity", identity);
    tag.putBoolean("Initialized", initialized);
    if (initialized) {
      tag.putLong("InitialYield", yield.initial());
      tag.putLong("CurrentYield", yield.current());
      tag.putLong("ExtractionCycles", yield.cycles());
      tag.putInt("OilResidue", yield.residue());
      tag.putLong("GeneratedOil", yield.generated());
    }
  }

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
    super.loadAdditional(tag, r);
    initialized = tag.getBoolean("Initialized");
    if (tag.hasUUID("OilIdentity")) identity = tag.getUUID("OilIdentity");
    if (initialized) {
      if (tag.getInt("Schema") != 2)
        throw new IllegalArgumentException("Pre-0.25 oil schema is unsupported");
      yield = new OilYield(2000);
      yield.restore(
          tag.getLong("InitialYield"),
          tag.getLong("CurrentYield"),
          tag.getLong("ExtractionCycles"),
          tag.getInt("OilResidue"),
          tag.getLong("GeneratedOil"));
    }
  }
}
