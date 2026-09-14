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
 * Regression for BUGS.md 1.1 / upstream planarnetwork/raptor #52: the trip-scan position must be
 * reset for every traversal of a route, not carried across rounds.
 * <p>
 * Route R calls A, M, B, C with trips T1 (A 08:00, M 08:30, B 09:00, C 10:00) and T2 (A 12:00,
 * M 12:30, B 13:00, C 14:00). Another route reaches B at 08:30 in round 1, so round 2 scans R from B,
 * boards T1 and leaves the scan position on T1. A 2h footpath B to A then marks A at 10:30, so round
 * 3 scans R from A. With a stale position the sweep starts at T1, reads its 08:00 departure from A,
 * breaks and finds no trip; T2 (catchable, 12:00) is never examined and the only journey to M is lost.
 */
class RouteScannerResetTest {

  private static final LocalDate TEST_DATE = LocalDate.of(2018, 10, 16);

  private static final int T0800 = 8 * 3600;
  private static final int T0830 = T0800 + 1800;
  private static final int T0900 = 9 * 3600;
  private static final int T1000 = 10 * 3600;
  private static final int T1200 = 12 * 3600;
  private static final int T1230 = T1200 + 1800;
  private static final int T1300 = 13 * 3600;
  private static final int T1400 = 14 * 3600;
  private static final int TWO_HOURS = 2 * 3600;

  private static List<Trip> trips() {
    return List.of(
      t(st("A", null, T0800), st("M", T0830, T0830), st("B", T0900, T0900), st("C", T1000, null)),
      t(st("A", null, T1200), st("M", T1230, T1230), st("B", T1300, T1300), st("C", T1400, null)),
      t(st("X", null, T0800), st("B", T0830, null))
    );
  }

  private static Map<Stop, List<Transfer>> transfers() {
    return Map.of(stop("B"), List.of(new Transfer(stop("B"), stop("A"), TWO_HOURS, 0, Integer.MAX_VALUE, null)));
  }

  private static Journey expected() {
    return j(
      List.of(st("X", null, T0800), st("B", T0830, null)),
      tf("B", "A", TWO_HOURS),
      List.of(st("A", null, T1200), st("M", T1230, T1230))
    );
  }

  @Test
  void departAfterFindsTheLaterTripWhenARouteIsReenteredUpstream() {
    var raptor = TestRaptorBuilder.create(trips(), transfers(), new HashMap<>());
    var query = new DepartAfterQuery(raptor, new JourneyFactory());
    var result = stripTrips(query.plan(stop("X"), stop("M"), TEST_DATE, T0800));

    assertThat(result).containsExactly(expected());
  }

  @Test
  void rangeQueryFindsTheLaterTripWhenARouteIsReenteredUpstream() {
    var raptor = TestRaptorBuilder.create(trips(), transfers(), new HashMap<>());
    var query = new RangeQuery(raptor, new JourneyFactory());
    var result = stripTrips(query.plan(stop("X"), stop("M"), TEST_DATE, null, null));

    assertThat(result).containsExactly(expected());
  }
}
