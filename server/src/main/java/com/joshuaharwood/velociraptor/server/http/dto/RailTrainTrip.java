package com.joshuaharwood.velociraptor.server.http.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * @param headcode the train's headcode (signalling ID, such as {@code 1A23}) from its CIF schedule; null where the
 *                 feed does not give one - a TfL trip, or a feed built before gb-transit published them
 * @param traction what hauls the train, one entry per source that says; empty where none does
 */
@Schema(requiredProperties = {"tripId", "stopTimes", "serviceId", "agencyId", "trainUid", "headcode", "traction"})
public record RailTrainTrip(String tripId,
                                    List<RailStopDateTime> stopTimes,
                                    String serviceId,
                                    String agencyId,
                                    @Schema(nullable = true) @Nullable String trainUid,
                                    @Schema(nullable = true) @Nullable String headcode,
                                    List<Traction> traction) {
}
