package factorycraft.simulation.fluids;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FluidTransportTest {
    private FluidCell<String> cell(int capacity,int amount) { var c=new FluidCell<String>(capacity,()->{});c.restore(amount==0 ? null:"water",amount);return c; }
    @Test void fluidMovesButNewlyReceivedVolumeCannotCrossTwoEdgesInOneTick() {
        var flow=new FluidTransport<String>();var a=cell(1000,1000);var b=cell(1000,0);var c=cell(1000,0);
        var na=flow.add(a);var nb=flow.add(b);var nc=flow.add(c);flow.connect(na,nb);flow.connect(nb,nc);
        flow.tick(10,100);assertEquals(900,a.amount());assertEquals(100,b.amount());assertEquals(0,c.amount());
        flow.tick(10,100);assertEquals(800,a.amount());assertEquals(175,b.amount());assertEquals(25,c.amount());
    }
    @Test void branchedReservationsNeverOverdrawASharedSourceOrDestination() {
        var flow=new FluidTransport<String>();var cells=new ArrayList<FluidCell<String>>();var source=cell(1000,1000);cells.add(source);var center=flow.add(source);
        for(int i=0;i<6;i++) { var c=cell(1000,0);cells.add(c);flow.connect(center,flow.add(c)); }
        for(int tick=0;tick<100;tick++) { flow.tick(100,1000);assertEquals(1000,cells.stream().mapToInt(FluidCell::amount).sum());cells.forEach(c->assertTrue(c.amount()>=0 && c.amount()<=1000)); }
        int min=cells.stream().mapToInt(FluidCell::amount).min().orElseThrow(),max=cells.stream().mapToInt(FluidCell::amount).max().orElseThrow();
        assertTrue(max-min<=12,"Junction did not settle within integer pressure tolerance: "+cells.stream().map(FluidCell::amount).toList());
    }
    @Test void simultaneousDifferentFluidsCannotMixInAnEmptyDestination() {
        var flow=new FluidTransport<String>();var water=cell(1000,500);var lava=cell(1000,0);lava.restore("lava",500);var middle=cell(1000,0);
        var w=flow.add(water);var l=flow.add(lava);var m=flow.add(middle);flow.connect(w,m);flow.connect(l,m);
        flow.tick(10,1000);assertEquals("water",middle.key());assertEquals(500,lava.amount());assertEquals(500,water.amount()+middle.amount());
        middle.drain(1000,false);water.drain(1000,false);flow.tick(10,1000);assertEquals("lava",middle.key());
    }
    @Test void simultaneousFlowsDoNotOvershootAnEmptySixWayJunction() {
        var flow=new FluidTransport<String>();var center=cell(1000,0);var node=flow.add(center);var ends=new ArrayList<FluidCell<String>>();
        for(int i=0;i<6;i++) { var c=cell(1000,1000);ends.add(c);flow.connect(node,flow.add(c)); }
        flow.tick(100,1000);assertTrue(center.amount()<=ends.stream().mapToInt(FluidCell::amount).min().orElseThrow());
        assertEquals(6000,center.amount()+ends.stream().mapToInt(FluidCell::amount).sum());
    }
    @Test void balancedEdgesSleepAndExternalVolumeChangesWakeThem() {
        var flow=new FluidTransport<String>();var a=cell(1000,500);var b=cell(16000,8000);flow.connect(flow.add(a),flow.add(b));
        flow.tick(10,100);assertEquals(0,flow.pendingEdges());long visits=flow.visits();
        for(int i=0;i<100;i++)flow.tick(10,100);assertEquals(visits,flow.visits());
        a.fill("water",100,true);assertEquals(0,flow.pendingEdges());a.fill("water",100,false);assertEquals(1,flow.pendingEdges());
        flow.tick(10,100);assertTrue(b.amount()>8000);assertEquals(8600,a.amount()+b.amount());
    }
    @Test void fifoBudgetEventuallyProcessesEveryBranchWithoutSkippingConservation() {
        var flow=new FluidTransport<String>();var center=cell(1000,1000);var root=flow.add(center);var ends=new ArrayList<FluidCell<String>>();
        for(int i=0;i<6;i++) { var c=cell(1000,0);ends.add(c);flow.connect(root,flow.add(c)); }
        for(int i=0;i<200;i++)assertTrue(flow.tick(1,100)<=1);
        ends.forEach(c->assertTrue(c.amount()>0));assertEquals(1000,center.amount()+ends.stream().mapToInt(FluidCell::amount).sum());
    }
    @Test void removingAndReconnectingNodesPreservesStoredFluidAndBlocksStaleEdges() {
        var flow=new FluidTransport<String>();var a=cell(1000,1000);var b=cell(1000,0);var n=flow.add(a);var m=flow.add(b);flow.connect(n,m);flow.remove(m);
        flow.tick(100,100);assertEquals(1000,a.amount());assertEquals(0,b.amount());assertEquals(0,flow.edges());
        m=flow.add(b);flow.connect(n,m);flow.tick(100,100);assertEquals(100,b.amount());assertEquals(1000,a.amount()+b.amount());
        assertThrows(IllegalStateException.class,()->flow.add(a));
    }
    @Test void largeIntegerPressureUsesLongArithmetic() {
        var flow=new FluidTransport<String>();var a=cell(Integer.MAX_VALUE,Integer.MAX_VALUE);var b=cell(Integer.MAX_VALUE,0);
        flow.connect(flow.add(a),flow.add(b));flow.tick(1,Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE,(long)a.amount()+b.amount());assertTrue(b.amount()>1_000_000_000);
    }
    @Test void randomizedTopologyAndTransfersConserveTenThousandCells() {
        var random=new Random(75433);var flow=new FluidTransport<String>();var cells=new ArrayList<FluidCell<String>>();var nodes=new ArrayList<FluidTransport.Node<String>>();long total=0;
        for(int i=0;i<10000;i++) { var c=cell(1000,random.nextInt(1001));total+=c.amount();cells.add(c);nodes.add(flow.add(c));if(i>0)flow.connect(nodes.get(i-1),nodes.get(i)); }
        for(int tick=0;tick<200;tick++) {
            int index=random.nextInt(9999);flow.disconnect(nodes.get(index),nodes.get(index+1));flow.tick(512,100);flow.connect(nodes.get(index),nodes.get(index+1));
            assertEquals(total,cells.stream().mapToLong(FluidCell::amount).sum());
        }
    }
    @Test void failedNotificationDoesNotReplayAnAlreadyCommittedBatch() {
        boolean[] first={true};var a=new FluidCell<String>(1000,()->{if(first[0]) { first[0]=false;throw new IllegalStateException("callback"); }});a.restore("water",1000);
        var b=cell(1000,0);var flow=new FluidTransport<String>();flow.connect(flow.add(a),flow.add(b));
        assertThrows(IllegalStateException.class,()->flow.tick(10,100));assertEquals(100,b.amount());flow.tick(10,100);assertEquals(200,b.amount());assertEquals(1000,a.amount()+b.amount());
    }
    @Test void topologyComponentsMergeAndSplitWithoutChangingStoredResources() {
        var flow=new FluidTransport<String>();var a=flow.add(cell(1000,1000));var b=flow.add(cell(1000,0));var c=flow.add(cell(1000,0));var d=flow.add(cell(1000,0));
        flow.connect(a,b);flow.connect(c,d);assertEquals(2,flow.components());flow.connect(b,c);assertEquals(1,flow.components());
        flow.disconnect(b,c);assertEquals(2,flow.components());flow.remove(b);assertEquals(2,flow.components());assertEquals(3,flow.nodes());
    }
    @Test void aTemporarilyUnavailableEndpointCannotReceiveOrLoseFluid() {
        var flow=new FluidTransport<String>();boolean[] ready={true};var a=cell(1000,1000);var b=cell(1000,0);
        flow.connect(flow.add(a),flow.add(b,()->ready[0]));flow.tick(10,100);assertEquals(100,b.amount());ready[0]=false;
        for(int i=0;i<20;i++)flow.tick(10,100);assertEquals(100,b.amount());assertEquals(1000,a.amount()+b.amount());
        ready[0]=true;flow.tick(10,100);assertEquals(200,b.amount());
    }
}
