package com.joshuaharwood.velociraptor.obabridge;

import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;
import com.joshuaharwood.velociraptor.raptor.model.Leg;
import com.joshuaharwood.velociraptor.raptor.model.PickupDropOffType;
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
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.services.calendar.CalendarService;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GTFS pickup_type / drop_off_type: 0 regular, 1 none, 2 phone the agency, 3 coordinate with the
 * driver. The UK feeds mark request stops with 3. Only 1 forbids boarding or alighting; the factory
 * used to treat everything but 0 as forbidden, which made every station served only by request stops
 * unroutable (BUGS.md 7.1).
 */
class RequestStopTest {

  private static final String AGENCY_ID = "AG";
  private static final ServiceDate SERVICE_DATE = new ServiceDate(2025, 9, 17);
  private static final LocalDate DATE = LocalDate.of(2025, 9, 17);

  private static ExtendedGtfsRelationalDaoImpl dao;
  private static CalendarService calendarService;

  /** A (regular) → R (request stop, 3/3) → P (phone the agency, 2/2) → N (not advertised, 1/1) → B (regular). */
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

    var trip = new Trip();
    trip.setId(id("T1"));
    trip.setRoute(route);
    trip.setServiceId(id("everyday"));
    dao.saveEntity(trip);

    saveStopTime(1, trip, obaStop("A"), 8 * 3600, 1, 0, 0);
    saveStopTime(2, trip, obaStop("R"), 8 * 3600 + 600, 2, 3, 3);
    saveStopTime(3, trip, obaStop("P"), 8 * 3600 + 1200, 3, 2, 2);
    saveStopTime(4, trip, obaStop("N"), 8 * 3600 + 1800, 4, 1, 1);
    saveStopTime(5, trip, obaStop("B"), 9 * 3600, 5, 0, 0);

    calendarService = CalendarServiceDataFactoryImpl.createService(dao);
  }

  @Test
  void aRequestStopCanBeBoardedAndAlighted() {
    var raptor = RaptorAlgorithmFactory.createFromDao(dao, calendarService, SERVICE_DATE);
    var query = new DepartAfterQuery<>(raptor, new JourneyFactory());

    var toRequestStop = query.plan(new Stop("A"), new Stop("R"), DATE, 7 * 3600);
    assertThat(toRequestStop).hasSize(1);
    assertThat(query.plan(new Stop("R"), new Stop("B"), DATE, 7 * 3600)).hasSize(1);

    // The type travels into the result, so a consumer can tell the passenger to ask the guard.
    var leg = (Leg.TimetableLeg) toRequestStop.getFirst().legs().getFirst();
    assertThat(leg.stopTimes().getFirst().pickup()).isEqualTo(PickupDropOffType.REGULAR);
    assertThat(leg.stopTimes().getLast().dropOff()).isEqualTo(PickupDropOffType.COORDINATE_WITH_DRIVER);
  }

  @Test
  void aPhoneTheAgencyStopCanBeBoardedAndAlighted() {
    var raptor = RaptorAlgorithmFactory.createFromDao(dao, calendarService, SERVICE_DATE);
    var query = new DepartAfterQuery<>(raptor, new JourneyFactory());

    assertThat(query.plan(new Stop("A"), new Stop("P"), DATE, 7 * 3600)).hasSize(1);
    assertThat(query.plan(new Stop("P"), new Stop("B"), DATE, 7 * 3600)).hasSize(1);
  }

  @Test
  void aNotAdvertisedStopStillCannotBeUsed() {
    var raptor = RaptorAlgorithmFactory.createFromDao(dao, calendarService, SERVICE_DATE);
    var query = new DepartAfterQuery<>(raptor, new JourneyFactory());

    assertThat(query.plan(new Stop("A"), new Stop("N"), DATE, 7 * 3600)).isEmpty();
    assertThat(query.plan(new Stop("N"), new Stop("B"), DATE, 7 * 3600)).isEmpty();
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

  private static void saveStopTime(int id, Trip trip, org.onebusaway.gtfs.model.Stop stop, int time, int sequence,
                                   int pickupType, int dropOffType) {
    var stopTime = new StopTime();
    stopTime.setId(id);
    stopTime.setTrip(trip);
    stopTime.setStop(stop);
    stopTime.setArrivalTime(time);
    stopTime.setDepartureTime(time);
    stopTime.setStopSequence(sequence);
    stopTime.setPickupType(pickupType);
    stopTime.setDropOffType(dropOffType);
    dao.saveEntity(stopTime);
  }
}
