package factorycraft.simulation.research;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class LabWorkTest {
  private ResearchLedger.Technology tech(int units) {
    return new ResearchLedger.Technology("t", List.of(), units, 10, false, "t-v1");
  }

  @Test
  void bonusProgressConsumesNoAdditionalScienceAndLastUnitIsAtomic() {
    var ledger = new ResearchLedger();
    var tech = tech(12);
    ledger.select(tech);
    var a = new LabWork();
    var b = new LabWork();
    int[] packs = {20};
    for (int i = 0; i < 100; i++)
      for (var lab : List.of(a, b))
        lab.advance(
            "t",
            10,
            10000,
            10000,
            2000,
            () -> ledger.available(tech) && !ledger.selected().isEmpty() && packs[0] > 0,
            () -> ledger.completeUnit(tech, () -> packs[0]--),
            () -> ledger.completeUnit(tech, () -> {}));
    assertEquals(1, ledger.level("t"));
    assertEquals(10, packs[0]);
  }

  @Test
  void researchAndModuleSpeedMultiplyWithExactFractionalProgress() {
    var lab = new LabWork();
    for (int i = 0; i < 100; i++)
      lab.advance("t", 1000, 12000, 15000, 0, () -> true, () -> true, () -> true);
    assertEquals(180 * LabWork.UNIT, lab.partial("t").work());
  }

  @Test
  void switchingAndIndependentRestoreKeepWorkAndEarnedProductivity() {
    var lab = new LabWork();
    lab.advance("a", 100, 9000, 12000, 800, () -> true, () -> true, () -> true);
    var before = lab.partial("a");
    long credit = lab.bonus();
    lab.advance("b", 200, 10000, 10000, 0, () -> true, () -> true, () -> true);
    assertEquals(before, lab.partial("a"));
    assertEquals(credit, lab.bonus());
    var copy = new LabWork();
    copy.restore(lab.bonus(), lab.partials());
    copy.advance("a", 100, 9000, 12000, 800, () -> true, () -> true, () -> true);
    assertEquals(before.work() * 2, copy.partial("a").work());
    assertEquals(credit * 2, copy.bonus());
  }

  @Test
  void ineligibleLabDoesNotEarnWorkOrBonus() {
    var lab = new LabWork();
    lab.advance("a", 1, 50000, 35000, 10000, () -> false, () -> fail(), () -> fail());
    assertEquals(0, lab.partial("a").work());
    assertEquals(0, lab.bonus());
  }

  @Test
  void fastShortUnitsKeepOvershootAndPackLedger() {
    var lab = new LabWork();
    int[] units = {0};
    lab.advance(
        "a",
        1,
        50000,
        35000,
        0,
        () -> true,
        () -> {
          units[0]++;
          return true;
        },
        () -> fail());
    assertEquals(17, units[0]);
    assertEquals(LabWork.UNIT / 2, lab.partial("a").work());
  }

  @Test
  void sixFiniteResearchLevelsProduceCanonicalCumulativeSpeed() {
    var ledger = new ResearchLedger();
    var levels = new HashMap<String, Integer>();
    int[] expected = {12000, 15000, 19000, 24000, 29000, 35000};
    for (int i = 1; i <= 6; i++) {
      levels.put("factorycraft:lab_research_speed_" + i, 1);
      ledger.restore("", levels, Map.of());
      assertEquals(expected[i - 1], LabWork.researchSpeed(ledger));
    }
  }

  @Test
  void triggersDoNotStealCurrentResearchOrCountBeforePrerequisites() {
    var ledger = new ResearchLedger();
    var science = tech(6);
    ledger.select(science);
    var trigger = new ResearchLedger.Technology("craft", List.of(), 10, 1, false, "craft");
    assertTrue(ledger.trigger(trigger, 4));
    assertEquals("t", ledger.selected());
    assertEquals(4, ledger.units(trigger));
    assertTrue(ledger.trigger(trigger, 6));
    assertFalse(ledger.trigger(trigger, 1));
    assertEquals(1, ledger.level("craft"));
    assertFalse(
        ledger.trigger(
            new ResearchLedger.Technology("later", List.of("missing"), 1, 1, false, "later"), 1));
  }
}
