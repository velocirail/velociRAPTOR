package com.joshuaharwood.velociraptor.rail;

import org.jspecify.annotations.Nullable;

/**
 * One train a {@link RailTrip} is made of, as the feed identifies it. A trip is built for a service date without
 * knowing which, so the run says how many service days after that date it runs; {@link RailJourneyFactory} adds it
 * to the query's date to name the run in a {@link TrainService}.
 *
 * @param tripId          the GTFS trip_id
 * @param dayOffset       the service days after the trip's own that this train runs on: 1 for a sleeper's portion
 *                        that leaves after midnight on the next day's timetable, otherwise 0
 * @param trainUid        the ATOC/CIF train UID; null where the train has none, as a TfL trip does not
 * @param retailServiceId the retail service ID ticketing knows the train by, e.g. {@code SN430003}; null where the
 *                        feed gives none
 */
public record TrainRun(String tripId, int dayOffset, @Nullable String trainUid, @Nullable String retailServiceId) {
}
