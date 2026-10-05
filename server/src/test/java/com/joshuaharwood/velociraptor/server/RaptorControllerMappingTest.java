package com.joshuaharwood.velociraptor.server;

import com.joshuaharwood.velociraptor.rail.RailTrip;
import com.joshuaharwood.velociraptor.rail.StopDateTime;
import com.joshuaharwood.velociraptor.rail.TrainTrip;
import com.joshuaharwood.velociraptor.raptor.model.Leg;
import com.joshuaharwood.velociraptor.raptor.model.Leg.TimetableLeg;
import com.joshuaharwood.velociraptor.raptor.model.Leg.TransferLeg;
import com.joshuaharwood.velociraptor.raptor.model.PickupDropOffType;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.model.StopTime;
import com.joshuaharwood.velociraptor.raptor.result.Journey;
import com.joshuaharwood.velociraptor.server.http.dto.SimpleJourney;
import com.joshuaharwood.velociraptor.server.http.dto.SimpleLeg;
import org.jspecify.annotations.Nullable;
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

  private static TimetableLeg train(String agency, @Nullable String uid, String from, int dep, PickupDropOffType pickup,
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
  void aDetailJourneyRunsFromItsFirstLegToItsLastIncludingLinksAtEitherEnd() {
    // Tube 08:40-08:55 to EUS, the 09:05 train to MKC arriving 09:40, a walk 09:45-09:50 to the destination.
    // (rail's Leg and RailJourney share their names with the raptor model's and the DTO's, so are written in full.)
    var day = LocalDate.of(2026, 6, 3);
    var tube = new com.joshuaharwood.velociraptor.rail.Leg.FixedLink(new Stop("VIC"), new Stop("EUS"),
        day.atTime(8, 40), day.atTime(8, 55), 900, 0, 600, "TUBE");
    var stops = List.of(
        new StopDateTime(new Stop("EUS"), day.atTime(9, 5), day.atTime(9, 5), true, false,
                         PickupDropOffType.REGULAR, PickupDropOffType.NONE),
        new StopDateTime(new Stop("MKC"), day.atTime(9, 40), day.atTime(9, 40), false, true,
                         PickupDropOffType.NONE, PickupDropOffType.REGULAR));
    var train = new com.joshuaharwood.velociraptor.rail.Leg.RailLeg(new Stop("EUS"), new Stop("MKC"),
        day.atTime(9, 5), day.atTime(9, 40), "MA0905", "MA0905", new TrainTrip("t", stops, "svc", "=LM", "MA0905"), 0, 1);
    var walk = new com.joshuaharwood.velociraptor.rail.Leg.FixedLink(new Stop("MKC"), new Stop("XMK"),
        day.atTime(9, 45), day.atTime(9, 50), 300, 300, 0, "WALK");

    var detail = RaptorController.toRailJourney(
        new com.joshuaharwood.velociraptor.rail.RailJourney(new Stop("VIC"), new Stop("XMK"), List.of(tube, train, walk)),
        stop -> 0);

    assertThat(detail.departureTime()).isEqualTo(OffsetDateTime.parse("2026-06-03T08:40:00+01:00"));
    assertThat(detail.arrivalTime()).isEqualTo(OffsetDateTime.parse("2026-06-03T09:50:00+01:00"));
    assertThat(detail.duration()).isEqualTo(Duration.ofMinutes(70));
    assertThat(detail.changes()).isZero();
  }

  @Test
  void operatorIsTheAtocCodeWithOrWithoutTheNocPrefix() {
    assertThat(RaptorController.operatorOf("GW")).isEqualTo("GW");
    assertThat(RaptorController.operatorOf("=GW")).isEqualTo("GW");
  }
}
