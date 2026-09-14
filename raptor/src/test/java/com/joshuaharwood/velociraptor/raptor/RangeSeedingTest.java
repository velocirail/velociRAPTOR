package com.joshuaharwood.velociraptor.raptor;

import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.model.Transfer;
import com.joshuaharwood.velociraptor.raptor.model.Trip;
import com.joshuaharwood.velociraptor.raptor.query.RangeQuery;
import com.joshuaharwood.velociraptor.raptor.result.Journey;
import com.joshuaharwood.velociraptor.raptor.result.JourneyFactory;
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
 * A range query iterates every departure a journey in the window could start with: the origin's own
 * trip departures, and for each fixed link out of the origin, the far end's departures pushed back by
 * the link. Seeding only the origin's departures (BUGS.md 6.3) never found the journey that starts with
 * a fixed link to the neighbouring stop.
 */
class RangeSeedingTest {

  private static final LocalDate TEST_DATE = LocalDate.of(2018, 10, 16);

  /**
   * O→X direct at 1000 (arr 1100) and 830 (arr 1200). Y→X at 930 (arr 1000), and a 30 s link O→Y.
   * Leaving O at 900, taking the link to Y for the 930 and arriving 1000 is the best journey in [900, 1100).
   */
  private static final List<Trip> TRIPS = List.of(
    t(st("O", null, 830), st("X", 1200, null)),
    t(st("O", null, 1000), st("X", 1100, null)),
    t(st("Y", null, 930), st("X", 1000, null))
  );
  private static final Map<Stop, List<Transfer>> LINKS = Map.of(
    stop("O"), List.of(new Transfer(stop("O"), stop("Y"), 30, 0, Integer.MAX_VALUE, null))
  );

  private static final Journey DIRECT = j(List.of(st("O", null, 1000), st("X", 1100, null)));
  private static final Journey VIA_Y = j(tf("O", "Y", 30), List.of(st("Y", null, 930), st("X", 1000, null)));

  private static List<Journey> range(FixedLinkRules rules, int start, int end) {
    var raptor = TestRaptorBuilder.create(TRIPS, LINKS, new HashMap<>());
    return stripTrips(new RangeQuery<>(raptor, new JourneyFactory(), rules).plan(stop("O"), stop("X"), TEST_DATE, start, end));
  }

  @Test
  void aJourneyThatStartsWithAFixedLinkIsSeededFromTheFarEndsDeparture() {
    var result = range(FixedLinkRules.NONE, 900, 1100);

    assertThat(result).containsExactly(VIA_Y, DIRECT);
    assertThat(result.getFirst().departureTime()).as("leaves the origin exactly in time for the 930").isEqualTo(900);
  }

  @Test
  void theSeedRespectsTheWindow() {
    // 901 onwards: leaving at 900 is no longer in the window, so only the direct train is offered.
    assertThat(range(FixedLinkRules.NONE, 901, 1100)).containsExactly(DIRECT);
  }

  @Test
  void theFarEndsInterchangeIsPartOfTheSeed() {
    // With a 60 s interchange at Y, the link has to land 60 s before the 930: leave at 840.
    var interchange = new HashMap<Stop, Integer>();
    interchange.put(stop("Y"), 60);
    var raptor = TestRaptorBuilder.create(TRIPS, LINKS, interchange);

    var result = stripTrips(new RangeQuery<>(raptor, new JourneyFactory()).plan(stop("O"), stop("X"), TEST_DATE, 800, 1100));

    assertThat(result).extracting(Journey::departureTime).contains(840);
  }

  @Test
  void noLinkSeedsWhenAJourneyMayNotBeginWithOne() {
    assertThat(range(new FixedLinkRules(true, false, false), 900, 1100)).containsExactly(DIRECT);
  }

  /**
   * A journey behind a leading link departs at the latest time the link can be started and still
   * make the train, whichever seed found it - not at the seed. Here an unrelated 850 departure at O
   * is the only seed in the window; the link journey it finds still leaves at 900.
   */
  @Test
  void aLeadingLinkJourneyDepartsAtItsLatestStartNotAtTheSeed() {
    var trips = List.of(
      t(st("O", null, 850), st("Z", 860, null)),
      t(st("Y", null, 930), st("X", 1000, null))
    );
    var raptor = TestRaptorBuilder.create(trips, LINKS, new HashMap<>());

    var result = stripTrips(new RangeQuery<>(raptor, new JourneyFactory()).plan(stop("O"), stop("X"), TEST_DATE, 850, 899));

    assertThat(result).containsExactly(VIA_Y);
    assertThat(result.getFirst().departureTime()).isEqualTo(900);
  }
}
