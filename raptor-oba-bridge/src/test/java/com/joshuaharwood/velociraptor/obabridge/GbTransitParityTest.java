package com.joshuaharwood.velociraptor.obabridge;

import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;
import com.joshuaharwood.velociraptor.gtfs.FeedFormat;
import com.joshuaharwood.velociraptor.gtfs.GtfsDeserialiser;
import com.joshuaharwood.velociraptor.raptor.RaptorAlgorithm;
import com.joshuaharwood.velociraptor.raptor.model.Leg;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.query.RangeQuery;
import com.joshuaharwood.velociraptor.raptor.result.Journey;
import com.joshuaharwood.velociraptor.raptor.result.JourneyFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.onebusaway.gtfs.impl.calendar.CalendarServiceDataFactoryImpl;
import org.onebusaway.gtfs.model.calendar.ServiceDate;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The same invented network read from the deprecated dtd2gtfs sample and from the gb-transit sample must plan the same
 * journeys: the same stations, times, links and interchanges, differing only in trip ids. Checked across every pair
 * of stations.
 * <p>
 * The one difference is by design and pinned last: gb-transit publishes one row per pair of stations for a fixed
 * link, the envelope of its windows, so on a Sunday a link is offered for the longer Monday-Saturday hours
 * (BUGS.md 10.1).
 */
@SuppressWarnings("removal")
class GbTransitParityTest {

  private static ExtendedGtfsRelationalDaoImpl dtd2gtfs;
  private static ExtendedGtfsRelationalDaoImpl gbTransit;
  private static List<String> stations;

  @BeforeAll
  static void load() {
    dtd2gtfs = GtfsDeserialiser.createNewDao(Path.of("..", "fixtures", "gtfs-sample").toFile(), FeedFormat.DTD2GTFS);
    gbTransit = GtfsDeserialiser.createNewDao(Path.of("..", "fixtures", "gtfs-sample-gb-transit").toFile(), FeedFormat.GB_TRANSIT);
    stations = dtd2gtfs.getAllStops().stream().map(s -> s.getId().getId()).sorted().toList();
  }

  /** A weekday, a Saturday (with the night owl), the bank holiday, and a day of the engineering overlay. */
  @ParameterizedTest
  @ValueSource(strings = {"2026-06-03", "2026-06-06", "2026-06-15", "2026-06-24"})
  void everyPairPlansTheSameJourneys(String day) {
    var date = LocalDate.parse(day);
    var legacy = algorithm(dtd2gtfs, date);
    var current = algorithm(gbTransit, date);
    int start = LocalTime.of(7, 0).toSecondOfDay();
    int end = LocalTime.of(23, 59).toSecondOfDay() + 3600;

    var differences = new ArrayList<String>();
    int journeys = 0;
    for (var origin : stations) {
      for (var destination : stations) {
        if (origin.equals(destination)) {
          continue;
        }
        var expected = plan(legacy, origin, destination, date, start, end);
        var actual = plan(current, origin, destination, date, start, end);
        journeys += expected.size();
        if (!expected.equals(actual)) {
          differences.add(origin + "->" + destination + ": " + expected + " vs " + actual);
        }
      }
    }
    assertThat(journeys).as("the comparison found journeys to compare").isGreaterThan(1000);
    assertThat(differences).isEmpty();
  }

  @Test
  void onASundayALinkIsOfferedForItsEnvelope() {
    // SHN 06:50 -> RYP 07:15 reaches PMH by the ferry at 07:42, inside the Mon-Sat 06:00 start but before the DTD's
    // Sunday 08:00 one. The dtd2gtfs sample keeps the Sunday window and has no journey; gb-transit's envelope row is
    // open from 06:00 every day, so it has one.
    var sunday = LocalDate.of(2026, 6, 7);
    int start = LocalTime.of(6, 0).toSecondOfDay();
    int end = LocalTime.of(7, 0).toSecondOfDay();
    assertThat(plan(algorithm(dtd2gtfs, sunday), "SHN", "BTN", sunday, start, end)).isEmpty();
    assertThat(plan(algorithm(gbTransit, sunday), "SHN", "BTN", sunday, start, end)).hasSize(1);
  }

  private static RaptorAlgorithm algorithm(ExtendedGtfsRelationalDaoImpl dao, LocalDate date) {
    return RaptorAlgorithmFactory.createFromDao(dao, CalendarServiceDataFactoryImpl.createService(dao),
      new ServiceDate(date.getYear(), date.getMonthValue(), date.getDayOfMonth()));
  }

  /** Journeys as text without trip ids, sorted, so two feeds with different identifiers compare equal. */
  private static TreeSet<String> plan(RaptorAlgorithm raptor, String origin, String destination, LocalDate date, int start, int end) {
    return new RangeQuery<>(raptor, new JourneyFactory())
      .plan(new Stop(origin), new Stop(destination), date, start, end)
      .stream()
      .map(GbTransitParityTest::describe)
      .collect(Collectors.toCollection(TreeSet::new));
  }

  private static String describe(Journey journey) {
    return journey.departureTime() + "-" + journey.arrivalTime() + " " + journey.legs().stream()
      .map(leg -> switch (leg) {
        case Leg.TimetableLeg t -> t.origin().id() + ">" + t.destination().id() + "@"
          + t.stopTimes().getFirst().departureTime() + "-" + t.stopTimes().getLast().arrivalTime()
          + t.stopTimes().stream().map(st -> st.stop().id()).toList();
        case Leg.TransferLeg t -> t.origin().id() + "~" + t.mode() + "~" + t.destination().id() + "/" + t.duration()
          + "/" + t.originInterchange() + "/" + t.destinationInterchange();
      })
      .collect(Collectors.joining(" "));
  }
}
