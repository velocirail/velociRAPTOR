package com.joshuaharwood.velociraptor.server.http.dto;

import com.joshuaharwood.velociraptor.raptor.model.PickupDropOffType;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * @param duration            arrival minus departure
 * @param boardingInterchange the minimum interchange at this leg's origin, which had to elapse after
 *                            the previous leg arrived before this leg could be boarded (or, for a
 *                            fixed link, started); {@code null} on the first leg, which nothing precedes
 */
public record SimpleLeg(String origin,
                        String destination,
                        OffsetDateTime departureTime,
                        OffsetDateTime arrivalTime,
                        @Nullable String originTrainUid,
                        @Nullable String destinationTrainUid,
                        @Nullable PickupDropOffType originPickUpType,
                        @Nullable PickupDropOffType destinationDropOffType,
                        @Nullable String operator,
                        @Nullable String mode,
                        Duration duration,
                        @Nullable Duration boardingInterchange) {
}
