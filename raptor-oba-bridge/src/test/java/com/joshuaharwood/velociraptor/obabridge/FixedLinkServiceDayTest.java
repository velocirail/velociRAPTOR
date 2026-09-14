package com.joshuaharwood.velociraptor.obabridge;

import java.util.List;
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
import com.joshuaharwood.velociraptor.gtfs.FixedLink;
import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;
import com.joshuaharwood.velociraptor.raptor.RaptorAlgorithm;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.model.Transfer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fixed links carry day-of-week flags plus start/end dates; the factory must only offer a link as a
 * transfer when it actually runs on the queried service date (mirroring the service-calendar filter
 * it applies to trips).
 */
class FixedLinkServiceDayTest {

  private static final String AGENCY_ID = "AG";

  /** 2025-09-21 is a Sunday; 2025-09-17 is a Wednesday. */
  private static final ServiceDate SUNDAY = new ServiceDate(2025, 9, 21);
  private static final ServiceDate WEDNESDAY = new ServiceDate(2025, 9, 17);

  private static ExtendedGtfsRelationalDaoImpl dao;
  private static CalendarService calendarService;

  @BeforeAll
  static void buildDao() {
    dao = new ExtendedGtfsRelationalDaoImpl();

    var agency = new Agency();
    agency.setId(AGENCY_ID);
    agency.setName("Test Agency");
    agency.setUrl("https://example.com");
    agency.setTimezone("Europe/London");
    dao.saveEntity(agency);

    var stopA = obaStop("A");
    var stopB = obaStop("B");
    var stopC = obaStop("C");

    // Service running every day of the week so the trip exists on both query dates.
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

    saveStopTime(1, trip, stopA, 8 * 3600, 1);
    saveStopTime(2, trip, stopB, 9 * 3600, 2);

    // Fixed link B -> C running on Sundays only.
    var link = new FixedLink();
    link.setId(1);
    link.setFromStop(stopB);
    link.setToStop(stopC);
    link.setMode("WALK");
    link.setDurationInSeconds(300);
    link.setStartTime(60);
    link.setEndTime(86_340);
    link.setStartDate(new ServiceDate(2025, 1, 1));
    link.setEndDate(new ServiceDate(2025, 12, 31));
    link.setSunday(true);
    dao.saveEntity(link);

    calendarService = CalendarServiceDataFactoryImpl.createService(dao);
  }

  @Test
  void fixedLinkIsOfferedOnADayItRuns() {
    RaptorAlgorithm raptor = RaptorAlgorithmFactory.createFromDao(dao, calendarService, SUNDAY);

    assertThat(transfersAt(raptor, new Stop("B")))
      .anySatisfy(transfer -> {
        assertThat(transfer.origin()).isEqualTo(new Stop("B"));
        assertThat(transfer.destination()).isEqualTo(new Stop("C"));
      });
  }

  @Test
  void fixedLinkIsNotOfferedOnADayItDoesNotRun() {
    RaptorAlgorithm raptor = RaptorAlgorithmFactory.createFromDao(dao, calendarService, WEDNESDAY);

    assertThat(transfersAt(raptor, new Stop("B")))
      .noneSatisfy(transfer -> assertThat(transfer.destination()).isEqualTo(new Stop("C")));
  }

  private static List<Transfer> transfersAt(RaptorAlgorithm raptor, Stop stop) {
    int stopIndex = raptor.stopToIndex().getInt(stop);
    assertThat(stopIndex).as("stop %s is indexed", stop.id()).isNotEqualTo(RaptorAlgorithmFactory.ABSENT_VAL);
    return List.of(raptor.transfersArray()[stopIndex]);
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
