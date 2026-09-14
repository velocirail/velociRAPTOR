package com.joshuaharwood.velociraptor.gtfs.sample;

import com.joshuaharwood.velociraptor.gtfs.GtfsDeserialiser;
import org.junit.jupiter.api.Test;
import org.onebusaway.gtfs.impl.calendar.CalendarServiceDataFactoryImpl;
import org.onebusaway.gtfs.model.calendar.ServiceDate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@code fixtures/gtfs-sample} to what {@link SampleFeed} generates, so a change to the network arrives as a
 * readable diff of the committed text. To take a change:
 * <pre>
 *   mvn -pl gtfs test -Dtest=SampleFeedGoldenTest -Dsample.feed.update=true
 * </pre>
 * then read the diff before committing it.
 */
class SampleFeedGoldenTest {

  /** Relative to the gtfs module, which is where surefire runs. */
  static final Path FIXTURE = Path.of("..", "fixtures", "gtfs-sample");

  private static final boolean UPDATE = Boolean.getBoolean("sample.feed.update");

  @Test
  void committedFixtureMatchesTheGenerator() throws IOException {
    var generated = SampleFeed.files();
    if (UPDATE) {
      SampleFeed.writeTo(FIXTURE);
    }

    assertThat(FIXTURE).isDirectory();
    for (var e : generated.entrySet()) {
      assertThat(FIXTURE.resolve(e.getKey()))
        .as("%s (regenerate with -Dsample.feed.update=true)", e.getKey())
        .hasContent(e.getValue());
    }
    try (var listing = Files.list(FIXTURE)) {
      var committed = listing.map(p -> p.getFileName().toString())
        .filter(n -> n.endsWith(".txt"))
        .collect(Collectors.toSet());
      assertThat(committed).as("no stray files in the fixture").isEqualTo(generated.keySet());
    }
  }

  @Test
  void tripIdsAndUidsAreUnique() {
    var trips = SampleFeed.trips();
    var ids = new HashSet<String>();
    var uids = new HashSet<String>();
    for (var t : trips) {
      assertThat(ids.add(t.id())).as("duplicate trip id %s", t.id()).isTrue();
      assertThat(uids.add(t.uid())).as("duplicate uid %s", t.uid()).isTrue();
      assertThat(t.id()).isNotEqualTo(t.uid());
    }
  }

  @Test
  void everyCallIsAtAKnownStationAndTimesAreMonotonic() {
    var known = SampleFeed.STATIONS.stream().map(SampleFeed.Station::crs).collect(Collectors.toSet());
    for (var t : SampleFeed.trips()) {
      int last = -1;
      for (var c : t.calls()) {
        assertThat(known).contains(c.crs());
        assertThat(c.arrival()).as("%s at %s", t.id(), c.crs()).isGreaterThanOrEqualTo(last);
        assertThat(c.departure()).isGreaterThanOrEqualTo(c.arrival());
        last = c.departure();
      }
    }
    for (var l : SampleFeed.LINKS) {
      assertThat(known).contains(l.from(), l.to());
    }
  }

  @Test
  void theFixtureLoadsThroughTheReader() {
    var dao = GtfsDeserialiser.createNewDao(FIXTURE.toFile());
    dao.initialise();
    var calendar = CalendarServiceDataFactoryImpl.createService(dao);

    assertThat(dao.getAllTrips()).hasSize(SampleFeed.trips().size());
    assertThat(dao.getAllStops()).hasSize(SampleFeed.STATIONS.size());
    assertThat(dao.getAllFixedLinks()).hasSize(SampleFeed.LINKS.size());
    assertThat(dao.getAllTransfers()).hasSize((int) SampleFeed.STATIONS.stream().filter(SampleFeed.Station::hasInterchangeRow).count());

    var wednesday = new ServiceDate(2026, 6, 3);
    var bankHoliday = new ServiceDate(2026, 6, 15);
    var sunday = new ServiceDate(2026, 6, 7);
    assertThat(calendar.getServiceIdsOnDate(wednesday)).extracting(id -> Integer.parseInt(id.getId()))
      .containsExactlyInAnyOrder(SampleFeed.MON_SAT, SampleFeed.DAILY, SampleFeed.MON_SAT_OVERLAY_BASE);
    assertThat(calendar.getServiceIdsOnDate(sunday)).extracting(id -> Integer.parseInt(id.getId()))
      .containsExactlyInAnyOrder(SampleFeed.DAILY);
    assertThat(calendar.getServiceIdsOnDate(bankHoliday)).extracting(id -> Integer.parseInt(id.getId()))
      .containsExactlyInAnyOrder(SampleFeed.DAILY);
    assertThat(calendar.getServiceIdsOnDate(new ServiceDate(2026, 6, 24))).extracting(id -> Integer.parseInt(id.getId()))
      .containsExactlyInAnyOrder(SampleFeed.MON_SAT, SampleFeed.DAILY, SampleFeed.OVERLAY);
    assertThat(calendar.getServiceIdsOnDate(new ServiceDate(2026, 5, 31))).isEmpty();
  }

  @Test
  void gtfsTimesRunPastMidnight() {
    assertThat(SampleFeed.time(SampleFeed.hm(24, 15))).isEqualTo("24:15:00");
    assertThat(Files.exists(FIXTURE)).isTrue();
    assertThat(SampleFeed.files().get("stop_times.txt")).contains(",24:15:00,24:15:00,VIC,");
    assertThat(new String(SampleFeed.files().get("links.txt").getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8))
      .contains("VIC,LBG,TUBE,1500,05:30:00,24:30:00,2026-01-01,2026-12-31,1,1,1,1,1,1,0");
  }
}
