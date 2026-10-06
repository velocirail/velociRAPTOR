package com.joshuaharwood.velociraptor.server.http.dto;

import com.joshuaharwood.velociraptor.rail.TransitMode;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * @param retailServiceId the retail service ID ticketing knows the train by, e.g. {@code SN430003}; {@code null}
 *                        where the feed gives none
 * @param headsign        where the trip is going, as a passenger reads it; {@code null} where the feed gives none
 * @param transitMode     what the trip runs as; a rail replacement bus is {@code REPLACEMENT_BUS}
 */
@Schema(requiredProperties = {"tripId", "stopTimes", "serviceId", "trainUid", "retailServiceId", "headsign",
    "transitMode"})
public record RailTrainTrip(String tripId,
                                    List<RailStopDateTime> stopTimes,
                                    String serviceId,
                                    @Schema(nullable = true) @Nullable String trainUid,
                                    @Schema(nullable = true) @Nullable String retailServiceId,
                                    @Schema(nullable = true) @Nullable String headsign,
                                    TransitMode transitMode) {
}
