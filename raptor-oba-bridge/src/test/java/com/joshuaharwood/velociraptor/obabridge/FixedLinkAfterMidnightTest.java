package com.joshuaharwood.velociraptor.obabridge;

import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;
import com.joshuaharwood.velociraptor.gtfs.FixedLink;
import com.joshuaharwood.velociraptor.raptor.model.Leg;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.query.DepartAfterQuery;
import com.joshuaharwood.velociraptor.raptor.result.Journey;
import com.joshuaharwood.velociraptor.raptor.result.JourneyFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.onebusaway.gtfs.impl.calendar.CalendarServiceDataFactoryImpl;
import org.onebusaway.gtfs.model.Agency;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.ServiceCalendar;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.services.calendar.CalendarService;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A fixed link's time window and day flags are wall-clock; a trip arriving after midnight is still on
 * the previous service date with a time past 24:00. The factory offers each link for the service date
 * and, shifted by a day, for the following calendar day, so the 00:01-23:59 window admits a 24:08
 * arrival and a Sunday-only link is offered at 24:30 on a Saturday (BUGS.md 7.2).
 */
class FixedLinkAfterMidnightTest {

  private static final String AGENCY_ID = "AG";
  /** 2025-09-20 is a Saturday. */
  private static final ServiceDate SATURDAY = new ServiceDate(2025, 9, 20);
  private static final LocalDate SATURDAY_DATE = LocalDate.of(2025, 9, 20);
  private static final int DAY = 24 * 3600;

  private static ExtendedGtfsRelationalDaoImpl dao;
  private static CalendarService calendarService;

  /**
   * Trips: A 23:00 → B 24:08 (arrives after midnight) and A 22:00 → B 23:30. Links from B: to C
   * every day 00:01-23:59, to D on Sundays only, all day.
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

    var stopA = obaStop("A");
    var stopB = obaStop("B");
    var stopC = obaStop("C");
    var stopD = obaStop("D");

    var late = trip("late", route);
    saveStopTime(1, late, stopA, 23 * 3600, 1);
    saveStopTime(2, late, stopB, DAY + 8 * 60, 2);

    var earlier = trip("earlier", route);
    saveStopTime(3, earlier, stopA, 22 * 3600, 1);
    saveStopTime(4, earlier, stopB, 23 * 3600 + 30 * 60, 2);

    var everyDay = link(1, stopB, stopC, 60, DAY - 60);
    everyDay.setMonday(true);
    everyDay.setTuesday(true);
    everyDay.setWednesday(true);
    everyDay.setThursday(true);
    everyDay.setFriday(true);
    everyDay.setSaturday(true);
    everyDay.setSunday(true);
    dao.saveEntity(everyDay);

    var sundayOnly = link(2, stopB, stopD, 0, DAY - 1);
    sundayOnly.setSunday(true);
    dao.saveEntity(sundayOnly);

    calendarService = CalendarServiceDataFactoryImpl.createService(dao);
  }

  private static List<Journey> plan(String destination, int time) {
    var raptor = RaptorAlgorithmFactory.createFromDao(dao, calendarService, SATURDAY);
    return new DepartAfterQuery<>(raptor, new JourneyFactory()).plan(new Stop("A"), new Stop(destination), SATURDAY_DATE, time);
  }

  @Test
  void aLinkWhoseWindowEndsAt2359IsStillTakenAt0008() {
    var journeys = plan("C", 22 * 3600 + 30 * 60);

    assertThat(journeys).hasSize(1);
    assertThat(journeys.getFirst().legs()).hasSize(2);
    assertThat(journeys.getFirst().legs().getLast()).isInstanceOf(Leg.TransferLeg.class);
    assertThat(journeys.getFirst().arrivalTime()).isEqualTo(DAY + 8 * 60 + 300);
  }

  @Test
  void aSundayOnlyLinkIsOfferedAfterMidnightOnASaturdayServiceDate() {
    // Leave at 22:30: the 23:00 reaches B at 00:08 Sunday, when the Sunday link runs.
    assertThat(plan("D", 22 * 3600 + 30 * 60)).hasSize(1);
  }

  @Test
  void aSundayOnlyLinkIsNotOfferedBeforeMidnightOnASaturday() {
    // Leave at 21:30: the 22:00 reaches B at 23:30 Saturday; the only onward link runs on Sundays.
    // The later train would work, but the scan only labels B once - by the earlier arrival - so the
    // journey via the 00:08 arrival is not the one it explores; the point here is the 23:30 refusal.
    var journeys = plan("D", 21 * 3600 + 30 * 60);

    assertThat(journeys).allSatisfy(journey ->
      assertThat(journey.arrivalTime()).as("only the after-midnight arrival may take the Sunday link").isGreaterThan(DAY));
  }

  private static Trip trip(String id, Route route) {
    var trip = new Trip();
    trip.setId(id(id));
    trip.setRoute(route);
    trip.setServiceId(id("everyday"));
    dao.saveEntity(trip);
    return trip;
  }

  private static FixedLink link(int id, org.onebusaway.gtfs.model.Stop from, org.onebusaway.gtfs.model.Stop to, int start, int end) {
    var link = new FixedLink();
    link.setId(id);
    link.setFromStop(from);
    link.setToStop(to);
    link.setMode("WALK");
    link.setDurationInSeconds(300);
    link.setStartTime(start);
    link.setEndTime(end);
    link.setStartDate(new ServiceDate(2025, 1, 1));
    link.setEndDate(new ServiceDate(2025, 12, 31));
    return link;
  }

  private static AgencyAndId id(String id) {
    return new AgencyAndId(AGENCY_ID, id);
  }

  private static org.onebusaway.gtfs.model.Stop obaStop(String id) {
    var stop = new org.onebusaway.gtfs.model.Stop();
    stop.setId(id(id));
    stop.setName("Stop " + id);
    dao.saveEntity(stop);
    return stop;
  }

  private static void saveStopTime(int id, Trip trip, org.onebusaway.gtfs.model.Stop stop, int time, int sequence) {
    var stopTime = new StopTime();
    stopTime.setId(id);
    stopTime.setTrip(trip);
    stopTime.setStop(stop);
    stopTime.setArrivalTime(time);
    stopTime.setDepartureTime(time);
    stopTime.setStopSequence(sequence);
    dao.saveEntity(stopTime);
  }
}
