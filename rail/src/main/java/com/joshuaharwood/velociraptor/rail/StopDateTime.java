package com.joshuaharwood.velociraptor.rail;

import com.joshuaharwood.velociraptor.raptor.model.PickupDropOffType;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;

/**
 * @param platform the platform the train calls at, e.g. {@code 5} or {@code 1F}; null where the feed names none
 * @param headsign where the train is going as this call shows it, e.g. {@code Portsmouth Harbour and Bognor Regis}
 *                 before it divides; null where the call shows the trip's own headsign
 */
public record StopDateTime(Stop stop,
                           LocalDateTime departureTime,
                           LocalDateTime arrivalTime,
                           boolean isPickUp,
                           boolean isDropOff,
                           PickupDropOffType pickUpType,
                           PickupDropOffType dropOffType,
                           @Nullable String platform,
                           @Nullable String headsign) {
}
