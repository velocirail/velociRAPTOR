package com.joshuaharwood.velociraptor.obabridge;

import com.joshuaharwood.velociraptor.gtfs.GtfsDeserialiser;
import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.onebusaway.gtfs.impl.calendar.CalendarServiceDataFactoryImpl;
import org.onebusaway.gtfs.services.calendar.CalendarService;

import java.io.File;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class ExtendedGtfsDaoInitialisationTest {

  private static final File GTFS_FILE = Paths.get("..", "fixtures", "gtfs-sample").toFile();

  private static ExtendedGtfsRelationalDaoImpl DAO;
  private static CalendarService CALENDAR_SERVICE;

  @BeforeAll
  static void loadAndInitialise() {
    DAO = GtfsDeserialiser.createNewDao(GTFS_FILE);
    CALENDAR_SERVICE = CalendarServiceDataFactoryImpl.createService(DAO);
    DAO.initialise();
  }

  @Test
  void fixedLinkCacheIsPopulated() {
    assertThat(DAO.getAllFixedLinks()).isNotNull();
  }

  @Test
  void fixedLinkByStopCacheIsPopulated() {
    assertThat(DAO.getAllFixedLinksByStop()).isNotNull();
  }

  @Test
  void tripsByServiceIdIndexIsPopulated() {
    var trips = DAO.getAllTrips();
    assertThat(trips).isNotEmpty();
    assertThat(DAO.getTripsForServiceId(trips.iterator().next().getServiceId())).isNotNull();
  }

  @Test
  void stopTimesByTripIndexIsPopulated() {
    var trips = DAO.getAllTrips();
    assertThat(trips).isNotEmpty();
    assertThat(DAO.getStopTimesForTrip(trips.iterator().next())).isNotNull();
  }

  @Test
  void createFromDaoIsParallelSafeAfterInitialise() {
    var dates = CALENDAR_SERVICE.getServiceIds().stream()
      .flatMap(id -> CALENDAR_SERVICE.getServiceDatesForServiceId(id).stream())
      .distinct()
      .limit(4)
      .toList();

    var algorithms = dates.parallelStream()
      .map(date -> RaptorAlgorithmFactory.createFromDao(DAO, CALENDAR_SERVICE, date))
      .toList();

    assertThat(algorithms)
      .hasSize(dates.size())
      .doesNotContainNull();
  }
}
