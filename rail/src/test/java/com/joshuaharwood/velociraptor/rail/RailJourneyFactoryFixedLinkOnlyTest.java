package com.joshuaharwood.velociraptor.rail;

import com.joshuaharwood.velociraptor.raptor.model.Leg.TransferLeg;
import com.joshuaharwood.velociraptor.raptor.model.ResultConnectionIndex;
import com.joshuaharwood.velociraptor.raptor.model.ResultConnectionIndex.ResultConnection;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.model.StopTime;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** A journey made only of fixed links (a walk, a Tube ride, a ferry) rides no train and is not returned. */
class RailJourneyFactoryFixedLinkOnlyTest {

  private static final LocalDate START = LocalDate.of(2025, 6, 1);
  private static final Stop A = new Stop("A");
  private static final Stop B = new Stop("B");
  private static final Stop W = new Stop("W");

  private static TransferLeg walk(Stop from, Stop to) {
    return new TransferLeg(from, to, 600, 0, Integer.MAX_VALUE, 0, 0, null);
  }

  @Test
  void aJourneyThatIsOnlyAFixedLinkIsNotReturned() {
    // W is reached at round 1 by walking from the origin A, which has no connection of its own.
    Map<Stop, Map<Integer, ResultConnectionIndex>> kConnections = Map.of(W, Map.of(1, walk(A, W)));

    assertThat(new RailJourneyFactory(START).getResults(kConnections, W, null)).isEmpty();
  }

  @Test
  void aJourneyEndingWithAFixedLinkIsReturned() {
    var trip = new RailTrip("t1",
        List.of(new StopTime(A, 1000, 1000, true, false), new StopTime(B, 1600, 1600, false, true)),
        "svc", "agency", null);
    Map<Stop, Map<Integer, ResultConnectionIndex>> kConnections = Map.of(
        B, Map.of(1, new ResultConnection(trip, 0, 1)),
        W, Map.of(2, walk(B, W)));

    var results = new RailJourneyFactory(START).getResults(kConnections, W, null);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().legs()).hasSize(2);
    assertThat(results.getFirst().legs().getLast()).isInstanceOf(Leg.FixedLink.class);
  }
}
