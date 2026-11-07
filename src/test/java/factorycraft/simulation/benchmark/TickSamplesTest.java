package factorycraft.simulation.benchmark;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TickSamplesTest {
    @Test void percentilesUseNearestRankAndIncludeSlowTicks() {
        var samples=new TickSamples(100);for(int i=100;i>=1;i--)samples.add(i*1_000_000L);
        var summary=samples.summarize();assertEquals(50.5,summary.meanMs());assertEquals(50,summary.p50Ms());
        assertEquals(95,summary.p95Ms());assertEquals(99,summary.p99Ms());assertEquals(100,summary.maxMs());assertEquals(50,summary.over50Ms());
    }
    @Test void partialWindowDoesNotCountUnusedZerosAndSummarizingDoesNotMutateIt() {
        var samples=new TickSamples(100);samples.add(2_000_000);samples.add(6_000_000);
        assertEquals(4,samples.summarize().meanMs());assertEquals(2,samples.summarize().count());samples.add(1_000_000);
        assertEquals(2,samples.summarize().p50Ms());assertEquals(6,samples.summarize().p99Ms());
    }
    @Test void invalidSamplesAndEmptyWindowsCannotProduceMisleadingReports() {
        assertThrows(IllegalArgumentException.class,()->new TickSamples(0));var samples=new TickSamples(1);
        assertThrows(IllegalStateException.class,samples::summarize);assertThrows(IllegalArgumentException.class,()->samples.add(-1));
        samples.add(0);assertThrows(IllegalArgumentException.class,()->samples.add(1));assertEquals(0,samples.summarize().meanMs());
    }
}
