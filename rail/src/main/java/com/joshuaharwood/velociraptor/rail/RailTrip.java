package com.joshuaharwood.velociraptor.rail;

import org.jspecify.annotations.Nullable;
import com.joshuaharwood.velociraptor.raptor.model.StopTime;
import com.joshuaharwood.velociraptor.raptor.model.Trip;

import java.util.List;

/**
 * Trip enriched with GTFS metadata needed to produce rail result DTOs.
 *
 * The raptor algorithm treats this as an opaque {@link Trip}; the id and stopTimes are the only fields it reads.
 * The extra fields flow through kConnections and get unpacked by {@link RailJourneyFactory}.
 *
 * @param retailServiceId the retail service ID ticketing knows the train by, e.g. {@code SN430003}; null where the
 *                        feed gives none
 * @param headsign        where the trip is going, as a passenger reads it; null where the feed gives none
 * @param operator        the company running the trip
 * @param route           the line or brand it runs under
 * @param platforms       the platform of each call, by the same index as {@code stopTimes}, null where the feed
 *                        names none; empty where the feed names no platforms at all
 */
public record RailTrip(String id,
                       List<StopTime> stopTimes,
                       String serviceId,
                       @Nullable String trainUid,
                       @Nullable String retailServiceId,
                       @Nullable String headsign,
                       TransitMode mode,
                       Operator operator,
                       Route route,
                       List<@Nullable String> platforms) implements Trip {

  /** The platform of the call at {@code stopIndex}, or null where the feed names none. */
  public @Nullable String platform(int stopIndex) {
    return stopIndex < platforms.size() ? platforms.get(stopIndex) : null;
  }
}
