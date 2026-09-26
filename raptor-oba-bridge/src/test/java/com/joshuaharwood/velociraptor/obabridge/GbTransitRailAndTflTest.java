package com.joshuaharwood.velociraptor.obabridge;

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
import org.onebusaway.gtfs.impl.calendar.CalendarServiceDataFactoryImpl;
import org.onebusaway.gtfs.model.calendar.ServiceDate;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rail and TfL sample: no Tube links, a timetabled Victoria line whose platforms at VIC, EUS and STP are children
 * of those rail stations, three TfL stations of their own, and a walk with no window from Warren Street to Euston.
 */
class GbTransitRailAndTflTest {

  private static final LocalDate WEDNESDAY = LocalDate.of(2026, 6, 3);

  private static RaptorAlgorithm raptor;

  @BeforeAll
  static void load() {
    var dao = GtfsDeserialiser.createNewDao(
      Path.of("..", "fixtures", "gtfs-sample-gb-transit-rail-and-tfl").toFile(), FeedFormat.GB_TRANSIT);
    raptor = RaptorAlgorithmFactory.createFromDao(dao, CalendarServiceDataFactoryImpl.createService(dao),
      new ServiceDate(2026, 6, 3));
  }

  @Test
  void crossLondonIsATimetabledTubeTrainChangedOntoAtTheRailStation() {
    // The 13:00 fast reaches VIC at 14:00; after VIC's 10 minute interchange the 14:10 Victoria line reaches EUS at
    // 14:18, and after EUS's 10 minutes the 14:50 London Northwestern reaches MKC at 15:25.
    var journeys = plan("BTN", "MKC", 12, 30, 13, 5);

    assertThat(journeys).hasSize(1);
    var legs = journeys.getFirst().legs();
    assertThat(legs).hasSize(3).allMatch(leg -> leg instanceof Leg.TimetableLeg);
    var tube = (Leg.TimetableLeg) legs.get(1);
    assertThat(tube.trip().id()).startsWith("tfl_VIC_N_");
    assertThat(tube.origin().id()).isEqualTo("VIC");
    assertThat(tube.destination().id()).isEqualTo("EUS");
    assertThat(tube.stopTimes().getFirst().departureTime()).isEqualTo(LocalTime.of(14, 10).toSecondOfDay());
    assertThat(journeys.getFirst().arrivalTime()).isEqualTo(LocalTime.of(15, 25).toSecondOfDay());
  }

  @Test
  void aTflStationOfItsOwnIsReachedByItsCode() {
    // Oxford Circus is TfL's alone: it routes under the code gb-transit gives it.
    var journeys = plan("BTN", "101", 12, 30, 13, 5);

    assertThat(journeys).isNotEmpty();
    var last = (Leg.TimetableLeg) journeys.getFirst().legs().getLast();
    assertThat(last.destination().id()).isEqualTo("101");
  }

  @Test
  void aWalkWithNoWindowIsOfferedAllDayAndIntoTheNextMorning() {
    // Warren Street to Euston has no hours of its own: the bridge offers it across the service day, and again
    // shifted a day for the arrivals after midnight, as it does every link.
    assertThat(raptor.transfersFrom(new Stop("102")))
      .filteredOn(t -> t.destination().id().equals("EUS"))
      .extracting(t -> t.mode() + " " + t.duration() + " " + t.startTime() + "-" + t.endTime())
      .containsExactlyInAnyOrder("WALK 300 0-86399", "WALK 300 86400-172799");
  }

  private static List<Journey> plan(String origin, String destination, int fromHour, int fromMinute, int toHour, int toMinute) {
    return new RangeQuery<>(raptor, new JourneyFactory())
      .plan(new Stop(origin), new Stop(destination), WEDNESDAY,
        LocalTime.of(fromHour, fromMinute).toSecondOfDay(), LocalTime.of(toHour, toMinute).toSecondOfDay());
  }
}
