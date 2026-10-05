package com.joshuaharwood.velociraptor.server.http.dto;

import com.joshuaharwood.velociraptor.rail.TransitMode;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * @param services    the trains the trip is made of, in order: one, or for a trip that stays aboard as its train
 *                    divides, joins another or forms the next service, one more than it has associations
 * @param headsign    where the trip is going, as a passenger reads it; {@code null} where the feed gives none
 * @param transitMode what the trip runs as; a rail replacement bus is {@code REPLACEMENT_BUS}
 */
@Schema(requiredProperties = {"stopTimes", "services", "headsign", "transitMode"})
public record RailTrainTrip(List<RailStopDateTime> stopTimes,
                            List<TrainService> services,
                            @Schema(nullable = true) @Nullable String headsign,
                            TransitMode transitMode) {
}
