package factorycraft.simulation.belts;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class RouteFastPathTest {
  @Test
  void optimizedAndGeneralReservationsHaveIdenticalStatesAcrossMixedGraphs() {
    for (int seed = 0; seed < 12; seed++) {
      var random = new Random(seed);
      var fast = new BeltTransport<Integer>(true);
      var general = new BeltTransport<Integer>(false);
      var a = new ArrayList<BeltTransport.Node<Integer>>();
      var b = new ArrayList<BeltTransport.Node<Integer>>();
      var outA = new ArrayList<Integer>();
      var outB = new ArrayList<Integer>();
      for (int i = 0; i < 30; i++) {
        int length = new int[] {42, 96, 109, 136, 384}[random.nextInt(5)],
            tier = 1 + random.nextInt(3);
        a.add(fast.create(new BeltLane<>(length), tier, c -> {}));
        b.add(general.create(new BeltLane<>(length), tier, c -> {}));
      }
      var sinkA = fast.createSink(outA::add);
      var sinkB = general.createSink(outB::add);
      for (int i = 0; i < 30; i++) {
        var firstA = i == 29 ? sinkA : a.get(i + 1);
        var firstB = i == 29 ? sinkB : b.get(i + 1);
        boolean fork = i % 7 == 0 && i + 2 < 30;
        fast.connect(a.get(i), firstA, fork ? a.get(i + 2) : null);
        general.connect(b.get(i), firstB, fork ? b.get(i + 2) : null);
        if (fork && i % 2 == 0) {
          var policy = new SplitterPolicy<Integer>(1, v -> v % 3 == 0);
          fast.configure(a.get(i), policy, i % 3 == 0 ? 1 : 0);
          general.configure(b.get(i), policy, i % 3 == 0 ? 1 : 0);
        }
      }
      int id = 0;
      for (int step = 0; step < 3000; step++) {
        if (step == 900 || step == 1100) {
          fast.setAvailable(a.get(15), step == 1100);
          general.setAvailable(b.get(15), step == 1100);
        }
        int port = random.nextInt(3);
        boolean inserted = fast.insert(a.get(port), id);
        assertEquals(inserted, general.insert(b.get(port), id));
        if (inserted) id++;
        fast.step();
        general.step();
        assertEquals(outA, outB);
        var seen = new HashSet<Integer>(outA);
        for (int i = 0; i < 30; i++) {
          var left = a.get(i).lane().carriers();
          var right = b.get(i).lane().carriers();
          assertEquals(left.size(), right.size());
          for (int j = 0; j < left.size(); j++) {
            assertEquals(left.get(j).value, right.get(j).value);
            assertEquals(left.get(j).positionUnits(), right.get(j).positionUnits());
            assertTrue(seen.add(left.get(j).value));
          }
        }
        assertEquals(id, seen.size());
      }
    }
  }
}
