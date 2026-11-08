package factorycraft.thermal;

public enum ThermalKind {
    BOILER("boiler"),ENGINE("steam_engine");
    public final String id;
    ThermalKind(String id) { this.id=id; }
}
