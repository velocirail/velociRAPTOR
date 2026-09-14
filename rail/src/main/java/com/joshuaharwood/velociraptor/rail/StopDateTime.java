package com.joshuaharwood.velociraptor.rail;

import com.joshuaharwood.velociraptor.raptor.model.PickupDropOffType;
import com.joshuaharwood.velociraptor.raptor.model.Stop;

import java.time.LocalDateTime;

public record StopDateTime(Stop stop,
                           LocalDateTime departureTime,
                           LocalDateTime arrivalTime,
                           boolean isPickUp,
                           boolean isDropOff,
                           PickupDropOffType pickUpType,
                           PickupDropOffType dropOffType) {
}
