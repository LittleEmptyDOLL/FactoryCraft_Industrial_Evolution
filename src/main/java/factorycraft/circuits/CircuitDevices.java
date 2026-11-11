package factorycraft.circuits;

import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.fluids.*;
import factorycraft.multiblock.*;
import factorycraft.research.*;
import factorycraft.simulation.circuits.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Read-only observations and guarded high-level commands; existing production owns every resource.
 */
public final class CircuitDevices {
  public record Reading(boolean ready, SignalSet signals) {}

  private record Request(CircuitAttachment owner, ResourceLocation technology) {}

  private static final Map<Level, Map<String, TreeMap<BlockPos, Request>>> RESEARCH =
      new IdentityHashMap<>();
  private static final Map<Level, Map<String, BlockPos>> RESEARCH_CONTROL = new IdentityHashMap<>();

  public static boolean needsPoll(CircuitAttachment n) {
    return n.legacyMode
            && n.legacy() != null
            && (n.legacy().kind() == FactoryKind.CIRCUIT_READER
                || n.legacy().kind() == FactoryKind.CIRCUIT_CONTROLLER)
        || !n.combinator()
            && (n.settings().contents()
                || n.settings().state()
                || n.settings().ingredients()
                || n.settings().results()
                || n.settings().researchInfo()
                || n.settings().selectResearch());
  }

  public static boolean gateable(BlockEntity be) {
    return be instanceof InserterBlockEntity
        || be instanceof BeltBlockEntity
        || be instanceof MachineBlockEntity
        || be instanceof ThermalBlockEntity
        || be instanceof LabBlockEntity
        || be instanceof FluidBlockEntity fluid && fluid.kind() == FluidKind.PUMP;
  }

  public static boolean stateful(BlockEntity be) {
    return gateable(be);
  }

  public static boolean readable(BlockEntity be) {
    return !(be instanceof PoleBlockEntity) && !(be instanceof CircuitNodeBlockEntity);
  }

  public static boolean validSettings(CircuitAttachment n, CircuitSettings s) {
    var b = n.owner();
    boolean machine =
        b instanceof MachineBlockEntity m
            && (m.kind() == FactoryKind.ASSEMBLER
                || m.kind() == FactoryKind.ADVANCED_ASSEMBLER
                || m.kind() == FactoryKind.REFINERY
                || m.kind() == FactoryKind.CHEMICAL_PLANT);
    return (!s.filters() && !s.hand() || b instanceof InserterBlockEntity)
        && (!s.recipe() || machine)
        && (!s.selectResearch() && !s.researchInfo() || b instanceof LabBlockEntity)
        && (!s.pulse() || b instanceof BeltBlockEntity)
        && (!s.ingredients() && !s.results() || b instanceof MachineBlockEntity)
        && (!s.gate() || gateable(b))
        && (!s.contents() || readable(b))
        && (!s.state() || stateful(b));
  }

  private static void item(SignalSet.Builder out, ItemStack stack) {
    if (!stack.isEmpty())
      out.add(
          SignalKey.item(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()),
          stack.getCount());
  }

  private static void fluid(SignalSet.Builder out, FluidStack stack) {
    if (!stack.isEmpty())
      out.add(
          SignalKey.fluid(BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString()),
          stack.getAmount());
  }

  public static int stackSize(SignalKey key) {
    if (key.type() != SignalKey.Type.ITEM) return 0;
    var id = ResourceLocation.tryParse(key.id());
    return id == null
        ? 0
        : BuiltInRegistries.ITEM
            .getOptional(id)
            .map(item -> new ItemStack(item).getMaxStackSize())
            .orElse(0);
  }

  public static SignalSet stateSignals(CircuitAttachment n) {
    var out = new SignalSet.Builder();
    if (!n.settings().state()) return out.build();
    var b = n.owner();
    int state = 0, energy = 0, progress = 0;
    if (b instanceof InserterBlockEntity arm) {
      state = arm.status();
      energy = arm.burner() ? (int) arm.fuelEnergy() : arm.power.getEnergyStored();
      progress = arm.phase();
    } else if (b instanceof MachineBlockEntity m) {
      state = m.getBlockState().getValue(FactoryBlock.ACTIVE) ? 1 : 0;
      energy = m.power.getEnergyStored();
      progress = m.petroleum() == null ? m.data.get(0) : m.petroleum().data.get(0);
    } else if (b instanceof LabBlockEntity l) {
      state = l.getBlockState().getValue(FactoryBlock.ACTIVE) ? 1 : 0;
      energy = l.power.getEnergyStored();
      progress = l.progress();
    } else if (b instanceof FluidBlockEntity f) {
      state =
          f.kind() == FluidKind.PUMP && f.getBlockState().getValue(FluidDeviceBlock.ACTIVE) ? 1 : 0;
      energy = f.power.getEnergyStored();
    } else if (b instanceof ThermalBlockEntity t) {
      state = t.getBlockState().getValue(ThermalBlock.ACTIVE) ? 1 : 0;
      energy = t.generatesElectricity() ? t.engine.energy() : t.boiler.fuel();
    } else if (b instanceof BeltBlockEntity belt) state = belt.circuitRunning() ? 1 : 0;
    out.add(SignalKey.STATE, state).add(SignalKey.ENERGY, energy).add(SignalKey.PROGRESS, progress);
    return out.build();
  }

