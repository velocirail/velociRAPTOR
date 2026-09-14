package com.joshuaharwood.velociraptor.server;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.virtual.ShouldNotPin;
import io.quarkus.test.junit.virtual.VirtualThreadUnit;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests against the artificial sample feed in {@code fixtures/gtfs-sample} (see its README for the
 * network). Every time below is invented there, so the assertions are exact.
 * <p>
 * Reference trip: 1020900 (UID WB0900) - the 09:00 Southern fast BTN -> HHE -> GTW -> ECR -> VIC, arriving 10:00,
 * Mon-Sat. 2026-06-03 is a Wednesday with the full timetable.
 */
@QuarkusTest
@QuarkusTestResource(LocalStackS3Resource.class)

@TestMethodOrder(OrderAnnotation.class)
@VirtualThreadUnit // Use the extension
@ShouldNotPin // Detect pinned carrier thread
class RaptorResourceTest {

  private static final String ORIG = "BTN";
  private static final String DEST = "VIC";
  // A Wednesday in the feed window with no calendar exception.
  private static final String DATE = "2026-06-03";
  // All endpoints serialise leg/stop times as Europe/London OffsetDateTime (SIRI/Transmodel style):
  // local wall-clock plus offset. June is BST, so +01:00.
  private static final String TRIP_DEPARTURE = DATE + "T09:00:00+01:00";
  private static final String TRIP_ARRIVAL = DATE + "T10:00:00+01:00";
  // trainUid is the train UID from GTFS trip_headsign, distinct from the numeric trip_id.
  private static final String TRIP_ID = "1020900";
  private static final String TRAIN_UID = "WB0900";

