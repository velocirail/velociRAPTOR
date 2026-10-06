package com.joshuaharwood.velociraptor.rail;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record TrainTrip(String tripId,
                        List<StopDateTime> stopTimes,
                        String serviceId,
                        @Nullable String trainUid,
                        @Nullable String retailServiceId,
                        @Nullable String headsign,
                        TransitMode mode,
                        Operator operator,
                        Route route) {
}
