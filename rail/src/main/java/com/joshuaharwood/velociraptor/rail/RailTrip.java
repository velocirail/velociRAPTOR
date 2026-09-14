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
 */
public record RailTrip(String id,
                       List<StopTime> stopTimes,
                       String serviceId,
                       String agencyId,
                       @Nullable String trainUid) implements Trip {
}
