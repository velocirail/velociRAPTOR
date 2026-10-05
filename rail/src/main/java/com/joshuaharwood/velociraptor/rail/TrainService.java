package com.joshuaharwood.velociraptor.rail;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * A run of a train on a day: what a passenger rides, as the feed and the railway identify it. The trip_id and the
 * service date together name exactly one run; the train UID and retail service ID are how the railway knows it.
 *
 * @param tripId          the GTFS trip_id
 * @param serviceDate     the service date it runs on, which for a train run past midnight is the day it started
 * @param trainUid        the ATOC/CIF train UID; null where the train has none, as a TfL trip does not
 * @param retailServiceId the retail service ID ticketing knows the train by, e.g. {@code SN430003}; null where the
 *                        feed gives none
 */
public record TrainService(String tripId,
                           LocalDate serviceDate,
                           @Nullable String trainUid,
                           @Nullable String retailServiceId) {
}
