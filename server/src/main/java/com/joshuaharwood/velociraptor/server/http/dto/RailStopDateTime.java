package com.joshuaharwood.velociraptor.server.http.dto;

import com.joshuaharwood.velociraptor.raptor.model.PickupDropOffType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;

/**
 * @param platform the platform the train calls at, e.g. {@code 5} or {@code 1F}; {@code null} where the feed names none
 * @param headsign where the train is going as this call shows it, e.g. {@code Portsmouth Harbour and Bognor Regis}
 *                 before it divides; {@code null} where the call shows the trip's own headsign
 */
@Schema(requiredProperties = {"stop", "departureTime", "arrivalTime", "pickUp", "dropOff", "pickUpType", "dropOffType",
    "platform", "headsign"})
public record RailStopDateTime(String stop,
                                       OffsetDateTime departureTime,
                                       OffsetDateTime arrivalTime,
                                       boolean pickUp,
                                       boolean dropOff,
                                       PickupDropOffType pickUpType,
                                       PickupDropOffType dropOffType,
                                       @Schema(nullable = true) @Nullable String platform,
                                       @Schema(nullable = true) @Nullable String headsign) {
}
