package com.joshuaharwood.velociraptor.obabridge;

import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;
import com.joshuaharwood.velociraptor.raptor.model.DefaultTrip;
import com.joshuaharwood.velociraptor.raptor.model.Leg;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.query.DepartAfterQuery;
import com.joshuaharwood.velociraptor.raptor.result.JourneyFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.onebusaway.gtfs.impl.calendar.CalendarServiceDataFactoryImpl;
import org.onebusaway.gtfs.model.Agency;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.ServiceCalendar;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Transfer;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.services.calendar.CalendarService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * In-seat transfers ({@code transfer_type} 4): a train that divides, one that joins another, and one whose onward
 * train leaves before it arrives. A followed link is a through trip a passenger stays aboard, with no change.
 */
class TripLinkTest {

  private static final String AGENCY_ID = "AG";
  private static final ServiceDate SERVICE_DATE = new ServiceDate(2025, 9, 17);
  private static final LocalDate DATE = LocalDate.of(2025, 9, 17);

  private static ExtendedGtfsRelationalDaoImpl dao;
  private static CalendarService calendarService;
  private static final Map<String, org.onebusaway.gtfs.model.Stop> STOPS = new HashMap<>();
  private static int stopTimeId;
  private static int transferId;

  /**
   * Divide: the 08:00 A -> D -> E carries on to E, and its portion leaves D at 08:35 for F.
   * Join: the 10:00 G -> H joins the 10:00 K -> H -> L at H, which leaves at 10:40.
   * Too late: the 12:00 M -> N arrives at 12:30, after the 12:20 N -> P it is linked to has gone.
   * Sleeper: the 22:00 Q -> R -> S reaches R at 04:00, as 28:00 on its service day, and divides there three ways: it
   * carries on to S, and portions leave R at 04:28 for T and 04:40 for U - on the following service day's timetable.
   */
  @BeforeAll
  static void buildDao() {
    dao = new ExtendedGtfsRelationalDaoImpl();

    var agency = new Agency();
    agency.setId(AGENCY_ID);
    agency.setName("Test Agency");
    agency.setUrl("https://example.com");
    agency.setTimezone("Europe/London");
    dao.saveEntity(agency);

    var calendar = new ServiceCalendar();
    calendar.setId(1);
    calendar.setServiceId(id("everyday"));
    calendar.setMonday(1);
    calendar.setTuesday(1);
    calendar.setWednesday(1);
    calendar.setThursday(1);
    calendar.setFriday(1);
    calendar.setSaturday(1);
    calendar.setSunday(1);
    calendar.setStartDate(new ServiceDate(2025, 1, 1));
    calendar.setEndDate(new ServiceDate(2025, 12, 31));
    dao.saveEntity(calendar);

    var route = new Route();
    route.setId(id("R1"));
    route.setAgency(agency);
    route.setType(2);
    dao.saveEntity(route);

    var main = trip(route, "MAIN", "A", 8 * 60, "D", 8 * 60 + 30, "E", 9 * 60);
    var portion = trip(route, "PORTION", "D", 8 * 60 + 35, "F", 9 * 60 + 5);
    inSeat(main, portion, "D");

    var joining = trip(route, "JOINING", "G", 10 * 60, "H", 10 * 60 + 30);
    var joined = trip(route, "JOINED", "K", 10 * 60, "H", 10 * 60 + 40, "L", 11 * 60);
    inSeat(joining, joined, "H");

    var late = trip(route, "LATE", "M", 12 * 60, "N", 12 * 60 + 30);
    var gone = trip(route, "GONE", "N", 12 * 60 + 20, "P", 12 * 60 + 50);
    inSeat(late, gone, "N");

    var sleeper = trip(route, "SLEEPER", "Q", 22 * 60, "R", 28 * 60, "S", 29 * 60);
    var morning = trip(route, "MORNING", "R", 4 * 60 + 28, "T", 7 * 60 + 50);
    inSeat(sleeper, morning, "R");
    var other = trip(route, "OTHER", "R", 4 * 60 + 40, "U", 6 * 60);
    inSeat(sleeper, other, "R");

    calendarService = CalendarServiceDataFactoryImpl.createService(dao);
  }

