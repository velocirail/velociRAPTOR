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

  private static TimetableLeg train(String agency, @org.jspecify.annotations.Nullable String uid, String from, int dep, PickupDropOffType pickup,
                                    String to, int arr, PickupDropOffType dropOff) {
    var stopTimes = List.of(
      new StopTime(new Stop(from), dep, dep, pickup, PickupDropOffType.NONE),
      new StopTime(new Stop(to), arr, arr, PickupDropOffType.NONE, dropOff));
    var trip = new RailTrip("t-" + from + dep, stopTimes, "svc", agency, uid);
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
    assertThat(simple.legs().get(0)).isInstanceOfSatisfying(SimpleLeg.RailLeg.class, leg -> assertThat(leg)
      .returns(Duration.ofMinutes(40), from(SimpleLeg.RailLeg::duration))
      .returns(null, from(SimpleLeg.RailLeg::boardingInterchange))
      .returns("CH", from(SimpleLeg.RailLeg::operator))
      .returns("C12345", from(SimpleLeg.RailLeg::originTrainUid))
      .returns(PickupDropOffType.COORDINATE_WITH_DRIVER, from(SimpleLeg.RailLeg::originPickUpType))
      .returns(PickupDropOffType.REGULAR, from(SimpleLeg.RailLeg::destinationDropOffType)));
    // A fixed link is its own type: it has no train UIDs, operator or pickup types to leave empty.
    assertThat(simple.legs().get(1)).isInstanceOfSatisfying(SimpleLeg.FixedLink.class, leg -> assertThat(leg)
      .returns(Duration.ofMinutes(15), from(SimpleLeg.FixedLink::duration))
      .returns(Duration.ofMinutes(5), from(SimpleLeg.FixedLink::boardingInterchange))
      .returns("TUBE", from(SimpleLeg.FixedLink::mode)));
    assertThat(simple.legs().get(2)).isInstanceOfSatisfying(SimpleLeg.RailLeg.class, leg -> assertThat(leg)
      .returns(Duration.ofMinutes(25), from(SimpleLeg.RailLeg::duration))
      .returns(Duration.ofMinutes(10), from(SimpleLeg.RailLeg::boardingInterchange))
      .returns("GN", from(SimpleLeg.RailLeg::operator)));
  }

  @Test
  void aTrainWithNoUidStillCarriesTheFieldAsNull() {
    // A TfL trip in a gb-transit rail and TfL feed has no train UID: the leg is still a rail leg.
    var tube = train("LUL", null, "VIC", 9 * 3600, PickupDropOffType.REGULAR, "EUS", 9 * 3600 + 600, PickupDropOffType.REGULAR);
    var simple = RaptorController.toSimpleJourney(new Journey(List.<Leg>of(tube), 9 * 3600, 9 * 3600 + 600), DATE, stop -> 0);

    assertThat(simple.legs()).singleElement().isInstanceOfSatisfying(SimpleLeg.RailLeg.class, leg -> assertThat(leg)
      .returns(null, from(SimpleLeg.RailLeg::originTrainUid))
      .returns(null, from(SimpleLeg.RailLeg::destinationTrainUid))
      .returns("LUL", from(SimpleLeg.RailLeg::operator)));
  }

  @Test
  void operatorIsTheAtocCodeWithOrWithoutTheNocPrefix() {
    assertThat(RaptorController.operatorOf("GW")).isEqualTo("GW");
    assertThat(RaptorController.operatorOf("=GW")).isEqualTo("GW");
    assertThat(RaptorController.operatorOf(null)).isNull();
  }
}
