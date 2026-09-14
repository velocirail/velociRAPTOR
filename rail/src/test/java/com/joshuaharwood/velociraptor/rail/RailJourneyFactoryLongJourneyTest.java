package com.joshuaharwood.velociraptor.rail;

import com.joshuaharwood.velociraptor.raptor.model.ResultConnectionIndex;
import com.joshuaharwood.velociraptor.raptor.model.ResultConnectionIndex.ResultConnection;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.model.StopTime;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A journey is returned however many legs it takes. The factory used to drop journeys of five or
 * more rounds on both the range and the depart-after path, so {@code /first-arrival} returned nothing
 * for a pair only reachable with five trains.
 */
class RailJourneyFactoryLongJourneyTest {

  private static final LocalDate START = LocalDate.of(2025, 6, 1);

  private static RailTrip hop(String id, String from, int dep, String to, int arr) {
    return new RailTrip(id,
        List.of(new StopTime(new Stop(from), dep, dep, true, false), new StopTime(new Stop(to), arr, arr, false, true)),
        "svc", "agency", null);
  }

  /** A → B → C → D → E → F: the destination is reached at round 5 and nowhere earlier. */
  private static Map<Stop, Map<Integer, ResultConnectionIndex>> fiveRoundChain() {
    String[] stops = {"A", "B", "C", "D", "E", "F"};
    Map<Stop, Map<Integer, ResultConnectionIndex>> kConnections = new HashMap<>();
    for (int i = 1; i < stops.length; i++) {
      var trip = hop("t" + i, stops[i - 1], 1000 + 200 * (i - 1), stops[i], 1100 + 200 * (i - 1));
      kConnections.put(new Stop(stops[i]), Map.of(i, new ResultConnection(trip, 0, 1)));
    }
    return kConnections;
  }

  @Test
  void departAfterKeepsAFiveLegJourney() {
    var results = new RailJourneyFactory(START).getResults(fiveRoundChain(), new Stop("F"), null);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().legs()).hasSize(5);
  }

  @Test
  void rangeKeepsAFiveLegJourneyDepartingAtTheSearchTime() {
    var results = new RailJourneyFactory(START).getResults(fiveRoundChain(), new Stop("F"), 1000);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().legs()).hasSize(5);
  }
}