  @Test
  void eachLinkIsReadAsADivideOrAJoinAndOneWhoseTrainHasGoneIsNotFollowed() {
    var links = new ArrayList<TripLink>();
    RaptorAlgorithmFactory.createFromDao(dao, calendarService, SERVICE_DATE,
                                         (trip, stopTimes) -> new DefaultTrip(trip.getId().getId(), stopTimes),
                                         (link, stopTimes, _) -> {
                                           links.add(link);
                                           return new DefaultTrip(link.from().id() + "+" + link.to().id(), stopTimes);
                                         });

    assertThat(links).extracting(link -> link.from().id() + ">" + link.to().id() + " " + link.type())
                     .containsExactlyInAnyOrder("MAIN>PORTION DIVIDE", "JOINING>JOINED JOIN",
                                                "SLEEPER>MORNING DIVIDE", "SLEEPER>OTHER DIVIDE");
    var divide = links.stream().filter(link -> link.from().id().equals("MAIN")).findFirst().orElseThrow();
    // The main train is the portion that goes elsewhere, to E.
    assertThat(divide.otherPortions()).extracting(trip -> trip.id()).containsExactly("MAIN");
    assertThat(divide.fromIndex()).isEqualTo(1);
    // A three-way divide: the sleeper carries on, and the other portion goes elsewhere too.
    var sleeper = links.stream().filter(link -> link.to().id().equals("MORNING")).findFirst().orElseThrow();
    assertThat(sleeper.otherPortions()).extracting(trip -> trip.id()).containsExactly("SLEEPER", "OTHER");
    assertThat(divide.toIndex()).isZero();
  }

  @Test
  void aPassengerStaysAboardAsTheTrainDivides() {
    // A -> F: the portion for F is the train from A, so it is one leg from A, arriving 09:05 - not a change at D.
    var journey = plan("A", "F", 7 * 60 + 50);

    assertThat(journey.legs()).singleElement().isInstanceOfSatisfying(Leg.TimetableLeg.class, leg -> {
      assertThat(leg.trip().id()).isEqualTo("MAIN+PORTION");
      assertThat(leg.stopTimes()).extracting(call -> call.stop().id()).containsExactly("A", "D", "F");
      // At D the train arrives as the main train did and leaves as the portion does.
      assertThat(leg.stopTimes().get(1).arrivalTime()).isEqualTo((8 * 60 + 30) * 60);
      assertThat(leg.stopTimes().get(1).departureTime()).isEqualTo((8 * 60 + 35) * 60);
    });
  }

  @Test
  void aJourneyThatDoesNotCrossTheDivideRidesTheTrainItself() {
    // Before D the through train runs at the main train's times, and after it at the portion's: a journey on one
    // side only must not come back as the through train, under its ids and destination.
    assertThat(plan("A", "D", 7 * 60 + 50).legs()).singleElement()
      .isInstanceOfSatisfying(Leg.TimetableLeg.class, leg -> assertThat(leg.trip().id()).isEqualTo("MAIN"));
    assertThat(plan("D", "F", 8 * 60 + 30).legs()).singleElement()
      .isInstanceOfSatisfying(Leg.TimetableLeg.class, leg -> assertThat(leg.trip().id()).isEqualTo("PORTION"));
  }

  @Test
  void aPassengerStaysAboardAsTheirTrainJoinsAnother() {
    assertThat(plan("G", "L", 9 * 60 + 50).legs()).singleElement()
      .isInstanceOfSatisfying(Leg.TimetableLeg.class, leg -> assertThat(leg.trip().id()).isEqualTo("JOINING+JOINED"));
  }

