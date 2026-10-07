package com.joshuaharwood.velociraptor.server;

import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;
import com.joshuaharwood.velociraptor.gtfs.FeedAttribution;
import com.joshuaharwood.velociraptor.gtfs.FeedFormat;
import com.joshuaharwood.velociraptor.server.http.dto.Feed;
import org.jspecify.annotations.Nullable;
import org.onebusaway.gtfs.model.FeedInfo;
import org.onebusaway.gtfs.model.calendar.ServiceDate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;

/**
 * What {@code GET /feed} answers: the feed as loaded, described once at start-up since none of it changes after.
 */
final class FeedDescription {

  private FeedDescription() {
  }

  static Feed describe(FeedFormat format, FeedSource source, ExtendedGtfsRelationalDaoImpl dao, Instant loadedAt) {
    // feed_info.txt has one row. Should a feed carry more, the first by id is as good as any and stable.
    var info = dao.getAllFeedInfos().stream().min(Comparator.comparing(FeedInfo::getId)).map(FeedDescription::info).orElse(null);
    var attributions = dao.getAllAttributions().stream().map(FeedDescription::attribution).toList();
    return new Feed(source.id(), format.configValue(), loadedAt.atOffset(ZoneOffset.UTC), source(source), info, attributions);
  }

  private static Feed.Source source(FeedSource source) {
    return new Feed.Source(source.location(), source.sha256(), source.sizeBytes(), source.s3VersionId(),
                           source.s3ETag(), source.s3LastModified() == null ? null : source.s3LastModified().atOffset(ZoneOffset.UTC));
  }

  private static Feed.Info info(FeedInfo info) {
    return new Feed.Info(info.getPublisherName(), info.getPublisherUrl(), info.getLang(), date(info.getStartDate()),
                         date(info.getEndDate()), blankToNull(info.getVersion()), blankToNull(info.getContactEmail()),
                         blankToNull(info.getContactUrl()));
  }

  private static Feed.Attribution attribution(FeedAttribution row) {
    return new Feed.Attribution(row.getOrganizationName(), FeedAttribution.flag(row.getIsProducer()),
                                FeedAttribution.flag(row.getIsOperator()), FeedAttribution.flag(row.getIsAuthority()),
                                blankToNull(row.getUrl()), blankToNull(row.getEmail()), blankToNull(row.getPhone()),
                                blankToNull(row.getLicence()));
  }

  private static @Nullable LocalDate date(@Nullable ServiceDate date) {
    return date == null ? null : LocalDate.of(date.getYear(), date.getMonth(), date.getDay());
  }

  private static @Nullable String blankToNull(@Nullable String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
