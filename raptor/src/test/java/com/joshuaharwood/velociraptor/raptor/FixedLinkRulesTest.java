package com.joshuaharwood.velociraptor.raptor;

import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.model.Transfer;
import com.joshuaharwood.velociraptor.raptor.model.Trip;
import com.joshuaharwood.velociraptor.raptor.query.DepartAfterQuery;
import com.joshuaharwood.velociraptor.raptor.query.RangeQuery;
import com.joshuaharwood.velociraptor.raptor.result.Journey;
import com.joshuaharwood.velociraptor.raptor.result.JourneyFactory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.joshuaharwood.velociraptor.raptor.TestData.j;
import static com.joshuaharwood.velociraptor.raptor.TestData.st;
import static com.joshuaharwood.velociraptor.raptor.TestData.stop;
import static com.joshuaharwood.velociraptor.raptor.TestData.stripTrips;
import static com.joshuaharwood.velociraptor.raptor.TestData.t;
import static com.joshuaharwood.velociraptor.raptor.TestData.tf;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FixedLinkRules} restrict where a journey may use a fixed link, and they do it inside the
 * scan: with a rule on, the train journey the link would have out-competed is returned instead of
 * nothing. Each rule is checked with {@link FixedLinkRules#NONE} first, to show the link journey the
 * rule then removes, and on both query types.
 */
class FixedLinkRulesTest {

  private static final LocalDate TEST_DATE = LocalDate.of(2018, 10, 16);

  private static Transfer link(String from, String to, int duration) {
    return new Transfer(stop(from), stop(to), duration, 0, Integer.MAX_VALUE, null);
  }

  private static List<Journey> departAfter(List<Trip> trips, Map<Stop, List<Transfer>> links, FixedLinkRules rules,
                                           String origin, String destination, int time) {
    var raptor = TestRaptorBuilder.create(trips, links, new HashMap<>());
    return stripTrips(new DepartAfterQuery<>(raptor, new JourneyFactory(), rules).plan(stop(origin), stop(destination), TEST_DATE, time));
  }

  private static List<Journey> range(List<Trip> trips, Map<Stop, List<Transfer>> links, FixedLinkRules rules,
                                     String origin, String destination, int from, int to) {
    var raptor = TestRaptorBuilder.create(trips, links, new HashMap<>());
    return stripTrips(new RangeQuery<>(raptor, new JourneyFactory(), rules).plan(stop(origin), stop(destination), TEST_DATE, from, to));
  }

  /**
   * From O, a fixed link to Y (300 s) catches the 1400 to D, arriving 1500; the direct train leaves O
   * at 2000 and arrives 3000. The 1000 to Z goes nowhere useful; it is there so the range query has a
   * departure to seed at 1000 (it seeds only the origin's trip departures - BUGS.md 6.3).
   */
  @Nested
  class Leading {
    final List<Trip> trips = List.of(
      t(st("O", null, 2000), st("D", 3000, null)),
      t(st("Y", null, 1400), st("D", 1500, null)),
      t(st("O", null, 1000), st("Z", 1050, null))
    );
    final Map<Stop, List<Transfer>> links = Map.of(stop("O"), List.of(link("O", "Y", 300)));
    final Journey direct = j(List.of(st("O", null, 2000), st("D", 3000, null)));
    final Journey viaLink = j(tf("O", "Y", 300), List.of(st("Y", null, 1400), st("D", 1500, null)));
    final FixedLinkRules rule = new FixedLinkRules(true, false, false);

    @Test
    void withoutTheRuleTheJourneyBeginningWithALinkIsReturned() {
      assertThat(departAfter(trips, links, FixedLinkRules.NONE, "O", "D", 1000)).containsExactly(direct, viaLink);
    }

    @Test
    void departAfterDoesNotBeginWithALink() {
      assertThat(departAfter(trips, links, rule, "O", "D", 1000)).containsExactly(direct);
    }

    @Test
    void rangeDoesNotBeginWithALink() {
      assertThat(range(trips, links, FixedLinkRules.NONE, "O", "D", 1000, 2500))
        .extracting(journey -> journey.legs().getFirst() instanceof com.joshuaharwood.velociraptor.raptor.model.Leg.TransferLeg)
        .contains(true);
      assertThat(range(trips, links, rule, "O", "D", 1000, 2500)).containsExactly(direct);
    }
  }

  /**
   * O→A arrives 1100. A fixed link A→D (300 s) reaches D at 1400; the 1500 train from A reaches it at
   * 1600. Without the rule the link out-competes the train and is the only round-2 journey; with it,
   * the train journey is returned - which is why the rule lives in the scan and not in a filter.
   */
  @Nested
  class Trailing {
    final List<Trip> trips = List.of(
      t(st("O", null, 1000), st("A", 1100, null)),
      t(st("A", null, 1500), st("D", 1600, null))
    );
    final Map<Stop, List<Transfer>> links = Map.of(stop("A"), List.of(link("A", "D", 300)));
    final Journey viaLink = j(List.of(st("O", null, 1000), st("A", 1100, null)), tf("A", "D", 300));
    final Journey byTrain = j(List.of(st("O", null, 1000), st("A", 1100, null)), List.of(st("A", null, 1500), st("D", 1600, null)));
    final FixedLinkRules rule = new FixedLinkRules(false, true, false);

    @Test
    void withoutTheRuleTheJourneyEndingWithALinkIsReturned() {
      assertThat(departAfter(trips, links, FixedLinkRules.NONE, "O", "D", 900)).containsExactly(viaLink);
    }

    @Test
    void departAfterDoesNotEndWithALink() {
      assertThat(departAfter(trips, links, rule, "O", "D", 900)).containsExactly(byTrain);
    }

    @Test
    void rangeDoesNotEndWithALink() {
      assertThat(range(trips, links, FixedLinkRules.NONE, "O", "D", 900, 1100)).containsExactly(viaLink);
      assertThat(range(trips, links, rule, "O", "D", 900, 1100)).containsExactly(byTrain);
    }

    @Test
    void aLinkIntoAnotherStopIsStillTaken() {
      // The rule is about the destination only: O→A, link A→D, then the train D→E is fine.
      var withOnward = List.of(
        t(st("O", null, 1000), st("A", 1100, null)),
        t(st("D", null, 1450), st("E", 1700, null))
      );
      var expected = j(List.of(st("O", null, 1000), st("A", 1100, null)), tf("A", "D", 300), List.of(st("D", null, 1450), st("E", 1700, null)));

      assertThat(departAfter(withOnward, links, rule, "O", "E", 900)).containsExactly(expected);
    }
  }

  /**
   * O→A arrives 1100; links A→B (300 s) and B→C (300 s) chain to C at 1700; the 1800 from B reaches
   * C at 1900. Without the rule the two links in a row win; with it, the second link is not taken and
   * the train from B is.
   */
  @Nested
  class Contiguous {
    final List<Trip> trips = List.of(
      t(st("O", null, 1000), st("A", 1100, null)),
      t(st("B", null, 1800), st("C", 1900, null))
    );
    final Map<Stop, List<Transfer>> links = Map.of(
      stop("A"), List.of(link("A", "B", 300)),
      stop("B"), List.of(link("B", "C", 300))
    );
    final Journey twoLinks = j(List.of(st("O", null, 1000), st("A", 1100, null)), tf("A", "B", 300), tf("B", "C", 300));
    final Journey linkThenTrain = j(List.of(st("O", null, 1000), st("A", 1100, null)), tf("A", "B", 300), List.of(st("B", null, 1800), st("C", 1900, null)));
    final FixedLinkRules rule = new FixedLinkRules(false, false, true);

    @Test
    void withoutTheRuleTwoLinksInARowAreReturned() {
      assertThat(departAfter(trips, links, FixedLinkRules.NONE, "O", "C", 900)).containsExactly(twoLinks);
    }

    @Test
    void departAfterDoesNotTakeTwoLinksInARow() {
      assertThat(departAfter(trips, links, rule, "O", "C", 900)).containsExactly(linkThenTrain);
    }

    @Test
    void rangeDoesNotTakeTwoLinksInARow() {
      assertThat(range(trips, links, FixedLinkRules.NONE, "O", "C", 900, 1100)).containsExactly(twoLinks);
      assertThat(range(trips, links, rule, "O", "C", 900, 1100)).containsExactly(linkThenTrain);
    }

    @Test
    void aLinkAfterATrainAfterALinkIsFine() {
      // link, train, link: the links are not adjacent.
      var trips = List.of(
        t(st("O", null, 1000), st("A", 1100, null)),
        t(st("B", null, 1500), st("C", 1600, null))
      );
      var links = Map.of(
        stop("A"), List.of(link("A", "B", 300)),
        stop("C"), List.of(link("C", "D", 300))
      );
      var expected = j(List.of(st("O", null, 1000), st("A", 1100, null)), tf("A", "B", 300),
                       List.of(st("B", null, 1500), st("C", 1600, null)), tf("C", "D", 300));

      assertThat(departAfter(trips, links, rule, "O", "D", 900)).containsExactly(expected);
    }
  }

  @Test
  void allRulesTogether() {
    // Origin link O→Y, chained links A→B→C, and a link into D all exist; only train-link-train survives.
    var trips = List.of(
      t(st("O", null, 1000), st("A", 1100, null)),
      t(st("Y", null, 1200), st("D", 1250, null)),
      t(st("B", null, 1500), st("D", 1600, null))
    );
    var links = Map.of(
      stop("O"), List.of(link("O", "Y", 100)),
      stop("A"), List.of(link("A", "B", 300), link("A", "D", 100)),
      stop("B"), List.of(link("B", "D", 100))
    );
    var expected = j(List.of(st("O", null, 1000), st("A", 1100, null)), tf("A", "B", 300), List.of(st("B", null, 1500), st("D", 1600, null)));

    assertThat(departAfter(trips, links, FixedLinkRules.ALL, "O", "D", 900)).containsExactly(expected);
    assertThat(range(trips, links, FixedLinkRules.ALL, "O", "D", 900, 1100)).containsExactly(expected);
  }
}
