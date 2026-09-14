package com.joshuaharwood.velociraptor.raptor;

import com.joshuaharwood.velociraptor.raptor.query.RangeQuery;
import com.joshuaharwood.velociraptor.raptor.result.Journey;
import com.joshuaharwood.velociraptor.raptor.result.JourneyFactory;
import com.joshuaharwood.velociraptor.raptor.model.Leg;
import com.joshuaharwood.velociraptor.raptor.model.Leg.TimetableLeg;
import com.joshuaharwood.velociraptor.raptor.model.Leg.TransferLeg;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.joshuaharwood.velociraptor.raptor.TestData.st;
import static com.joshuaharwood.velociraptor.raptor.TestData.stop;
import static com.joshuaharwood.velociraptor.raptor.TestData.t;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression for the journey-reconstruction NPE on the {@code /} and {@code /detail} range endpoints.
 * <p>
 * In range mode the retained {@code kArrivals} labels (rRAPTOR) can give a boarding stop an arrival at
 * round {@code i-1} that was carried forward from another departure (or the round-0 origin seed) with
 * no connection recorded at {@code i-1} in this departure's index. The back-walk then dereferenced a
 * missing connection and threw.
 * <p>
 * This timetable was found by a differential search and reproduces the crash end-to-end (departure
 * 2100 boards C at round 2 to reach D, but C's own connection lives only in the 2400 departure). The
 * <em>same</em> timetable throws {@code TypeError: Cannot read properties of undefined (reading
 * 'origin')} in the TS reference ({@code RaptorTimeRangeQuery} &rarr; its detailed journey factory), so the
 * fragility is shared with the source - the Java port merely surfaced it one frame earlier as an NPE.
 */
class RangeQueryDanglingPointerTest {

  private static final LocalDate TEST_DATE = LocalDate.of(2018, 10, 16);

  @Test
  void rangeQueryOverARetainedLabelTimetableDoesNotThrowAndStartsAtTheOrigin() {
    var trips = List.of(
      t(st("A", null, 2400), st("B", 2711, 2711), st("C", 3030, null)),
      t(st("B", null, 2600), st("C", 3080, 3080), st("D", 3185, null)),
      t(st("A", null, 2100), st("B", 2581, null)),
      t(st("B", null, 2600), st("C", 2867, 2867), st("D", 3268, null)),
      t(st("B", null, 1400), st("C", 1704, 1704), st("D", 1950, null))
    );

    var raptor = TestRaptorBuilder.create(trips, Map.of(), new HashMap<>());
    var query = new RangeQuery<>(raptor, new JourneyFactory());

    // Window [1800, 2700) covers both A departures (2100 and 2400). Pre-fix this threw an NPE while
    // reconstructing the 2100 departure.
    List<Journey> result = query.plan(stop("A"), stop("D"), TEST_DATE, 1800, 2700);

    // Every reconstructed journey must begin at the queried origin - never at a carried-forward
    // intermediate stop (the upper-bound filter drops any such truncated journey).
    assertThat(result).isNotNull();
    for (Journey journey : result) {
      assertThat(firstOrigin(journey)).isEqualTo(stop("A"));
    }
  }

  private static Object firstOrigin(Journey journey) {
    Leg first = journey.legs().getFirst();
    return switch (first) {
      case TimetableLeg tl -> tl.origin();
      case TransferLeg t -> t.origin();
    };
  }
}
