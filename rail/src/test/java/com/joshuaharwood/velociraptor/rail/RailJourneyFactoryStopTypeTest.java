package com.joshuaharwood.velociraptor.rail;

import com.joshuaharwood.velociraptor.raptor.model.PickupDropOffType;
import com.joshuaharwood.velociraptor.raptor.model.ResultConnectionIndex;
import com.joshuaharwood.velociraptor.raptor.model.ResultConnectionIndex.ResultConnection;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.model.StopTime;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The GTFS pickup and drop-off types travel from the timetable into each rendered call. */
class RailJourneyFactoryStopTypeTest {

  private static final LocalDate START = LocalDate.of(2025, 6, 1);
  private static final Stop A = new Stop("A");
  private static final Stop R = new Stop("R");

  @Test
  void eachCallCarriesItsPickUpAndDropOffTypes() {
    var trip = new RailTrip("t1",
        List.of(new StopTime(A, 1000, 1000, PickupDropOffType.REGULAR, PickupDropOffType.NONE),
                new StopTime(R, 1600, 1600, PickupDropOffType.COORDINATE_WITH_DRIVER, PickupDropOffType.COORDINATE_WITH_DRIVER)),
        "svc", "agency", null);
    Map<Stop, Map<Integer, ResultConnectionIndex>> kConnections = Map.of(R, Map.of(1, new ResultConnection(trip, 0, 1)));

    var journey = new RailJourneyFactory(START).getResults(kConnections, R, null).getFirst();
    var leg = (Leg.RailLeg) journey.legs().getFirst();
    var calls = leg.trainTrip().stopTimes();

    assertThat(calls.get(0).pickUpType()).isEqualTo(PickupDropOffType.REGULAR);
    assertThat(calls.get(0).dropOffType()).isEqualTo(PickupDropOffType.NONE);
    assertThat(calls.get(0).isDropOff()).isFalse();
    assertThat(calls.get(1).pickUpType()).isEqualTo(PickupDropOffType.COORDINATE_WITH_DRIVER);
    assertThat(calls.get(1).dropOffType()).isEqualTo(PickupDropOffType.COORDINATE_WITH_DRIVER);
    assertThat(calls.get(1).isDropOff()).as("a request stop still counts as a drop-off").isTrue();
  }
}
