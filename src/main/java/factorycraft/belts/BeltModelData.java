package factorycraft.belts;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Immutable local physical metadata; chunk mesh never reads a controller in another chunk. */
public final class BeltModelData {
  public record Cell(BeltRoute route, Direction facing, int tier, int offsetY, boolean output) {}

  public static final ModelProperty<Cell> CELL = new ModelProperty<>();

  private BeltModelData() {}
}
