package factorycraft.simulation.thermal;

import factorycraft.simulation.fluids.FluidCell;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SteamCycleTest {
    private FluidCell<String> cell(String key,int amount) { var cell=new FluidCell<String>(4000,()->{});cell.restore(key,amount);return cell; }
    @Test void conversionSimulatesWithoutMutationAndCommitsBeforeObservers() {
        @SuppressWarnings("unchecked") FluidCell<String>[] cells=new FluidCell[2];int[] observed={0};
        Runnable observer=()->{assertEquals(200,cells[0].amount()+cells[1].amount());assertEquals("steam",cells[1].key());observed[0]++;};
        cells[0]=new FluidCell<>(4000,observer);cells[1]=new FluidCell<>(4000,observer);cells[0].restore("water",200);
        assertTrue(cells[0].convertTo(cells[1],200,"steam",200,true));assertEquals(0,observed[0]);assertEquals(200,cells[0].amount());
        assertTrue(cells[0].convertTo(cells[1],200,"steam",200,false));assertEquals(2,observed[0]);assertEquals(0,cells[0].amount());
    }
    @Test void blockedOrIncompatibleConversionCannotSpendInput() {
        var input=cell("water",200);var output=cell("oil",100);
        assertFalse(input.convertTo(output,200,"steam",200,false));assertEquals(200,input.amount());
        output.restore("steam",3900);assertFalse(input.convertTo(output,200,"steam",200,false));assertFalse(input.convertTo(input,100,"steam",100,false));assertEquals(200,input.amount());
    }
    @Test void aRecipeSpendsExactlyItsTicksAndPreservesVolume() {
        var input=cell("water",200);var output=cell(null,0);var boiler=new BoilerProcess();boiler.refuel(1600);
        for(int i=0;i<19;i++)assertTrue(boiler.advance("water",20,input,200,output,"steam",200));
        assertEquals(200,input.amount());assertEquals(0,output.amount());assertEquals(19,boiler.progress());
        assertTrue(boiler.advance("water",20,input,200,output,"steam",200));assertEquals(1580,boiler.fuel());assertEquals(200,output.amount());assertEquals(0,boiler.progress());
    }
    @Test void missingFuelInputOrOutputNeverBurnsOrAdvances() {
        var input=cell("water",200);var output=cell(null,0);var boiler=new BoilerProcess();
        assertFalse(boiler.advance("water",20,input,200,output,"steam",200));boiler.refuel(8);
        output.restore("steam",4000);assertFalse(boiler.advance("water",20,input,200,output,"steam",200));
        output.restore(null,0);input.drain(1,false);assertFalse(boiler.advance("water",20,input,200,output,"steam",200));assertEquals(8,boiler.fuel());assertEquals(0,boiler.progress());
    }
    @Test void partialProgressAndFuelRestoreWithoutFreeOutput() {
        var input=cell("water",200);var output=cell(null,0);var before=new BoilerProcess();before.refuel(20);
        for(int i=0;i<7;i++)before.advance("recipe",20,input,200,output,"steam",200);
        var after=new BoilerProcess();after.restore(before.fuel(),before.progress(),before.recipe());
        for(int i=0;i<13;i++)assertTrue(after.advance("recipe",20,input,200,output,"steam",200));
        assertEquals(0,after.fuel());assertEquals(200,output.amount());assertEquals(0,input.amount());
    }
    @Test void changedRecipeDefinitionCannotReuseOldProgress() {
        var input=cell("water",200);var output=cell(null,0);var boiler=new BoilerProcess();boiler.restore(100,19,"old-definition");
        assertTrue(boiler.advance("new-definition",20,input,200,output,"steam",200));assertEquals(1,boiler.progress());assertEquals(0,output.amount());
    }
    @Test void engineProducesOnlyFromSteamAndNeverOverfills() {
        var steam=cell("steam",100);var engine=new SteamEngine<>(steam,()->{});
        assertEquals(0,engine.generate(false));assertEquals(100,steam.amount());
        assertEquals(120,engine.generate(true));assertEquals(90,steam.amount());assertEquals(120,engine.energy());
        engine.restore(15995);assertEquals(0,engine.generate(true));assertEquals(90,steam.amount());
        engine.extract(7,false);assertEquals(12,engine.generate(true));assertEquals(16000,engine.energy());assertEquals(89,steam.amount());
    }
    @Test void engineEnergyAndSteamAreBothOwnedBeforeNotification() {
        @SuppressWarnings("unchecked") SteamEngine<String>[] engines=new SteamEngine[1];
        var steam=new FluidCell<String>(4000,()->assertEquals(120,engines[0].energy()));steam.restore("steam",10);
        engines[0]=new SteamEngine<>(steam,()->{});assertEquals(120,engines[0].generate(true));assertEquals(0,steam.amount());
    }
    @Test void simulatedExtractionAndOversizedSavedEnergyPreserveBalance() {
        var steam=cell("steam",100);var engine=new SteamEngine<>(steam,()->{});engine.restore(Integer.MAX_VALUE);
        assertEquals(0,engine.generate(true));assertEquals(50,engine.extract(50,true));assertEquals(Integer.MAX_VALUE,engine.energy());
        assertEquals(Integer.MAX_VALUE,engine.extract(Integer.MAX_VALUE,false));assertEquals(0,engine.energy());assertEquals(100,steam.amount());
    }
    @Test void completeCycleMatchesItsMaterialFuelAndEnergyLedger() {
        var input=cell("water",4000);var steam=cell(null,0);var boiler=new BoilerProcess();boiler.refuel(400);var engine=new SteamEngine<>(steam,()->{});long delivered=0;
        for(int tick=0;tick<500;tick++) { boiler.advance("water",20,input,200,steam,"steam",200);engine.generate(true);delivered+=engine.extract(120,false); }
        assertEquals(0,boiler.fuel());assertEquals(0,input.amount());assertEquals(0,steam.amount());assertEquals(48000,delivered+engine.energy());
    }
    @Test void malformedRestoredStateAndRecipeAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new BoilerProcess().restore(-1,0,""));
        assertThrows(IllegalArgumentException.class,()->new SteamEngine<>(cell(null,0),()->{}).restore(-1));
    }
}
