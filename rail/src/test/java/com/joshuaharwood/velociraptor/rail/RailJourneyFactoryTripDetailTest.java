package com.joshuaharwood.velociraptor.rail;

import com.joshuaharwood.velociraptor.raptor.model.ResultConnectionIndex;
import com.joshuaharwood.velociraptor.raptor.model.ResultConnectionIndex.ResultConnection;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.model.StopTime;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** A trip's retail service ID, headsign, mode, operator and platforms travel from the feed into the rendered trip and its calls. */
class RailJourneyFactoryTripDetailTest {

  private static final LocalDate START = LocalDate.of(2025, 6, 1);
  private static final Stop A = new Stop("A");
  private static final Stop B = new Stop("B");
  private static final Stop C = new Stop("C");

  private static final Operator SOUTHERN =
      new Operator("SN", "=SN", "Southern", "https://www.southernrailway.com/", "0345 127 2920");

  private static final List<StopTime> CALLS = List.of(new StopTime(A, 1000, 1000, true, false),
                                                      new StopTime(B, 1300, 1300, true, true),
                                                      new StopTime(C, 1600, 1600, false, true));

  @Test
  void eachCallCarriesThePlatformAtTheSameIndexAndTheTripItsDetail() {
    var trip = new RailTrip("t1", CALLS, "svc", "W12345", "SN123400", "Seaford", TransitMode.REPLACEMENT_BUS,
                            SOUTHERN, Arrays.<@Nullable String>asList("5", null, "1F"));

    var leg = legOf(trip);

    assertThat(leg.trainTrip().stopTimes()).map(StopDateTime::platform).containsExactly("5", null, "1F");
    assertThat(leg.trainTrip().retailServiceId()).isEqualTo("SN123400");
    assertThat(leg.trainTrip().headsign()).isEqualTo("Seaford");
    assertThat(leg.trainTrip().mode()).isEqualTo(TransitMode.REPLACEMENT_BUS);
    assertThat(leg.trainTrip().operator()).isEqualTo(SOUTHERN);
  }

  @Test
  void aTripWhoseFeedNamesNoPlatformsHasNoneAtAnyCall() {
    var trip = new RailTrip("t1", CALLS, "svc", null, null, null, TransitMode.RAIL,
        new Operator("XX", "XX", null, null, null), List.of());

    assertThat(legOf(trip).trainTrip().stopTimes()).map(StopDateTime::platform).containsOnlyNulls();
  }

  @Test
  void theModeIsTheRouteTypeBasicOrExtended() {
    assertThat(TransitMode.fromGtfs(2)).isEqualTo(TransitMode.RAIL);
    assertThat(TransitMode.fromGtfs(1)).isEqualTo(TransitMode.SUBWAY);
    assertThat(TransitMode.fromGtfs(714)).isEqualTo(TransitMode.REPLACEMENT_BUS);
    assertThat(TransitMode.fromGtfs(1100)).isEqualTo(TransitMode.AIR);
    assertThat(TransitMode.fromGtfs(109)).isEqualTo(TransitMode.OTHER);
  }

  private static Leg.RailLeg legOf(RailTrip trip) {
    Map<Stop, Map<Integer, ResultConnectionIndex>> kConnections = Map.of(C, Map.of(1, new ResultConnection(trip, 0, 2)));
    return (Leg.RailLeg) new RailJourneyFactory(START).getResults(kConnections, C, null).getFirst().legs().getFirst();
  }
}
