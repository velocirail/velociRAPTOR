package com.joshuaharwood.velociraptor.raptor;

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
 * A journey is returned however many legs it takes. The range result factory used to drop journeys
 * of five or more rounds, so a destination only reachable with five trips came back empty from a
 * range query while the depart-after query found it.
 */
class LongJourneyTest {

  private static final LocalDate TEST_DATE = LocalDate.of(2018, 10, 16);

  /** A → B → C → D → E → F, one trip per hop, each connecting into the next. */
  private static final List<Trip> CHAIN = List.of(
    t(st("A", null, 1000), st("B", 1100, null)),
    t(st("B", null, 1200), st("C", 1300, null)),
    t(st("C", null, 1400), st("D", 1500, null)),
    t(st("D", null, 1600), st("E", 1700, null)),
    t(st("E", null, 1800), st("F", 1900, null))
  );

  private static final Journey FIVE_LEGS = j(
    List.of(st("A", null, 1000), st("B", 1100, null)),
    List.of(st("B", null, 1200), st("C", 1300, null)),
    List.of(st("C", null, 1400), st("D", 1500, null)),
    List.of(st("D", null, 1600), st("E", 1700, null)),
    List.of(st("E", null, 1800), st("F", 1900, null))
  );

  @Test
  void departAfterReturnsAFiveLegJourney() {
    var raptor = TestRaptorBuilder.create(CHAIN, Map.of(), new HashMap<>());

    var result = stripTrips(new DepartAfterQuery<>(raptor, new JourneyFactory()).plan(stop("A"), stop("F"), TEST_DATE, 900));

    assertThat(result).containsExactly(FIVE_LEGS);
  }

  @Test
  void rangeQueryReturnsAFiveLegJourney() {
    var raptor = TestRaptorBuilder.create(CHAIN, Map.of(), new HashMap<>());

    var result = stripTrips(new RangeQuery<>(raptor, new JourneyFactory()).plan(stop("A"), stop("F"), TEST_DATE, 900, 2000));

    assertThat(result).containsExactly(FIVE_LEGS);
  }
}
