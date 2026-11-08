package factorycraft.blocks;

public enum FactoryKind {
    BELT("transport_belt", 1), FAST_BELT("fast_transport_belt", 2), EXPRESS_BELT("express_transport_belt", 3),
    SPLITTER("splitter", 1), INSERTER("inserter", 0), DRILL("electric_drill", 0),
    FURNACE("electric_furnace", 0), ASSEMBLER("assembling_machine", 0), GENERATOR("coal_generator", 0),
    ADVANCED_ASSEMBLER("assembling_machine_2",0), LAB("lab",0), BEACON("beacon",0), POLE("electric_pole", 0), INDUSTRIAL_FURNACE("industrial_furnace",0), CASING("machine_casing",0), PUMPJACK("pumpjack",0), REFINERY("oil_refinery",0), CHEMICAL_PLANT("chemical_plant",0), REFINERY_CASING("refinery_casing",0);
    public final String id;
    public final int beltTier;
    FactoryKind(String id, int beltTier) { this.id = id; this.beltTier = beltTier; }
    public boolean petroleum() { return this==ADVANCED_ASSEMBLER || this==PUMPJACK || this==REFINERY || this==CHEMICAL_PLANT; }
    public boolean large() { return factorycraft.multiblock.MachineProfile.byId(id)!=null; }
    public boolean casing() { return this==CASING || this==REFINERY_CASING; }
    public boolean belt() { return beltTier > 0; }
    public boolean processing() { return this==FURNACE || this==ASSEMBLER || this==ADVANCED_ASSEMBLER || this==INDUSTRIAL_FURNACE || this==REFINERY || this==CHEMICAL_PLANT; }
    public String recipeGroup() { return this==INDUSTRIAL_FURNACE ? FURNACE.id:id; }
    public boolean machine() { return this == DRILL || this == FURNACE || this == ASSEMBLER || this == GENERATOR || this == INDUSTRIAL_FURNACE || petroleum(); }
}
