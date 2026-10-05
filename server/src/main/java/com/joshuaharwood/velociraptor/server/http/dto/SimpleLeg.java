package com.joshuaharwood.velociraptor.server.http.dto;

import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;

public record SimpleLeg(String origin,
                        String destination,
                        OffsetDateTime departureTime,
                        OffsetDateTime arrivalTime,
                        @Nullable String originTrainUid,
                        @Nullable String destinationTrainUid) {
}
