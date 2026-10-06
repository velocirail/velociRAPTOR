package com.joshuaharwood.velociraptor.server.http.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * What the server is and which timetable it is planning on.
 *
 * @param version       the server's version
 * @param feedFormat    how the feed was read: {@code gb-transit}, or the deprecated {@code dtd2gtfs}
 * @param feedVersion   the feed's own version, from its {@code feed_info.txt}; for a gb-transit feed, the National
 *                      Rail timetable it was built from, e.g. {@code RJTTC979.ZIP}; {@code null} where it gives none
 * @param feedStartDate the first service date the feed covers; {@code null} where it does not say
 * @param feedEndDate   the last service date the feed covers; {@code null} where it does not say
 * @param publisherName who published the feed; {@code null} where it does not say
 * @param publisherUrl  the publisher's site; {@code null} where it does not say
 */
@Schema(requiredProperties = {"version", "feedFormat", "feedVersion", "feedStartDate", "feedEndDate", "publisherName",
    "publisherUrl"})
public record ServerInfo(String version,
                         String feedFormat,
                         @Schema(nullable = true) @Nullable String feedVersion,
                         @Schema(nullable = true) @Nullable LocalDate feedStartDate,
                         @Schema(nullable = true) @Nullable LocalDate feedEndDate,
                         @Schema(nullable = true) @Nullable String publisherName,
                         @Schema(nullable = true) @Nullable String publisherUrl) {
}
