package com.joshuaharwood.velociraptor.server.http.dto;

import com.joshuaharwood.velociraptor.raptor.model.PickupDropOffType;

import java.time.OffsetDateTime;

public record RailStopDateTime(String stop,
                                       OffsetDateTime departureTime,
                                       OffsetDateTime arrivalTime,
                                       boolean pickUp,
                                       boolean dropOff,
                                       PickupDropOffType pickUpType,
                                       PickupDropOffType dropOffType) {
}
