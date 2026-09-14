package com.joshuaharwood.velociraptor.raptor;

import com.joshuaharwood.velociraptor.raptor.model.Leg;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.model.Trip;
import com.joshuaharwood.velociraptor.raptor.query.DepartAfterQuery;
import com.joshuaharwood.velociraptor.raptor.query.RangeQuery;
import com.joshuaharwood.velociraptor.raptor.result.Journey;
import com.joshuaharwood.velociraptor.raptor.result.JourneyFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.joshuaharwood.velociraptor.raptor.TestData.j;
import static com.joshuaharwood.velociraptor.raptor.TestData.st;
import static com.joshuaharwood.velociraptor.raptor.TestData.stop;
import static com.joshuaharwood.velociraptor.raptor.TestData.stripTrips;
import static com.joshuaharwood.velociraptor.raptor.TestData.t;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A not-via stop forbids passing through it, not boarding beyond it. The route scan used to stop at
 * a forbidden stop, and since the queue only records the earliest marked position on a route, every
 * marked stop after it on the same route was never examined (BUGS.md 6.1).
 */
class NotViaTest {

  private static final LocalDate TEST_DATE = LocalDate.of(2018, 10, 16);

  /**
   * Route 1: O 100 → A 200 → B 300. Route 2: A 210 → N 220 → B 320 → C 400. Not via N.
   * Round 1 marks A and B; round 2 scans route 2 from A and must not carry on through N, but B is
   * also marked and boarding there is fine: O→B on route 1, then B→C on route 2.
   */
  private static final List<Trip> TRIPS = List.of(
    t(st("O", null, 100), st("A", 200, 200), st("B", 300, null)),
    t(st("A", null, 210), st("N", 220, 220), st("B", 320, 320), st("C", 400, null))
  );

  private static final Journey AVOIDING_N = j(
    List.of(st("O", null, 100), st("A", 200, 200), st("B", 300, null)),
    List.of(st("B", 320, 320), st("C", 400, null))
  );

  private static RaptorAlgorithm raptor() {
    return TestRaptorBuilder.create(TRIPS, Map.of(), new HashMap<>());
  }

  @Test
  void withoutNotViaTheJourneyThroughNIsPreferred() {
    var viaN = j(
      List.of(st("O", null, 100), st("A", 200, 200)),
      List.of(st("A", null, 210), st("N", 220, 220), st("B", 320, 320), st("C", 400, null))
    );

    assertThat(stripTrips(new DepartAfterQuery<>(raptor(), new JourneyFactory()).plan(stop("O"), stop("C"), TEST_DATE, 0)))
      .containsExactly(viaN);
  }

  @Test
  void departAfterBoardsBeyondTheNotViaStop() {
    var result = stripTrips(new DepartAfterQuery<>(raptor(), new JourneyFactory()).plan(stop("O"), stop("C"), TEST_DATE, 0, Set.of(stop("N"))));

    assertThat(result).containsExactly(AVOIDING_N);
  }

  @Test
  void rangeBoardsBeyondTheNotViaStop() {
    var result = stripTrips(new RangeQuery<>(raptor(), new JourneyFactory()).plan(stop("O"), stop("C"), TEST_DATE, 0, 1000, Set.of(stop("N"))));

    assertThat(result).containsExactly(AVOIDING_N);
  }

  @Test
  void aTripBoardedBeforeTheNotViaStopIsNotRiddenThroughIt() {
    // Only route 2 exists, so the sole way to C passes N: nothing may be returned.
    var onlyRoute2 = TestRaptorBuilder.create(List.of(TRIPS.get(1)), Map.of(), new HashMap<>());

    var result = new DepartAfterQuery<>(onlyRoute2, new JourneyFactory()).plan(stop("A"), stop("C"), TEST_DATE, 0, Set.of(stop("N")));

    assertThat(result).isEmpty();
  }

  @Test
  void noReturnedLegCallsAtTheNotViaStop() {
    var result = new DepartAfterQuery<>(raptor(), new JourneyFactory()).plan(stop("O"), stop("C"), TEST_DATE, 0, Set.of(stop("N")));

    for (Journey journey : result) {
      for (Leg leg : journey.legs()) {
        if (leg instanceof Leg.TimetableLeg tl) {
          assertTrue(tl.stopTimes().stream().map(s -> s.stop()).noneMatch(new Stop("N")::equals), "leg calls at N: " + tl);
        }
      }
    }
  }
}
