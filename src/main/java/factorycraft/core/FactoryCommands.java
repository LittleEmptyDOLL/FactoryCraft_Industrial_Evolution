package factorycraft.core;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.fluids.FluidKind;
import factorycraft.multiblock.StructurePlacement;
import factorycraft.registry.Content;
import factorycraft.thermal.ThermalKind;
import net.minecraft.commands.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = FactoryCraft.ID)
public final class FactoryCommands {
  @SubscribeEvent
  public static void register(RegisterCommandsEvent event) {
    var root = Commands.literal("factorycraft").requires(s -> s.hasPermission(2));
    for (String name :
        new String[] {
          "powerworks",
          "demo",
          "smelter",
          "waterworks",
          "steamworks",
          "oilworks",
          "petrochemistry",
          "researchworks",
          "chemicalworks",
          "moduleworks",
          "transportworks",
          "transportlab",
          "sortingworks",
          "tieredsortingworks",
          "inserterworks",
          "inserterlab",
          "circuitworks",
          "fluidcircuitworks",
          "circuitsortingworks"
        }) root.then(Commands.literal(name).executes(c -> build(c.getSource(), name)));
    root.then(
        Commands.literal("showroom")
            .executes(c -> factorycraft.diagnostics.MachineShowcase.build(c.getSource())));
    root.then(
        Commands.literal("power")
            .executes(
                c -> {
                  c.getSource()
                      .sendSuccess(
                          () ->
                              Component.literal(
                                  factorycraft.energy.PowerGrid.statistics(
                                      c.getSource().getLevel())),
                          false);
                  return 1;
                }));
    root.then(
        Commands.literal("fluids")
            .executes(
                c -> {
                  c.getSource()
                      .sendSuccess(
                          () ->
                              Component.literal(
                                  factorycraft.fluids.FluidSimulation.statistics(
                                      c.getSource().getLevel())),
                          false);
                  return 1;
                }));
    root.then(
        Commands.literal("profile")
            .executes(
                c -> {
                  c.getSource()
                      .sendSuccess(
                          () ->
                              Component.literal(
                                  factorycraft.simulation.BeltSimulation.statistics(
                                      c.getSource().getLevel())),
                          false);
                  return 1;
                }));
    root.then(
        Commands.literal("circuits")
            .executes(
                c -> {
                  c.getSource()
                      .sendSuccess(
                          () ->
                              Component.literal(
                                  factorycraft.circuits.CircuitSimulation.statistics(
                                      c.getSource().getLevel())),
                          false);
                  return 1;
                }));
    root.then(
        Commands.literal("inserterresearch")
            .then(
                Commands.argument(
                        "capacity",
                        com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 7))
                    .then(
                        Commands.argument(
                                "belt",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 2))
                            .executes(
                                c ->
                                    factorycraft.inserters.InserterResearch.grant(
                                        c.getSource().getPlayerOrException(),
                                        com.mojang.brigadier.arguments.IntegerArgumentType
                                            .getInteger(c, "capacity"),
                                        com.mojang.brigadier.arguments.IntegerArgumentType
                                            .getInteger(c, "belt"))))));
    event.getDispatcher().register(root);
  }

  private static int build(CommandSourceStack source, String name) {
    var level = source.getLevel();
    var origin = BlockPos.containing(source.getPosition()).offset(3, 0, 3);
    int depth = name.equals("transportlab") ? 30 : name.equals("petrochemistry") ? 16 : 13;
    if (name.equals("petrochemistry")
        && !factorycraft.diagnostics.AdvancedOilFactory.available(level)) {
      source.sendFailure(Component.translatable("command.factorycraft.petrochemistry_recipe"));
      return 0;
    }
    if ((name.equals("researchworks") || name.equals("chemicalworks"))
        && (source.getPlayer() == null
            || factorycraft.research.ResearchTeams.resolve(source.getPlayer()).isEmpty())) {
      source.sendFailure(Component.translatable("command.factorycraft.research_team_unavailable"));
      return 0;
    }
    for (var pos :
        BlockPos.betweenClosed(
            origin,
            origin.offset(
                29,
                name.equals("transportlab") ? 18 : name.equals("petrochemistry") ? 12 : 7,
                depth - 1)))
      if (!level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()) {
        source.sendFailure(
            Component.translatable(
                name.equals("petrochemistry")
                    ? "command.factorycraft.petrochemistry_space"
                    : "command.factorycraft.full_size_space"));
        return 0;
      }
    for (int x = 0; x < 30; x++)
      for (int z = 0; z < depth; z++)
        level.setBlockAndUpdate(origin.offset(x, 0, z), Blocks.STONE_BRICKS.defaultBlockState());
    switch (name) {
      case "powerworks" ->
          factorycraft.diagnostics.PowerShowcase.build(
              level,
              origin.above(),
              source.getPlayer() == null
                  ? "unavailable"
                  : factorycraft.research.ResearchTeams.resolve(source.getPlayer())
                      .orElse("unavailable"));
      case "demo", "smelter" -> ironLine(level, origin.above(), name.equals("smelter"));
      case "waterworks" -> waterLine(level, origin.above(), false);
      case "steamworks" -> waterLine(level, origin.above(), true);
      case "oilworks" -> oilLine(level, origin.above());
      case "petrochemistry" ->
          factorycraft.diagnostics.AdvancedOilFactory.build(level, origin.above());
      case "circuitsortingworks" ->
          factorycraft.diagnostics.CircuitSortingFactory.build(level, origin.above());
      case "circuitworks" -> factorycraft.diagnostics.CircuitFactory.build(level, origin.above());
      case "fluidcircuitworks" ->
          factorycraft.diagnostics.FluidCircuitFactory.build(level, origin.above());
      case "inserterlab" ->
          factorycraft.diagnostics.InserterShowcase.build(
              level,
              origin.above(),
              source.getPlayer() == null
                  ? ""
                  : factorycraft.research.ResearchTeams.resolve(source.getPlayer()).orElse(""));
      case "inserterworks" -> factorycraft.diagnostics.InserterFactory.build(level, origin.above());
      case "tieredsortingworks" ->
          factorycraft.diagnostics.SortingFactory.buildTiered(level, origin.above());
      case "sortingworks" -> factorycraft.diagnostics.SortingFactory.build(level, origin.above());
      case "transportlab" ->
          factorycraft.diagnostics.TransportShowcase.build(level, origin.above());
      case "transportworks" ->
          factorycraft.diagnostics.TransportFactory.build(level, origin.above());
      case "moduleworks" -> factorycraft.diagnostics.ModuleFactory.build(level, origin.above());
      case "chemicalworks" ->
          factorycraft.diagnostics.ChemicalScienceFactory.build(
              level,
              origin.above(),
              factorycraft.research.ResearchTeams.resolve(source.getPlayer()).orElseThrow());
      case "researchworks" ->
          factorycraft.diagnostics.ResearchFactory.build(
              level,
              origin.above(),
              factorycraft.research.ResearchTeams.resolve(source.getPlayer()).orElseThrow());
      default -> throw new IllegalArgumentException(name);
    }
    source.sendSuccess(
        () ->
            name.equals("tieredsortingworks")
                ? Component.translatable(
                    "command.factorycraft.tiered_sorting", origin.toShortString())
                : Component.translatable(
                    "command.factorycraft.full_size_built", name, origin.toShortString()),
        true);
    return 1;
  }

  public static MachineBlockEntity machine(
      ServerLevel level, BlockPos pos, FactoryKind kind, Direction facing) {
    var state =
        Content.FACTORY.get(kind).get().defaultBlockState().setValue(FactoryBlock.FACING, facing);
    var controller = StructurePlacement.fromOrigin(level, pos, state);
    return level.getBlockEntity(controller) instanceof MachineBlockEntity m ? m : null;
  }

  private static void pole(ServerLevel level, BlockPos origin, int x, int z) {
    machine(level, origin.offset(x, 0, z), FactoryKind.MEDIUM_POLE, Direction.NORTH);
  }

  private static void arm(ServerLevel level, BlockPos origin, int x, int z, Direction direction) {
    machine(level, origin.offset(x, 0, z), FactoryKind.INSERTER, direction);
  }

  private static void generator(ServerLevel level, BlockPos origin, int x, int z) {
    machine(level, origin.offset(x, 0, z), FactoryKind.GENERATOR, Direction.NORTH)
        .items
        .setStackInSlot(8, new ItemStack(Items.COAL, 8));
  }

  /** Complete finite-resource line; shared with the physical integration test. */
  public static ChestBlockEntity ironLine(ServerLevel level, BlockPos origin, boolean industrial) {
    var ore = origin.offset(1, -1, 3);
    level.setBlockAndUpdate(ore, Content.IRON_ORE.get().defaultBlockState());
    machine(level, origin.offset(0, 0, 2), FactoryKind.DRILL, Direction.NORTH);
    arm(level, origin, 3, 3, Direction.EAST);
    machine(level, origin.offset(4, 0, 3), FactoryKind.BELT, Direction.EAST);
    machine(level, origin.offset(5, 0, 3), FactoryKind.BELT, Direction.EAST);
    arm(level, origin, 6, 3, Direction.EAST);
    machine(
        level,
        origin.offset(7, 0, 2),
        industrial ? FactoryKind.INDUSTRIAL_FURNACE : FactoryKind.FURNACE,
        Direction.NORTH);
    arm(level, origin, 10, 3, Direction.EAST);
    machine(level, origin.offset(11, 0, 2), FactoryKind.ASSEMBLER, Direction.NORTH);
    arm(level, origin, 14, 3, Direction.EAST);
    level.setBlockAndUpdate(origin.offset(15, 0, 3), Blocks.CHEST.defaultBlockState());
    generator(level, origin, 5, 7);
    for (int x : new int[] {4, 9, 13}) pole(level, origin, x, 5);
    return (ChestBlockEntity) level.getBlockEntity(origin.offset(15, 0, 3));
  }

  private static void fluid(ServerLevel level, BlockPos origin, int x, int z, FluidKind kind) {
    var state = Content.FLUID_BLOCKS.get(kind).get().defaultBlockState();
    if (kind != FluidKind.PIPE)
      state =
          state.setValue(
              FluidDeviceBlock.FACING, kind == FluidKind.TANK ? Direction.NORTH : Direction.EAST);
    StructurePlacement.place(level, origin.offset(x, 0, z), state);
  }

  private static void waterLine(ServerLevel level, BlockPos o, boolean thermal) {
    int pumpX = thermal ? 4 : 3;
    var water = o.offset(pumpX - 2, 0, 2);
    level.setBlockAndUpdate(water.below(), Blocks.STONE.defaultBlockState());
    for (var side : Direction.Plane.HORIZONTAL)
      if (side != Direction.EAST)
        level.setBlockAndUpdate(water.relative(side), Blocks.STONE.defaultBlockState());
    level.setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
    fluid(level, o, pumpX, 2, FluidKind.PUMP);
    if (thermal) {
      StructurePlacement.place(
          level,
          o.offset(8, 0, 2),
          Content.THERMAL_BLOCKS
              .get(ThermalKind.BOILER)
              .get()
              .defaultBlockState()
              .setValue(ThermalBlock.FACING, Direction.EAST));
      ((ThermalBlockEntity) level.getBlockEntity(o.offset(8, 0, 2)))
          .items.setStackInSlot(0, new ItemStack(Items.COAL, 8));
      StructurePlacement.place(
          level,
          o.offset(15, 0, 2),
          Content.THERMAL_BLOCKS
              .get(ThermalKind.ENGINE)
              .get()
              .defaultBlockState()
              .setValue(ThermalBlock.FACING, Direction.EAST));
      for (var p :
          java.util.List.of(
              new BlockPos(5, 0, 2),
              new BlockPos(5, 0, 3),
              new BlockPos(5, 0, 4),
              new BlockPos(6, 0, 4),
              new BlockPos(7, 0, 4),
              new BlockPos(9, 0, 2),
              new BlockPos(10, 0, 2),
              new BlockPos(11, 0, 2),
              new BlockPos(12, 0, 2))) fluid(level, o, p.getX(), p.getZ(), FluidKind.PIPE);
      StructurePlacement.place(
          level,
          o.offset(22, 0, 2),
          Content.FACTORY.get(FactoryKind.FURNACE).get().defaultBlockState());
      ((MachineBlockEntity) level.getBlockEntity(o.offset(22, 0, 2)))
          .items.setStackInSlot(0, new ItemStack(Items.RAW_IRON, 8));
      for (int x : new int[] {4, 10, 16, 22}) pole(level, o, x, 5);
    } else {
      for (int x = 4; x <= 6; x++) fluid(level, o, x, 2, FluidKind.PIPE);
      fluid(level, o, 8, 3, FluidKind.TANK);
      pole(level, o, 3, 5);
    }
    generator(level, o, 0, 7);
  }

  public static void oilLine(ServerLevel level, BlockPos o) {
    level.setBlockAndUpdate(o.offset(2, -1, 3), Content.OIL_DEPOSIT.get().defaultBlockState());
    machine(level, o.offset(3, 0, 2), FactoryKind.PUMPJACK, Direction.EAST);
    for (int x = 4; x <= 6; x++) fluid(level, o, x, 2, FluidKind.PIPE);
    machine(level, o.offset(11, 0, 1), FactoryKind.REFINERY, Direction.EAST);
    for (int x = 12; x <= 14; x++) fluid(level, o, x, 5, FluidKind.PIPE);
    fluid(level, o, 14, 4, FluidKind.PIPE);
    machine(level, o.offset(15, 0, 4), FactoryKind.CHEMICAL_PLANT, Direction.WEST);
    level.setBlockAndUpdate(o.offset(16, 0, 0), Blocks.CHEST.defaultBlockState());
    ((ChestBlockEntity) level.getBlockEntity(o.offset(16, 0, 0)))
        .setItem(0, new ItemStack(Items.COAL, 32));
    arm(level, o, 16, 1, Direction.SOUTH);
    arm(level, o, 18, 3, Direction.EAST);
    level.setBlockAndUpdate(o.offset(19, 0, 3), Blocks.CHEST.defaultBlockState());
    generator(level, o, 4, 8);
    for (int x : new int[] {3, 8, 13, 17}) pole(level, o, x, 6);
    pole(level, o, 18, 0);
  }

  private FactoryCommands() {}
}
