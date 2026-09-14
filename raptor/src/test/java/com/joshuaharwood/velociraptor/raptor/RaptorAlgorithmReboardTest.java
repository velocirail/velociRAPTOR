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
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The re-board step of the route scan: at each stop, if the previous round's label is at or before
 * the held trip's departure, look up the earliest trip departing at or after that label and switch
 * to it only when it is an earlier trip than the one held.
 * <p>
 * Three behaviours pinned here, each of which the strict port of the TS reference got wrong (BUGS.md 1.4, 1.5):
 * a lookup that finds nothing keeps the held trip rather than clearing it; a label equal to the held
 * trip's arrival still triggers the lookup; and a label inside the held trip's dwell both improves
 * the stop and catches a trip that arrived before the held one.
 */
class RaptorAlgorithmReboardTest {

  private static final LocalDate TEST_DATE = LocalDate.of(2018, 10, 16);

  private static List<Journey> departAfter(List<Trip> trips, Map<Stop, Integer> interchange,
                                           String origin, String destination, int time) {
    var raptor = TestRaptorBuilder.create(trips, Map.<Stop, List<Transfer>>of(), interchange);
    return stripTrips(new DepartAfterQuery<>(raptor, new JourneyFactory()).plan(stop(origin), stop(destination), TEST_DATE, time));
  }

  private static List<Journey> range(List<Trip> trips, Map<Stop, Integer> interchange,
                                     String origin, String destination) {
    var raptor = TestRaptorBuilder.create(trips, Map.<Stop, List<Transfer>>of(), interchange);
    return stripTrips(new RangeQuery<>(raptor, new JourneyFactory()).plan(stop(origin), stop(destination), TEST_DATE, null, null));
  }

  /**
   * Formerly RaptorAlgorithmReboardParityTest, which asserted the opposite: the TS reference reassigned the
   * trip unconditionally, so a lookup that found nothing cleared the trip already boarded and every
   * stop after it lost that trip's arrivals.
   * <p>
   * R0 reaches A at 1000 and X at 1050, which with X's 600 interchange is a 1650 label. Round 2
   * boards R1 at A (dep 1000) and reaches X at 1100. X's label 1650 is after R1 departs X (1100),
   * so nothing below R1 can be caught there and R1 is kept; it then alights at D at 1200, ahead of
   * the direct R2 at 1300.
   */
  @Test
  void aFailedReboardLookupKeepsTheHeldTrip() {
    var r0 = t(st("O", null, 900), st("A", 1000, 1000), st("X", 1050, null));
    var r1 = t(st("A", null, 1000), st("X", 1100, 1100), st("D", 1200, null));
    var r2 = t(st("O", null, 905), st("D", 1300, null));

    Map<Stop, Integer> interchange = new HashMap<>();
    interchange.put(stop("X"), 600);

    var result = departAfter(List.of(r0, r1, r2), interchange, "O", "D", 800);

    assertThat(result).containsExactlyInAnyOrder(
      j(List.of(st("O", null, 905), st("D", 1300, null))),
      j(List.of(st("O", null, 900), st("A", 1000, 1000)),
        List.of(st("A", null, 1000), st("X", 1100, 1100), st("D", 1200, null)))
    );
  }

  /**
   * BUGS.md 1.5. Trips A and B share a route and A is earlier at every call, but A dwells at Q2 from
   * 1200 to 1400 while B arrives at 1300. From P at 1020 round 1 boards B and labels Q1 1150,
   * Q2 1300, R 1600. Round 2 enters the route at Q1, boards B again and reaches Q2 at 1300, equal to
   * the label there. A strict comparison never looked for another trip; the label is at or before
   * B's departure (1420), the lookup finds A departing at 1400, and A reaches R at 1500.
   */
  @Test
  void catchesATripStillDwellingWhenTheLabelEqualsTheHeldTripsArrival() {
    var a = t(st("P", null, 1000), st("Q1", 1100, 1110), st("Q2", 1200, 1400), st("R", 1500, null));
    var b = t(st("P", null, 1050), st("Q1", 1150, 1160), st("Q2", 1300, 1420), st("R", 1600, null));

    var expectedDirect = j(List.of(st("P", null, 1050), st("Q1", 1150, 1160), st("Q2", 1300, 1420), st("R", 1600, null)));
    var expectedChange = j(
      List.of(st("P", null, 1050), st("Q1", 1150, 1160), st("Q2", 1300, 1420)),
      List.of(st("Q2", 1200, 1400), st("R", 1500, null))
    );

    assertThat(departAfter(List.of(a, b), new HashMap<>(), "P", "R", 1020))
      .containsExactlyInAnyOrder(expectedDirect, expectedChange);
    assertThat(range(List.of(a, b), new HashMap<>(), "P", "R"))
      .contains(expectedChange);
  }

  /**
   * The label at Q2 (1350, from the direct C2) lies inside B's dwell there (1300 to 1420). B improves
   * Q2 to 1300 and, in the same visit, A is still catchable from 1350 (departs 1400) and reaches R at
   * 1500 where B would reach it at 1600. An else-branch after the alight check, or a comparison with
   * the held trip's arrival instead of its departure, misses the change.
   */
  @Test
  void catchesATripFromALabelInsideTheHeldTripsDwellAfterImprovingTheStop() {
    var c1 = t(st("O", null, 900), st("Q1", 1140, null));
    var c2 = t(st("O", null, 905), st("Q2", 1350, null));
    var a = t(st("P", null, 1000), st("Q1", 1100, 1110), st("Q2", 1200, 1400), st("R", 1500, null));
    var b = t(st("P", null, 1050), st("Q1", 1150, 1160), st("Q2", 1300, 1420), st("R", 1600, null));

    var result = departAfter(List.of(c1, c2, a, b), new HashMap<>(), "O", "R", 800);

    assertThat(result).containsExactly(
      j(List.of(st("O", null, 905), st("Q2", 1350, null)),
        List.of(st("Q2", 1200, 1400), st("R", 1500, null)))
    );
  }
}
