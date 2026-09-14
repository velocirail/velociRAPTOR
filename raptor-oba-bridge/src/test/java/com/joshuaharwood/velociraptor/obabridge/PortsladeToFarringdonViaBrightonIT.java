package com.joshuaharwood.velociraptor.obabridge;

import com.joshuaharwood.velociraptor.gtfs.GtfsDeserialiser;
import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;
import com.joshuaharwood.velociraptor.raptor.RaptorAlgorithm;
import com.joshuaharwood.velociraptor.raptor.model.Leg;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.query.RangeQuery;
import com.joshuaharwood.velociraptor.raptor.result.Journey;
import com.joshuaharwood.velociraptor.raptor.result.JourneyFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.onebusaway.gtfs.impl.calendar.CalendarServiceDataFactoryImpl;
import org.onebusaway.gtfs.model.calendar.ServiceDate;

import java.io.File;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Portslade (PLD) to Farringdon (ZFD) changing at Brighton (BTN): the West Coastway brings the passenger into the
 * terminus and Thameslink takes them on to London. In the sample feed (see fixtures/gtfs-sample/README.md) the
 * coastway from PMH reaches BTN at xx:15 and Thameslink leaves at xx:20 with a 5 minute interchange, so every hour
 * connects.
 */
class PortsladeToFarringdonViaBrightonIT {

  private static final File GTFS_FILE = Paths.get("..", "fixtures", "gtfs-sample").toFile();
  private static final ServiceDate SERVICE_DATE = new ServiceDate(2026, 6, 3); // a Wednesday
  private static final LocalDate QUERY_DATE = LocalDate.of(2026, 6, 3);
  private static final int WINDOW_START = LocalTime.of(6, 0).toSecondOfDay();
  private static final int WINDOW_END = LocalTime.of(10, 0).toSecondOfDay();

  private static final Stop PORTSLADE = new Stop("PLD");
  private static final Stop BRIGHTON = new Stop("BTN");
  private static final Stop FARRINGDON = new Stop("ZFD");

  private static RaptorAlgorithm raptor;

  @BeforeAll
  static void loadGtfs() {
    ExtendedGtfsRelationalDaoImpl dao = GtfsDeserialiser.createNewDao(GTFS_FILE);
    raptor = RaptorAlgorithmFactory.createFromDao(
        dao, CalendarServiceDataFactoryImpl.createService(dao), SERVICE_DATE);
  }

  @Test
  void findsPortsladeToFarringdonViaBrighton() {
    List<Journey> journeys = new RangeQuery<>(raptor, new JourneyFactory())
        .plan(PORTSLADE, FARRINGDON, QUERY_DATE, WINDOW_START, WINDOW_END);

    assertThat(journeys)
        .as("morning Portslade -> Farringdon journeys")
        .isNotEmpty();

    assertThat(journeys)
        .as("every PLD -> ZFD journey changes at Brighton")
        .allSatisfy(journey -> {
          assertThat(journey.legs()).hasSize(2);
          assertThat(journey.legs().getFirst().origin()).isEqualTo(PORTSLADE);
          assertThat(journey.legs().getFirst().destination()).isEqualTo(BRIGHTON);
          assertThat(journey.legs().getLast().origin()).isEqualTo(BRIGHTON);
          assertThat(journey.legs().getLast().destination()).isEqualTo(FARRINGDON);
          assertThat(journey.legs()).allMatch(leg -> leg instanceof Leg.TimetableLeg);
          assertThat(journey.arrivalTime()).isGreaterThan(journey.departureTime());
        });
  }
}
