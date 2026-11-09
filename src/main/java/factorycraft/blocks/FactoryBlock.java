package factorycraft.blocks;

import com.mojang.serialization.MapCodec;
import factorycraft.blockentity.*;
import factorycraft.registry.Content;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class FactoryBlock extends BaseEntityBlock {
  public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
  public static final EnumProperty<factorycraft.multiblock.MachineProfile> PROFILE =
      EnumProperty.create("profile", factorycraft.multiblock.MachineProfile.class);
  public static final BooleanProperty ACTIVE = BlockStateProperties.LIT;
  public final FactoryKind kind;

  public FactoryBlock(FactoryKind kind, Properties properties) {
    super(
        kind.belt() || kind == FactoryKind.TRANSPORT_PART ? properties.dynamicShape() : properties);
    this.kind = kind;
    registerDefaultState(
        stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
  }

  @Override
  protected MapCodec<? extends BaseEntityBlock> codec() {
    return simpleCodec(p -> new FactoryBlock(kind, p));
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
    b.add(FACING, ACTIVE, PROFILE);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext c) {
    var profile = factorycraft.multiblock.MachineProfile.byId(kind.id);
    return defaultBlockState()
        .setValue(
            FACING,
            profile == null
                ? c.getHorizontalDirection()
                : profile.placementFacing(c.getHorizontalDirection()));
  }

  @Override
  protected BlockState rotate(BlockState s, Rotation r) {
    return s.setValue(FACING, r.rotate(s.getValue(FACING)));
  }

  @Override
  protected BlockState mirror(BlockState s, Mirror m) {
    return rotate(s, m.getRotation(s.getValue(FACING)));
  }

  @Override
  protected RenderShape getRenderShape(BlockState s) {
    return RenderShape.MODEL;
  }

  @Override
  protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
    if (kind.casing() && s.getValue(PROFILE).splitter()) return box(0, 0, 0, 16, 3, 16);
    if (kind.belt() || kind == FactoryKind.TRANSPORT_PART) {
      var be = l.getBlockEntity(p);
      var cell =
          be instanceof BeltBlockEntity belt
              ? belt.modelCell(p)
              : be instanceof TransportPartBlockEntity part ? part.cell() : null;
      if (cell != null) return factorycraft.belts.TransportShapes.shape(cell);
      return box(0, 0, 0, 16, 3, 16);
    }
    if (kind.circuit() || kind.casing() && s.getValue(PROFILE).circuit())
      return box(0, 0, 0, 16, 8, 16);
    if (kind.inserter()) return box(5, 0, 5, 11, 8, 11);
    if (kind == FactoryKind.POLE) return box(6, 0, 6, 10, 16, 10);
    return Shapes.block();
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
    if (kind.circuit()) return new factorycraft.circuits.CircuitNodeBlockEntity(p, s);
    if (kind == FactoryKind.TRANSPORT_PART) return new TransportPartBlockEntity(p, s);
    if (kind == FactoryKind.BEACON) return new factorycraft.blockentity.BeaconBlockEntity(p, s);
    if (kind == FactoryKind.LAB) return new LabBlockEntity(p, s);
    if (kind.belt()) return new BeltBlockEntity(p, s);
    if (kind.inserter()) return new InserterBlockEntity(p, s);
    if (kind == FactoryKind.POLE) return new PoleBlockEntity(p, s);
    if (kind.casing()) return new CasingBlockEntity(p, s);
    return new MachineBlockEntity(p, s);
  }

  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
      Level l, BlockState s, BlockEntityType<T> t) {
    if (l.isClientSide) return null;
    if (kind == FactoryKind.TRANSPORT_PART)
      return createTickerHelper(t, Content.TRANSPORT_PART.get(), (a, b, c, d) -> d.tick());
    if (kind == FactoryKind.LAB)
      return createTickerHelper(t, Content.LAB.get(), (a, b, c, d) -> d.tick());
    if (kind.belt()) return null;
    if (kind.inserter())
      return createTickerHelper(t, Content.INSERTER.get(), (a, b, c, d) -> d.tick());
    if (kind.machine())
      return createTickerHelper(t, Content.MACHINE.get(), (a, b, c, d) -> d.tick());
    return null;
  }

  @Override
  protected ItemInteractionResult useItemOn(
      ItemStack stack,
      BlockState state,
      Level level,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      BlockHitResult hit) {
    if (stack.getItem() instanceof factorycraft.circuits.CircuitWireItem wire) {
      var result = wire.useOn(new net.minecraft.world.item.context.UseOnContext(player, hand, hit));
      return result.consumesAction() ? ItemInteractionResult.SUCCESS : ItemInteractionResult.FAIL;
    }
    if (kind.large() || kind.casing())
      return factorycraft.fluids.FluidBlocks.useItem(
          stack, level, pos, player, hand, hit.getDirection());
    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
  }

  @Override
  protected InteractionResult useWithoutItem(
      BlockState s, Level l, BlockPos p, Player player, BlockHitResult hit) {
    if (l.isClientSide) return InteractionResult.SUCCESS;
    if (player.getMainHandItem().is(Content.WRENCH.get())) return useWrench(l, p, player);
    if (l.getBlockEntity(p) instanceof factorycraft.blockentity.BeaconBlockEntity beacon)
      factorycraft.multiblock.MachineInteraction.open(beacon,player,p);
    else if (l.getBlockEntity(p) instanceof LabBlockEntity lab) {
      if (factorycraft.research.ResearchTeams.member(player, lab.scope())) factorycraft.multiblock.MachineInteraction.open(lab,player,p);
      else
        player.displayClientMessage(
            Component.translatable("message.factorycraft.research_team_required"), true);
    } else if (l.getBlockEntity(p) instanceof MachineBlockEntity machine) {
      if (machine.assembly() != null && !machine.assembly().formed() && !machine.assembly().form())
        player.displayClientMessage(
            Component.translatable("message.factorycraft.assembly_missing"), true);
      factorycraft.multiblock.MachineInteraction.open(machine,player,p);
    } else if (l.getBlockEntity(p) instanceof InserterBlockEntity arm && arm.electric()) {
      factorycraft.inserters.InserterMenu.open(arm, player);
    } else if (l.getBlockEntity(p) instanceof factorycraft.circuits.CircuitNodeBlockEntity node) {
      factorycraft.multiblock.MachineInteraction.open(node,player,p);
    } else if (l.getBlockEntity(p) instanceof CasingBlockEntity part) {
      var owner = part.genericForRemoval();
      if (owner instanceof BeltBlockEntity belt && belt.kind().splitter())
        factorycraft.logistics.SplitterMenu.open(belt, player,p);
      else if (owner != null) factorycraft.multiblock.MachineInteraction.open(owner.factoryEntity(),player,p);
      else
        player.displayClientMessage(
            Component.translatable("message.factorycraft.assembly_missing"), true);
    } else if (l.getBlockEntity(p) instanceof BeltBlockEntity belt) {
      if (belt.kind().splitter()) factorycraft.logistics.SplitterMenu.open(belt, player,p);
      else showTransport(belt, player);
    } else if (l.getBlockEntity(p) instanceof TransportPartBlockEntity part
        && part.ownerForRemoval() != null) showTransport(part.ownerForRemoval(), player);
    else
      player.displayClientMessage(
          Component.translatable("message.factorycraft.direction", s.getValue(FACING).getName()),
          true);
    return InteractionResult.CONSUME;
  }

  @Override
  public void setPlacedBy(
      Level level,
      BlockPos pos,
      BlockState state,
      net.minecraft.world.entity.LivingEntity placer,
      ItemStack stack) {
    super.setPlacedBy(level, pos, state, placer, stack);
    if (level.isClientSide || !(placer instanceof Player player)) return;
    var scope = factorycraft.research.ResearchTeams.resolve(player).orElse("unavailable");
    var be = level.getBlockEntity(pos);
    if (be instanceof LabBlockEntity lab && lab.scope().isEmpty()) lab.bind(scope);
    if (be instanceof MachineBlockEntity machine
        && !stack.has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA))
      machine.bindResearch(scope);
  }

  public InteractionResult useWrench(Level level, BlockPos pos, Player player) {
    if (kind.large() || kind.casing())
      return factorycraft.multiblock.StructureSupport.wrench(level, pos, player);
    var target = level.getBlockEntity(pos);
    if (target instanceof TransportPartBlockEntity part) target = part.ownerForRemoval();
    if (target instanceof InserterBlockEntity arm && arm.electric() && player.isShiftKeyDown()) {
      if (!level.isClientSide) level.removeBlock(pos, false);
      return InteractionResult.sidedSuccess(level.isClientSide);
    }
    if (target instanceof BeltBlockEntity belt) {
      if (level.isClientSide) return InteractionResult.SUCCESS;
      if (belt.kind().lift()) {
        if (player.isShiftKeyDown()) belt.removeTransport(true);
        else if (belt.rotateExit()) showTransport(belt, player);
        else
          player.displayClientMessage(
              Component.translatable("message.factorycraft.transport_blocked"), true);
        return InteractionResult.CONSUME;
      }
      if (player.isShiftKeyDown()) {
        boolean ok = belt.kind().underground() ? belt.toggleUnderground() : belt.correctForm();
        if (ok) showTransport(belt, player);
        else
          player.displayClientMessage(
              Component.translatable("message.factorycraft.transport_blocked"), true);
        return InteractionResult.CONSUME;
      }
      pos = belt.getBlockPos();
    }
    if (!level.isClientSide) level.setBlock(pos, level.getBlockState(pos).cycle(FACING), 3);
    return InteractionResult.sidedSuccess(level.isClientSide);
  }

  private static void showTransport(BeltBlockEntity belt, Player player) {
    String mode =
        belt.kind().underground()
            ? belt.undergroundOutput() ? "output" : "input"
            : Integer.toString(belt.formMode());
    player.displayClientMessage(
        Component.translatable(
            "message.factorycraft.transport_state",
            Component.translatable(
                "transport.factorycraft.shape."
                    + belt.route().shape.name().toLowerCase(java.util.Locale.ROOT)),
            Component.translatable(
                "transport.factorycraft.direction." + belt.outputFacing().getName()),
            Component.translatable("transport.factorycraft.mode." + mode)),
        true);
  }

  @Override
  protected void onRemove(BlockState s, Level l, BlockPos p, BlockState next, boolean moving) {
    if (!s.is(next.getBlock())
        && !l.restoringBlockSnapshots
        && l.getBlockEntity(p) instanceof FactoryBlockEntity be) be.dropContents();
    super.onRemove(s, l, p, next, moving);
  }

  @Override
  protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
    if (kind.electricInserter()) return List.of();
    if (kind == FactoryKind.TRANSPORT_PART
        || params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof BeltBlockEntity belt
            && belt.special()) return List.of();
    if (kind.casing()
        && params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
            instanceof CasingBlockEntity part
        && part.virtualPart()) return List.of();
    return factorycraft.multiblock.StructureSupport.preserve(super.getDrops(state, params), params);
  }

  @Override
  public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
    if (!level.isClientSide && player.isCreative()) {
      var b = level.getBlockEntity(pos);
      if (b instanceof TransportPartBlockEntity part && part.ownerForRemoval() != null)
        part.ownerForRemoval().removeTransport(false);
      else if (b instanceof BeltBlockEntity belt && belt.special()) belt.removeTransport(false);
    }
    if (!level.isClientSide
        && player.isCreative()
        && level.getBlockEntity(pos) instanceof CasingBlockEntity part
        && part.virtualPart()) {
      var owner = part.genericForRemoval();
      if (owner != null) owner.assembly().removeWhole(false);
    }
    return super.playerWillDestroy(level, pos, state, player);
  }

  @Override
  public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
    if (level.getBlockEntity(pos) instanceof TransportPartBlockEntity part) {
      var owner = part.owner();
      return owner == null ? ItemStack.EMPTY : new ItemStack(owner.getBlockState().getBlock());
    }
    if (kind.casing()
        && level.getBlockEntity(pos) instanceof CasingBlockEntity part
        && part.virtualPart()) return part.controllerItem();
    return super.getCloneItemStack(level, pos, state);
  }
}