  @Test
  void rangeQuery_returnsJourneysForKnownService() {
    // 08:30-09:30: the 08:40 stopper reaches VIC after the 09:00 fast and is dominated; the 09:10 stopper stays
    // because the next fast is at 10:00; and the 09:20 Thameslink overtakes that stopper, so changing onto it at
    // ECR departs later for the same arrival and is a third Pareto-optimal journey.
    given()
            .queryParam("orig", ORIG)
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + "T08:30:00")
            .queryParam("endDate", DATE + "T09:30:00")
            .when().get("/")
            .then()
            .statusCode(200)
            .body("size()", is(3))
            .body("[0].legs", hasSize(1))
            .body("[0].legs[0].origin",        is(ORIG))
            .body("[0].legs[0].destination",   is(DEST))
            .body("[0].legs[0].departureTime", is(TRIP_DEPARTURE))
            .body("[0].legs[0].arrivalTime",   is(TRIP_ARRIVAL))
            .body("[0].legs[0].originTrainUid", is(TRAIN_UID))
            .body("[0].legs[0].destinationTrainUid", is(TRAIN_UID))
            .body("[0].legs[0].originPickUpType", is("REGULAR"))
            .body("[0].legs[0].destinationDropOffType", is("REGULAR"))
            .body("[0].legs[0].operator", is("SN"))
            .body("[0].legs[0].mode", nullValue())
            .body("[0].legs[0].duration", is("PT1H"))
            .body("[0].legs[0].boardingInterchange", nullValue())
            .body("[0].departureTime", is(TRIP_DEPARTURE))
            .body("[0].arrivalTime", is(TRIP_ARRIVAL))
            .body("[0].duration", is("PT1H"))
            .body("[0].changes", is(0))
            .body("[1].legs[0].departureTime", is(DATE + "T09:10:00+01:00"))
            .body("[1].legs[0].arrivalTime", is(DATE + "T10:35:00+01:00"))
            .body("[1].changes", is(0))
            .body("[2].legs", hasSize(2))
            .body("[2].legs[0].operator", is("TL"))
            .body("[2].legs[0].destination", is("ECR"))
            .body("[2].legs[1].origin", is("ECR"))
            .body("[2].legs[1].boardingInterchange", is("PT5M"))
            .body("[2].departureTime", is(DATE + "T09:20:00+01:00"))
            .body("[2].arrivalTime", is(DATE + "T10:35:00+01:00"))
            .body("[2].changes", is(1));
  }

  @Test
  void rangeQuery_acceptsTheWindowAsInstants() {
    given()
            .queryParam("orig", ORIG)
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + "T07:30:00.000Z")
            .queryParam("endDate", DATE + "T08:30:00.000Z")
            .when().get("/")
            .then()
            .statusCode(200)
            .body("size()", is(3))
            .body("[0].departureTime", is(TRIP_DEPARTURE))
            .body("[0].arrivalTime", is(TRIP_ARRIVAL))
            .body("[1].departureTime", is(DATE + "T09:10:00+01:00"))
            .body("[2].departureTime", is(DATE + "T09:20:00+01:00"));
  }

  @Test
  void rangeQuery_acceptsItsOwnOutputFormat() {
    given()
            .queryParam("orig", ORIG)
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + "T08:30:00+01:00")
            .queryParam("endDate", DATE + "T09:30:00+01:00")
            .when().get("/")
            .then()
            .statusCode(200)
            .body("size()", is(3))
            .body("[0].departureTime", is(TRIP_DEPARTURE));
  }

  @Test
  void rangeQuery_returns400ProblemForAMalformedDate() {
    given()
            .queryParam("orig", ORIG)
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + " 08:30")
            .queryParam("endDate", DATE + "T09:30:00+01:00")
            .when().get("/")
            .then()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("detail", startsWith("startDate=" + DATE + " 08:30 "));
  }

  @Test
  void rangeQuery_allJourneysDepartWithinRequestedWindow() {
    given()
            .queryParam("orig", ORIG)
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + "T08:30:00")
            .queryParam("endDate", DATE + "T11:30:00")
            .when().get("/")
            .then()
            .statusCode(200)
            .body("size()", greaterThan(2))
            .body("departureTime", everyItem(greaterThanOrEqualTo(DATE + "T08:30:00")))
            .body("departureTime", everyItem(lessThanOrEqualTo(DATE + "T11:30:00")));
  }

  @Test
  void rangeQuery_returnsEmptyListOnDateWithNoService() {
    // The feed window opens on 2026-06-01, so nothing runs on 2026-05-01.
    given()
            .queryParam("orig", ORIG)
            .queryParam("dest", DEST)
            .queryParam("startDate", "2026-05-01T08:00:00")
            .queryParam("endDate", "2026-05-01T23:59:00")
            .when().get("/")
            .then()
            .statusCode(200)
            .body("size()", is(0));
  }

  @Test
  void rangeQuery_returns400WhenOriginIsInNotVia() {
    given()
            .queryParam("orig", ORIG)
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + "T08:30:00")
            .queryParam("endDate", DATE + "T09:30:00")
            .queryParam("notVia", ORIG)
            .when().get("/")
            .then()
            .statusCode(400);
  }

  @Test
  void rangeQuery_returns400WhenDestinationIsInNotVia() {
    given()
            .queryParam("orig", ORIG)
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + "T08:30:00")
            .queryParam("endDate", DATE + "T09:30:00")
            .queryParam("notVia", DEST)
            .when().get("/")
            .then()
            .statusCode(400);
  }

  @Test
  void rangeQuery_excludesJourneysViaNotViaStop() {
    // Every train from BTN to VIC calls at GTW, and the only other way in (a Tube link) may not end a journey.
    given()
            .queryParam("orig", ORIG)
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + "T08:30:00")
            .queryParam("endDate", DATE + "T09:30:00")
            .queryParam("notVia", "GTW")
            .when().get("/")
            .then()
            .statusCode(200)
            .body("size()", is(0));
  }

  @Test
  void rangeQuery_excludesJourneysViaMultipleNotViaStops() {
    given()
            .queryParam("orig", ORIG)
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + "T08:30:00")
            .queryParam("endDate", DATE + "T09:30:00")
            .queryParam("notVia", "GTW")
            .queryParam("notVia", "HHE")
            .when().get("/")
            .then()
            .statusCode(200)
            .body("size()", is(0));
  }

  @Test
  void rangeQuery_notViaRemovesOnlyTheTrainsThatCallThere() {
    // Only the stoppers call at Clapham Junction, so ruling it out leaves the 09:00 fast alone.
    given()
            .queryParam("orig", ORIG)
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + "T08:30:00")
            .queryParam("endDate", DATE + "T09:30:00")
            .queryParam("notVia", "CLJ")
            .when().get("/")
            .then()
            .statusCode(200)
            .body("size()", is(1))
            .body("[0].legs[0].departureTime", is(TRIP_DEPARTURE))
            .body("legs.flatten().origin", not(hasItem("CLJ")))
            .body("legs.flatten().destination", not(hasItem("CLJ")));
  }

  @Test
  void detail_returnsJourneysForKnownService() {
    given()
            .queryParam("orig", ORIG)
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + "T08:30:00")
            .queryParam("endDate", DATE + "T09:30:00")
            .when().get("/detail")
            .then()
            .statusCode(200)
            .body("size()", is(3))
            .body("[0].origin", is(ORIG))
            .body("[0].destination", is(DEST))
            .body("[0].legs", hasSize(1))
            .body("[0].legs[0].origin", is(ORIG))
            .body("[0].legs[0].departureTime", is(TRIP_DEPARTURE))
            .body("[0].legs[0].arrivalTime", is(TRIP_ARRIVAL))
            .body("[0].legs[0].originTrainUid", is(TRAIN_UID))
            .body("[0].legs[0].trainTrip.tripId", is(TRIP_ID))
            .body("[0].legs[0].trainTrip.trainUid", is(TRAIN_UID))
            .body("[0].legs[0].originPickUpType", is("REGULAR"))
            .body("[0].legs[0].destinationDropOffType", is("REGULAR"))
            .body("[0].legs[0].trainTrip.stopTimes[0].pickUpType", is("REGULAR"))
            .body("[0].legs[0].trainTrip.stopTimes[0].pickUp", is(true))
            .body("[0].legs[0].trainTrip.stopTimes[0].dropOffType", is("NONE"))
            .body("[0].legs[0].trainTrip.stopTimes[0].dropOff", is(false))
            .body("[0].legs[0].trainTrip.stopTimes", hasSize(5))
            .body("[0].legs[0].type", is("RAIL_LEG"))
            .body("[0].legs[0].operator", is("SN"))
            .body("[0].legs[0].duration", is("PT1H"))
            .body("[0].legs[0].boardingInterchange", nullValue())
            .body("[0].departureTime", is(TRIP_DEPARTURE))
            .body("[0].arrivalTime", is(TRIP_ARRIVAL))
            .body("[0].duration", is("PT1H"))
            .body("[0].changes", is(0));
  }

  @Test
  void detail_acceptsTheWindowAsInstants() {
    given()
            .queryParam("orig", ORIG)
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + "T07:30:00Z")
            .queryParam("endDate", DATE + "T08:30:00Z")
            .when().get("/detail")
            .then()
            .statusCode(200)
            .body("size()", is(3))
            .body("[0].legs[0].departureTime", is(TRIP_DEPARTURE))
            .body("[0].legs[0].arrivalTime", is(TRIP_ARRIVAL));
  }

  @Test
  void detail_excludesJourneysViaNotViaStop() {
    // Not-via is applied inline: the scan never rides through CLJ, so no returned leg starts or ends there.
    given()
            .queryParam("orig", ORIG)
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + "T08:30:00")
            .queryParam("endDate", DATE + "T09:30:00")
            .queryParam("notVia", "CLJ")
            .when().get("/detail")
            .then()
            .statusCode(200)
            .body("size()", is(1))
            .body("legs.flatten().origin", not(hasItem("CLJ")))
            .body("legs.flatten().destination", not(hasItem("CLJ")));
  }

  @Test
  void detail_returns400WhenOriginIsInNotVia() {
    given()
            .queryParam("orig", ORIG)
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + "T08:30:00")
            .queryParam("endDate", DATE + "T09:30:00")
            .queryParam("notVia", ORIG)
            .when().get("/detail")
            .then()
            .statusCode(400);
  }

  @Test
  void detail_returns400WhenDestinationIsInNotVia() {
    given()
            .queryParam("orig", ORIG)
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + "T08:30:00")
            .queryParam("endDate", DATE + "T09:30:00")
            .queryParam("notVia", DEST)
            .when().get("/detail")
            .then()
            .statusCode(400);
  }

  @Test
  void firstArrival_returnsSoonestJourney() {
    // first-arrival returns one journey per round in which the destination improved. From 08:30 the one-train
    // answer is the 09:00 fast (the 08:40 stopper arrives five minutes later).
    given()
            .queryParam("orig", ORIG)
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + "T08:30:00")
            .when().get("/first-arrival")
            .then()
            .statusCode(200)
            .body("size()", greaterThan(0))
            .body("legs[0].departureTime", hasItem(TRIP_DEPARTURE))
            .body("legs[0].arrivalTime", hasItem(TRIP_ARRIVAL));
  }

  @Test
  void firstArrival_acceptsTheStartAsAnInstant() {
    given()
            .queryParam("orig", ORIG)
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + "T07:30:00Z")
            .when().get("/first-arrival")
            .then()
            .statusCode(200)
            .body("size()", greaterThan(0))
            .body("legs[0].departureTime", hasItem(TRIP_DEPARTURE));
  }

  @Test
  void firstArrival_excludesJourneysViaNotViaStop() {
    given()
            .queryParam("orig", ORIG)
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + "T08:30:00")
            .queryParam("notVia", "GTW")
            .when().get("/first-arrival")
            .then()
            .statusCode(200)
            .body("size()", is(0));
  }

  @Test
  void firstArrival_returns400WhenOriginIsInNotVia() {
    given()
            .queryParam("orig", ORIG)
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + "T08:30:00")
            .queryParam("notVia", ORIG)
            .when().get("/first-arrival")
            .then()
            .statusCode(400);
  }

  @Test
  void firstArrival_returns400WhenDestinationIsInNotVia() {
    given()
            .queryParam("orig", ORIG)
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + "T08:30:00")
            .queryParam("notVia", DEST)
            .when().get("/first-arrival")
            .then()
            .statusCode(400);
  }

  @Test
  void firstArrival_findsNothingAfterTheLastTrainOfTheDay() {
    // The last Wednesday departure from BTN towards VIC is the 22:40 stopper; the owl is Saturday only.
    given()
            .queryParam("orig", ORIG)
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + "T23:00:00")
            .when().get("/first-arrival")
            .then()
            .statusCode(200)
            .body("size()", is(0));
  }

  // --- After-midnight (GTFS >24h) ---
  // Trip 1052415 (UID WE2415) is the Saturday night owl VIC -> ECR -> GTW -> HHE -> BTN. It departs VIC at
  // 24:15:00 - i.e. 00:15 the following morning, owned by the originating Saturday service date. 2026-06-06 is a
  // Saturday. A window from Saturday evening to Sunday morning must surface it, and it is the only departure in
  // the window (the last ordinary train leaves at 22:45).
  private static final String AM_ORIG = "VIC";
  private static final String AM_DEST = "BTN";
  private static final String AM_START = "2026-06-06T23:30:00"; // service date = Sat 2026-06-06
  private static final String AM_END = "2026-06-07T01:00:00";   // next morning => endTime > 86400
  private static final String OWL_DEPARTURE = "2026-06-07T00:15:00+01:00";
  private static final String OWL_ARRIVAL = "2026-06-07T01:15:00+01:00";

  @Test
  void rangeQuery_includesAfterMidnightDeparturesWhenWindowCrossesMidnight() {
    given()
            .queryParam("orig", AM_ORIG)
            .queryParam("dest", AM_DEST)
            .queryParam("startDate", AM_START)
            .queryParam("endDate", AM_END)
            .when().get("/")
            .then()
            .statusCode(200)
            .body("size()", is(1))
            .body("[0].legs[0].departureTime", is(OWL_DEPARTURE))
            .body("[0].legs[0].arrivalTime", is(OWL_ARRIVAL))
            .body("[0].legs[0].originTrainUid", is("WE2415"));
  }

  @Test
  void detail_includesAfterMidnightDeparturesAndTheirStopTypes() {
    // The owl is set-down only at East Croydon.
    given()
            .queryParam("orig", AM_ORIG)
            .queryParam("dest", AM_DEST)
            .queryParam("startDate", AM_START)
            .queryParam("endDate", AM_END)
            .when().get("/detail")
            .then()
            .statusCode(200)
            .body("size()", is(1))
            .body("[0].legs[0].departureTime", is(OWL_DEPARTURE))
            .body("[0].legs[0].trainTrip.tripId", is("1052415"))
            .body("[0].legs[0].trainTrip.stopTimes[1].stop", is("ECR"))
            .body("[0].legs[0].trainTrip.stopTimes[1].pickUpType", is("NONE"))
            .body("[0].legs[0].trainTrip.stopTimes[1].pickUp", is(false))
            .body("[0].legs[0].trainTrip.stopTimes[1].dropOffType", is("REGULAR"));
  }

  // --- Fixed links ---

  @Test
  void rangeQuery_crossLondonJourneyCarriesTheTubeLegWithItsInterchange() {
    // BTN -> MKC is fast to VIC, Tube VIC -> EUS, London Northwestern on to MKC: the only way there. The window
    // holds one seed that gets there (the 13:00 fast); the 12:40 stopper reaches MKC at the same time and is
    // dominated.
    given()
            .queryParam("orig", "BTN")
            .queryParam("dest", "MKC")
            .queryParam("startDate", DATE + "T12:30:00")
            .queryParam("endDate", DATE + "T13:05:00")
            .when().get("/")
            .then()
            .statusCode(200)
            .body("size()", is(1))
            .body("[0].legs", hasSize(3))
            .body("[0].legs[0].origin", is("BTN"))
            .body("[0].legs[0].destination", is("VIC"))
            .body("[0].legs[0].departureTime", is(DATE + "T13:00:00+01:00"))
            .body("[0].legs[0].arrivalTime", is(DATE + "T14:00:00+01:00"))
            .body("[0].legs[0].operator", is("SN"))
            .body("[0].legs[0].boardingInterchange", nullValue())
            .body("[0].legs[1].origin", is("VIC"))
            .body("[0].legs[1].destination", is("EUS"))
            .body("[0].legs[1].mode", is("TUBE"))
            .body("[0].legs[1].operator", nullValue())
            .body("[0].legs[1].originTrainUid", nullValue())
            .body("[0].legs[1].originPickUpType", nullValue())
            // Leaves VIC after its 10 minute interchange, and that interchange is reported on the leg it delays.
            .body("[0].legs[1].departureTime", is(DATE + "T14:10:00+01:00"))
            .body("[0].legs[1].arrivalTime", is(DATE + "T14:25:00+01:00"))
            .body("[0].legs[1].duration", is("PT15M"))
            .body("[0].legs[1].boardingInterchange", is("PT10M"))
            .body("[0].legs[2].origin", is("EUS"))
            .body("[0].legs[2].destination", is("MKC"))
            .body("[0].legs[2].departureTime", is(DATE + "T14:50:00+01:00"))
            .body("[0].legs[2].arrivalTime", is(DATE + "T15:25:00+01:00"))
            .body("[0].legs[2].operator", is("LM"))
            .body("[0].legs[2].originTrainUid", is("MA1450"))
            .body("[0].legs[2].boardingInterchange", is("PT10M"))
            .body("[0].departureTime", is(DATE + "T13:00:00+01:00"))
            .body("[0].arrivalTime", is(DATE + "T15:25:00+01:00"))
            .body("[0].duration", is("PT2H25M"))
            .body("[0].changes", is(1));
  }

  @Test
  void detail_crossLondonJourneyRendersTheTubeLegAsAFixedLeg() {
    given()
            .queryParam("orig", "BTN")
            .queryParam("dest", "MKC")
            .queryParam("startDate", DATE + "T12:30:00")
            .queryParam("endDate", DATE + "T13:05:00")
            .when().get("/detail")
            .then()
            .statusCode(200)
            .body("size()", is(1))
            .body("[0].legs", hasSize(3))
            .body("[0].legs[0].type", is("RAIL_LEG"))
            .body("[0].legs[1].type", is("FIXED_LEG"))
            .body("[0].legs[1].mode", is("TUBE"))
            .body("[0].legs[1].departureTime", is(DATE + "T14:10:00+01:00"))
            .body("[0].legs[1].arrivalTime", is(DATE + "T14:25:00+01:00"))
            .body("[0].legs[1].duration", is("PT15M"))
            .body("[0].legs[1].boardingInterchange", is("PT10M"))
            .body("[0].legs[2].type", is("RAIL_LEG"))
            .body("[0].legs[2].operator", is("LM"))
            .body("[0].legs[2].boardingInterchange", is("PT10M"))
            .body("[0].changes", is(1));
  }

  @Test
  void rangeQuery_ferryLinkIsUsableOnlyInsideItsWindowForTheDay() {
    // SHN 06:50 -> RYP 07:15, ferry to PMH, coastway 08:05 -> BTN 09:15. The ferry check is made on the arrival at
    // the far end (07:15 + 2 min + 22 min + 3 min = 07:42): inside the Mon-Sat 06:00-23:00 window on Saturday
    // 2026-06-06, before the Sunday 08:00 start on 2026-06-07.
    given()
            .queryParam("orig", "SHN")
            .queryParam("dest", "BTN")
            .queryParam("startDate", "2026-06-06T06:00:00")
            .queryParam("endDate", "2026-06-06T07:00:00")
            .when().get("/")
            .then()
            .statusCode(200)
            .body("size()", is(1))
            .body("[0].legs", hasSize(3))
            .body("[0].legs[1].mode", is("FERRY"))
            .body("[0].legs[1].origin", is("RYP"))
            .body("[0].legs[1].destination", is("PMH"))
            .body("[0].legs[1].duration", is("PT22M"))
            .body("[0].legs[2].departureTime", is("2026-06-06T08:05:00+01:00"))
            .body("[0].arrivalTime", is("2026-06-06T09:15:00+01:00"));

    given()
            .queryParam("orig", "SHN")
            .queryParam("dest", "BTN")
            .queryParam("startDate", "2026-06-07T06:00:00")
            .queryParam("endDate", "2026-06-07T07:00:00")
            .when().get("/")
            .then()
            .statusCode(200)
            .body("size()", is(0));
  }

  @Test
  void rangeQuery_journeysMayNotBeginOrEndWithAFixedLink() {
    // EUS has no trains to the south: the only way to BTN starts with the Tube, which the default rules forbid.
    given()
            .queryParam("orig", "EUS")
            .queryParam("dest", "BTN")
            .queryParam("startDate", DATE + "T08:00:00")
            .queryParam("endDate", DATE + "T10:00:00")
            .when().get("/")
            .then()
            .statusCode(200)
            .body("size()", is(0));

    // ASI is reached only by the walk from AFK, which would end the journey.
    given()
            .queryParam("orig", "HGS")
            .queryParam("dest", "ASI")
            .queryParam("startDate", DATE + "T08:00:00")
            .queryParam("endDate", DATE + "T10:00:00")
            .when().get("/")
            .then()
            .statusCode(200)
            .body("size()", is(0));
  }

  // --- Calendars ---

  @Test
  void rangeQuery_followsTheCalendar() {
    // BTN -> VIC 09:30-10:15: on a weekday the 10:00 fast and the 10:10 stopper; on Sunday 2026-06-07 and on the
    // bank holiday 2026-06-15 (Mon-Sat services removed by calendar_dates) only the fast.
    given()
            .queryParam("orig", ORIG)
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + "T09:30:00")
            .queryParam("endDate", DATE + "T10:15:00")
            .when().get("/")
            .then()
            .statusCode(200)
            .body("size()", is(2));

    for (String quietDay : new String[]{"2026-06-07", "2026-06-15"}) {
      given()
              .queryParam("orig", ORIG)
              .queryParam("dest", DEST)
              .queryParam("startDate", quietDay + "T09:30:00")
              .queryParam("endDate", quietDay + "T10:15:00")
              .when().get("/")
              .then()
              .statusCode(200)
              .body("size()", is(1))
              .body("[0].legs[0].departureTime", is(quietDay + "T10:00:00+01:00"));
    }
  }

  @Test
  void rangeQuery_appliesTheEngineeringOverlay() {
    // Mon-Fri 22-26 June the 22:00 fast from VIC is withdrawn (calendar_dates) and a 22:05 stopper runs instead.
    given()
            .queryParam("orig", "VIC")
            .queryParam("dest", "BTN")
            .queryParam("startDate", DATE + "T21:30:00")
            .queryParam("endDate", DATE + "T22:30:00")
            .when().get("/")
            .then()
            .statusCode(200)
            .body("departureTime", hasItem(DATE + "T22:00:00+01:00"))
            .body("departureTime", not(hasItem(DATE + "T22:05:00+01:00")));

    String overlayDay = "2026-06-24";
    given()
            .queryParam("orig", "VIC")
            .queryParam("dest", "BTN")
            .queryParam("startDate", overlayDay + "T21:30:00")
            .queryParam("endDate", overlayDay + "T22:30:00")
            .when().get("/")
            .then()
            .statusCode(200)
            .body("departureTime", hasItem(overlayDay + "T22:05:00+01:00"))
            .body("departureTime", not(hasItem(overlayDay + "T22:00:00+01:00")));
  }

  // --- Request stops ---

  @Test
  void rangeQuery_reportsRequestStopsOnTheLegEnds() {
    // Doleham is a request stop on the Marshlink: the 12:45 from Hastings sets down there at 13:00.
    given()
            .queryParam("orig", "HGS")
            .queryParam("dest", "DLH")
            .queryParam("startDate", DATE + "T12:30:00")
            .queryParam("endDate", DATE + "T13:30:00")
            .when().get("/")
            .then()
            .statusCode(200)
            .body("size()", is(1))
            .body("[0].legs[0].originPickUpType", is("REGULAR"))
            .body("[0].legs[0].destinationDropOffType", is("COORDINATE_WITH_DRIVER"))
            .body("[0].legs[0].arrivalTime", is(DATE + "T13:00:00+01:00"));

    given()
            .queryParam("orig", "DLH")
            .queryParam("dest", "AFK")
            .queryParam("startDate", DATE + "T13:00:00")
            .queryParam("endDate", DATE + "T13:30:00")
            .when().get("/")
            .then()
            .statusCode(200)
            .body("size()", is(1))
            .body("[0].legs[0].originPickUpType", is("COORDINATE_WITH_DRIVER"))
            .body("[0].legs[0].destinationDropOffType", is("REGULAR"));
  }

  // --- Validation ---

  @Test
  void rangeQuery_returns400WhenOrigMissing() {
    given()
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + "T08:30:00")
            .queryParam("endDate", DATE + "T09:30:00")
            .when().get("/")
            .then().statusCode(400);
  }

  @Test
  void rangeQuery_returns400WhenOrigBlank() {
    given()
            .queryParam("orig", "")
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + "T08:30:00")
            .queryParam("endDate", DATE + "T09:30:00")
            .when().get("/")
            .then().statusCode(400);
  }

  @Test
  void rangeQuery_returns400WhenDestMissing() {
    given()
            .queryParam("orig", ORIG)
            .queryParam("startDate", DATE + "T08:30:00")
            .queryParam("endDate", DATE + "T09:30:00")
            .when().get("/")
            .then().statusCode(400);
  }

  @Test
  void rangeQuery_returns400WhenStartDateMissing() {
    given()
            .queryParam("orig", ORIG)
            .queryParam("dest", DEST)
            .queryParam("endDate", DATE + "T09:30:00")
            .when().get("/")
            .then().statusCode(400);
  }

  @Test
  void rangeQuery_returns400WhenEndDateMissing() {
    given()
            .queryParam("orig", ORIG)
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + "T08:30:00")
            .when().get("/")
            .then().statusCode(400);
  }

  @Test
  void detail_returns400WhenOrigMissing() {
    given()
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + "T08:30:00")
            .queryParam("endDate", DATE + "T09:30:00")
            .when().get("/detail")
            .then().statusCode(400);
  }

  @Test
  void detail_returns400WhenEndDateMissing() {
    given()
            .queryParam("orig", ORIG)
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + "T08:30:00")
            .when().get("/detail")
            .then().statusCode(400);
  }

  @Test
  void firstArrival_returns400WhenOrigMissing() {
    given()
            .queryParam("dest", DEST)
            .queryParam("startDate", DATE + "T08:30:00")
            .when().get("/first-arrival")
            .then().statusCode(400);
  }

  @Test
  void firstArrival_returns400WhenStartDateMissing() {
    given()
            .queryParam("orig", ORIG)
            .queryParam("dest", DEST)
            .when().get("/first-arrival")
            .then().statusCode(400);
  }
}
