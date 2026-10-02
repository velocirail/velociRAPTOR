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

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;

/** The simple-journey mapping's derived fields. Plain unit test - no Quarkus/S3. */
class RaptorControllerMappingTest {

  private static final LocalDate DATE = LocalDate.of(2025, 6, 1);

  private static TimetableLeg train(String agency, String uid, String from, int dep, PickupDropOffType pickup,
                                    String to, int arr, PickupDropOffType dropOff) {
    var stopTimes = List.of(
      new StopTime(new Stop(from), dep, dep, pickup, PickupDropOffType.NONE),
      new StopTime(new Stop(to), arr, arr, PickupDropOffType.NONE, dropOff));
    var trip = new RailTrip("t-" + uid, stopTimes, "svc", agency, uid);
    return new TimetableLeg(new Stop(from), new Stop(to), stopTimes, trip);
  }

  @Test
  void trainLinkTrainCarriesOperatorModeTypesAndSummary() {
    // 09:00 SDR→MYB, Tube to KGX, KGX→SVG: Chiltern then Great Northern, gb-transit-style "=" agency id on one.
    var first = train("=CH", "C12345", "SDR", 9 * 3600, PickupDropOffType.COORDINATE_WITH_DRIVER, "MYB", 9 * 3600 + 2400, PickupDropOffType.REGULAR);
    var tube = new TransferLeg(new Stop("MYB"), new Stop("KGX"), 900, 0, Integer.MAX_VALUE, 300, 600, "TUBE");
    var second = train("GN", "G67890", "KGX", 10 * 3600, PickupDropOffType.REGULAR, "SVG", 10 * 3600 + 1500, PickupDropOffType.REGULAR);
    var journey = new Journey(List.<Leg>of(first, tube, second), 9 * 3600, 10 * 3600 + 1500);

    // Minimum interchange: 5 min at Marylebone, 10 min at King's Cross, 3 min elsewhere.
    var simple = RaptorController.toSimpleJourney(journey, DATE, stop -> switch (stop.id()) {
      case "MYB" -> 300;
      case "KGX" -> 600;
      default -> 180;
    });

    assertThat(simple)
      .returns(OffsetDateTime.parse("2025-06-01T09:00:00+01:00"), from(SimpleJourney::departureTime))
      .returns(OffsetDateTime.parse("2025-06-01T10:25:00+01:00"), from(SimpleJourney::arrivalTime))
      .returns(Duration.ofMinutes(85), from(SimpleJourney::duration))
      .returns(1, from(SimpleJourney::changes));

    assertThat(simple.legs()).hasSize(3);
    assertThat(simple.legs().get(0))
      .returns(Duration.ofMinutes(40), from(SimpleLeg::duration))
      .returns(null, from(SimpleLeg::boardingInterchange))
      .returns("CH", from(SimpleLeg::operator))
      .returns(PickupDropOffType.COORDINATE_WITH_DRIVER, from(SimpleLeg::originPickUpType))
      .returns(PickupDropOffType.REGULAR, from(SimpleLeg::destinationDropOffType))
      .returns(null, from(SimpleLeg::mode));
    assertThat(simple.legs().get(1))
      .returns(Duration.ofMinutes(15), from(SimpleLeg::duration))
      .returns(Duration.ofMinutes(5), from(SimpleLeg::boardingInterchange))
      .returns("TUBE", from(SimpleLeg::mode))
      .returns(null, from(SimpleLeg::operator))
      .returns(null, from(SimpleLeg::originTrainUid))
      .returns(null, from(SimpleLeg::destinationTrainUid))
      .returns(null, from(SimpleLeg::originPickUpType));
    assertThat(simple.legs().get(2))
      .returns(Duration.ofMinutes(25), from(SimpleLeg::duration))
      .returns(Duration.ofMinutes(10), from(SimpleLeg::boardingInterchange))
      .returns("GN", from(SimpleLeg::operator));
  }

  @Test
  void operatorIsTheAtocCodeWithOrWithoutTheNocPrefix() {
    assertThat(RaptorController.operatorOf("GW")).isEqualTo("GW");
    assertThat(RaptorController.operatorOf("=GW")).isEqualTo("GW");
    assertThat(RaptorController.operatorOf(null)).isNull();
  }
}
