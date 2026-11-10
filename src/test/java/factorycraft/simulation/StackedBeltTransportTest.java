package factorycraft.simulation;

import static org.junit.jupiter.api.Assertions.*;

import factorycraft.simulation.belts.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Immutable component tokens stand in for native ItemStack payloads tested by GameTest. */
class StackedBeltTransportTest {
  record Stack(int identity, int quantity, String components) {}

  @Test
  void fullRingsConservePhysicalCarriersWeightedQuantityAndComponentsAtEveryTier() {
    for (int tier = 1; tier <= 3; tier++) {
      var transport = new BeltTransport<Stack>();
      var ring = new ArrayList<BeltTransport.Node<Stack>>();
      for (int i = 0; i < 12; i++) ring.add(transport.create(new BeltLane<>(), tier, c -> {}));
      var expected = new HashMap<Integer, Stack>();
      int id = 0;
      for (int i = 0; i < ring.size(); i++) {
        var node = ring.get(i);
        transport.connect(node, ring.get((i + 1) % ring.size()), null);
        for (int j = 0; j < 4; j++) {
          var stack = new Stack(id, 1 + id % 4, "grade-" + id);
          expected.put(id++, stack);
          node.lane().restore(stack, 1 - j * .25);
        }
        transport.changedExternally(node);
      }
      for (int step = 0; step < 2500; step++) {
        transport.step();
        for (var node : ring) BeltLaneTest.assertSpacing(node.lane());
      }
      var actual = new HashMap<Integer, Stack>();
      for (var node : ring)
        for (var carrier : node.lane().carriers())
          assertNull(actual.put(carrier.value.identity(), carrier.value));
      assertEquals(expected, actual);
      assertEquals(48, actual.size());
      assertEquals(120, actual.values().stream().mapToInt(Stack::quantity).sum());
      assertTrue(transport.transfers() > 100);
    }
  }

  @Test
  void mixedSpeedsBackpressureAndRouteChangesNeverConfuseItemsWithCarriers() {
    var transport = new BeltTransport<Stack>();
    var chain = new ArrayList<BeltTransport.Node<Stack>>();
    for (int tier : new int[] {3, 1, 2, 3, 1})
      chain.add(transport.create(new BeltLane<>(), tier, c -> {}));
    for (int i = 0; i < chain.size() - 1; i++)
      transport.connect(chain.get(i), chain.get(i + 1), null);
    var delivered = new HashMap<Integer, Stack>();
    var sink = transport.createSink(s -> assertNull(delivered.put(s.identity(), s)));
    int created = 0, quantity = 0;
    for (int step = 0; step < 4500; step++) {
      if (step == 1200) transport.connect(chain.getLast(), sink, null);
      if (step == 2400) transport.connect(chain.getLast(), null, null);
      if (step == 3100) transport.connect(chain.getLast(), sink, null);
      var next = new Stack(created, 1 + created % 4, "grade-" + created);
      if (transport.insert(chain.getFirst(), next)) {
        created++;
        quantity += next.quantity();
      }
      transport.step();
      int physical = delivered.size(),
          items = delivered.values().stream().mapToInt(Stack::quantity).sum();
      var ids = new HashSet<>(delivered.keySet());
      for (var node : chain) {
        BeltLaneTest.assertSpacing(node.lane());
        for (var c : node.lane().carriers()) {
          assertTrue(ids.add(c.value.identity()));
          assertEquals("grade-" + c.value.identity(), c.value.components());
          physical++;
          items += c.value.quantity();
        }
      }
      assertEquals(created, physical);
      assertEquals(quantity, items);
    }
    assertTrue(delivered.size() > 20);
  }
}
