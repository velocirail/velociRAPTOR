package com.joshuaharwood.velociraptor.obabridge;

import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;
import com.joshuaharwood.velociraptor.gtfs.GtfsDeserialiser;
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
import org.onebusaway.gtfs.services.calendar.CalendarService;

import java.io.File;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;

/**
 * The Portsmouth Harbour - Ryde Pier Head ferry in the sample feed has a Mon-Sat window of 06:00-23:00 and a
 * Sunday window of 08:00-20:00, as separate links.txt rows. The window is checked against the arrival at the far
 * end of the link, interchange included: Shanklin 06:50 reaches Ryde Pier Head at 07:15, and 07:15 + 2 min + 22 min
 * + 3 min = 07:42 is inside Saturday's window and before Sunday's start.
 */
class FerryLinkWindowTest {

  private static final File GTFS_FILE = Paths.get("..", "fixtures", "gtfs-sample").toFile();
  private static final int WINDOW_START = LocalTime.of(6, 0).toSecondOfDay();
  private static final int WINDOW_END = LocalTime.of(7, 0).toSecondOfDay();

  private static final Stop SHANKLIN = new Stop("SHN");
  private static final Stop RYDE_PIER_HEAD = new Stop("RYP");
  private static final Stop PORTSMOUTH_HARBOUR = new Stop("PMH");
  private static final Stop BRIGHTON = new Stop("BTN");

  private static ExtendedGtfsRelationalDaoImpl dao;
  private static CalendarService calendarService;

  @BeforeAll
  static void loadGtfs() {
    dao = GtfsDeserialiser.createNewDao(GTFS_FILE);
    calendarService = CalendarServiceDataFactoryImpl.createService(dao);
  }

  private static List<Journey> shanklinToBrighton(LocalDate date) {
    RaptorAlgorithm raptor = RaptorAlgorithmFactory.createFromDao(
      dao, calendarService, new ServiceDate(date.getYear(), date.getMonthValue(), date.getDayOfMonth()));
    return new RangeQuery<>(raptor, new JourneyFactory()).plan(SHANKLIN, BRIGHTON, date, WINDOW_START, WINDOW_END);
  }

  @Test
  void theFirstSailingIsCaughtOnASaturday() {
    var journeys = shanklinToBrighton(LocalDate.of(2026, 6, 6));

    assertThat(journeys).hasSize(1);
    var legs = journeys.getFirst().legs();
    assertThat(legs).hasSize(3);
    assertThat(legs.get(0)).isInstanceOf(Leg.TimetableLeg.class)
      .returns(SHANKLIN, from(Leg::origin))
      .returns(RYDE_PIER_HEAD, from(Leg::destination));
    assertThat(legs.get(1)).isInstanceOfSatisfying(Leg.TransferLeg.class, ferry -> assertThat(ferry)
      .returns(RYDE_PIER_HEAD, from(Leg.TransferLeg::origin))
      .returns(PORTSMOUTH_HARBOUR, from(Leg.TransferLeg::destination))
      .returns("FERRY", from(Leg.TransferLeg::mode))
      .returns(1320, from(Leg.TransferLeg::duration)));
    assertThat(legs.get(2)).isInstanceOf(Leg.TimetableLeg.class)
      .returns(PORTSMOUTH_HARBOUR, from(Leg::origin))
      .returns(BRIGHTON, from(Leg::destination));
    assertThat(journeys.getFirst())
      .returns(LocalTime.of(6, 50).toSecondOfDay(), from(Journey::departureTime))
      .returns(LocalTime.of(9, 15).toSecondOfDay(), from(Journey::arrivalTime));
  }

  @Test
  void theSameTrainMissesTheLaterSundayStart() {
    assertThat(shanklinToBrighton(LocalDate.of(2026, 6, 7))).isEmpty();
  }
}
