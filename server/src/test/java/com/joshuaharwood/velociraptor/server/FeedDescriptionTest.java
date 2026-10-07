package com.joshuaharwood.velociraptor.server;

import com.joshuaharwood.velociraptor.gtfs.FeedFormat;
import com.joshuaharwood.velociraptor.gtfs.GtfsDeserialiser;
import com.joshuaharwood.velociraptor.server.http.dto.Feed;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code GET /feed}'s answer, built from the sample feed. Plain unit test - no Quarkus/S3. */
@SuppressWarnings("removal")
class FeedDescriptionTest {

  private static final Path GB_TRANSIT = Path.of("..", "fixtures", "gtfs-sample-gb-transit");
  private static final Path DTD2GTFS = Path.of("..", "fixtures", "gtfs-sample");
  private static final Instant LOADED = Instant.parse("2026-06-01T05:00:00Z");

  @Test
  void describesTheFeedItsSourceAndItsAttributions() {
    var source = FeedSource.ofPath(GB_TRANSIT);
    var feed = FeedDescription.describe(FeedFormat.GB_TRANSIT, source,
                                        GtfsDeserialiser.createNewDao(GB_TRANSIT.toFile(), FeedFormat.GB_TRANSIT), LOADED);

    assertThat(feed.id()).isEqualTo(source.id()).hasSize(12);
    assertThat(feed.format()).isEqualTo("gb-transit");
    assertThat(feed.loadedAt()).isEqualTo(OffsetDateTime.parse("2026-06-01T05:00:00Z"));
    assertThat(feed.source().sha256()).isEqualTo(source.sha256());
    assertThat(feed.source().s3VersionId()).isNull();

    assertThat(feed.feedInfo()).isEqualTo(new Feed.Info("velociRAPTOR sample feed",
      "https://github.com/joshuaharwood/velociRAPTOR", "en", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 28),
      "sample-1", null, null));

    assertThat(feed.attributions()).containsExactly(
      new Feed.Attribution("Rail Delivery Group", false, false, true, "https://raildata.org.uk/", null, null,
                           "Rail Settlement Plan data licence"),
      new Feed.Attribution("Department for Transport", false, false, true, "https://beta-naptan.dft.gov.uk/", null,
                           null, "Open Government Licence v3.0"));
  }

  @Test
  void aFeedThatCreditsNobodyHasNoAttributions() {
    var feed = FeedDescription.describe(FeedFormat.DTD2GTFS, FeedSource.ofPath(DTD2GTFS),
                                        GtfsDeserialiser.createNewDao(DTD2GTFS.toFile(), FeedFormat.DTD2GTFS), LOADED);

    assertThat(feed.attributions()).isEmpty();
    assertThat(feed.feedInfo()).isNotNull();
  }
}
