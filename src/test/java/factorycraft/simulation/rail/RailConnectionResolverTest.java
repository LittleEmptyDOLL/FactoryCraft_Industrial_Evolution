package factorycraft.simulation.rail;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class RailConnectionResolverTest {
  private static RailEnd end(int x, int z, int h) {
    return new RailEnd(x, 70, z, new RailHeading(h));
  }

  @Test
  void automaticExtensionUsesEitherActualEndForEveryHeading() {
    for (int i = 0; i < 16; i++) {
      var heading = new RailHeading(i);
      var v = heading.vector();
      int x =
          (int) Math.round(v.x() * (i % 4 == 1 || i % 4 == 3 ? Math.sqrt(5) : Math.sqrt(2)) * 8);
      int z =
          (int) Math.round(v.z() * (i % 4 == 1 || i % 4 == 3 ? Math.sqrt(5) : Math.sqrt(2)) * 8);
      // Endpoints need only be collinear integer cells; use the exact source heading family.
      if (i % 4 == 0) {
        x = (int) Math.round(v.x() * 16);
        z = (int) Math.round(v.z() * 16);
      }
      var a = end(0, 0, i);
      var b = end(x, z, i);
      for (boolean pickFirst : new boolean[] {false, true}) {
        var source =
            new RailConnectionResolver.Source(
                pickFirst ? a.reverse() : b, pickFirst ? b : a.reverse(), 0);
        for (boolean forward : new boolean[] {false, true}) {
          var goal = end(forward ? x * 3 : -x * 2, forward ? z * 3 : -z * 2, i);
          var result =
              RailConnectionResolver.resolve(
                  new RailConnectionResolver.Request(source, List.of(goal), false));
          assertTrue(
              result.valid(), "heading=" + i + " direction=" + forward + " " + result.reason());
          assertEquals(forward ? b : a.reverse(), result.start());
          assertEquals(forward ? i : (i + 8) % 16, result.end().heading().index());
          assertTrue(result.curve().validated());
        }
      }
    }
  }

  @Test
  void cameraAxisCanReverseWithoutMovingSavedEndpointsAndExplicitOverrideStaysExplicit() {
    var a = end(0, 0, 0);
    var b = end(0, -16, 0);
    var source = new RailConnectionResolver.Source(b, a.reverse(), 0);
    var goal = end(0, 32, 0);
    var auto =
        RailConnectionResolver.resolve(
            new RailConnectionResolver.Request(source, List.of(goal), false));
    assertTrue(auto.valid());
    assertEquals(a.reverse(), auto.start());
    assertEquals(8, auto.end().heading().index());
    assertFalse(
        RailConnectionResolver.resolve(
                new RailConnectionResolver.Request(source, List.of(goal), true))
            .valid());
    assertEquals(a, end(0, 0, 0));
    assertEquals(b, end(0, -16, 0));
  }

  @Test
  void existingDestinationCanUseTheNextActualConnectableEndWithoutInventingMidEdgeNodes() {
    var source = new RailConnectionResolver.Source(end(0, 0, 0), end(0, 16, 8), 0);
    var inaccessible = end(0, -32, 8);
    var connectable = end(0, -48, 0);
    var result =
        RailConnectionResolver.resolve(
            new RailConnectionResolver.Request(source, List.of(inaccessible, connectable), true));
    assertTrue(result.valid());
    assertEquals(connectable, result.end());
    assertEquals(end(0, 0, 0), result.start());
  }

  @Test
  void curvedSourceKeepsBothPhysicalOutwardTangentsAndManualChoiceCanBeRed() {
    var a = end(0, 0, 0);
    var b = end(24, -24, 4);
    var source = new RailConnectionResolver.Source(a.reverse(), b, 0);
    assertEquals(
        b,
        RailConnectionResolver.resolve(
                new RailConnectionResolver.Request(source, List.of(end(56, -24, 4)), false))
            .start());
    assertEquals(
        a.reverse(),
        RailConnectionResolver.resolve(
                new RailConnectionResolver.Request(source, List.of(end(0, 32, 8)), false))
            .start());
    var locked = new RailConnectionResolver.Source(a.reverse(), b, 1);
    assertFalse(
        RailConnectionResolver.resolve(
                new RailConnectionResolver.Request(locked, List.of(end(56, -24, 4)), true))
            .valid());
  }

  @Test
  void sourceSideAndOtherEndpointInvalidateTheBudgetedHintCache() {
    var hints = new EndpointHints();
    var id = UUID.randomUUID();
    var a = end(0, 0, 0);
    var b = end(0, -16, 0);
    var goal = end(0, -32, 0);
    hints.key(new EndpointHints.Key(a, goal, -1, id, 1, b, 0));
    assertEquals(1, hints.advance(1, p -> new EndpointHints.Result(p, "ready", 16)));
    assertTrue(hints.key(new EndpointHints.Key(a, goal, -1, id, 1, b, 2)));
    assertNull(hints.get(0));
  }
}
