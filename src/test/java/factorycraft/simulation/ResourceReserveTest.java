package factorycraft.simulation;
import factorycraft.simulation.resources.ResourceReserve;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ResourceReserveTest {
    @Test void blockedDestinationDoesNotConsumeFiniteStock() { var r=new ResourceReserve();r.restore(3);assertFalse(r.extractOne(false));assertEquals(3,r.remaining());for(int i=0;i<3;i++)assertTrue(r.extractOne(true));assertFalse(r.extractOne(true));assertEquals(0,r.remaining()); }
    @Test void exhaustedStockStaysExhaustedAcrossRestore() { var r=new ResourceReserve();r.restore(0);assertFalse(r.extractOne(true));assertThrows(IllegalArgumentException.class,()->r.restore(-1)); }
}
