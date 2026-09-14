package com.joshuaharwood.velociraptor.raptor.tsport;

import com.joshuaharwood.velociraptor.raptor.TestRaptorBuilder;
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
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Range-query correctness against the TS references. Behavioural 1-to-1 with the TS reference's
 * {@code RaptorTimeRangeQuery} (per-round retained {@code kArrivals} + destination domination), which
 * on these (zero-interchange) cases produces the same set as planarnetwork-raptor's {@code RangeQuery}.
 * <p>
 * One deliberate Java divergence: identical journeys reached from several departures are de-duplicated
 * via an {@code ObjectLinkedOpenHashSet}, so the assertions check <em>set</em> equality with TS output.
 */
class RangeQuerySpecTest {

  private static final LocalDate TEST_DATE = LocalDate.of(2018, 10, 16);

  @Test
  void performsProfileQueries() {
    var trips = List.of(
      t(st("A", null, 1000), st("B", 1030, 1035), st("C", 1100, null)),
      t(st("A", null, 1100), st("B", 1130, 1135), st("C", 1200, null)),
      t(st("A", null, 1200), st("B", 1230, 1235), st("C", 1300, null))
    );

    var raptor = TestRaptorBuilder.create(trips, Map.of(), new HashMap<>());
    var query = new RangeQuery(raptor, new JourneyFactory());
    var result = stripTrips(query.plan(stop("A"), stop("C"), TEST_DATE, null, null));

    assertThat(result).containsExactly(
      j(List.of(st("A", null, 1000), st("B", 1030, 1035), st("C", 1100, null))),
      j(List.of(st("A", null, 1100), st("B", 1130, 1135), st("C", 1200, null))),
      j(List.of(st("A", null, 1200), st("B", 1230, 1235), st("C", 1300, null)))
    );
  }

  /**
   * Port of TS "does not share bestArrivals or routeScanner". A direct A&rarr;C departing 1359 and
   * arriving 1501 is kept alongside the A&rarr;B&rarr;C journey (departing 1400, arriving 1500): the
   * direct reaches C at round 1 and the via journey at round 2, so the per-round domination bound never
   * pits them against each other - both survive.
   */
  @Test
  void keepsDirectAndViaReachingDestinationAtDifferentRounds() {
    var trips = List.of(
      t(st("A", null, 1359), st("C", 1501, null)),
      t(st("A", null, 1400), st("B", 1430, null)),
      t(st("B", null, 1430), st("C", 1500, null))
    );

    var raptor = TestRaptorBuilder.create(trips, Map.of(), new HashMap<>());
    var query = new RangeQuery(raptor, new JourneyFactory());
    var result = stripTrips(query.plan(stop("A"), stop("C"), TEST_DATE, null, null));

    assertThat(result).containsExactly(
      j(List.of(st("A", null, 1359), st("C", 1501, null))),
      j(List.of(st("A", null, 1400), st("B", 1430, null)),
        List.of(st("B", null, 1430), st("C", 1500, null)))
    );
  }
}
