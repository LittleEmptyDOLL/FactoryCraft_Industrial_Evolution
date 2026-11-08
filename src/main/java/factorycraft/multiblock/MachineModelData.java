package factorycraft.multiblock;

import net.neoforged.neoforge.client.model.data.ModelProperty;

/** NeoForge's distribution-neutral BE model-data key; contains visual coordinates only. */
public final class MachineModelData {
    public record Cell(MachineProfile profile,int right,int up,int back) {}
    public static final ModelProperty<Cell> CELL=new ModelProperty<>();
    private MachineModelData() {}
}
