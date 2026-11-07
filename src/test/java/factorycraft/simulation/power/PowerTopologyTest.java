package factorycraft.simulation.power;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PowerTopologyTest {
    private static final class Store implements PowerTopology.Receiver {
        int energy,calls;
        final int capacity;
        Store(int capacity) { this.capacity=capacity; }
        public int receive(int amount,boolean simulate) { calls++;int accepted=Math.min(amount,capacity-energy);if(!simulate)energy+=accepted;return accepted; }
    }
    private static PowerTopology.Node<Object> pole(PowerTopology<Object> g,int x,int y,int z) { return g.add(new Object(),new PowerPoint(x,y,z),PowerTopology.Kind.POLE,null); }
    private static PowerTopology.Node<Object> source(PowerTopology<Object> g,int x,int y,int z) { return g.add(new Object(),new PowerPoint(x,y,z),PowerTopology.Kind.SOURCE,null); }
    private static PowerTopology.Node<Object> consumer(PowerTopology<Object> g,int x,int y,int z,Store s) { return g.add(new Object(),new PowerPoint(x,y,z),PowerTopology.Kind.CONSUMER,s); }
    private static void finish(PowerTopology<?> g) { int steps=0;while(g.pending()) { assertTrue(g.advance(17)<=17);assertTrue(++steps<1000000,"rebuild did not terminate"); } }
    @Test void spatialCoverageHandlesNegativeCoordinatesAndSphericalBoundaries() {
        var g=new PowerTopology<Object>(6);pole(g,-6,-6,-6);var a=source(g,-12,-6,-6);var b=consumer(g,-6,-6,0,new Store(100));var outside=source(g,0,0,0);
        finish(g);assertTrue(g.connected(a,b));assertFalse(g.connected(outside,b));assertFalse(g.pending());
    }
    @Test void bridgeRemovalImmediatelyQuarantinesOnlyTheAffectedComponent() {
        var g=new PowerTopology<Object>(6);pole(g,0,0,0);var bridge=pole(g,6,0,0);pole(g,12,0,0);
        var a=source(g,0,1,0);var b=consumer(g,12,1,0,new Store(100));pole(g,100,0,0);var otherA=source(g,100,1,0);var otherB=consumer(g,100,2,0,new Store(100));
        finish(g);assertTrue(g.connected(a,b));g.remove(bridge.value());
        assertFalse(g.connected(a,b));assertTrue(g.connected(otherA,otherB));g.advance(1);assertFalse(g.connected(a,b));
        finish(g);assertFalse(g.connected(a,b));pole(g,6,0,0);finish(g);assertTrue(g.connected(a,b));
    }
    @Test void additionsDoNotInterruptExistingNetworksWhileRebuilding() {
        var g=new PowerTopology<Object>(6);pole(g,0,0,0);var a=source(g,0,1,0);var b=consumer(g,0,2,0,new Store(100));finish(g);
        pole(g,200,0,0);g.advance(1);assertTrue(g.pending());assertTrue(g.connected(a,b));finish(g);assertTrue(g.connected(a,b));
    }
    @Test void removingARecipientAfterPreviewCannotSendEnergyIntoAnUnloadedNode() {
        var g=new PowerTopology<Object>(6);pole(g,0,0,0);var a=source(g,0,1,0);var store=new Store(100);var b=consumer(g,0,2,0,store);finish(g);
        assertTrue(g.prepareDemand(a,32));g.remove(b.value());assertEquals(0,g.distributePrepared(a,80));assertEquals(0,store.energy);
    }
    @Test void poleRemovalAfterPreviewCannotUseAStaleRoute() {
        var g=new PowerTopology<Object>(6);var p=pole(g,0,0,0);var a=source(g,0,1,0);var store=new Store(100);consumer(g,0,2,0,store);finish(g);
        assertTrue(g.prepareDemand(a,32));g.remove(p.value());assertEquals(0,g.distributePrepared(a,80));assertEquals(0,store.energy);
    }
    @Test void generationIsConservedAndFullRecipientsStopDemand() {
        var g=new PowerTopology<Object>(6);pole(g,0,0,0);var a=source(g,0,1,0);var x=new Store(31);var y=new Store(41);consumer(g,0,2,0,x);consumer(g,0,3,0,y);finish(g);
        int supplied=0;for(int i=0;i<5;i++)if(g.prepareDemand(a,32))supplied+=g.distributePrepared(a,80);
        assertEquals(72,supplied);assertEquals(supplied,x.energy+y.energy);assertFalse(g.prepareDemand(a,32));
    }
    @Test void deviceOverlapDoesNotBridgeDisconnectedPoleNetworksOrDoubleSupply() {
        var g=new PowerTopology<Object>(6);pole(g,-5,0,0);pole(g,5,0,0);var a=source(g,0,0,0);var b=source(g,-10,0,0);var c=source(g,10,0,0);
        var store=new Store(1000);consumer(g,0,1,0,store);finish(g);assertFalse(g.connected(b,c));
        assertTrue(g.prepareDemand(a,32));assertEquals(80,g.distributePrepared(a,80));assertEquals(80,store.energy);
    }
    @Test void recipientScanningHasABudgetAndEventuallyReachesDistantDemand() {
        var g=new PowerTopology<Object>(6);var a=source(g,0,2,0);List<Store> stores=new ArrayList<>();
        for(int i=0;i<100;i++){pole(g,i*6,0,0);var s=new Store(100);s.energy=100;stores.add(s);consumer(g,i*6,1,0,s);}stores.get(80).energy=0;finish(g);
        for(var s:stores)s.calls=0;int received=0;
        for(int tick=0;tick<10;tick++){int before=stores.stream().mapToInt(s->s.calls).sum();if(g.prepareDemand(a,7))received+=g.distributePrepared(a,10);int after=stores.stream().mapToInt(s->s.calls).sum();assertTrue(after-before<=14);}
        // Scan the rest of the round-robin cycle; component insertion order is intentionally unspecified.
        for(int tick=0;tick<30;tick++)if(g.prepareDemand(a,7))received+=g.distributePrepared(a,10);
        assertTrue(received>0);assertTrue(received<=100);
    }
    @Test void configurationChangesDisableOldCoverageUntilAnAtomicSnapshotIsReady() {
        var g=new PowerTopology<Object>(6);pole(g,0,0,0);pole(g,6,0,0);var a=source(g,0,1,0);var b=consumer(g,6,1,0,new Store(100));finish(g);assertTrue(g.connected(a,b));
        g.setRadius(2);assertFalse(g.connected(a,b));finish(g);assertFalse(g.connected(a,b));g.setRadius(6);finish(g);assertTrue(g.connected(a,b));
    }
    @Test void topologyChangesDuringAnyRebuildPhaseNeverReenableARemovedPole() {
        for(int cut=0;cut<200;cut++) {
            var g=new PowerTopology<Object>(6);pole(g,0,0,0);var p=pole(g,6,0,0);var a=source(g,0,1,0);var b=consumer(g,6,1,0,new Store(100));
            g.advance(cut);g.remove(p.value());assertFalse(g.connected(a,b));
            while(g.pending()){g.advance(3);assertFalse(g.connected(a,b),"obsolete snapshot reenabled a removed pole at phase cut "+cut);}
            assertEquals(3,g.size());
        }
    }
    @Test void spatialRebuildMatchesBruteForceConnectivity() {
        for(int seed=0;seed<20;seed++) {
            var random=new Random(seed);var g=new PowerTopology<Object>(6);List<PowerTopology.Node<Object>> sources=new ArrayList<>();List<PowerPoint> points=new ArrayList<>();
            for(int i=0;i<60;i++){var p=new PowerPoint(i%10*4-20,i/10*4-12,random.nextInt(3)*4-4);points.add(p);pole(g,p.x(),p.y(),p.z());sources.add(source(g,p.x(),p.y()+1,p.z()));}
            int[] roots=new int[60];for(int i=0;i<60;i++)roots[i]=i;
            for(int i=0;i<60;i++)for(int j=0;j<i;j++)if(points.get(i).within(points.get(j),6)){int a=root(roots,i),b=root(roots,j);roots[a]=b;}
            finish(g);for(int i=0;i<60;i++)for(int j=0;j<60;j++)assertEquals(root(roots,i)==root(roots,j),g.connected(sources.get(i),sources.get(j)));
        }
    }
    private static int root(int[] roots,int i) { while(roots[i]!=i)i=roots[i];return i; }
    @Test void tenThousandPolesUseLocalCandidatesAndNeverExceedTheRebuildBudget() {
        var g=new PowerTopology<Object>(6);for(int i=0;i<10000;i++)pole(g,i*6,0,0);var a=source(g,0,1,0);var b=consumer(g,59994,1,0,new Store(100));
        long start=System.nanoTime();int ticks=0;while(g.pending()){assertTrue(g.advance(4096)<=4096);assertTrue(++ticks<1000);}
        assertTrue(g.connected(a,b));assertTrue(g.candidateChecks()<100000,"spatial rebuild regressed to all pairs");
        long checks=g.candidateChecks();for(int i=0;i<100;i++)assertEquals(0,g.advance(4096));assertEquals(checks,g.candidateChecks());
        System.out.printf(Locale.ROOT,"Power kernel: 10000 poles, %d budgeted steps, %d candidates, %.2f ms%n",ticks,checks,(System.nanoTime()-start)/1e6);
    }
    @Test void continuousAdditionsCannotStarveSnapshotPublication() {
        var g=new PowerTopology<Object>(6);for(int i=0;i<100;i++)pole(g,i*6,0,0);
        var a=source(g,0,1,0);var b=consumer(g,594,1,0,new Store(100));
        for(int tick=0;tick<250;tick++){pole(g,tick*6,0,1000);g.advance(32);}
        assertTrue(g.rebuilds()>0,"continuous chunk loading starved all new connections");
        assertTrue(g.connected(a,b));assertTrue(g.pending());finish(g);assertEquals(352,g.size());
    }
    @Test void radiusChangeCancelsAWorkInProgressSnapshot() {
        var g=new PowerTopology<Object>(6);pole(g,0,0,0);pole(g,6,0,0);var a=source(g,0,1,0);var b=consumer(g,6,1,0,new Store(100));
        g.advance(4);g.setRadius(2);finish(g);assertFalse(g.connected(a,b));assertEquals(1,g.restarts());
    }

    @Test void aPreparedBatchCannotCrossAnAtomicSnapshotPublication() {
        var g=new PowerTopology<Object>(6);pole(g,0,0,0);var a=source(g,0,1,0);var store=new Store(100);consumer(g,0,2,0,store);finish(g);
        assertTrue(g.prepareDemand(a,32));pole(g,100,0,0);finish(g);
        assertEquals(0,g.distributePrepared(a,80));assertEquals(0,store.energy);
        assertTrue(g.prepareDemand(a,32));assertEquals(80,g.distributePrepared(a,80));
    }

}
