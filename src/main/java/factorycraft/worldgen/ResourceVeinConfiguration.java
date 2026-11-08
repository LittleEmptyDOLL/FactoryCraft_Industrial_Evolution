package factorycraft.worldgen;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import factorycraft.blocks.ResourceDepositBlock;
import factorycraft.simulation.resources.ResourceVeins;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

public record ResourceVeinConfiguration(BlockState state,long salt,boolean useWorldSettings,ResourceVeins.Settings settings) implements FeatureConfiguration {
    private record Bounds(int minRadius,int maxRadius,int minStock,int maxStock,int spacing) {}
    private static final Codec<Bounds> BOUNDS=RecordCodecBuilder.create(i->i.group(
            Codec.intRange(4,48).fieldOf("min_radius").forGetter(Bounds::minRadius),Codec.intRange(4,48).fieldOf("max_radius").forGetter(Bounds::maxRadius),
            Codec.intRange(1,10000000).fieldOf("min_stock").forGetter(Bounds::minStock),Codec.intRange(1,10000000).fieldOf("max_stock").forGetter(Bounds::maxStock),
            Codec.intRange(2,32).fieldOf("spacing_chunks").forGetter(Bounds::spacing)).apply(i,Bounds::new));
    public static final Codec<ResourceVeins.Settings> SETTINGS=BOUNDS.flatXmap(b->{
        try { return DataResult.success(new ResourceVeins.Settings(b.minRadius,b.maxRadius,b.minStock,b.maxStock,b.spacing)); }
        catch(IllegalArgumentException e) { return DataResult.error(e::getMessage); }
    },s->DataResult.success(new Bounds(s.minRadius(),s.maxRadius(),s.minStock(),s.maxStock(),s.spacingChunks())));
    public static final Codec<ResourceVeinConfiguration> CODEC=RecordCodecBuilder.<ResourceVeinConfiguration>create(i->i.group(
            BlockState.CODEC.fieldOf("state").forGetter(ResourceVeinConfiguration::state),Codec.LONG.fieldOf("salt").forGetter(ResourceVeinConfiguration::salt),
            Codec.BOOL.optionalFieldOf("use_world_settings",true).forGetter(ResourceVeinConfiguration::useWorldSettings),
            SETTINGS.fieldOf("vein").forGetter(ResourceVeinConfiguration::settings)).apply(i,ResourceVeinConfiguration::new))
            .validate(c->c.state.getBlock() instanceof ResourceDepositBlock ? DataResult.success(c):DataResult.error(()->"Resource vein state must own a finite resource deposit"));
}
