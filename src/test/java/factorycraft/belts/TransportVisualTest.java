package factorycraft.belts;

import static org.junit.jupiter.api.Assertions.*;

import factorycraft.simulation.inserters.InserterPlacement;
import org.junit.jupiter.api.Test;

class TransportVisualTest {
  @Test
  void reflectionOnlyChangesAmbiguousAxis() {
    int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    for (var arm : dirs)
      for (var belt : dirs) {
        int a = InserterPlacement.lane(arm[0], arm[1], belt[0], belt[1], false),
            b = InserterPlacement.lane(arm[0], arm[1], belt[0], belt[1], true);
        int cross = arm[0] * -belt[1] + arm[1] * belt[0];
        if (cross == 0) {
          assertEquals(1, a);
          assertEquals(0, b);
        } else {
          assertEquals(a, b);
          assertEquals(cross > 0 ? 1 : 0, a);
        }
      }
  }

  @Test
  void allRouteMeshesRemainLocalFiniteAndNonempty() {
    for (var shape : BeltRoute.Shape.values())
      for (int span : new int[] {-16, -4, -1, 1, 4, 16})
        for (int turn = 0; turn < 4; turn++) {
          if (shape != BeltRoute.Shape.LIFT && span != 1) continue;
          var r =
              new BeltRoute(
                  shape,
                  shape == BeltRoute.Shape.LIFT
                      ? span
                      : shape == BeltRoute.Shape.UNDERGROUND ? 4 : 0,
                  turn);
          for (int y = Math.min(0, r.heightDelta()); y <= Math.max(0, r.heightDelta()); y++) {
            var mesh = TransportMesh.build(r, y, false);
            assertFalse(mesh.faces().isEmpty());
            assertTrue(mesh.sampledSegments() <= 24);
            for (var f : mesh.faces())
              for (var v : f.vertices()) {
                assertTrue(Double.isFinite(v.x() + v.y() + v.z() + v.u() + v.v()));
                assertTrue(v.y() >= -1e-8 && v.y() <= 1.00000001, "cell-local height");
              }
          }
        }
  }

  @Test
  void maximumLiftSamplesLinearWorkInsteadOfResamplingWholePathPerCell() {
    int faces = 0, samples = 0;
    var r = new BeltRoute(BeltRoute.Shape.LIFT, 16, 1);
    long start = System.nanoTime();
    for (int i = 0; i <= 16; i++) {
      var m = TransportMesh.build(r, i, false);
      faces += m.faces().size();
      samples += m.sampledSegments();
    }
    int old = (16 + 1) * (32 + 16 * 16);
    assertTrue(samples <= 24 * 17);
    assertTrue(samples < old / 8);
    System.out.printf(
        "FACTORYCRAFT_TRANSPORT_MESH: height=16 turn=1 old sampled segments=%d new=%d logical"
            + " faces=%d double-sided quad upper bound=%d vertex-array upper bound bytes=%d"
            + " construction ms=%.3f%n",
        old, samples, faces, faces * 4, faces * 4 * 128, (System.nanoTime() - start) / 1e6);
  }

  @Test
  void meshBuildingNeverChangesMetricLaneLengthsOrOwnership() {
    for (var shape : BeltRoute.Shape.values()) {
      var r =
          new BeltRoute(
              shape,
              shape == BeltRoute.Shape.LIFT ? 16 : shape == BeltRoute.Shape.UNDERGROUND ? 4 : 0,
              1);
      int left = r.length(0), right = r.length(1);
      var a = r.point(0, left / 2d);
      TransportMesh.build(r, 0, false);
      assertEquals(left, r.length(0));
      assertEquals(right, r.length(1));
      assertEquals(a, r.point(0, left / 2d));
    }
  }
}
