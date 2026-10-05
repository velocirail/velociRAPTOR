package com.joshuaharwood.velociraptor.server;

import com.joshuaharwood.velociraptor.gtfs.FeedFormat;
import com.joshuaharwood.velociraptor.gtfs.GtfsDeserialiser;
import com.joshuaharwood.velociraptor.server.http.dto.Station;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** The stations the reference endpoint lists, from either feed format. Plain unit test - no Quarkus/S3. */
class FeedReferenceTest {

  @Test
  void aGbTransitStationIsListedOnceAsTheStationNotItsPlatforms() {
    var dao = GtfsDeserialiser.createNewDao(Path.of("..", "fixtures", "gtfs-sample-gb-transit").toFile(),
                                            FeedFormat.GB_TRANSIT);

    var stations = FeedReference.stationsOf(dao);

    assertThat(stations).extracting(Station::code).doesNotHaveDuplicates().isSorted().contains("BTN", "VIC");
    assertThat(stations).filteredOn(station -> station.code().equals("BTN")).singleElement()
                        .satisfies(btn -> assertThat(btn.name()).isEqualTo("Brighton"));
  }

  @Test
  @SuppressWarnings("removal")
  void aDtd2gtfsStopIsAStationOfItsOwn() {
    var dao = GtfsDeserialiser.createNewDao(Path.of("..", "fixtures", "gtfs-sample").toFile(), FeedFormat.DTD2GTFS);

    assertThat(FeedReference.stationsOf(dao)).extracting(Station::code)
                                             .containsExactlyElementsOf(dao.getAllStops().stream()
                                                                           .map(stop -> stop.getId().getId())
                                                                           .sorted()
                                                                           .toList());
  }
}
