package com.joshuaharwood.velociraptor.server.http.dto;

import com.joshuaharwood.velociraptor.raptor.model.PickupDropOffType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(requiredProperties = {"stop", "departureTime", "arrivalTime", "pickUp", "dropOff", "pickUpType", "dropOffType"})
public record RailStopDateTime(String stop,
                                       OffsetDateTime departureTime,
                                       OffsetDateTime arrivalTime,
                                       boolean pickUp,
                                       boolean dropOff,
                                       PickupDropOffType pickUpType,
                                       PickupDropOffType dropOffType) {
}