  public static Reading read(CircuitAttachment n) {
    if (!n.available()) return new Reading(false, SignalSet.EMPTY);
    if (n.legacyMode && n.legacy() != null && n.legacy().kind() == FactoryKind.CIRCUIT_READER)
      return legacyRead(n);
    var out = new SignalSet.Builder().addAll(stateSignals(n));
    var be = n.owner();
    var settings = n.settings();
    if (settings.contents()) {
      if (be instanceof InserterBlockEntity arm) item(out, arm.held());
      else if (be instanceof BeltBlockEntity belt) {
        if (settings.pulse()) {
          for (var entry : n.output.values().entrySet())
            if (entry.getKey().type() == SignalKey.Type.ITEM)
              out.add(entry.getKey(), entry.getValue());
        } else
          for (int lane = 0; lane < belt.laneCount(); lane++)
            for (var carrier : belt.lane(lane).carriers()) item(out, carrier.value);
      } else if (be instanceof FluidBlockEntity pipe) {
        var sample = FluidSimulation.circuitContents(pipe);
        if (!sample.ready()) return new Reading(false, SignalSet.EMPTY);
        sample.amounts().forEach(out::add);
      } else if (be instanceof ChestBlockEntity chest) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) item(out, chest.getItem(slot));
      } else if (be instanceof FactoryBlockEntity factory) {
        var inv = factory.inventory();
        for (int slot = 0; slot < Math.min(256, inv.getSlots()); slot++)
          item(out, inv.getStackInSlot(slot));
        if (be instanceof MachineBlockEntity m && m.petroleum() != null)
          for (int i = 0; i < m.petroleum().cells.size(); i++) fluid(out, m.petroleum().stored(i));
        if (be instanceof ThermalBlockEntity t) {
          fluid(out, t.contents(t.water));
          fluid(out, t.contents(t.steam));
        }
      } else {
        var inv = n.level().getCapability(Capabilities.ItemHandler.BLOCK, n.pos(), null);
        if (inv == null || inv.getSlots() > 256) return new Reading(false, SignalSet.EMPTY);
        for (int slot = 0; slot < inv.getSlots(); slot++) item(out, inv.getStackInSlot(slot));
      }
    }
    if (be instanceof MachineBlockEntity m && (settings.ingredients() || settings.results()))
      recipe(m, settings, out);
    if (be instanceof LabBlockEntity lab && settings.researchInfo()) research(lab, out);
    return new Reading(true, out.build());
  }

  private static void recipe(
      MachineBlockEntity m, CircuitSettings settings, SignalSet.Builder out) {
    if (m.petroleum() != null) {
      var holder = m.petroleum().circuitRecipe();
      if (holder == null) return;
      var r = holder.value();
      if (settings.ingredients()) {
        for (var part : r.items()) {
          var choices = part.ingredient().getItems();
          if (choices.length > 0) item(out, choices[0].copyWithCount(part.count()));
        }
        for (var part : r.fluids()) {
          var choices = part.ingredient().getStacks();
          if (choices.length > 0) fluid(out, choices[0].copyWithAmount(part.amount()));
        }
      }
      if (settings.results()) {
        r.results().forEach(stack -> item(out, stack));
        r.fluidResults().forEach(stack -> fluid(out, stack));
      }
      return;
    }
    var holder = m.circuitRecipe();
    if (holder == null) return;
    var r = holder.value();
    if (settings.ingredients())
      for (var part : r.inputs()) {
        var choices = part.ingredient().getItems();
        if (choices.length > 0) item(out, choices[0].copyWithCount(part.count()));
      }
    if (settings.results()) item(out, r.result());
  }

  private static void research(LabBlockEntity lab, SignalSet.Builder out) {
    var level = (ServerLevel) lab.getLevel();
    if (!ResearchTeams.exists(level.getServer(), lab.scope())) return;
    var ledger = ResearchData.get(level.getServer()).team(lab.scope());
    var id = ResourceLocation.tryParse(ledger.selected());
    var e = id == null ? null : ResearchCatalog.get(level).entry(id);
    if (e == null) return;
    int cost = e.logic().cost(ledger.level(id.toString()));
    out.add(
        new SignalKey(SignalKey.Type.TECHNOLOGY, id.toString()), ledger.level(id.toString()) + 1);
    out.add(SignalKey.virtual("research_cost"), cost)
        .add(SignalKey.virtual("research_units"), ledger.units(e.logic()))
        .add(SignalKey.virtual("research_level"), ledger.level(id.toString()) + 1);
    e.recipe()
        .packs()
        .forEach(
            (key, count) ->
                out.add(SignalKey.item(key.toString()), count * (cost - ledger.units(e.logic()))));
  }

  public static boolean condition(CircuitAttachment n) {
    var conditions = n.settings().conditions();
    boolean each =
        conditions.stream()
            .anyMatch(
                c ->
                    c.first().mode() == SignalProgram.Mode.EACH
                        || c.second().mode() == SignalProgram.Mode.EACH);
    if (!each) return SignalProgram.matches(conditions, n.inputs, null);
    for (var key : n.input.values().keySet())
      if (SignalProgram.matches(conditions, n.inputs, key)) return true;
    return false;
  }

  public static void controlChanged(CircuitAttachment n) {
    if (n.owner() instanceof BeltBlockEntity belt && belt.loaded()) belt.refreshCircuitControl();
    if (n.owner() instanceof FluidBlockEntity pump && pump.kind() == FluidKind.PUMP)
      FluidSimulation.activatePump(pump);
    if (n.owner() instanceof LabBlockEntity l && !n.active) request(n, l, null);
  }

  public static void apply(CircuitAttachment n) {
    if (!n.available() || !n.inputReady) return;
    var settings = n.settings();
    if (n.owner() instanceof InserterBlockEntity arm) {
      if (settings.filters()) {
        var selected =
            n.input.values().entrySet().stream()
                .filter(
                    e ->
                        e.getKey().type() == SignalKey.Type.ITEM
                            && e.getValue() > 0
                            && BuiltInRegistries.ITEM.containsKey(
                                ResourceLocation.parse(e.getKey().id())))
                .sorted(
                    Map.Entry.<SignalKey, Integer>comparingByValue()
                        .reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(5)
                .map(e -> e.getKey().id())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!selected.equals(n.filterIds)) {
          n.filterIds = selected;
          arm.circuitFiltersChanged();
        }
      }
      if (settings.hand())
        n.handLimit = Math.clamp(n.input.get(settings.handSignal()), 1, arm.researchedMaximum());
    }
    if (n.owner() instanceof MachineBlockEntity m && settings.recipe())
      for (var entry : ordered(n.input, SignalKey.Type.RECIPE)) {
        if (m.circuitSelectRecipe(ResourceLocation.parse(entry.getKey().id()))) break;
      }
    if (n.owner() instanceof LabBlockEntity lab) {
      ResourceLocation wanted = null;
      if (settings.selectResearch()
          && ResearchTeams.exists(((ServerLevel) n.level()).getServer(), lab.scope())) {
        var ledger = ResearchData.get(((ServerLevel) n.level()).getServer()).team(lab.scope());
        for (var entry : ordered(n.input, SignalKey.Type.TECHNOLOGY)) {
          var id = ResourceLocation.parse(entry.getKey().id());
          var e = ResearchCatalog.get(n.level()).entry(id);
          if (e != null && !e.recipe().sandboxOnly() && ledger.available(e.logic())) {
            wanted = id;
            break;
          }
        }
      }
      request(n, lab, wanted);
    }
  }

  private static List<Map.Entry<SignalKey, Integer>> ordered(SignalSet input, SignalKey.Type type) {
    return input.values().entrySet().stream()
        .filter(e -> e.getKey().type() == type && e.getValue() > 0)
        .sorted(
            Map.Entry.<SignalKey, Integer>comparingByValue()
                .reversed()
                .thenComparing(Map.Entry.comparingByKey()))
        .toList();
  }

  private static void request(
      CircuitAttachment owner, LabBlockEntity lab, ResourceLocation wanted) {
    var teams = RESEARCH.computeIfAbsent(owner.level(), k -> new HashMap<>());
    var requests = teams.computeIfAbsent(lab.scope(), k -> new TreeMap<>());
    if (wanted == null) requests.remove(owner.pos());
    else requests.put(owner.pos(), new Request(owner, wanted));
  }

  public static void selectResearch(ServerLevel level) {
    var teams = RESEARCH.get(level);
    if (teams == null) return;
    var control = RESEARCH_CONTROL.computeIfAbsent(level, k -> new HashMap<>());
    control.clear();
    int budget = factorycraft.config.FactoryConfig.CIRCUIT_DEVICE_WORK.get();
    for (var team : teams.entrySet()) {
      if (budget <= 0) break;
      if (!ResearchTeams.exists(level.getServer(), team.getKey())) continue;
      var data = ResearchData.get(level.getServer());
      var ledger = data.team(team.getKey());
      // Running/manual research owns its progress. Do not poll every requesting lab each tick.
      if (!ledger.selected().isEmpty()) continue;
      var iterator = team.getValue().values().iterator();
      while (iterator.hasNext() && budget-- > 0) {
        var request = iterator.next();
        var owner = request.owner;
        if (!owner.active
            || !owner.available()
            || !owner.inputReady
            || !owner.settings().selectResearch()) {
          iterator.remove();
          continue;
        }
        var entry = ResearchCatalog.get(level).entry(request.technology);
        if (entry == null || entry.recipe().sandboxOnly() || !ledger.available(entry.logic())) {
          iterator.remove();
          continue;
        }
        if (ledger.select(entry.logic())) {
          data.setDirty();
          control.put(team.getKey(), owner.pos());
          break;
        }
      }
    }
  }

  public static boolean researchControlled(Level level, String scope) {
    var teams = RESEARCH.get(level);
    var requests = teams == null ? null : teams.get(scope);
    return requests != null
        && requests.values().stream()
            .anyMatch(r -> r.owner.active && r.owner.inputReady && r.owner.available());
  }

  public static void unload(Level level) {
    RESEARCH.remove(level);
    RESEARCH_CONTROL.remove(level);
  }

  private static Reading legacyRead(CircuitAttachment attachment) {
    var n = attachment.legacy();
    var level = (ServerLevel) n.getLevel();
    var p = n.targetPosition();
    if (level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4) == null
        || !level.shouldTickBlocksAt(
            net.minecraft.world.level.ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4)))
      return new Reading(false, SignalSet.EMPTY);
    var endpoint = FluidConnections.endpoint(level, p);
    CircuitSimulation.watchSensor(attachment, p, endpoint);
    if (endpoint instanceof FluidBlockEntity fluid && !fluid.simulationAvailable())
      return new Reading(false, SignalSet.EMPTY);
    var owner = AssemblyAccess.owner(endpoint);
    if (owner != null) {
      if (!owner.loaded() || !owner.assembly().ready()) return new Reading(false, SignalSet.EMPTY);
      for (var cell :
          owner
              .assembly()
              .profile()
              .cells(
                  endpoint.getBlockPos(),
                  owner.assembly().profile().facing(endpoint.getBlockState())))
        if (!level.shouldTickBlocksAt(
            net.minecraft.world.level.ChunkPos.asLong(cell.getX() >> 4, cell.getZ() >> 4)))
          return new Reading(false, SignalSet.EMPTY);
    }
    if (endpoint instanceof BeltBlockEntity belt
        && (!belt.loaded() || !factorycraft.simulation.BeltSimulation.tickable(belt)))
      return new Reading(false, SignalSet.EMPTY);
    if (!n.readerSettings().valid()) return new Reading(false, SignalSet.EMPTY);
    int total = 0;
    if (n.readerSettings().fluids()) {
      var handler =
          level.getCapability(
              Capabilities.FluidHandler.BLOCK,
              p,
              n.getBlockState().getValue(FactoryBlock.FACING).getOpposite());
      if (handler == null || handler.getTanks() < 1 || handler.getTanks() > 64)
        return new Reading(false, SignalSet.EMPTY);
      for (int i = 0; i < handler.getTanks(); i++) {
        var fluid = handler.getFluidInTank(i);
        if (n.readerSettings().accepts(fluid)) total += fluid.getAmount();
      }
    } else {
      var handler =
          level.getCapability(
              Capabilities.ItemHandler.BLOCK,
              p,
              n.getBlockState().getValue(FactoryBlock.FACING).getOpposite());
      if (handler == null || handler.getSlots() < 1 || handler.getSlots() > 256)
        return new Reading(false, SignalSet.EMPTY);
      for (int i = 0; i < handler.getSlots(); i++) {
        var item = handler.getStackInSlot(i);
        if (n.program().input().equals(CircuitProgram.A)
            || BuiltInRegistries.ITEM.getKey(item.getItem()).equals(n.program().input()))
          total += item.getCount();
      }
    }
    return new Reading(true, SignalSet.of(SignalProgramData.legacy(n.program().output()), total));
  }

  private CircuitDevices() {}
}
