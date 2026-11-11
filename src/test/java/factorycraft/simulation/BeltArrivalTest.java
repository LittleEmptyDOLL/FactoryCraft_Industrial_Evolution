package factorycraft.simulation;

import static org.junit.jupiter.api.Assertions.*;

import factorycraft.simulation.belts.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class BeltArrivalTest {
  record Payload(int id, int items) {}

  @Test
  void countedCarrierPulsesOnlyOnRealAcceptanceNotBlockedOffersOrInternalMotion() {
    var transport = new BeltTransport<Payload>();
    var a = transport.create(new BeltLane<>(), 3, c -> {});
    var b = transport.create(new BeltLane<>(), 1, c -> {});
    transport.connect(a, b, null);
    var arrivals = new ArrayList<Payload>();
    transport.arrivalListener(b, arrivals::add);
    var expected = new ArrayList<Payload>();
    for (int i = 0; i < 4; i++) {
      var value = new Payload(i, i + 1);
      a.lane().restore(value, 1 - i * .25);
      expected.add(value);
    }
    transport.changedExternally(a);
    transport.setEnabled(b, false);
    for (int i = 0; i < 200; i++) transport.step();
    assertTrue(arrivals.isEmpty());
    transport.setEnabled(b, true);
    for (int i = 0; i < 2000; i++) transport.step();
    assertEquals(expected, arrivals);
    assertEquals(10, arrivals.stream().mapToInt(Payload::items).sum());
    for (int i = 0; i < 500; i++) transport.step();
    assertEquals(4, arrivals.size());
    assertFalse(transport.insert(b, new Payload(55, 4)));
    assertEquals(4, arrivals.size());
    assertEquals(4, b.lane().carriers().size());
  }
}
