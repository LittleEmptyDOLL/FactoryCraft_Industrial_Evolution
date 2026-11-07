package factorycraft.simulation;

import factorycraft.simulation.belts.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BeltTransportTest {
    private static <T> BeltTransport.Node<T> node(BeltTransport<T> t,int tier) { return t.create(new BeltLane<>(),tier,c->{}); }
    private static void assertEdgeSpacing(BeltTransport.Node<?> a,BeltTransport.Node<?> b) {
        BeltLaneTest.assertSpacing(a.lane());BeltLaneTest.assertSpacing(b.lane());
        if(!a.lane().carriers().isEmpty() && !b.lane().carriers().isEmpty())
            assertTrue(96+b.lane().carriers().getLast().positionUnits()-a.lane().carriers().getFirst().positionUnits()>=24,"cross-segment overlap");
    }
    @Test void saturatedTwoLaneChainsAchieveAllThreeNominalRates() {
        for(int tier=1;tier<=3;tier++) {
            var t=new BeltTransport<Integer>();int[] delivered={0};var sink=t.createSink(v->delivered[0]++);
            List<BeltTransport.Node<Integer>> a=new ArrayList<>(),b=new ArrayList<>();
            for(int i=0;i<8;i++){a.add(node(t,tier));b.add(node(t,tier));}
            for(var chain:List.of(a,b))for(int i=0;i<chain.size();i++)t.connect(chain.get(i),i+1<chain.size()?chain.get(i+1):sink,null);
            int id=0;
            for(int step=0;step<2000;step++){for(var chain:List.of(a,b))if(t.insert(chain.getFirst(),id))id++;t.step();}
            int start=delivered[0];
            // 60 seconds = 10800 microsteps. This is an exact item count, not a rounded FPS metric.
            for(int step=0;step<10800;step++) {
                for(var chain:List.of(a,b))if(t.insert(chain.getFirst(),id))id++;
                t.step();for(var chain:List.of(a,b))for(int i=0;i+1<chain.size();i++)assertEdgeSpacing(chain.get(i),chain.get(i+1));
            }
            assertEquals(15*tier*60,delivered[0]-start,"tier "+tier+" throughput");
        }
    }
    @Test void fullRingMovesWithoutNeedingAnEmptySlot() {
        var t=new BeltTransport<Integer>();List<BeltTransport.Node<Integer>> ring=new ArrayList<>();
        for(int i=0;i<12;i++)ring.add(node(t,1));
        int id=0;
        for(int i=0;i<ring.size();i++) {
            t.connect(ring.get(i),ring.get((i+1)%ring.size()),null);
            for(int j=0;j<4;j++)ring.get(i).lane().restore(id++,1-j*.25);
            t.changedExternally(ring.get(i));
        }
        for(int step=0;step<500;step++) {t.step();for(int i=0;i<ring.size();i++)assertEdgeSpacing(ring.get(i),ring.get((i+1)%ring.size()));}
        Set<Integer> ids=new HashSet<>();for(var n:ring)for(var c:n.lane().carriers())assertTrue(ids.add(c.value));
        assertEquals(48,ids.size());assertTrue(t.transfers()>100);
    }
    @Test void unequalSpeedChainsBackpressureAndConserve() {
        var t=new BeltTransport<Integer>();Set<Integer> delivered=new HashSet<>();var sink=t.createSink(v->assertTrue(delivered.add(v)));
        List<BeltTransport.Node<Integer>> chain=new ArrayList<>();for(int tier:new int[]{3,2,1,3,1,2,3})chain.add(node(t,tier));
        for(int i=0;i<chain.size();i++)t.connect(chain.get(i),i+1<chain.size()?chain.get(i+1):sink,null);
        int created=0;
        for(int step=0;step<6000;step++) {
            if(t.insert(chain.getFirst(),created))created++;
            t.step();for(int i=0;i+1<chain.size();i++)assertEdgeSpacing(chain.get(i),chain.get(i+1));
            assertEquals(created,delivered.size()+chain.stream().mapToInt(n->n.lane().carriers().size()).sum());
        }
    }
    @Test void mergeAlternatesWithoutDuplicationOrBackwardMovement() {
        var t=new BeltTransport<Integer>();Set<Integer> delivered=new HashSet<>();var sink=t.createSink(v->assertTrue(delivered.add(v)));
        var a=node(t,1);var b=node(t,1);var dest=node(t,1);t.connect(a,dest,null);t.connect(b,dest,null);t.connect(dest,sink,null);
        int created=0;Map<Integer,Integer> previous=new HashMap<>();int fromA=0,fromB=0;
        for(int step=0;step<4000;step++) {
            if(t.insert(a,created*2)){created++;fromA++;}if(t.insert(b,created*2+1)){created++;fromB++;}
            t.step();assertEdgeSpacing(a,dest);assertEdgeSpacing(b,dest);
            for(var n:List.of(a,b))for(var c:n.lane().carriers()) {
                Integer old=previous.put(c.value,c.positionUnits());if(old!=null)assertTrue(c.positionUnits()>=old,"merge moved a waiting carrier backwards");
            }
            assertEquals(created,delivered.size()+a.lane().carriers().size()+b.lane().carriers().size()+dest.lane().carriers().size());
        }
        assertTrue(Math.abs(fromA-fromB)<=1,"merge starved one input");
    }
    @Test void splitterBalancesAndUsesUnblockedOutput() {
        var t=new BeltTransport<Integer>();int[] left={0},right={0};var a=node(t,1);var b=node(t,1);var c=node(t,1);
        t.connect(a,b,c);t.connect(b,t.createSink(v->left[0]++),null);t.connect(c,t.createSink(v->right[0]++),null);
        int created=0;
        for(int step=0;step<2500;step++){if(t.insert(a,created))created++;t.step();}
        assertTrue(Math.abs(left[0]-right[0])<=1);
        int before=right[0];t.connect(b,null,null);
        for(int step=0;step<1500;step++){if(t.insert(a,created))created++;t.step();}
        assertTrue(right[0]-before>30,"splitter failed to use free output");
        assertEquals(created,left[0]+right[0]+a.lane().carriers().size()+b.lane().carriers().size()+c.lane().carriers().size());
    }
    @Test void sleepingChainWakesWhenDownstreamIsExtracted() {
        var t=new BeltTransport<Integer>();var a=node(t,1);var b=node(t,1);t.connect(a,b,null);
        for(var n:List.of(a,b)){for(int i=0;i<4;i++)n.lane().restore(i,1-i*.25);t.changedExternally(n);}
        t.tick();assertFalse(a.isActive());assertFalse(b.isActive());
        b.lane().remove(0);t.changedExternally(b);for(int i=0;i<20;i++)t.tick();
        assertEquals(3,a.lane().carriers().size());assertEquals(4,b.lane().carriers().size());assertEdgeSpacing(a,b);
    }
    @Test void disconnectPausesAndReconnectResumesWithoutLoss() {
        var t=new BeltTransport<Integer>();var a=node(t,2);var b=node(t,2);t.connect(a,b,null);assertTrue(t.insert(a,12));
        t.tick();t.remove(b);for(int i=0;i<50;i++)t.tick();assertEquals(1,a.lane().carriers().size());
        var replacement=node(t,2);t.connect(a,replacement,null);for(int i=0;i<10;i++)t.tick();
        assertTrue(a.lane().carriers().isEmpty());assertEquals(12,replacement.lane().carriers().getFirst().value);
    }
    @Test void externalInjectionCannotOverlapAnIncomingCarrier() {
        var t=new BeltTransport<Integer>();var a=node(t,1);var b=node(t,1);t.connect(a,b,null);
        a.lane().restore(1,.9);assertFalse(t.canInsert(b));assertFalse(t.insert(b,2));assertTrue(b.lane().carriers().isEmpty());
    }
    @Test void changingActivationOrderDoesNotChangeAChainResult() {
        assertEquals(runChain(false),runChain(true));
    }
    private static List<Integer> runChain(boolean reverse) {
        var t=new BeltTransport<Integer>();var a=node(t,3);var b=node(t,2);var c=node(t,1);
        t.connect(a,b,null);t.connect(b,c,null);
        a.lane().restore(1,.9375);b.lane().restore(2,.625);c.lane().restore(3,.875);
        for(var n:reverse?List.of(c,b,a):List.of(a,b,c))t.changedExternally(n);
        for(int i=0;i<100;i++)t.step();
        List<Integer> result=new ArrayList<>();for(var n:List.of(a,b,c))for(var v:n.lane().carriers()){result.add(v.value);result.add(v.positionUnits());}return result;
    }
    @Test void tenThousandCompleteTwoLaneSegmentsPreserveAllCarriers() {
        var t=new BeltTransport<Integer>();List<BeltTransport.Node<Integer>> nodes=new ArrayList<>();
        for(int i=0;i<20000;i++)nodes.add(node(t,1));
        for(int lane=0;lane<2;lane++)for(int segment=0;segment<10000;segment++) {
            int i=lane*10000+segment;t.connect(nodes.get(i),nodes.get(lane*10000+(segment+1)%10000),null);
            nodes.get(i).lane().restore(i,1);t.changedExternally(nodes.get(i));
        }
        long start=System.nanoTime();for(int i=0;i<100;i++)t.tick();
        Set<Integer> all=new HashSet<>();for(var n:nodes)for(var c:n.lane().carriers())assertTrue(all.add(c.value));
        assertEquals(20000,all.size());System.out.printf(Locale.ROOT,"Transport kernel: 10000 two-lane segments, 100 ticks, %.2f ms%n",(System.nanoTime()-start)/1e6);
    }
    @Test void randomizedGraphsConserveAcrossCongestionAndTopologyChanges() {
        for(int seed=0;seed<12;seed++) {
            var random=new Random(seed);var t=new BeltTransport<Integer>();Set<Integer> removed=new HashSet<>();
            List<BeltTransport.Node<Integer>> nodes=new ArrayList<>();for(int i=0;i<18;i++)nodes.add(node(t,1+random.nextInt(3)));
            int[] destinations=new int[nodes.size()];
            for(int i=0;i<nodes.size();i++){destinations[i]=(i+1)%nodes.size();t.connect(nodes.get(i),nodes.get(destinations[i]),null);}
            int created=0;
            for(int step=0;step<1500;step++) {
                if(random.nextBoolean() && t.insert(nodes.get(random.nextInt(nodes.size())),created))created++;
                var extracted=nodes.get(random.nextInt(nodes.size()));
                if(random.nextInt(8)==0 && !extracted.lane().carriers().isEmpty()) {
                    assertTrue(removed.add(extracted.lane().remove(0)));t.changedExternally(extracted);
                }
                if(step%37==0) {
                    int from=random.nextInt(nodes.size()),to=random.nextInt(nodes.size());
                    if(from!=to){destinations[from]=to;t.connect(nodes.get(from),nodes.get(to),null);}
                }
                t.step();Set<Integer> all=new HashSet<>(removed);
                for(var n:nodes)for(var c:n.lane().carriers())assertTrue(all.add(c.value),"duplicate in graph seed "+seed);
                assertEquals(created,all.size());
                for(int i=0;i<nodes.size();i++)assertEdgeSpacing(nodes.get(i),nodes.get(destinations[i]));
            }
        }
    }

    @Test void legacyOverlappingJunctionSettlesWithoutDroppingCarriers() {
        var t=new BeltTransport<Integer>();var a=node(t,1);var b=node(t,1);t.connect(a,b,null);
        for(int i=0;i<4;i++){a.lane().restore(i,1-i*.25);b.lane().restore(i+4,.75-i*.25);}
        t.changedExternally(a);t.changedExternally(b);
        for(int i=0;i<60;i++){t.step();assertEdgeSpacing(a,b);}
        assertEquals(8,a.lane().carriers().size()+b.lane().carriers().size());
    }

    @Test void extractingTheLastWaitingCarrierCannotRepeatAnEarlierTransfer() {
        var t=new BeltTransport<Integer>();var a=node(t,2);var b=node(t,2);t.connect(a,b,null);
        a.lane().restore(10,95.0/96);a.lane().restore(11,71.0/96);t.changedExternally(a);t.step();
        assertEquals(10,b.lane().carriers().getFirst().value);
        assertEquals(11,a.lane().remove(0));t.changedExternally(a);
        assertDoesNotThrow(t::step);
        assertTrue(a.lane().carriers().isEmpty());assertEquals(1,b.lane().carriers().size());
        assertEquals(10,b.lane().carriers().getFirst().value);
    }

}
