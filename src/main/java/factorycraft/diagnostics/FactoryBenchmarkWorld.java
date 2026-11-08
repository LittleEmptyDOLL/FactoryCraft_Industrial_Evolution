package factorycraft.diagnostics;

import factorycraft.blockentity.*;
import factorycraft.blocks.*;
import factorycraft.registry.Content;
import factorycraft.simulation.BeltSimulation;
import factorycraft.simulation.benchmark.BenchmarkPlan;
import factorycraft.simulation.benchmark.BenchmarkPlan.*;
import factorycraft.simulation.belts.BeltLane;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Owns only the explicitly opted-in, disposable benchmark world. Scans occur outside sampled ticks. */
final class FactoryBenchmarkWorld {
    static final BlockPos ORIGIN=new BlockPos(1024,80,1024);
    record Audit(long beltItems,long ironUnits,long storedFE,int producers,int inserters,int casings,int activeBelts,String identityHash) {}
    final ServerLevel level;
    final Profile profile;
    final BenchmarkPlan plan;
    private final List<ChunkPos> tickets=new ArrayList<>();
    private int buildCursor;
    FactoryBenchmarkWorld(ServerLevel level,Profile profile) {
        this.level=level;this.profile=profile;plan=new BenchmarkPlan(profile);
    }
    BlockPos position(Cell cell) { return ORIGIN.offset(cell.x(),cell.y(),cell.z()); }
    void pin() {
        var chunks=new TreeSet<Long>();
        for(var cell:plan.blocks())chunks.add(new ChunkPos(position(cell)).toLong());
        for(long value:chunks) {
            var chunk=new ChunkPos(value);level.setChunkForced(chunk.x,chunk.z,true);tickets.add(chunk);
            level.getChunk(chunk.x,chunk.z);
        }
    }
    void release() { for(var c:tickets)level.setChunkForced(c.x,c.z,false);tickets.clear(); }
    int pinnedChunks() { return tickets.size(); }
    boolean buildStep(int budget) {
        for(int end=Math.min(plan.blocks().size(),buildCursor+budget);buildCursor<end;buildCursor++) {
            var cell=plan.blocks().get(buildCursor);var pos=position(cell);
            require(level.getBlockState(pos).isAir(),"Fixture is not empty at "+pos+"; use resetBenchmarkWorld for a fresh measurement");
            require(level.setBlockAndUpdate(pos,state(cell)),"Cannot place fixture cell "+pos);
        }
        return buildCursor==plan.blocks().size();
    }
    private BlockState state(Cell cell) {
        if(cell.role()==Role.SOURCE)return Blocks.CHEST.defaultBlockState();
        var kind=switch(cell.role()) {
            case BELT->FactoryKind.BELT;case FURNACE->FactoryKind.FURNACE;case ASSEMBLER->FactoryKind.ASSEMBLER;
            case INDUSTRIAL_FURNACE->FactoryKind.INDUSTRIAL_FURNACE;case CASING->FactoryKind.CASING;
            case GENERATOR->FactoryKind.GENERATOR;case POLE->FactoryKind.POLE;case INSERTER->FactoryKind.INSERTER;
            case SOURCE->throw new IllegalStateException();
        };
        return Content.FACTORY.get(kind).get().defaultBlockState().setValue(FactoryBlock.FACING,Direction.valueOf(cell.heading().name()));
    }
    private Item plate() { return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("factorycraft","iron_plate")); }
    private Item gear() { return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("factorycraft","iron_gear")); }
    void seed() {
        for(var cell:plan.blocks())if(level.getBlockEntity(position(cell)) instanceof MachineBlockEntity machine && machine.assembly()!=null)
            require(machine.assembly().formAutomatic(),"Full-size benchmark structure failed to form");
        for(var cell:plan.blocks()) {
            var be=level.getBlockEntity(position(cell));
            switch(cell.role()) {
                case BELT -> {
                    var belt=(BeltBlockEntity)be;require(belt.simulationLoaded,"Belt has not loaded");
                    for(int lane=0;lane<2;lane++)belt.lane(lane).restore(new ItemStack(Items.COPPER_INGOT),.5);
                    belt.setChanged();belt.wakeTransport();
                }
                case FURNACE,INDUSTRIAL_FURNACE -> {
                    var machine=(MachineBlockEntity)be;machine.items.setStackInSlot(0,new ItemStack(Items.RAW_IRON,64));
                    if(machine.assembly()!=null)require(machine.assembly().formAutomatic(),"Industrial fixture did not form");
                }
                case ASSEMBLER -> { var machine=(MachineBlockEntity)be;for(int slot=0;slot<4;slot++)machine.items.setStackInSlot(slot,new ItemStack(plate(),64)); }
                case SOURCE -> ((ChestBlockEntity)be).setItem(0,new ItemStack(Items.RAW_IRON,64));
                case GENERATOR -> ((MachineBlockEntity)be).items.setStackInSlot(8,new ItemStack(Items.COAL,4));
                default -> {}
            }
        }
    }
    Audit audit() {
        long copper=0,iron=0,energy=0;int producers=0,inserters=0,casings=0,active=0;
        MessageDigest digest;
        try { digest=MessageDigest.getInstance("SHA-256"); }catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        for(var cell:plan.blocks()) {
            var pos=position(cell);require(level.hasChunkAt(pos),"Fixture chunk became unavailable");
            var current=level.getBlockState(pos);
            require(current.getBlock()==state(cell).getBlock(),"Missing/wrong fixture block at "+pos);
            var be=level.getBlockEntity(pos);
            switch(cell.role()) {
                case BELT -> {
                    var belt=(BeltBlockEntity)be;if(belt.isScheduled())active++;
                    for(int lane=0;lane<2;lane++) {
                        int previous=Integer.MAX_VALUE;
                        for(var carrier:belt.lane(lane).carriers()) {
                            require(carrier.value.is(Items.COPPER_INGOT) && carrier.value.getCount()==1,"Unexpected/stacked belt carrier");
                            require(carrier.positionUnits()>=0 && carrier.positionUnits()<=BeltLane.LENGTH && previous-carrier.positionUnits()>=BeltLane.SPACING,"Invalid belt spacing/position");
                            previous=carrier.positionUnits();copper++;
                        }
                    }
                }
                case FURNACE,ASSEMBLER,INDUSTRIAL_FURNACE -> {
                    var machine=(MachineBlockEntity)be;producers++;energy+=machine.power.getEnergyStored();
                    for(int slot=0;slot<machine.items.getSlots();slot++)iron+=units(machine.items.getStackInSlot(slot));
                    if(machine.assembly()!=null) {
                        var assembly=machine.assembly();require(assembly.automatic() && assembly.ready(),"Industrial structure is not operational");
                        digest.update((pos.asLong()+":"+assembly.identity()+";").getBytes(StandardCharsets.UTF_8));
                        for(var partPos:assembly.positions())require(level.getBlockEntity(partPos) instanceof CasingBlockEntity part && part.virtualPart() && part.boundTo(pos,assembly.identity()),"Part lost its identity");
                    }
                }
                case INSERTER -> { inserters++;iron+=units(((InserterBlockEntity)be).held()); }
                case SOURCE -> { var chest=(ChestBlockEntity)be;for(int slot=0;slot<chest.getContainerSize();slot++)iron+=units(chest.getItem(slot)); }
                case CASING -> casings++;
                default -> {}
            }
        }
        require(level.getEntitiesOfClass(ItemEntity.class,new AABB(ORIGIN).expandTowards(402,50,122).inflate(3)).isEmpty(),"Fixture produced unexpected item entities");
        require(copper==profile.loops*200L,"Belt resource conservation failed: "+copper);
        require(iron==profile.cells*512L,"Production resource conservation failed: "+iron);
        require(producers==profile.cells*4 && inserters==profile.cells*2 && casings==profile.cells*130,"Workload counts changed");
        require(BeltSimulation.snapshot(level).loaded()==profile.loops*100,"Not every belt registered");
        return new Audit(copper,iron,energy,producers,inserters,casings,active,HexFormat.of().formatHex(digest.digest()));
    }
    private long units(ItemStack stack) {
        if(stack.isEmpty())return 0;
        require(stack.is(Items.RAW_IRON) || stack.is(plate()) || stack.is(gear()),"Unexpected production item "+stack);
        return (long)stack.getCount()*(stack.is(gear()) ? 2:1);
    }
    /** Exact authoritative state before any restart tick; excludes client snapshot/animation timestamps. */
    String persistentHash() {
        MessageDigest digest;
        try { digest=MessageDigest.getInstance("SHA-256"); }catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        for(var cell:plan.blocks()) {
            var be=level.getBlockEntity(position(cell));require(be!=null,"Missing persisted fixture entity");
            digest.update((position(cell).asLong()+":"+cell.role()+";").getBytes(StandardCharsets.UTF_8));
            var tag=be.saveWithoutMetadata(level.registryAccess());
            // Absolute display timestamps are regenerated on save, not part of resource ownership.
            tag.remove("SnapshotTick");tag.remove("PhaseTick");hashTag(digest,tag);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static void hashTag(MessageDigest digest,Tag tag) {
        digest.update(tag.getId());
        if(tag instanceof CompoundTag compound) {
            for(String key:new TreeSet<>(compound.getAllKeys())) {
                digest.update((key.length()+":"+key).getBytes(StandardCharsets.UTF_8));hashTag(digest,compound.get(key));
            }
            digest.update((byte)0);
        } else if(tag instanceof ListTag list) {
            for(var element:list)hashTag(digest,element);digest.update((byte)0);
        } else digest.update(tag.toString().getBytes(StandardCharsets.UTF_8));
    }
    BlockPos probePole() { return ORIGIN.offset(5,0,4); }
    void removeProbe() { level.removeBlock(probePole(),false); }
    void restoreProbe() { level.setBlockAndUpdate(probePole(),Content.FACTORY.get(FactoryKind.POLE).get().defaultBlockState()); }
    static void require(boolean valid,String message) { if(!valid)throw new IllegalStateException(message); }
}
