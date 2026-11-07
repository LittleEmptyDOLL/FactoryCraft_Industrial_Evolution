package factorycraft.simulation;

import factorycraft.simulation.EnergyBuffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PowerBufferTest {
    @Test void simulationCapacityAndConsumptionConserveEnergy() {
        int[] changes={0};var power=new EnergyBuffer(100,()->changes[0]++);
        assertEquals(100,power.receiveEnergy(200,true));assertEquals(0,power.getEnergyStored());assertEquals(0,changes[0]);
        assertEquals(80,power.receiveEnergy(80,false));assertTrue(power.spend(30));assertFalse(power.spend(51));
        assertEquals(50,power.receiveEnergy(200,false));assertEquals(100,power.getEnergyStored());
        assertEquals(0,power.extractEnergy(999,false));assertFalse(power.spend(-1));
        assertEquals(3,changes[0]);
    }
    @Test void invalidRestoredValuesAreBounded() {
        var power=new EnergyBuffer(100,()->{});power.restore(-20);assertEquals(0,power.getEnergyStored());power.restore(Integer.MAX_VALUE);assertEquals(100,power.getEnergyStored());
    }
}
