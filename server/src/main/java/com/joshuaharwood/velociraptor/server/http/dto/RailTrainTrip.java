package com.joshuaharwood.velociraptor.server.http.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Schema(requiredProperties = {"tripId", "stopTimes", "serviceId", "agencyId", "trainUid"})
public record RailTrainTrip(String tripId,
                                    List<RailStopDateTime> stopTimes,
                                    String serviceId,
                                    String agencyId,
                                    @Schema(nullable = true) @Nullable String trainUid) {
}
