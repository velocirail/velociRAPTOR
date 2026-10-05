package com.joshuaharwood.velociraptor.server.http.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * A run of a train on a day. The trip_id and service date together name exactly one run, which can be found in the
 * feed and matched to live running; the train UID and retail service ID are how the railway knows it.
 *
 * @param tripId          the GTFS trip_id
 * @param serviceDate     the service date it runs on, which for a train run past midnight is the day it started; a
 *                        sleeper's portion that leaves after midnight on the next day's timetable is on the next day
 * @param trainUid        the ATOC/CIF train UID; {@code null} where the train has none, as a TfL trip does not
 * @param retailServiceId the retail service ID ticketing knows the train by, e.g. {@code SN430003}; {@code null}
 *                        where the feed gives none
 */
@Schema(requiredProperties = {"tripId", "serviceDate", "trainUid", "retailServiceId"})
public record TrainService(String tripId,
                           LocalDate serviceDate,
                           @Schema(nullable = true) @Nullable String trainUid,
                           @Schema(nullable = true) @Nullable String retailServiceId) {
}
