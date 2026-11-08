package factorycraft.simulation.fluids;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FluidCellTest {
    @Test void simulationAndIncompatibleFillDoNotMutateOrNotify() {
        int[] events={0};var cell=new FluidCell<String>(1000,()->events[0]++);
        assertEquals(1000,cell.fill("water",2000,true));assertEquals(0,cell.amount());assertEquals(0,events[0]);
        assertEquals(800,cell.fill("water",800,false));assertEquals(0,cell.fill("lava",100,false));
        assertEquals(800,cell.drain(2000,true));assertEquals(800,cell.amount());assertEquals(1,events[0]);
        assertEquals(800,cell.drain(2000,false));assertNull(cell.key());assertEquals(2,events[0]);
    }
    @Test void transactionCommitsBothOwnersBeforeEitherCallback() {
        @SuppressWarnings("unchecked") FluidCell<String>[] pair=new FluidCell[2];int[] callbacks={0};
        Runnable check=()->{assertEquals(1000,pair[0].amount()+pair[1].amount());callbacks[0]++;};
        pair[0]=new FluidCell<>(1000,check);pair[1]=new FluidCell<>(400,check);pair[0].restore("water",1000);
        assertEquals(400,pair[0].transferTo(pair[1],600,true));assertEquals(0,callbacks[0]);
        assertEquals(400,pair[0].transferTo(pair[1],600,false));assertEquals(600,pair[0].amount());assertEquals(400,pair[1].amount());assertEquals(2,callbacks[0]);
        assertEquals(0,pair[0].transferTo(pair[0],100,false));assertEquals(0,pair[0].transferTo(pair[1],100,false));
    }
    @Test void oversizedLegacyRestorePreservesEveryUnitAndRecoversByDraining() {
        var cell=new FluidCell<String>(1000,()->{});cell.restore("water",2000);assertEquals(0,cell.fill("water",100,false));
        assertEquals(1200,cell.drain(1200,false));assertEquals(200,cell.space());assertEquals(200,cell.fill("water",500,false));
        assertEquals(1000,cell.amount());assertThrows(IllegalArgumentException.class,()->cell.restore(null,1));
    }
    @Test void integerLimitsAndInvalidRequestsCannotOverflowVolumes() {
        var cell=new FluidCell<String>(Integer.MAX_VALUE,()->{});var target=new FluidCell<String>(Integer.MAX_VALUE,()->{});
        assertEquals(Integer.MAX_VALUE,cell.fill("water",Integer.MAX_VALUE,false));assertEquals(0,cell.fill("water",1,false));
        assertEquals(0,cell.drain(Integer.MIN_VALUE,false));assertEquals(Integer.MAX_VALUE,cell.transferTo(target,Integer.MAX_VALUE,false));
        assertEquals(0,cell.amount());assertEquals(Integer.MAX_VALUE,target.amount());assertThrows(IllegalArgumentException.class,()->new FluidCell<>(0,()->{}));
    }
    @Test void aThrowingCallbackCannotExposeUncommittedVolumeOrSkipTheOtherNotification() {
        var first=new FluidCell<String>(1000,()->{throw new IllegalStateException("callback");});int[] called={0};
        var second=new FluidCell<String>(1000,()->called[0]++);first.restore("water",1000);
        assertThrows(IllegalStateException.class,()->first.transferTo(second,200,false));assertEquals(1,called[0]);assertEquals(1000,first.amount()+second.amount());
    }
}
