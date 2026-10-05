package com.joshuaharwood.velociraptor.rail;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * @param services     the trains the trip is made of, in order: one, or one more than it has associations, the k-th
 *                     association being between the k-th and (k+1)-th
 * @param associations where the trip stays aboard from one train onto the next, in call order
 */
public record TrainTrip(List<StopDateTime> stopTimes,
                        List<TrainService> services,
                        @Nullable String headsign,
                        TransitMode mode,
                        Operator operator,
                        Route route,
                        List<Association> associations) {
}
