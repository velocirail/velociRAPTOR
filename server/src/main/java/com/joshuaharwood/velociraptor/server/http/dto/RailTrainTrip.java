package com.joshuaharwood.velociraptor.server.http.dto;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record RailTrainTrip(String tripId,
                                    List<RailStopDateTime> stopTimes,
                                    String serviceId,
                                    String agencyId,
                                    @Nullable String trainUid) {
}
