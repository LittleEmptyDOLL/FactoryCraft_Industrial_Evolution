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
  BURNER_DRILL("burner_mining_drill", 0),
  FURNACE("electric_furnace", 0),
  STONE_FURNACE("stone_furnace", 0),
  ASSEMBLER("assembling_machine", 0),
  ADVANCED_ASSEMBLER("assembling_machine_2", 0),
  ASSEMBLER_3("assembling_machine_3", 0),
  LAB("lab", 0),
  BEACON("beacon", 0),
  POLE("electric_pole", 0),
  MEDIUM_POLE("medium_electric_pole", 0),
  BIG_POLE("big_electric_pole", 0),
  SUBSTATION("substation", 0),
  STEEL_FURNACE("steel_furnace", 0),
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
  SELECTOR("selector_combinator", 0),
  GUN_TURRET("gun_turret", 0),
  LASER_TURRET("laser_turret", 0),
  FLAME_TURRET("flamethrower_turret", 0),
  SOLAR_PANEL("solar_panel", 0),
  ACCUMULATOR("accumulator", 0),
  CENTRIFUGE("centrifuge", 0),
  NUCLEAR_REACTOR("nuclear_reactor", 0),
  HEAT_PIPE("heat_pipe", 0),
  HEAT_EXCHANGER("heat_exchanger", 0),
  STEAM_TURBINE("steam_turbine", 0);
  public final String id;
  public final int beltTier;

  FactoryKind(String id, int beltTier) {
    this.id = id;
    this.beltTier = beltTier;
  }

  public factorycraft.simulation.power.PoleSpec poleType() {
    return factorycraft.simulation.power.PoleSpec.byId(id);
  }

  public boolean nuclear() {
    return this == SOLAR_PANEL
        || this == ACCUMULATOR
        || this == NUCLEAR_REACTOR
        || this == HEAT_PIPE
        || this == HEAT_EXCHANGER
        || this == STEAM_TURBINE;
  }

  public boolean turret() {
    return this == GUN_TURRET || this == LASER_TURRET || this == FLAME_TURRET;
  }

  public boolean pole() {
    return poleType() != null;
  }

  public boolean circuit() {
    return this == CONSTANT || this == ARITHMETIC || this == DECIDER || this == SELECTOR;
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
    return this == ASSEMBLER_3
        || this == ADVANCED_ASSEMBLER
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

  public boolean furnace() {
    return this == FURNACE || this == STONE_FURNACE || this == STEEL_FURNACE;
  }

  public boolean burnerFurnace() {
    return this == STONE_FURNACE || this == STEEL_FURNACE;
  }

  public int craftingSpeed() {
    return this == FURNACE || this == STEEL_FURNACE ? 2 : 1;
  }

  public boolean assembler() {
    return this == ASSEMBLER || this == ADVANCED_ASSEMBLER || this == ASSEMBLER_3;
  }

  public boolean drill() {
    return this == DRILL || this == BURNER_DRILL;
  }

  public boolean burner() {
    return burnerFurnace() || this == BURNER_DRILL;
  }

  public int workQuarters() {
    return switch (this) {
      case ASSEMBLER -> 2;
      case ADVANCED_ASSEMBLER -> 3;
      case ASSEMBLER_3 -> 5;
      case BURNER_DRILL -> 1;
      case DRILL -> 2;
      case FURNACE, STEEL_FURNACE -> 8;
      default -> 4;
    };
  }

  public int activeFE() {
    return switch (this) {
      case ASSEMBLER -> 10;
      case ADVANCED_ASSEMBLER -> 20;
      case ASSEMBLER_3 -> 50;
      case CENTRIFUGE -> 47;
      case LAB -> 8;
      case BEACON -> 64;
      case FURNACE -> 24;
      case DRILL, PUMPJACK -> 12;
      case REFINERY -> 56;
      case CHEMICAL_PLANT -> 28;
      default -> 0;
    };
  }

  public int moduleSlots() {
    return switch (this) {
      case ASSEMBLER_3 -> 4;
      case DRILL, REFINERY, CHEMICAL_PLANT -> 3;
      case FURNACE, ADVANCED_ASSEMBLER, PUMPJACK, LAB, BEACON, CENTRIFUGE -> 2;
      default -> 0;
    };
  }

  public boolean supportsModules() {
    return moduleSlots() > 0;
  }

  public boolean processing() {
    return this == CENTRIFUGE
        || furnace()
        || this == ASSEMBLER
        || this == ADVANCED_ASSEMBLER
        || this == ASSEMBLER_3
        || this == REFINERY
        || this == CHEMICAL_PLANT;
  }

  public String recipeGroup() {
    return furnace() ? "smelting" : id;
  }

  public boolean machine() {
    return drill() || furnace() || this == ASSEMBLER || petroleum() || this == CENTRIFUGE;
  }
}
