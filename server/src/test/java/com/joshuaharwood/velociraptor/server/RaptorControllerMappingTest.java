package com.joshuaharwood.velociraptor.server;

import com.joshuaharwood.velociraptor.rail.Operator;
import com.joshuaharwood.velociraptor.rail.RailTrip;
import com.joshuaharwood.velociraptor.rail.Route;
import com.joshuaharwood.velociraptor.rail.StopDateTime;
import com.joshuaharwood.velociraptor.rail.TrainRun;
import com.joshuaharwood.velociraptor.rail.TrainTrip;
import com.joshuaharwood.velociraptor.rail.TransitMode;
import com.joshuaharwood.velociraptor.raptor.model.Leg;
import com.joshuaharwood.velociraptor.raptor.model.Leg.TimetableLeg;
import com.joshuaharwood.velociraptor.raptor.model.Leg.TransferLeg;
import com.joshuaharwood.velociraptor.raptor.model.PickupDropOffType;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.model.StopTime;
import com.joshuaharwood.velociraptor.raptor.result.Journey;
import com.joshuaharwood.velociraptor.server.http.dto.RailJourneyLeg;
import com.joshuaharwood.velociraptor.server.http.dto.RailStopDateTime;
import com.joshuaharwood.velociraptor.server.http.dto.SimpleJourney;
import com.joshuaharwood.velociraptor.server.http.dto.SimpleLeg;
import com.joshuaharwood.velociraptor.server.http.dto.TrainService;
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
  private static final Operator SOUTHERN =
      new Operator("SN", "=SN", "Southern", "https://www.southernrailway.com/", "0345 127 2920");
  private static final Route SOUTHERN_TRAINS = new Route("SN", null, "Southern", "8cc63e", "000000", null);
  private static final Route SOUTHERN_RRB = new Route("SN_RRB", null, "Southern", "8cc63e", "000000", null);

  private static TimetableLeg train(String agency, @Nullable String uid, String from, int dep, PickupDropOffType pickup,
                                    String to, int arr, PickupDropOffType dropOff) {
    var stopTimes = List.of(
      new StopTime(new Stop(from), dep, dep, pickup, PickupDropOffType.NONE),
      new StopTime(new Stop(to), arr, arr, PickupDropOffType.NONE, dropOff));
    var tripId = "t-" + from + dep;
    var trip = new RailTrip(tripId, stopTimes, List.of(new TrainRun(tripId, 0, uid, null)), null, TransitMode.RAIL,
                            new Operator(RailTrips.operatorOf(agency), agency, null, null, null),
                            new Route(agency, null, null, null, null, null), List.of(), List.of(),
                            List.of(), List.of());
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
      .returns("CH", from(rail -> rail.operator().code()))
      .returns(new TrainService("t-SDR32400", DATE, "C12345", null), from(SimpleLeg.RailLeg::originService))
      .returns(PickupDropOffType.COORDINATE_WITH_DRIVER, from(SimpleLeg.RailLeg::originPickUpType))
      .returns(PickupDropOffType.REGULAR, from(SimpleLeg.RailLeg::destinationDropOffType)));
    // A fixed link is its own type: it has no services, operator or pickup types to leave empty.
    assertThat(simple.legs().get(1)).isInstanceOfSatisfying(SimpleLeg.FixedLink.class, leg -> assertThat(leg)
      .returns(Duration.ofMinutes(15), from(SimpleLeg.FixedLink::duration))
      .returns(Duration.ofMinutes(5), from(SimpleLeg.FixedLink::boardingInterchange))
      .returns("TUBE", from(SimpleLeg.FixedLink::mode)));
    assertThat(simple.legs().get(2)).isInstanceOfSatisfying(SimpleLeg.RailLeg.class, leg -> assertThat(leg)
      .returns(Duration.ofMinutes(25), from(SimpleLeg.RailLeg::duration))
      .returns(Duration.ofMinutes(10), from(SimpleLeg.RailLeg::boardingInterchange))
      .returns("GN", from(rail -> rail.operator().code())));
  }

  @Test
  void aTrainWithNoUidStillHasAServiceWithANullUid() {
    // A TfL trip in a gb-transit rail and TfL feed has no train UID: the leg is still a rail leg.
    var tube = train("LUL", null, "VIC", 9 * 3600, PickupDropOffType.REGULAR, "EUS", 9 * 3600 + 600, PickupDropOffType.REGULAR);
    var simple = RaptorController.toSimpleJourney(new Journey(List.<Leg>of(tube), 9 * 3600, 9 * 3600 + 600), DATE, stop -> 0);

    assertThat(simple.legs()).singleElement().isInstanceOfSatisfying(SimpleLeg.RailLeg.class, leg -> assertThat(leg)
      .returns(null, from(rail -> rail.originService().trainUid()))
      .returns("t-VIC32400", from(rail -> rail.originService().tripId()))
      .returns("LUL", from(rail -> rail.operator().code())));
  }

  @Test
  void aDetailJourneyRunsFromItsFirstLegToItsLastIncludingLinksAtEitherEnd() {
    // Tube 08:40-08:55 to EUS, the 09:05 train to MKC arriving 09:40, a walk 09:45-09:50 to the destination.
    // (rail's Leg and RailJourney share their names with the raptor model's and the DTO's, so are written in full.)
    var day = LocalDate.of(2026, 6, 3);
    var MA0905 = new com.joshuaharwood.velociraptor.rail.TrainService("MA0905_20260601_20260628", day, "MA0905", null);
    var tube = new com.joshuaharwood.velociraptor.rail.Leg.FixedLink(new Stop("VIC"), new Stop("EUS"),
        day.atTime(8, 40), day.atTime(8, 55), 900, 0, 600, "TUBE");
    var stops = List.of(
        new StopDateTime(new Stop("EUS"), day.atTime(9, 5), day.atTime(9, 5), true, false,
                         PickupDropOffType.REGULAR, PickupDropOffType.NONE, null, null),
        new StopDateTime(new Stop("MKC"), day.atTime(9, 40), day.atTime(9, 40), false, true,
                         PickupDropOffType.NONE, PickupDropOffType.REGULAR, null, null));
    var train = new com.joshuaharwood.velociraptor.rail.Leg.RailLeg(new Stop("EUS"), new Stop("MKC"),
        day.atTime(9, 5), day.atTime(9, 40), MA0905, MA0905,
        new TrainTrip(stops, List.of(MA0905), null, TransitMode.RAIL,
                      new Operator("LM", "=LM", "West Midlands Railway", null, null),
                      new Route("LM", null, "West Midlands Railway", "ff8200", "000000", null), List.of()), 0, 1);
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
  void aTrainLegCarriesItsTripsHeadsignModeOperatorNameAndThePlatformsAtEitherEnd() {
    // A replacement bus calling at three stops, boarded at the second and left at the third; the feed names a
    // platform (here a bus stop letter) at the first two only.
    var stopTimes = List.of(
      new StopTime(new Stop("BTN"), 9 * 3600, 9 * 3600, PickupDropOffType.REGULAR, PickupDropOffType.NONE),
      new StopTime(new Stop("HHE"), 9 * 3600 + 1200, 9 * 3600 + 1200, PickupDropOffType.REGULAR, PickupDropOffType.REGULAR),
      new StopTime(new Stop("GTW"), 9 * 3600 + 2400, 9 * 3600 + 2400, PickupDropOffType.NONE, PickupDropOffType.REGULAR));
    var platforms = java.util.Arrays.<@Nullable String>asList("5", "B", null);
    var trip = new RailTrip("t", stopTimes, List.of(new TrainRun("t", 0, "W12345", "SN123400")), "Gatwick Airport",
                            TransitMode.REPLACEMENT_BUS, SOUTHERN, SOUTHERN_RRB, platforms, List.of(), List.of(),
                            List.of());
    var leg = new TimetableLeg(new Stop("HHE"), new Stop("GTW"), stopTimes.subList(1, 3), trip);

    var simple = RaptorController.toSimpleJourney(new Journey(List.<Leg>of(leg), 9 * 3600 + 1200, 9 * 3600 + 2400), DATE,
                                                  stop -> 0);

    assertThat(simple.legs()).singleElement().isInstanceOfSatisfying(SimpleLeg.RailLeg.class, rail -> assertThat(rail)
      .returns(new TrainService("t", DATE, "W12345", "SN123400"), from(SimpleLeg.RailLeg::originService))
      .returns("Gatwick Airport", from(SimpleLeg.RailLeg::headsign))
      .returns(TransitMode.REPLACEMENT_BUS, from(SimpleLeg.RailLeg::transitMode))
      .returns(new com.joshuaharwood.velociraptor.server.http.dto.Operator("SN", "=SN", "Southern",
                                                                             "https://www.southernrailway.com/",
                                                                             "0345 127 2920"),
               from(SimpleLeg.RailLeg::operator))
      .returns(new com.joshuaharwood.velociraptor.server.http.dto.Route("SN_RRB", null, "Southern", "8cc63e", "000000",
                                                                         null),
               from(SimpleLeg.RailLeg::route))
      .returns("B", from(SimpleLeg.RailLeg::originPlatform))
      .returns(null, from(SimpleLeg.RailLeg::destinationPlatform)));
  }

  @Test
  void aDetailLegCarriesThePlatformsAtEitherEndAndItsTripsHeadsignModeAndOperatorName() {
    var day = LocalDate.of(2026, 6, 3);
    var service = new com.joshuaharwood.velociraptor.rail.TrainService("W12345_20260601_20260628", day, "W12345",
                                                                       "SN123400");
    var stops = List.of(
        new StopDateTime(new Stop("BTN"), day.atTime(9, 0), day.atTime(9, 0), true, false,
                         PickupDropOffType.REGULAR, PickupDropOffType.NONE, "5", null),
        new StopDateTime(new Stop("VIC"), day.atTime(10, 0), day.atTime(10, 0), false, true,
                         PickupDropOffType.NONE, PickupDropOffType.REGULAR, "15", null));
    var train = new com.joshuaharwood.velociraptor.rail.Leg.RailLeg(new Stop("BTN"), new Stop("VIC"),
        day.atTime(9, 0), day.atTime(10, 0), service, service,
        new TrainTrip(stops, List.of(service), "London Victoria", TransitMode.RAIL, SOUTHERN, SOUTHERN_TRAINS,
                      List.of()), 0, 1);

    var detail = RaptorController.toRailJourney(
        new com.joshuaharwood.velociraptor.rail.RailJourney(new Stop("BTN"), new Stop("VIC"), List.of(train)),
        stop -> 0);

    assertThat(detail.legs()).singleElement().isInstanceOfSatisfying(RailJourneyLeg.RailLeg.class, leg -> {
      assertThat(leg.originPlatform()).isEqualTo("5");
      assertThat(leg.destinationPlatform()).isEqualTo("15");
      assertThat(leg.operator().name()).isEqualTo("Southern");
      assertThat(leg.operator().phone()).isEqualTo("0345 127 2920");
      assertThat(leg.route().id()).isEqualTo("SN");
      assertThat(leg.route().colour()).isEqualTo("8cc63e");
      assertThat(leg.transitMode()).isEqualTo(TransitMode.RAIL);
      assertThat(leg.trainTrip().headsign()).isEqualTo("London Victoria");
      var expected = new TrainService("W12345_20260601_20260628", day, "W12345", "SN123400");
      assertThat(leg.trainTrip().services()).containsExactly(expected);
      assertThat(leg.originService()).isEqualTo(expected);
      assertThat(leg.destinationService()).isEqualTo(expected);
      assertThat(leg.trainTrip().stopTimes()).map(RailStopDateTime::platform).containsExactly("5", "15");
    });
  }
}
