package factorycraft.gametest;

import factorycraft.FactoryCraft;
import factorycraft.blockentity.ResourceDepositBlockEntity;
import factorycraft.config.ResourceWorldgenConfig;
import factorycraft.registry.Content;
import factorycraft.resources.ResourceKind;
import factorycraft.simulation.resources.ResourceVeins;
import factorycraft.worldgen.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(FactoryCraft.ID)
@PrefixGameTestTemplate(false)
public final class VeinGameTests {
    private static final int Y=70;
    private static Set<BlockPos> plane(GameTestHelper h,ResourceVeins.Patch patch) {
        var chunks=new TreeSet<BlockPos>(Comparator.<BlockPos>comparingInt(p->p.getX()).thenComparingInt(p->p.getZ()));
        for(int x=patch.x()-patch.radius()-1;x<=patch.x()+patch.radius()+1;x++)for(int z=patch.z()-patch.radius()-1;z<=patch.z()+patch.radius()+1;z++) {
            var pos=new BlockPos(x,Y,z);h.getLevel().getChunkAt(pos);h.getLevel().setBlock(pos,Blocks.STONE.defaultBlockState(),2);chunks.add(new BlockPos(x&~15,Y,z&~15));
        }return chunks;
    }
    static void configuredResources(GameTestHelper h) {
        var level=h.getLevel();int index=0;
        for(var kind:ResourceKind.values()) {
            var feature=level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(ResourceLocation.fromNamespaceAndPath(FactoryCraft.ID,kind.id));
            var config=(ResourceVeinConfiguration)feature.config();var settings=ResourceWorldgenConfig.settings();var patch=ResourceVeins.patch(level.getSeed(),config.salt(),200+index++*5,200,settings);var chunks=plane(h,patch);
            // The first call must never write outside its decorating chunk.
            var first=chunks.iterator().next();feature.place(level,level.getChunkSource().getGenerator(),net.minecraft.util.RandomSource.create(1),first);
            for(int x=patch.x()-patch.radius();x<=patch.x()+patch.radius();x++)for(int z=patch.z()-patch.radius();z<=patch.z()+patch.radius();z++)if((x>>4)!=(first.getX()>>4) || (z>>4)!=(first.getZ()>>4))
                h.assertTrue(!level.getBlockState(new BlockPos(x,Y,z)).is(Content.RESOURCES.get(kind).get()),"vein wrote into another chunk");
            for(var origin:chunks)feature.place(level,level.getChunkSource().getGenerator(),net.minecraft.util.RandomSource.create(2),origin);
            int count=0;long total=0;var snapshot=new HashMap<BlockPos,Integer>();
            for(int x=patch.x()-patch.radius();x<=patch.x()+patch.radius();x++)for(int z=patch.z()-patch.radius();z<=patch.z()+patch.radius();z++) {
                int expected=0;for(var candidate:ResourceVeins.inChunk(level.getSeed(),config.salt(),x>>4,z>>4,settings))expected=Math.max(expected,candidate.stock(x,z));if(expected==0)continue;
                var pos=new BlockPos(x,Y,z);h.assertTrue(level.getBlockEntity(pos) instanceof ResourceDepositBlockEntity,"surface field has a hole at chunk seam");var be=(ResourceDepositBlockEntity)level.getBlockEntity(pos);
                h.assertTrue(be.remaining()==expected && expected>=settings.minStock() && expected<=settings.maxStock(),"generated tile stock outside configured range");snapshot.put(pos,expected);total+=expected;count++;
            }
            h.assertTrue(count>80 && total>80L*settings.minStock() && Content.RESOURCES.get(kind).get().asItem()==Items.AIR,"field is small or regenerates from block items");
            var reversed=new ArrayList<>(chunks);Collections.reverse(reversed);for(var origin:reversed)feature.place(level,level.getChunkSource().getGenerator(),net.minecraft.util.RandomSource.create(3),origin);
            for(var entry:snapshot.entrySet())h.assertTrue(((ResourceDepositBlockEntity)level.getBlockEntity(entry.getKey())).remaining()==entry.getValue(),"repeated chunk decoration reset finite stock");
        }h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void explicitVeinRangesAndCodecValidationAreHonored(GameTestHelper h) {
        var settings=new ResourceVeins.Settings(6,6,71,83,6);var config=new ResourceVeinConfiguration(Content.IRON_ORE.get().defaultBlockState(),319,false,settings);
        var ops=h.getLevel().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);var json=ResourceVeinConfiguration.CODEC.encodeStart(ops,config).getOrThrow();
        h.assertTrue(ResourceVeinConfiguration.CODEC.parse(ops,json).getOrThrow().equals(config),"vein settings codec changed ranges");
        json.getAsJsonObject().getAsJsonObject("vein").addProperty("min_radius",7);h.assertTrue(ResourceVeinConfiguration.CODEC.parse(ops,json).error().isPresent(),"inverted radius range accepted");
        var patch=ResourceVeins.patch(h.getLevel().getSeed(),319,240,200,settings);var chunks=plane(h,patch);var feature=new ConfiguredFeature<>(Content.RESOURCE_PATCH_FEATURE.get(),config);
        for(var p:chunks)feature.place(h.getLevel(),h.getLevel().getChunkSource().getGenerator(),net.minecraft.util.RandomSource.create(),p);
        int count=0;for(int x=patch.x()-6;x<=patch.x()+6;x++)for(int z=patch.z()-6;z<=patch.z()+6;z++)if(h.getLevel().getBlockEntity(new BlockPos(x,Y,z)) instanceof ResourceDepositBlockEntity be) {h.assertTrue(be.remaining()>=71 && be.remaining()<=83,"datapack stock range ignored");count++;}
        h.assertTrue(count>50,"explicit radius was not used");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void generatedVeinStockPersistsWithoutReinitializingAcrossProcesses(GameTestHelper h) {
        var level=h.getLevel();var settings=new ResourceVeins.Settings(6,6,123,234,6);var patch=ResourceVeins.patch(level.getSeed(),719,-45,-45,settings);var pos=new BlockPos(patch.x(),Y,patch.z());var marker=pos.above(5);var pending=pos.offset(8,5,8);level.getChunkAt(pos);level.getChunkAt(pending);
        if(!level.getBlockState(marker).is(Blocks.GOLD_BLOCK)) {
            h.assertTrue(level.getBlockState(marker).isAir() && level.getBlockState(pending).isAir() && level.getBlockEntity(pos)==null,"partial vein fixture; refusing to reseed");level.setBlockAndUpdate(pending,Blocks.DIAMOND_BLOCK.defaultBlockState());plane(h,patch);
            new ConfiguredFeature<>(Content.RESOURCE_PATCH_FEATURE.get(),new ResourceVeinConfiguration(Content.COPPER_ORE.get().defaultBlockState(),719,false,settings))
                    .place(level,level.getChunkSource().getGenerator(),net.minecraft.util.RandomSource.create(),pos);
            h.assertTrue(level.getBlockEntity(pos) instanceof ResourceDepositBlockEntity,"no generated disk tile");level.setBlockAndUpdate(marker,Blocks.GOLD_BLOCK.defaultBlockState());level.removeBlock(pending,false);
            System.out.println("FACTORYCRAFT_VEIN_DISK: SEEDED native generated field tile");
        } else System.out.println("FACTORYCRAFT_VEIN_DISK: VERIFIED generated stock across server processes");
        h.assertTrue(level.getBlockEntity(pos) instanceof ResourceDepositBlockEntity && ((ResourceDepositBlockEntity)level.getBlockEntity(pos)).remaining()==patch.stock(pos.getX(),pos.getZ()),"generated finite stock changed after save/reload");h.succeed();
    }
}
