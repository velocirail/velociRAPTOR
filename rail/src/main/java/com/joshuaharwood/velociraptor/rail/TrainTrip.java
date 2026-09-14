package com.joshuaharwood.velociraptor.rail;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record TrainTrip(String tripId,
                        List<StopDateTime> stopTimes,
                        String serviceId,
                        String agencyId,
                        @Nullable String trainUid) {
}
