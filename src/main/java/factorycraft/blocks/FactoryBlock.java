package factorycraft.blocks;

import com.mojang.serialization.MapCodec;
import factorycraft.blockentity.*;
import factorycraft.registry.Content;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import java.util.List;

public final class FactoryBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<factorycraft.multiblock.MachineProfile> PROFILE=EnumProperty.create("profile",factorycraft.multiblock.MachineProfile.class);
    public static final BooleanProperty ACTIVE = BlockStateProperties.LIT;
    public final FactoryKind kind;
    public FactoryBlock(FactoryKind kind, Properties properties) {
        super(properties); this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(p -> new FactoryBlock(kind, p)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, ACTIVE,PROFILE); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { var profile=factorycraft.multiblock.MachineProfile.byId(kind.id);return defaultBlockState().setValue(FACING,profile==null ? c.getHorizontalDirection():profile.placementFacing(c.getHorizontalDirection())); }
    @Override protected BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState s, Mirror m) { return rotate(s, m.getRotation(s.getValue(FACING))); }
    @Override protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        if(kind.casing() && s.getValue(PROFILE)==factorycraft.multiblock.MachineProfile.SPLITTER)return box(0,0,0,16,3,16);
        if (kind.belt()) return box(0, 0, 0, 16, 3, 16);
        if (kind == FactoryKind.INSERTER) return box(5, 0, 5, 11, 8, 11);
        if (kind == FactoryKind.POLE) return box(6, 0, 6, 10, 16, 10);
        return Shapes.block();
    }
    @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        if(kind==FactoryKind.BEACON)return new factorycraft.blockentity.BeaconBlockEntity(p,s);
        if(kind==FactoryKind.LAB)return new LabBlockEntity(p,s);
        if (kind.belt()) return new BeltBlockEntity(p, s);
        if (kind == FactoryKind.INSERTER) return new InserterBlockEntity(p, s);
        if (kind == FactoryKind.POLE) return new PoleBlockEntity(p, s);
        if (kind.casing()) return new CasingBlockEntity(p,s);
        return new MachineBlockEntity(p, s);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        if (l.isClientSide) return null;
        if(kind==FactoryKind.LAB)return createTickerHelper(t,Content.LAB.get(),(a,b,c,d)->d.tick());
        if (kind.belt()) return null;
        if (kind == FactoryKind.INSERTER) return createTickerHelper(t, Content.INSERTER.get(), (a,b,c,d) -> d.tick());
        if (kind.machine()) return createTickerHelper(t, Content.MACHINE.get(), (a,b,c,d) -> d.tick());
        return null;
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(kind.large() || kind.casing())return factorycraft.fluids.FluidBlocks.useItem(stack,level,pos,player,hand,hit.getDirection());
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player player, BlockHitResult hit) {
        if (l.isClientSide) return InteractionResult.SUCCESS;
        if(player.getMainHandItem().is(Content.WRENCH.get()))return useWrench(l,p,player);
        if(l.getBlockEntity(p) instanceof factorycraft.blockentity.BeaconBlockEntity beacon)player.openMenu(beacon,p);
        else if(l.getBlockEntity(p) instanceof LabBlockEntity lab){if(factorycraft.research.ResearchTeams.member(player,lab.scope()))player.openMenu(lab,p);else player.displayClientMessage(Component.translatable("message.factorycraft.research_team_required"),true);}
        else if (l.getBlockEntity(p) instanceof MachineBlockEntity machine) {
            if(machine.assembly()!=null && !machine.assembly().formed() && !machine.assembly().form())
                player.displayClientMessage(Component.translatable("message.factorycraft.assembly_missing"),true);
            player.openMenu(machine,p);
        } else if(l.getBlockEntity(p) instanceof CasingBlockEntity part) {
            var owner=part.genericForRemoval();
            if(owner!=null)factorycraft.multiblock.AssemblyAccess.open(owner,player);
            else player.displayClientMessage(Component.translatable("message.factorycraft.assembly_missing"),true);
        }
        else player.displayClientMessage(Component.translatable("message.factorycraft.direction", s.getValue(FACING).getName()), true);
        return InteractionResult.CONSUME;
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,ItemStack stack) {
        super.setPlacedBy(level,pos,state,placer,stack);if(level.isClientSide || !(placer instanceof Player player))return;
        var scope=factorycraft.research.ResearchTeams.resolve(player).orElse("unavailable");var be=level.getBlockEntity(pos);
        if(be instanceof LabBlockEntity lab && lab.scope().isEmpty())lab.bind(scope);
        if(be instanceof MachineBlockEntity machine && !stack.has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA))machine.bindResearch(scope);
    }
    public InteractionResult useWrench(Level level,BlockPos pos,Player player) {
        if(kind.large() || kind.casing())return factorycraft.multiblock.StructureSupport.wrench(level,pos,player);
        if(!level.isClientSide)level.setBlock(pos,level.getBlockState(pos).cycle(FACING),3);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState s, Level l, BlockPos p, BlockState next, boolean moving) {
        if (!s.is(next.getBlock()) && !l.restoringBlockSnapshots && l.getBlockEntity(p) instanceof FactoryBlockEntity be) be.dropContents();
        super.onRemove(s, l, p, next, moving);
    }
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder params) {
        if(kind.casing() && params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof CasingBlockEntity part && part.virtualPart())return List.of();
        return factorycraft.multiblock.StructureSupport.preserve(super.getDrops(state,params),params);
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player) {
        if(!level.isClientSide && player.isCreative() && level.getBlockEntity(pos) instanceof CasingBlockEntity part && part.virtualPart()) {
            var owner=part.genericForRemoval();if(owner!=null)owner.assembly().removeWhole(false);
        }
        return super.playerWillDestroy(level,pos,state,player);
    }
    @Override public ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state) {
        if(kind.casing() && level.getBlockEntity(pos) instanceof CasingBlockEntity part && part.virtualPart())
            return part.controllerItem();
        return super.getCloneItemStack(level,pos,state);
    }
}
