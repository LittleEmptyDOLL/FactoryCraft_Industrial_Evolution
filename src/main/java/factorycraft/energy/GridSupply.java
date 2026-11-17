package factorycraft.energy;

/** Exact 25-J dispatch source. Stored charge is never copied into topology or a cable. */
public interface GridSupply extends GridGenerator {
  /** 0 solar, 1 heat engines, 2 tertiary storage. */
  int supplyPriority();

  boolean supplyReady();

  long supplyAvailable();

  long takeSupply(long requested);

  void refundSupply(long rejected);

  default long chargeSupply(long offered) {
    return 0;
  }

  default boolean storage() {
    return supplyPriority() == 2;
  }

  default void endSupplyTick() {}
}
