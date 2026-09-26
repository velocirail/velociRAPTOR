package com.joshuaharwood.velociraptor.gtfs.sample;

import com.joshuaharwood.velociraptor.gtfs.FeedFormat;
import com.joshuaharwood.velociraptor.gtfs.GtfsDeserialiser;
import com.joshuaharwood.velociraptor.gtfs.sample.SampleFeed.Shape;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
 * Pins each {@code fixtures/gtfs-sample*} directory to what {@link SampleFeed} generates for its {@link Shape}, so a
 * change to the network arrives as a readable diff of the committed text. To take a change:
 * <pre>
 *   mvn -pl gtfs test -Dtest=SampleFeedGoldenTest -Dsample.feed.update=true
 * </pre>
 * then read the diff before committing it.
 */
@SuppressWarnings({"deprecation", "removal"}) // the dtd2gtfs shape stays pinned while the reader accepts it
class SampleFeedGoldenTest {

  private static final boolean UPDATE = Boolean.getBoolean("sample.feed.update");

  /** Relative to the gtfs module, which is where surefire runs. */
  static Path fixture(Shape shape) {
    return Path.of("..", "fixtures", shape.directory());
  }

  static FeedFormat format(Shape shape) {
    return shape == Shape.DTD2GTFS ? FeedFormat.DTD2GTFS : FeedFormat.GB_TRANSIT;
  }

  @ParameterizedTest
  @EnumSource(Shape.class)
  void committedFixtureMatchesTheGenerator(Shape shape) throws IOException {
    var fixture = fixture(shape);
    var generated = SampleFeed.files(shape);
    if (UPDATE) {
      SampleFeed.writeTo(fixture, shape);
    }

    assertThat(fixture).isDirectory();
    for (var e : generated.entrySet()) {
      assertThat(fixture.resolve(e.getKey()))
        .as("%s %s (regenerate with -Dsample.feed.update=true)", shape, e.getKey())
        .hasContent(e.getValue());
    }
    try (var listing = Files.list(fixture)) {
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
    var gbTransitIds = new HashSet<String>();
    for (var t : trips) {
      assertThat(ids.add(t.id())).as("duplicate trip id %s", t.id()).isTrue();
      assertThat(uids.add(t.uid())).as("duplicate uid %s", t.uid()).isTrue();
      assertThat(gbTransitIds.add(GbTransitFiles.tripId(t))).as("duplicate gb-transit trip id for %s", t.id()).isTrue();
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

  @ParameterizedTest
  @EnumSource(Shape.class)
  void theFixtureLoadsThroughTheReader(Shape shape) {
    var dao = GtfsDeserialiser.createNewDao(fixture(shape).toFile(), format(shape));
    dao.initialise();
    var calendar = CalendarServiceDataFactoryImpl.createService(dao);
    var profile = dao.feedProfile();
    boolean tfl = shape == Shape.GB_TRANSIT_RAIL_AND_TFL;

    int tflTrips = tfl ? GbTransitFiles.tflTrips().size() : 0;
    assertThat(dao.getAllTrips()).hasSize(SampleFeed.trips().size() + tflTrips);

    // Every station routes under its CRS whichever shape it is read from, and the only other keys are the TfL
    // stations of their own.
    var keys = dao.getAllStops().stream().map(profile::stopKey).collect(Collectors.toSet());
    var expectedKeys = SampleFeed.STATIONS.stream().map(SampleFeed.Station::crs).collect(Collectors.toSet());
    if (tfl) {
      SampleFeed.VICTORIA_LINE.stream().filter(t -> t.within() == null).forEach(t -> expectedKeys.add(t.code()));
    }
    assertThat(keys).isEqualTo(expectedKeys);

    int links = switch (shape) {
      case DTD2GTFS -> SampleFeed.LINKS.size();
      case GB_TRANSIT -> GbTransitFiles.envelopes(false).size();
      case GB_TRANSIT_RAIL_AND_TFL -> GbTransitFiles.envelopes(true).size() + 2;
    };
    assertThat(dao.getAllFixedLinks()).hasSize(links);

    var wednesday = new ServiceDate(2026, 6, 3);
    var bankHoliday = new ServiceDate(2026, 6, 15);
    var sunday = new ServiceDate(2026, 6, 7);
    assertThat(railServices(calendar.getServiceIdsOnDate(wednesday)))
      .containsExactlyInAnyOrder(SampleFeed.MON_SAT, SampleFeed.DAILY, SampleFeed.MON_SAT_OVERLAY_BASE);
    assertThat(railServices(calendar.getServiceIdsOnDate(sunday)))
      .containsExactlyInAnyOrder(SampleFeed.DAILY);
    assertThat(railServices(calendar.getServiceIdsOnDate(bankHoliday)))
      .containsExactlyInAnyOrder(SampleFeed.DAILY);
    assertThat(railServices(calendar.getServiceIdsOnDate(new ServiceDate(2026, 6, 24))))
      .containsExactlyInAnyOrder(SampleFeed.MON_SAT, SampleFeed.DAILY, SampleFeed.OVERLAY);
    assertThat(calendar.getServiceIdsOnDate(new ServiceDate(2026, 5, 31))).isEmpty();
  }

  @Test
  void theTrainUidIsReadFromEitherShape() {
    var gbTransit = GtfsDeserialiser.createNewDao(fixture(Shape.GB_TRANSIT_RAIL_AND_TFL).toFile(), FeedFormat.GB_TRANSIT);
    var dtd2gtfs = GtfsDeserialiser.createNewDao(fixture(Shape.DTD2GTFS).toFile(), FeedFormat.DTD2GTFS);

    var fast = gbTransit.getAllTrips().stream().filter(t -> t.getId().getId().equals("WB0900_20260601_20260628")).findFirst().orElseThrow();
    assertThat(gbTransit.feedProfile().trainUid(fast)).isEqualTo("WB0900");
    var tube = gbTransit.getAllTrips().stream().filter(t -> t.getId().getId().startsWith("tfl_")).findFirst().orElseThrow();
    assertThat(gbTransit.feedProfile().trainUid(tube)).isNull();

    var legacyFast = dtd2gtfs.getAllTrips().stream().filter(t -> t.getId().getId().equals("1020900")).findFirst().orElseThrow();
    assertThat(dtd2gtfs.feedProfile().trainUid(legacyFast)).isEqualTo("WB0900");
  }

  @Test
  void gtfsTimesRunPastMidnight() {
    assertThat(SampleFeed.time(SampleFeed.hm(24, 15))).isEqualTo("24:15:00");
    assertThat(SampleFeed.files(Shape.DTD2GTFS).get("stop_times.txt")).contains(",24:15:00,24:15:00,VIC,");
    assertThat(SampleFeed.files(Shape.GB_TRANSIT).get("stop_times.txt")).contains(",24:15:00,24:15:00,9100VICTRIC15,");
    assertThat(new String(SampleFeed.files(Shape.DTD2GTFS).get("links.txt").getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8))
      .contains("VIC,LBG,TUBE,1500,05:30:00,24:30:00,2026-01-01,2026-12-31,1,1,1,1,1,1,0");
  }

  private static java.util.List<Integer> railServices(java.util.Set<org.onebusaway.gtfs.model.AgencyAndId> ids) {
    return ids.stream().map(org.onebusaway.gtfs.model.AgencyAndId::getId)
      .filter(id -> !id.startsWith("tfl_"))
      .map(Integer::parseInt)
      .toList();
  }
}
