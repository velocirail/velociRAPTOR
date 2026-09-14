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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.joshuaharwood.velociraptor.raptor.TestData.j;
import static com.joshuaharwood.velociraptor.raptor.TestData.st;
import static com.joshuaharwood.velociraptor.raptor.TestData.stop;
import static com.joshuaharwood.velociraptor.raptor.TestData.stripTrips;
import static com.joshuaharwood.velociraptor.raptor.TestData.t;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A range query returns no journey that another journey in the same result dominates: leaves no
 * earlier, arrives no later, uses no more legs, and differs in at least one. rRAPTOR guarantees this
 * by keeping the per-round labels between departures, so an earlier departure only records an
 * arrival that beats what a later departure already achieved. The fixed-link relaxation used to
 * ignore those retained labels (BUGS.md 6.2): once a later departure had labelled the destination,
 * the route scan for an earlier one correctly declined to re-record the same arrival, which left the
 * per-departure best arrival unset, and a fixed link into the destination then recorded whatever it
 * arrived at - and overwrote the retained label with it, weakening the bound for every departure
 * after.
 */
class RangeDominationTest {

  private static final LocalDate TEST_DATE = LocalDate.of(2018, 10, 16);
  private static final int CASES = 2000;

  /**
   * O→W→Y is the best journey (1000→1030). Leaving O at 800 for Z and taking the fixed link Z→Y
   * arrives 1120; leaving at 600 via V arrives 1110. Both leave earlier and arrive later, so neither
   * is returned. The bug recorded the 800 link (best arrival unset for that departure), which raised
   * the retained round-2 label at Y to 1120, and the 600 departure's V→Y then beat that.
   */
  @Test
  void aFixedLinkIntoTheDestinationDoesNotBeatARetainedLabel() {
    List<Trip> trips = List.of(
      t(st("O", null, 1000), st("W", 1010, null)),
      t(st("W", null, 1015), st("Y", 1030, null)),
      t(st("O", null, 800), st("Z", 820, null)),
      t(st("O", null, 600), st("V", 700, null)),
      t(st("V", null, 700), st("Y", 1110, null))
    );
    Map<Stop, List<Transfer>> transfers = Map.of(
      stop("Z"), List.of(new Transfer(stop("Z"), stop("Y"), 300, 0, Integer.MAX_VALUE, null))
    );
    var raptor = TestRaptorBuilder.create(trips, transfers, new HashMap<>());

    var result = stripTrips(new RangeQuery<>(raptor, new JourneyFactory()).plan(stop("O"), stop("Y"), TEST_DATE, 0, 2000));

    assertThat(result).containsExactly(
      j(List.of(st("O", null, 1000), st("W", 1010, null)), List.of(st("W", null, 1015), st("Y", 1030, null)))
    );
  }

  @Test
  void rangeResultsAreNeverDominatedWithinTheResult() {
    for (int seed = 0; seed < CASES; seed++) {
      checkCase(seed, 0);
    }
  }

  @Test
  void rangeResultsAreNeverDominatedWithinTheResultWithInterchange() {
    for (int seed = 0; seed < CASES; seed++) {
      checkCase(seed, 300);
    }
  }

  /**
   * Completeness: whatever a depart-after query finds from any time inside the window, the range
   * query offers something at least as good - leaving no earlier, arriving no later, with no more
   * legs. Run with the contiguous-link rule on in both queries, since the range seeds look one link
   * out from the origin (as the paper's Ψ does) and a journey beginning with two links in a row is
   * the one shape that rule removes.
   */
  @Test
  void rangeResultsCoverEveryDepartAfterJourney() {
    var rules = new FixedLinkRules(false, false, true);
    for (int seed = 0; seed < CASES; seed++) {
      var random = new Random(seed);
      var data = ScanOracleTest.Timetable.random(random, random.nextBoolean() ? 0 : 300);
      var origin = data.stops().get(random.nextInt(data.stops().size()));
      var destination = data.stops().get(random.nextInt(data.stops().size()));
      if (origin.equals(destination)) {
        continue;
      }
      int start = random.nextInt(30_000);
      int end = start + 1 + random.nextInt(30_000);
      int at = start + random.nextInt(end - start);

      var raptor = TestRaptorBuilder.create(data.trips(), data.transfers(), new HashMap<>(data.interchange()));
      List<Journey> range = new RangeQuery<>(raptor, new JourneyFactory(), rules).plan(origin, destination, TEST_DATE, start, end);
      List<Journey> departAfter = new DepartAfterQuery<>(raptor, new JourneyFactory(), rules).plan(origin, destination, TEST_DATE, at);

      for (Journey journey : departAfter) {
        if (journey.departureTime() < start || journey.departureTime() >= end) {
          continue;
        }
        boolean covered = range.stream().anyMatch(r -> r.departureTime() >= journey.departureTime()
          && r.arrivalTime() <= journey.arrivalTime()
          && r.legs().size() <= journey.legs().size());
        assertThat(covered)
          .as("seed %d, %s -> %s in [%d, %d): depart-after at %d found %s, range offers %s", seed, origin.id(), destination.id(),
              start, end, at, describe(journey), range.stream().map(RangeDominationTest::describe).toList())
          .isTrue();
      }
    }
  }

  private static void checkCase(int seed, int interchangeTime) {
    var random = new Random(seed);
    var data = ScanOracleTest.Timetable.random(random, interchangeTime);
    var origin = data.stops().get(random.nextInt(data.stops().size()));
    var destination = data.stops().get(random.nextInt(data.stops().size()));
    if (origin.equals(destination)) {
      return;
    }
    int start = random.nextInt(30_000);
    int end = start + random.nextInt(30_000);

    var raptor = TestRaptorBuilder.create(data.trips(), data.transfers(), new HashMap<>(data.interchange()));
    List<Journey> journeys = new RangeQuery<>(raptor, new JourneyFactory()).plan(origin, destination, TEST_DATE, start, end);

    List<String> dominated = new ArrayList<>();
    for (Journey a : journeys) {
      for (Journey b : journeys) {
        if (a != b && dominates(b, a)) {
          dominated.add(describe(a) + " is dominated by " + describe(b));
        }
      }
    }
    assertThat(dominated)
      .as("seed %d, %s -> %s in [%d, %d)", seed, origin.id(), destination.id(), start, end)
      .isEmpty();
  }

  /** b dominates a: b leaves no earlier, arrives no later, has no more legs, and differs somewhere. */
  private static boolean dominates(Journey b, Journey a) {
    return b.departureTime() >= a.departureTime()
      && b.arrivalTime() <= a.arrivalTime()
      && b.legs().size() <= a.legs().size()
      && (b.departureTime() > a.departureTime() || b.arrivalTime() < a.arrivalTime() || b.legs().size() < a.legs().size());
  }

  private static String describe(Journey journey) {
    return journey.departureTime() + "->" + journey.arrivalTime() + " (" + journey.legs().size() + " legs)";
  }
}
