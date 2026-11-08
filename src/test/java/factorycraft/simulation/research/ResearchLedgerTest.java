package factorycraft.simulation.research;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ResearchLedgerTest {
    private ResearchLedger.Technology tech(String id,List<String> parents,int units,boolean repeat){return new ResearchLedger.Technology(id,parents,units,20,repeat,id+"-v1");}
    @Test void prerequisitesAndTeamsStayIndependent(){var a=new ResearchLedger();var b=new ResearchLedger();var root=tech("a",List.of(),1,false);var next=tech("b",List.of("a"),1,false);assertFalse(a.select(next));assertTrue(a.select(root));assertTrue(a.completeUnit(root,()->{}));assertTrue(a.select(next));assertEquals(0,b.level("a"));assertFalse(b.select(next));}
    @Test void twoLabsCannotSpendPacksForTheSameLastUnit(){var t=new ResearchLedger();var tech=tech("a",List.of(),1,false);t.select(tech);int[] packs={2};assertTrue(t.completeUnit(tech,()->packs[0]--));assertFalse(t.completeUnit(tech,()->packs[0]--));assertEquals(1,packs[0]);assertEquals(1,t.level("a"));}
    @Test void switchingPreservesCompletedUnitsButStaleContributorsCannotCommit(){var t=new ResearchLedger();var a=tech("a",List.of(),3,false);var b=tech("b",List.of(),2,false);t.select(a);t.completeUnit(a,()->{});t.select(b);assertFalse(t.completeUnit(a,()->fail("stale consumer called")));assertEquals(1,t.units(a));t.select(a);assertEquals(1,t.units(a));}
    @Test void reloadAndDefinitionEditsDoNotReuseAnotherCost(){var t=new ResearchLedger();var a=tech("a",List.of(),3,false);t.select(a);t.completeUnit(a,()->{});var copy=new ResearchLedger();copy.restore(t.selected(),t.levels(),t.progress());assertEquals(1,copy.units(a));var changed=new ResearchLedger.Technology("a",List.of(),4,20,false,"a-v2");assertEquals(0,copy.units(changed));}
    @Test void repeatableLevelsGrowCostsWithoutOverflow(){var t=new ResearchLedger();var a=tech("a",List.of(),1,true);t.select(a);t.completeUnit(a,()->{});assertEquals(1,t.level("a"));assertEquals(2,a.cost(t.level("a")));t.completeUnit(a,()->{});assertEquals(1,t.level("a"));t.completeUnit(a,()->{});assertEquals(2,t.level("a"));assertEquals(1000000,a.cost(Integer.MAX_VALUE));}
    @Test void failedInventoryCommitCannotUnlockResearch(){var t=new ResearchLedger();var a=tech("a",List.of(),1,false);t.select(a);assertThrows(IllegalStateException.class,()->t.completeUnit(a,()->{throw new IllegalStateException();}));assertEquals(0,t.level("a"));assertEquals(0,t.units(a));}
}
