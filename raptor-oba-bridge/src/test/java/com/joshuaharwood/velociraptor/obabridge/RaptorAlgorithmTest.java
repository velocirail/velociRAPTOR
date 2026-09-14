package com.joshuaharwood.velociraptor.obabridge;

import java.io.File;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.onebusaway.gtfs.impl.calendar.CalendarServiceDataFactoryImpl;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.services.calendar.CalendarService;
import com.joshuaharwood.velociraptor.gtfs.GtfsDeserialiser;
import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;
import com.joshuaharwood.velociraptor.raptor.RaptorAlgorithm;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.query.DepartAfterQuery;
import com.joshuaharwood.velociraptor.raptor.result.JourneyFactory;

import static org.assertj.core.api.Assertions.assertThat;

/** Loads the artificial sample feed (see fixtures/gtfs-sample/README.md) through the OBA reader and scans it. */
class RaptorAlgorithmTest {

  private static final File GTFS_FILE = Paths.get("..", "fixtures", "gtfs-sample").toFile();

  // A Wednesday: the full Mon-Sat timetable runs.
  private static final ServiceDate SERVICE_DATE = new ServiceDate(2026, 6, 3);
  private static final LocalDate QUERY_DATE = LocalDate.of(2026, 6, 3);

  // 13:00 local - 46800s since midnight.
  private static final int DEPART_TIME = LocalTime.of(13, 0).toSecondOfDay();

  private static final Stop BRIGHTON = new Stop("BTN");
  private static final Stop LONDON_VICTORIA = new Stop("VIC");

  private static RaptorAlgorithm RAPTOR;

  @BeforeAll
  static void loadGtfs() {
    ExtendedGtfsRelationalDaoImpl dao = GtfsDeserialiser.createNewDao(GTFS_FILE);
    CalendarService calendarService = CalendarServiceDataFactoryImpl.createService(dao);
    RAPTOR = RaptorAlgorithmFactory.createFromDao(dao, calendarService, SERVICE_DATE);
  }

  @Test
  void scanFromBrightonProducesResults() {
    var scan = RAPTOR.scan(Map.of(BRIGHTON, DEPART_TIME));

    assertThat(scan.kConnections()).isNotEmpty();
    assertThat(scan.bestArrivals()).isNotEmpty();

    // Origin's best arrival is its departure time - it's already there at the query moment.
    assertThat(scan.bestArrivals().get(BRIGHTON)).isEqualTo(DEPART_TIME);

    // Any arrival that got updated must be >= departure time; INT_MAX entries are untouched seeds.
    assertThat(scan.bestArrivals().values())
      .filteredOn(t -> t < Integer.MAX_VALUE)
      .allSatisfy(t -> assertThat(t).isGreaterThanOrEqualTo(DEPART_TIME));
  }

  @Test
  void reachesLondonVictoriaOnTheThirteenHundredFast() {
    var scan = RAPTOR.scan(Map.of(BRIGHTON, DEPART_TIME));

    // The 13:00 fast (trip 1021300) runs BTN -> VIC in 60 minutes. Labels carry the alighting stop's minimum
    // interchange (VIC: 600 s), so the scan reports 14:10 rather than the 14:00 the train arrives.
    assertThat(scan.bestArrivals()).containsEntry(LONDON_VICTORIA, LocalTime.of(14, 10).toSecondOfDay());
  }

  @Test
  void departAfterQueryPlansJourneyFromBrightonToVictoria() {
    var query = new DepartAfterQuery<>(RAPTOR, new JourneyFactory());
    var journeys = query.plan(BRIGHTON, LONDON_VICTORIA, QUERY_DATE, DEPART_TIME);

    assertThat(journeys).isNotEmpty();
    assertThat(journeys).allSatisfy(journey -> {
      assertThat(journey.legs()).isNotEmpty();
      assertThat(journey.departureTime()).isGreaterThanOrEqualTo(DEPART_TIME);
      assertThat(journey.arrivalTime()).isGreaterThan(journey.departureTime());
      assertThat(journey.legs().getFirst().origin()).isEqualTo(BRIGHTON);
      assertThat(journey.legs().getLast().destination()).isEqualTo(LONDON_VICTORIA);
    });
  }
}