  @Test
  void aSleeperDividesIntoAPortionOnTheFollowingServiceDay() {
    // The portion runs on the following day, so on this day's time-line it leaves R at 28:28 and reaches T at 31:50.
    var journey = plan("Q", "T", 21 * 60 + 50);

    assertThat(journey.legs()).singleElement().isInstanceOfSatisfying(Leg.TimetableLeg.class, leg -> {
      assertThat(leg.trip().id()).isEqualTo("SLEEPER+MORNING");
      assertThat(leg.stopTimes().get(1).arrivalTime()).isEqualTo(28 * 3600);
      assertThat(leg.stopTimes().get(1).departureTime()).isEqualTo(28 * 3600 + 28 * 60);
      assertThat(leg.stopTimes().getLast().arrivalTime()).isEqualTo((31 * 60 + 50) * 60);
    });
  }

  @Test
  void aTrainThatHasGoneCannotBeStayedAboardFor() {
    // M reaches N at 12:30, before midnight, so tomorrow's N -> P is not the one it forms.
    var raptor = RaptorAlgorithmFactory.createFromDao(dao, calendarService, SERVICE_DATE);
    var journeys = new DepartAfterQuery<>(raptor, new JourneyFactory())
      .plan(new Stop("M"), new Stop("P"), DATE, 11 * 3600 + 50 * 60);

    assertThat(journeys).isEmpty();
  }

  private static com.joshuaharwood.velociraptor.raptor.result.Journey plan(String origin, String destination,
                                                                           int minutes) {
    var raptor = RaptorAlgorithmFactory.createFromDao(dao, calendarService, SERVICE_DATE);
    var journeys = new DepartAfterQuery<>(raptor, new JourneyFactory())
      .plan(new Stop(origin), new Stop(destination), DATE, minutes * 60);
    assertThat(journeys).as("%s -> %s", origin, destination).isNotEmpty();
    return journeys.getFirst();
  }

  /** A trip calling at each stop at each time, in minutes since midnight, given in pairs. */
  private static Trip trip(Route route, String tripId, Object... callsAndTimes) {
    var trip = new Trip();
    trip.setId(id(tripId));
    trip.setRoute(route);
    trip.setServiceId(id("everyday"));
    dao.saveEntity(trip);

    List<Object> calls = List.of(callsAndTimes);
    for (int i = 0; i < calls.size(); i += 2) {
      boolean first = i == 0;
      boolean last = i == calls.size() - 2;
      var stopTime = new StopTime();
      stopTime.setId(++stopTimeId);
      stopTime.setTrip(trip);
      stopTime.setStop(stop((String) calls.get(i)));
      stopTime.setArrivalTime((Integer) calls.get(i + 1) * 60);
      stopTime.setDepartureTime((Integer) calls.get(i + 1) * 60);
      stopTime.setStopSequence(i / 2 + 1);
      stopTime.setPickupType(last ? 1 : 0);
      stopTime.setDropOffType(first ? 1 : 0);
      dao.saveEntity(stopTime);
    }
    return trip;
  }

  private static void inSeat(Trip from, Trip to, String at) {
    var transfer = new Transfer();
    transfer.setId(++transferId);
    transfer.setFromStop(stop(at));
    transfer.setToStop(stop(at));
    transfer.setFromTrip(from);
    transfer.setToTrip(to);
    transfer.setTransferType(4);
    dao.saveEntity(transfer);
  }

  private static org.onebusaway.gtfs.model.Stop stop(String id) {
    return STOPS.computeIfAbsent(id, key -> {
      var stop = new org.onebusaway.gtfs.model.Stop();
      stop.setId(id(key));
      stop.setName("Stop " + key);
      dao.saveEntity(stop);
      return stop;
    });
  }

  private static AgencyAndId id(String id) {
    return new AgencyAndId(AGENCY_ID, id);
  }
}
