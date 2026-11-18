package factorycraft.simulation.rail;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RailGhostTest {
  @Test
  void impossibleTurnStillHasFiniteVisibleGeometryWithoutAuthority() {
    var a = new RailEnd(0, 70, 0, new RailHeading(0));
    var b = new RailEnd(4, 70, -4, new RailHeading(4));
    assertThrows(IllegalArgumentException.class, () -> RailCurve.plan(a, b));
    var ghost = RailCurve.preview(a, b);
    assertFalse(ghost.validated());
    assertTrue(ghost.length() > 0);
    for (double s = 0; s < ghost.length(); s += .1)
      assertTrue(Double.isFinite(ghost.sample(s, false).position().y()));
    assertFalse(RailMesh.build(ghost).isEmpty());
  }

  @Test
  void unsupportedSteepSlopeCanBeShownButNeverBecomesARealCurve() {
    var a = new RailEnd(0, 70, 0, new RailHeading(0));
    var b = new RailEnd(0, 110, -8, new RailHeading(0));
    assertThrows(IllegalArgumentException.class, () -> RailCurve.plan(a, b));
    var ghost = RailCurve.preview(a, b);
    assertFalse(ghost.validated());
    assertEquals(110.125, ghost.sample(ghost.length(), false).position().y(), 1e-6);
    assertTrue(RailCurve.plan(a, new RailEnd(0, 70, -8, new RailHeading(0))).validated());
  }
}
