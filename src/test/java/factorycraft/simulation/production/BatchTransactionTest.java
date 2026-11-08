package factorycraft.simulation.production;

import factorycraft.simulation.fluids.FluidCell;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BatchTransactionTest {
    private List<BatchInventory.Stack<String>> slots() { var slots=new ArrayList<BatchInventory.Stack<String>>();for(int i=0;i<8;i++)slots.add(new BatchInventory.Stack<>(null,0,64));return slots; }
    private BatchInventory.Ingredient<String> coal(int n) { return new BatchInventory.Ingredient<>("coal"::equals,n); }
    @Test void overlappingPredicatesNeverReserveOneItemTwice() {
        var slots=slots();slots.set(0,new BatchInventory.Stack<>("coal",3,64));
        assertNull(BatchInventory.plan(slots,List.of(coal(2),new BatchInventory.Ingredient<>(k->true,2)),List.of()));assertEquals(3,slots.getFirst().count());
    }
    @Test void outputsReserveSpaceTogetherRatherThanOverwritingPreviousResults() {
        var slots=slots();for(int i=4;i<8;i++)slots.set(i,new BatchInventory.Stack<>("plastic",64,64));slots.set(4,new BatchInventory.Stack<>("plastic",63,64));
        assertNull(BatchInventory.plan(slots,List.of(),List.of(new BatchInventory.Stack<>("plastic",1,64),new BatchInventory.Stack<>("plastic",1,64))));assertEquals(63,slots.get(4).count());
    }
    @Test void successfulPlanIsDetachedAndRespectsNonstandardStackLimits() {
        var slots=slots();slots.set(0,new BatchInventory.Stack<>("coal",4,64));
        var plan=BatchInventory.plan(slots,List.of(coal(1)),List.of(new BatchInventory.Stack<>("canister",20,16)));
        assertNotNull(plan);assertEquals(3,plan.get(0).count());assertEquals(16,plan.get(4).count());assertEquals(4,plan.get(5).count());assertEquals(0,slots.get(4).count());assertEquals(4,slots.get(0).count());
    }
    @Test void itemComponentIdentitiesCannotMergeIntoAnOccupiedSlot() {
        var slots=slots();for(int i=4;i<8;i++)slots.set(i,new BatchInventory.Stack<>("plastic:quality1",1,64));
        assertNull(BatchInventory.plan(slots,List.of(),List.of(new BatchInventory.Stack<>("plastic:quality2",1,64))));
    }
    @Test void allFluidsItemsAndProgressAreOwnedBeforeObservers() {
        @SuppressWarnings("unchecked") FluidCell<String>[] cells=new FluidCell[3];int[] items={1,0},progress={19},events={0};
        Runnable observer=()->{assertEquals(0,cells[0].amount());assertEquals(0,cells[1].amount());assertEquals(45,cells[2].amount());assertArrayEquals(new int[]{0,2},items);assertEquals(0,progress[0]);events[0]++;};
        for(int i=0;i<3;i++)cells[i]=new FluidCell<>(4000,observer);cells[0].restore("crude",100);cells[1].restore("water",30);
        var changes=List.of(new FluidCell.Mutation<>(cells[0],"crude",0),new FluidCell.Mutation<>(cells[1],"water",0),new FluidCell.Mutation<>(cells[2],"gas",45));
        assertTrue(FluidCell.commitBatch(changes,()->{items[0]=0;items[1]=2;progress[0]=0;},true));assertEquals(0,events[0]);assertEquals(100,cells[0].amount());
        assertTrue(FluidCell.commitBatch(changes,()->{items[0]=0;items[1]=2;progress[0]=0;},false));assertEquals(3,events[0]);
    }
    @Test void duplicateOrOversizedReservationsCannotPartiallyApply() {
        var c=new FluidCell<String>(100,()->{});c.restore("oil",80);int[] calls={0};
        assertFalse(FluidCell.commitBatch(List.of(new FluidCell.Mutation<>(c,"oil",40),new FluidCell.Mutation<>(c,"gas",20)),()->calls[0]++,false));
        assertFalse(FluidCell.commitBatch(List.of(new FluidCell.Mutation<>(c,"oil",101)),()->calls[0]++,false));assertEquals(80,c.amount());assertEquals(0,calls[0]);
    }
    @Test void observerFailureOccursAfterCommitAndDoesNotSkipOtherOwners() {
        var a=new FluidCell<String>(1000,()->{throw new IllegalStateException("observer");});int[] calls={0},items={0};var b=new FluidCell<String>(1000,()->calls[0]++);a.restore("oil",100);
        assertThrows(IllegalStateException.class,()->FluidCell.commitBatch(List.of(new FluidCell.Mutation<>(a,"oil",0),new FluidCell.Mutation<>(b,"gas",45)),()->items[0]=2,false));
        assertEquals(0,a.amount());assertEquals(45,b.amount());assertEquals(2,items[0]);assertEquals(1,calls[0]);
    }
    @Test void oversizedPersistedFluidCanBeDrainedWithoutTruncation() {
        var c=new FluidCell<String>(4000,()->{});c.restore("oil",Integer.MAX_VALUE);
        assertTrue(FluidCell.commitBatch(List.of(new FluidCell.Mutation<>(c,"oil",Integer.MAX_VALUE-100)),()->{},false));assertEquals(Integer.MAX_VALUE-100,c.amount());
    }
}
