package com.joshuaharwood.velociraptor.raptor;

import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.model.Transfer;
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

import static com.joshuaharwood.velociraptor.raptor.TestData.j;
import static com.joshuaharwood.velociraptor.raptor.TestData.st;
import static com.joshuaharwood.velociraptor.raptor.TestData.stop;
import static com.joshuaharwood.velociraptor.raptor.TestData.stripTrips;
import static com.joshuaharwood.velociraptor.raptor.TestData.t;
import static com.joshuaharwood.velociraptor.raptor.TestData.tf;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stops the timetable does not call at: an unknown one yields no journeys rather than a crash
 * (BUGS.md 1.2), one at the end of a footpath is a reachable destination, and a journey that is only
 * a footpath is not returned (BUGS.md 1.3).
 */
class FootpathDestinationTest {

  private static final LocalDate TEST_DATE = LocalDate.of(2018, 10, 16);

  /** A calls at A and B only; W is reached from B by a 10 minute walk, and from A by a 3 hour one. */
  private static final List<Trip> TRIPS = List.of(
    t(st("A", null, 1000), st("B", 1600, null)),
    t(st("A", null, 2000), st("B", 2600, null))
  );

  private static Map<Stop, List<Transfer>> transfers() {
    return Map.of(
      stop("B"), List.of(new Transfer(stop("B"), stop("W"), 600, 0, Integer.MAX_VALUE, null)),
      stop("A"), List.of(new Transfer(stop("A"), stop("W"), 10_800, 0, Integer.MAX_VALUE, null))
    );
  }

  private static List<Journey> departAfter(String origin, String destination, int time) {
    var raptor = TestRaptorBuilder.create(TRIPS, transfers(), new HashMap<>());
    return stripTrips(new DepartAfterQuery<>(raptor, new JourneyFactory()).plan(stop(origin), stop(destination), TEST_DATE, time));
  }

  private static List<Journey> range(String origin, String destination) {
    var raptor = TestRaptorBuilder.create(TRIPS, transfers(), new HashMap<>());
    return stripTrips(new RangeQuery<>(raptor, new JourneyFactory()).plan(stop(origin), stop(destination), TEST_DATE, null, null));
  }

  @Test
  void unknownOriginYieldsNoJourneys() {
    assertThat(departAfter("Z", "B", 900)).isEmpty();
    assertThat(range("Z", "B")).isEmpty();
  }

  @Test
  void unknownDestinationYieldsNoJourneys() {
    assertThat(departAfter("A", "Z", 900)).isEmpty();
    assertThat(range("A", "Z")).isEmpty();
  }

  @Test
  void aStopReachedOnlyByFootpathIsADestination() {
    var viaB = j(List.of(st("A", null, 1000), st("B", 1600, null)), tf("B", "W", 600));

    assertThat(departAfter("A", "W", 900)).containsExactly(viaB);
  }

  @Test
  void rangeQueryReachesAFootpathOnlyDestinationFromEveryDeparture() {
    assertThat(range("A", "W")).containsExactly(
      j(List.of(st("A", null, 1000), st("B", 1600, null)), tf("B", "W", 600)),
      j(List.of(st("A", null, 2000), st("B", 2600, null)), tf("B", "W", 600))
    );
  }

  /**
   * From A at 1700 the 2000 train reaches W at 3200, and the direct walk reaches it at 12_500. The
   * walk is a journey with fewer legs, but it rides nothing, so it is not returned.
   */
  @Test
  void aJourneyThatIsOnlyAFootpathIsNotReturned() {
    var result = departAfter("A", "W", 1700);

    assertThat(result).containsExactly(
      j(List.of(st("A", null, 2000), st("B", 2600, null)), tf("B", "W", 600))
    );
  }

  @Test
  void aFootpathOnlyStopCanBeWalkedOnFrom() {
    var trips = List.of(t(st("A", null, 1000), st("B", 1600, null)));
    var transfers = Map.of(
      stop("B"), List.of(new Transfer(stop("B"), stop("W"), 600, 0, Integer.MAX_VALUE, null)),
      stop("W"), List.of(new Transfer(stop("W"), stop("X"), 300, 0, Integer.MAX_VALUE, null))
    );
    var raptor = TestRaptorBuilder.create(trips, transfers, new HashMap<>());

    var result = stripTrips(new DepartAfterQuery<>(raptor, new JourneyFactory()).plan(stop("A"), stop("X"), TEST_DATE, 900));

    assertThat(result).containsExactly(
      j(List.of(st("A", null, 1000), st("B", 1600, null)), tf("B", "W", 600), tf("W", "X", 300))
    );
  }
}
