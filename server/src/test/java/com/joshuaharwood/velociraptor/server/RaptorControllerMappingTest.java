package com.joshuaharwood.velociraptor.server;

import com.joshuaharwood.velociraptor.rail.RailTrip;
import com.joshuaharwood.velociraptor.raptor.model.Leg;
import com.joshuaharwood.velociraptor.raptor.model.Leg.TimetableLeg;
import com.joshuaharwood.velociraptor.raptor.model.Leg.TransferLeg;
import com.joshuaharwood.velociraptor.raptor.model.PickupDropOffType;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.model.StopTime;
import com.joshuaharwood.velociraptor.raptor.result.Journey;
import com.joshuaharwood.velociraptor.server.http.dto.SimpleJourney;
import com.joshuaharwood.velociraptor.server.http.dto.SimpleLeg;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;

/**
 * The range endpoint's mapping, pinned to the shape the production server sends and its consumer reads: legs only,
 * six fields each, empty-string train UIDs on a fixed link. Plain unit test - no Quarkus/S3.
 */
class RaptorControllerMappingTest {

  private static final LocalDate DATE = LocalDate.of(2025, 6, 1);

  private static TimetableLeg train(String uid, String from, int dep, String to, int arr) {
    var stopTimes = List.of(
      new StopTime(new Stop(from), dep, dep, PickupDropOffType.REGULAR, PickupDropOffType.NONE),
      new StopTime(new Stop(to), arr, arr, PickupDropOffType.NONE, PickupDropOffType.REGULAR));
    var trip = new RailTrip("t-" + uid, stopTimes, "svc", "XX", uid);
    return new TimetableLeg(new Stop(from), new Stop(to), stopTimes, trip);
  }

  @Test
  void trainLinkTrainIsMappedToTheProductionShape() {
    // 09:00 SDR-MYB, Tube to KGX with 5 min interchange at MYB, KGX-SVG at 10:00.
    var first = train("C12345", "SDR", 9 * 3600, "MYB", 9 * 3600 + 2400);
    var tube = new TransferLeg(new Stop("MYB"), new Stop("KGX"), 900, 0, Integer.MAX_VALUE, 300, 600, "TUBE");
    var second = train("G67890", "KGX", 10 * 3600, "SVG", 10 * 3600 + 1500);
    var journey = new Journey(List.<Leg>of(first, tube, second), 9 * 3600, 10 * 3600 + 1500);

    SimpleJourney simple = RaptorController.toSimpleJourney(journey, DATE);

    assertThat(simple.legs()).hasSize(3);
    assertThat(simple.legs().get(0))
      .returns("SDR", from(SimpleLeg::origin))
      .returns("MYB", from(SimpleLeg::destination))
      .returns(OffsetDateTime.parse("2025-06-01T09:00:00+01:00"), from(SimpleLeg::departureTime))
      .returns(OffsetDateTime.parse("2025-06-01T09:40:00+01:00"), from(SimpleLeg::arrivalTime))
      .returns("C12345", from(SimpleLeg::originTrainUid))
      .returns("C12345", from(SimpleLeg::destinationTrainUid));
    assertThat(simple.legs().get(1))
      .returns("MYB", from(SimpleLeg::origin))
      .returns("KGX", from(SimpleLeg::destination))
      .returns(OffsetDateTime.parse("2025-06-01T09:45:00+01:00"), from(SimpleLeg::departureTime))
      .returns(OffsetDateTime.parse("2025-06-01T10:00:00+01:00"), from(SimpleLeg::arrivalTime))
      .returns("", from(SimpleLeg::originTrainUid))
      .returns("", from(SimpleLeg::destinationTrainUid));
    assertThat(simple.legs().get(2))
      .returns("G67890", from(SimpleLeg::originTrainUid));
  }

  @Test
  void theResponseRecordsCarryOnlyTheProductionFields() {
    assertThat(Arrays.stream(SimpleJourney.class.getRecordComponents()).map(c -> c.getName()))
      .containsExactly("legs");
    assertThat(Arrays.stream(SimpleLeg.class.getRecordComponents()).map(c -> c.getName()))
      .containsExactly("origin", "destination", "departureTime", "arrivalTime", "originTrainUid", "destinationTrainUid");
  }
}
