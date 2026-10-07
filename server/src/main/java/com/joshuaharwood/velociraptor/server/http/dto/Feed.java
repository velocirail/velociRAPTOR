package com.joshuaharwood.velociraptor.server.http.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The feed this server answers from, and where it came from.
 *
 * @param id           a short id for the feed, the first twelve hex characters of {@code source.sha256}. Every
 *                     response carries it in the {@code X-Feed-Id} header, so a stored answer can be matched to the
 *                     feed that produced it
 * @param format       the configured feed format: {@code gb-transit} or {@code dtd2gtfs}
 * @param loadedAt     when this server finished reading the feed
 * @param source       the bytes the feed was read from
 * @param feedInfo     the feed's {@code feed_info.txt}, as its producer wrote it; null where the feed has none
 * @param attributions the feed's {@code attributions.txt}: who it credits for its data and on what terms, in file
 *                     order; empty where the feed has none
 */
@Schema(requiredProperties = {"id", "format", "loadedAt", "source", "feedInfo", "attributions"})
public record Feed(String id,
                   String format,
                   OffsetDateTime loadedAt,
                   Source source,
                   @Schema(nullable = true) @Nullable Info feedInfo,
                   List<Attribution> attributions) {

  /**
   * @param location       the configured source: an {@code s3://} URI or a local path
   * @param sha256         the SHA-256 of the zip as read, matching {@code sha256sum} on the same file. A feed read
   *                       from a directory is digested over its files in name order instead
   * @param sizeBytes      the size of what was read
   * @param s3VersionId    the S3 object version; null for a local path or an unversioned bucket
   * @param s3ETag         the S3 object's ETag; null for a local path
   * @param s3LastModified when the S3 object was last written; null for a local path
   */
  @Schema(name = "FeedSource",
          requiredProperties = {"location", "sha256", "sizeBytes", "s3VersionId", "s3ETag", "s3LastModified"})
  public record Source(String location,
                       String sha256,
                       long sizeBytes,
                       @Schema(nullable = true) @Nullable String s3VersionId,
                       @Schema(nullable = true) @Nullable String s3ETag,
                       @Schema(nullable = true) @Nullable OffsetDateTime s3LastModified) {
  }

  /**
   * {@code feed_info.txt}. For a gb-transit feed {@code version} is the DTD timetable file the feed was built from,
   * such as {@code RJTTF001.ZIP}.
   */
  @Schema(name = "FeedInfo",
          requiredProperties = {"publisherName", "publisherUrl", "lang", "startDate", "endDate", "version",
            "contactEmail", "contactUrl"})
  public record Info(String publisherName,
                     String publisherUrl,
                     String lang,
                     @Schema(nullable = true) @Nullable LocalDate startDate,
                     @Schema(nullable = true) @Nullable LocalDate endDate,
                     @Schema(nullable = true) @Nullable String version,
                     @Schema(nullable = true) @Nullable String contactEmail,
                     @Schema(nullable = true) @Nullable String contactUrl) {
  }

  /**
   * A row of {@code attributions.txt}.
   *
   * @param licence what the data may be used under. Not a GTFS field: gb-transit adds it, so it is null for a feed
   *                from anywhere else
   */
  @Schema(name = "FeedAttribution",
          requiredProperties = {"organizationName", "producer", "operator", "authority", "url", "email", "phone",
            "licence"})
  public record Attribution(String organizationName,
                            boolean producer,
                            boolean operator,
                            boolean authority,
                            @Schema(nullable = true) @Nullable String url,
                            @Schema(nullable = true) @Nullable String email,
                            @Schema(nullable = true) @Nullable String phone,
                            @Schema(nullable = true) @Nullable String licence) {
  }
}
