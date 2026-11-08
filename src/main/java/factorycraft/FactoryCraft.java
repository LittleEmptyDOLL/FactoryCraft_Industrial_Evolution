package factorycraft;

import factorycraft.config.FactoryConfig;
import factorycraft.registry.Content;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(FactoryCraft.ID)
public final class FactoryCraft {
    public static final String ID = "factorycraft";

    public FactoryCraft(IEventBus bus, ModContainer container) {
        Content.register(bus);
        container.registerConfig(ModConfig.Type.COMMON,factorycraft.config.ResourceWorldgenConfig.SPEC,"factorycraft-worldgen.toml");
        container.registerConfig(ModConfig.Type.SERVER, FactoryConfig.SPEC);
    }
}
