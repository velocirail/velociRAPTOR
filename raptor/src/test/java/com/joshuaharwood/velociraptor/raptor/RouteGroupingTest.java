package com.joshuaharwood.velociraptor.raptor;

import com.joshuaharwood.velociraptor.raptor.model.Trip;
import com.joshuaharwood.velociraptor.raptor.query.DepartAfterQuery;
import com.joshuaharwood.velociraptor.raptor.result.JourneyFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.joshuaharwood.velociraptor.raptor.TestData.j;
import static com.joshuaharwood.velociraptor.raptor.TestData.st;
import static com.joshuaharwood.velociraptor.raptor.TestData.stop;
import static com.joshuaharwood.velociraptor.raptor.TestData.stripTrips;
import static com.joshuaharwood.velociraptor.raptor.TestData.t;
import static com.joshuaharwood.velociraptor.raptor.TestData.via;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * BUGS.md 2.3 / upstream planarnetwork/raptor #51: a trip that overtakes another at any call must
 * not share its route, and the trips diverted off a route must be ordered among themselves too.
 */
class RouteGroupingTest {

  private static final LocalDate TEST_DATE = LocalDate.of(2018, 10, 16);

  /** Assigns the trips in the order given and returns the route id of each. */
  private static List<String> assign(List<Trip> trips) {
    Map<String, List<Trip>> tripsByRoute = new HashMap<>();
    List<String> routeIds = new ArrayList<>();
    for (Trip trip : trips) {
      String routeId = RouteGrouping.routeIdFor(trip.stopTimes(), tripsByRoute);
      tripsByRoute.computeIfAbsent(routeId, _ -> new ArrayList<>()).add(trip);
      routeIds.add(routeId);
    }
    return routeIds;
  }

  @Test
  void tripsOrderedAtEveryCallShareARoute() {
    var ids = assign(List.of(
      t(st("A", null, 1000), st("B", 1030, 1030), st("C", 1100, null)),
      t(st("A", null, 1100), st("B", 1130, 1130), st("C", 1200, null))
    ));

    assertThat(ids.get(0)).isEqualTo(ids.get(1)).isEqualTo("A10,B11,C01");
  }

  @Test
  void differentPickUpFlagsAreDifferentRoutes() {
    var ids = assign(List.of(
      t(st("A", null, 1000), st("B", 1030, 1030), st("C", 1100, null)),
      t(st("A", null, 1100), via("B", 1130, 1130, false, true), st("C", 1200, null))
    ));

    assertThat(ids.get(0)).isNotEqualTo(ids.get(1));
  }

  @Test
  void aTripOvertakingAtAnIntermediateCallOnlyIsSplitOff() {
    // T2 leaves A later, overtakes at B and C, and is caught up again before D: its final arrival
    // is later, which is all the old test looked at.
    var ids = assign(List.of(
      t(st("A", null, 1000), st("B", 1030, 1030), st("C", 1100, 1100), st("D", 1200, null)),
      t(st("A", null, 1010), st("B", 1025, 1025), st("C", 1050, 1050), st("D", 1210, null))
    ));

    assertThat(ids.get(0)).isNotEqualTo(ids.get(1));
  }

  @Test
  void tripsDivertedOffARouteAreOrderedAmongThemselves() {
    // T2 and T3 both overtake T1; T3 also overtakes T2, so each needs a route of its own.
    var ids = assign(List.of(
      t(st("A", null, 1000), st("B", 1100, null)),
      t(st("A", null, 1010), st("B", 1050, null)),
      t(st("A", null, 1020), st("B", 1040, null))
    ));

    assertThat(ids).doesNotHaveDuplicates();
  }

  @Test
  void aDivertedTripJoinsTheFirstRouteItDoesNotOvertake() {
    // T3 overtakes T1 but not T2, so it belongs with T2 rather than on a third route.
    var ids = assign(List.of(
      t(st("A", null, 1000), st("B", 1100, null)),
      t(st("A", null, 1010), st("B", 1050, null)),
      t(st("A", null, 1020), st("B", 1055, null))
    ));

    assertThat(ids.get(0)).isNotEqualTo(ids.get(1));
    assertThat(ids.get(2)).isEqualTo(ids.get(1));
  }

  @Test
  void plansTheTripThatOvertakesAtAnIntermediateCall() {
    // Same trips as aTripOvertakingAtAnIntermediateCallOnlyIsSplitOff. On one route the sweep at
    // B walks back from T2 (dep 1025) to T1 (dep 1030) and boards T1, arriving C at 1100; the
    // 1050 arrival on T2 is never found.
    var trips = List.of(
      t(st("A", null, 1000), st("B", 1030, 1030), st("C", 1100, 1100), st("D", 1200, null)),
      t(st("A", null, 1010), st("B", 1025, 1025), st("C", 1050, 1050), st("D", 1210, null))
    );

    var raptor = TestRaptorBuilder.create(trips, Map.of(), new HashMap<>());
    var query = new DepartAfterQuery(raptor, new JourneyFactory());
    var result = stripTrips(query.plan(stop("B"), stop("C"), TEST_DATE, 900));

    assertThat(result).containsExactly(j(List.of(st("B", 1025, 1025), st("C", 1050, 1050))));
  }

  @Test
  void plansTheFastestOfThreeMutuallyOvertakingTrips() {
    // Old grouping: T1 alone, T2 and T3 together on "overtakes" in the wrong order, so a sweep from
    // A at 1005 walks back from T3 to T2 and boards T2 (arr 1050) instead of T3 (arr 1040).
    var trips = List.of(
      t(st("A", null, 1000), st("B", 1100, null)),
      t(st("A", null, 1010), st("B", 1050, null)),
      t(st("A", null, 1020), st("B", 1040, null))
    );

    var raptor = TestRaptorBuilder.create(trips, Map.of(), new HashMap<>());
    var query = new DepartAfterQuery(raptor, new JourneyFactory());
    var result = stripTrips(query.plan(stop("A"), stop("B"), TEST_DATE, 1005));

    assertThat(result).containsExactly(j(List.of(st("A", null, 1020), st("B", 1040, null))));
  }
}
