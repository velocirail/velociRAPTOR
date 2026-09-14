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

import static com.joshuaharwood.velociraptor.raptor.TestData.st;
import static com.joshuaharwood.velociraptor.raptor.TestData.stop;
import static com.joshuaharwood.velociraptor.raptor.TestData.t;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;

/**
 * A stop's minimum interchange applies only between legs. It never delays the start of the first
 * leg (the traveller is already at the origin) and never extends the end of the last (the journey
 * is over on arrival). The scan folds it into the alighting label, so this pins that the folded
 * value never leaks into a reported time.
 */
class InterchangeAtJourneyEndsTest {

  private static final LocalDate TEST_DATE = LocalDate.of(2018, 10, 16);
  private static final int INTERCHANGE = 300;

  /** O→A→D direct at 1000→2000; A→B 1000→1500 then B→D 1900→2500; links O→Y (100 s) and D→Z (100 s). */
  private static final List<Trip> TRIPS = List.of(
    t(st("O", null, 1000), st("D", 2000, null)),
    t(st("O", null, 1000), st("B", 1500, null)),
    t(st("B", null, 1900), st("D", 2500, null)),
    t(st("Y", null, 1000), st("E", 3000, null))
  );
  private static final Map<Stop, List<Transfer>> LINKS = Map.of(
    stop("O"), List.of(new Transfer(stop("O"), stop("Y"), 100, 0, Integer.MAX_VALUE, null)),
    stop("D"), List.of(new Transfer(stop("D"), stop("Z"), 100, 0, Integer.MAX_VALUE, null))
  );

  private static RaptorAlgorithm raptor() {
    Map<Stop, Integer> interchange = new HashMap<>();
    for (String id : List.of("O", "A", "B", "D", "Y", "E", "Z")) {
      interchange.put(stop(id), INTERCHANGE);
    }
    return TestRaptorBuilder.create(TRIPS, LINKS, interchange);
  }

  @Test
  void aDirectJourneyReportsTheTrainsOwnTimes() {
    // Standing at O at 1000 exactly: the 1000 departure is caught, no interchange first.
    var journeys = new DepartAfterQuery<>(raptor(), new JourneyFactory()).plan(stop("O"), stop("D"), TEST_DATE, 1000);

    assertThat(journeys).hasSize(1);
    assertThat(journeys.getFirst())
      .returns(1000, from(Journey::departureTime))
      .returns(2000, from(Journey::arrivalTime));
  }

  @Test
  void aChangeNeedsTheInterchangeButTheEndsDoNot() {
    // B→D leaves at 1900 and arrives 2500, later than the direct train, so it is only returned when
    // it is the only way: forbid the direct train's stop D as a via? Simpler: query O→D at 1000 with
    // the direct trip absent and check the times.
    var trips = List.of(TRIPS.get(1), TRIPS.get(2));
    Map<Stop, Integer> interchange = new HashMap<>();
    for (String id : List.of("O", "B", "D")) {
      interchange.put(stop(id), INTERCHANGE);
    }
    var raptor = TestRaptorBuilder.create(trips, Map.of(), interchange);

    var journeys = new DepartAfterQuery<>(raptor, new JourneyFactory()).plan(stop("O"), stop("D"), TEST_DATE, 1000);

    assertThat(journeys).hasSize(1);
    assertThat(journeys.getFirst())
      .returns(1000, from(Journey::departureTime))
      .returns(2500, from(Journey::arrivalTime));

    // Arrive B at 1500, interchange 300: a train leaving B at 1799 would be missed, 1800 caught.
    var tight = TestRaptorBuilder.create(List.of(TRIPS.get(1), t(st("B", null, 1799), st("D", 2400, null))), Map.of(), interchange);
    assertThat(new DepartAfterQuery<>(tight, new JourneyFactory()).plan(stop("O"), stop("D"), TEST_DATE, 1000)).isEmpty();
    var justMade = TestRaptorBuilder.create(List.of(TRIPS.get(1), t(st("B", null, 1800), st("D", 2400, null))), Map.of(), interchange);
    assertThat(new DepartAfterQuery<>(justMade, new JourneyFactory()).plan(stop("O"), stop("D"), TEST_DATE, 1000)).hasSize(1);
  }

  @Test
  void aTrailingLinkAddsTheInterchangeAtItsOriginOnly() {
    // Direct train reaches D at 2000; interchange at D 300, link 100: arrive Z at 2400, and the
    // interchange at Z itself is not added.
    var journeys = new DepartAfterQuery<>(raptor(), new JourneyFactory()).plan(stop("O"), stop("Z"), TEST_DATE, 1000);

    assertThat(journeys).hasSize(1);
    assertThat(journeys.getFirst())
      .returns(1000, from(Journey::departureTime))
      .returns(2400, from(Journey::arrivalTime));
  }

  @Test
  void aLeadingLinkAddsTheInterchangeAtItsDestinationOnly() {
    // The train leaves Y at 1000 and Y's interchange is 300: the link (100 s) must end by 700, so the
    // journey leaves O at 600. O's own interchange is not added at the start.
    var journeys = new RangeQuery<>(raptor(), new JourneyFactory()).plan(stop("O"), stop("E"), TEST_DATE, 0, 1000);

    assertThat(journeys).hasSize(1);
    assertThat(journeys.getFirst())
      .returns(600, from(Journey::departureTime))
      .returns(3000, from(Journey::arrivalTime));
  }
}
