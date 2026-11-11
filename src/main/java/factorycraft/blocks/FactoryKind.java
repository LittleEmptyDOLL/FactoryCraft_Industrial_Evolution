package factorycraft.blocks;

public enum FactoryKind {
  BELT("transport_belt", 1),
  FAST_BELT("fast_transport_belt", 2),
  EXPRESS_BELT("express_transport_belt", 3),
  LIFT("conveyor_lift", 1),
  FAST_LIFT("fast_conveyor_lift", 2),
  EXPRESS_LIFT("express_conveyor_lift", 3),
  UNDERGROUND("underground_belt", 1),
  FAST_UNDERGROUND("fast_underground_belt", 2),
  EXPRESS_UNDERGROUND("express_underground_belt", 3),
  TRANSPORT_PART("transport_part", 0),
  SPLITTER("splitter", 1),
  INSERTER("inserter", 0),
  DRILL("electric_drill", 0),
  FURNACE("electric_furnace", 0),
  ASSEMBLER("assembling_machine", 0),
  GENERATOR("coal_generator", 0),
  ADVANCED_ASSEMBLER("assembling_machine_2", 0),
  LAB("lab", 0),
  BEACON("beacon", 0),
  POLE("electric_pole", 0),
  MEDIUM_POLE("medium_electric_pole", 0),
  BIG_POLE("big_electric_pole", 0),
  SUBSTATION("substation", 0),
  INDUSTRIAL_FURNACE("industrial_furnace", 0),
  CASING("machine_casing", 0),
  PUMPJACK("pumpjack", 0),
  REFINERY("oil_refinery", 0),
  CHEMICAL_PLANT("chemical_plant", 0),
  REFINERY_CASING("refinery_casing", 0),
  FAST_SPLITTER("fast_splitter", 2),
  EXPRESS_SPLITTER("express_splitter", 3),
  FAST_INSERTER("fast_inserter", 0),
  LONG_INSERTER("long_inserter", 0),
  BULK_INSERTER("bulk_inserter", 0),
  STACK_INSERTER("stack_inserter", 0),
  BURNER_INSERTER("burner_inserter", 0),
  CONSTANT("constant_combinator", 0),
  ARITHMETIC("arithmetic_combinator", 0),
  DECIDER("decider_combinator", 0),
  CIRCUIT_READER("circuit_reader", 0),
  CIRCUIT_CONTROLLER("circuit_controller", 0),
  SELECTOR("selector_combinator", 0);
  public final String id;
  public final int beltTier;

  FactoryKind(String id, int beltTier) {
    this.id = id;
    this.beltTier = beltTier;
  }

  public factorycraft.simulation.power.PoleSpec poleType() {
    return factorycraft.simulation.power.PoleSpec.byId(id);
  }

  public boolean pole() {
    return poleType() != null;
  }

  public boolean circuit() {
    return this == CONSTANT
        || this == ARITHMETIC
        || this == DECIDER
        || this == CIRCUIT_READER
        || this == CIRCUIT_CONTROLLER
        || this == SELECTOR;
  }

  public boolean dualCircuit() {
    return this == ARITHMETIC || this == DECIDER || this == SELECTOR;
  }

  public boolean inserter() {
    return this == BURNER_INSERTER || electricInserter();
  }

  public boolean electricInserter() {
    return this == INSERTER
        || this == FAST_INSERTER
        || this == LONG_INSERTER
        || this == BULK_INSERTER
        || this == STACK_INSERTER;
  }

  public factorycraft.simulation.inserters.ArmCycle.Spec armSpec() {
    return switch (this) {
      case BURNER_INSERTER -> factorycraft.simulation.inserters.ArmCycle.BURNER;
      case LONG_INSERTER -> factorycraft.simulation.inserters.ArmCycle.LONG;
      case FAST_INSERTER -> factorycraft.simulation.inserters.ArmCycle.FAST;
      case BULK_INSERTER -> factorycraft.simulation.inserters.ArmCycle.BULK;
      case STACK_INSERTER -> factorycraft.simulation.inserters.ArmCycle.STACK;
      default -> factorycraft.simulation.inserters.ArmCycle.STANDARD;
    };
  }

  public boolean splitter() {
    return this == SPLITTER || this == FAST_SPLITTER || this == EXPRESS_SPLITTER;
  }

  public boolean lift() {
    return this == LIFT || this == FAST_LIFT || this == EXPRESS_LIFT;
  }

  public boolean underground() {
    return this == UNDERGROUND || this == FAST_UNDERGROUND || this == EXPRESS_UNDERGROUND;
  }

  public boolean ordinaryBelt() {
    return this == BELT || this == FAST_BELT || this == EXPRESS_BELT;
  }

  public boolean petroleum() {
    return this == ADVANCED_ASSEMBLER
        || this == PUMPJACK
        || this == REFINERY
        || this == CHEMICAL_PLANT;
  }

  public boolean large() {
    return factorycraft.multiblock.MachineProfile.byId(id) != null;
  }

  public boolean casing() {
    return this == CASING || this == REFINERY_CASING;
  }

  public boolean belt() {
    return beltTier > 0;
  }

  public boolean processing() {
    return this == FURNACE
        || this == ASSEMBLER
        || this == ADVANCED_ASSEMBLER
        || this == INDUSTRIAL_FURNACE
        || this == REFINERY
        || this == CHEMICAL_PLANT;
  }

  public String recipeGroup() {
    return this == INDUSTRIAL_FURNACE ? FURNACE.id : id;
  }

  public boolean machine() {
    return this == DRILL
        || this == FURNACE
        || this == ASSEMBLER
        || this == GENERATOR
        || this == INDUSTRIAL_FURNACE
        || petroleum();
  }
}
