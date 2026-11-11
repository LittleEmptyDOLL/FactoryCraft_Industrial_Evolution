package factorycraft.circuits;

import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.fluids.FluidConnections;
import factorycraft.multiblock.*;
import factorycraft.registry.Content;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/** A single program/output owner; casing cells contain only the ordinary assembly identity. */
public final class CircuitNodeBlockEntity extends FactoryBlockEntity
    implements AssemblyOwner, MenuProvider {
  private final IndustrialAssembly assembly = new IndustrialAssembly(this);
  private boolean loaded;
  public boolean active, gate, readerReady;
  public int inputValue, lastOutput;
  private CircuitProgram program = CircuitProgram.DEFAULT;
  private ReaderSettings readerSettings = ReaderSettings.DEFAULT;
  public final List<CircuitLink> links = new ArrayList<>();
  private static final IItemHandler EMPTY = new net.neoforged.neoforge.items.ItemStackHandler(0);

  public CircuitNodeBlockEntity(BlockPos p, BlockState s) {
    super(Content.CIRCUIT.get(), p, s);
    if (kind() != FactoryKind.CONSTANT)
      program = new CircuitProgram(CircuitProgram.A, CircuitProgram.A, 0, 0, false);
  }

  public FactoryKind kind() {
    return ((FactoryBlock) getBlockState().getBlock()).kind;
  }

  public CircuitProgram program() {
    return program;
  }

  public ReaderSettings readerSettings() {
    return readerSettings;
  }

  public boolean configureReader(ReaderSettings settings) {
    if (kind() != FactoryKind.CIRCUIT_READER || !available()) return false;
    readerSettings = settings;
    readerReady = false;
    sync();
    circuit(true).useLegacy();
    return true;
  }

  @Override
  public IndustrialAssembly assembly() {
    return assembly;
  }

  @Override
  public boolean loaded() {
    return loaded && !isRemoved();
  }

  public boolean available() {
    if (!loaded() || !FluidConnections.canonical(this) || !assembly.ready()) return false;
    var server = (net.minecraft.server.level.ServerLevel) level;
    for (var p :
        assembly.profile().cells(worldPosition, getBlockState().getValue(FactoryBlock.FACING)))
      if (!server.shouldTickBlocksAt(
          net.minecraft.world.level.ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4))) return false;
    return true;
  }

  public int ports() {
    return kind().dualCircuit() ? 2 : 1;
  }

  public int outputPort() {
    return kind().dualCircuit() ? 1 : 0;
  }

  public int portAt(BlockPos p) {
    if (!kind().dualCircuit()) return p.equals(worldPosition) ? 0 : -1;
    return p.equals(worldPosition) ? 1 : assembly.positions().contains(p) ? 0 : -1;
  }

  public BlockPos portPosition(int port) {
    return kind().dualCircuit() && port == 0 ? assembly.positions().getFirst() : worldPosition;
  }

  public BlockPos targetPosition() {
    return worldPosition.relative(getBlockState().getValue(FactoryBlock.FACING));
  }

  public boolean configure(CircuitProgram next) {
    if (!available() || next.operation() >= (kind() == FactoryKind.ARITHMETIC ? 10 : 6))
      return false;
    program = next;
    gate = false;
    sync();
    circuit(true).useLegacy();
    return true;
  }

  public void persistSignal() {
    markPersistentState();
  }

  @Override
  public IItemHandler inventory() {
    return EMPTY;
  }

  @Override
  public void dropContents() {
    CircuitSimulation.destroyed(this);
    assembly.removeWhole(true);
  }

  @Override
  public void onLoad() {
    super.onLoad();
    loaded = true;
    assembly.invalidate();
    if (!level.isClientSide) CircuitSimulation.add(this);
  }

  @Override
  public void onChunkUnloaded() {
    loaded = false;
    if (level != null && !level.isClientSide) CircuitSimulation.remove(this);
    super.onChunkUnloaded();
  }

  @Override
  public void setRemoved() {
    loaded = false;
    if (level != null && !level.isClientSide) CircuitSimulation.remove(this);
    super.setRemoved();
  }

  @Override
  protected void saveAdditional(CompoundTag t, HolderLookup.Provider r) {
    super.saveAdditional(t, r);
    assembly.save(t);
    var data = new CompoundTag();
    data.putInt("Schema", 1);
    data.put("Program", program.save());
    data.putInt("Output", lastOutput);
    data.putInt("InputValue", inputValue);
    if (!readerSettings.isDefault()) data.put("Reader", readerSettings.save(r));
    var list = new ListTag();
    for (var link : links) list.add(link.save());
    data.put("Links", list);
    t.put("Circuit", data);
  }

  @Override
  protected void loadAdditional(CompoundTag t, HolderLookup.Provider r) {
    super.loadAdditional(t, r);
    assembly.load(t);
    var d = t.getCompound("Circuit");
    program =
        d.contains("Program")
            ? CircuitProgram.load(d.getCompound("Program"))
            : CircuitProgram.DEFAULT;
    lastOutput = d.getInt("Output");
    inputValue = d.getInt("InputValue");
    readerSettings = ReaderSettings.load(d.getCompound("Reader"), r);
    readerReady = false;
    links.clear();
    if (t.contains("Assembly"))
      for (var item : d.getList("Links", 10)) {
        var link = CircuitLink.load((CompoundTag) item);
        if (link != null && !links.contains(link) && links.size() < WireColors.MAX_LINKS)
          links.add(link);
      }
    gate = false;
    if (!t.contains("DirectCircuit")) circuit(true).useLegacy();
  }

  @Override
  public Component getDisplayName() {
    return Component.translatable("block.factorycraft." + kind().id);
  }

  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
    return new CircuitMenu(id, inv, this);
  }
}
