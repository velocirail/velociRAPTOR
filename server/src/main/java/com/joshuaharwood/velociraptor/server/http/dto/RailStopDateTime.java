package com.joshuaharwood.velociraptor.server.http.dto;

import java.time.OffsetDateTime;

public record RailStopDateTime(String stop,
                                       OffsetDateTime departureTime,
                                       OffsetDateTime arrivalTime,
                                       boolean pickUp,
                                       boolean dropOff) {
}
